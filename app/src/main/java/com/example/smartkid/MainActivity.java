package com.example.smartkid;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.navigation.RoleNavigation;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.feature.auth.LoginActivity;
import com.example.smartkid.common.ui.BaseActivity;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            String previousFatalError = AppLogger.consumeFatalError(this);
            if (previousFatalError != null && !previousFatalError.trim().isEmpty()) {
                String displayError = previousFatalError.length() > 700
                        ? previousFatalError.substring(0, 700) + "…" : previousFatalError;
                new AlertDialog.Builder(this)
                        .setTitle("Ứng dụng đã gặp lỗi ở lần chạy trước")
                        .setMessage(displayError)
                        .setPositiveButton("Tiếp tục", (dialog, which) -> routeToNextScreen())
                        .setCancelable(false)
                        .show();
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(this::routeToNextScreen, 450);
            }
        } catch (Exception exception) {
            AppLogger.error(this, "MainActivity", "Không thể khởi động ứng dụng", exception);
            showErrorDialog("Không thể khởi động SmartKid: " + exception.getMessage());
        }
    }

    private void routeToNextScreen() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            SessionManager sessionManager = new SessionManager(this);
            Class<?> destination = sessionManager.hasSession()
                    ? RoleNavigation.destination(this) : LoginActivity.class;
            startActivity(new Intent(this, destination));
            finish();
        } catch (Exception exception) {
            AppLogger.error(this, "MainActivity", "Không thể điều hướng", exception);
            showErrorDialog("Không thể mở màn hình tiếp theo");
        }
    }
}
