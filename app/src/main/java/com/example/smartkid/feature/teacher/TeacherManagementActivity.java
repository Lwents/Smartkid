package com.example.smartkid.feature.teacher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
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
import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.common.util.AppConstants;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import com.example.smartkid.common.util.SwipeRefreshFix;

/** Teacher-owned management list backed by real APIs, with teacher-only actions. */
public class TeacherManagementActivity extends BaseActivity {
    public static final String EXTRA_SPEC_KEY = "teacher_spec_key";

    private FeatureSpec spec;
    private ManagementRepository repository;
    private FeatureItemAdapter adapter;
    private TeacherQuestionAdapter questionAdapter;
    private TeacherExamAdapter examAdapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private TextView questionSummary;
    private TextView examSummary;
    private View refreshButton;
    private SwipeRefreshLayout refreshLayout;
    private String currentSearchQuery = "";

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
            String key = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SPEC_KEY);
            spec = TeacherManagementSpec.get(key);
            UserRole role = UserRole.fromString(new SessionManager(this).getUser().getRole());
            if (spec == null || !spec.isAvailable() || !spec.isAllowedForRole(role)) {
                setContentView(R.layout.common_activity_feature_list);
                showErrorDialog("Chức năng quản lý không hợp lệ");
                finish();
                return;
            }
            setContentView(isQaFeature()
                    ? R.layout.teacher_activity_lesson_questions
                    : (isExamFeature() ? R.layout.teacher_activity_exams
                            : R.layout.common_activity_feature_list));
            repository = new ManagementRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarFeatureList);
            progressBar = findViewById(R.id.progressFeatureList);
            emptyText = findViewById(R.id.textFeatureListEmpty);
            questionSummary = findViewById(R.id.textTeacherQuestionSummary);
            examSummary = findViewById(R.id.textTeacherExamSummary);
            refreshButton = findViewById(R.id.buttonFeatureAction);
            refreshLayout = findViewById(R.id.refreshFeatureList);
            SwipeRefreshFix.attach(refreshLayout);
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
            if (isQaFeature()) {
                emptyText.setText(R.string.teacher_no_lesson_questions);
            } else if (!isExamFeature()) {
                emptyText.setText(R.string.no_server_data);
            }
            if (isQaFeature()) {
                questionAdapter = new TeacherQuestionAdapter(this);
                list.setAdapter(questionAdapter);
            } else if (isExamFeature()) {
                examAdapter = new TeacherExamAdapter(this);
                list.setAdapter(examAdapter);
            } else {
                adapter = new FeatureItemAdapter(this);
                list.setAdapter(adapter);
            }
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) -> {
                FeatureItem item = itemAt(position);
                if ("teacher_courses".equals(spec.getActionKind()) || isExamFeature()) {
                    showActions(item);
                }
                else showItem(item);
            });
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s == null ? "" : s.toString();
                    filterItems(currentSearchQuery);
                    updateEmptyMessage();
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
            ((TextView) refreshButton).setText(isExamFeature() ? "Tạo bài" : "Tạo mới");
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
                setItems(filterForCurrentFeature(data));
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private FeatureItem itemAt(int position) {
        if (questionAdapter != null) return questionAdapter.getItem(position);
        if (examAdapter != null) return examAdapter.getItem(position);
        return adapter == null ? null : adapter.getItem(position);
    }

    private void setItems(List<FeatureItem> items) {
        if (questionAdapter != null) {
            questionAdapter.setItems(items);
            if (!currentSearchQuery.isEmpty()) questionAdapter.filter(currentSearchQuery);
            updateQuestionSummary();
        } else if (examAdapter != null) {
            examAdapter.setItems(items);
            if (!currentSearchQuery.isEmpty()) examAdapter.filter(currentSearchQuery);
            updateExamSummary();
        } else if (adapter != null) {
            adapter.setItems(items);
            if (!currentSearchQuery.isEmpty()) adapter.filter(currentSearchQuery);
        }
    }

    private void filterItems(String keyword) {
        if (questionAdapter != null) questionAdapter.filter(keyword);
        else if (examAdapter != null) examAdapter.filter(keyword);
        else if (adapter != null) adapter.filter(keyword);
    }

    private void updateQuestionSummary() {
        if (questionSummary == null || questionAdapter == null) return;
        int total = questionAdapter.getTotalCount();
        int pending = questionAdapter.getPendingCount();
        if (total == 0) {
            questionSummary.setText(R.string.teacher_question_summary_empty);
        } else if (pending == 0) {
            questionSummary.setText(getResources().getQuantityString(
                    R.plurals.teacher_question_summary_done, total, total));
        } else {
            questionSummary.setText(getResources().getQuantityString(
                    R.plurals.teacher_question_summary_pending,
                    total,
                    total,
                    pending
            ));
        }
    }

    private void updateExamSummary() {
        if (examSummary == null || examAdapter == null) return;
        int total = examAdapter.getTotalCount();
        int published = examAdapter.getPublishedCount();
        int drafts = Math.max(0, total - published);
        if (total == 0) {
            examSummary.setText(R.string.teacher_exam_summary_empty);
        } else if (drafts == 0) {
            examSummary.setText(getResources().getQuantityString(
                    R.plurals.teacher_exam_summary_open, total, total));
        } else {
            examSummary.setText(getResources().getQuantityString(
                    R.plurals.teacher_exam_summary_mixed,
                    total,
                    total,
                    published,
                    drafts
            ));
        }
    }

    private void updateEmptyMessage() {
        if (emptyText == null || !isExamFeature()) return;
        emptyText.setText(currentSearchQuery.trim().isEmpty()
                ? R.string.teacher_exam_empty
                : R.string.teacher_exam_search_empty);
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
                // Thông báo học sinh hỏi bài: vào thẳng màn Hỏi đáp, không cần
                // qua hộp thoại rồi bấm thêm một lần nữa.
                String category = SafeJson.string(item.getSource(), "", "category");
                if (category.startsWith("lesson_question")) {
                    markNotificationRead(item);
                    openQaScreen();
                    return;
                }
                showNotificationDetail(item);
                return;
            }
            if (isQaFeature()) {
                showQuestionDetail(item);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(item.getTitle())
                    .setMessage(friendlyDetail(item, specKey))
                    .setNegativeButton("Đóng", null);
            if (!spec.getActionKind().isEmpty() && !item.getId().isEmpty()) {
                builder.setPositiveButton(actionButtonLabel(specKey),
                        (dialog, which) -> showActions(item));
            }
            builder.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể hiện chi tiết", exception);
            showErrorDialog("Không thể đọc chi tiết dữ liệu");
        }
    }

    /** Chi tiết dạng "Nhãn: giá trị" theo từng loại dữ liệu, không dump JSON thô. */
    private String friendlyDetail(FeatureItem item, String specKey) {
        JSONObject source = item.getSource();
        StringBuilder detail = new StringBuilder();
        if ("teacher_exams".equals(specKey) || "teacher_exam_reports".equals(specKey)) {
            JSONArray questions = source.optJSONArray("questions");
            appendInfoLine(detail, "Số câu hỏi",
                    String.valueOf(questions == null ? 0 : questions.length()));
            appendInfoLine(detail, "Dạng câu hỏi", questionTypeLabel(
                    SafeJson.string(source, "", "type")));
            JSONObject settings = source.optJSONObject("settings");
            if (settings != null) {
                int seconds = SafeJson.integer(settings, 0, "duration_seconds",
                        "time_limit_seconds");
                if (seconds > 0) appendInfoLine(detail, "Thời gian làm bài",
                        (seconds / 60) + " phút");
                double pass = SafeJson.decimal(settings, -1, "pass_score");
                if (pass >= 0) appendInfoLine(detail, "Điểm đạt", String.valueOf(pass));
                int attempts = SafeJson.integer(settings, 0, "max_attempts");
                if (attempts > 0) appendInfoLine(detail, "Số lần làm tối đa",
                        String.valueOf(attempts));
            }
            appendInfoLine(detail, "Trạng thái",
                    source.optBoolean("published", false) ? "Đã xuất bản" : "Bản nháp");
        } else if ("teacher_students".equals(specKey) || "teacher_progress".equals(specKey)) {
            appendInfoLine(detail, "Email", SafeJson.string(source, "", "email"));
            double score = SafeJson.decimal(source, -1, "avgScore");
            if (score >= 0) appendInfoLine(detail, "Điểm trung bình", String.valueOf(score));
            appendInfoLine(detail, "Hoạt động gần nhất",
                    SafeJson.string(source, "", "lastActive"));
            JSONArray courses = source.optJSONArray("courses");
            if (courses != null && courses.length() > 0) {
                appendInfoLine(detail, "", "\nTiến độ từng khóa học:");
                for (int index = 0; index < courses.length(); index++) {
                    JSONObject course = courses.optJSONObject(index);
                    if (course == null) continue;
                    int progress = SafeJson.integer(course, 0, "progress");
                    appendInfoLine(detail, "", "• "
                            + SafeJson.string(course, "Khóa học", "title")
                            + " — " + progress + "% ("
                            + SafeJson.integer(course, 0, "completedLessons") + "/"
                            + SafeJson.integer(course, 0, "totalLessons") + " bài)"
                            + (progress >= 100 ? " ✓ hoàn thành" : ""));
                }
            } else {
                appendInfoLine(detail, "", "Học viên chưa tham gia khóa học nào của bạn.");
            }
        } else if ("teacher_feedback".equals(specKey)) {
            appendInfoLine(detail, "Học sinh",
                    SafeJson.string(source, item.getTitle(), "studentName"));
            appendInfoLine(detail, "Khóa học",
                    SafeJson.string(source, "Phản hồi chung", "courseTitle", "course_title"));
            appendInfoLine(detail, "Lời nhận xét",
                    SafeJson.string(source, item.getDetail(), "message"));
            appendInfoLine(detail, "Điểm đánh giá",
                    readableMetric(SafeJson.decimal(source, 0, "rating")) + "/10");
            appendInfoLine(detail, "Đã gửi lúc",
                    readableTime(SafeJson.string(source, "", "createdAt", "created_at")));
        } else {
            appendInfoLine(detail, "", item.getSubtitle());
            appendInfoLine(detail, "", item.getDetail());
            appendInfoLine(detail, "", item.getStatus());
        }
        return detail.length() == 0
                ? (item.getSubtitle() + "\n" + item.getDetail()).trim() : detail.toString();
    }

    /** Nhãn nút hành động theo từng màn, thay cho "Thao tác" chung chung. */
    private String actionButtonLabel(String specKey) {
        switch (specKey) {
            case "teacher_exam_reports": return "Xem báo cáo";
            case "teacher_exams": return "Sửa bài kiểm tra";
            case "teacher_students": return "Gửi phản hồi";
            default: return "Thao tác";
        }
    }

    private String questionTypeLabel(String type) {
        switch (type) {
            case "mcq": return "Trắc nghiệm";
            case "short_answer": return "Trả lời ngắn";
            case "matching": return "Nối cặp";
            default: return type;
        }
    }

    /** Thông báo: hiện nội dung thân thiện thay vì JSON thô, kèm ngữ cảnh khóa học/bài học. */
    /** Đánh dấu đã đọc thông báo (chạy nền, không chặn việc mở màn hỏi đáp). */
    private void markNotificationRead(FeatureItem item) {
        if (item == null || item.getId().isEmpty()
                || SafeJson.bool(item.getSource(), false, "is_read", "isRead")) return;
        repository.action(Request.Method.PATCH,
                "teacher/notifications/" + item.getId() + "/read/", new JSONObject(),
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        try { item.getSource().put("is_read", true); }
                        catch (Exception ignored) { }
                        if (adapter != null) adapter.notifyDataSetChanged();
                    }
                    @Override public void onError(ApiError error) { }
                });
    }

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
        // Thông báo thường: chỉ là bảng để đọc. Thông báo hỏi bài đã được mở thẳng
        // sang màn Hỏi đáp từ trước nên không vào tới đây.
        new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setMessage(message.trim())
                .setPositiveButton("Đóng", null)
                .show();
        markNotificationRead(item);
    }

    /** Hỏi đáp: đọc toàn bộ cuộc trò chuyện và trả lời ngay trong bottom sheet. */
    @SuppressLint("InflateParams")
    private void showQuestionDetail(FeatureItem item) {
        if (item == null) return;
        try {
            JSONObject source = item.getSource();
            JSONArray replies = SafeJson.array(source, "replies");
            int teacherReplies = TeacherQuestionUiFormatter.teacherReplyCount(replies);
            BottomSheetDialog sheet = new BottomSheetDialog(
                    this, R.style.ThemeOverlay_Smartkid_NotificationSheet);
            View content = getLayoutInflater().inflate(R.layout.teacher_sheet_lesson_question, null);
            sheet.setContentView(content);

            bindText(content, R.id.textTeacherQuestionSheetAvatar,
                    TeacherQuestionUiFormatter.studentInitial(source));
            bindText(content, R.id.textTeacherQuestionSheetStudent,
                    TeacherQuestionUiFormatter.studentName(source));
            bindText(content, R.id.textTeacherQuestionSheetTime,
                    TeacherQuestionUiFormatter.timeLabel(source));
            bindText(content, R.id.textTeacherQuestionSheetContent,
                    SafeJson.string(source, item.getDetail(), "content"));
            bindText(content, R.id.textTeacherQuestionSheetContext,
                    TeacherQuestionUiFormatter.contextLabel(source));

            TextView status = content.findViewById(R.id.textTeacherQuestionSheetStatus);
            status.setText(TeacherQuestionUiFormatter.statusLabel(source));
            status.setBackgroundResource(teacherReplies == 0
                    ? R.drawable.teacher_bg_question_pending
                    : R.drawable.teacher_bg_question_answered);
            status.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                    teacherReplies == 0 ? R.color.teacher_question_pending_text
                            : R.color.teacher_question_answered_text));
            bindQuestionReplies(content, source, replies);

            TextInputEditText replyInput = content.findViewById(R.id.inputTeacherQuestionReply);
            View sendReply = content.findViewById(R.id.buttonTeacherQuestionSendReply);
            sendReply.setOnClickListener(view -> {
                String value = textOf(replyInput);
                if (value.length() < 2) {
                    replyInput.setError("Câu trả lời phải có ít nhất 2 ký tự");
                    replyInput.requestFocus();
                    return;
                }
                sheet.dismiss();
                performTextAction(item, "Trả lời học viên", value);
            });

            String lessonId = SafeJson.string(source, "", "lesson_id");
            View openLesson = content.findViewById(R.id.buttonTeacherQuestionOpenLesson);
            openLesson.setVisibility(lessonId.isEmpty() ? View.GONE : View.VISIBLE);
            openLesson.setOnClickListener(view -> {
                sheet.dismiss();
                openLessonPreview(lessonId, SafeJson.string(source, "", "lesson_title"));
            });
            content.findViewById(R.id.buttonTeacherQuestionClose)
                    .setOnClickListener(view -> sheet.dismiss());
            keepQuestionComposerAboveKeyboard(content, sheet, replyInput);
            sheet.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity",
                    "Không thể hiện cuộc trò chuyện học viên", exception);
            showErrorDialog("Không thể đọc câu hỏi của học sinh");
        }
    }

    private void bindQuestionReplies(View content, JSONObject question, JSONArray replies) {
        LinearLayout container = content.findViewById(R.id.layoutTeacherQuestionReplies);
        TextView empty = content.findViewById(R.id.textTeacherQuestionNoReplies);
        TextView title = content.findViewById(R.id.textTeacherQuestionRepliesTitle);
        container.removeAllViews();
        int replyCount = replies == null ? 0 : replies.length();
        title.setText(replyCount == 0
                ? "Lịch sử trao đổi" : "Lịch sử trao đổi • " + replyCount + " phản hồi");
        if (replyCount == 0) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int index = 0; index < replies.length(); index++) {
            JSONObject reply = replies.optJSONObject(index);
            if (reply == null) continue;
            boolean teacher = SafeJson.bool(reply, false, "is_teacher");
            View row = inflater.inflate(R.layout.teacher_item_question_reply, container, false);
            TextView avatar = row.findViewById(R.id.textTeacherReplyAvatar);
            TextView author = row.findViewById(R.id.textTeacherReplyAuthor);
            TextView time = row.findViewById(R.id.textTeacherReplyTime);
            TextView message = row.findViewById(R.id.textTeacherReplyContent);
            String replyName = SafeJson.string(reply, "Học sinh", "user", "username");
            String replyInitial = replyName.trim().isEmpty() ? "HS"
                    : replyName.trim().substring(0, 1).toUpperCase(java.util.Locale.getDefault());
            avatar.setText(teacher ? "GV" : replyInitial);
            author.setText(teacher ? "Bạn đã trả lời" : replyName + " phản hồi");
            time.setText(TeacherQuestionUiFormatter.timeLabel(reply));
            time.setVisibility(time.getText().length() == 0 ? View.GONE : View.VISIBLE);
            message.setText(SafeJson.string(reply, "Không có nội dung", "content"));
            message.setBackgroundResource(teacher
                    ? R.drawable.teacher_bg_question_reply_teacher
                    : R.drawable.teacher_bg_question_reply_student);
            container.addView(row);
        }
    }

    private void bindText(View root, int viewId, String value) {
        TextView view = root.findViewById(viewId);
        view.setText(value == null ? "" : value);
        view.setVisibility(view.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void keepQuestionComposerAboveKeyboard(View content, BottomSheetDialog sheet,
                                                   TextInputEditText input) {
        ScrollView root = content.findViewById(R.id.rootTeacherQuestionSheet);
        if (root == null) return;
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) input.postDelayed(() -> {
                Rect rectangle = new Rect();
                input.getDrawingRect(rectangle);
                root.offsetDescendantRectToMyCoords(input, rectangle);
                int margin = (int) (20 * getResources().getDisplayMetrics().density);
                root.smoothScrollTo(0, Math.max(0, rectangle.top - margin));
            }, 320);
        });
        sheet.setOnShowListener(ignored -> {
            if (sheet.getWindow() != null) {
                sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
    }

    /** Mở bài học ngay trong app (trình phát của LessonPlayerActivity, chế độ xem trước). */
    private void openLessonPreview(String lessonId, String lessonTitle) {
        try {
            Intent intent = new Intent(this,
                    com.example.smartkid.feature.student.course.LessonPlayerActivity.class);
            intent.putExtra(AppConstants.EXTRA_LESSON_ID, lessonId.trim());
            if (!lessonTitle.isEmpty()) {
                intent.putExtra(AppConstants.EXTRA_LESSON_TITLE, lessonTitle);
            }
            intent.putExtra(com.example.smartkid.feature.student.course.LessonPlayerActivity
                    .EXTRA_PREVIEW_MODE, true);
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity", "Không thể mở bài học", exception);
            showErrorDialog("Không thể mở bài học để xem trước");
        }
    }

    private void appendInfoLine(StringBuilder target, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append('\n');
        // Nhãn rỗng: chỉ in giá trị, không thêm dấu ":" đứng đầu dòng.
        if (label != null && !label.isEmpty()) target.append(label).append(": ");
        target.append(value.trim());
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

    private boolean isQaFeature() {
        return spec != null && "teacher_qa".equals(spec.getActionKind());
    }

    private boolean isExamFeature() {
        return spec != null && "teacher_exams".equals(spec.getActionKind());
    }

    private void showActions(FeatureItem item) {
        if (item == null) return;
        String kind = spec.getActionKind();
        if ("teacher_exams".equals(kind)) {
            showExamActions(item);
            return;
        }
        String[] labels;
        if ("teacher_courses".equals(kind)) {
            boolean published = item.getSource().optBoolean("published", false)
                    || "published".equals(SafeJson.string(item.getSource(), "", "status"));
            labels = published
                    ? new String[]{"Quản lý nội dung", "Gỡ xuất bản", "Xóa"}
                    : new String[]{"Quản lý nội dung", "Xuất bản", "Xóa"};
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

    @SuppressLint("InflateParams")
    private void showExamActions(FeatureItem item) {
        try {
            JSONObject source = item.getSource();
            boolean published = source.optBoolean("published", false);
            int questionCount = examQuestionCount(source);
            BottomSheetDialog sheet = new BottomSheetDialog(
                    this, R.style.ThemeOverlay_Smartkid_NotificationSheet);
            View content = getLayoutInflater().inflate(R.layout.teacher_sheet_exam_actions, null);
            sheet.setContentView(content);

            bindText(content, R.id.textTeacherExamSheetTitle, item.getTitle());
            bindText(content, R.id.textTeacherExamSheetMeta, examMeta(source));
            TextView status = content.findViewById(R.id.textTeacherExamSheetStatus);
            status.setText(published ? "Đang mở cho học sinh" : "Bản nháp • Học sinh chưa thấy");
            status.setBackgroundResource(published
                    ? R.drawable.teacher_bg_exam_published : R.drawable.teacher_bg_exam_draft);
            status.setTextColor(androidx.core.content.ContextCompat.getColor(this, published
                    ? R.color.teacher_exam_published_text : R.color.teacher_exam_draft_text));

            content.findViewById(R.id.buttonTeacherExamClose)
                    .setOnClickListener(view -> sheet.dismiss());
            content.findViewById(R.id.buttonTeacherExamEdit).setOnClickListener(view -> {
                sheet.dismiss();
                openExamEditor(item);
            });
            content.findViewById(R.id.buttonTeacherExamStats).setOnClickListener(view -> {
                sheet.dismiss();
                showStatistics(item);
            });

            MaterialButton publishButton = content.findViewById(R.id.buttonTeacherExamPublish);
            String publishAction = getString(published
                    ? R.string.teacher_unpublish : R.string.teacher_publish);
            publishButton.setText(published
                    ? R.string.teacher_pause_exam : R.string.teacher_publish);
            publishButton.setIconResource(published
                    ? R.drawable.teacher_ic_unpublish : R.drawable.teacher_ic_publish);
            TextView hint = content.findViewById(R.id.textTeacherExamPublishHint);
            boolean publishBlocked = !published && questionCount == 0;
            publishButton.setEnabled(!publishBlocked);
            if (publishBlocked) {
                hint.setVisibility(View.VISIBLE);
                hint.setText(R.string.teacher_publish_blocked);
            } else if (published) {
                hint.setVisibility(View.VISIBLE);
                hint.setText(R.string.teacher_unpublish_hint);
                hint.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                        R.color.smartkid_text_secondary));
            }
            publishButton.setOnClickListener(view -> {
                sheet.dismiss();
                confirmAction(item, publishAction);
            });
            content.findViewById(R.id.buttonTeacherExamDelete).setOnClickListener(view -> {
                sheet.dismiss();
                confirmAction(item, "Xóa");
            });
            sheet.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity",
                    "Không thể mở quản lý bài kiểm tra", exception);
            showErrorDialog("Không thể mở bài kiểm tra");
        }
    }

    private void openExamEditor(FeatureItem item) {
        if (item == null || item.getId().isEmpty()) {
            showErrorDialog("Bài kiểm tra không có mã hợp lệ");
            return;
        }
        try {
            Intent intent = new Intent(this, TeacherExerciseEditorActivity.class);
            intent.putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                    ExerciseScope.STANDALONE_EXAM.name());
            intent.putExtra(ContentFormActivity.EXTRA_EDIT_ID, item.getId());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity",
                    "Không thể chỉnh sửa bài kiểm tra", exception);
            showErrorDialog("Không thể mở trình chỉnh sửa bài kiểm tra");
        }
    }

    private int examQuestionCount(JSONObject source) {
        JSONArray questions = source == null ? null : source.optJSONArray("questions");
        return questions == null ? 0 : questions.length();
    }

    private String examMeta(JSONObject source) {
        int questions = examQuestionCount(source);
        JSONObject settings = source == null ? null : source.optJSONObject("settings");
        int seconds = settings == null ? 0
                : SafeJson.integer(settings, 0, "duration_seconds", "time_limit_seconds");
        return questions + " câu hỏi  •  "
                + (seconds > 0 ? Math.max(1, seconds / 60) + " phút" : "Không giới hạn")
                + "  •  " + questionTypeLabel(SafeJson.string(source, "mcq", "type"));
    }

    private void confirmAction(FeatureItem item, String label) {
        if ("Quản lý nội dung".equals(label)) {
            openCourseContent(item);
            return;
        }
        if ("Xuất bản".equals(label) && hasNoPlayableContent(item)) {
            showErrorDialog(publishBlockedMessage());
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
            boolean exam = isExamFeature();
            new AlertDialog.Builder(this).setTitle(exam
                            ? "Xóa bài kiểm tra?" : "Xóa dữ liệu")
                    .setMessage(exam
                            ? "“" + item.getTitle()
                                    + "” sẽ bị xóa vĩnh viễn. Bạn không thể hoàn tác."
                            : "Xóa vĩnh viễn “" + item.getTitle()
                                    + "” khỏi server? Thao tác này không thể hoàn tác.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Xóa", (dialog, which) -> deleteItem(item))
                    .show();
            return;
        }
        if (isExamFeature() && ("Xuất bản".equals(label) || "Gỡ xuất bản".equals(label))) {
            boolean publish = "Xuất bản".equals(label);
            new AlertDialog.Builder(this)
                    .setTitle(publish ? "Xuất bản bài kiểm tra?" : "Tạm đóng bài kiểm tra?")
                    .setMessage(publish
                            ? "Học sinh sẽ thấy và có thể bắt đầu làm bài này."
                            : "Học sinh sẽ không còn thấy bài này trong danh sách làm bài.")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(publish ? "Xuất bản" : "Tạm đóng",
                            (dialog, which) -> performAction(item, label))
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
        if ("teacher_courses".equals(spec.getActionKind())) {
            // Server yêu cầu khóa học có ít nhất 1 chương + bài học mới cho xuất bản.
            return source.optInt("lessonsCount", 0) <= 0;
        }
        return false;
    }

    /** Thông báo chặn xuất bản, đúng ngữ cảnh khóa học hay bài kiểm tra. */
    private String publishBlockedMessage() {
        return "teacher_courses".equals(spec == null ? "" : spec.getActionKind())
                ? "Hãy thêm ít nhất một chương và một bài học trước khi xuất bản."
                : "Hãy thêm ít nhất một câu hỏi trước khi xuất bản.";
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
                showExamStatistics(item, data == null ? new JSONObject() : data);
            }

            @Override public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    @SuppressLint("InflateParams")
    private void showExamStatistics(FeatureItem item, JSONObject data) {
        try {
            BottomSheetDialog sheet = new BottomSheetDialog(
                    this, R.style.ThemeOverlay_Smartkid_NotificationSheet);
            View content = getLayoutInflater().inflate(R.layout.teacher_sheet_exam_stats, null);
            sheet.setContentView(content);
            bindText(content, R.id.textTeacherExamStatsTitle, item.getTitle());
            bindText(content, R.id.textTeacherExamSubmissions,
                    String.valueOf(SafeJson.integer(data, 0, "submissions", "total_attempts")));
            bindText(content, R.id.textTeacherExamAverage,
                    readableMetric(SafeJson.decimal(data, 0, "avgScore", "avg_score")));
            bindText(content, R.id.textTeacherExamPassRate,
                    readableMetric(SafeJson.decimal(data, 0, "passRate", "pass_rate")) + "%");

            JSONArray students = SafeJson.array(data, "top_students");
            StringBuilder ranking = new StringBuilder();
            for (int index = 0; index < Math.min(3, students.length()); index++) {
                JSONObject student = students.optJSONObject(index);
                if (student == null) continue;
                if (ranking.length() > 0) ranking.append('\n');
                ranking.append(index + 1).append(". ")
                        .append(SafeJson.string(student, "Học sinh", "student_name", "name"))
                        .append("  •  ")
                        .append(readableMetric(SafeJson.decimal(student, 0, "score", "total_score")))
                        .append(" điểm");
            }
            TextView topStudents = content.findViewById(R.id.textTeacherExamTopStudents);
            topStudents.setText(ranking.length() == 0
                    ? "Chưa có học sinh hoàn thành bài kiểm tra này." : ranking.toString());
            View.OnClickListener close = view -> sheet.dismiss();
            content.findViewById(R.id.buttonTeacherExamStatsClose).setOnClickListener(close);
            content.findViewById(R.id.buttonTeacherExamStatsDone).setOnClickListener(close);
            sheet.show();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherManagementActivity",
                    "Không thể hiện thống kê bài kiểm tra", exception);
            showErrorDialog("Không thể đọc kết quả bài kiểm tra");
        }
    }

    private String readableMetric(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(java.util.Locale.US, "%.1f", value);
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
        // Khóa học và bài kiểm tra nằm ở hai API khác nhau.
        String endpoint = "teacher_courses".equals(spec == null ? "" : spec.getActionKind())
                ? "content/courses/" + item.getId() + "/"
                : "activities/exercises/" + item.getId() + "/";
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
