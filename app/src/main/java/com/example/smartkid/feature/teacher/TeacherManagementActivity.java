package com.example.smartkid.feature.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.example.smartkid.common.ui.FeatureSpec;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.feature.teacher.course.TeacherCourseCreateActivity;
import com.example.smartkid.feature.teacher.course.builder.TeacherCourseBuilderActivity;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Teacher-owned management list backed by real APIs, with teacher-only actions. */
public class TeacherManagementActivity extends BaseActivity {
    public static final String EXTRA_SPEC_KEY = "teacher_spec_key";

    private FeatureSpec spec;
    private ManagementRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private View refreshButton;
    private SwipeRefreshLayout refreshLayout;

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.common_slide_in_left, R.anim.common_slide_out_right);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (repository != null && spec != null && spec.isAvailable()) loadSafely();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.common_activity_feature_list);
            String key = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SPEC_KEY);
            spec = TeacherManagementSpec.get(key);
            UserRole role = UserRole.fromString(new SessionManager(this).getUser().getRole());
            if (spec == null || !spec.isAvailable() || !spec.isAllowedForRole(role)) {
                showErrorDialog("Chức năng quản lý không hợp lệ");
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarFeatureList);
            progressBar = findViewById(R.id.progressFeatureList);
            emptyText = findViewById(R.id.textFeatureListEmpty);
            refreshButton = findViewById(R.id.buttonFeatureAction);
            refreshLayout = findViewById(R.id.refreshFeatureList);
            TextInputEditText search = findViewById(R.id.inputFeatureSearch);
            ListView list = findViewById(R.id.listFeatures);
            if (toolbar == null || progressBar == null || emptyText == null || refreshButton == null
                    || refreshLayout == null || search == null || list == null) {
                throw new IllegalStateException("Giao diện quản lý chưa đầy đủ");
            }
            toolbar.setTitle(spec.getTitle());
            toolbar.setNavigationOnClickListener(view -> finish());
            refreshLayout.setOnRefreshListener(this::loadSafely);
            configurePrimaryAction(toolbar);
            emptyText.setText(R.string.no_server_data);
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) -> {
                FeatureItem item = adapter.getItem(position);
                if ("teacher_courses".equals(spec.getActionKind())) showActions(item);
                else showItem(item);
            });
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể tạo chức năng", exception);
            showErrorDialog("Không thể mở dữ liệu quản lý");
        }
    }

    private void configurePrimaryAction(MaterialToolbar toolbar) {
        if (supportsCreate()) {
            ((TextView) refreshButton).setText("Tạo mới");
            refreshButton.setOnClickListener(view -> openCreate());
            android.view.MenuItem refresh = toolbar.getMenu().add(R.string.refresh);
            refresh.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
            refresh.setOnMenuItemClickListener(item -> {
                loadSafely();
                return true;
            });
        } else {
            ((TextView) refreshButton).setText(R.string.refresh);
            refreshButton.setOnClickListener(view -> loadSafely());
        }
    }

    private boolean supportsCreate() {
        String kind = spec == null ? "" : spec.getActionKind();
        return "teacher_courses".equals(kind) || "teacher_exams".equals(kind);
    }

    private void openCreate() {
        try {
            String kind = spec.getActionKind();
            Intent intent;
            if ("teacher_exams".equals(kind)) {
                intent = new Intent(this, TeacherExerciseEditorActivity.class);
                intent.putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                        ExerciseScope.STANDALONE_EXAM.name());
            } else {
                intent = new Intent(this, TeacherCourseCreateActivity.class);
            }
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể mở tạo mới", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    private void loadSafely() {
        setLoading(true);
        repository.load(spec.getEndpoint(), new ApiCallback<List<FeatureItem>>() {
            @Override
            public void onSuccess(List<FeatureItem> data) {
                if (!isUsable()) return;
                setLoading(false);
                adapter.setItems(filterForCurrentFeature(data));
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private List<FeatureItem> filterForCurrentFeature(List<FeatureItem> data) {
        if (data == null || spec == null) return data;
        String kind = spec.getActionKind();
        if (!("teacher_exams".equals(kind) || "teacher_exam_reports".equals(kind))) {
            return data;
        }
        List<FeatureItem> standalone = new java.util.ArrayList<>();
        for (FeatureItem item : data) {
            if (item == null) continue;
            JSONObject source = item.getSource();
            Object lesson = source == null ? null : source.opt("lesson");
            if (lesson == null || lesson == JSONObject.NULL
                    || String.valueOf(lesson).trim().isEmpty()) {
                standalone.add(item);
            }
        }
        return standalone;
    }

    private void showItem(FeatureItem item) {
        if (item == null) return;
        try {
            String specKey = spec == null ? "" : spec.getKey();
            if ("teacher_notifications".equals(specKey)) {
                showNotificationDetail(item);
                return;
            }
            if ("teacher_qa".equals(specKey)) {
                showQuestionDetail(item);
                return;
            }
            String json = item.getSource().length() == 0 ? ""
                    : item.getSource().toString(2);
            String message = item.getSubtitle() + "\n" + item.getDetail() + "\n"
                    + item.getStatus() + (json.isEmpty() ? "" : "\n\n" + limit(json));
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(item.getTitle()).setMessage(message.trim())
                    .setNegativeButton("Đóng", null);
            if (!spec.getActionKind().isEmpty() && !item.getId().isEmpty()) {
                builder.setPositiveButton("Thao tác", (dialog, which) -> showActions(item));
            }
            builder.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể hiện chi tiết", exception);
            showErrorDialog("Không thể đọc chi tiết dữ liệu");
        }
    }

    /** Thông báo: hiện nội dung thân thiện thay vì JSON thô, kèm ngữ cảnh khóa học/bài học. */
    private void showNotificationDetail(FeatureItem item) {
        JSONObject source = item.getSource();
        JSONObject metadata = source == null ? null : source.optJSONObject("metadata");
        StringBuilder info = new StringBuilder();
        appendInfoLine(info, "Học sinh", SafeJson.string(metadata, "", "student"));
        appendInfoLine(info, "Khóa học", SafeJson.string(metadata, "", "course_title"));
        appendInfoLine(info, "Bài học", SafeJson.string(metadata, "", "lesson_title"));
        appendInfoLine(info, "Thời gian", readableTime(SafeJson.string(source, "", "created_at")));
        String body = SafeJson.string(source, item.getDetail(), "message");
        String message = body.isEmpty() ? info.toString()
                : (info.length() == 0 ? body : body + "\n\n" + info);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setMessage(message.trim())
                .setNegativeButton("Đóng", null);
        if ("lesson_question".equals(SafeJson.string(source, "", "category"))) {
            builder.setPositiveButton("Mở hỏi đáp", (dialog, which) -> openQaScreen());
        }
        builder.show();
    }

    /** Hỏi đáp: câu hỏi + bài học liên quan, trả lời học viên ngay trong dialog. */
    private void showQuestionDetail(FeatureItem item) {
        JSONObject source = item.getSource();
        StringBuilder info = new StringBuilder();
        appendInfoLine(info, "Khóa học", SafeJson.string(source, "", "course_title"));
        appendInfoLine(info, "Bài học", SafeJson.string(source, "", "lesson_title"));
        appendInfoLine(info, "Thời gian", readableTime(SafeJson.string(source, "", "created_at")));
        JSONArray replies = source == null ? null : source.optJSONArray("replies");
        int replyCount = replies == null ? 0 : replies.length();
        appendInfoLine(info, "Phản hồi", replyCount == 0 ? "Chưa có" : replyCount + " phản hồi");
        String content = SafeJson.string(source, item.getDetail(), "content");
        String message = content.isEmpty() ? info.toString() : content + "\n\n" + info;
        new AlertDialog.Builder(this)
                .setTitle("Câu hỏi của " + SafeJson.string(source, item.getTitle(), "student"))
                .setMessage(message.trim())
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Trả lời học viên", (dialog, which) ->
                        promptText("Trả lời học viên", "Nhập nội dung phản hồi", "Gửi",
                                value -> performTextAction(item, "Trả lời học viên", value)))
                .show();
    }

    private void appendInfoLine(StringBuilder target, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append('\n');
        target.append(label).append(": ").append(value.trim());
    }

    /** "2026-07-25T18:11:34.845711+00:00" (UTC) -> "25/07/2026 18:11" theo giờ máy. */
    private String readableTime(String isoValue) {
        if (isoValue == null || isoValue.trim().isEmpty()) return "";
        String raw = isoValue.trim();
        try {
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date parsed = parser.parse(raw.substring(0, Math.min(19, raw.length())));
            java.text.SimpleDateFormat printer = new java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm", java.util.Locale.US);
            printer.setTimeZone(java.util.TimeZone.getDefault());
            return parsed == null ? raw : printer.format(parsed);
        } catch (Exception ignored) {
            return raw.replace('T', ' ');
        }
    }

    private void openQaScreen() {
        Intent intent = new Intent(this, TeacherManagementActivity.class);
        intent.putExtra(EXTRA_SPEC_KEY, "teacher_qa");
        startActivity(intent);
    }

    private void showActions(FeatureItem item) {
        if (item == null) return;
        String kind = spec.getActionKind();
        String[] labels;
        if ("teacher_courses".equals(kind)) {
            boolean published = item.getSource().optBoolean("published", false);
            labels = published
                    ? new String[]{"Quản lý nội dung", "Gỡ xuất bản"}
                    : new String[]{"Quản lý nội dung", "Xuất bản"};
        } else if ("teacher_exams".equals(kind)) {
            labels = new String[]{"Thêm câu hỏi", "Xem thống kê", "Xuất bản", "Gỡ xuất bản", "Xóa"};
        } else if ("teacher_exam_reports".equals(kind)) {
            labels = new String[]{"Xem thống kê", "Xem lượt nộp"};
        } else if ("teacher_qa".equals(kind)) {
            labels = new String[]{"Trả lời học viên"};
        } else if ("teacher_students".equals(kind)) {
            labels = new String[]{"Gửi phản hồi"};
        } else {
            return;
        }
        new AlertDialog.Builder(this).setTitle("Chọn thao tác")
                .setItems(labels, (dialog, which) -> confirmAction(item, labels[which]))
                .show();
    }

    private void confirmAction(FeatureItem item, String label) {
        if ("Quản lý nội dung".equals(label)) {
            openCourseContent(item);
            return;
        }
        if ("Xuất bản".equals(label) && hasNoPlayableContent(item)) {
            showErrorDialog("Hãy thêm ít nhất một câu hỏi trước khi xuất bản.");
            return;
        }
        if ("Thêm câu hỏi".equals(label)) {
            promptQuestion(item);
            return;
        }
        if ("Xem thống kê".equals(label)) {
            showStatistics(item);
            return;
        }
        if ("Xem lượt nộp".equals(label)) {
            showAttempts(item);
            return;
        }
        if ("teacher_qa".equals(spec.getActionKind())) {
            promptText("Trả lời học viên", "Nhập nội dung phản hồi", "Gửi",
                    value -> performTextAction(item, label, value));
            return;
        }
        if ("teacher_students".equals(spec.getActionKind())) {
            promptFeedback(item);
            return;
        }
        if ("Xóa".equals(label)) {
            new AlertDialog.Builder(this).setTitle("Xóa dữ liệu")
                    .setMessage("Xóa vĩnh viễn “" + item.getTitle()
                            + "” khỏi server? Thao tác này không thể hoàn tác.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Xóa", (dialog, which) -> deleteItem(item))
                    .show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(label)
                .setMessage("Thực hiện “" + label + "” với “" + item.getTitle() + "”? Dữ liệu sẽ được cập nhật trên server.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xác nhận", (dialog, which) -> performAction(item, label))
                .show();
    }

    private void openCourseContent(FeatureItem item) {
        if (item == null || item.getId().isEmpty()) {
            showErrorDialog("Khóa học không có mã hợp lệ");
            return;
        }
        try {
            Intent intent = new Intent(this, TeacherCourseBuilderActivity.class);
            intent.putExtra(TeacherCourseBuilderActivity.EXTRA_COURSE_ID, item.getId());
            intent.putExtra(TeacherCourseBuilderActivity.EXTRA_COURSE_TITLE, item.getTitle());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity",
                    "Không thể mở nội dung khóa học", exception);
            showErrorDialog("Không thể mở nội dung khóa học");
        }
    }

    private boolean hasNoPlayableContent(FeatureItem item) {
        if (item == null || spec == null) return false;
        JSONObject source = item.getSource();
        if ("teacher_exams".equals(spec.getActionKind())) {
            JSONArray questions = source.optJSONArray("questions");
            return questions == null || questions.length() == 0;
        }
        return false;
    }

    private void promptQuestion(FeatureItem item) {
        showChoiceQuestionDialog(item);
    }

    private void showChoiceQuestionDialog(FeatureItem item) {
        try {
            LinearLayout fields = dialogFields();
            EditText prompt = dialogInput("Câu hỏi kiểm tra");
            fields.addView(prompt);
            EditText[] choices = new EditText[4];
            for (int index = 0; index < choices.length; index++) {
                choices[index] = dialogInput("Đáp án " + (index + 1));
                fields.addView(choices[index]);
            }
            Spinner correct = new Spinner(this);
            correct.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Đáp án đúng: 1", "Đáp án đúng: 2",
                            "Đáp án đúng: 3", "Đáp án đúng: 4"}));
            fields.addView(correct);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(fields);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Thêm câu hỏi")
                    .setView(scroll).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Lưu lên server", null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String promptValue = textOf(prompt);
                        if (promptValue.length() < 2) {
                            prompt.setError("Câu hỏi phải có ít nhất 2 ký tự");
                            return;
                        }
                        JSONArray optionValues = new JSONArray();
                        for (EditText choice : choices) {
                            String value = textOf(choice);
                            if (value.isEmpty()) {
                                choice.setError("Không được để trống đáp án");
                                return;
                            }
                            optionValues.put(value);
                        }
                        dialog.dismiss();
                        addExamQuestion(item, promptValue, optionValues,
                                correct.getSelectedItemPosition());
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể mở câu hỏi", exception);
            showErrorDialog("Không thể mở biểu mẫu câu hỏi");
        }
    }

    private void addExamQuestion(FeatureItem item, String prompt, JSONArray options,
                                 int correctIndex) {
        try {
            JSONObject body = new JSONObject();
            body.put("prompt", prompt);
            body.put("meta", new JSONObject().put("type", "mcq").put("points", 1));
            JSONArray choices = new JSONArray();
            for (int index = 0; index < options.length(); index++) {
                choices.put(new JSONObject().put("text", options.optString(index))
                        .put("is_correct", index == correctIndex).put("position", index));
            }
            body.put("choices", choices);
            setLoading(true);
            repository.action(Request.Method.POST,
                    "activities/exercises/" + item.getId() + "/questions/", body,
                    actionCallback("Đã thêm câu hỏi và đáp án vào bài kiểm tra"));
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể thêm câu hỏi", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị câu hỏi kiểm tra");
        }
    }

    private void showStatistics(FeatureItem item) {
        String endpoint = "activities/exercises/" + item.getId() + "/stats/";
        setLoading(true);
        repository.loadObject(endpoint, new ApiCallback<JSONObject>() {
            @Override public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                setLoading(false);
                showJsonDialog("Thống kê • " + item.getTitle(), data);
            }

            @Override public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void showAttempts(FeatureItem item) {
        setLoading(true);
        repository.load("activities/exercises/" + item.getId() + "/attempts/",
                new ApiCallback<List<FeatureItem>>() {
                    @Override public void onSuccess(List<FeatureItem> data) {
                        if (!isUsable()) return;
                        setLoading(false);
                        StringBuilder message = new StringBuilder();
                        for (FeatureItem attempt : data) {
                            message.append("• ").append(attempt.getTitle())
                                    .append(" — ").append(attempt.getStatus()).append('\n');
                        }
                        new AlertDialog.Builder(TeacherManagementActivity.this)
                                .setTitle("Lượt nộp • " + data.size())
                                .setMessage(message.length() == 0
                                        ? "Chưa có học viên nộp bài." : message.toString().trim())
                                .setPositiveButton("Đóng", null).show();
                    }

                    @Override public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void deleteItem(FeatureItem item) {
        String endpoint = "activities/exercises/" + item.getId() + "/";
        setLoading(true);
        repository.action(Request.Method.DELETE, endpoint, null,
                actionCallback("Đã xóa dữ liệu khỏi server"));
    }

    private void showJsonDialog(String title, JSONObject data) {
        try {
            String value = data == null ? "Không có dữ liệu" : limit(data.toString(2));
            new AlertDialog.Builder(this).setTitle(title).setMessage(value)
                    .setPositiveButton("Đóng", null).show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể hiện thống kê", exception);
            showErrorDialog("Không thể đọc dữ liệu thống kê");
        }
    }

    private LinearLayout dialogFields() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding / 2, padding, padding / 2);
        return fields;
    }

    private EditText dialogInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        return input;
    }

    private String textOf(EditText input) {
        return input == null || input.getText() == null
                ? "" : input.getText().toString().trim();
    }

    private void performTextAction(FeatureItem item, String label, String value) {
        try {
            JSONObject body = new JSONObject();
            body.put("content", value);
            setLoading(true);
            repository.action(Request.Method.POST,
                    "teacher/lesson-questions/" + item.getId() + "/reply/", body,
                    actionCallback("Đã gửi phản hồi tới học viên"));
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể gửi phản hồi", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị phản hồi");
        }
    }

    private void promptFeedback(FeatureItem item) {
        try {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (20 * getResources().getDisplayMetrics().density);
            container.setPadding(padding, padding / 2, padding, 0);
            EditText message = new EditText(this);
            message.setHint("Nội dung phản hồi");
            message.setMinLines(2);
            message.setMaxLines(5);
            EditText rating = new EditText(this);
            rating.setHint("Điểm đánh giá từ 0 đến 10");
            rating.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            container.addView(message);
            container.addView(rating);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Gửi phản hồi cho " + item.getTitle())
                    .setView(container).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Gửi", null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String content = message.getText() == null ? ""
                                : message.getText().toString().trim();
                        String rawRating = rating.getText() == null ? ""
                                : rating.getText().toString().trim();
                        if (content.isEmpty()) {
                            message.setError("Nội dung không được để trống");
                            return;
                        }
                        double score;
                        try { score = Double.parseDouble(rawRating); }
                        catch (Exception exception) {
                            rating.setError("Điểm phải là số từ 0 đến 10");
                            return;
                        }
                        if (score < 0 || score > 10) {
                            rating.setError("Điểm phải nằm trong khoảng 0–10");
                            return;
                        }
                        dialog.dismiss();
                        sendFeedback(item, content, score);
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể mở phản hồi", exception);
            showErrorDialog("Không thể mở biểu mẫu phản hồi");
        }
    }

    private void sendFeedback(FeatureItem item, String message, double rating) {
        try {
            JSONObject body = new JSONObject();
            body.put("studentId", item.getId());
            body.put("message", message);
            body.put("rating", rating);
            setLoading(true);
            repository.action(Request.Method.POST, "teacher/students/feedback/", body,
                    actionCallback("Đã gửi phản hồi và thông báo cho học viên"));
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể tạo phản hồi", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị phản hồi học viên");
        }
    }

    private void promptText(String title, String hint, String positive,
                            TextValueAction action) {
        try {
            EditText input = new EditText(this);
            input.setHint(hint);
            input.setMinLines(2);
            input.setMaxLines(6);
            int padding = (int) (20 * getResources().getDisplayMetrics().density);
            input.setPadding(padding, padding / 2, padding, padding / 2);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                    .setView(input).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(positive, null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String value = input.getText() == null ? ""
                                : input.getText().toString().trim();
                        if (value.length() < 2) {
                            input.setError("Nội dung phải có ít nhất 2 ký tự");
                            return;
                        }
                        dialog.dismiss();
                        try { action.run(value); }
                        catch (Exception exception) {
                            AppLogger.error(this, "TeacherManagementActivity",
                                    "Không thể xử lý biểu mẫu", exception);
                            setLoading(false);
                        }
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể mở biểu mẫu", exception);
            showErrorDialog("Không thể mở biểu mẫu nhập liệu");
        }
    }

    private ApiCallback<JSONObject> actionCallback(String message) {
        return new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                showShortMessage(message);
                loadSafely();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        };
    }

    private void performAction(FeatureItem item, String label) {
        try {
            String kind = spec.getActionKind();
            String endpoint;
            int method;
            JSONObject body = new JSONObject();
            if ("teacher_courses".equals(kind)) {
                if ("Xuất bản".equals(label)) {
                    endpoint = "content/courses/" + item.getId() + "/publish/";
                    method = Request.Method.POST;
                    body.put("published", true);
                } else {
                    endpoint = "content/courses/" + item.getId() + "/";
                    method = Request.Method.PATCH;
                    body.put("published", false);
                }
            } else if ("teacher_exams".equals(kind)) {
                endpoint = "activities/exercises/" + item.getId() + "/";
                method = Request.Method.PATCH;
                body.put("published", "Xuất bản".equals(label));
            } else return;

            setLoading(true);
            repository.action(method, endpoint, body, new ApiCallback<JSONObject>() {
                @Override
                public void onSuccess(JSONObject data) {
                    if (!isUsable()) return;
                    showShortMessage("Đã cập nhật trên server");
                    loadSafely();
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể thao tác", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị thao tác quản lý");
        }
    }

    private String limit(String value) {
        return value.length() > 3000 ? value.substring(0, 3000) + "…" : value;
    }

    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }

    private interface TextValueAction { void run(String value); }
}
