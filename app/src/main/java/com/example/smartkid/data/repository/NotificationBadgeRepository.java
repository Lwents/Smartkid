package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONArray;
import org.json.JSONObject;

/** Loads the server-backed unread count used by role dashboard bells. */
public final class NotificationBadgeRepository {
    private final ApiClient apiClient;

    public NotificationBadgeRepository(Context context) {
        apiClient = ApiClient.getInstance(context.getApplicationContext());
    }

    public void loadUnreadCount(String endpoint, ApiCallback<Integer> callback) {
        apiClient.get(endpoint, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(unreadCount(data));
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    static int unreadCount(JSONObject response) {
        JSONObject root = response == null ? new JSONObject() : response;
        if (root.has("unread_count")) {
            return Math.max(0, SafeJson.integer(root, 0, "unread_count"));
        }
        int unread = 0;
        JSONArray notifications = SafeJson.array(root, "notifications");
        for (int index = 0; index < notifications.length(); index++) {
            JSONObject item = notifications.optJSONObject(index);
            if (item != null && !SafeJson.bool(item, false, "is_read", "isRead")) unread++;
        }
        return unread;
    }
}
