package com.example.smartkid.ui.management;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.ui.common.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Biểu mẫu tạo mới theo vai trò, chỉ gửi dữ liệu người dùng nhập lên API. */
public class ManagementCreateActivity extends BaseActivity {
    public static final String EXTRA_KIND = "management_create_kind";
    private final Map<String, TextInputEditText> inputs = new LinkedHashMap<>();
    private LinearLayout container;
    private Spinner primarySpinner;
    private Spinner courseSpinner;
    private final List<FeatureItem> courseOptions = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView statusText;
    private View submitButton;
    private ManagementRepository repository;
    private String kind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_management_create);
            kind = getIntent() == null ? "" : safe(getIntent().getStringExtra(EXTRA_KIND));
            if (!isSupported(kind)) {
                showErrorDialog("Chức năng tạo mới không hợp lệ");
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarManagementCreate);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng");
            toolbar.setNavigationOnClickListener(view -> finish());
            toolbar.setTitle(titleForKind());
            buildFields();
            submitButton.setOnClickListener(view -> submitSafely());
            if ("teacher_exams".equals(kind)) loadCourseOptions();
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementCreateActivity", "Không thể tạo biểu mẫu", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    private void bindViews() {
        container = findViewById(R.id.containerManagementFields);
        progressBar = findViewById(R.id.progressManagementCreate);
        statusText = findViewById(R.id.textManagementCreateStatus);
        submitButton = findViewById(R.id.buttonManagementCreateSubmit);
        if (container == null || progressBar == null || statusText == null
                || submitButton == null) {
            throw new IllegalStateException("Biểu mẫu tạo mới thiếu thành phần bắt buộc");
        }
    }

    private void buildFields() {
        if ("admin_users".equals(kind)) {
            addInput("username", "Tên đăng nhập", InputType.TYPE_CLASS_TEXT);
            addInput("email", "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            addInput("phone", "Số điện thoại (không bắt buộc)", InputType.TYPE_CLASS_PHONE);
            addInput("password", "Mật khẩu", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            addSpinner(new String[]{"student", "instructor", "admin"});
        } else if ("teacher_courses".equals(kind)) {
            addInput("title", "Tên khóa học", InputType.TYPE_CLASS_TEXT);
            addInput("description", "Mô tả", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            addInput("subject_slug", "Mã môn: math, vietnamese, english…", InputType.TYPE_CLASS_TEXT);
            addInput("grade", "Lớp: 1–5", InputType.TYPE_CLASS_NUMBER);
            addInput("price", "Giá (0 nếu miễn phí)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else if ("teacher_exams".equals(kind)) {
            addInput("title", "Tên bài kiểm tra", InputType.TYPE_CLASS_TEXT);
            addCourseSpinner();
            addInput("duration", "Thời gian làm bài (phút)", InputType.TYPE_CLASS_NUMBER);
            addInput("pass_score", "Điểm đạt (0–100)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            addInput("title", "Tên trò chơi", InputType.TYPE_CLASS_TEXT);
            addInput("description", "Mô tả", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            addInput("subject", "Môn học", InputType.TYPE_CLASS_TEXT);
            addInput("grade_level", "Lớp: 1–5", InputType.TYPE_CLASS_NUMBER);
            addSpinner(new String[]{"quiz", "word_match", "puzzle"});
            addInput("difficulty", "Độ khó: easy, medium hoặc hard", InputType.TYPE_CLASS_TEXT);
        }
    }

    private void addInput(String key, String hint, int inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        params.setMargins(0, margin, 0, 0);
        layout.setLayoutParams(params);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(inputType);
        input.setMaxLines((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 ? 5 : 1);
        layout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(layout);
        inputs.put(key, input);
    }

    private void addSpinner(String[] values) {
        primarySpinner = new Spinner(this);
        primarySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values));
        primarySpinner.setBackgroundResource(R.drawable.bg_glass_capsule);
        int padding = (int) (10 * getResources().getDisplayMetrics().density);
        primarySpinner.setPadding(padding, padding, padding, padding);
        container.addView(primarySpinner);
    }

    private void addCourseSpinner() {
        TextView label = new TextView(this);
        label.setText(R.string.course_applied_label);
        int padding = (int) (10 * getResources().getDisplayMetrics().density);
        label.setPadding(0, padding, 0, 0);
        container.addView(label);
        courseSpinner = new Spinner(this);
        courseSpinner.setBackgroundResource(R.drawable.bg_glass_capsule);
        courseSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Đang tải khóa học từ server…"}));
        courseSpinner.setEnabled(false);
        courseSpinner.setPadding(padding, padding, padding, padding);
        container.addView(courseSpinner);
    }

    private void loadCourseOptions() {
        setLoading(true);
        showStatus("Đang tải khóa học thật từ server…");
        repository.load("content/courses/?page=1&pageSize=100",
                new ApiCallback<List<FeatureItem>>() {
                    @Override public void onSuccess(List<FeatureItem> data) {
                        if (!isUsable()) return;
                        courseOptions.clear();
                        if (data != null) {
                            for (FeatureItem item : data) {
                                if (item != null && !item.getId().isEmpty()) courseOptions.add(item);
                            }
                        }
                        List<String> labels = new ArrayList<>();
                        for (FeatureItem item : courseOptions) labels.add(item.getTitle());
                        courseSpinner.setAdapter(new ArrayAdapter<>(ManagementCreateActivity.this,
                                android.R.layout.simple_spinner_dropdown_item, labels));
                        setLoading(false);
                        if (courseOptions.isEmpty()) {
                            submitButton.setEnabled(false);
                            showStatus("Chưa có khóa học thật để gắn bài kiểm tra. Hãy tạo khóa học trước.");
                        } else {
                            courseSpinner.setEnabled(true);
                            statusText.setVisibility(View.GONE);
                        }
                    }

                    @Override public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        submitButton.setEnabled(false);
                        showStatus(error == null ? "Không thể tải danh sách khóa học"
                                : error.getMessage());
                    }
                });
    }

    private void submitSafely() {
        try {
            JSONObject body = buildBody();
            if (body == null) return;
            String endpoint;
            if ("admin_users".equals(kind)) endpoint = "account/admin/users/";
            else if ("teacher_courses".equals(kind)) endpoint = "content/courses/";
            else if ("teacher_exams".equals(kind)) endpoint = "activities/exercises/";
            else endpoint = "teacher/games/";
            setLoading(true);
            repository.action(Request.Method.POST, endpoint, body,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showShortMessage("Đã tạo dữ liệu trên server");
                            setResult(RESULT_OK);
                            finish();
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showStatus(error == null ? getString(R.string.unknown_error)
                                    : error.getMessage());
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementCreateActivity", "Không thể gửi biểu mẫu", exception);
            setLoading(false);
            showStatus("Không thể chuẩn bị dữ liệu tạo mới");
        }
    }

    private JSONObject buildBody() throws Exception {
        JSONObject body = new JSONObject();
        if ("admin_users".equals(kind)) {
            String username = required("username");
            String email = required("email");
            String password = required("password");
            if (username == null || email == null || password == null) return null;
            if (password.length() < 8) {
                inputs.get("password").setError("Mật khẩu phải có ít nhất 8 ký tự");
                return null;
            }
            body.put("username", username);
            body.put("email", email);
            body.put("password", password);
            String phone = value("phone");
            if (!phone.isEmpty()) body.put("phone", phone);
            body.put("role", selectedValue());
        } else if ("teacher_courses".equals(kind)) {
            String title = required("title");
            String grade = required("grade");
            if (title == null || grade == null) return null;
            body.put("title", title);
            body.put("description", value("description"));
            body.put("subject_slug", value("subject_slug"));
            body.put("grade", grade);
            body.put("price", decimal("price", 0, 0, Double.MAX_VALUE));
        } else if ("teacher_exams".equals(kind)) {
            String title = required("title");
            String courseId = selectedCourseId();
            if (title == null || courseId == null) return null;
            int durationMinutes = (int) decimal("duration", 30, 1, 300);
            double passScore = decimal("pass_score", 50, 0, 100);
            JSONObject settings = new JSONObject();
            settings.put("duration_seconds", durationMinutes * 60);
            settings.put("pass_score", passScore);
            settings.put("max_attempts", 1);
            settings.put("shuffle_questions", true);
            settings.put("shuffle_choices", true);
            settings.put("course_id", courseId);
            body.put("title", title);
            body.put("type", "mcq");
            body.put("published", false);
            body.put("settings", settings);
            body.put("questions", new JSONArray());
        } else {
            String title = required("title");
            String grade = required("grade_level");
            if (title == null || grade == null) return null;
            body.put("title", title);
            body.put("description", value("description"));
            body.put("subject", value("subject"));
            body.put("grade_level", Integer.parseInt(grade));
            body.put("game_type", selectedValue());
            String difficulty = value("difficulty");
            body.put("difficulty", difficulty.isEmpty() ? "easy" : difficulty);
            body.put("is_published", false);
            body.put("questions", new JSONArray());
            body.put("settings", new JSONObject());
        }
        return body;
    }

    private String required(String key) {
        String value = value(key);
        TextInputEditText input = inputs.get(key);
        if (value.isEmpty()) {
            if (input != null) input.setError("Trường này là bắt buộc");
            return null;
        }
        return value;
    }

    private double decimal(String key, double defaultValue, double min, double max) {
        String raw = value(key);
        if (raw.isEmpty()) return defaultValue;
        try {
            double value = Double.parseDouble(raw);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            TextInputEditText input = inputs.get(key);
            if (input != null) input.setError("Giá trị phải nằm trong khoảng hợp lệ");
            throw exception;
        }
    }

    private String value(String key) {
        TextInputEditText input = inputs.get(key);
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String selectedValue() {
        return primarySpinner == null || primarySpinner.getSelectedItem() == null
                ? "" : String.valueOf(primarySpinner.getSelectedItem());
    }

    private String selectedCourseId() {
        int position = courseSpinner == null ? -1 : courseSpinner.getSelectedItemPosition();
        if (position < 0 || position >= courseOptions.size()) {
            showStatus("Vui lòng chọn một khóa học thật từ server");
            return null;
        }
        return courseOptions.get(position).getId();
    }

    private String titleForKind() {
        if ("admin_users".equals(kind)) return "Tạo tài khoản";
        if ("teacher_courses".equals(kind)) return "Tạo khóa học";
        if ("teacher_exams".equals(kind)) return "Tạo bài kiểm tra";
        return "Tạo trò chơi";
    }

    private boolean isSupported(String value) {
        return "admin_users".equals(value) || "teacher_courses".equals(value)
                || "teacher_exams".equals(value) || "teacher_games".equals(value);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
        for (TextInputEditText input : inputs.values()) input.setEnabled(!loading);
        if (primarySpinner != null) primarySpinner.setEnabled(!loading);
        if (courseSpinner != null) courseSpinner.setEnabled(!loading && !courseOptions.isEmpty());
    }

    private void showStatus(String message) {
        statusText.setText(message == null ? getString(R.string.unknown_error) : message);
        statusText.setVisibility(View.VISIBLE);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
