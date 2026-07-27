package com.example.smartkid.feature.admin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/** Editable forms for security policy and system configuration backed by real admin APIs. */
public final class AdminSettingsActivity extends BaseActivity {
    public static final String EXTRA_MODE = "admin_settings_mode";

    private static final String SECURITY_ENDPOINT = "admin/security/policy/";
    private static final String SYSTEM_ENDPOINT = "admin/system/config/";

    private ManagementRepository repository;
    private String mode;
    private ProgressBar progress;
    private TextView status;
    private View content;
    private MaterialButton saveButton;

    private SwitchMaterial securityAdmin2fa;
    private SwitchMaterial securityTeacher2fa;
    private TextInputLayout securityLoginFailuresLayout;
    private TextInputLayout securityWindowLayout;
    private TextInputLayout securityLockAttemptsLayout;
    private TextInputLayout securityLockMinutesLayout;
    private TextInputLayout securityBanStrikesLayout;
    private TextInputEditText securityLoginFailures;
    private TextInputEditText securityWindow;
    private TextInputEditText securityLockAttempts;
    private TextInputEditText securityLockMinutes;
    private TextInputEditText securityBanStrikes;
    private TextInputEditText securityRbacNote;

    private TextInputLayout configSiteNameLayout;
    private TextInputLayout configTimezoneLayout;
    private TextInputLayout configDomainLayout;
    private TextInputLayout configSmtpPortLayout;
    private TextInputLayout configFromEmailLayout;
    private TextInputLayout configIdleTimeoutLayout;
    private TextInputLayout configMaxSessionLayout;
    private TextInputLayout configRememberDaysLayout;
    private TextInputLayout configMaintenanceDayLayout;
    private TextInputLayout configMaintenanceStartLayout;
    private TextInputLayout configMaintenanceEndLayout;
    private TextInputLayout configBackupRetentionLayout;
    private TextInputLayout configLogRetentionLayout;
    private TextInputEditText configSiteName;
    private MaterialAutoCompleteTextView configLanguage;
    private TextInputEditText configTimezone;
    private MaterialAutoCompleteTextView configCurrency;
    private TextInputEditText configLogoUrl;
    private TextInputEditText configDomain;
    private SwitchMaterial configForceHttps;
    private SwitchMaterial configHsts;
    private TextInputEditText configSmtpHost;
    private TextInputEditText configSmtpPort;
    private TextInputEditText configSmtpUsername;
    private TextInputEditText configSenderName;
    private TextInputEditText configFromEmail;
    private TextInputEditText configIdleTimeout;
    private TextInputEditText configMaxSession;
    private TextInputEditText configRememberDays;
    private SwitchMaterial configSingleDevice;
    private SwitchMaterial configGoogleSso;
    private TextInputEditText configGoogleClientId;
    private SwitchMaterial configMaintenance;
    private TextInputEditText configMaintenanceDay;
    private TextInputEditText configMaintenanceStart;
    private TextInputEditText configMaintenanceEnd;
    private MaterialAutoCompleteTextView configBackupSchedule;
    private TextInputEditText configBackupRetention;
    private SwitchMaterial configBackupEncrypted;
    private MaterialAutoCompleteTextView configLogLevel;
    private TextInputEditText configLogRetention;
    private SwitchMaterial configTraceId;
    private SwitchMaterial configZoom;
    private TextInputEditText configGa4;
    private MaterialAutoCompleteTextView configStorageProvider;
    private TextInputEditText configStorageBucket;
    private TextInputEditText configStorageRegion;

