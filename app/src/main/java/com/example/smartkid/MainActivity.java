package com.example.smartkid;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smartkid.common.navigation.RoleNavigation;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.feature.shared.auth.LoginActivity;

public class MainActivity extends BaseActivity {

    private static final long SPLASH_DELAY_MS = 1000L;
    private static final long TIMEOUT_MS = 3000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasNavigated = false;
    private Runnable timeoutRunnable;

    private View splashContent;
    private View errorContainer;
    private TextView textSplashError;
    private View buttonSplashRetry;
    private ProgressBar progressSplash;

    /** Tạo màn hình splash, khôi phục lỗi nghiêm trọng cũ và hẹn chuyển sang màn hình theo session. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            bindViews();

            // Đặt timeout dự phòng để splash không bị treo vô thời hạn.
            timeoutRunnable = () -> {
                AppLogger.error(this, "MainActivity", "Splash timeout reached, forcing navigation", null);
                performNavigationSafely();
            };
            mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

            // Bắt đầu hiệu ứng hiện dần của màn hình khởi động.
            startFadeInAnimation();

            // Nếu lần chạy trước có lỗi nghiêm trọng thì hiển thị lỗi; nếu không thì tiếp tục khởi tạo.
            String previousFatalError = AppLogger.consumeFatalError(this);
            if (previousFatalError != null && !previousFatalError.trim().isEmpty()) {
                mainHandler.removeCallbacks(timeoutRunnable);
                String displayError = previousFatalError.length() > 700
                        ? previousFatalError.substring(0, 700) + "…" : previousFatalError;
                new AlertDialog.Builder(this)
                        .setTitle("Ứng dụng đã gặp lỗi ở lần chạy trước")
                        .setMessage(displayError)
                        .setPositiveButton("Tiếp tục", (dialog, which) -> performNavigationSafely())
                        .setCancelable(false)
                        .show();
            } else {
                mainHandler.postDelayed(this::performNavigationSafely, SPLASH_DELAY_MS);
            }
        } catch (Exception exception) {
            AppLogger.error(this, "MainActivity", "Không thể khởi động ứng dụng", exception);
            showInitializationError("Không thể khởi động ứng dụng. Vui lòng thử lại.");
        }
    }

    /** Ánh xạ các view của splash và gắn hành động thử khởi tạo lại. */
    private void bindViews() {
        splashContent = findViewById(R.id.layoutSplashContent);
        errorContainer = findViewById(R.id.layoutErrorContainer);
        textSplashError = findViewById(R.id.textSplashError);
        buttonSplashRetry = findViewById(R.id.buttonSplashRetry);
        progressSplash = findViewById(R.id.progressSplash);

        if (buttonSplashRetry != null) {
            buttonSplashRetry.setOnClickListener(v -> retryInitialization());
        }
    }

    /** Chạy hiệu ứng xuất hiện cho splash; nếu animation lỗi thì hiển thị nội dung ngay. */
    private void startFadeInAnimation() {
        if (splashContent == null) return;
        try {
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(600L);
            fadeIn.setFillAfter(true);
            fadeIn.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (splashContent != null) {
                        splashContent.setAlpha(1.0f);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            splashContent.startAnimation(fadeIn);
        } catch (Exception exception) {
            AppLogger.error(this, "MainActivity", "Lỗi animation fade-in, fallback hiển thị trực tiếp", null);
            splashContent.setAlpha(1.0f);
        }
    }

    /** Đặt lại trạng thái splash rồi thực hiện lại bước kiểm tra session. */
    private void retryInitialization() {
        if (errorContainer != null) {
            errorContainer.setVisibility(View.GONE);
        }
        if (progressSplash != null) {
            progressSplash.setVisibility(View.VISIBLE);
        }
        hasNavigated = false;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(this::performNavigationSafely, 600L);
    }

    /** Kiểm tra phiên đăng nhập và điều hướng đến Login hoặc dashboard đúng role. */
    private void performNavigationSafely() {
        if (hasNavigated || isFinishing() || isDestroyed()) {
            return;
        }
        hasNavigated = true;
        mainHandler.removeCallbacksAndMessages(null);

        try {
            SessionManager sessionManager = new SessionManager(this);
            boolean isValidSession = sessionManager.hasSession();

            Class<?> destination;
            if (isValidSession) {
                try {
                    destination = RoleNavigation.destination(this);
                } catch (Exception roleException) {
                    AppLogger.error(this, "MainActivity", "Không thể xác định role, chuyển đến Login", roleException);
                    destination = LoginActivity.class;
                }
            } else {
                destination = LoginActivity.class;
            }

            Intent intent = new Intent(this, destination);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception exception) {
            AppLogger.error(this, "MainActivity", "Lỗi trong quá trình điều hướng", exception);
            hasNavigated = false;
            showInitializationError("Không thể mở màn hình tiếp theo: " + exception.getMessage());
        }
    }

    /** Hiển thị lỗi khởi động ngay trên splash, có fallback sang dialog nếu layout thiếu view. */
    private void showInitializationError(String errorMessage) {
        if (isFinishing() || isDestroyed()) return;
        if (progressSplash != null) {
            progressSplash.setVisibility(View.GONE);
        }
        if (errorContainer != null && textSplashError != null) {
            textSplashError.setText(errorMessage);
            errorContainer.setVisibility(View.VISIBLE);
        } else {
            showErrorDialog(errorMessage);
        }
    }

    /** Hủy callback và animation để splash không tiếp tục chạy sau khi Activity đóng. */
    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (splashContent != null) {
            splashContent.clearAnimation();
        }
        super.onDestroy();
    }
}
