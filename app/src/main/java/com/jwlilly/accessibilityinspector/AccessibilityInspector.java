package com.jwlilly.accessibilityinspector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import com.google.android.accessibility.utils.TreeDebug;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;


public class AccessibilityInspector extends AccessibilityService implements InspectorServer.CaptureListener {
    private final String LOG_TAG = "AccessibilityInspector";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        InspectorServer.getInstance().clearCaptureListener(this);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
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
        InspectorServer.getInstance().setCaptureListener(this);
    }

    public Context getContext() {
        return this.getApplicationContext();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        InspectorServer.getInstance().clearCaptureListener(this);
        return super.onUnbind(intent);
    }

    /** Called from the websocket thread; window content must be read on the main thread. */
    @Override
    public void onCaptureRequested(boolean includeNotImportant) {
        mainHandler.post(() -> {
            if (includeNotImportant) {
                showNotImportant();
            } else {
                hideNotImportant();
            }
            startCapture();
        });
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

    public void sendAnnouncement(String announcement) {
        try {
            JSONObject announcementJson = new JSONObject();
            announcementJson.put("type", "announcement");
            announcementJson.put("announcement", announcement);
            InspectorServer.getInstance().broadcast(announcementJson);
            Log.d(LOG_TAG, "announcement sent");
        } catch (JSONException e) {
            Log.e(LOG_TAG, "could not send announcement", e);
        }
    }

    public void startCapture() {
        List<AccessibilityWindowInfo> windows = getWindows();
        try {
            JSONObject tree = TreeDebug.buildNodeTrees(windows, this);
            tree.put("type", "tree");
            tree.put("name", "");
            InspectorServer.getInstance().broadcast(tree);
            Log.d(LOG_TAG, "tree sent");
        } catch (Exception e) {
            Log.e(LOG_TAG, "capture failed", e);
            InspectorServer.getInstance().broadcast(InspectorServer.errorMessage("Capture failed: " + e.getMessage()));
        }
    }
}
