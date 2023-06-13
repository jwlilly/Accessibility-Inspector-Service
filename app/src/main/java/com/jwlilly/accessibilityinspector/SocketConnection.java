package com.jwlilly.accessibilityinspector;
import android.util.Log;

import java.net.UnknownHostException;
import java.util.Observable;

import org.java_websocket.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The ClassSocketConnection.
 * @Author J Jesús Piedra Chávez
 * @Version 1.0 (05-22-2013)
 */

public class SocketConnection extends Observable {

    private final int PORT = 38301;
    protected ServerWebSocket server;
    public String contentInfo;
    private static SocketConnection socketConnection = null;
    private AccessibilityInspector inspector;

    /**
     * Instantiates a new server
     */
    protected SocketConnection() {

        try {
            server = new ServerWebSocket(PORT) {
                public void onMessage(WebSocket conn, String message) {
                    contentInfo = message;
                    Log.d("SocketServer", "Message received: " + message);
                    try {
                        JSONObject jsonObject = new JSONObject(message);
                        if(jsonObject.getString("message").equals("capture")) {
                            startCapture();
                        } else if(jsonObject.getString("message").equals("ping")) {
                            JSONObject pongJson = new JSONObject();
                            pongJson.put("message", "pong");
                            conn.send(pongJson.toString());
                        }
                    } catch (JSONException e) {
                        Log.e("SocketServer", e.getMessage());
                    }
                    setChanged ();
                    notifyObservers();
                }
            };
            server.start();
        } catch (UnknownHostException e) {
            Log.e("SocketServer", e.getMessage());
            e.printStackTrace ();
        }
    }

    static public SocketConnection getInstance() {

        if (socketConnection == null) {
            socketConnection = new SocketConnection();
        }
        return socketConnection;
    }

    /**
     * Send data. Send the message
     *
     * @param message
     *            the information
     */
    public void sendData(String message) throws Exception {

        server.sendToAll(message);
    }

    private void startCapture() {
        if(inspector != null) {
            inspector.startCapture();
        }
    }

    public void setInspector(AccessibilityInspector accessibilityInspector) {
        inspector = accessibilityInspector;
    }
}
