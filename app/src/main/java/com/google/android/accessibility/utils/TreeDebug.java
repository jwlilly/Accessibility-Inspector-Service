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

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.jwlilly.accessibilityinspector.AccessibilityInspector;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Util class to help debug Node trees. */
public class TreeDebug {

  public static final String TAG = "TreeDebug";
  /** Logs the layout hierarchy of node trees for given list of windows. */
  public static void logNodeTrees(List<AccessibilityWindowInfo> windows, AccessibilityInspector receiver) {
    JSONObject parentObject = new JSONObject();
    if (windows == null) {
      return;
    }
    //Log.v(TAG, "------------Node tree------------");
    JSONArray windowArray = new JSONArray();
    for (AccessibilityWindowInfo window : windows) {
      JSONObject windowObject = new JSONObject();
      if (window == null) {
        continue;
      }

      // TODO: Filter and print useful window information.
//      Log.v(TAG, "Window: " + window);

      try {
        windowObject.put("windowId", window.getId());
        windowObject.put("role", "Window");
        windowObject.put("title", window.getTitle());
      } catch (JSONException e) {
        Log.e("JSON Error", e.getMessage());
      }
      AccessibilityNodeInfoCompat root =
          AccessibilityNodeInfoUtils.toCompat(AccessibilityWindowInfoUtils.getRoot(window));
      logNodeTree(root, windowObject);
      try {
        Rect rect = new Rect();
        root.getBoundsInScreen(rect);
        windowObject.put("x1", rect.left);
        windowObject.put("y1", rect.top);
        windowObject.put("x2", rect.right);
        windowObject.put("y2", rect.bottom);
        windowObject.put("id", root.hashCode());
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
    try {
      parentObject.put("children", windowArray);
//      String json = parentObject.toString();
//      for(int i = 0; i < json.length(); i += 1024) {
//        Log.d("DebugJSON", json.substring(i, Math.min(i + 1024, json.length())));
//      }
      receiver.sendJSON(parentObject);
      parentObject = new JSONObject();
    } catch (JSONException e) {
      Log.e("JSON Error", e.getMessage());
    }
  }

  /** Logs the layout hierarchy of node tree for using the input node as the root. */
  public static void logNodeTree(@Nullable AccessibilityNodeInfoCompat node, JSONObject windowObject) {
    if (node == null) {
      return;
    }

    HashSet<AccessibilityNodeInfoCompat> seen = new HashSet<>();
    HashSet<AccessibilityNodeInfoCompat> seenJson = new HashSet<>();
    AccessibilityNodeInfoCompat compatNode = AccessibilityNodeInfoCompat.obtain(node);
    logNodeTree(compatNode, "", seen);
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
      jsonObject.put("id", node.hashCode());
      //jsonObject.put("resourceId", node.getViewIdResourceName());
      if (node.getClassName() != null) {
        if (node.getRoleDescription() != null) {
          jsonObject.put("role", getSimpleName(node.getClassName()) + " (" + node.getRoleDescription() + ")");
        } else {
          jsonObject.put("role", getSimpleName(node.getClassName()));
        }
      } else {
        jsonObject.put("role", "??");
      }

      if (!node.isVisibleToUser()) {
        jsonObject.put("visibility", "invisible");
      }

      Rect rect = new Rect();
      node.getBoundsInScreen(rect);
      jsonObject.put("x1", rect.left);
      jsonObject.put("y1", rect.top);
      jsonObject.put("x2", rect.right);
      jsonObject.put("y2", rect.bottom);

      if (!TextUtils.isEmpty(node.getPaneTitle())) {
        jsonObject.put("paneTitle", node.getPaneTitle());
      }
      List<AccessibilityNodeInfoUtils.ClickableString> clickableStrings = AccessibilityNodeInfoUtils.getNodeClickableStrings(node);
      if(clickableStrings.size() > 0) {
        JSONArray jsonArray = new JSONArray();
        for(AccessibilityNodeInfoUtils.ClickableString clickableString : clickableStrings) {
          jsonArray.put(clickableString.string());
          //List<Rect> rects = AccessibilityNodeInfoUtils.getTextLocations(node, 0, node.getText().length() - 1);
          //Log.d("AccessibilityInspector", rects.toString());
        }
        jsonObject.put("links", jsonArray);
      }
      @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
      if (nodeText != null) {

        jsonObject.put("text", nodeText.toString().trim());
      }
      if(node.getLabeledBy() != null) {
        @Nullable CharSequence labeledByText = AccessibilityNodeInfoUtils.getText(node.getLabeledBy());
        if(node.getLabeledBy().getContentDescription() != null) {
          String labeledByContent = node.getLabeledBy().getContentDescription().toString().trim();
          if (labeledByContent.length() > 0) {
            labeledByText = labeledByContent;
          }
        }
        jsonObject.put("labeledBy", labeledByText);
        jsonObject.put("labeledById", node.getLabeledBy().hashCode());
      }
      if (node.getHintText() != null) {
        jsonObject.put("hint", node.getHintText().toString().trim());
      }
      if (node.getContentDescription() != null) {
        jsonObject.put("content", node.getContentDescription().toString().trim());
      }

      if (AccessibilityNodeInfoUtils.getState(node) != null) {
        if(node.getStateDescription() != null) {
          jsonObject.put("state", AccessibilityNodeInfoUtils.getState(node).toString().trim() + " (" + node.getStateDescription() + ")");
        } else {
          jsonObject.put("state", AccessibilityNodeInfoUtils.getState(node).toString().trim());
        }
      }
      // Views that inherit Checkable can have its own state description and the log already covered
      // by above SD, but for some views that are not Checkable but have checked status, like
      // overriding by AccessibilityDelegate, we should also log it.
      if (node.isCheckable()) {
        if (node.isChecked()) {
          jsonObject.put("checkable", "checked");
        } else {
          jsonObject.put("checkable", "not checked");
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
            actionText = actionText + " (" + action.getLabel() + ")";
          }
          if(actionText.length() > 0) {
            stringArray.put(actionText);
          }
        }
        if (stringArray.length() > 0) {
          jsonObject.put("actions", stringArray);
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
        jsonObject.put("properties", properties);
      }

      if (node.getCollectionInfo() != null) {
        jsonObject.put("collectionInfo", "Rows: " + node.getCollectionInfo().getRowCount() + ", Columns: " + node.getCollectionInfo().getColumnCount());
      }

      if (AccessibilityNodeInfoUtils.isHeading(node)) {
        jsonObject.put("heading", true);
      }
      if (node.getCollectionItemInfo() != null) {
        jsonObject.put("collectionItemInfo", "Row: " + node.getCollectionItemInfo().getRowIndex() + ", Column: " + node.getCollectionItemInfo().getColumnIndex());
      }

      return jsonObject;
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage());
      return null;
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
}
