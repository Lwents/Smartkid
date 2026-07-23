package com.example.smartkid.feature.management;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.ui.FeatureItemAdapter;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.common.ui.BaseActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Danh sách quản lý dùng API thật, kèm thao tác theo quyền của từng nhóm. */
public class ManagementFeatureActivity extends BaseActivity {
    public static final String EXTRA_SPEC_KEY = "management_spec_key";

    private ManagementSpec spec;
    private ManagementRepository repository;
    private FeatureItemAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyText;
    private View refreshButton;

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
            spec = ManagementSpec.get(key);
            if (spec == null || !spec.isAvailable()) {
                showErrorDialog("Chức năng quản lý không hợp lệ");
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            MaterialToolbar toolbar = findViewById(R.id.toolbarFeatureList);
            progressBar = findViewById(R.id.progressFeatureList);
            emptyText = findViewById(R.id.textFeatureListEmpty);
            refreshButton = findViewById(R.id.buttonFeatureAction);
            TextInputEditText search = findViewById(R.id.inputFeatureSearch);
            ListView list = findViewById(R.id.listFeatures);
            if (toolbar == null || progressBar == null || emptyText == null || refreshButton == null
                    || search == null || list == null) {
                throw new IllegalStateException("Giao diện quản lý chưa đầy đủ");
            }
            toolbar.setTitle(spec.getTitle());
            toolbar.setNavigationOnClickListener(view -> finish());
            configurePrimaryAction(toolbar);
            emptyText.setText(R.string.no_server_data);
            adapter = new FeatureItemAdapter(this);
            list.setAdapter(adapter);
            list.setEmptyView(emptyText);
            list.setOnItemClickListener((parent, row, position, id) ->
                    showItem(adapter.getItem(position)));
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            loadSafely();
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể tạo chức năng", exception);
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
        return "admin_users".equals(kind) || "teacher_courses".equals(kind)
                || "teacher_exams".equals(kind) || "teacher_games".equals(kind);
    }

