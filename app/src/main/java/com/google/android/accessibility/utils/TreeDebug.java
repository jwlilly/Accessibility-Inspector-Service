/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.jwlilly.accessibilityinspector.AccessibilityInspector;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Util class to help debug Node trees. */
public class TreeDebug {
  private static AccessibilityInspector inspector = null;
  public static final String TAG = "TreeDebug";
  /** Ids only need to be unique within one capture; 0 is reserved for the tree root. */
  private static int nextNodeId = 1;

  /** Builds the JSON layout hierarchy of node trees for the given list of windows. */
  public static JSONObject buildNodeTrees(@Nullable List<AccessibilityWindowInfo> windows, AccessibilityInspector receiver) throws JSONException {
    inspector = receiver;
    nextNodeId = 1;
    JSONObject parentObject = new JSONObject();
    if (windows == null) {
      parentObject.put("children", new JSONArray());
      return parentObject;
    }
    //Log.v(TAG, "------------Node tree------------");
    JSONArray windowArray = new JSONArray();
    for (AccessibilityWindowInfo window : windows) {
      JSONObject windowObject = new JSONObject();
      if (window == null || !window.isActive()) {
        continue;
      }


      // TODO: Filter and print useful window information.
//      Log.v(TAG, "Window: " + window);
      JSONObject metadata = new JSONObject();
      try {

        metadata.put("windowId", window.getId());
        metadata.put("role", "Window");
        windowObject.put("name", "Window");
        metadata.put("title", window.getTitle());

      } catch (JSONException e) {
        Log.e("JSON Error", e.getMessage());
      }
      AccessibilityNodeInfo rootInfo = window.getRoot();
      if (rootInfo == null) {
        continue;
      }
      AccessibilityNodeInfoCompat root = AccessibilityNodeInfoCompat.wrap(rootInfo);
      try {
        windowObject.put("id", nextNodeId++);
      } catch (JSONException e) {
        Log.e("AccessibilityInspector", e.getMessage());
      }
      logNodeTree(root, windowObject);
      try {
        Rect rect = new Rect();
        root.getBoundsInScreen(rect);
        metadata.put("x1", rect.left);
        metadata.put("y1", rect.top);
        metadata.put("x2", rect.right);
        metadata.put("y2", rect.bottom);
        windowObject.put("metadata", metadata);
        AccessibilityNodeInfoUtils.recycleNodes(root);
      } catch (JSONException e) {
        Log.e("AccessibilityInspector", e.getMessage());
      }
      String title = "";
      if(window.getTitle() != null) {
        title = window.getTitle().toString();
      }

      boolean isEmpty = false;
      try {
        isEmpty = windowObject.getJSONArray("children").length() == 0;
        if(!isEmpty) {
          JSONArray children = windowObject.getJSONArray("children");
          if(children.get(0) != null) {
            JSONObject object = children.getJSONObject(0);
            if(object.has("paneTitle")) {
              isEmpty = object.getString("paneTitle").equals("Status bar") || object.getString("paneTitle").equals("Notification shade.");
            }
          }
        }
      } catch (JSONException e) {
        isEmpty = true;
      }

      if(!title.equals("Navigation bar") && !isEmpty){
        windowArray.put(windowObject);
      }
    }
    parentObject.put("children", windowArray);
    parentObject.put("sdkInt", Build.VERSION.SDK_INT);
    return parentObject;
  }

  /** Logs the layout hierarchy of node tree for using the input node as the root. */
  public static void logNodeTree(@Nullable AccessibilityNodeInfoCompat node, JSONObject windowObject) {
    if (node == null) {
      return;
    }

    HashSet<AccessibilityNodeInfoCompat> seen = new HashSet<>();
    HashSet<AccessibilityNodeInfoCompat> seenJson = new HashSet<>();
    AccessibilityNodeInfoCompat compatNode = AccessibilityNodeInfoCompat.obtain(node);
    //logNodeTree(compatNode, "", seen);
    logNodeTreeJson(compatNode, windowObject, seenJson);
    for (AccessibilityNodeInfoCompat n : seen) {
      n.recycle();
    }
  }

  private static void logNodeTree(
      AccessibilityNodeInfoCompat node, String indent, HashSet<AccessibilityNodeInfoCompat> seen) {
    if (!seen.add(node)) {
//      Log.v(TAG, "Cycle: " + node.hashCode());
      return;
    }
    // Include the hash code as a "poor man's" id, knowing that it
    // might not always be unique.
//    Log.v(TAG, indent + "(" + node.hashCode() + ")" + nodeDebugDescription(node));

    indent += "  ";
    int childCount = node.getChildCount();

    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
//        Log.v(TAG, indent + "Couldn't get child " + i);
        continue;
      }

