package com.example.smartkid.common.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class AppLogger {
    private static final String PREF_NAME = "smartkid_error_log";
    private static final String KEY_FATAL_ERROR = "fatal_error";
    private static final int MAX_ERROR_LENGTH = 4_000;

    private AppLogger() {
    }

    public static void error(@Nullable Context context, String tag, String message,
                             @Nullable Throwable throwable) {
        if (throwable == null) {
            Log.e(tag, message);
        } else {
            Log.e(tag, message, throwable);
        }

        if (context == null) {
            return;
        }

        try {
            String detail = message;
            if (throwable != null && throwable.getMessage() != null) {
                detail += ": " + throwable.getMessage();
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_error", trim(detail))
                    .apply();
        } catch (Exception ignored) {
            // Logger không được làm ứng dụng dừng.
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveFatalError(Context context, String threadName, Throwable throwable) {
        StringWriter writer = new StringWriter();
        if (throwable != null) {
            throwable.printStackTrace(new PrintWriter(writer));
        }
        String detail = "Luồng: " + threadName + "\n" + writer;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FATAL_ERROR, trim(detail))
                .commit(); // Cần ghi đồng bộ vì tiến trình sẽ dừng ngay sau lỗi fatal.
    }

    @Nullable
    public static String consumeFatalError(Context context) {
        try {
            SharedPreferences preferences =
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String detail = preferences.getString(KEY_FATAL_ERROR, null);
            preferences.edit().remove(KEY_FATAL_ERROR).apply();
            return detail;
        } catch (Exception exception) {
            Log.e("AppLogger", "Không đọc được lỗi trước đó", exception);
            return null;
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return "Không có mô tả lỗi";
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}