    private void openCreate() {
        try {
            Intent intent = new Intent(this, ManagementCreateActivity.class);
            intent.putExtra(ManagementCreateActivity.EXTRA_KIND, spec.getActionKind());
            startActivity(intent);
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể mở tạo mới", exception);
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
                adapter.setItems(data);
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setLoading(false);
                handleApiError(error);
            }
        });
    }

    private void showItem(FeatureItem item) {
        if (item == null) return;
        try {
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể hiện chi tiết", exception);
            showErrorDialog("Không thể đọc chi tiết dữ liệu");
        }
    }

    private void showActions(FeatureItem item) {
        String kind = spec.getActionKind();
        String[] labels;
        if ("admin_courses".equals(kind)) {
            labels = new String[]{"Duyệt", "Từ chối", "Xuất bản", "Gỡ xuất bản", "Lưu trữ", "Khôi phục"};
        } else if ("admin_users".equals(kind)) {
            labels = new String[]{"Khóa tài khoản", "Mở khóa tài khoản"};
        } else if ("teacher_courses".equals(kind)) {
            labels = new String[]{"Xuất bản", "Gỡ xuất bản", "Lưu trữ", "Khôi phục"};
        } else if ("teacher_exams".equals(kind) || "teacher_games".equals(kind)) {
            labels = new String[]{"Thêm câu hỏi", "Xem thống kê", "Xuất bản", "Gỡ xuất bản", "Xóa"};
        } else if ("teacher_exam_reports".equals(kind)) {
            labels = new String[]{"Xem thống kê", "Xem lượt nộp"};
        } else if ("teacher_qa".equals(kind)) {
            labels = new String[]{"Trả lời học viên"};
        } else if ("teacher_students".equals(kind)) {
            labels = new String[]{"Gửi phản hồi"};
        } else if ("admin_transactions".equals(kind)) {
            // Backend refund hiện chỉ đổi status trong DB, chưa gọi hoàn tiền ở cổng MoMo.
            labels = new String[]{"Đánh dấu tranh chấp"};
        } else {
            return;
        }
        new AlertDialog.Builder(this).setTitle("Chọn thao tác")
                .setItems(labels, (dialog, which) -> confirmAction(item, labels[which]))
                .show();
    }

    private void confirmAction(FeatureItem item, String label) {
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
        if ("admin_transactions".equals(spec.getActionKind())) {
            String hint = "Hoàn tiền".equals(label)
                    ? "Nhập lý do hoàn tiền" : "Nhập ghi chú tranh chấp";
            promptText(label, hint, "Tiếp tục",
                    value -> confirmSensitiveTransaction(item, label, value));
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

    private boolean hasNoPlayableContent(FeatureItem item) {
        if (item == null || spec == null) return false;
        JSONObject source = item.getSource();
        if ("teacher_exams".equals(spec.getActionKind())) {
            JSONArray questions = source.optJSONArray("questions");
            return questions == null || questions.length() == 0;
        }
        if ("teacher_games".equals(spec.getActionKind())) {
            return source.optInt("question_count", 0) <= 0;
        }
        return false;
    }

    private void promptQuestion(FeatureItem item) {
        if ("teacher_games".equals(spec.getActionKind())) {
            loadGameForQuestion(item);
        } else {
            showChoiceQuestionDialog(item, false, null);
        }
    }

    private void loadGameForQuestion(FeatureItem item) {
        setLoading(true);
        repository.loadObject("teacher/games/" + item.getId() + "/",
                new ApiCallback<JSONObject>() {
                    @Override public void onSuccess(JSONObject game) {
                        if (!isUsable()) return;
                        setLoading(false);
                        String type = game == null ? "" : game.optString("game_type", "");
                        if ("word_match".equals(type)) showWordPairDialog(item, game);
                        else showChoiceQuestionDialog(item, true, game);
                    }

                    @Override public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        handleApiError(error);
                    }
                });
    }

    private void showChoiceQuestionDialog(FeatureItem item, boolean game, JSONObject gameData) {
        try {
            LinearLayout fields = dialogFields();
            EditText prompt = dialogInput(game ? "Nội dung câu hỏi" : "Câu hỏi kiểm tra");
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
                        if (game) addGameQuestion(item, gameData, promptValue,
                                optionValues, correct.getSelectedItemPosition());
                        else addExamQuestion(item, promptValue, optionValues,
                                correct.getSelectedItemPosition());
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể mở câu hỏi", exception);
            showErrorDialog("Không thể mở biểu mẫu câu hỏi");
        }
    }

    private void showWordPairDialog(FeatureItem item, JSONObject gameData) {
        try {
            LinearLayout fields = dialogFields();
            EditText left = dialogInput("Từ hoặc phép tính bên trái");
            EditText right = dialogInput("Nội dung tương ứng bên phải");
            fields.addView(left);
            fields.addView(right);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Thêm cặp ghép từ").setView(fields)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton("Lưu lên server", null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String leftValue = textOf(left);
                        String rightValue = textOf(right);
                        if (leftValue.isEmpty()) { left.setError("Không được để trống"); return; }
                        if (rightValue.isEmpty()) { right.setError("Không được để trống"); return; }
                        dialog.dismiss();
                        addGamePair(item, gameData, leftValue, rightValue);
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể mở cặp từ", exception);
            showErrorDialog("Không thể mở biểu mẫu ghép từ");
        }
    }

    private void addExamQuestion(FeatureItem item, String prompt, JSONArray options,
                                 int correctIndex) {
        try {
            JSONObject body = new JSONObject();
            body.put("prompt", prompt);
            body.put("meta", new JSONObject().put("type", "single").put("points", 1));
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể thêm câu hỏi", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị câu hỏi kiểm tra");
        }
    }

    private void addGameQuestion(FeatureItem item, JSONObject gameData, String prompt,
                                 JSONArray options, int correctIndex) {
        try {
            JSONArray questions = copyArray(gameData == null ? null : gameData.optJSONArray("questions"));
            questions.put(new JSONObject().put("id", java.util.UUID.randomUUID().toString())
                    .put("question", prompt).put("options", options).put("correct", correctIndex));
            saveGameQuestions(item, questions);
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể thêm câu hỏi game", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị câu hỏi trò chơi");
        }
    }

    private void addGamePair(FeatureItem item, JSONObject gameData, String left, String right) {
        try {
            JSONArray questions = copyArray(gameData == null ? null : gameData.optJSONArray("questions"));
            questions.put(new JSONObject().put("id", java.util.UUID.randomUUID().toString())
                    .put("left", left).put("right", right));
            saveGameQuestions(item, questions);
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể thêm cặp từ", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị cặp ghép từ");
        }
    }

    private void saveGameQuestions(FeatureItem item, JSONArray questions) throws Exception {
        setLoading(true);
        repository.action(Request.Method.PUT, "teacher/games/" + item.getId() + "/",
                new JSONObject().put("questions", questions),
                actionCallback("Đã cập nhật nội dung trò chơi trên server"));
    }

    private void showStatistics(FeatureItem item) {
        String endpoint = "teacher_games".equals(spec.getActionKind())
                ? "teacher/games/" + item.getId() + "/stats/"
                : "activities/exercises/" + item.getId() + "/stats/";
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
                        new AlertDialog.Builder(ManagementFeatureActivity.this)
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
        String endpoint = "teacher_games".equals(spec.getActionKind())
                ? "teacher/games/" + item.getId() + "/"
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể hiện thống kê", exception);
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

    private JSONArray copyArray(JSONArray source) throws Exception {
        return source == null ? new JSONArray() : new JSONArray(source.toString());
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể gửi phản hồi", exception);
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể mở phản hồi", exception);
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể tạo phản hồi", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị phản hồi học viên");
        }
    }

    private void confirmSensitiveTransaction(FeatureItem item, String label, String note) {
        new AlertDialog.Builder(this).setTitle("Xác nhận "
                        + label.toLowerCase(Locale.getDefault()))
                .setMessage("Giao dịch: " + item.getId()
                        + "\n\nThao tác này cập nhật dữ liệu thanh toán thật trên server.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        performTransactionAction(item, label, note)).show();
    }

    private void performTransactionAction(FeatureItem item, String label, String note) {
        try {
            boolean refund = "Hoàn tiền".equals(label);
            JSONObject body = new JSONObject();
            body.put(refund ? "reason" : "note", note);
            setLoading(true);
            repository.action(Request.Method.POST,
                    "admin/transactions/" + item.getId()
                            + (refund ? "/refund/" : "/dispute/"),
                    body, actionCallback(refund ? "Đã cập nhật trạng thái hoàn tiền"
                            : "Đã đánh dấu giao dịch tranh chấp"));
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể cập nhật giao dịch", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị thao tác giao dịch");
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
                            AppLogger.error(this, "ManagementFeatureActivity",
                                    "Không thể xử lý biểu mẫu", exception);
                            setLoading(false);
                        }
                    }));
            dialog.show();
        } catch (Exception exception) {
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể mở biểu mẫu", exception);
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
            if ("admin_courses".equals(kind)) {
                String action = adminCourseAction(label);
                endpoint = "admin/courses/" + item.getId() + "/" + action + "/";
                method = Request.Method.POST;
                if ("reject".equals(action)) body.put("reason", "Từ chối từ ứng dụng Android");
            } else if ("admin_users".equals(kind)) {
                endpoint = "account/admin/users/" + item.getId() + "/";
                method = Request.Method.PATCH;
                body.put("is_active", label.startsWith("Mở"));
            } else if ("teacher_courses".equals(kind)) {
                if ("Xuất bản".equals(label) || "Khôi phục".equals(label)) {
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
            } else if ("teacher_games".equals(kind)) {
                endpoint = "teacher/games/" + item.getId() + "/";
                method = Request.Method.PUT;
                body.put("is_published", "Xuất bản".equals(label));
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
            AppLogger.error(this, "ManagementFeatureActivity", "Không thể thao tác", exception);
            setLoading(false);
            showErrorDialog("Không thể chuẩn bị thao tác quản lý");
        }
    }

    private String adminCourseAction(String label) {
        if ("Duyệt".equals(label)) return "approve";
        if ("Từ chối".equals(label)) return "reject";
        if ("Xuất bản".equals(label)) return "publish";
        if ("Gỡ xuất bản".equals(label)) return "unpublish";
        if ("Lưu trữ".equals(label)) return "archive";
        return "restore";
    }

    private String limit(String value) {
        return value.length() > 3000 ? value.substring(0, 3000) + "…" : value;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!loading);
    }

    private boolean isUsable() { return !isFinishing() && !isDestroyed(); }

    private interface TextValueAction { void run(String value); }
}
