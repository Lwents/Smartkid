package com.example.smartkid.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.User;

/**
 * Bộ nhớ phiên đăng nhập, lưu vào SharedPreferences của máy.
 * 
 * Giữ access token, refresh token và thông tin người dùng nên đóng app mở lại vẫn
 * còn đăng nhập. Các hàm ghi đều synchronized vì ApiClient có thể làm mới token
 * ở luồng nền cùng lúc giao diện đang đọc.
 */
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

    /** Mở vùng lưu trữ phiên của app. */
    public SessionManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context không được để trống");
        }
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Lưu phiên mới sau khi đăng nhập thành công. */
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

    /** Cập nhật access token sau khi làm mới. */
    public synchronized void updateAccessToken(String accessToken) {
        try {
            preferences.edit().putString(KEY_ACCESS, safe(accessToken)).apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể cập nhật access token", exception);
        }
    }

    /** Cập nhật cả hai token khi server xoay vòng refresh token (ROTATE_REFRESH_TOKENS). */
    public synchronized void updateTokens(String accessToken, String refreshToken) {
        try {
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(KEY_ACCESS, safe(accessToken));
            if (refreshToken != null && !refreshToken.isEmpty()) {
                editor.putString(KEY_REFRESH, refreshToken);
            }
            editor.apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể cập nhật token", exception);
        }
    }

    /** Cập nhật thông tin người dùng sau khi sửa hồ sơ. */
    public synchronized void updateUser(User user) {
        try {
            SharedPreferences.Editor editor = preferences.edit();
            writeUser(editor, user);
            editor.apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể cập nhật người dùng", exception);
        }
    }

    /** Còn phiên đăng nhập hay không, dùng để quyết định mở màn nào lúc khởi động. */
    public boolean hasSession() {
        return !getAccessToken().isEmpty() && !getRefreshToken().isEmpty();
    }

    /** Token gắn vào header Authorization của mỗi request. */
    public String getAccessToken() {
        return preferences.getString(KEY_ACCESS, "");
    }

    /** Token dùng để xin access token mới khi hết hạn. */
    public String getRefreshToken() {
        return preferences.getString(KEY_REFRESH, "");
    }

    /** Thông tin người dùng đang đăng nhập (tên, email, vai trò). */
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

    /** Xóa sạch phiên khi đăng xuất hoặc phiên hết hạn. */
    public synchronized void clear() {
        try {
            preferences.edit().clear().apply();
        } catch (Exception exception) {
            AppLogger.error(appContext, "SessionManager", "Không thể xóa phiên đăng nhập", exception);
        }
    }

    /** Ghi từng trường của người dùng xuống bộ nhớ. */
    private void writeUser(SharedPreferences.Editor editor, User user) {
        User safeUser = user == null ? new User("", "", "", "", "student", "") : user;
        editor.putString(KEY_USER_ID, safeUser.getId())
                .putString(KEY_USERNAME, safeUser.getUsername())
                .putString(KEY_FULL_NAME, safeUser.getFullName())
                .putString(KEY_EMAIL, safeUser.getEmail())
                .putString(KEY_ROLE, safeUser.getRole())
                .putString(KEY_CLASS_NAME, safeUser.getClassName());
    }

    /** Đổi null thành chuỗi rỗng để không phải kiểm tra null khắp nơi. */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
