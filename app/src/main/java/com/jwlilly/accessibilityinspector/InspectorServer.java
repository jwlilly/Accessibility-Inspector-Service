package com.jwlilly.accessibilityinspector;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide websocket server. SocketService owns its lifecycle and AccessibilityInspector
 * pushes trees and announcements through it directly, so JSON never has to travel through
 * intents or static buffers.
 *
 * Every outgoing message is a JSON object with a "type" of "tree", "announcement", "version",
 * "pong" or "error".
 */
public final class InspectorServer {
    /** Called on the server thread when the inspector asks for a capture. */
    public interface CaptureListener {
        void onCaptureRequested(boolean includeNotImportant);
    }

    public static final int PORT = 38301;
    /** Bump when the message format changes in a way the inspector app needs to know about. */
    public static final int PROTOCOL_VERSION = 2;

    private static final String LOG_TAG = "InspectorServer";
    private static final InspectorServer INSTANCE = new InspectorServer();

    private final List<WebSocket> sockets = new CopyOnWriteArrayList<>();
    private AsyncHttpServer server;
    private volatile CaptureListener captureListener;

    private InspectorServer() {}

    public static InspectorServer getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }
        server = new AsyncHttpServer();
        server.websocket("/", this::onConnected);
        server.listen(AsyncServer.getDefault(), PORT);
        Log.d(LOG_TAG, "listening on " + PORT);
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        for (WebSocket socket : sockets) {
            socket.close();
        }
        sockets.clear();
        server.stop();
        server = null;
    }

    public void setCaptureListener(@Nullable CaptureListener listener) {
        captureListener = listener;
    }

    /** Clears the listener only if it is still the given one. */
    public void clearCaptureListener(CaptureListener listener) {
        if (captureListener == listener) {
            captureListener = null;
        }
    }

    public void broadcast(JSONObject message) {
        String payload = message.toString();
        AsyncServer.getDefault().post(() -> {
            for (WebSocket socket : sockets) {
                socket.send(payload);
            }
        });
    }

    public static JSONObject versionMessage() throws JSONException {
        JSONObject message = new JSONObject();
        message.put("type", "version");
        message.put("versionCode", BuildConfig.VERSION_CODE);
        message.put("versionName", BuildConfig.VERSION_NAME);
        message.put("protocolVersion", PROTOCOL_VERSION);
        message.put("sdkInt", Build.VERSION.SDK_INT);
        return message;
    }

    public static JSONObject errorMessage(String error) {
        JSONObject message = new JSONObject();
        try {
            message.put("type", "error");
            message.put("error", error);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "could not build error message", e);
        }
        return message;
    }

    private void send(WebSocket socket, JSONObject message) {
        String payload = message.toString();
        AsyncServer.getDefault().post(() -> socket.send(payload));
    }

    private void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
        sockets.add(webSocket);
        Log.d(LOG_TAG, "client connected");
        webSocket.setStringCallback(s -> handleMessage(webSocket, s));
        webSocket.setClosedCallback(ex -> {
            if (ex != null) {
                Log.e(LOG_TAG, "An error occurred", ex);
            }
            Log.d(LOG_TAG, "closed");
            sockets.remove(webSocket);
        });
        webSocket.setEndCallback(ex -> Log.d(LOG_TAG, "ended"));
    }

    private void handleMessage(WebSocket webSocket, @Nullable String raw) {
        if (raw == null) {
            return;
        }
        Log.d(LOG_TAG, raw);
        try {
            String message = new JSONObject(raw).optString("message");
            switch (message.toLowerCase()) {
                case "capture":
                case "capturenotimportant":
                    CaptureListener listener = captureListener;
                    if (listener == null) {
                        send(webSocket, errorMessage("The Accessibility Inspector accessibility service is not enabled on the device."));
                    } else {
                        listener.onCaptureRequested(message.equalsIgnoreCase("captureNotImportant"));
                    }
                    break;
                case "ping":
                    JSONObject pong = new JSONObject();
                    pong.put("type", "pong");
                    pong.put("message", "pong");
                    send(webSocket, pong);
                    break;
                case "version":
                    send(webSocket, versionMessage());
                    break;
                default:
                    Log.d(LOG_TAG, "unknown message " + message);
            }
        } catch (JSONException e) {
            Log.d(LOG_TAG, "invalid message: " + e.getMessage());
        }
    }
}