    public static Intent createIntent(Context context, String mode) {
        return new Intent(context, AdminSettingsActivity.class).putExtra(EXTRA_MODE, mode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            mode = getIntent() == null ? "" : safe(getIntent().getStringExtra(EXTRA_MODE));
            UserRole role = UserRole.fromString(new SessionManager(this).getUser().getRole());
            if (!role.isAdmin() || !AdminSettingsRules.supports(mode)) {
                showErrorDialog(getString(R.string.admin_course_video_admin_only));
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            if (AdminSettingsRules.MODE_SECURITY.equals(mode)) {
                setContentView(R.layout.admin_activity_security_settings);
                bindSecurityViews();
            } else {
                setContentView(R.layout.admin_activity_system_settings);
                bindSystemViews();
            }
            loadSettings();
        } catch (Exception exception) {
            AppLogger.error(this, "AdminSettingsActivity",
                    "Không thể mở biểu mẫu cấu hình", exception);
            showErrorDialog(getString(R.string.admin_settings_load_error));
        }
    }

    private void bindSecurityViews() {
        MaterialToolbar toolbar = required(R.id.toolbarAdminSecuritySettings);
        progress = required(R.id.progressAdminSecuritySettings);
        status = required(R.id.textAdminSecurityStatus);
        content = required(R.id.contentAdminSecuritySettings);
        saveButton = required(R.id.buttonSaveSecuritySettings);
        securityAdmin2fa = required(R.id.switchSecurityAdmin2fa);
        securityTeacher2fa = required(R.id.switchSecurityTeacher2fa);
        securityLoginFailuresLayout = required(R.id.layoutSecurityLoginFailures);
        securityWindowLayout = required(R.id.layoutSecurityWindowMinutes);
        securityLockAttemptsLayout = required(R.id.layoutSecurityLockAttempts);
        securityLockMinutesLayout = required(R.id.layoutSecurityLockMinutes);
        securityBanStrikesLayout = required(R.id.layoutSecurityBanStrikes);
        securityLoginFailures = required(R.id.inputSecurityLoginFailures);
        securityWindow = required(R.id.inputSecurityWindowMinutes);
        securityLockAttempts = required(R.id.inputSecurityLockAttempts);
        securityLockMinutes = required(R.id.inputSecurityLockMinutes);
        securityBanStrikes = required(R.id.inputSecurityBanStrikes);
        securityRbacNote = required(R.id.inputSecurityRbacNote);
        toolbar.setNavigationOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> saveSecurity());
    }

