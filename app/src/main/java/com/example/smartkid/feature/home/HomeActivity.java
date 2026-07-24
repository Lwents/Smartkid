package com.example.smartkid.feature.home;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.NetworkStateReceiver;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.feature.course.CoursesFragment;
import com.example.smartkid.feature.home.DashboardFragment;
import com.example.smartkid.feature.exam.ExamsFragment;
import com.example.smartkid.feature.profile.ProfileFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

public class HomeActivity extends BaseActivity {
    private MaterialToolbar toolbar;
    private static final String STATE_SELECTED_NAVIGATION = "selected_navigation";
    private final int[] navigationIds = {
            R.id.nav_dashboard, R.id.nav_courses, R.id.nav_exams, R.id.nav_profile
    };
    private FrameLayout bottomNavigation;
    private View navigationIndicator;
    private ViewPager2 studentPager;
    private int selectedNavigationId = R.id.nav_dashboard;
    private NetworkStateReceiver networkReceiver;
    private boolean receiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.home_activity_home);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.homeRoot,
                    R.drawable.common_bg_liquid_screen, true);
            LiquidGlassUi.useDarkNavigationBar(this);
            toolbar = findViewById(R.id.toolbarHome);
            bottomNavigation = findViewById(R.id.bottomNavigation);
            navigationIndicator = findViewById(R.id.navSelectionIndicator);
            studentPager = findViewById(R.id.studentPager);
            if (toolbar == null || bottomNavigation == null || navigationIndicator == null
                    || studentPager == null) {
                throw new IllegalStateException("Giao diện trang chính chưa đầy đủ");
            }
            setSupportActionBar(toolbar);
            bindNavigationItems();
            configurePager();
            int initialNavigation = savedInstanceState == null
                    ? R.id.nav_dashboard
                    : savedInstanceState.getInt(STATE_SELECTED_NAVIGATION, R.id.nav_dashboard);
            int initialIndex = Math.max(0, navigationIndex(initialNavigation));
            studentPager.setCurrentItem(initialIndex, false);
            applyNavigationState(initialIndex);
            bottomNavigation.post(() -> updateIndicatorPosition(initialIndex, 0f));
            networkReceiver = new NetworkStateReceiver(this::showNetworkState);
        } catch (Exception exception) {
            AppLogger.error(this, "HomeActivity", "Không thể tạo trang chính", exception);
            showErrorDialog("Không thể mở trang chính: " + exception.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkReceiverSafely();
    }

    @Override
    protected void onStop() {
        unregisterNetworkReceiverSafely();
        super.onStop();
    }

    private void bindNavigationItems() {
        for (int navigationId : navigationIds) {
            View item = findViewById(navigationId);
            if (item == null) {
                throw new IllegalStateException("Thanh điều hướng thiếu mục bắt buộc");
            }
            item.setOnClickListener(clicked -> {
                int targetIndex = navigationIndex(clicked.getId());
                if (targetIndex >= 0 && targetIndex != studentPager.getCurrentItem()) {
                    studentPager.setCurrentItem(targetIndex, true);
                }
            });
        }
    }

    private void configurePager() {
        studentPager.setAdapter(new StudentPagerAdapter(this));
        studentPager.setUserInputEnabled(true);
        // Chỉ giữ trang liền kề để thao tác mượt mà nhưng không tải cả bốn API lúc mở app.
        studentPager.setOffscreenPageLimit(1);
        studentPager.setPageTransformer((page, position) -> {
            float distance = Math.min(1f, Math.abs(position));
            page.setAlpha(1f - distance * 0.28f);
            page.setScaleX(1f - distance * 0.035f);
            page.setScaleY(1f - distance * 0.035f);
            page.setTranslationX(-position * page.getWidth() * 0.035f);
        });
        studentPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
                updateIndicatorPosition(position, positionOffset);
            }

            @Override
            public void onPageSelected(int position) {
                applyNavigationState(position);
            }
        });
    }

    private int navigationIndex(int itemId) {
        for (int index = 0; index < navigationIds.length; index++) {
            if (navigationIds[index] == itemId) return index;
        }
        return -1;
    }

    private String navigationTitle(int itemId) {
        if (itemId == R.id.nav_courses) return getString(R.string.title_my_courses);
        if (itemId == R.id.nav_exams) return getString(R.string.exams);
        if (itemId == R.id.nav_profile) return getString(R.string.title_profile);
        return getString(R.string.title_home);
    }

    private void applyNavigationState(int position) {
        if (position < 0 || position >= navigationIds.length) return;
        selectedNavigationId = navigationIds[position];
        updateNavigationSelection();
        toolbar.setTitle(navigationTitle(selectedNavigationId));
    }

    private void updateIndicatorPosition(int position, float positionOffset) {
        int availableWidth = bottomNavigation.getWidth()
                - bottomNavigation.getPaddingLeft() - bottomNavigation.getPaddingRight();
        if (availableWidth <= 0) return;
        float itemWidth = availableWidth / (float) navigationIds.length;
        int horizontalInset = dpToPixels(2f);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) navigationIndicator.getLayoutParams();
        int indicatorWidth = Math.round(itemWidth) - horizontalInset * 2;
        if (params.width != indicatorWidth) {
            params.width = indicatorWidth;
            navigationIndicator.setLayoutParams(params);
        }
        navigationIndicator.setTranslationX(
                itemWidth * (position + positionOffset) + horizontalInset);
    }

    private int dpToPixels(float dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.round(dp * metrics.density);
    }

    private void updateNavigationSelection() {
        for (int navigationId : navigationIds) {
            View item = findViewById(navigationId);
            if (item != null) {
                item.setSelected(navigationId == selectedNavigationId);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_NAVIGATION, selectedNavigationId);
        super.onSaveInstanceState(outState);
    }

    private void registerNetworkReceiverSafely() {
        if (receiverRegistered || networkReceiver == null) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            ContextCompat.registerReceiver(this, networkReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        } catch (Exception exception) {
            AppLogger.error(this, "HomeActivity", "Không thể theo dõi kết nối mạng", exception);
        }
    }

    private void unregisterNetworkReceiverSafely() {
        if (!receiverRegistered || networkReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception exception) {
            AppLogger.error(this, "HomeActivity", "Không thể hủy theo dõi mạng", exception);
        } finally {
            receiverRegistered = false;
        }
    }

    private void showNetworkState(boolean connected) {
        try {
            if (!connected) {
                Snackbar.make(findViewById(R.id.homeRoot),
                        "Đang ngoại tuyến – dữ liệu tạm chỉ có trong phiên hiện tại",
                        Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception exception) {
            AppLogger.error(this, "HomeActivity", "Không thể báo trạng thái mạng", exception);
        }
    }

    private static final class StudentPagerAdapter extends FragmentStateAdapter {
        StudentPagerAdapter(HomeActivity activity) {
            super(activity);
        }

        @androidx.annotation.NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 1) return new CoursesFragment();
            if (position == 2) return new ExamsFragment();
            if (position == 3) return new ProfileFragment();
            return new DashboardFragment();
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
