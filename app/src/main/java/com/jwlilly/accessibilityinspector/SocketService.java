package com.jwlilly.accessibilityinspector;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

public class SocketService extends Service {
    AsyncHttpServer server;
    private final int PORT = 38301;
    private SocketRequestCallback requestCallback;

    private Context context = this;

    private final String CHANNEL_ID = "AccessibilityInspectorChannel";
    public static String BROADCAST_MESSAGE = "broadcast";

    public static byte[] data;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(server == null) {
            server = new AsyncHttpServer();
            requestCallback = new SocketRequestCallback();
            Toast.makeText(context, "Inspector Service Created", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "Inspector Service already running", Toast.LENGTH_LONG).show();
        }
        try{
            server.listen(AsyncServer.getDefault(), PORT);
            server.websocket("/", requestCallback);
            Toast.makeText(this, "Inspector Service Started", Toast.LENGTH_LONG).show();
        } catch(Error e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) {
            return START_NOT_STICKY;
        }
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, ServiceActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android Accessibility Inspector")
                .setContentText(input)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        String action = intent.getAction();

        if (action == null) {
            return START_NOT_STICKY;
        } else if (action.equals(BROADCAST_MESSAGE)) {
            try {
                requestCallback.BroadcastMessage(decompress(data));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Inspector Service Stopped", Toast.LENGTH_LONG).show();
        server.stop();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private class SocketRequestCallback implements AsyncHttpServer.WebSocketRequestCallback {
        ArrayList<WebSocket> _sockets = new ArrayList<>();
        @Override
        public void onConnected(WebSocket webSocket, AsyncHttpServerRequest request) {
            _sockets.add(webSocket);
            Log.d("SERVER", "client connected");
            webSocket.setStringCallback(s -> {
                if(s != null) {
                    try{
                        JSONObject jsonObject = new JSONObject(s);
                        if(jsonObject.has("message") && jsonObject.getString("message").equalsIgnoreCase("capture")) {
                            Intent intent = new Intent("com.jwlilly.accessibilityinspector");
                            intent.setAction("A11yInspector");
                            sendBroadcast(intent);
                        }
                        if(jsonObject.has("message") && jsonObject.getString("message").equalsIgnoreCase("ping")) {
                            JSONObject pongObject = new JSONObject();
                            pongObject.put("message", "pong");
                            webSocket.send(pongObject.toString());
                        }
                        if(jsonObject.has("message") && jsonObject.getString("message").equalsIgnoreCase("captureNotImportant")) {
                            Intent intent = new Intent("com.jwlilly.accessibilityinspector");
                            intent.setAction("A11yInspectorImportant");
                            sendBroadcast(intent);
                        }

                    } catch(JSONException e) {
                        Log.d("ERROR", e.getMessage());
                    }
                }
                Log.d("SERVER", s);
            });

            webSocket.setClosedCallback(ex -> {
                try {
                    if (ex != null)
                        Log.e("SERVER", "An error occurred", ex);
                } finally {
                    Log.d("SERVER", "closed");
                    _sockets.remove(webSocket);
                }
            });

            webSocket.setEndCallback(ex -> Log.d("SERVER", "ended: " + ex.getMessage()));
        }

        public void BroadcastMessage(String message) {
            for (WebSocket socket : _sockets)
                socket.send(message);
        }
    }
    public static String decompress(byte[] compressed) throws IOException {
        final int BUFFER_SIZE = 32;
        ByteArrayInputStream is = new ByteArrayInputStream(compressed);
        GZIPInputStream gis = new GZIPInputStream(is, BUFFER_SIZE);
        StringBuilder string = new StringBuilder();
        byte[] data = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = gis.read(data)) != -1) {
            string.append(new String(data, 0, bytesRead));
        }
        gis.close();
        is.close();
        return string.toString();
    }

}


