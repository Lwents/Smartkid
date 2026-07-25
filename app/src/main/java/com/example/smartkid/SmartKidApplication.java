package com.example.smartkid;

import android.app.Application;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiEnvironment;

/**
 * Điểm khởi tạo dùng chung của ứng dụng.
 * Lỗi nghiêm trọng ngoài dự kiến được lưu lại để lần mở sau có thể giải thích nguyên nhân.
 */
public class SmartKidApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ApiClient.initialize(this);
            // Chốt URL API: dùng backend local nếu kết nối được, ngược lại chuyển sang VPS.
            ApiEnvironment.resolveAsync();
        } catch (Exception exception) {
            AppLogger.error(this, "SmartKidApplication", "Không thể khởi tạo lớp gọi API", exception);
        }

        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                AppLogger.saveFatalError(this, thread.getName(), throwable);
            } catch (Exception ignored) {
                // Không được phát sinh thêm lỗi trong bộ ghi nhận lỗi cuối cùng.
            }

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }
}
