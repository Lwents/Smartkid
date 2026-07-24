package com.example.smartkid.common.ui;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Áp dụng các chi tiết Liquid Glass có thể dùng an toàn từ Android 7.0.
 * Lớp này không dùng RenderEffect/blur thật để tránh lỗi GPU và giữ chữ luôn dễ đọc.
 */
public final class LiquidGlassUi {
    private static final float PRESSED_SCALE = 0.97f;

    private LiquidGlassUi() {
    }

    public static void decorate(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            configureSystemBars(activity);
            View content = activity.findViewById(android.R.id.content);
            if (content != null) {
                applyWindowBackground(activity, content);
                decorateTree(content);
            }
        } catch (Exception exception) {
            AppLogger.error(activity, "LiquidGlassUi", "Không thể hoàn thiện giao diện kính", exception);
        }
    }

    public static void decorate(View root) {
        if (root == null) {
            return;
        }
        try {
            decorateTree(root);
        } catch (Exception exception) {
            AppLogger.error(root.getContext(), "LiquidGlassUi",
                    "Không thể hoàn thiện giao diện màn con", exception);
        }
    }

    /** Keeps the top system-bar area visually attached to a screen's header. */
    public static void useStatusBarBackdrop(Activity activity, int rootId,
                                            int backdropDrawable, boolean lightIcons) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        decor.setTag(R.id.tag_status_bar_backdrop, backdropDrawable);
        decor.setTag(R.id.tag_status_bar_light_icons, lightIcons);
        decor.setTag(R.id.tag_status_bar_root, rootId);
        decorate(activity);
    }

    /** Keeps the system navigation area consistent with the role dashboards. */
    public static void useDarkNavigationBar(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        activity.getWindow().getDecorView().setTag(R.id.tag_dark_navigation_bar, true);
        configureSystemBars(activity);
    }

    private static void configureSystemBars(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(ContextCompat.getColor(activity, android.R.color.transparent));
        boolean darkNavigationBar = Boolean.TRUE.equals(
                window.getDecorView().getTag(R.id.tag_dark_navigation_bar));
        window.setNavigationBarColor(ContextCompat.getColor(activity,
                darkNavigationBar ? android.R.color.black : R.color.glass_navigation_bar));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        boolean darkMode = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Object lightIconTag = window.getDecorView().getTag(R.id.tag_status_bar_light_icons);
        boolean lightStatusIcons = lightIconTag instanceof Boolean
                ? (Boolean) lightIconTag : !darkMode;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window,
                window.getDecorView());
        controller.setAppearanceLightStatusBars(lightStatusIcons);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(!darkNavigationBar && !darkMode);
        }
        applySafeInsets(activity);
    }

    private static void applyWindowBackground(Activity activity, View content) {
        View decor = activity.getWindow().getDecorView();
        Object backdropTag = decor.getTag(R.id.tag_status_bar_backdrop);
        Object rootTag = decor.getTag(R.id.tag_status_bar_root);
        if (backdropTag instanceof Integer && rootTag instanceof Integer) {
            decor.setBackgroundResource((Integer) backdropTag);
            content.setBackgroundColor(ContextCompat.getColor(activity, android.R.color.transparent));
            View root = activity.findViewById((Integer) rootTag);
            if (root != null) root.setBackgroundResource(R.drawable.common_bg_liquid_screen);
            return;
        }
        content.setBackgroundResource(R.drawable.common_bg_liquid_screen);
    }

    private static void applySafeInsets(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || content.getTag(R.id.tag_liquid_glass_insets) != null) {
            return;
        }
        int[] initialPadding = new int[] {
                content.getPaddingLeft(), content.getPaddingTop(),
                content.getPaddingRight(), content.getPaddingBottom()
        };
        content.setTag(R.id.tag_liquid_glass_insets, initialPadding);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(initialPadding[0] + bars.left, initialPadding[1] + bars.top,
                    initialPadding[2] + bars.right, initialPadding[3] + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private static void decorateTree(View view) {
        if (view == null) {
            return;
        }
        Context context = view.getContext();
        try {
            if (view instanceof MaterialToolbar) {
                decorateToolbar((MaterialToolbar) view);
            } else if (view instanceof TextInputLayout) {
                decorateInput((TextInputLayout) view);
            } else if (view instanceof MaterialButton) {
                decorateButton((MaterialButton) view);
            } else if (view instanceof MaterialCardView) {
                decorateCard((MaterialCardView) view);
            } else if (view instanceof ProgressBar) {
                decorateProgress((ProgressBar) view);
            } else if (view instanceof TextView) {
                decorateStateText((TextView) view);
            }
        } catch (Exception exception) {
            AppLogger.error(context, "LiquidGlassUi", "Bỏ qua thành phần giao diện không tương thích", exception);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                decorateTree(group.getChildAt(index));
            }
        }
    }

    private static void decorateToolbar(MaterialToolbar toolbar) {
        Context context = toolbar.getContext();
        toolbar.setBackgroundColor(ContextCompat.getColor(context, R.color.glass_toolbar));
        toolbar.setTitleTextColor(ContextCompat.getColor(context, R.color.smartkid_text));
        toolbar.setSubtitleTextColor(ContextCompat.getColor(context, R.color.smartkid_text_secondary));
        toolbar.setElevation(0f);
        Drawable icon = toolbar.getNavigationIcon();
        if (icon != null) {
            Drawable wrapped = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(context, R.color.smartkid_text));
            toolbar.setNavigationIcon(wrapped);
        }
    }

    private static void decorateInput(TextInputLayout input) {
        Context context = input.getContext();
        int radius = context.getResources().getDimensionPixelSize(R.dimen.glass_radius_small);
        input.setBoxBackgroundColor(ContextCompat.getColor(context, R.color.glass_input));
        input.setBoxCornerRadii(radius, radius, radius, radius);
        input.setBoxStrokeColor(ContextCompat.getColor(context, R.color.smartkid_primary));
        input.setHintTextColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.smartkid_text_secondary)));
    }

    private static void decorateButton(MaterialButton button) {
        Context context = button.getContext();
        button.setCornerRadius(context.getResources().getDimensionPixelSize(R.dimen.glass_radius_large));
        button.setMinHeight(context.getResources().getDimensionPixelSize(R.dimen.touch_target));
        installPressFeedback(button);
    }

    private static void decorateCard(MaterialCardView card) {
        Context context = card.getContext();
        card.setRadius(context.getResources().getDimension(R.dimen.glass_radius));
        card.setStrokeColor(ContextCompat.getColor(context, R.color.glass_stroke));
        card.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.glass_stroke_width));
        if (card.isClickable()) {
            installPressFeedback(card);
        }
    }

    private static void decorateProgress(ProgressBar progressBar) {
        int primary = ContextCompat.getColor(progressBar.getContext(), R.color.smartkid_primary);
        ColorStateList tint = ColorStateList.valueOf(primary);
        progressBar.setIndeterminateTintList(tint);
        progressBar.setProgressTintList(tint);
    }

    private static void decorateStateText(TextView textView) {
        int id = textView.getId();
        if (id == View.NO_ID) {
            return;
        }
        String entryName;
        try {
            entryName = textView.getResources().getResourceEntryName(id);
        } catch (Exception ignored) {
            return;
        }
        if (!entryName.toLowerCase(java.util.Locale.ROOT).contains("empty")) {
            return;
        }
        Drawable icon = ContextCompat.getDrawable(textView.getContext(), R.drawable.common_ic_empty_state);
        textView.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        textView.setCompoundDrawablePadding(textView.getResources()
                .getDimensionPixelSize(R.dimen.space_md));
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void installPressFeedback(View view) {
        if (Boolean.TRUE.equals(view.getTag(R.id.tag_liquid_glass_decorated))) {
            return;
        }
        view.setTag(R.id.tag_liquid_glass_decorated, Boolean.TRUE);
        if (!animationsEnabled(view.getContext())) {
            return;
        }
        view.setOnTouchListener((target, event) -> {
            try {
                if (!target.isEnabled()) {
                    return false;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    target.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                            .setDuration(90L).start();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    target.animate().scaleX(1f).scaleY(1f).setDuration(140L).start();
                }
            } catch (Exception exception) {
                target.setScaleX(1f);
                target.setScaleY(1f);
            }
            return false;
        });
    }

    private static boolean animationsEnabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return ValueAnimator.areAnimatorsEnabled();
            }
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception ignored) {
            return true;
        }
    }
}
