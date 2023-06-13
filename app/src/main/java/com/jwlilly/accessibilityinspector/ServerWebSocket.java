package com.jwlilly.accessibilityinspector;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

public class ServerWebSocket extends WebSocketServer {
    private static int counter = 0;

    public ServerWebSocket(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        counter++;
        Log.d("SocketServer", "///////////Opened connection number" + counter);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d("SocketServer", "closed " + reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e("SocketServer", ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        Log.d("SocketServer", "started");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d("SocketServer", message);
        //conn.send(message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer blob) {
        conn.send(blob);
        Log.d("SocketServer", "message received");
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients.
     *
     * @param text
     *            The String to send across the network.
     * @throws InterruptedException
     *             When socket related I/O errors occur.
     */
    public void sendToAll(String text) {
        Collection<WebSocket> con = getConnections();
        synchronized (con) {
            for (WebSocket c : con) {
                c.send(text);
            }
        }
    }
}
