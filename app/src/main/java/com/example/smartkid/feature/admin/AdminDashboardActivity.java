package com.example.smartkid.feature.admin;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.core.widget.NestedScrollView;

import com.example.smartkid.R;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.feature.admin.data.AdminDashboardRepository;
import com.example.smartkid.feature.admin.model.AdminDashboardData;
import com.example.smartkid.feature.admin.ui.AdminActivityChartView;
import com.example.smartkid.feature.management.RoleDashboardActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native XML implementation of the former Flutter admin experience. */
public final class AdminDashboardActivity extends RoleDashboardActivity {
    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_USERS = 1;
    private static final int PAGE_CONTENT = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");

    private AdminDashboardRepository repository;
    private ProgressBar progressBar;
    private TextView statusText;
    private NestedScrollView dashboardScroll;
    private ViewFlipper pageFlipper;
    private TextView[] navItems;
    private AdminActivityChartView activityChart;
    private ProgressBar chartProgress;
    private TextView chartPeriod;
    private TextView chartEmpty;
    private TextView[] chartTabs;
    private int selectedPage;
    private float swipeStartX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireRole("admin")) return;
        try {
            setContentView(R.layout.admin_activity_dashboard);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.adminDashboardRoot,
                    R.drawable.admin_bg_screen, true);
            findViewById(R.id.adminDashboardRoot).setBackgroundResource(R.drawable.admin_bg_screen);
            repository = new AdminDashboardRepository(this);
            bindViews();
            bindIdentity();
            bindNavigation();
            bindDashboardActions();
            bindSectionPages();
            bindSettings();
            bindChart();
            selectPage(PAGE_OVERVIEW, false);
            renderAdminTools(null);
            loadDashboard();
            loadChartPeriod("7_days", 7, getString(R.string.admin_chart_7_days));
        } catch (Exception exception) {
            AppLogger.error(this, "AdminDashboardActivity", "Không thể tạo dashboard", exception);
            showErrorDialog("Không thể mở bảng điều khiển quản trị");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressAdminDashboard);
        statusText = findViewById(R.id.textAdminDashboardStatus);
        dashboardScroll = findViewById(R.id.scrollAdminDashboard);
        pageFlipper = findViewById(R.id.adminPageFlipper);
        activityChart = findViewById(R.id.adminActivityChart);
        chartProgress = findViewById(R.id.progressAdminChart);
        chartPeriod = findViewById(R.id.textAdminChartPeriod);
        chartEmpty = findViewById(R.id.textAdminChartNoData);
        navItems = new TextView[]{findViewById(R.id.buttonAdminNavOverview),
                findViewById(R.id.buttonAdminNavUsers), findViewById(R.id.buttonAdminNavContent),
                findViewById(R.id.buttonAdminNavSettings)};
        chartTabs = new TextView[]{findViewById(R.id.buttonAdminChart7),
                findViewById(R.id.buttonAdminChart30), findViewById(R.id.buttonAdminChart90),
                findViewById(R.id.buttonAdminChartCustom)};
        if (progressBar == null || statusText == null || dashboardScroll == null
                || pageFlipper == null || activityChart == null) {
            throw new IllegalStateException("Dashboard quản trị thiếu thành phần bắt buộc");
        }
    }

    private void bindIdentity() {
        User user = currentUser();
        String name = firstNonEmpty(user.getFullName(), user.getUsername(),
                getString(R.string.admin_role_name));
        String email = firstNonEmpty(user.getEmail(), getString(R.string.admin_account_fallback));
        String initials = initials(name);
        setText(R.id.textAdminAvatar, initials);
        setText(R.id.textAdminSettingsAvatar, initials);
        setText(R.id.textAdminSettingsName, name);
        setText(R.id.textAdminSettingsEmail, email);
    }

    private void bindNavigation() {
        navItems[0].setOnClickListener(view -> selectPage(PAGE_OVERVIEW, true));
        navItems[1].setOnClickListener(view -> selectPage(PAGE_USERS, true));
        navItems[2].setOnClickListener(view -> selectPage(PAGE_CONTENT, true));
        navItems[3].setOnClickListener(view -> selectPage(PAGE_SETTINGS, true));
        pageFlipper.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getX();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float distance = event.getX() - swipeStartX;
                if (Math.abs(distance) > dp(64)) {
                    int target = selectedPage + (distance < 0 ? 1 : -1);
                    selectPage(Math.max(PAGE_OVERVIEW, Math.min(PAGE_SETTINGS, target)), true);
                }
            }
            return false;
        });
    }

    private void bindDashboardActions() {
        findViewById(R.id.buttonAdminMenu).setOnClickListener(view ->
                scrollTo(findViewById(R.id.textAdminManagementTitle)));
        findViewById(R.id.buttonAdminNotifications).setOnClickListener(view ->
                openManagementFeature("admin_notifications"));
        findViewById(R.id.buttonAdminProfile).setOnClickListener(view ->
                selectPage(PAGE_SETTINGS, true));
        bindFeature(R.id.buttonAdminUsers, "admin_users");
        bindFeature(R.id.buttonAdminApprovals, "admin_approval");
        bindFeature(R.id.buttonAdminTransactions, "admin_transactions");
        bindFeature(R.id.buttonAdminHealth, "admin_health");
        bindFeature(R.id.cardAdminKpiDau, "admin_active_users");
        bindFeature(R.id.cardAdminKpiSignups, "admin_report_users");
        bindFeature(R.id.cardAdminKpiRevenue, "admin_report_revenue");
        bindFeature(R.id.cardAdminKpiTransactions, "admin_transactions");
        bindFeature(R.id.cardAdminKpiRefund, "admin_transactions");
        bindFeature(R.id.cardAdminKpiPending, "admin_approval");
    }

    private void bindSectionPages() {
        LinearLayout users = findViewById(R.id.containerAdminUserActions);
        addSectionAction(users, "admin_users", getString(R.string.manage_users),
                getString(R.string.admin_action_manage_users_description), R.drawable.admin_ic_users,
                color(R.color.admin_blue));
        addSectionAction(users, "admin_active_users", getString(R.string.active_users_title),
                getString(R.string.admin_action_active_users_description), R.drawable.admin_ic_users,
                color(R.color.admin_green));
        addSectionAction(users, "admin_report_users", "Báo cáo người dùng",
                getString(R.string.admin_action_report_users_description), R.drawable.admin_ic_chart,
                Color.rgb(115, 87, 232));
        addSectionAction(users, "admin_security", getString(R.string.security_overview_title),
                getString(R.string.admin_action_security_description), R.drawable.role_ic_security,
                color(R.color.admin_cyan));

        LinearLayout content = findViewById(R.id.containerAdminContentActions);
        addSectionAction(content, "admin_courses", "Quản lý khóa học",
                getString(R.string.admin_action_courses_description), R.drawable.admin_ic_course,
                color(R.color.admin_green));
        addSectionAction(content, "admin_approval", getString(R.string.approve_courses),
                getString(R.string.admin_action_approval_description), R.drawable.admin_ic_approval,
                color(R.color.admin_orange));
        addSectionAction(content, "admin_report_content", "Báo cáo nội dung",
                getString(R.string.admin_action_report_content_description), R.drawable.admin_ic_chart,
                Color.rgb(115, 87, 232));
        addSectionAction(content, "admin_report_learning", "Báo cáo học tập",
                getString(R.string.admin_action_report_learning_description), R.drawable.role_ic_chart,
                color(R.color.admin_cyan));
    }

    private void bindSettings() {
        configureSetting(R.id.buttonAdminSettingsSecurity, R.drawable.role_ic_security,
                getString(R.string.security_overview_title),
                getString(R.string.admin_security_subtitle), "admin_security");
        configureSetting(R.id.buttonAdminSettingsHealth, R.drawable.admin_ic_health,
                getString(R.string.system_health), getString(R.string.admin_health_subtitle),
                "admin_health");
        configureSetting(R.id.buttonAdminSettingsConfig, R.drawable.admin_ic_settings,
                getString(R.string.admin_config_title), getString(R.string.admin_config_subtitle),
                "admin_config");
        bindLogoutAction(R.id.buttonAdminLogout, R.string.admin_logout_dialog_title,
                R.string.admin_logout_dialog_message);
    }

    private void bindChart() {
        chartTabs[0].setOnClickListener(view -> loadChartPeriod(
                "7_days", 7, getString(R.string.admin_chart_7_days)));
        chartTabs[1].setOnClickListener(view -> loadChartPeriod(
                "30_days", 30, getString(R.string.admin_chart_30_days)));
        chartTabs[2].setOnClickListener(view -> loadChartPeriod(
                "90_days", 90, getString(R.string.admin_chart_90_days)));
        chartTabs[3].setOnClickListener(view -> selectCustomChartRange());
    }

    private void selectPage(int page, boolean animate) {
        if (page < PAGE_OVERVIEW || page > PAGE_SETTINGS) return;
        boolean pageChanged = page != selectedPage || pageFlipper.getDisplayedChild() != page;
        pageFlipper.setInAnimation(null);
        pageFlipper.setOutAnimation(null);
        selectedPage = page;
        pageFlipper.setDisplayedChild(page);
        long navigationDuration = animate && pageChanged ? 120L : 0L;
        for (int index = 0; index < navItems.length; index++) {
            boolean selected = index == page;
            navItems[index].animate().cancel();
            navItems[index].setSelected(selected);
            navItems[index].animate().scaleX(selected ? 1.04f : 1f)
                    .scaleY(selected ? 1.04f : 1f).setDuration(navigationDuration).start();
        }
    }

    private void loadDashboard() {
        setLoading(true, "");
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
        setText(R.id.textAdminDau, number(kpis.getDailyActiveUsers()));
        setText(R.id.textAdminSignups, number(kpis.getSignupsLastSevenDays()));
        setText(R.id.textAdminRevenue, money(kpis.getGrossToday()));
        setText(R.id.textAdminTransactionsToday, number(kpis.getTransactionsToday()));
        setText(R.id.textAdminRefundRate,
                getString(R.string.percent_format, kpis.getRefundRate()));
        setText(R.id.textAdminPending, number(kpis.getApprovalsPending()));
        renderAdminTools(data);
    }

    private void renderAdminTools(AdminDashboardData data) {
        LinearLayout container = findViewById(R.id.containerAdminManagement);
        container.removeAllViews();
        List<ToolSpec> tools = tools(data);
        setText(R.id.textAdminToolCount,
                getString(R.string.admin_tools_count_format, tools.size()));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ToolSpec tool : tools) {
            View row = inflater.inflate(R.layout.admin_item_tool, container, false);
            MaterialCardView card = row.findViewById(R.id.cardAdminTool);
            FrameLayout iconContainer = row.findViewById(R.id.containerAdminToolIcon);
            ImageView icon = row.findViewById(R.id.imageAdminToolIcon);
            TextView title = row.findViewById(R.id.textAdminToolTitle);
            TextView description = row.findViewById(R.id.textAdminToolDescription);
            TextView badge = row.findViewById(R.id.textAdminToolBadge);
            iconContainer.setBackground(roundedGradient(tool.startColor, tool.endColor, 14));
            icon.setImageResource(tool.iconRes);
            title.setText(tool.title);
            description.setText(tool.description);
            if (!tool.badge.isEmpty()) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(tool.badge);
                badge.setTextColor(tool.startColor);
                badge.setBackground(roundedSolid(withAlpha(tool.startColor, 31), 10));
            }
            card.setOnClickListener(view -> openManagementFeature(tool.key));
            container.addView(row);
        }
    }

    private List<ToolSpec> tools(AdminDashboardData data) {
        int active = data == null ? 0 : data.getActiveUsers().getCount();
        int window = data == null ? 10 : data.getActiveUsers().getWindowMinutes();
        int cpu = data == null ? 0 : data.getSystemHealth().getCpuPercent();
        int ram = data == null ? 0 : data.getSystemHealth().getRamPercent();
        int disk = data == null ? 0 : data.getSystemHealth().getDiskPercent();
        List<ToolSpec> tools = new ArrayList<>();
        tools.add(tool("admin_active_users", "Người dùng đang hoạt động",
                "Theo dõi người dùng trực tuyến trong " + window + " phút gần nhất",
                R.drawable.admin_ic_users, 0xFF3B82F6, 0xFF60A5FA, active + " online"));
        tools.add(tool("admin_users", "Quản lý người dùng", "Phân quyền, khóa và quản lý tài khoản",
                R.drawable.admin_ic_users, 0xFF635BFF, 0xFF818CF8, ""));
        tools.add(tool("admin_courses", "Quản lý khóa học",
                "Quản lý nội dung, trạng thái và danh mục khóa học",
                R.drawable.admin_ic_course, 0xFF10B981, 0xFF34D399, ""));
        tools.add(tool("admin_approval", "Duyệt khóa học",
                "Kiểm tra và phê duyệt nội dung từ giáo viên",
                R.drawable.admin_ic_approval, 0xFFF59E0B, 0xFFFBBF24, ""));
        tools.add(tool("admin_health", "Sức khỏe hệ thống",
                "CPU " + cpu + "% • RAM " + ram + "% • Disk " + disk + "%",
                R.drawable.admin_ic_health, 0xFFEF4444, 0xFFF87171, cpu + "%"));
        tools.add(tool("admin_activity", "Nhật ký hoạt động",
                "Theo dõi lịch sử thao tác của quản trị viên",
                R.drawable.admin_ic_chart, 0xFF8B5CF6, 0xFFA78BFA, ""));
        tools.add(tool("admin_security", "Bảo mật",
                "Chính sách bảo mật và trạng thái chứng chỉ",
                R.drawable.role_ic_security, 0xFF0EA5E9, 0xFF38BDF8, ""));
        tools.add(tool("admin_sessions", "Phiên đăng nhập",
                "Quản lý thiết bị và phiên đang kết nối",
                R.drawable.admin_ic_settings, 0xFFEC4899, 0xFFF472B6, ""));
        tools.add(tool("admin_config", "Cấu hình hệ thống",
                "Thông số vận hành, email và cấu hình API",
                R.drawable.admin_ic_settings, 0xFF64748B, 0xFF94A3B8, ""));
        tools.add(tool("admin_backups", "Sao lưu hệ thống",
                "Kiểm tra lịch sao lưu và khôi phục dữ liệu",
                R.drawable.admin_ic_refresh, 0xFF0284C7, 0xFF38BDF8, ""));
        tools.add(tool("admin_report_revenue", "Báo cáo doanh thu",
                "Thống kê doanh thu theo mốc thời gian",
                R.drawable.admin_ic_chart, 0xFF059669, 0xFF34D399, ""));
        tools.add(tool("admin_report_users", "Báo cáo người dùng",
                "Phân tích tăng trưởng và người dùng hoạt động",
                R.drawable.admin_ic_chart, 0xFF4F46E5, 0xFF818CF8, ""));
        tools.add(tool("admin_report_learning", "Báo cáo học tập",
                "Tiến độ, điểm số và thời gian học tập",
                R.drawable.role_ic_chart, 0xFFD97706, 0xFFFBBF24, ""));
        tools.add(tool("admin_report_content", "Báo cáo nội dung",
                "Hiệu quả và mức độ tương tác với bài học",
                R.drawable.admin_ic_chart, 0xFF7C3AED, 0xFFA78BFA, ""));
        tools.add(tool("admin_transactions", "Giao dịch",
                "Tra cứu thanh toán và trạng thái giao dịch",
                R.drawable.admin_ic_card, 0xFF2563EB, 0xFF60A5FA, ""));
        tools.add(tool("admin_notifications", "Thông báo",
                "Xem và quản lý thông báo hệ thống",
                R.drawable.admin_ic_bell, 0xFFDC2626, 0xFFF87171, ""));
        return tools;
    }

    private void loadChartPeriod(String key, int days, String label) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        loadChartRange(key, label, from, to);
    }

    private void loadChartRange(String key, String label, LocalDate from, LocalDate to) {
        selectChartTab(key);
        chartProgress.setVisibility(View.VISIBLE);
        chartPeriod.setVisibility(View.GONE);
        chartEmpty.setVisibility(View.GONE);
        repository.loadActivityChart(API_DATE.format(from), API_DATE.format(to),
                new ApiCallback<List<AdminDashboardData.ActivityPoint>>() {
                    @Override
                    public void onSuccess(List<AdminDashboardData.ActivityPoint> data) {
                        if (!isUsable()) return;
                        renderChart(label, data);
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        renderChart(label, new ArrayList<>());
                    }
                });
    }

    private void renderChart(String label, List<AdminDashboardData.ActivityPoint> points) {
        chartProgress.setVisibility(View.GONE);
        chartPeriod.setVisibility(View.VISIBLE);
        chartPeriod.setText(label);
        List<String> labels = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        if (points != null && !points.isEmpty()) {
            int groupSize = Math.max(1, (int) Math.ceil(points.size() / 7d));
            for (int start = 0; start < points.size(); start += groupSize) {
                int end = Math.min(points.size(), start + groupSize);
                int total = 0;
                for (int index = start; index < end; index++) {
                    total += points.get(index).getNewUsers();
                }
                labels.add(shortDate(points.get(end - 1).getDate()));
                values.add((float) total);
            }
        }
        activityChart.setData(labels, values);
        chartEmpty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void selectCustomChartRange() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText("Chọn khoảng thời gian")
                        .setSelection(androidx.core.util.Pair.create(
                                MaterialDatePicker.todayInUtcMilliseconds() - 13L * 86400000L,
                                MaterialDatePicker.todayInUtcMilliseconds()))
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            LocalDate from = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate to = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC).toLocalDate();
            loadChartRange("custom", SHORT_DATE.format(from) + " – " + SHORT_DATE.format(to),
                    from, to);
        });
        picker.show(getSupportFragmentManager(), "admin_chart_range");
    }

    private void selectChartTab(String key) {
        String[] keys = {"7_days", "30_days", "90_days", "custom"};
        for (int index = 0; index < chartTabs.length; index++) {
            boolean selected = keys[index].equals(key);
            chartTabs[index].setBackgroundResource(selected
                    ? R.drawable.admin_bg_chart_tab_selected : android.R.color.transparent);
            chartTabs[index].setTextColor(color(selected
                    ? R.color.admin_primary : R.color.admin_text_secondary));
            chartTabs[index].setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void addSectionAction(LinearLayout container, String key, String title,
                                  String description, int iconRes, int accent) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.admin_item_section_action, container, false);
        MaterialCardView card = row.findViewById(R.id.cardAdminSectionAction);
        FrameLayout iconContainer = row.findViewById(R.id.containerAdminSectionActionIcon);
        ImageView icon = row.findViewById(R.id.imageAdminSectionActionIcon);
        ((TextView) row.findViewById(R.id.textAdminSectionActionTitle)).setText(title);
        ((TextView) row.findViewById(R.id.textAdminSectionActionDescription)).setText(description);
        iconContainer.setBackground(roundedSolid(withAlpha(accent, 28), 15));
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(accent));
        card.setOnClickListener(view -> openManagementFeature(key));
        container.addView(row);
    }

    private void configureSetting(int rootId, int iconRes, String title,
                                  String subtitle, String key) {
        View row = findViewById(rootId);
        ((ImageView) row.findViewById(R.id.imageAdminSettingIcon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.textAdminSettingTitle)).setText(title);
        ((TextView) row.findViewById(R.id.textAdminSettingSubtitle)).setText(subtitle);
        row.setOnClickListener(view -> openManagementFeature(key));
    }

    private void bindFeature(int viewId, String key) {
        findViewById(viewId).setOnClickListener(view -> openManagementFeature(key));
    }

    private void scrollTo(View target) {
        if (target == null) return;
        dashboardScroll.post(() -> dashboardScroll.smoothScrollTo(0, target.getTop()));
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(message == null ? "" : message);
        statusText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setText(int id, String value) {
        TextView view = findViewById(id);
        if (view != null) view.setText(value);
    }

    private String number(int value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(value);
    }

    private String money(double value) {
        return NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                .format(Math.round(value)) + " đ";
    }

    private String shortDate(String value) {
        try {
            return SHORT_DATE.format(LocalDate.parse(value, API_DATE));
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private String initials(String name) {
        String safe = name == null ? "" : name.trim();
        if (safe.isEmpty()) return "AD";
        String[] parts = safe.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private int color(int colorRes) {
        return getColor(colorRes);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private GradientDrawable roundedSolid(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedGradient(int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private ToolSpec tool(String key, String title, String description, int icon,
                          int startColor, int endColor, String badge) {
        return new ToolSpec(key, title, description, icon, startColor, endColor, badge);
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static final class ToolSpec {
        private final String key;
        private final String title;
        private final String description;
        private final int iconRes;
        private final int startColor;
        private final int endColor;
        private final String badge;

        private ToolSpec(String key, String title, String description, int iconRes,
                         int startColor, int endColor, String badge) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.startColor = startColor;
            this.endColor = endColor;
            this.badge = badge == null ? "" : badge;
        }
    }
}
