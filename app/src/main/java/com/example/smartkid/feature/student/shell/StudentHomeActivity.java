package com.example.smartkid.feature.student.shell;

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
import com.example.smartkid.feature.student.course.CoursesFragment;
import com.example.smartkid.feature.student.dashboard.StudentDashboardFragment;
import com.example.smartkid.feature.student.exam.ExamsFragment;
import com.example.smartkid.feature.student.profile.StudentProfileFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

public class StudentHomeActivity extends BaseActivity {
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

    /** Khởi tạo khung chính của học viên gồm toolbar, ViewPager và thanh điều hướng. */
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
            AppLogger.error(this, "StudentHomeActivity", "Không thể tạo trang chính", exception);
            showErrorDialog("Không thể mở trang chính: " + exception.getMessage());
        }
    }

    /** Đăng ký lắng nghe trạng thái mạng khi màn hình bắt đầu hiển thị. */
    @Override
    protected void onStart() {
        super.onStart();
        registerNetworkReceiverSafely();
    }

    /** Gỡ NetworkStateReceiver để tránh giữ Activity khi màn hình không còn hiển thị. */
    @Override
    protected void onStop() {
        unregisterNetworkReceiverSafely();
        super.onStop();
    }

    /** Gắn sự kiện cho bốn mục Dashboard, Khóa học, Bài thi và Hồ sơ. */
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

    /** Cấu hình ViewPager2, hiệu ứng chuyển trang và đồng bộ trạng thái thanh điều hướng. */
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

    /** Đổi ID mục điều hướng thành vị trí trang trong ViewPager. */
    private int navigationIndex(int itemId) {
        for (int index = 0; index < navigationIds.length; index++) {
            if (navigationIds[index] == itemId) return index;
        }
        return -1;
    }

    /** Chọn tiêu đề toolbar tương ứng với trang Student hiện tại. */
    private String navigationTitle(int itemId) {
        if (itemId == R.id.nav_courses) return getString(R.string.title_my_courses);
        if (itemId == R.id.nav_exams) return getString(R.string.exams);
        if (itemId == R.id.nav_profile) return getString(R.string.title_profile);
        return getString(R.string.title_home);
    }

    /** Ghi nhận trang đang chọn rồi cập nhật tiêu đề và trạng thái các nút. */
    private void applyNavigationState(int position) {
        if (position < 0 || position >= navigationIds.length) return;
        selectedNavigationId = navigationIds[position];
        updateNavigationSelection();
        toolbar.setTitle(navigationTitle(selectedNavigationId));
    }

    /** Di chuyển thanh chỉ báo theo vị trí và tiến độ vuốt của ViewPager. */
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

    /** Đánh dấu mục điều hướng đang hoạt động và bỏ chọn các mục còn lại. */
    private void updateNavigationSelection() {
        for (int navigationId : navigationIds) {
            View item = findViewById(navigationId);
            if (item != null) {
                item.setSelected(navigationId == selectedNavigationId);
            }
        }
    }

    /** Lưu tab Student đang mở để khôi phục sau khi Activity được tạo lại. */
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_NAVIGATION, selectedNavigationId);
        super.onSaveInstanceState(outState);
    }

    /** Đăng ký receiver theo phiên bản Android và tránh đăng ký lặp. */
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
            AppLogger.error(this, "StudentHomeActivity", "Không thể theo dõi kết nối mạng", exception);
        }
    }

    /** Gỡ receiver nếu đã đăng ký và bỏ qua lỗi lifecycle không nguy hiểm. */
    private void unregisterNetworkReceiverSafely() {
        if (!receiverRegistered || networkReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception exception) {
            AppLogger.error(this, "StudentHomeActivity", "Không thể hủy theo dõi mạng", exception);
        } finally {
            receiverRegistered = false;
        }
    }

    /** Thông báo cho học viên khi mất hoặc khôi phục kết nối mạng. */
    private void showNetworkState(boolean connected) {
        try {
            if (!connected) {
                Snackbar.make(findViewById(R.id.homeRoot),
                        "Đang ngoại tuyến – dữ liệu tạm chỉ có trong phiên hiện tại",
                        Snackbar.LENGTH_LONG).show();
            }
        } catch (Exception exception) {
            AppLogger.error(this, "StudentHomeActivity", "Không thể báo trạng thái mạng", exception);
        }
    }

    private static final class StudentPagerAdapter extends FragmentStateAdapter {
        /** Tạo adapter cung cấp bốn Fragment chính của học viên. */
        StudentPagerAdapter(StudentHomeActivity activity) {
            super(activity);
        }

        /** Tạo Fragment đúng với vị trí của tab Student. */
        @androidx.annotation.NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 1) return new CoursesFragment();
            if (position == 2) return new ExamsFragment();
            if (position == 3) return new StudentProfileFragment();
            return new StudentDashboardFragment();
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