    private void bindSystemViews() {
        MaterialToolbar toolbar = required(R.id.toolbarAdminSystemSettings);
        progress = required(R.id.progressAdminSystemSettings);
        status = required(R.id.textAdminSystemStatus);
        content = required(R.id.contentAdminSystemSettings);
        saveButton = required(R.id.buttonSaveSystemSettings);
        configSiteNameLayout = required(R.id.layoutConfigSiteName);
        configTimezoneLayout = required(R.id.layoutConfigTimezone);
        configDomainLayout = required(R.id.layoutConfigDomain);
        configSmtpPortLayout = required(R.id.layoutConfigSmtpPort);
        configFromEmailLayout = required(R.id.layoutConfigFromEmail);
        configIdleTimeoutLayout = required(R.id.layoutConfigIdleTimeout);
        configMaxSessionLayout = required(R.id.layoutConfigMaxSession);
        configRememberDaysLayout = required(R.id.layoutConfigRememberDays);
        configMaintenanceDayLayout = required(R.id.layoutConfigMaintenanceDay);
        configMaintenanceStartLayout = required(R.id.layoutConfigMaintenanceStart);
        configMaintenanceEndLayout = required(R.id.layoutConfigMaintenanceEnd);
        configBackupRetentionLayout = required(R.id.layoutConfigBackupRetention);
        configLogRetentionLayout = required(R.id.layoutConfigLogRetention);
        configSiteName = required(R.id.inputConfigSiteName);
        configLanguage = required(R.id.inputConfigLanguage);
        configTimezone = required(R.id.inputConfigTimezone);
        configCurrency = required(R.id.inputConfigCurrency);
        configLogoUrl = required(R.id.inputConfigLogoUrl);
        configDomain = required(R.id.inputConfigDomain);
        configForceHttps = required(R.id.switchConfigForceHttps);
        configHsts = required(R.id.switchConfigHsts);
        configSmtpHost = required(R.id.inputConfigSmtpHost);
        configSmtpPort = required(R.id.inputConfigSmtpPort);
        configSmtpUsername = required(R.id.inputConfigSmtpUsername);
        configSenderName = required(R.id.inputConfigSenderName);
        configFromEmail = required(R.id.inputConfigFromEmail);
        configIdleTimeout = required(R.id.inputConfigIdleTimeout);
        configMaxSession = required(R.id.inputConfigMaxSession);
        configRememberDays = required(R.id.inputConfigRememberDays);
        configSingleDevice = required(R.id.switchConfigSingleDevice);
        configGoogleSso = required(R.id.switchConfigGoogleSso);
        configGoogleClientId = required(R.id.inputConfigGoogleClientId);
        configMaintenance = required(R.id.switchConfigMaintenance);
        configMaintenanceDay = required(R.id.inputConfigMaintenanceDay);
        configMaintenanceStart = required(R.id.inputConfigMaintenanceStart);
        configMaintenanceEnd = required(R.id.inputConfigMaintenanceEnd);
        configBackupSchedule = required(R.id.inputConfigBackupSchedule);
        configBackupRetention = required(R.id.inputConfigBackupRetention);
        configBackupEncrypted = required(R.id.switchConfigBackupEncrypted);
        configLogLevel = required(R.id.inputConfigLogLevel);
        configLogRetention = required(R.id.inputConfigLogRetention);
        configTraceId = required(R.id.switchConfigTraceId);
        configZoom = required(R.id.switchConfigZoom);
        configGa4 = required(R.id.inputConfigGa4);
        configStorageProvider = required(R.id.inputConfigStorageProvider);
        configStorageBucket = required(R.id.inputConfigStorageBucket);
        configStorageRegion = required(R.id.inputConfigStorageRegion);
        toolbar.setNavigationOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> saveSystem());
        configureDropdowns();
    }

    private void configureDropdowns() {
        configureDropdown(configLanguage,
                new String[]{getString(R.string.admin_option_vietnamese),
                        getString(R.string.admin_option_english)});
        configureDropdown(configCurrency, new String[]{"VND", "USD"});
        configureDropdown(configBackupSchedule,
                new String[]{getString(R.string.admin_option_daily),
                        getString(R.string.admin_option_weekly),
                        getString(R.string.admin_option_manual)});
        configureDropdown(configLogLevel,
                new String[]{getString(R.string.admin_option_log_debug),
                        getString(R.string.admin_option_log_info),
                        getString(R.string.admin_option_log_warning),
                        getString(R.string.admin_option_log_error)});
        configureDropdown(configStorageProvider,
                new String[]{getString(R.string.admin_option_storage_local),
                        getString(R.string.admin_option_storage_s3)});
    }

    private void configureDropdown(MaterialAutoCompleteTextView input, String[] labels) {
        input.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, labels));
    }

    private void loadSettings() {
        setLoading(true, getString(R.string.admin_settings_loading));
        repository.loadObject(endpoint(), new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                JSONObject source = data == null ? new JSONObject() : data;
                if (AdminSettingsRules.MODE_SECURITY.equals(mode)) bindSecurityData(source);
                else bindSystemData(source);
                setLoading(false, statusMessage(source));
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false, getString(R.string.admin_settings_load_error));
                handleApiError(error);
            }
        });
    }

    private void bindSecurityData(JSONObject source) {
        JSONObject twofa = child(source, "twoFA");
        JSONObject rate = child(source, "rateLimit");
        JSONObject lockout = child(source, "lockout");
        securityAdmin2fa.setChecked(SafeJson.bool(twofa, false, "enforceAdmin"));
        securityTeacher2fa.setChecked(SafeJson.bool(twofa, false, "enforceTeacher"));
        setNumber(securityLoginFailures, SafeJson.integer(rate, 5, "loginFailures"));
        setNumber(securityWindow, SafeJson.integer(rate, 10, "windowMin"));
        setNumber(securityLockAttempts, SafeJson.integer(lockout, 5, "attempts"));
        setNumber(securityLockMinutes, SafeJson.integer(lockout, 30, "lockMinutes"));
        setNumber(securityBanStrikes, SafeJson.integer(lockout, 5, "banStrikes"));
        securityRbacNote.setText(SafeJson.string(source, "", "rbacNote"));
    }

    private void bindSystemData(JSONObject source) {
        JSONObject brand = child(source, "brand");
        JSONObject domainEmail = child(source, "domainEmail");
        JSONObject smtp = child(domainEmail, "smtp");
        JSONObject auth = child(source, "authSession");
        JSONObject maintenance = child(source, "maintenance");
        JSONObject window = child(maintenance, "window");
        JSONObject backup = child(source, "backup");
        JSONObject logging = child(source, "logging");
        JSONObject integrations = child(source, "integrations");
        JSONObject analytics = child(integrations, "analytics");
        JSONObject zoom = child(integrations, "zoom");
        JSONObject storage = child(integrations, "storage");

        configSiteName.setText(SafeJson.string(brand, "SmartKid", "siteName"));
        setDropdown(configLanguage, languageLabel(SafeJson.string(brand, "vi", "language")));
        configTimezone.setText(SafeJson.string(brand, "Asia/Ho_Chi_Minh", "timezone"));
        setDropdown(configCurrency, SafeJson.string(brand, "VND", "currency"));
        configLogoUrl.setText(SafeJson.string(brand, "", "logoUrl"));
        configDomain.setText(SafeJson.string(domainEmail, "localhost", "domain"));
        configForceHttps.setChecked(SafeJson.bool(domainEmail, true, "forceHttps"));
        configHsts.setChecked(SafeJson.bool(domainEmail, true, "hsts"));
        configSmtpHost.setText(SafeJson.string(smtp, "", "host"));
        setNumber(configSmtpPort, SafeJson.integer(smtp, 587, "port"));
        configSmtpUsername.setText(SafeJson.string(smtp, "", "username"));
        configSenderName.setText(SafeJson.string(smtp, "", "senderName"));
        configFromEmail.setText(SafeJson.string(smtp, "", "fromEmail"));
        setNumber(configIdleTimeout, SafeJson.integer(auth, 30, "idleTimeoutMin"));
        setNumber(configMaxSession, SafeJson.integer(auth, 24, "maxSessionHours"));
        setNumber(configRememberDays, SafeJson.integer(auth, 14, "rememberMeDays"));
        configSingleDevice.setChecked(SafeJson.bool(auth, true, "singleDeviceOnly"));
        configGoogleSso.setChecked(SafeJson.bool(auth, false, "ssoGoogleEnabled"));
        configGoogleClientId.setText(SafeJson.string(auth, "", "googleClientId"));
        configMaintenance.setChecked(SafeJson.bool(maintenance, false, "enabled"));
        setNumber(configMaintenanceDay, SafeJson.integer(window, 0, "dayOfWeek"));
        configMaintenanceStart.setText(SafeJson.string(window, "01:00", "start"));
        configMaintenanceEnd.setText(SafeJson.string(window, "03:00", "end"));
        setDropdown(configBackupSchedule,
                scheduleLabel(SafeJson.string(backup, "daily", "schedule")));
        setNumber(configBackupRetention, SafeJson.integer(backup, 30, "retentionDays"));
        configBackupEncrypted.setChecked(SafeJson.bool(backup, true, "encrypted"));
        setDropdown(configLogLevel,
                logLevelLabel(SafeJson.string(logging, "info", "level")));
        setNumber(configLogRetention, SafeJson.integer(logging, 90, "retentionDays"));
        configTraceId.setChecked(SafeJson.bool(logging, true, "traceIdEnabled"));
        configZoom.setChecked(SafeJson.bool(zoom, false, "enabled"));
        configGa4.setText(SafeJson.string(analytics, "", "ga4MeasurementId"));
        setDropdown(configStorageProvider,
                storageLabel(SafeJson.string(storage, "local", "provider")));
        configStorageBucket.setText(SafeJson.string(storage, "", "bucket"));
        configStorageRegion.setText(SafeJson.string(storage, "", "region"));
    }

    private void saveSecurity() {
        Integer loginFailures = number(securityLoginFailuresLayout,
                securityLoginFailures, 1, 50);
        Integer windowMin = number(securityWindowLayout, securityWindow, 1, 1440);
        Integer attempts = number(securityLockAttemptsLayout,
                securityLockAttempts, 1, 50);
        Integer lockMinutes = number(securityLockMinutesLayout,
                securityLockMinutes, 1, 10080);
        Integer banStrikes = number(securityBanStrikesLayout,
                securityBanStrikes, 1, 50);
        if (loginFailures == null || windowMin == null || attempts == null
                || lockMinutes == null || banStrikes == null) return;
        try {
            JSONObject payload = new JSONObject()
                    .put("twoFA", new JSONObject()
                            .put("enforceAdmin", securityAdmin2fa.isChecked())
                            .put("enforceTeacher", securityTeacher2fa.isChecked()))
                    .put("rateLimit", new JSONObject()
                            .put("loginFailures", loginFailures)
                            .put("windowMin", windowMin))
                    .put("lockout", new JSONObject()
                            .put("attempts", attempts)
                            .put("lockMinutes", lockMinutes)
                            .put("banStrikes", banStrikes))
                    .put("rbacNote", text(securityRbacNote));
            save(payload);
        } catch (Exception exception) {
            failSave(exception);
        }
    }

    private void saveSystem() {
        String siteName = requiredText(configSiteNameLayout, configSiteName);
        String timezone = requiredText(configTimezoneLayout, configTimezone);
        String domain = requiredText(configDomainLayout, configDomain);
        Integer smtpPort = number(configSmtpPortLayout, configSmtpPort, 1, 65535);
        Integer idle = number(configIdleTimeoutLayout, configIdleTimeout, 1, 1440);
        Integer maxSession = number(configMaxSessionLayout, configMaxSession, 1, 720);
        Integer remember = number(configRememberDaysLayout, configRememberDays, 0, 365);
        Integer day = number(configMaintenanceDayLayout, configMaintenanceDay, 0, 6);
        Integer backupRetention = number(configBackupRetentionLayout,
                configBackupRetention, 1, 3650);
        Integer logRetention = number(configLogRetentionLayout,
                configLogRetention, 1, 3650);
        boolean startValid = validateTime(configMaintenanceStartLayout, configMaintenanceStart);
        boolean endValid = validateTime(configMaintenanceEndLayout, configMaintenanceEnd);
        boolean emailValid = AdminSettingsRules.validOptionalEmail(text(configFromEmail));
        configFromEmailLayout.setError(emailValid ? null
                : getString(R.string.admin_settings_invalid_email));
        if (siteName == null || timezone == null || domain == null || smtpPort == null
                || idle == null || maxSession == null || remember == null || day == null
                || backupRetention == null || logRetention == null || !startValid
                || !endValid || !emailValid) return;

        try {
            JSONObject payload = new JSONObject();
            payload.put("brand", new JSONObject()
                    .put("siteName", siteName)
                    .put("language", languageCode(text(configLanguage)))
                    .put("timezone", timezone)
                    .put("currency", text(configCurrency))
                    .put("logoUrl", text(configLogoUrl)));
            payload.put("domainEmail", new JSONObject()
                    .put("domain", domain)
                    .put("forceHttps", configForceHttps.isChecked())
                    .put("hsts", configHsts.isChecked())
                    .put("smtp", new JSONObject()
                            .put("host", text(configSmtpHost))
                            .put("port", smtpPort)
                            .put("username", text(configSmtpUsername))
                            .put("senderName", text(configSenderName))
                            .put("fromEmail", text(configFromEmail))));
            payload.put("authSession", new JSONObject()
                    .put("idleTimeoutMin", idle)
                    .put("maxSessionHours", maxSession)
                    .put("rememberMeDays", remember)
                    .put("ssoGoogleEnabled", configGoogleSso.isChecked())
                    .put("googleClientId", text(configGoogleClientId))
                    .put("singleDeviceOnly", configSingleDevice.isChecked()));
            payload.put("maintenance", new JSONObject()
                    .put("enabled", configMaintenance.isChecked())
                    .put("window", new JSONObject()
                            .put("dayOfWeek", day)
                            .put("start", text(configMaintenanceStart))
                            .put("end", text(configMaintenanceEnd))));
            payload.put("backup", new JSONObject()
                    .put("schedule", scheduleCode(text(configBackupSchedule)))
                    .put("retentionDays", backupRetention)
                    .put("encrypted", configBackupEncrypted.isChecked()));
            payload.put("logging", new JSONObject()
                    .put("level", logLevelCode(text(configLogLevel)))
                    .put("retentionDays", logRetention)
                    .put("traceIdEnabled", configTraceId.isChecked()));
            payload.put("integrations", new JSONObject()
                    .put("analytics", new JSONObject()
                            .put("ga4MeasurementId", text(configGa4)))
                    .put("zoom", new JSONObject().put("enabled", configZoom.isChecked()))
                    .put("storage", new JSONObject()
                            .put("provider", storageCode(text(configStorageProvider)))
                            .put("bucket", text(configStorageBucket))
                            .put("region", text(configStorageRegion))));
            save(payload);
        } catch (Exception exception) {
            failSave(exception);
        }
    }

    private void save(JSONObject payload) {
        setLoading(true, getString(R.string.admin_settings_saving));
        repository.action(Request.Method.PATCH, endpoint(), payload,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        JSONObject source = data == null ? new JSONObject() : data;
                        if (AdminSettingsRules.MODE_SECURITY.equals(mode)) {
                            bindSecurityData(source);
                        } else {
                            bindSystemData(source);
                        }
                        setLoading(false, statusMessage(source));
                        showShortMessage(getString(R.string.admin_settings_saved));
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false, getString(R.string.admin_settings_save_error));
                        handleApiError(error);
                    }
                });
    }

    private Integer number(TextInputLayout layout, TextInputEditText input, int min, int max) {
        Integer value = AdminSettingsRules.boundedInteger(text(input), min, max);
        layout.setError(value == null
                ? getString(R.string.admin_settings_invalid_number, min, max) : null);
        return value;
    }

    private String requiredText(TextInputLayout layout, TextInputEditText input) {
        String value = text(input);
        layout.setError(value.isEmpty() ? getString(R.string.admin_settings_required) : null);
        return value.isEmpty() ? null : value;
    }

    private boolean validateTime(TextInputLayout layout, TextInputEditText input) {
        boolean valid = AdminSettingsRules.validTime(text(input));
        layout.setError(valid ? null : getString(R.string.admin_settings_invalid_time));
        return valid;
    }

    private void failSave(Exception exception) {
        AppLogger.error(this, "AdminSettingsActivity",
                "Không thể chuẩn bị dữ liệu cấu hình", exception);
        setLoading(false, getString(R.string.admin_settings_save_error));
        showErrorDialog(getString(R.string.admin_settings_save_error));
    }

    private String endpoint() {
        return AdminSettingsRules.MODE_SECURITY.equals(mode) ? SECURITY_ENDPOINT : SYSTEM_ENDPOINT;
    }

    private String statusMessage(JSONObject source) {
        if (AdminSettingsRules.MODE_SECURITY.equals(mode)) {
            return getString(R.string.admin_settings_server_source);
        }
        int version = SafeJson.integer(source, 0, "version");
        String updatedAt = shortTimestamp(SafeJson.string(source, "", "updatedAt"));
        return version > 0 && !updatedAt.isEmpty()
                ? getString(R.string.admin_settings_version, version, updatedAt)
                : getString(R.string.admin_settings_server_source);
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        saveButton.setEnabled(!loading);
        content.setAlpha(loading ? 0.62f : 1f);
        status.setText(message == null ? "" : message);
    }

    private <T extends View> T required(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalStateException("Thiếu view " + id);
        return view;
    }

    private static JSONObject child(JSONObject source, String key) {
        JSONObject child = source == null ? null : source.optJSONObject(key);
        return child == null ? new JSONObject() : child;
    }

    private static void setNumber(TextInputEditText input, int value) {
        input.setText(String.valueOf(value));
    }

    private static void setDropdown(MaterialAutoCompleteTextView input, String value) {
        input.setText(value, false);
    }

    private static String text(TextView input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String languageLabel(String code) {
        return "en".equals(code) ? getString(R.string.admin_option_english)
                : getString(R.string.admin_option_vietnamese);
    }

    private String languageCode(String label) {
        return getString(R.string.admin_option_english).equals(label) ? "en" : "vi";
    }

    private String scheduleLabel(String code) {
        if ("weekly".equals(code)) return getString(R.string.admin_option_weekly);
        if ("manual".equals(code)) return getString(R.string.admin_option_manual);
        return getString(R.string.admin_option_daily);
    }

    private String scheduleCode(String label) {
        if (getString(R.string.admin_option_weekly).equals(label)) return "weekly";
        if (getString(R.string.admin_option_manual).equals(label)) return "manual";
        return "daily";
    }

    private String logLevelLabel(String code) {
        if ("debug".equals(code)) return getString(R.string.admin_option_log_debug);
        if ("warning".equals(code)) return getString(R.string.admin_option_log_warning);
        if ("error".equals(code)) return getString(R.string.admin_option_log_error);
        return getString(R.string.admin_option_log_info);
    }

    private String logLevelCode(String label) {
        if (getString(R.string.admin_option_log_debug).equals(label)) return "debug";
        if (getString(R.string.admin_option_log_warning).equals(label)) return "warning";
        if (getString(R.string.admin_option_log_error).equals(label)) return "error";
        return "info";
    }

    private String storageLabel(String code) {
        return "s3".equals(code) ? getString(R.string.admin_option_storage_s3)
                : getString(R.string.admin_option_storage_local);
    }

    private String storageCode(String label) {
        return getString(R.string.admin_option_storage_s3).equals(label) ? "s3" : "local";
    }

    private static String shortTimestamp(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim().replace('T', ' ');
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }
}
