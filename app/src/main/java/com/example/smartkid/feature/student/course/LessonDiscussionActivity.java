package com.example.smartkid.feature.student.course;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import com.example.smartkid.common.util.SwipeRefreshFix;

/** Hỏi đáp theo bài học, đọc và cập nhật trực tiếp trên PostgreSQL qua API. */
public class LessonDiscussionActivity extends BaseActivity {
    private StudentFeatureRepository repository;
    private LessonQuestionAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private TextView statusText;
    private TextInputEditText questionInput;
    private View sendButton;
    private SwipeRefreshLayout refreshLayout;
    private String lessonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.course_activity_lesson_discussion);
            lessonId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID));
            if (lessonId.isEmpty()) {
                showErrorDialog("Không tìm thấy mã bài học");
                finish();
                return;
            }
            repository = new StudentFeatureRepository(this);
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarLessonDiscussion);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng hỏi đáp");
            toolbar.setNavigationOnClickListener(view -> finish());
            String title = getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE);
            if (title != null && !title.trim().isEmpty()) toolbar.setSubtitle(title.trim());
            keepComposerAboveKeyboard();
            ListView list = findViewById(R.id.listLessonQuestions);
            if (list == null) throw new IllegalStateException("Thiếu danh sách hỏi đáp");
            adapter = new LessonQuestionAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) ->
                    showQuestion(adapter.getItem(position)));
            sendButton.setOnClickListener(view -> createQuestionSafely());
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể tạo hỏi đáp", exception);
            showErrorDialog("Không thể mở hỏi đáp bài học");
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressLessonDiscussion);
        emptyText = findViewById(R.id.textLessonDiscussionEmpty);
        statusText = findViewById(R.id.textLessonDiscussionStatus);
        questionInput = findViewById(R.id.inputLessonQuestion);
        sendButton = findViewById(R.id.buttonSendLessonQuestion);
        refreshLayout = findViewById(R.id.refreshLessonDiscussion);
        SwipeRefreshFix.attach(refreshLayout);
        if (progressBar == null || emptyText == null || statusText == null
                || questionInput == null || sendButton == null || refreshLayout == null) {
            throw new IllegalStateException("Giao diện hỏi đáp thiếu thành phần bắt buộc");
        }
        refreshLayout.setOnRefreshListener(this::loadSafely);
    }

    /** Edge-to-edge needs explicit IME padding so the question box remains visible. */
    private void keepComposerAboveKeyboard() {
        View root = findViewById(R.id.rootLessonDiscussion);
        if (root == null) return;
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int keyboardPadding = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
                    ? Math.max(0, ime.bottom - bars.bottom) : 0;
            view.setPadding(left, top, right, bottom + keyboardPadding);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void loadSafely() {
        try {
            setLoading(true);
            repository.loadLessonQuestions(lessonId, new ApiCallback<List<FeatureItem>>() {
                @Override
                public void onSuccess(List<FeatureItem> data) {
                    if (!isUsable()) return;
                    setLoading(false);
                    adapter.setItems(data == null ? new ArrayList<>() : data);
                    showStatus(data == null || data.isEmpty()
                            ? "Chưa có câu hỏi nào. Em hãy hỏi thầy cô nhé!"
                            : "Chạm vào một câu hỏi để xem thầy cô trả lời");
                }

                @Override
                public void onError(ApiError error) {
                    if (!isUsable()) return;
                    setLoading(false);
                    handleApiError(error);
                }
            });
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể tải hỏi đáp", exception);
            setLoading(false);
            showStatus("Không thể tải hỏi đáp");
        }
    }

    private void createQuestionSafely() {
        String content = textOf(questionInput);
        if (content.isEmpty()) {
            showStatus("Bạn hãy nhập nội dung câu hỏi");
            return;
        }
        setLoading(true);
        repository.createLessonQuestion(lessonId, content, refreshCallback(
                "Đã gửi câu hỏi tới giáo viên", () -> questionInput.setText("")));
    }

    @SuppressLint("InflateParams")
    private void showQuestion(FeatureItem item) {
        if (item == null) return;
        try {
            JSONObject source = item.getSource();
            JSONArray replies = SafeJson.array(source, "replies");
            BottomSheetDialog sheet = new BottomSheetDialog(
                    this, R.style.ThemeOverlay_Smartkid_NotificationSheet);
            View content = getLayoutInflater().inflate(R.layout.course_sheet_lesson_question, null);
            sheet.setContentView(content);

            bindText(content, R.id.textDiscussionQuestionAuthor,
                    LessonDiscussionUiFormatter.questionAuthor(source) + " đã hỏi:");
            bindText(content, R.id.textDiscussionQuestionTime,
                    LessonDiscussionUiFormatter.timeLabel(source));
            bindText(content, R.id.textDiscussionQuestionContent, item.getDetail());
            bindText(content, R.id.textDiscussionRepliesTitle,
                    LessonDiscussionUiFormatter.repliesTitle(replies));
            bindReplies(content, replies);

            boolean owner = SafeJson.bool(source, false, "is_owner");
            boolean reacted = SafeJson.bool(source, false, "reacted");
            int likes = SafeJson.integer(source, 0, "reactions_count");
            TextView likeButton = content.findViewById(R.id.buttonDiscussionLike);
            likeButton.setText(reacted
                    ? "Đã thích" + (likes > 0 ? " · " + likes : "")
                    : "Thích" + (likes > 0 ? " · " + likes : ""));

            View editButton = content.findViewById(R.id.buttonDiscussionEdit);
            View deleteButton = content.findViewById(R.id.buttonDiscussionDelete);
            View reportButton = content.findViewById(R.id.buttonDiscussionReport);
            editButton.setVisibility(owner ? View.VISIBLE : View.GONE);
            deleteButton.setVisibility(owner ? View.VISIBLE : View.GONE);
            reportButton.setVisibility(owner ? View.GONE : View.VISIBLE);

            content.findViewById(R.id.buttonDiscussionReply).setOnClickListener(view -> {
                sheet.dismiss();
                performAction(item, "Trả lời");
            });
            likeButton.setOnClickListener(view -> {
                sheet.dismiss();
                performAction(item, reacted ? "Bỏ thích" : "Thích");
            });
            editButton.setOnClickListener(view -> {
                sheet.dismiss();
                performAction(item, "Sửa câu hỏi");
            });
            deleteButton.setOnClickListener(view -> {
                sheet.dismiss();
                performAction(item, "Xóa câu hỏi");
            });
            reportButton.setOnClickListener(view -> {
                sheet.dismiss();
                performAction(item, "Báo cáo nội dung");
            });
            content.findViewById(R.id.buttonDiscussionCloseTop)
                    .setOnClickListener(view -> sheet.dismiss());
            sheet.show();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể hiện câu hỏi", exception);
            showErrorDialog("Không thể đọc nội dung hỏi đáp");
        }
    }

    private void bindReplies(View content, JSONArray replies) {
        LinearLayout container = content.findViewById(R.id.layoutDiscussionReplies);
        TextView empty = content.findViewById(R.id.textDiscussionNoReplies);
        container.removeAllViews();
        if (replies == null || replies.length() == 0) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int index = 0; index < replies.length(); index++) {
            JSONObject reply = replies.optJSONObject(index);
            if (reply == null) continue;
            View row = inflater.inflate(R.layout.course_item_lesson_reply, container, false);
            boolean teacher = LessonDiscussionUiFormatter.isTeacherReply(reply);
            TextView avatar = row.findViewById(R.id.textLessonReplyAvatar);
            TextView author = row.findViewById(R.id.textLessonReplyAuthor);
            TextView time = row.findViewById(R.id.textLessonReplyTime);
            TextView message = row.findViewById(R.id.textLessonReplyContent);
            String authorLabel = LessonDiscussionUiFormatter.replyAuthor(reply);
            avatar.setText(teacher ? "GV" : authorLabel);
            avatar.setBackgroundResource(teacher
                    ? R.drawable.discussion_bg_avatar_teacher
                    : R.drawable.discussion_bg_avatar_student);
            author.setText(teacher ? "Thầy/Cô trả lời" : authorLabel + " trả lời");
            time.setText(LessonDiscussionUiFormatter.timeLabel(reply));
            time.setVisibility(time.getText().length() == 0 ? View.GONE : View.VISIBLE);
            message.setText(SafeJson.string(reply, "Không có nội dung", "content"));
            message.setBackgroundResource(teacher
                    ? R.drawable.discussion_bg_reply_teacher
                    : R.drawable.discussion_bg_reply_student);
            container.addView(row);
        }
    }

    private void bindText(View root, int viewId, String value) {
        TextView view = root.findViewById(viewId);
        view.setText(value == null ? "" : value);
        view.setVisibility(view.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void performAction(FeatureItem item, String action) {
        if ("Trả lời".equals(action)) {
            showTextDialog("Nhập phản hồi", "Gửi", "", value ->
                    repository.replyLessonQuestion(item.getId(), value,
                            refreshCallback("Đã gửi phản hồi", null)));
        } else if ("Thích".equals(action) || "Bỏ thích".equals(action)) {
            setLoading(true);
            repository.reactLessonQuestion(item.getId(),
                    refreshCallback("Đã cập nhật lượt thích", null));
        } else if ("Sửa câu hỏi".equals(action)) {
            showTextDialog("Sửa câu hỏi", R.string.save, item.getDetail(), value ->
                    repository.editLessonQuestion(item.getId(), value,
                            refreshCallback("Đã sửa câu hỏi", null)));
        } else if ("Xóa câu hỏi".equals(action)) {
            confirmDelete(item);
        } else if ("Báo cáo nội dung".equals(action)) {
            showTextDialog("Chi tiết báo cáo", "Gửi báo cáo", "", value ->
                    repository.reportLessonQuestion(item.getId(), value,
                            refreshCallback("Đã gửi báo cáo tới quản trị viên", null)));
        }
    }

    private void confirmDelete(FeatureItem item) {
        new AlertDialog.Builder(this).setTitle("Xóa câu hỏi")
                .setMessage("Em có chắc muốn xóa câu hỏi này không? Các câu trả lời cũng sẽ bị xóa.")
                .setNegativeButton("Giữ lại", null)
                .setPositiveButton("Xóa", (dialog, which) -> {
                    setLoading(true);
                    repository.deleteLessonQuestion(item.getId(),
                            refreshCallback("Đã xóa câu hỏi", null));
                }).show();
    }

    private void showTextDialog(String title, Object positiveLabel, String initial,
                                TextAction action) {
        try {
            EditText input = new EditText(this);
            input.setText(initial == null ? "" : initial);
            input.setSelection(input.length());
            input.setMaxLines(5);
            input.setPadding(40, 24, 40, 24);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                    .setView(input).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(positiveLabel instanceof Integer
                                    ? getString((Integer) positiveLabel) : String.valueOf(positiveLabel),
                            null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String value = input.getText() == null ? ""
                                : input.getText().toString().trim();
                        if (value.isEmpty()) {
                            input.setError("Nội dung không được để trống");
                            return;
                        }
                        dialog.dismiss();
                        setLoading(true);
                        try { action.run(value); }
                        catch (Exception exception) {
                            AppLogger.error(this, "LessonDiscussionActivity",
                                    "Không thể thực hiện thao tác hỏi đáp", exception);
                            setLoading(false);
                            showStatus("Không thể chuẩn bị dữ liệu hỏi đáp");
                        }
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "LessonDiscussionActivity", "Không thể mở hộp nhập", exception);
            showErrorDialog("Không thể mở biểu mẫu nhập liệu");
        }
    }

    private ApiCallback<Boolean> refreshCallback(String message, Runnable beforeRefresh) {
        return new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean ignored) {
                if (!isUsable()) return;
                try {
                    if (beforeRefresh != null) beforeRefresh.run();
                    showShortMessage(message);
                    loadSafely();
                } catch (Exception exception) {
                    AppLogger.error(LessonDiscussionActivity.this,
                            "LessonDiscussionActivity", "Không thể làm mới hỏi đáp", exception);
                    setLoading(false);
                }
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        };
    }

    private void setLoading(boolean loading) {
        if (!loading && refreshLayout != null) {
            refreshLayout.setRefreshing(false);
        }
        boolean swiping = loading && refreshLayout != null && refreshLayout.isRefreshing();
        progressBar.setVisibility(loading && !swiping ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        questionInput.setEnabled(!loading);
    }

    private void showStatus(String message) {
        statusText.setText(safe(message));
        statusText.setVisibility(View.VISIBLE);
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private interface TextAction { void run(String value); }
}
