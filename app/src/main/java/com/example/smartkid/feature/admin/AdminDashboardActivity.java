package com.example.smartkid.feature.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.admin.data.AdminDashboardRepository;
import com.example.smartkid.feature.admin.model.AdminDashboardData;
import com.example.smartkid.feature.management.RoleDashboardActivity;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Native admin home based on SunEdu's KPI-oriented admin dashboard. */
public final class AdminDashboardActivity extends RoleDashboardActivity {
    private static final String[] MANAGEMENT_KEYS = {
            "admin_active_users", "admin_users", "admin_courses", "admin_approval",
            "admin_health", "admin_activity", "admin_security", "admin_sessions",
            "admin_config", "admin_backups", "admin_report_revenue", "admin_report_users",
            "admin_report_learning", "admin_report_content", "admin_transactions",
            "admin_notifications"
    };

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private AdminDashboardRepository repository;
    private ProgressBar progressBar;
    private TextView statusText;
    private LinearLayout topCoursesContainer;
    private LinearLayout transactionsContainer;
    private NestedScrollView dashboardScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireRole("admin")) return;
        try {
            setContentView(R.layout.admin_activity_dashboard);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.adminDashboardRoot,
                    R.drawable.admin_bg_header, true);
            repository = new AdminDashboardRepository(this);
            bindViews();
            bindHeader();
            bindActions();
            populateManagementActions(findViewById(R.id.containerAdminManagement), MANAGEMENT_KEYS);
            tuneAdminCards(findViewById(R.id.adminDashboardRoot));
            loadDashboard();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminDashboardActivity", "Không thể tạo dashboard", exception);
            showErrorDialog("Không thể mở bảng điều khiển quản trị");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressAdminDashboard);
        statusText = findViewById(R.id.textAdminDashboardStatus);
        topCoursesContainer = findViewById(R.id.containerAdminTopCourses);
        transactionsContainer = findViewById(R.id.containerAdminTransactions);
        dashboardScroll = findViewById(R.id.scrollAdminDashboard);
        if (progressBar == null || statusText == null || topCoursesContainer == null
                || transactionsContainer == null || dashboardScroll == null) {
            throw new IllegalStateException("Dashboard quản trị thiếu thành phần bắt buộc");
        }
    }

    private void bindHeader() {
        ((TextView) findViewById(R.id.textAdminAvatar)).setText("AD");
        bindLogoutAction(R.id.buttonAdminLogout);
    }

    private void bindActions() {
        findViewById(R.id.buttonAdminNavOverview).setSelected(true);
        findViewById(R.id.buttonAdminRefresh).setOnClickListener(view -> loadDashboard());
        findViewById(R.id.buttonAdminMenu).setOnClickListener(view ->
                scrollTo(findViewById(R.id.textAdminManagementTitle)));
        findViewById(R.id.buttonAdminNotifications).setOnClickListener(view ->
                openManagementFeature("admin_notifications"));
        findViewById(R.id.buttonAdminUsers).setOnClickListener(view ->
                openManagementFeature("admin_users"));
        findViewById(R.id.buttonAdminApprovals).setOnClickListener(view ->
                openManagementFeature("admin_approval"));
        findViewById(R.id.buttonAdminTransactions).setOnClickListener(view ->
                openManagementFeature("admin_transactions"));
        findViewById(R.id.buttonAdminHealth).setOnClickListener(view ->
                openManagementFeature("admin_health"));
        findViewById(R.id.buttonAdminNavOverview).setOnClickListener(view ->
                dashboardScroll.smoothScrollTo(0, 0));
        findViewById(R.id.buttonAdminNavUsers).setOnClickListener(view ->
                openManagementFeature("admin_users"));
        findViewById(R.id.buttonAdminNavContent).setOnClickListener(view ->
                openManagementFeature("admin_courses"));
        findViewById(R.id.buttonAdminNavReports).setOnClickListener(view ->
                openManagementFeature("admin_report_users"));
        findViewById(R.id.buttonAdminNavSettings).setOnClickListener(view ->
                scrollTo(findViewById(R.id.cardAdminSession)));
    }

    private void scrollTo(View target) {
        if (target == null) return;
        dashboardScroll.post(() -> dashboardScroll.smoothScrollTo(0, target.getTop()));
    }

    private void loadDashboard() {
        setLoading(true, getString(R.string.loading_dashboard));
        repository.load(new ApiCallback<AdminDashboardData>() {
            @Override
            public void onSuccess(AdminDashboardData data) {
                if (!isUsable()) return;
                setLoading(false, "");
                render(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false, error == null ? getString(R.string.dashboard_load_error)
                        : error.getMessage());
                if (error != null && error.isSessionExpired()) handleApiError(error);
            }
        });
    }

    private void render(AdminDashboardData data) {
        AdminDashboardData.Kpis kpis = data.getKpis();
        setText(R.id.textAdminWelcomeUsers, number(kpis.getDailyActiveUsers()));
        setText(R.id.textAdminWelcomeCourses, number(data.getTopCourses().size()));
        setText(R.id.textAdminDau, number(kpis.getDailyActiveUsers()));
        setText(R.id.textAdminSignups, number(kpis.getSignupsLastSevenDays()));
        setText(R.id.textAdminRevenue, currency.format(kpis.getGrossToday()));
        setText(R.id.textAdminTransactionsToday, number(kpis.getTransactionsToday()));
        setText(R.id.textAdminRefundRate,
                getString(R.string.percent_format, kpis.getRefundRate()));
        setText(R.id.textAdminPending, number(kpis.getApprovalsPending()));

        AdminDashboardData.ActiveUsers active = data.getActiveUsers();
        setText(R.id.textAdminActiveUsers, number(active.getCount()));
        setText(R.id.textAdminActiveWindow,
                getString(R.string.active_window_format, active.getWindowMinutes()));

        AdminDashboardData.Security security = data.getSecurity();
        setText(R.id.textAdminFailedLogins, number(security.getFailedLogins()));
        setText(R.id.textAdminLockedAccounts, number(security.getLockedAccounts()));
        setText(R.id.textAdminSslDays,
                getString(R.string.day_count_format, security.getSslDaysToExpire()));

        AdminDashboardData.SystemHealth system = data.getSystemHealth();
        bindMetric(R.id.progressAdminCpu, R.id.textAdminCpu, system.getCpuPercent());
        bindMetric(R.id.progressAdminRam, R.id.textAdminRam, system.getRamPercent());
        bindMetric(R.id.progressAdminDisk, R.id.textAdminDisk, system.getDiskPercent());
        setText(R.id.textAdminBackup, getString(R.string.backup_status_format,
                emptyFallback(system.getBackupLastRun()), emptyFallback(system.getBackupStatus())));

        renderCourses(data.getTopCourses());
        renderTransactions(data.getRecentTransactions());
        tuneAdminCards(findViewById(R.id.adminDashboardRoot));
    }

    private void tuneAdminCards(View root) {
        if (root instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) root;
            float density = getResources().getDisplayMetrics().density;
            card.setRadius(20f * density);
            card.setCardElevation(2f * density);
            card.setMaxCardElevation(3f * density);
            card.setUseCompatPadding(false);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                tuneAdminCards(group.getChildAt(index));
            }
        }
    }

    private void renderCourses(List<AdminDashboardData.CourseItem> courses) {
        topCoursesContainer.removeAllViews();
        TextView empty = findViewById(R.id.textAdminCoursesEmpty);
        empty.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int index = 0; index < courses.size(); index++) {
            AdminDashboardData.CourseItem item = courses.get(index);
            View row = inflater.inflate(R.layout.admin_item_top_course, topCoursesContainer, false);
            ((TextView) row.findViewById(R.id.textAdminCourseRank)).setText(String.valueOf(index + 1));
            ((TextView) row.findViewById(R.id.textAdminCourseTitle)).setText(item.getTitle());
            ((TextView) row.findViewById(R.id.textAdminCourseEnrollments)).setText(
                    getString(R.string.enrollment_count_format, item.getEnrollments()));
            row.setOnClickListener(view -> openManagementFeature("admin_courses"));
            topCoursesContainer.addView(row);
        }
    }

    private void renderTransactions(List<AdminDashboardData.TransactionItem> transactions) {
        transactionsContainer.removeAllViews();
        TextView empty = findViewById(R.id.textAdminTransactionsEmpty);
        empty.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminDashboardData.TransactionItem item : transactions) {
            View row = inflater.inflate(R.layout.admin_item_transaction, transactionsContainer, false);
            ((TextView) row.findViewById(R.id.textAdminTransactionId)).setText(
                    getString(R.string.transaction_id_format, item.getId()));
            ((TextView) row.findViewById(R.id.textAdminTransactionUser)).setText(item.getUser());
            ((TextView) row.findViewById(R.id.textAdminTransactionCourse)).setText(item.getCourse());
            ((TextView) row.findViewById(R.id.textAdminTransactionAmount)).setText(
                    currency.format(item.getAmount()));
            ((TextView) row.findViewById(R.id.textAdminTransactionStatus)).setText(item.getStatus());
            row.setOnClickListener(view -> openManagementFeature("admin_transactions"));
            transactionsContainer.addView(row);
        }
    }

    private void bindMetric(int progressId, int textId, int value) {
        ((ProgressBar) findViewById(progressId)).setProgress(value);
        setText(textId, getString(R.string.integer_percent_format, value));
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        statusText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setText(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String number(int value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    private String emptyFallback(String value) {
        return value == null || value.trim().isEmpty() ? getString(R.string.not_available) : value;
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
