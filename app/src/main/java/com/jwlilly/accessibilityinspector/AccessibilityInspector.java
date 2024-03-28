package com.jwlilly.accessibilityinspector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.accessibility.utils.TreeDebug;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;


public class AccessibilityInspector extends AccessibilityService implements Observer {
    private String LOG_TAG = "AccessibilityInspector";
    private AccessibilityListener captureListener;

    private AccessibilityListener importantListener;
    public AccessibilityInspector _this = this;
    private JSONObject jsonObject;
    private String screenshot = "";
    private long eventTime = 0;
    private int eventTimeout = 1000;

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
                | AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT
                | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
        info.eventTypes = AccessibilityEvent.TYPE_ANNOUNCEMENT;
        this.setServiceInfo(info);
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
                startCapture();
            } else if(intent.getAction().equalsIgnoreCase("A11yInspectorImportant")) {
                toggleShowNotImportant();
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
    public void sendWithoutScreenshot() {
        try {
            Intent announcementIntent = new Intent(SocketService.BROADCAST_MESSAGE, null, this, SocketService.class);
            SocketService.data = compress(jsonObject.toString());
            startService(announcementIntent);
            Log.d(LOG_TAG, "message sent");
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }

    public void toggleShowNotImportant() {
        int flags = this.getServiceInfo().flags;
        int allFlags = AccessibilityServiceInfo.DEFAULT
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FEEDBACK_GENERIC
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT
                | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        if(flags == allFlags) {
            AccessibilityServiceInfo info = this.getServiceInfo();
            info.flags = AccessibilityServiceInfo.DEFAULT
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FEEDBACK_GENERIC
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT
                    | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
            this.setServiceInfo(info);
            Log.d("INSPECTOR", "hide not important views");
        } else {
            AccessibilityServiceInfo info = this.getServiceInfo();
            info.flags = allFlags;
            this.setServiceInfo(info);
            Log.d("INSPECTOR", "show not important views");
        }
    }
    public void sendWithScreenshot() {
        try {
            JSONObject combinedJson = new JSONObject();
            //combinedJson.put("screenshot", screenshot);
            combinedJson.putOpt("views", jsonObject);
            Intent announcementIntent = new Intent(SocketService.BROADCAST_MESSAGE, null, this, SocketService.class);
            SocketService.data = compress(combinedJson.toString());
            startService(announcementIntent);
            Log.d(LOG_TAG, "message sent");
            screenshot = "";
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
            screenshot = "";
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
        sendWithScreenshot();
//        takeScreenshot(Display.DEFAULT_DISPLAY,
//                getApplicationContext().getMainExecutor(), new TakeScreenshotCallback() {
//                    @RequiresApi(api = Build.VERSION_CODES.R)
//                    @Override
//                    public void onSuccess(@NonNull ScreenshotResult screenshotResult) {
//
//                        Log.i("ScreenShotResult","onSuccess");
//                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(),screenshotResult.getColorSpace());
//                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 50, byteArrayOutputStream);
//                        byte[] byteArray = byteArrayOutputStream.toByteArray();
//                        String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);
//                        screenshot = encoded;
//                        sendWithScreenshot();
//                    }
//
//                    @Override
//                    public void onFailure(int i) {
//                        Log.i("ScreenShotResult","onFailure code is "+ i);
//                        sendWithoutScreenshot();
//                    }
//                });
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
