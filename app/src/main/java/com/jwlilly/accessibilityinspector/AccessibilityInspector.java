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
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executor;



public class AccessibilityInspector extends AccessibilityService implements Observer {
    private String LOG_TAG = "AccessibilityInspector";
    private AccessibilityListener listener;
    public AccessibilityInspector _this = this;
    public SocketConnection socketConnection;
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
        socketConnection.deleteObservers();
        try {
            socketConnection.server.stop(1000);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        super.onDestroy();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        listener = new AccessibilityListener();
        registerReceiver(listener, new IntentFilter("A11yInspector"));
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
        Log.d("SocketServer", "attempting to start server");
        socketConnection = SocketConnection.getInstance();
        socketConnection.setInspector(_this);
        socketConnection.deleteObservers();
        socketConnection.addObserver(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("ServerSocket", "stopping server");
        socketConnection.deleteObservers();
        try {
            socketConnection.server.stop(1000);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
        unregisterReceiver(listener);
        return super.onUnbind(intent);
    }

    @Override
    public void update(Observable observable, Object o) {

    }

    private class AccessibilityListener extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void onReceive(Context context, Intent intent) {
            startCapture();
        }
    }
    @Override
    public void takeScreenshot(int displayId, @NonNull Executor executor, @NonNull TakeScreenshotCallback callback) {
        super.takeScreenshot(displayId, executor, callback);
    }
    public void store(Bitmap bm, String fileName) {
        final String dirPath = Environment.getExternalStorageDirectory()+ "/";
        File dir = new File(dirPath);
        if (!dir.exists())
            dir.mkdirs();
        File file = new File(dirPath, fileName);
        try {


            FileOutputStream fOut = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendJSON(JSONObject object) {
        jsonObject = object;
    }
    public void sendWithoutScreenshot() {
        try {
            socketConnection.sendData(jsonObject.toString());
            Log.d(LOG_TAG, "message sent");
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }
    public void sendWithScreenshot() {
        try {
            JSONObject combinedJson = new JSONObject();
            combinedJson.put("screenshot", screenshot);
            combinedJson.putOpt("views", jsonObject);
            socketConnection.sendData(combinedJson.toString());
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
            socketConnection.sendData(announcementJson.toString());
            Log.d(LOG_TAG, "announcement sent");
        } catch (Exception e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }

    public void startCapture() {
        List<AccessibilityWindowInfo> windows = getWindows();
        //Log.d(LOG_TAG, "intent received " + windows.size());

        TreeDebug.logNodeTrees(windows, _this);
        //TreeDebug.logOrderedTraversalTree(windows);
        takeScreenshot(Display.DEFAULT_DISPLAY,
                getApplicationContext().getMainExecutor(), new TakeScreenshotCallback() {
                    @RequiresApi(api = Build.VERSION_CODES.R)
                    @Override
                    public void onSuccess(@NonNull ScreenshotResult screenshotResult) {

                        Log.i("ScreenShotResult","onSuccess");
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.getHardwareBuffer(),screenshotResult.getColorSpace());
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                        byte[] byteArray = byteArrayOutputStream.toByteArray();
                        String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);
                        screenshot = encoded;
                        sendWithScreenshot();
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.i("ScreenShotResult","onFailure code is "+ i);
                        sendWithoutScreenshot();
                    }
                });
    }
}