      logNodeTree(child, indent, seen);
    }
  }
  private static void logNodeTreeJson(
          AccessibilityNodeInfoCompat node, JSONObject parent, HashSet<AccessibilityNodeInfoCompat> seen) {
    if (!seen.add(node)) {
//      Log.v(TAG, "Cycle: " + node.hashCode());
      return;
    }
    JSONArray childArray = new JSONArray();
    // Include the hash code as a "poor man's" id, knowing that it
    // might not always be unique.

    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; ++i) {
      JSONObject childObject  = new JSONObject();
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (child == null) {
        continue;
      }
      childArray.put(nodeDebugDescriptionJson(child, childObject));
      logNodeTreeJson(child, childObject, seen);
    }
    try{
      if(childArray.length() > 0) {
        parent.put("children", childArray);
      }
    } catch (JSONException e) {
      Log.e("JSON Error", e.getMessage());
    }
  }

  private static void appendSimpleName(StringBuilder sb, CharSequence fullName) {
    int dotIndex = TextUtils.lastIndexOf(fullName, '.');
    if (dotIndex < 0) {
      dotIndex = 0;
    }

    sb.append(fullName, dotIndex, fullName.length());
  }

  private static String getSimpleName(CharSequence fullName) {
    int dotIndex = TextUtils.lastIndexOf(fullName, '.');
    if (dotIndex < 0) {
      dotIndex = 0;
    }

    return fullName.subSequence(dotIndex, fullName.length()).toString().replace(".", "");
  }

  /** Gets a description of the properties of a node. */
  public static CharSequence nodeDebugDescription(AccessibilityNodeInfoCompat node) {
    StringBuilder sb = new StringBuilder();

    sb.append(node.getWindowId());

    if (node.getClassName() != null) {
      if (node.getRoleDescription() != null) {
        appendSimpleName(sb, node.getClassName() + " (" + node.getRoleDescription() + ")");

      } else {
        appendSimpleName(sb, node.getClassName());

      }
    } else {
      sb.append("??");

    }

    if (!node.isVisibleToUser()) {
      sb.append(":invisible");

    }

    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    sb.append(":");
    sb.append("(")
            .append(rect.left)
            .append(", ")
            .append(rect.top)
            .append(" - ")
            .append(rect.right)
            .append(", ")
            .append(rect.bottom)
            .append(")");


    if (!TextUtils.isEmpty(node.getPaneTitle())) {
      sb.append(":PANE{");
      sb.append(node.getPaneTitle());
      sb.append("}");

    }

    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    if (nodeText != null) {
      sb.append(":TEXT{");
      sb.append(nodeText.toString().trim());
      sb.append("}");

    }

    if (node.getContentDescription() != null) {
      sb.append(":CONTENT{");
      sb.append(node.getContentDescription().toString().trim());
      sb.append("}");

    }

    if (AccessibilityNodeInfoUtils.getState(node) != null) {
      sb.append(":STATE{");
      sb.append(AccessibilityNodeInfoUtils.getState(node).toString().trim());
      sb.append("}");
    }
    // Views that inherit Checkable can have its own state description and the log already covered
    // by above SD, but for some views that are not Checkable but have checked status, like
    // overriding by AccessibilityDelegate, we should also log it.
    if (node.isCheckable()) {
      sb.append(":");
      if (node.isChecked()) {
        sb.append("checked");
      } else {
        sb.append("not checked");
      }

    }

    int actions = node.getActions();
    if (actions != 0) {

      sb.append("(action:");
      if ((actions & AccessibilityNodeInfoCompat.ACTION_FOCUS) != 0) {
        sb.append("FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("CLEAR_A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) != 0) {
        sb.append("SCROLL_BACKWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) != 0) {
        sb.append("SCROLL_FORWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLICK) != 0) {
        sb.append("CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) != 0) {
        sb.append("LONG_CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_EXPAND) != 0) {
        sb.append("EXPAND/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_COLLAPSE) != 0) {
        sb.append("COLLAPSE/");
      }
      sb.setLength(sb.length() - 1);
      sb.append(")");
    }
    if (node.isFocusable()) {
      sb.append(":focusable");
    }
    if (node.isScreenReaderFocusable()) {
      sb.append(":screenReaderfocusable");
    }

    if (node.isFocused()) {
      sb.append(":focused");
    }

    if (node.isSelected()) {
      sb.append(":selected");
    }

    if (node.isScrollable()) {
      sb.append(":scrollable");
    }

    if (node.isClickable()) {
      sb.append(":clickable");
    }

    if (node.isLongClickable()) {
      sb.append(":longClickable");
    }

    if (node.isAccessibilityFocused()) {
      sb.append(":accessibilityFocused");
    }
    if (AccessibilityNodeInfoUtils.supportsTextLocation(node)) {
      sb.append(":supportsTextLocation");
    }
    if (!node.isEnabled()) {
      sb.append(":disabled");
    }

    if (node.getCollectionInfo() != null) {
      sb.append(":collection");
      sb.append("#R");
      sb.append(node.getCollectionInfo().getRowCount());
      sb.append("C");
      sb.append(node.getCollectionInfo().getColumnCount());
    }

    if (AccessibilityNodeInfoUtils.isHeading(node)) {
      sb.append(":heading");
    } else if (node.getCollectionItemInfo() != null) {
      sb.append(":item");
    }
    if (node.getCollectionItemInfo() != null) {
      sb.append("#r");
      sb.append(node.getCollectionItemInfo().getRowIndex());
      sb.append("c");
      sb.append(node.getCollectionItemInfo().getColumnIndex());
    }

    return sb.toString().replace("\n", "").replace("\r", "");
  }

  /** Gets a description of the properties of a node. */
  public static JSONObject nodeDebugDescriptionJson(AccessibilityNodeInfoCompat node, JSONObject childObject) {
    try {
      JSONObject jsonObject = childObject;
      JSONObject metadata = new JSONObject();
      jsonObject.put("id", nextNodeId++);
      metadata.put("hashCode", node.hashCode());
      if (node.getViewIdResourceName() != null) {
        metadata.put("resourceId", node.getViewIdResourceName());
      }
      if (node.getClassName() != null) {
        if (node.getRoleDescription() != null) {
          metadata.put("roleDescription", node.getRoleDescription());
        }
        metadata.put("role", getSimpleName(node.getClassName()));
        jsonObject.put("name", getSimpleName(node.getClassName()));
      } else {
        metadata.put("role", "??");
        jsonObject.put("name", "??");
      }

      if(node.isContentInvalid()) {
        metadata.put("contentInvalid", true);
      }

      if(node.getError() != null) {
        metadata.put("errorMessage", node.getError());
      }

      if(node.getTooltipText() != null) {
        metadata.put("tooltip", node.getTooltipText());
      }

      if (!node.isVisibleToUser()) {
        metadata.put("visibility", "invisible");
      }


      metadata.put("importantForAccessibility", node.isImportantForAccessibility());


      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      metadata.put("x1", rect.left);
      metadata.put("y1", rect.top);
      metadata.put("x2", rect.right);
      metadata.put("y2", rect.bottom);



      node.getBoundsInScreen(rect);

      int width = rect.right - rect.left;
      int height = rect.bottom - rect.top;


      float scaledWidth = convertPixelsToDp(width, inspector.getContext());

      float scaledHeight = convertPixelsToDp(height, inspector.getContext());

      DecimalFormat decimalFormat = new DecimalFormat("#0.00");

      metadata.put("scaledWidth", decimalFormat.format(scaledWidth));
      metadata.put("scaledHeight", decimalFormat.format(scaledHeight));

      metadata.put("dpScaleFactor", convertPixelsToDp(1, inspector.getContext()));

      if (!TextUtils.isEmpty(node.getPaneTitle())) {
        metadata.put("paneTitle", node.getPaneTitle());
      }
      List<AccessibilityNodeInfoUtils.ClickableString> clickableStrings = AccessibilityNodeInfoUtils.getNodeClickableStrings(node);
      if(clickableStrings.size() > 0) {
        JSONArray jsonArray = new JSONArray();
        for(AccessibilityNodeInfoUtils.ClickableString clickableString : clickableStrings) {
          jsonArray.put(clickableString.string());
        }
        metadata.put("links", jsonArray);
      }
      List<AccessibilityNodeInfoUtils.LocaleString> localeStrings = AccessibilityNodeInfoUtils.getNodeLocaleStrings(node);
      if(localeStrings.size() > 0) {
        JSONArray jsonArray = new JSONArray();
        for(AccessibilityNodeInfoUtils.LocaleString localeString : localeStrings) {
          Log.d("TAG", "Locale " + localeString.localeSpan());
          jsonArray.put(localeString.string() + " - " + Objects.requireNonNull(localeString.localeSpan().getLocale()).toLanguageTag());
        }
        metadata.put("locales", jsonArray);
      }
      @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
      if (nodeText != null) {

        metadata.put("text", nodeText.toString().trim());
      }
      if(node.getLabeledBy() != null) {
        @Nullable CharSequence labeledByText = AccessibilityNodeInfoUtils.getText(node.getLabeledBy());
        if(node.getLabeledBy().getContentDescription() != null) {
          String labeledByContent = node.getLabeledBy().getContentDescription().toString().trim();
          if (labeledByContent.length() > 0) {
            labeledByText = labeledByContent;
          }
        }
        metadata.put("labeledBy", labeledByText);
        metadata.put("labeledById", node.getLabeledBy().hashCode());
      }
      if (node.getHintText() != null) {
        metadata.put("hint", node.getHintText().toString().trim());
      }
      if (node.getContentDescription() != null) {
        metadata.put("content", node.getContentDescription().toString().trim());
      }

      if (AccessibilityNodeInfoUtils.getState(node) != null) {
        if(node.getStateDescription() != null) {
          metadata.put("stateDescription", node.getStateDescription().toString().trim());
        } else {
          metadata.put("state", AccessibilityNodeInfoUtils.getState(node).toString().trim());
        }
      }
      // Views that inherit Checkable can have its own state description and the log already covered
      // by above SD, but for some views that are not Checkable but have checked status, like
      // overriding by AccessibilityDelegate, we should also log it.
      if (node.isCheckable()) {
        if (node.isChecked()) {
          metadata.put("checkable", "checked");
        } else {
          metadata.put("checkable", "not checked");
        }

      }

      int actions = node.getActions();
      if (actions != 0) {
        JSONArray stringArray = new JSONArray();
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actionList = node.getActionList();
        for(int i = 0; i < actionList.size(); i++) {
          AccessibilityNodeInfoCompat.AccessibilityActionCompat action = actionList.get(i);
          String actionText = "";
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_FOCUS) {
            actionText = "focus";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) {
            actionText = "a11y focus";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
            actionText = "clear a11y focus";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
            actionText = "scroll backward";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
            actionText = "scroll forward";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK) {
            actionText = "click";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) {
            actionText = "long click";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
            actionText = "expand";
          }
          if (action.getId() == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
            actionText = "collapse";
          }
          if(action.getLabel() != null && action.getLabel().length() > 0) {
            actionText = action.getLabel() + " (custom)";
          }
          if(actionText.length() > 0) {
            stringArray.put(actionText);
          }
        }
        if (stringArray.length() > 0) {
          metadata.put("actions", stringArray);
        }
      }
      JSONArray properties = new JSONArray();
      if (node.isFocusable()) {
        properties.put("focusable");
      }
      if (node.isScreenReaderFocusable()) {
        properties.put("screen reader focusable");
      }

      if (node.isFocused()) {
        properties.put("focused");
      }

      if (node.isSelected()) {
        properties.put("selected");
      }

      if (node.isScrollable()) {
        properties.put("scrollable");
      }

      if (node.isClickable()) {
        properties.put("clickable");
      }

      if (node.isLongClickable()) {
        properties.put("long clickable");
      }

      if (node.isAccessibilityFocused()) {
        properties.put("accessibility focused");
      }
      if (AccessibilityNodeInfoUtils.supportsTextLocation(node)) {
        //properties.put("supportsTextLocation");
      }
      if (!node.isEnabled()) {
        properties.put("disabled");
      }
      if(properties.length() > 0) {
        metadata.put("properties", properties);
      }

      if (node.getCollectionInfo() != null) {
        metadata.put("collectionInfo", "Rows: " + node.getCollectionInfo().getRowCount() + ", Columns: " + node.getCollectionInfo().getColumnCount());
      }

      if (AccessibilityNodeInfoUtils.isHeading(node)) {
        metadata.put("heading", true);
      }
      if (node.getCollectionItemInfo() != null) {
        metadata.put("collectionItemInfo", "Row: " + node.getCollectionItemInfo().getRowIndex() + ", Column: " + node.getCollectionItemInfo().getColumnIndex());
      }
      putRecentApiProperties(node, metadata);
      jsonObject.put("metadata", metadata);
      return jsonObject;
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage());
      return null;
    }
  }

  /**
   * Adds AccessibilityNodeInfo properties introduced in API 33-36. Properties the device's API level
   * doesn't support are left out so the inspector can tell "unsupported" apart from "false".
   */
  private static void putRecentApiProperties(AccessibilityNodeInfoCompat node, JSONObject metadata)
      throws JSONException {
    AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      metadata.put("textSelectable", info.isTextSelectable());
      if (info.getUniqueId() != null) {
        metadata.put("uniqueId", info.getUniqueId());
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      metadata.put("accessibilityDataSensitive", info.isAccessibilityDataSensitive());
      metadata.put("requestInitialAccessibilityFocus", info.hasRequestInitialAccessibilityFocus());
      if (!TextUtils.isEmpty(info.getContainerTitle())) {
        metadata.put("containerTitle", info.getContainerTitle().toString().trim());
      }
      Duration minDuration = info.getMinDurationBetweenContentChanges();
      if (minDuration != null && !minDuration.isZero()) {
        metadata.put("minDurationBetweenContentChanges", minDuration.toMillis());
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      metadata.put("granularScrollingSupported", info.isGranularScrollingSupported());
      AccessibilityNodeInfo.CollectionInfo collectionInfo = info.getCollectionInfo();
      if (collectionInfo != null) {
        if (collectionInfo.getItemCount() != AccessibilityNodeInfo.CollectionInfo.UNDEFINED) {
          metadata.put("collectionItemCount", collectionInfo.getItemCount());
        }
        if (collectionInfo.getImportantForAccessibilityItemCount()
            != AccessibilityNodeInfo.CollectionInfo.UNDEFINED) {
          metadata.put(
              "collectionImportantItemCount",
              collectionInfo.getImportantForAccessibilityItemCount());
        }
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
      metadata.put("fieldRequired", info.isFieldRequired());
      if (info.isCheckable()) {
        metadata.put("checkedState", checkedStateName(info.getChecked()));
      }
      if (info.getExpandedState() != AccessibilityNodeInfo.EXPANDED_STATE_UNDEFINED) {
        metadata.put("expandedState", expandedStateName(info.getExpandedState()));
      }
      if (!TextUtils.isEmpty(info.getSupplementalDescription())) {
        metadata.put(
            "supplementalDescription", info.getSupplementalDescription().toString().trim());
      }
      List<AccessibilityNodeInfo> labels = info.getLabeledByList();
      if (labels.size() > 1) {
        JSONArray labelArray = new JSONArray();
        for (AccessibilityNodeInfo label : labels) {
          CharSequence labelText =
              !TextUtils.isEmpty(label.getContentDescription())
                  ? label.getContentDescription()
                  : label.getText();
          if (!TextUtils.isEmpty(labelText)) {
            labelArray.put(labelText.toString().trim());
          }
        }
        metadata.put("labeledByList", labelArray);
      }
    }
  }

  private static String checkedStateName(int checkedState) {
    switch (checkedState) {
      case AccessibilityNodeInfo.CHECKED_STATE_TRUE:
        return "checked";
      case AccessibilityNodeInfo.CHECKED_STATE_PARTIAL:
        return "partial";
      default:
        return "not checked";
    }
  }

  private static String expandedStateName(int expandedState) {
    switch (expandedState) {
      case AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED:
        return "collapsed";
      case AccessibilityNodeInfo.EXPANDED_STATE_PARTIAL:
        return "partial";
      case AccessibilityNodeInfo.EXPANDED_STATE_FULL:
        return "full";
      default:
        return "undefined";
    }
  }

  /** Logs the traversal order of node trees for given list of windows. */
  public static void logOrderedTraversalTree(List<AccessibilityWindowInfo> windows) {
    if (windows == null) {
      return;
    }
//    Log.v(TAG, "------------Node tree traversal order------------");
    for (AccessibilityWindowInfo window : windows) {
      if (window == null) {
        continue;
      }
//      Log.v(TreeDebug.TAG, "Window: " + window);
      AccessibilityNodeInfoCompat root =
          AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(window));
      logOrderedTraversalTree(root);
      AccessibilityNodeInfoUtils.recycleNodes(root);
    }
  }

  /** Logs the traversal order of node tree for using the input node as the root. */
  private static void logOrderedTraversalTree(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return;
    }
    OrderedTraversalStrategy orderTraversalStrategy = new OrderedTraversalStrategy(node);
    orderTraversalStrategy.dumpTree();
    orderTraversalStrategy.recycle();
  }

  public static float convertPixelsToDp(float px, Context context){
    return px / ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
  }
}
