package com.jwlilly.accessibilityinspector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.accessibility.utils.TreeDebug;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;


public class AccessibilityInspector extends AccessibilityService implements Observer {
    private final String LOG_TAG = "AccessibilityInspector";
    private AccessibilityListener captureListener;

    private AccessibilityListener importantListener;
    public AccessibilityInspector _this = this;
    private JSONObject jsonObject;

    private final int allFlags = AccessibilityServiceInfo.DEFAULT
        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        | AccessibilityServiceInfo.FEEDBACK_GENERIC
        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT
        | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
            List<CharSequence> list = event.getText();
            for(CharSequence charSequence : list) {
                if(charSequence.toString().trim().length() > 0) {
                    sendAnnouncement(charSequence.toString());
                }
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        Log.d("ServerSocket", "stopping server");
        super.onDestroy();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        captureListener = new AccessibilityListener();
        registerReceiver(captureListener, new IntentFilter("A11yInspector"));
        importantListener = new AccessibilityListener();
        registerReceiver(importantListener, new IntentFilter("A11yInspectorImportant"));
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.notificationTimeout = 100;
        info.flags =
                AccessibilityServiceInfo.DEFAULT
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FEEDBACK_GENERIC
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
        info.eventTypes = AccessibilityEvent.TYPE_ANNOUNCEMENT;
        this.setServiceInfo(info);
    }

    public Context getContext() {
        return this.getApplicationContext();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(captureListener);
        unregisterReceiver(importantListener);
        return super.onUnbind(intent);
    }

    @Override
    public void update(Observable observable, Object o) {

    }

    private class AccessibilityListener extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equalsIgnoreCase("A11yInspector")) {
                hideNotImportant();
                startCapture();
            } else if(intent.getAction().equalsIgnoreCase("A11yInspectorImportant")) {
                showNotImportant();
                startCapture();
            }
        }
    }
    @Override
    public void takeScreenshot(int displayId, @NonNull Executor executor, @NonNull TakeScreenshotCallback callback) {
        super.takeScreenshot(displayId, executor, callback);
    }
    public void sendJSON(JSONObject object) {
        jsonObject = object;
    }

    public void hideNotImportant() {
        int flags = this.getServiceInfo().flags;
        AccessibilityServiceInfo info = this.getServiceInfo();

        if (flags == allFlags) {
            info.flags = AccessibilityServiceInfo.DEFAULT
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FEEDBACK_GENERIC
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
            this.setServiceInfo(info);
        }
    }

    public void showNotImportant() {
        int flags = this.getServiceInfo().flags;
        if (flags != allFlags) {
            AccessibilityServiceInfo info = this.getServiceInfo();
            info.flags = allFlags;
            this.setServiceInfo(info);
        }

    }

    public void sendTree() {
        try {
            jsonObject.put("name", "");
            Intent announcementIntent = new Intent(SocketService.BROADCAST_MESSAGE, null, this, SocketService.class);
            SocketService.data = compress(jsonObject.toString());
            startService(announcementIntent);
            Log.d(LOG_TAG, "message sent");
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }
    public void sendAnnouncement(String announcement) {
        try {
            JSONObject announcementJson = new JSONObject();
            announcementJson.put("announcement", announcement);
            Intent announcementIntent = new Intent(SocketService.BROADCAST_MESSAGE, null, this, SocketService.class);
            SocketService.data = compress(announcementJson.toString());
            startService(announcementIntent);
            Log.d(LOG_TAG, "announcement sent");
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }

    public void startCapture() {
        List<AccessibilityWindowInfo> windows = getWindows();

        TreeDebug.logNodeTrees(windows, _this);
        sendTree();
    }

    public static byte[] compress(String string) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return compressed;
    }
}
