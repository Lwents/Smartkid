package com.example.smartkid.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.ui.auth.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                    new FragmentManager.FragmentLifecycleCallbacks() {
                        @Override
                        public void onFragmentViewCreated(@androidx.annotation.NonNull FragmentManager fm,
                                                          @androidx.annotation.NonNull Fragment fragment,
                                                          @androidx.annotation.NonNull View view,
                                                          @Nullable Bundle state) {
                            LiquidGlassUi.decorate(view);
                        }
                    }, true);
        } catch (Exception exception) {
            AppLogger.error(this, "BaseActivity", "Không thể đăng ký giao diện màn con", exception);
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        LiquidGlassUi.decorate(this);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        LiquidGlassUi.decorate(this);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        LiquidGlassUi.decorate(this);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        LiquidGlassUi.decorate(this);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        LiquidGlassUi.decorate(this);
    }

    public void handleApiError(ApiError error) {
        if (error == null) {
            showErrorDialog("Có lỗi không xác định xảy ra");
            return;
        }
        if (error.isSessionExpired()) {
            try {
                new SessionManager(this).clear();
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } catch (Exception exception) {
                AppLogger.error(this, "BaseActivity", "Không thể quay về đăng nhập", exception);
            }
            return;
        }
        showErrorDialog(error.getMessage());
    }

    public void showErrorDialog(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Không thể thực hiện")
                    .setMessage(message == null || message.trim().isEmpty()
                            ? "Có lỗi xảy ra, vui lòng thử lại" : message)
                    .setPositiveButton("Đã hiểu", null)
                    .show();
        } catch (Exception exception) {
            AppLogger.error(this, "BaseActivity", "Không thể hiển thị thông báo lỗi", exception);
            Toast.makeText(this, "Có lỗi xảy ra", Toast.LENGTH_LONG).show();
        }
    }

    public void showShortMessage(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            AppLogger.error(this, "BaseActivity", "Không thể hiển thị Toast", exception);
        }
    }
}
