package com.example.smartkid.common.util;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SafeJson {
    private SafeJson() {
    }

    public static String string(JSONObject object, String defaultValue, String... keys) {
        if (object == null || keys == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (key == null || object.isNull(key)) {
                continue;
            }
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }

    public static int integer(JSONObject object, int defaultValue, String... keys) {
        if (object == null || keys == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (key == null || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Thử khóa kế tiếp.
            }
        }
        return defaultValue;
    }

    public static double decimal(JSONObject object, double defaultValue, String... keys) {
        if (object == null || keys == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (key == null || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Thử khóa kế tiếp.
            }
        }
        return defaultValue;
    }

    public static boolean bool(JSONObject object, boolean defaultValue, String... keys) {
        if (object == null || keys == null) {
            return defaultValue;
        }
        for (String key : keys) {
            if (key == null || object.isNull(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if ("true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value))) {
                return true;
            }
            if ("false".equalsIgnoreCase(String.valueOf(value)) || "0".equals(String.valueOf(value))) {
                return false;
            }
        }
        return defaultValue;
    }

    @Nullable
    public static JSONObject object(JSONObject parent, String key) {
        return parent == null ? null : parent.optJSONObject(key);
    }

    public static JSONArray array(JSONObject parent, String... keys) {
        if (parent == null || keys == null) {
            return new JSONArray();
        }
        for (String key : keys) {
            if (key == null) continue;
            JSONArray value = parent.optJSONArray(key);
            if (value != null) return value;
        }
        return new JSONArray();
    }
}
