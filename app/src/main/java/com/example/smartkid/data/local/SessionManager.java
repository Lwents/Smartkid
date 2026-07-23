package com.example.smartkid.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.model.User;

public class SessionManager {
    private static final String PREF_NAME = "smartkid_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_CLASS_NAME = "class_name";

    private final Context appContext;
    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context không được để trống");
        }
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveSession(String accessToken, String refreshToken, User user) {
        try {
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(KEY_ACCESS, safe(accessToken))
                    .putString(KEY_REFRESH, safe(refreshToken));
            writeUser(editor, user);
            editor.apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể lưu phiên đăng nhập", exception);
        }
    }

    public synchronized void updateAccessToken(String accessToken) {
        try {
            preferences.edit().putString(KEY_ACCESS, safe(accessToken)).apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể cập nhật access token", exception);
        }
    }

    public synchronized void updateUser(User user) {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            writeUser(editor, user);
            editor.apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể cập nhật người dùng", exception);
        }
    }

    public boolean hasSession() {
        return !getAccessToken().isEmpty() && !getRefreshToken().isEmpty();
    }

    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS, "");
    }

    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH, "");
    }

    public User getUser() {
        return new User(
                preferences.getString(KEY_USER_ID, ""),
                preferences.getString(KEY_USERNAME, ""),
                preferences.getString(KEY_FULL_NAME, ""),
                preferences.getString(KEY_EMAIL, ""),
                preferences.getString(KEY_ROLE, "student"),
                preferences.getString(KEY_CLASS_NAME, "")
        );
    }

    public synchronized void clear() {
        try {
            preferences.edit().clear().apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể xóa phiên đăng nhập", exception);
        }
    }

    private void writeUser(SharedPreferences.Editor editor, User user) {
        User safeUser = user == null ? new User("", "", "", "", "student", "") : user;
        editor.putString(KEY_USER_ID, safeUser.getId())
                .putString(KEY_USERNAME, safeUser.getUsername())
                .putString(KEY_FULL_NAME, safeUser.getFullName())
                .putString(KEY_EMAIL, safeUser.getEmail())
                .putString(KEY_ROLE, safeUser.getRole())
                .putString(KEY_CLASS_NAME, safeUser.getClassName());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
