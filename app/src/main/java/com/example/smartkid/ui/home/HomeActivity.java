package com.example.smartkid.ui.home;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.NetworkStateReceiver;
import com.example.smartkid.ui.common.BaseActivity;
import com.example.smartkid.ui.courses.CoursesFragment;
import com.example.smartkid.ui.dashboard.DashboardFragment;
import com.example.smartkid.ui.exams.ExamsFragment;
import com.example.smartkid.ui.games.GamesFragment;
import com.example.smartkid.ui.profile.ProfileFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

public class HomeActivity extends BaseActivity {
    private MaterialToolbar toolbar;
    private static final String STATE_SELECTED_NAVIGATION = "selected_navigation";
    private final int[] navigationIds = {
            R.id.nav_dashboard, R.id.nav_courses, R.id.nav_exams,
            R.id.nav_games, R.id.nav_profile
    };
    private LinearLayout bottomNavigation;
    private int selectedNavigationId = R.id.nav_dashboard;
    private NetworkStateReceiver networkReceiver;
    private boolean receiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_home);
            toolbar = findViewById(R.id.toolbarHome);
            bottomNavigation = findViewById(R.id.bottomNavigation);
            if (toolbar == null || bottomNavigation == null) {
                throw new IllegalStateException("Giao diện trang chính chưa đầy đủ");
            }
            setSupportActionBar(toolbar);
            bindNavigationItems();
            if (savedInstanceState == null) {
                openNavigation(R.id.nav_dashboard);
            } else {
                selectedNavigationId = savedInstanceState.getInt(
                        STATE_SELECTED_NAVIGATION, R.id.nav_dashboard);
                updateNavigationSelection();
            }
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
            item.setOnClickListener(clicked -> openNavigation(clicked.getId()));
        }
    }

    private void openNavigation(int itemId) {
        try {
            Fragment fragment;
            String title;
            if (itemId == R.id.nav_courses) {
                fragment = new CoursesFragment();
                title = getString(R.string.title_my_courses);
            } else if (itemId == R.id.nav_exams) {
                fragment = new ExamsFragment();
                title = getString(R.string.exams);
            } else if (itemId == R.id.nav_games) {
                fragment = new GamesFragment();
                title = getString(R.string.games);
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
                title = getString(R.string.title_profile);
            } else {
                fragment = new DashboardFragment();
                title = getString(R.string.title_home);
            }
            selectedNavigationId = itemId;
            updateNavigationSelection();
            toolbar.setTitle(title);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
        } catch (Exception exception) {
            AppLogger.error(this, "HomeActivity", "Không thể đổi màn hình", exception);
            showErrorDialog("Không thể mở chức năng đã chọn");
        }
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
}
