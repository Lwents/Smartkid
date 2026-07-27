package com.example.smartkid.feature.student.ai;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.StudentFeatureRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

/** Trò chuyện với AI Tutor qua API thật; không sinh câu trả lời mẫu trên thiết bị. */
public class AITutorActivity extends BaseActivity {
    private TextView conversationText;
    private TextView statusText;
    private TextInputEditText messageInput;
    private Button sendButton;
    private ProgressBar progressBar;
    private StudentFeatureRepository repository;
    private String lessonId;
    private String lessonTitle;
    private final StringBuilder conversation = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.ai_activity_tutor);
            repository = new StudentFeatureRepository(this);
            lessonId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_ID));
            lessonTitle = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(AppConstants.EXTRA_LESSON_TITLE));
            bindViews();
            MaterialToolbar toolbar = findViewById(R.id.toolbarAiTutor);
            if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng Gia Sư AI");
            toolbar.setNavigationOnClickListener(view -> finish());
            if (!lessonTitle.isEmpty()) toolbar.setSubtitle(lessonTitle);
            sendButton.setOnClickListener(view -> sendSafely());
            keepComposerAboveKeyboard();
            bindConversation();
        } catch (Exception exception) {
            AppLogger.error(this, "AITutorActivity", "Không thể tạo Gia Sư AI", exception);
            showErrorDialog("Không thể mở Gia Sư AI");
        }
    }

    private void bindViews() {
        conversationText = findViewById(R.id.textAiConversation);
        statusText = findViewById(R.id.textAiStatus);
        messageInput = findViewById(R.id.inputAiMessage);
        sendButton = findViewById(R.id.buttonAiSend);
        progressBar = findViewById(R.id.progressAiTutor);
        if (conversationText == null || statusText == null || messageInput == null
                || sendButton == null || progressBar == null) {
            throw new IllegalStateException("Giao diện Gia Sư AI thiếu thành phần bắt buộc");
        }
    }

    /** Edge-to-edge không tự co layout ổn định trên mọi máy, nên bù đúng chiều cao IME. */
    private void keepComposerAboveKeyboard() {
        View root = findViewById(R.id.rootAiTutor);
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

    private void sendSafely() {
        try {
            String message = textOf(messageInput);
            if (message.isEmpty()) {
                showStatus("Bạn hãy nhập câu hỏi trước khi gửi");
                return;
            }
            if (message.length() > 1000) {
                showStatus("Câu hỏi tối đa 1000 ký tự");
                return;
            }
            appendLine("Bạn", message);
            messageInput.setText("");
            setLoading(true);
            repository.chatWithTutor(message, lessonId, lessonTitle,
                    new ApiCallback<String>() {
                        @Override
                        public void onSuccess(String answer) {
                            if (!isUsable()) return;
                            setLoading(false);
                            appendLine("Gia Sư AI", answer);
                            showStatus("Đã nhận câu trả lời từ server");
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
            AppLogger.error(this, "AITutorActivity", "Không thể gửi câu hỏi", exception);
            setLoading(false);
            showStatus("Không thể gửi câu hỏi tới server");
        }
    }

    private void appendLine(String speaker, String value) {
        if (conversation.length() > 0) conversation.append("\n");
        conversation.append(speaker).append(": ").append(safe(value)).append("\n");
        bindConversation();
    }

    private void bindConversation() {
        conversationText.setText(conversation.toString());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!loading);
        messageInput.setEnabled(!loading);
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
}
