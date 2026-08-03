package com.example.smartkid.common.ui.form;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.remote.MultipartFilePart;
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.domain.AiQuestionPolicy;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bộ máy form soạn nội dung trung lập với role, được tách từ ManagementCreateActivity cũ.
 * Lớp này không tự rẽ nhánh theo role: mỗi lớp con cố định {@link #formKind()}, còn điều hướng
 * sau khi tạo thành công được giao cho {@link #onContentCreated(JSONObject)}. Nhờ đó lớp nền
 * không cần import bất kỳ màn hình {@code feature.*} nào.
 */
public abstract class ContentFormActivity extends BaseActivity {
    public static final String EXTRA_PARENT_ID = "management_create_parent_id";
    public static final String EXTRA_PARENT_TITLE = "management_create_parent_title";
    public static final String EXTRA_COURSE_ID = "management_create_course_id";
    public static final String EXTRA_POSITION = "management_create_position";
    public static final String EXTRA_EDIT_ID = "management_edit_id";

    /** Loại nội dung duy nhất mà màn hình tạo; được lớp con của từng role cố định. */
    protected abstract ContentFormKind formKind();

    /**
     * Hook chạy sau khi tạo thành công. Trả {@code true} nếu lớp con đã mở màn hình tiếp theo,
     * khi đó form chỉ cần tự đóng. Mặc định không có điều hướng tiếp.
     */
    protected boolean onContentCreated(JSONObject data) {
        return false;
    }

    /** Cho lớp con chỉ đọc course ID liên kết để điều hướng sau khi tạo. */
    protected final String linkedCourseId() {
        return linkedCourseId;
    }

    /** Cho lớp con chỉ đọc tiêu đề vừa tạo để truyền sang màn hình tiếp theo. */
    protected final String currentTitle() {
        return value("title");
    }

    /** ID cha (module/lesson/course) được truyền qua Intent mở form. */
    protected final String parentId() {
        return parentId;
    }

    /** Loại nội dung bài học đang được chọn, ví dụ {@code video} hoặc {@code exercise}. */
    protected final String selectedContentType() {
        return spinnerValue("content_type");
    }

    private final Map<String, TextInputEditText> inputs = new LinkedHashMap<>();
    private final Map<String, SpinnerBinding> spinners = new LinkedHashMap<>();
    private final List<FeatureItem> courseOptions = new ArrayList<>();
    private final List<QuestionFields> questions = new ArrayList<>();
    private final List<JSONObject> compactQuestions = new ArrayList<>();
    private JSONObject existingExerciseSettings = new JSONObject();

    private ActivityResultLauncher<String[]> thumbnailPicker;
    private ActivityResultLauncher<String[]> videoPicker;
    private ActivityResultLauncher<String[]> documentPicker;
    private ActivityResultLauncher<String[]> aiDocumentPicker;
    private Uri selectedThumbnailUri;
    private Uri selectedVideoUri;
    private Uri selectedDocumentUri;
    private TextView thumbnailFileName;
    private TextView videoFileName;
    private MaterialButton thumbnailFileButton;
    private MaterialButton videoFileButton;
    private MaterialButton thumbnailClearButton;
    private MaterialButton videoClearButton;
    private TextView documentFileName;
    private MaterialButton documentFileButton;
    private MaterialButton documentClearButton;
    private View videoFileRow;
    private View documentFileRow;
    private View lessonTextRow;
    private LinearLayout gradeButtonGroup;
    private MaterialButton[] gradeButtons;
    private String selectedGrade = "1";

    private LinearLayout container;
    private LinearLayout questionsContainer;
    private TextView compactQuestionsSummary;
    private Spinner courseSpinner;
    private SwitchMaterial shuffleQuestionsSwitch;
    private SwitchMaterial shuffleChoicesSwitch;
    private SwitchMaterial requiresExerciseSwitch;
    private SwitchMaterial lessonPublishedSwitch;
    private ProgressBar progressBar;
    private TextView statusText;
    private MaterialButton submitButton;
    private MaterialButton addQuestionButton;
    private MaterialButton aiQuestionButton;
    private ManagementRepository repository;
    private ContentFormKind kind;
    private String parentId;
    private String parentTitle;
    private String linkedCourseId;
    private String editId;
    private int defaultPosition;
    private String savedFormSnapshot;
    private boolean formSubmitted;

    /** Khởi tạo form động theo ContentFormKind, tải dữ liệu phụ và gắn hành động lưu. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerFilePickers();
        try {
            setContentView(R.layout.management_activity_create);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.managementCreateRoot,
                    R.drawable.role_bg_screen, true);
            findViewById(R.id.managementCreateRoot).setBackgroundResource(R.drawable.role_bg_screen);
            kind = formKind();
            parentId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(EXTRA_PARENT_ID));
            parentTitle = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(EXTRA_PARENT_TITLE));
            linkedCourseId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(EXTRA_COURSE_ID));
            editId = getIntent() == null ? ""
                    : safe(getIntent().getStringExtra(EXTRA_EDIT_ID));
            defaultPosition = getIntent() == null ? 0
                    : Math.max(0, getIntent().getIntExtra(EXTRA_POSITION, 0));
            if (kind == null || !kind.isAllowedFor(
                    UserRole.fromString(new SessionManager(this).getUser().getRole()))) {
                showErrorDialog("Chức năng tạo mới không hợp lệ");
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    requestClose();
                }
            });
            bindViews();
            bindToolbar();
            configureHeader();
            buildFields();
            submitButton.setOnClickListener(view -> submitSafely());
            if (kind == ContentFormKind.TEACHER_EXAM) loadCourseOptions();
            else if (kind == ContentFormKind.TEACHER_EXERCISE && !editId.isEmpty()) {
                loadExerciseForEdit();
            } else rememberFormState();
        } catch (Exception exception) {
            AppLogger.error(this, "ContentFormActivity", "Không thể tạo biểu mẫu", exception);
            showErrorDialog("Không thể mở biểu mẫu tạo mới");
        }
    }

    /** Ánh xạ toolbar, vùng chứa trường động, nút lưu và trạng thái request. */
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

    /** Cấu hình nút quay lại và cảnh báo khi form còn thay đổi chưa lưu. */
    private void bindToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarManagementCreate);
        if (toolbar == null) throw new IllegalStateException("Thiếu thanh điều hướng");
        toolbar.setNavigationOnClickListener(view -> requestClose());
        toolbar.setTitle("");
    }

    /** Đóng form ngay hoặc yêu cầu xác nhận nếu có dữ liệu chưa lưu. */
    private void requestClose() {
        if (formSubmitted || !hasUnsavedChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Bỏ các thay đổi?")
                .setMessage("Nội dung em đang nhập chưa được lưu.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Bỏ thay đổi", (dialog, which) -> finish())
                .show();
    }

    /** Chọn tiêu đề, mô tả và biểu tượng theo loại nội dung đang tạo/sửa. */
    private void configureHeader() {
        ImageView icon = findViewById(R.id.imageManagementCreateIcon);
        TextView heading = findViewById(R.id.textManagementCreateHeading);
        TextView subtitle = findViewById(R.id.textManagementCreateSubtitle);
        heading.setText(titleForKind());
        if (kind == ContentFormKind.TEACHER_COURSE) {
            icon.setImageResource(R.drawable.role_ic_course_filled);
            subtitle.setText(R.string.builder_create_course_subtitle);
            submitButton.setText(R.string.builder_create_and_add_content);
        } else if (kind == ContentFormKind.TEACHER_MODULE) {
            icon.setImageResource(R.drawable.role_ic_course);
            subtitle.setText(parentTitle.isEmpty() ? R.string.management_create_module_subtitle
                    : R.string.management_create_module_in_course_subtitle);
            submitButton.setText(R.string.teacher_add_module);
        } else if (kind == ContentFormKind.TEACHER_LESSON) {
            icon.setImageResource(R.drawable.role_ic_course_filled);
            subtitle.setText(parentTitle.isEmpty() ? R.string.management_create_lesson_subtitle
                    : R.string.management_create_lesson_in_module_subtitle);
            submitButton.setText(R.string.teacher_add_lesson);
        } else if (kind == ContentFormKind.TEACHER_EXERCISE) {
            icon.setImageResource(R.drawable.role_ic_exam);
            subtitle.setText(R.string.management_create_exercise_subtitle);
            submitButton.setText(editId.isEmpty() ? R.string.management_create_exercise_action
                    : R.string.management_update_exercise_action);
        } else if (kind == ContentFormKind.TEACHER_EXAM) {
            icon.setImageResource(R.drawable.role_ic_exam);
            subtitle.setText(R.string.management_create_exam_subtitle);
            submitButton.setText(editId.isEmpty() ? getString(R.string.create_exam)
                    : "Lưu thay đổi");
        } else {
            icon.setImageResource(R.drawable.role_ic_user_add_filled);
            subtitle.setText(R.string.management_create_user_subtitle);
            submitButton.setText(R.string.management_create_user_action);
        }
    }

    /** Dựng tập trường phù hợp cho khóa học, module, lesson, user hoặc bài tập. */
    private void buildFields() {
        container.removeAllViews();
        if (kind == ContentFormKind.ADMIN_USER) {
            addSection(R.string.management_account_section,
                    R.string.management_account_section_description);
            addInput("username", getString(R.string.management_username),
                    InputType.TYPE_CLASS_TEXT, false);
            addInput("email", getString(R.string.email),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false);
            addInput("phone", getString(R.string.phone), InputType.TYPE_CLASS_PHONE, false);
            addInput("password", getString(R.string.password),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, false);
            addSpinner("role", getString(R.string.management_role),
                    new String[]{"Học sinh", "Giáo viên", "Quản trị viên"},
                    new String[]{"student", "instructor", "admin"});
            return;
        }

        if (kind == ContentFormKind.TEACHER_MODULE) {
            addSection(R.string.management_module_section,
                    R.string.management_module_section_description);
            addInput("title", getString(R.string.management_module_title),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
            addInput("position", getString(R.string.management_content_position),
                    InputType.TYPE_CLASS_NUMBER, false);
            inputs.get("position").setText(String.valueOf(defaultPosition));
            return;
        }

        if (kind == ContentFormKind.TEACHER_LESSON) {
            addSection(R.string.management_lesson_section,
                    R.string.management_lesson_section_description);
            addInput("title", getString(R.string.management_lesson_title),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
            addSpinner("content_type", getString(R.string.management_lesson_content_type),
                    new String[]{"Video", "Văn bản", "Bài luyện tập", "Tài liệu PDF",
                            "Tài liệu Word / ODT"},
                    new String[]{"video", "text", "exercise", "pdf", "document"});
            addInput("position", getString(R.string.management_content_position),
                    InputType.TYPE_CLASS_NUMBER, false);
            inputs.get("position").setText(String.valueOf(defaultPosition));
            addInput("introduction", getString(R.string.management_lesson_introduction),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
            // Bài học chỉ nhận video dạng file upload (không còn nhập link YouTube).
            videoFileRow = addCourseFilePicker(false);
            documentFileRow = addDocumentFilePicker();
            lessonTextRow = addInput("text_content", getString(R.string.management_lesson_text_content),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
            requiresExerciseSwitch = addSwitch(
                    R.string.management_lesson_requires_exercise, false);
            lessonPublishedSwitch = addSwitch(R.string.management_lesson_publish_now, true);
            bindLessonContentType();
            return;
        }

        if (kind == ContentFormKind.TEACHER_COURSE) {
            addSection(R.string.management_course_basic_section,
                    R.string.management_course_basic_description);
            addInput("title", getString(R.string.management_course_title),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
            addGradeSelector();
            return;
        }

        boolean lessonExercise = kind == ContentFormKind.TEACHER_EXERCISE;
        addSection(lessonExercise ? R.string.management_exercise_basic_section
                        : R.string.management_exam_basic_section,
                lessonExercise ? R.string.management_exercise_basic_description
                        : R.string.management_exam_basic_description);
        addInput("title", getString(R.string.management_exam_title),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);
        if (!lessonExercise) {
            addCourseSpinner();
        }

        addSection(R.string.management_exam_settings_section,
                R.string.management_exam_settings_description);
        addInput("duration", getString(R.string.management_exam_duration),
                InputType.TYPE_CLASS_NUMBER, false);
        addInput("pass_score", getString(R.string.management_exam_pass_score),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, false);
        addInput("max_attempts", getString(R.string.management_exam_max_attempts),
                InputType.TYPE_CLASS_NUMBER, false);
        addSpinner("status", getString(R.string.management_exam_status),
                new String[]{"Lưu bản nháp", "Xuất bản ngay"},
                new String[]{"draft", "published"});
        addSpinner("show_answers", getString(R.string.management_exam_show_answers),
                new String[]{"Luôn hiển thị sau khi nộp", "Sau khi hết thời gian",
                        "Sau khi bài thi kết thúc", "Không hiển thị"},
                new String[]{"always", "after_duration", "after_end", "never"});
        shuffleQuestionsSwitch = addSwitch(R.string.management_exam_shuffle_questions, true);
        shuffleChoicesSwitch = addSwitch(R.string.management_exam_shuffle_choices, true);

        addSection(lessonExercise ? R.string.management_exercise_questions_section
                        : R.string.management_exam_questions_section,
                R.string.management_exam_questions_description);
        questionsContainer = new LinearLayout(this);
        questionsContainer.setOrientation(LinearLayout.VERTICAL);
        questionsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(questionsContainer);
        compactQuestionsSummary = new TextView(this);
        compactQuestionsSummary.setTextColor(ContextCompat.getColor(this,
                R.color.role_text_secondary));
        compactQuestionsSummary.setTextSize(12f);
        compactQuestionsSummary.setPadding(dp(14), dp(12), dp(14), dp(12));
        compactQuestionsSummary.setBackgroundResource(R.drawable.role_bg_chart_tabs);
        compactQuestionsSummary.setVisibility(View.GONE);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, dp(10), 0, 0);
        compactQuestionsSummary.setLayoutParams(summaryParams);
        container.addView(compactQuestionsSummary);
        addQuestionButton = new MaterialButton(this);
        addQuestionButton.setText(R.string.management_add_question);
        addQuestionButton.setTextColor(ContextCompat.getColor(this, R.color.role_primary));
        addQuestionButton.setTextSize(13f);
        addQuestionButton.setAllCaps(false);
        addQuestionButton.setCornerRadius(dp(18));
        addQuestionButton.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.role_primary_light)));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        addParams.setMargins(0, dp(12), 0, 0);
        addQuestionButton.setLayoutParams(addParams);
        addQuestionButton.setOnClickListener(view -> addQuestionFromButton());
        container.addView(addQuestionButton);
        aiQuestionButton = new MaterialButton(this);
        aiQuestionButton.setText(R.string.management_generate_questions_ai);
        aiQuestionButton.setTextColor(ContextCompat.getColor(this, R.color.role_primary));
        aiQuestionButton.setTextSize(13f);
        aiQuestionButton.setAllCaps(false);
        aiQuestionButton.setCornerRadius(dp(18));
        aiQuestionButton.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.role_primary_light)));
        LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        aiParams.setMargins(0, dp(8), 0, 0);
        aiQuestionButton.setLayoutParams(aiParams);
        aiQuestionButton.setOnClickListener(view -> launchAiDocumentPicker());
        container.addView(aiQuestionButton);
        addQuestionCard();
    }

    /** Thêm tiêu đề phân nhóm để form dài vẫn dễ theo dõi. */
    private void addSection(int titleRes, int descriptionRes) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(ContextCompat.getColor(this, R.color.role_text_primary));
        title.setTextSize(16f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, container.getChildCount() == 0 ? 0 : dp(20), 0, 0);
        title.setLayoutParams(titleParams);
        container.addView(title);

        TextView description = new TextView(this);
        description.setText(descriptionRes);
        description.setTextColor(ContextCompat.getColor(this, R.color.role_text_secondary));
        description.setTextSize(11.5f);
        description.setPadding(0, dp(3), 0, dp(2));
        container.addView(description);
    }

    /** Tạo TextInput động, lưu theo key và theo dõi thay đổi của người dùng. */
    private View addInput(String key, String hint, int inputType, boolean multiline) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_create_input, container, false);
        TextInputLayout layout = row.findViewById(R.id.layoutManagementCreateInput);
        TextInputEditText input = row.findViewById(R.id.inputManagementCreate);
        layout.setHint(hint);
        input.setInputType(inputType);
        input.setGravity(multiline ? android.view.Gravity.TOP | android.view.Gravity.START
                : android.view.Gravity.CENTER_VERTICAL);
        input.setMinHeight(dp(multiline ? 96 : 56));
        input.setMaxLines(multiline ? 6 : 1);
        int maxLength = "title".equals(key) ? 255
                : "username".equals(key) ? 150
                : "email".equals(key) ? 254
                : "phone".equals(key) ? 15 : 0;
        if (maxLength > 0) {
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        container.addView(row);
        inputs.put(key, input);
        return row;
    }

    /** Tạo vùng chọn ảnh bìa hoặc video khóa học. */
    private View addCourseFilePicker(boolean thumbnail) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_file_picker, container, false);
        ImageView icon = row.findViewById(R.id.imageManagementFileType);
        TextView title = row.findViewById(R.id.textManagementFileTitle);
        TextView fileName = row.findViewById(R.id.textManagementFileName);
        MaterialButton button = row.findViewById(R.id.buttonManagementChooseFile);
        MaterialButton clearButton = row.findViewById(R.id.buttonManagementClearFile);
        if (thumbnail) {
            title.setText(R.string.management_course_thumbnail);
            fileName.setText(R.string.management_course_thumbnail_hint);
            icon.setImageResource(R.drawable.profile_ic_camera);
            button.setOnClickListener(view -> thumbnailPicker.launch(
                    new String[]{"image/jpeg", "image/png", "image/webp"}));
            thumbnailFileName = fileName;
            thumbnailFileButton = button;
            thumbnailClearButton = clearButton;
            clearButton.setOnClickListener(view -> clearSelectedFile(true));
        } else {
            title.setText(kind == ContentFormKind.TEACHER_LESSON
                    ? R.string.management_lesson_video_file
                    : R.string.management_course_video_file);
            fileName.setText(R.string.management_course_video_file_hint);
            icon.setImageResource(R.drawable.role_ic_course);
            button.setOnClickListener(view -> videoPicker.launch(new String[]{"video/*"}));
            videoFileName = fileName;
            videoFileButton = button;
            videoClearButton = clearButton;
            clearButton.setOnClickListener(view -> clearSelectedFile(false));
        }
        container.addView(row);
        return row;
    }

    /** Tạo vùng chọn tài liệu đính kèm cho lesson. */
    private View addDocumentFilePicker() {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_file_picker, container, false);
        ImageView icon = row.findViewById(R.id.imageManagementFileType);
        TextView title = row.findViewById(R.id.textManagementFileTitle);
        documentFileName = row.findViewById(R.id.textManagementFileName);
        documentFileButton = row.findViewById(R.id.buttonManagementChooseFile);
        documentClearButton = row.findViewById(R.id.buttonManagementClearFile);
        title.setText(R.string.management_lesson_document_file);
        documentFileName.setText(R.string.management_lesson_document_file_hint);
        icon.setImageResource(R.drawable.role_ic_course_filled);
        documentFileButton.setOnClickListener(view -> {
            String contentType = spinnerValue("content_type");
            if ("pdf".equals(contentType)) {
                documentPicker.launch(new String[]{"application/pdf"});
            } else {
                documentPicker.launch(new String[]{"application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.oasis.opendocument.text", "application/octet-stream"});
            }
        });
        documentClearButton.setOnClickListener(view -> clearSelectedDocument());
        container.addView(row);
        return row;
    }

    /** Đăng ký Activity Result launcher cho ảnh, video, tài liệu và tệp nguồn AI. */
    private void registerFilePickers() {
        thumbnailPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> handleSelectedFile(uri, true));
        videoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> handleSelectedFile(uri, false));
        documentPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::handleSelectedDocument);
        aiDocumentPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::handleAiDocument);
    }

    /** Theo dõi loại nội dung lesson để thay đổi các trường cần nhập. */
    private void bindLessonContentType() {
        SpinnerBinding binding = spinners.get("content_type");
        if (binding == null) return;
        binding.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateLessonContentFields();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        updateLessonContentFields();
    }

    /** Hiện/ẩn trường text, URL, video và tài liệu theo content type. */
    private void updateLessonContentFields() {
        String contentType = spinnerValue("content_type");
        boolean video = "video".equals(contentType);
        boolean document = "pdf".equals(contentType) || "document".equals(contentType);
        if (videoFileRow != null) {
            videoFileRow.setVisibility(video ? View.VISIBLE : View.GONE);
        }
        if (documentFileRow != null) {
            documentFileRow.setVisibility(document ? View.VISIBLE : View.GONE);
        }
        if (lessonTextRow != null) {
            lessonTextRow.setVisibility("text".equals(contentType) ? View.VISIBLE : View.GONE);
        }
        if (requiresExerciseSwitch != null) {
            boolean exercise = "exercise".equals(contentType);
            if (exercise) requiresExerciseSwitch.setChecked(true);
        }
    }

    /** Kiểm tra ảnh/video được chọn, kích thước và MIME trước khi lưu URI. */
    private void handleSelectedFile(Uri uri, boolean thumbnail) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // URI vẫn đọc được trong Activity hiện tại dù provider không cho lưu quyền lâu dài.
        }
        String mimeType = safe(getContentResolver().getType(uri)).toLowerCase(Locale.ROOT);
        String name = fileDisplayName(uri);
        long size = fileSize(uri);
        if (thumbnail && !isImageFile(mimeType, name)) {
            showStatus(getString(R.string.management_invalid_image_file));
            return;
        }
        if (!thumbnail && !isVideoFile(mimeType, name)) {
            showStatus(getString(R.string.management_invalid_video_file));
            return;
        }
        if (thumbnail && size > 5L * 1024L * 1024L) {
            showStatus(getString(R.string.management_thumbnail_too_large));
            return;
        }
        if (!thumbnail && size > 500L * 1024L * 1024L) {
            showStatus(getString(R.string.management_video_too_large));
            return;
        }
        statusText.setVisibility(View.GONE);
        String label = name + (size > 0 ? " · " + readableSize(size) : "");
        if (thumbnail) {
            selectedThumbnailUri = uri;
            thumbnailFileName.setText(label);
            thumbnailFileButton.setText(R.string.management_replace_file);
            thumbnailClearButton.setVisibility(View.VISIBLE);
        } else {
            selectedVideoUri = uri;
            videoFileName.setText(label);
            videoFileButton.setText(R.string.management_replace_file);
            videoClearButton.setVisibility(View.VISIBLE);
        }
    }

    /** Kiểm tra tài liệu được chọn rồi cập nhật tên và trạng thái form. */
    private void handleSelectedDocument(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // URI vẫn đọc được trong vòng đời Activity hiện tại.
        }
        String name = fileDisplayName(uri);
        String lowerName = name.toLowerCase(Locale.ROOT);
        String mimeType = safe(getContentResolver().getType(uri)).toLowerCase(Locale.ROOT);
        String contentType = spinnerValue("content_type");
        boolean valid = "pdf".equals(contentType)
                ? mimeType.equals("application/pdf") || lowerName.endsWith(".pdf")
                : lowerName.endsWith(".doc") || lowerName.endsWith(".docx")
                        || lowerName.endsWith(".odt")
                        || mimeType.equals("application/msword")
                        || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        || mimeType.equals("application/vnd.oasis.opendocument.text");
        if (!valid) {
            showStatus(getString("pdf".equals(contentType)
                    ? R.string.management_invalid_pdf_file
                    : R.string.management_invalid_document_file));
            return;
        }
        long size = fileSize(uri);
        if (size > 50L * 1024L * 1024L) {
            showStatus(getString(R.string.management_document_too_large));
            return;
        }
        selectedDocumentUri = uri;
        statusText.setVisibility(View.GONE);
        documentFileName.setText(size > 0
                ? getString(R.string.management_file_with_size, name, readableSize(size))
                : name);
        documentFileButton.setText(R.string.management_replace_file);
        documentClearButton.setVisibility(View.VISIBLE);
    }

    /** Bỏ ảnh/video đã chọn và khôi phục vùng file picker. */
    private void clearSelectedFile(boolean thumbnail) {
        if (thumbnail) {
            selectedThumbnailUri = null;
            thumbnailFileName.setText(R.string.management_course_thumbnail_hint);
            thumbnailFileButton.setText(R.string.management_choose_file);
            thumbnailClearButton.setVisibility(View.GONE);
        } else {
            selectedVideoUri = null;
            videoFileName.setText(R.string.management_course_video_file_hint);
            videoFileButton.setText(R.string.management_choose_file);
            videoClearButton.setVisibility(View.GONE);
        }
    }

    /** Bỏ tài liệu đã chọn khỏi payload sắp gửi. */
    private void clearSelectedDocument() {
        selectedDocumentUri = null;
        if (documentFileName != null) {
            documentFileName.setText(R.string.management_lesson_document_file_hint);
        }
        if (documentFileButton != null) {
            documentFileButton.setText(R.string.management_choose_file);
        }
        if (documentClearButton != null) documentClearButton.setVisibility(View.GONE);
    }

    private String fileDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return safe(cursor.getString(column));
            }
        } catch (Exception ignored) {
            // Nếu metadata không có tên thì dùng path của URI.
        }
        String path = safe(uri.getLastPathSegment());
        return path.isEmpty() ? "Tệp đã chọn" : path;
    }

    private long fileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column);
            }
        } catch (Exception ignored) {
            // Kích thước chưa xác định sẽ được backend kiểm tra khi nhận luồng dữ liệu.
        }
        return -1;
    }

    private boolean isImageFile(String mimeType, String name) {
        String lowerName = safe(name).toLowerCase(Locale.ROOT);
        return mimeType.startsWith("image/") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")
                || lowerName.endsWith(".webp");
    }

    private boolean isVideoFile(String mimeType, String name) {
        String lowerName = safe(name).toLowerCase(Locale.ROOT);
        return mimeType.startsWith("video/") || lowerName.endsWith(".mp4")
                || lowerName.endsWith(".webm") || lowerName.endsWith(".mov")
                || lowerName.endsWith(".m4v");
    }

    private String readableSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024d);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024d * 1024d));
    }

    /** Tạo Spinner động và ánh xạ nhãn hiển thị sang giá trị API. */
    private void addSpinner(String key, String label, String[] labels, String[] values) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_create_spinner, container, false);
        ((TextView) row.findViewById(R.id.textManagementCreateSpinnerLabel)).setText(label);
        Spinner spinner = row.findViewById(R.id.spinnerManagementCreate);
        spinner.setAdapter(spinnerAdapter(labels));
        container.addView(row);
        spinners.put(key, new SpinnerBinding(spinner, values));
    }

    /** Tạo nhóm nút chọn khối lớp cho khóa học. */
    private void addGradeSelector() {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_grade_selector, container, false);
        gradeButtonGroup = row.findViewById(R.id.groupManagementCourseGrade);
        gradeButtons = new MaterialButton[]{row.findViewById(R.id.chipManagementGrade1),
                row.findViewById(R.id.chipManagementGrade2),
                row.findViewById(R.id.chipManagementGrade3),
                row.findViewById(R.id.chipManagementGrade4),
                row.findViewById(R.id.chipManagementGrade5)};
        for (int index = 0; index < gradeButtons.length; index++) {
            final int selected = index;
            gradeButtons[index].setOnClickListener(view -> selectGrade(selected));
        }
        // Luồng tạo nội dung của SmartKid chỉ hỗ trợ từ lớp 3 trở lên.
        selectGrade(2);
        container.addView(row);
    }

    private void selectGrade(int selected) {
        if (gradeButtons == null || gradeButtons.length == 0) return;
        int safeIndex = Math.max(0, Math.min(selected, gradeButtons.length - 1));
        selectedGrade = String.valueOf(safeIndex + 1);
        for (int index = 0; index < gradeButtons.length; index++) {
            gradeButtons[index].setChecked(index == safeIndex);
        }
    }

    /** Tạo Spinner chọn khóa học liên kết cho bài kiểm tra hoặc nội dung. */
    private void addCourseSpinner() {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_create_spinner, container, false);
        ((TextView) row.findViewById(R.id.textManagementCreateSpinnerLabel))
                .setText(R.string.course_applied_label);
        courseSpinner = row.findViewById(R.id.spinnerManagementCreate);
        courseSpinner.setAdapter(spinnerAdapter(
                new String[]{getString(R.string.management_loading_courses)}));
        courseSpinner.setEnabled(false);
        container.addView(row);
    }

    private SwitchMaterial addSwitch(int textRes, boolean checked) {
        SwitchMaterial control = new SwitchMaterial(this);
        control.setText(textRes);
        control.setTextColor(ContextCompat.getColor(this, R.color.role_text_primary));
        control.setTextSize(13f);
        control.setChecked(checked);
        control.setGravity(android.view.Gravity.CENTER_VERTICAL);
        control.setPadding(dp(14), 0, dp(10), 0);
        control.setBackgroundResource(R.drawable.role_bg_chart_tabs);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        params.setMargins(0, dp(10), 0, 0);
        control.setLayoutParams(params);
        container.addView(control);
        return control;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    /** Thêm một thẻ câu hỏi mới với loại mặc định. */
    private QuestionFields addQuestionCard() {
        return addQuestionCard(true, "mcq");
    }

    /** Kiểm tra giới hạn rồi thêm câu hỏi khi người dùng bấm nút. */
    private void addQuestionFromButton() {
        if (AiQuestionPolicy.remainingCapacity(totalQuestionCount()) == 0) {
            showShortMessage(getString(R.string.management_question_limit));
            return;
        }
        if (questions.size() >= QuestionRenderPolicy.MAX_EXPANDED) {
            showShortMessage(getString(R.string.management_manual_question_memory_limit));
            return;
        }
        addQuestionCard();
    }

    private QuestionFields addQuestionCard(boolean updateNumbers) {
        return addQuestionCard(updateNumbers, "mcq");
    }

    private QuestionFields addQuestionCard(boolean updateNumbers, String initialType) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.management_item_exam_question, questionsContainer, false);
        QuestionFields fields = new QuestionFields(row, initialType);
        fields.delete.setOnClickListener(view -> removeQuestion(fields));
        questions.add(fields);
        questionsContainer.addView(row);
        if (updateNumbers) updateQuestionNumbers();
        return fields;
    }

    /** Đảm bảo chỉ một lựa chọn được đánh dấu đúng trong câu single-choice. */
    private void selectCorrectChoice(QuestionFields fields, ChoiceFields selected) {
        for (ChoiceFields choice : fields.choices) {
            choice.correct.setChecked(choice == selected);
        }
    }

    /** Xóa thẻ câu hỏi và đánh số lại các câu còn lại. */
    private void removeQuestion(QuestionFields fields) {
        if (totalQuestionCount() <= 1) {
            showShortMessage(getString(R.string.management_exam_requires_question));
            return;
        }
        questions.remove(fields);
        questionsContainer.removeView(fields.root);
        if (!compactQuestions.isEmpty()
                && questions.size() < QuestionRenderPolicy.MAX_EXPANDED) {
            JSONObject promoted = compactQuestions.remove(0);
            JSONObject meta = promoted.optJSONObject("meta");
            String type = meta == null ? "mcq" : safe(meta.optString("type", "mcq"));
            populateQuestion(addQuestionCard(false, type), promoted);
        }
        updateQuestionNumbers();
        updateCompactQuestionsSummary();
    }

    /** Cập nhật số thứ tự và nút xóa sau thay đổi danh sách câu hỏi. */
    private void updateQuestionNumbers() {
        for (int index = 0; index < questions.size(); index++) {
            questions.get(index).number.setText(
                    getString(R.string.management_question_number, index + 1));
        }
    }

    private int totalQuestionCount() {
        return questions.size() + compactQuestions.size();
    }

    private void updateCompactQuestionsSummary() {
        if (compactQuestionsSummary == null) return;
        int hidden = compactQuestions.size();
        if (hidden == 0) {
            compactQuestionsSummary.setVisibility(View.GONE);
            return;
        }
        compactQuestionsSummary.setText(getResources().getQuantityString(
                R.plurals.management_compact_questions_summary, hidden, hidden));
        compactQuestionsSummary.setVisibility(View.VISIBLE);
    }

    /** Mở trình chọn tài liệu nguồn để yêu cầu backend sinh câu hỏi. */
    private void launchAiDocumentPicker() {
        if (AiQuestionPolicy.remainingCapacity(totalQuestionCount()) == 0) {
            showShortMessage(getString(R.string.management_question_limit));
            return;
        }
        try {
            aiDocumentPicker.launch(new String[]{"application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain"});
        } catch (Exception exception) {
            AppLogger.error(this, "ContentFormActivity",
                    "Không thể mở bộ chọn tài liệu AI", exception);
            showStatus(getString(R.string.management_ai_error));
        }
    }

    /** Kiểm tra tài liệu nguồn AI trước khi hỏi số lượng câu cần tạo. */
    private void handleAiDocument(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // URI vẫn đọc được trong vòng đời Activity hiện tại.
        }
        String name = fileDisplayName(uri);
        String lowerName = name.toLowerCase(Locale.ROOT);
        String mimeType = safe(getContentResolver().getType(uri)).toLowerCase(Locale.ROOT);
        boolean valid = lowerName.endsWith(".pdf") || lowerName.endsWith(".docx")
                || lowerName.endsWith(".txt")
                || mimeType.equals("application/pdf")
                || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || mimeType.startsWith("text/");
        if (!valid) {
            showStatus(getString(R.string.management_ai_file_invalid));
            return;
        }
        long size = fileSize(uri);
        if (!AiQuestionPolicy.acceptsDocumentSize(size)) {
            showStatus(getString(R.string.management_ai_file_too_large));
            return;
        }
        showAiQuestionCountDialog(uri);
    }

    /** Cho người dùng chọn số câu hỏi muốn sinh từ tài liệu. */
    private void showAiQuestionCountDialog(Uri uri) {
        int[] counts = AiQuestionPolicy.allowedCounts();
        String[] labels = new String[counts.length];
        for (int index = 0; index < counts.length; index++) {
            labels[index] = getResources().getQuantityString(
                    R.plurals.management_ai_count_option, counts[index], counts[index]);
        }
        int[] selected = {1};
        new AlertDialog.Builder(this)
                .setTitle(R.string.management_ai_count_title)
                .setSingleChoiceItems(labels, selected[0], (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.management_ai_generate, (dialog, which) ->
                        requestAiQuestionsFromFile(uri, counts[selected[0]]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Upload tài liệu đến endpoint AI và nhận danh sách câu hỏi đã tạo. */
    private void requestAiQuestionsFromFile(Uri uri, int requestedCount) {
        if (!AiQuestionPolicy.isAllowedCount(requestedCount)) {
            showStatus(getString(R.string.management_ai_error));
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("count", requestedCount);
            body.put("level", selectedAiLevel());
            List<MultipartFilePart> files = new ArrayList<>();
            files.add(new MultipartFilePart("file", uri));
            setLoading(true);
            showStatus(getResources().getQuantityString(
                    R.plurals.management_ai_generating_count,
                    requestedCount,
                    requestedCount));
            repository.multipartAction(Request.Method.POST,
                    "activities/ai/generate-questions/", body, files,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            try {
                                JSONArray generated = extractAiQuestions(data);
                                int added = applyGeneratedQuestions(generated);
                                int expected = SafeJson.integer(data, requestedCount,
                                        "requestedCount", "requested_count");
                                setLoading(false);
                                if (added == 0) {
                                    showStatus(getString(R.string.management_ai_empty));
                                } else if (added < expected) {
                                    showStatus(getResources().getQuantityString(
                                            R.plurals.management_ai_partial,
                                            added,
                                            added,
                                            expected));
                                } else {
                                    statusText.setVisibility(View.GONE);
                                    showShortMessage(getResources().getQuantityString(
                                            R.plurals.management_ai_questions_added,
                                            added, added));
                                }
                            } catch (Exception exception) {
                                AppLogger.error(ContentFormActivity.this,
                                        "ContentFormActivity",
                                        "Không thể đọc câu hỏi AI", exception);
                                setLoading(false);
                                showStatus(getString(R.string.management_ai_invalid_response));
                            }
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showStatus(error == null ? getString(R.string.management_ai_error)
                                    : error.getMessage());
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(this, "ContentFormActivity",
                    "Không thể tạo yêu cầu câu hỏi AI", exception);
            setLoading(false);
            showStatus(getString(R.string.management_ai_error));
        }
    }

    private String selectedAiLevel() {
        int position = courseSpinner == null ? -1 : courseSpinner.getSelectedItemPosition();
        if (position >= 0 && position < courseOptions.size()) {
            String grade = safe(courseOptions.get(position).getSource().optString("grade", ""));
            if (!grade.isEmpty()) return "Lớp " + grade;
        }
        return "Tiểu học";
    }

    /** Tìm mảng câu hỏi trong các cấu trúc response AI được backend hỗ trợ. */
    private JSONArray extractAiQuestions(JSONObject data) throws Exception {
        if (data == null) return new JSONArray();
        JSONArray direct = data.optJSONArray("questions");
        if (direct != null) return direct;
        String raw = safe(data.optString("text", ""));
        if (raw.isEmpty()) return new JSONArray();
        if (raw.startsWith("```")) {
            int firstLine = raw.indexOf('\n');
            raw = firstLine >= 0 ? raw.substring(firstLine + 1) : raw.substring(3);
            int fence = raw.lastIndexOf("```");
            if (fence >= 0) raw = raw.substring(0, fence);
            raw = raw.trim();
        }
        int objectStart = raw.indexOf('{');
        int arrayStart = raw.indexOf('[');
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            int objectEnd = raw.lastIndexOf('}');
            if (objectEnd >= objectStart) {
                JSONArray nested = new JSONObject(raw.substring(objectStart, objectEnd + 1))
                        .optJSONArray("questions");
                return nested == null ? new JSONArray() : nested;
            }
        }
        if (arrayStart >= 0) {
            int arrayEnd = raw.lastIndexOf(']');
            if (arrayEnd >= arrayStart) {
                return new JSONArray(raw.substring(arrayStart, arrayEnd + 1));
            }
        }
        return new JSONArray();
    }

    /** Chuẩn hóa và thêm các câu hỏi AI hợp lệ vào form hiện tại. */
    private int applyGeneratedQuestions(JSONArray generated) {
        if (generated == null || generated.length() == 0) return 0;
        if (totalQuestionCount() == 1 && questions.size() == 1
                && text(questions.get(0).prompt).isEmpty()) {
            questionsContainer.removeView(questions.get(0).root);
            questions.clear();
        }
        int added = 0;
        int generatedCount = Math.min(
                AiQuestionPolicy.clampGeneratedCount(generated.length()),
                AiQuestionPolicy.remainingCapacity(totalQuestionCount())
        );
        for (int index = 0; index < generatedCount; index++) {
            JSONObject source = generated.optJSONObject(index);
            JSONObject normalized = normalizeGeneratedQuestion(source);
            if (normalized == null) continue;
            if (questions.size() < QuestionRenderPolicy.MAX_EXPANDED) {
                populateQuestion(addQuestionCard(false, "mcq"), normalized);
            } else {
                compactQuestions.add(normalized);
            }
            added++;
        }
        if (questions.isEmpty()) addQuestionCard();
        updateQuestionNumbers();
        updateCompactQuestionsSummary();
        return added;
    }

    /** Chuyển câu hỏi AI về schema thống nhất mà form và API tạo bài dùng. */
    private JSONObject normalizeGeneratedQuestion(JSONObject source) {
        if (source == null) return null;
        try {
            String prompt = safe(source.optString("text",
                    source.optString("prompt", "")));
            JSONArray options = source.optJSONArray("choices");
            if (options == null) options = source.optJSONArray("options");
            int correctIndex = source.optInt("correct_index", -1);
            String generatedType = safe(source.optString("type", ""))
                    .toLowerCase(Locale.ROOT);
            if (options == null && "boolean".equals(generatedType)) {
                options = new JSONArray()
                        .put(getString(R.string.management_boolean_true))
                        .put(getString(R.string.management_boolean_false));
                correctIndex = source.optBoolean("correct_answer", false) ? 0 : 1;
            }
            if (prompt.isEmpty() || options == null || options.length() < 2) return null;
            JSONArray correctIndices = source.optJSONArray("correct_indices");
            if (correctIndices != null && correctIndices.length() > 0) {
                correctIndex = correctIndices.optInt(0, correctIndex);
            }
            JSONArray choices = new JSONArray();
            int optionCount = Math.min(options.length(), 6);
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                Object rawOption = options.opt(optionIndex);
                if (rawOption == null || rawOption == JSONObject.NULL) continue;
                JSONObject optionObject = rawOption instanceof JSONObject
                        ? (JSONObject) rawOption : null;
                String optionText = optionObject == null ? safe(String.valueOf(rawOption))
                        : safe(optionObject.optString("text", ""));
                if (optionText.isEmpty()) continue;
                if (optionObject != null && optionObject.optBoolean("is_correct", false)) {
                    correctIndex = choices.length();
                }
                choices.put(new JSONObject()
                        .put("text", optionText)
                        .put("position", choices.length())
                        .put("is_correct", false));
            }
            if (choices.length() < 2) return null;
            int safeCorrect = correctIndex < 0 || correctIndex >= choices.length()
                    ? 0 : correctIndex;
            choices.getJSONObject(safeCorrect).put("is_correct", true);
            double points = source.optDouble("points", source.optDouble("score", 1d));
            return new JSONObject()
                    .put("prompt", prompt)
                    .put("meta", new JSONObject()
                            .put("type", "mcq")
                            .put("points", points <= 0 ? 1d : points))
                    .put("choices", choices);
        } catch (Exception exception) {
            AppLogger.error(this, "ContentFormActivity",
                    "Không thể chuẩn hóa câu hỏi AI", exception);
            return null;
        }
    }

    /** Tải danh sách khóa học để điền vào Spinner liên kết. */
    private void loadCourseOptions() {
        setLoading(true);
        showStatus(getString(R.string.management_loading_courses));
        repository.load("content/courses/?page=1&pageSize=100",
                new ApiCallback<List<FeatureItem>>() {
                    @Override
                    public void onSuccess(List<FeatureItem> data) {
                        if (!isUsable()) return;
                        courseOptions.clear();
                        if (data != null) {
                            for (FeatureItem item : data) {
                                if (item != null && !item.getId().isEmpty()) courseOptions.add(item);
                            }
                        }
                        List<String> labels = new ArrayList<>();
                        int preferredCourse = 0;
                        for (int index = 0; index < courseOptions.size(); index++) {
                            FeatureItem item = courseOptions.get(index);
                            boolean published = SafeJson.bool(
                                    item.getSource(), false, "published");
                            labels.add(item.getTitle() + (published
                                    ? " • Đã xuất bản" : " • Bản nháp"));
                            if (published && !SafeJson.bool(
                                    courseOptions.get(preferredCourse).getSource(),
                                    false, "published")) {
                                preferredCourse = index;
                            }
                        }
                        courseSpinner.setAdapter(spinnerAdapter(labels.toArray(new String[0])));
                        setLoading(false);
                        if (courseOptions.isEmpty()) {
                            submitButton.setEnabled(false);
                            showStatus(getString(R.string.management_exam_no_courses));
                        } else if (!editId.isEmpty()) {
                            loadExerciseForEdit();
                        } else {
                            courseSpinner.setSelection(preferredCourse);
                            courseSpinner.setEnabled(true);
                            statusText.setVisibility(View.GONE);
                            rememberFormState();
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        submitButton.setEnabled(false);
                        showStatus(error == null ? getString(R.string.management_courses_load_error)
                                : error.getMessage());
                    }
                });
    }

    /** Tải bài kiểm tra hiện có khi form chạy ở chế độ chỉnh sửa. */
    private void loadExerciseForEdit() {
        setLoading(true);
        showStatus(getString(R.string.management_loading_exercise));
        repository.loadObject("activities/exercises/" + editId + "/",
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        try {
                            populateExercise(data == null ? new JSONObject() : data);
                            setLoading(false);
                            statusText.setVisibility(View.GONE);
                            rememberFormState();
                        } catch (Exception exception) {
                            AppLogger.error(ContentFormActivity.this,
                                    "ContentFormActivity",
                                    "Không thể điền dữ liệu bài luyện tập", exception);
                            setLoading(false);
                            submitButton.setEnabled(false);
                            showStatus(getString(R.string.management_exercise_invalid_data));
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setLoading(false);
                        submitButton.setEnabled(false);
                        showStatus(error == null
                                ? getString(R.string.management_exercise_load_error)
                                : error.getMessage());
                    }
                });
    }

    /** Điền thiết lập và câu hỏi của bài kiểm tra vào form động. */
    private void populateExercise(JSONObject exercise) {
        Object lessonValue = exercise.opt("lesson");
        String exerciseLessonId = "";
        if (lessonValue instanceof JSONObject) {
            exerciseLessonId = safe(((JSONObject) lessonValue).optString("id",
                    ((JSONObject) lessonValue).optString("uuid", "")));
        } else if (lessonValue != null && lessonValue != JSONObject.NULL) {
            exerciseLessonId = safe(String.valueOf(lessonValue));
        }
        if (kind == ContentFormKind.TEACHER_EXERCISE
                && (parentId.isEmpty() || !parentId.equals(exerciseLessonId))) {
            throw new IllegalArgumentException("Bài tập không thuộc bài học đang mở");
        }
        if (kind == ContentFormKind.TEACHER_EXAM && !exerciseLessonId.isEmpty()) {
            throw new IllegalArgumentException("Dữ liệu không phải bài kiểm tra độc lập");
        }
        inputs.get("title").setText(safe(exercise.optString("title", "")));
        boolean published = exercise.optBoolean("published", false);
        setSpinnerValue("status", published ? "published" : "draft");
        JSONObject settings = exercise.optJSONObject("settings");
        if (settings == null) settings = new JSONObject();
        if (kind == ContentFormKind.TEACHER_EXAM) {
            selectCourse(SafeJson.string(settings, "", "course_id"));
        }
        try {
            existingExerciseSettings = new JSONObject(settings.toString());
        } catch (Exception ignored) {
            existingExerciseSettings = new JSONObject();
        }
        Object duration = settings.opt("duration_seconds");
        if (duration instanceof Number) {
            inputs.get("duration").setText(String.valueOf(
                    Math.max(1, ((Number) duration).intValue() / 60)));
        }
        Object passScore = settings.opt("pass_score");
        if (passScore instanceof Number) {
            inputs.get("pass_score").setText(String.valueOf(
                    ((Number) passScore).doubleValue()));
        }
        Object maxAttempts = settings.opt("max_attempts");
        if (maxAttempts instanceof Number) {
            inputs.get("max_attempts").setText(String.valueOf(
                    ((Number) maxAttempts).intValue()));
        }
        shuffleQuestionsSwitch.setChecked(settings.optBoolean("shuffle_questions", true));
        shuffleChoicesSwitch.setChecked(settings.optBoolean("shuffle_choices", true));
        setSpinnerValue("show_answers", settings.optString("show_answers", "always"));

        questions.clear();
        compactQuestions.clear();
        questionsContainer.removeAllViews();
        JSONArray sourceQuestions = exercise.optJSONArray("questions");
        if (sourceQuestions != null) {
            for (int index = 0; index < sourceQuestions.length(); index++) {
                JSONObject source = sourceQuestions.optJSONObject(index);
                if (source != null) {
                    if (questions.size() < QuestionRenderPolicy.MAX_EXPANDED) {
                        JSONObject meta = source.optJSONObject("meta");
                        String type = meta == null ? "mcq"
                                : safe(meta.optString("type", "mcq"));
                        populateQuestion(addQuestionCard(false, type), source);
                    } else {
                        try {
                            compactQuestions.add(new JSONObject(source.toString()));
                        } catch (Exception ignored) {
                            // Bỏ qua câu bị hỏng thay vì làm sập toàn bộ trình chỉnh sửa.
                        }
                    }
                }
            }
        }
        if (questions.isEmpty()) addQuestionCard();
        updateQuestionNumbers();
        updateCompactQuestionsSummary();
    }

    /** Điền một câu hỏi JSON vào QuestionFields tương ứng. */
    private void populateQuestion(QuestionFields fields, JSONObject source) {
        fields.existingId = safe(source.optString("id", ""));
        fields.prompt.setText(safe(source.optString("prompt",
                source.optString("text", ""))));
        JSONObject meta = source.optJSONObject("meta");
        if (meta == null) meta = new JSONObject();
        try {
            fields.existingMeta = new JSONObject(meta.toString());
        } catch (Exception ignored) {
            fields.existingMeta = new JSONObject();
        }
        String type = safe(meta.optString("type", "mcq"));
        fields.setQuestionType(type);
        double points = meta.optDouble("points", meta.optDouble("score", 1d));
        fields.score.setText(String.valueOf(points));
        if ("mcq".equals(type)) {
            JSONArray choicesData = source.optJSONArray("choices");
            if (choicesData == null) choicesData = new JSONArray();
            while (fields.choices.size() < Math.min(choicesData.length(), 6)) {
                fields.addChoice();
            }
            ChoiceFields selected = null;
            for (ChoiceFields choice : fields.choices) {
                choice.existingId = "";
                choice.input.setText("");
                choice.correct.setChecked(false);
            }
            for (int index = 0; index < choicesData.length()
                    && index < fields.choices.size(); index++) {
                JSONObject choiceData = choicesData.optJSONObject(index);
                if (choiceData == null) continue;
                ChoiceFields choice = fields.choices.get(index);
                choice.existingId = safe(choiceData.optString("id", ""));
                choice.input.setText(safe(choiceData.optString("text", "")));
                if (choiceData.optBoolean("is_correct", false)) selected = choice;
            }
            selectCorrectChoice(fields, selected == null ? fields.choices.get(0) : selected);
        } else if ("short_answer".equals(type)) {
            JSONArray accepted = meta.optJSONArray("accepted_answers");
            fields.acceptedAnswers.setText(joinJsonStrings(accepted));
        } else if ("matching".equals(type)) {
            JSONArray pairs = meta.optJSONArray("pairs");
            if (pairs == null) pairs = new JSONArray();
            JSONArray choicesData = source.optJSONArray("choices");
            if (choicesData == null) choicesData = new JSONArray();
            while (fields.matchingPairs.size() < Math.min(pairs.length(), 10)) {
                fields.addMatchingPair();
            }
            for (MatchingPairFields pair : fields.matchingPairs) {
                pair.leftChoiceId = "";
                pair.rightChoiceId = "";
                pair.left.setText("");
                pair.right.setText("");
            }
            for (int index = 0; index < pairs.length()
                    && index < fields.matchingPairs.size(); index++) {
                JSONObject pairData = pairs.optJSONObject(index);
                if (pairData == null) continue;
                fields.matchingPairs.get(index).left.setText(
                        safe(pairData.optString("left", "")));
                fields.matchingPairs.get(index).right.setText(
                        safe(pairData.optString("right", "")));
                JSONObject leftChoice = choicesData.optJSONObject(index * 2);
                JSONObject rightChoice = choicesData.optJSONObject(index * 2 + 1);
                fields.matchingPairs.get(index).leftChoiceId = leftChoice == null
                        ? "" : safe(leftChoice.optString("id", ""));
                fields.matchingPairs.get(index).rightChoiceId = rightChoice == null
                        ? "" : safe(rightChoice.optString("id", ""));
            }
        }
    }

    private String joinJsonStrings(JSONArray values) {
        if (values == null || values.length() == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length(); index++) {
            String value = safe(values.optString(index, ""));
            if (value.isEmpty()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private void setSpinnerValue(String key, String value) {
        SpinnerBinding binding = spinners.get(key);
        if (binding != null) binding.selectValue(value);
    }

    /** Kiểm tra form, tạo payload và chọn request JSON hoặc multipart phù hợp. */
    private void submitSafely() {
        try {
            JSONObject body = buildBody();
            if (body == null) return;
            String endpoint;
            if (kind == ContentFormKind.ADMIN_USER) endpoint = "account/admin/users/";
            else if (kind == ContentFormKind.TEACHER_COURSE) endpoint = "content/courses/";
            else if (kind == ContentFormKind.TEACHER_MODULE) {
                endpoint = "content/courses/" + parentId + "/modules/";
            } else if (kind == ContentFormKind.TEACHER_LESSON) {
                endpoint = "content/modules/" + parentId + "/lessons/";
            }
            else {
                boolean editingExercise = (kind == ContentFormKind.TEACHER_EXERCISE
                        || kind == ContentFormKind.TEACHER_EXAM) && !editId.isEmpty();
                endpoint = editingExercise ? "activities/exercises/" + editId + "/"
                        : "activities/exercises/";
            }
            boolean editingExercise = (kind == ContentFormKind.TEACHER_EXERCISE
                    || kind == ContentFormKind.TEACHER_EXAM) && !editId.isEmpty();
            int requestMethod = editingExercise ? Request.Method.PATCH : Request.Method.POST;
            setLoading(true);
            statusText.setVisibility(View.GONE);
            ApiCallback<JSONObject> callback = new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            if (!isUsable()) return;
                            setLoading(false);
                            showShortMessage(successMessage());
                            setResult(RESULT_OK);
                            formSubmitted = true;
                            // Điều hướng tiếp được giao cho lớp con của role để bộ máy form
                            // trung lập này không phải import màn hình feature cụ thể.
                            onContentCreated(data);
                            finish();
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable()) return;
                            setLoading(false);
                            if (error != null && error.isSessionExpired()) {
                                handleApiError(error);
                            } else {
                                showStatus(error == null ? getString(R.string.unknown_error)
                                        : error.getMessage());
                            }
                        }
                    };
            List<MultipartFilePart> files = multipartFiles();
            if (kind == ContentFormKind.TEACHER_LESSON && !files.isEmpty()) {
                createLessonThenUpload(endpoint, body, files, callback);
            } else if (!files.isEmpty()) {
                repository.multipartAction(requestMethod, endpoint, body, files, callback);
            } else {
                repository.action(requestMethod, endpoint, body, callback);
            }
        } catch (Exception exception) {
            AppLogger.error(this, "ContentFormActivity", "Không thể gửi biểu mẫu", exception);
            setLoading(false);
            showStatus(getString(R.string.management_create_prepare_error));
        }
    }

    /** Tạo lesson trước rồi upload file vào endpoint lesson vừa được cấp ID. */
    private void createLessonThenUpload(String endpoint, JSONObject body,
                                        List<MultipartFilePart> files,
                                        ApiCallback<JSONObject> finalCallback) {
        repository.action(Request.Method.POST, endpoint, body, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject created) {
                if (!isUsable()) return;
                String lessonId = created == null ? "" : safe(created.optString("id", ""));
                if (lessonId.isEmpty()) {
                    setLoading(false);
                    showStatus(getString(R.string.management_lesson_missing_created_id));
                    return;
                }
                repository.multipartAction(Request.Method.PATCH,
                        "content/lessons/" + lessonId + "/", new JSONObject(), files,
                        new ApiCallback<JSONObject>() {
                            @Override
                            public void onSuccess(JSONObject uploaded) {
                                finalCallback.onSuccess(uploaded == null ? created : uploaded);
                            }

                            @Override
                            public void onError(ApiError error) {
                                if (!isUsable()) return;
                                setLoading(false);
                                String reason = error == null ? getString(R.string.unknown_error)
                                        : error.getMessage();
                                showStatus(getString(R.string.management_lesson_upload_partial,
                                        reason));
                            }
                        });
            }

            @Override
            public void onError(ApiError error) {
                finalCallback.onError(error);
            }
        });
    }

    /** Đọc toàn bộ trường động và tạo request body theo ContentFormKind. */
    private JSONObject buildBody() throws Exception {
        JSONObject body = new JSONObject();
        if (kind == ContentFormKind.ADMIN_USER) {
            String username = required("username");
            String email = required("email");
            String password = required("password");
            if (username == null || email == null || password == null) return null;
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                inputs.get("email").setError(getString(R.string.invalid_email));
                return null;
            }
            if (password.length() < 8) {
                inputs.get("password").setError(getString(R.string.password_min_length));
                return null;
            }
            body.put("username", username);
            body.put("email", email);
            body.put("password", password);
            String phone = value("phone");
            if (!phone.isEmpty()) body.put("phone", phone);
            body.put("role", spinnerValue("role"));
            return body;
        }

        if (kind == ContentFormKind.TEACHER_COURSE) {
            String title = required("title");
            if (title == null) return null;
            body.put("title", title);
            body.put("grade", selectedGrade);
            return body;
        }

        if (kind == ContentFormKind.TEACHER_MODULE) {
            if (parentId.isEmpty()) {
                showStatus(getString(R.string.management_missing_parent));
                return null;
            }
            String title = required("title");
            if (title == null) return null;
            body.put("title", title);
            body.put("course", parentId);
            body.put("position", (int) decimal("position", 0, 0, 10000));
            return body;
        }

        if (kind == ContentFormKind.TEACHER_LESSON) {
            if (parentId.isEmpty()) {
                showStatus(getString(R.string.management_missing_parent));
                return null;
            }
            String title = required("title");
            if (title == null) return null;
            body.put("title", title);
            body.put("module", parentId);
            String contentType = spinnerValue("content_type");
            body.put("content_type", contentType);
            body.put("position", (int) decimal("position", 0, 0, 10000));
            body.put("introduction", value("introduction"));
            String textContent = value("text_content");
            if ("video".equals(contentType) && selectedVideoUri == null) {
                showStatus(getString(R.string.management_video_file_required));
                return null;
            }
            if ("text".equals(contentType) && textContent.isEmpty()) {
                inputs.get("text_content").setError(
                        getString(R.string.management_text_content_required));
                return null;
            }
            if (("pdf".equals(contentType) || "document".equals(contentType))
                    && selectedDocumentUri == null) {
                showStatus(getString(R.string.management_document_file_required));
                return null;
            }
            body.put("text_content", textContent);
            body.put("requires_exercise_completion", requiresExerciseSwitch.isChecked()
                    || "exercise".equals(contentType));
            body.put("published", lessonPublishedSwitch.isChecked());
            return body;
        }

        String title = required("title");
        boolean lessonExercise = kind == ContentFormKind.TEACHER_EXERCISE;
        String courseId = lessonExercise ? linkedCourseId : selectedCourseId();
        JSONArray questionPayload = buildQuestions();
        if (title == null || (!lessonExercise && courseId == null) || questionPayload == null) {
            return null;
        }
        if (lessonExercise && parentId.isEmpty()) {
            showStatus(getString(R.string.management_missing_parent));
            return null;
        }
        int durationMinutes = (int) decimal("duration", 30, 1, 300);
        double passScore = decimal("pass_score", 50, 0, 100);
        int maxAttempts = (int) decimal("max_attempts", 1, 1, 100);
        JSONObject settings;
        try {
            settings = new JSONObject(existingExerciseSettings.toString());
        } catch (Exception ignored) {
            settings = new JSONObject();
        }
        settings.put("duration_seconds", durationMinutes * 60);
        settings.put("pass_score", passScore);
        settings.put("max_attempts", maxAttempts);
        settings.put("shuffle_questions", shuffleQuestionsSwitch.isChecked());
        settings.put("shuffle_choices", shuffleChoicesSwitch.isChecked());
        settings.put("show_answers", spinnerValue("show_answers"));
        if (courseId != null && !courseId.isEmpty()) settings.put("course_id", courseId);
        body.put("title", title);
        body.put("type", firstQuestionType());
        body.put("published", "published".equals(spinnerValue("status")));
        if (lessonExercise) body.put("lesson", parentId);
        body.put("settings", settings);
        body.put("questions", questionPayload);
        return body;
    }

    /** Kiểm tra và chuyển các QuestionFields thành mảng JSON gửi server. */
    private JSONArray buildQuestions() throws Exception {
        if (totalQuestionCount() == 0) {
            showStatus(getString(R.string.management_exam_requires_question));
            return null;
        }
        JSONArray result = new JSONArray();
        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            QuestionFields fields = questions.get(questionIndex);
            String prompt = text(fields.prompt);
            if (prompt.isEmpty()) {
                fields.prompt.setError(getString(R.string.required_field));
                showStatus("Câu " + (questionIndex + 1) + " chưa có nội dung câu hỏi.");
                return null;
            }
            String questionType = fields.questionType();
            JSONObject meta;
            try {
                meta = new JSONObject(fields.existingMeta.toString());
            } catch (Exception ignored) {
                meta = new JSONObject();
            }
            meta.remove("accepted_answers");
            meta.remove("similarity_threshold");
            meta.remove("correct_pairs");
            meta.remove("pairs");
            meta.put("type", questionType);
            meta.put("points", decimal(fields.score, 1, 0.1, 100));
            JSONArray choices = new JSONArray();
            if ("mcq".equals(questionType)) {
                int nonEmptyChoices = 0;
                boolean correctChoiceIncluded = false;
                for (ChoiceFields choiceFields : fields.choices) {
                    String choiceText = text(choiceFields.input);
                    if (choiceText.isEmpty()) continue;
                    JSONObject choice = new JSONObject();
                    if (!choiceFields.existingId.isEmpty()) {
                        choice.put("id", choiceFields.existingId);
                    }
                    choice.put("text", choiceText);
                    choice.put("is_correct", choiceFields.correct.isChecked());
                    choice.put("position", nonEmptyChoices);
                    choices.put(choice);
                    nonEmptyChoices++;
                    if (choiceFields.correct.isChecked()) correctChoiceIncluded = true;
                }
                if (nonEmptyChoices < 2) {
                    fields.choices.get(0).input.setError(
                            getString(R.string.management_exam_two_choices));
                    showStatus("Câu " + (questionIndex + 1)
                            + " cần ít nhất 2 phương án trả lời.");
                    return null;
                }
                if (!correctChoiceIncluded) {
                    showStatus(getString(R.string.management_exam_select_correct));
                    return null;
                }
            } else if ("short_answer".equals(questionType)) {
                JSONArray accepted = commaSeparatedValues(text(fields.acceptedAnswers));
                if (accepted.length() == 0) {
                    fields.acceptedAnswers.setError(
                            getString(R.string.management_short_answer_required));
                    showStatus("Câu " + (questionIndex + 1)
                            + " chưa có đáp án được chấp nhận.");
                    return null;
                }
                meta.put("accepted_answers", accepted);
                meta.put("similarity_threshold", 0.85d);
            } else if ("matching".equals(questionType)) {
                JSONArray pairs = new JSONArray();
                JSONObject correctPairs = new JSONObject();
                int pairIndex = 0;
                for (MatchingPairFields pairFields : fields.matchingPairs) {
                    String left = text(pairFields.left);
                    String right = text(pairFields.right);
                    if (left.isEmpty() && right.isEmpty()) continue;
                    if (left.isEmpty() || right.isEmpty()) {
                        (left.isEmpty() ? pairFields.left : pairFields.right)
                                .setError(getString(R.string.required_field));
                        return null;
                    }
                    String leftId = "L" + (pairIndex + 1);
                    String rightId = "R" + (pairIndex + 1);
                    correctPairs.put(leftId, rightId);
                    JSONObject pair = new JSONObject();
                    pair.put("left", left);
                    pair.put("right", right);
                    pairs.put(pair);
                    JSONObject leftChoice = new JSONObject();
                    if (!pairFields.leftChoiceId.isEmpty()) {
                        leftChoice.put("id", pairFields.leftChoiceId);
                    }
                    leftChoice.put("text", left);
                    leftChoice.put("is_correct", false);
                    leftChoice.put("position", pairIndex * 2);
                    choices.put(leftChoice);
                    JSONObject rightChoice = new JSONObject();
                    if (!pairFields.rightChoiceId.isEmpty()) {
                        rightChoice.put("id", pairFields.rightChoiceId);
                    }
                    rightChoice.put("text", right);
                    rightChoice.put("is_correct", false);
                    rightChoice.put("position", pairIndex * 2 + 1);
                    choices.put(rightChoice);
                    pairIndex++;
                }
                if (pairIndex < 2) {
                    fields.matchingPairs.get(0).left.setError(
                            getString(R.string.management_matching_pairs_required));
                    showStatus("Câu " + (questionIndex + 1)
                            + " cần ít nhất 2 cặp để nối.");
                    return null;
                }
                meta.put("correct_pairs", correctPairs);
                meta.put("pairs", pairs);
            }
            JSONObject question = new JSONObject();
            if (!fields.existingId.isEmpty()) question.put("id", fields.existingId);
            question.put("prompt", prompt);
            question.put("meta", meta);
            question.put("choices", choices);
            result.put(question);
        }
        for (JSONObject compact : compactQuestions) {
            if (compact == null) continue;
            result.put(new JSONObject(compact.toString()));
        }
        return result;
    }

    private String firstQuestionType() {
        if (!questions.isEmpty()) return questions.get(0).questionType();
        if (!compactQuestions.isEmpty()) {
            JSONObject meta = compactQuestions.get(0).optJSONObject("meta");
            return meta == null ? "mcq" : safe(meta.optString("type", "mcq"));
        }
        return "mcq";
    }

    private JSONArray commaSeparatedValues(String raw) throws Exception {
        JSONArray result = new JSONArray();
        if (raw == null || raw.trim().isEmpty()) return result;
        for (String part : raw.split("[,;\\n]")) {
            String value = safe(part);
            if (!value.isEmpty()) result.put(value);
        }
        return result;
    }

    /** Đọc trường bắt buộc và ném lỗi có nội dung rõ ràng nếu để trống. */
    private String required(String key) {
        String value = value(key);
        TextInputEditText input = inputs.get(key);
        if (value.isEmpty()) {
            if (input != null) input.setError(getString(R.string.required_field));
            showStatus("Vui lòng điền đầy đủ các mục bắt buộc.");
            return null;
        }
        return value;
    }

    /** Kiểm tra URL tùy chọn chỉ chấp nhận HTTP/HTTPS hợp lệ. */
    private String optionalHttpUrl(String key) {
        String raw = value(key);
        if (raw.isEmpty()) return "";
        Uri uri = Uri.parse(raw);
        String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || safe(uri.getHost()).isEmpty()) {
            TextInputEditText input = inputs.get(key);
            if (input != null) input.setError(getString(R.string.management_invalid_http_url));
            return null;
        }
        return raw;
    }

    private boolean isYoutubeUrl(String raw) {
        Uri uri = Uri.parse(raw);
        String host = safe(uri.getHost()).toLowerCase(Locale.ROOT);
        return host.equals("youtu.be") || host.endsWith(".youtu.be")
                || host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com")
                || host.endsWith(".youtube-nocookie.com");
    }

    private double decimal(String key, double defaultValue, double min, double max) {
        return decimal(inputs.get(key), defaultValue, min, max);
    }

    /** Đọc số thực và kiểm tra giới hạn nghiệp vụ của trường. */
    private double decimal(TextInputEditText input, double defaultValue, double min, double max) {
        String raw = text(input);
        if (raw.isEmpty()) return defaultValue;
        try {
            double value = Double.parseDouble(raw);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            if (input != null) input.setError(getString(R.string.invalid_range_value));
            throw exception;
        }
    }

    private String value(String key) {
        return text(inputs.get(key));
    }

    private String spinnerValue(String key) {
        SpinnerBinding binding = spinners.get(key);
        return binding == null ? "" : binding.selectedValue();
    }

    private String selectedCourseId() {
        int position = courseSpinner == null ? -1 : courseSpinner.getSelectedItemPosition();
        if (position < 0 || position >= courseOptions.size()) {
            showStatus(getString(R.string.management_exam_select_course));
            return null;
        }
        return courseOptions.get(position).getId();
    }

    private void selectCourse(String courseId) {
        if (courseSpinner == null || courseId == null || courseId.trim().isEmpty()) return;
        for (int index = 0; index < courseOptions.size(); index++) {
            if (courseId.trim().equals(courseOptions.get(index).getId())) {
                courseSpinner.setSelection(index);
                return;
            }
        }
    }

    /** Tạo danh sách file multipart từ các URI người dùng đã chọn. */
    private List<MultipartFilePart> multipartFiles() {
        List<MultipartFilePart> files = new ArrayList<>();
        if (kind == ContentFormKind.TEACHER_COURSE) {
            if (selectedThumbnailUri != null) {
                files.add(new MultipartFilePart("thumbnail", selectedThumbnailUri));
            }
            if (selectedVideoUri != null) {
                files.add(new MultipartFilePart("video_file", selectedVideoUri));
            }
        } else if (kind == ContentFormKind.TEACHER_LESSON) {
            String contentType = spinnerValue("content_type");
            if ("video".equals(contentType) && selectedVideoUri != null) {
                files.add(new MultipartFilePart("video_file", selectedVideoUri));
            }
            if (("pdf".equals(contentType) || "document".equals(contentType))
                    && selectedDocumentUri != null) {
                files.add(new MultipartFilePart("document_file", selectedDocumentUri));
            }
        }
        return files;
    }

    private String titleForKind() {
        if (kind == ContentFormKind.ADMIN_USER) return getString(R.string.management_create_user_title);
        if (kind == ContentFormKind.TEACHER_COURSE) return getString(R.string.create_course);
        if (kind == ContentFormKind.TEACHER_MODULE) return getString(R.string.management_create_module_title);
        if (kind == ContentFormKind.TEACHER_LESSON) return getString(R.string.management_create_lesson_title);
        if (kind == ContentFormKind.TEACHER_EXERCISE) {
            return getString(editId.isEmpty() ? R.string.management_create_exercise_title
                    : R.string.management_update_exercise_title);
        }
        return editId.isEmpty() ? getString(R.string.create_exam) : "Chỉnh sửa bài kiểm tra";
    }

    private String successMessage() {
        if (kind == ContentFormKind.ADMIN_USER) return getString(R.string.management_user_created);
        if (kind == ContentFormKind.TEACHER_COURSE) return getString(R.string.management_course_created);
        if (kind == ContentFormKind.TEACHER_MODULE) return getString(R.string.management_module_created);
        if (kind == ContentFormKind.TEACHER_LESSON) return getString(R.string.management_lesson_created);
        if (kind == ContentFormKind.TEACHER_EXERCISE) {
            return getString(editId.isEmpty() ? R.string.management_exercise_created
                    : R.string.management_exercise_updated);
        }
        return editId.isEmpty() ? getString(R.string.management_exam_created)
                : "Đã cập nhật bài kiểm tra";
    }

    /** Khóa toàn bộ form và hiện tiến trình trong lúc tải/lưu. */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        submitButton.setEnabled(!loading);
        for (TextInputEditText input : inputs.values()) input.setEnabled(!loading);
        for (SpinnerBinding binding : spinners.values()) binding.spinner.setEnabled(!loading);
        if (courseSpinner != null) courseSpinner.setEnabled(!loading && !courseOptions.isEmpty());
        if (shuffleQuestionsSwitch != null) shuffleQuestionsSwitch.setEnabled(!loading);
        if (shuffleChoicesSwitch != null) shuffleChoicesSwitch.setEnabled(!loading);
        if (requiresExerciseSwitch != null) requiresExerciseSwitch.setEnabled(!loading);
        if (lessonPublishedSwitch != null) lessonPublishedSwitch.setEnabled(!loading);
        if (addQuestionButton != null) addQuestionButton.setEnabled(!loading);
        if (aiQuestionButton != null) aiQuestionButton.setEnabled(!loading);
        if (thumbnailFileButton != null) thumbnailFileButton.setEnabled(!loading);
        if (videoFileButton != null) videoFileButton.setEnabled(!loading);
        if (thumbnailClearButton != null) thumbnailClearButton.setEnabled(!loading);
        if (videoClearButton != null) videoClearButton.setEnabled(!loading);
        if (documentFileButton != null) documentFileButton.setEnabled(!loading);
        if (documentClearButton != null) documentClearButton.setEnabled(!loading);
        if (gradeButtonGroup != null) {
            for (int index = 0; index < gradeButtonGroup.getChildCount(); index++) {
                gradeButtonGroup.getChildAt(index).setEnabled(!loading);
            }
        }
        for (QuestionFields fields : questions) fields.setEnabled(!loading);
    }

    /** Hiển thị lỗi kiểm tra hoặc trạng thái request ngay trên form. */
    private void showStatus(String message) {
        statusText.setText(message == null ? getString(R.string.unknown_error) : message);
        statusText.setVisibility(View.VISIBLE);
    }

    private String text(TextInputEditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    /** Lưu snapshot sau khi tải/lưu để phát hiện thay đổi chưa đồng bộ. */
    private void rememberFormState() {
        savedFormSnapshot = formSnapshot();
    }

    /** So sánh snapshot hiện tại với trạng thái đã ghi nhớ. */
    private boolean hasUnsavedChanges() {
        return savedFormSnapshot != null && !savedFormSnapshot.equals(formSnapshot());
    }

    /** Tạo chuỗi đại diện ổn định cho toàn bộ giá trị trong form động. */
    private String formSnapshot() {
        StringBuilder state = new StringBuilder();
        for (Map.Entry<String, TextInputEditText> entry : inputs.entrySet()) {
            state.append(entry.getKey()).append('=').append(text(entry.getValue())).append('|');
        }
        for (Map.Entry<String, SpinnerBinding> entry : spinners.entrySet()) {
            state.append(entry.getKey()).append('=').append(entry.getValue().selectedValue())
                    .append('|');
        }
        state.append("grade=").append(selectedGrade).append('|');
        int coursePosition = courseSpinner == null
                ? -1 : courseSpinner.getSelectedItemPosition();
        String snapshotCourseId = coursePosition >= 0 && coursePosition < courseOptions.size()
                ? courseOptions.get(coursePosition).getId() : "";
        state.append("course=").append(snapshotCourseId).append('|');
        state.append("files=").append(selectedThumbnailUri).append(',')
                .append(selectedVideoUri).append(',').append(selectedDocumentUri).append('|');
        state.append("switches=")
                .append(shuffleQuestionsSwitch != null && shuffleQuestionsSwitch.isChecked())
                .append(',').append(shuffleChoicesSwitch != null && shuffleChoicesSwitch.isChecked())
                .append(',').append(requiresExerciseSwitch != null && requiresExerciseSwitch.isChecked())
                .append(',').append(lessonPublishedSwitch != null && lessonPublishedSwitch.isChecked())
                .append('|');
        for (QuestionFields question : questions) {
            state.append("question=").append(text(question.prompt)).append(';')
                    .append(question.questionType()).append(';')
                    .append(text(question.acceptedAnswers)).append(';')
                    .append(text(question.score)).append(';');
            for (ChoiceFields choice : question.choices) {
                state.append(text(choice.input)).append(':')
                        .append(choice.correct.isChecked()).append(',');
            }
            state.append(';');
            for (MatchingPairFields pair : question.matchingPairs) {
                state.append(text(pair.left)).append(':').append(text(pair.right)).append(',');
            }
            state.append('|');
        }
        for (JSONObject compact : compactQuestions) {
            state.append("compact=").append(compact == null ? "" : compact.toString())
                    .append('|');
        }
        return state.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Chỉ xử lý callback khi Activity form vẫn còn hoạt động. */
    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SpinnerBinding {
        private final Spinner spinner;
        private final String[] values;

        private SpinnerBinding(Spinner spinner, String[] values) {
            this.spinner = spinner;
            this.values = values == null ? new String[0] : values.clone();
        }

        private String selectedValue() {
            int position = spinner.getSelectedItemPosition();
            return position < 0 || position >= values.length ? "" : values[position];
        }

        private void selectValue(String value) {
            for (int index = 0; index < values.length; index++) {
                if (values[index].equals(value)) {
                    spinner.setSelection(index);
                    return;
                }
            }
        }
    }

    private final class QuestionFields {
        private final View root;
        private final TextView number;
        private final TextView delete;
        private final TextInputEditText prompt;
        private final Spinner typeSpinner;
        private final View mcqLayout;
        private final LinearLayout choicesContainer;
        private final MaterialButton addChoiceButton;
        private final TextInputLayout shortAnswerLayout;
        private final TextInputEditText acceptedAnswers;
        private final View matchingLayout;
        private final LinearLayout matchingPairsContainer;
        private final MaterialButton addMatchingPairButton;
        private final TextInputEditText score;
        private final List<ChoiceFields> choices = new ArrayList<>();
        private final List<MatchingPairFields> matchingPairs = new ArrayList<>();
        private final String[] questionTypes = {"mcq", "short_answer", "matching"};
        private String existingId = "";
        private JSONObject existingMeta = new JSONObject();

        private QuestionFields(View root, String initialType) {
            this.root = root;
            number = root.findViewById(R.id.textExamQuestionNumber);
            delete = root.findViewById(R.id.buttonExamQuestionDelete);
            prompt = root.findViewById(R.id.inputExamQuestionPrompt);
            typeSpinner = root.findViewById(R.id.spinnerExamQuestionType);
            mcqLayout = root.findViewById(R.id.layoutExamMcq);
            choicesContainer = root.findViewById(R.id.groupExamQuestionCorrect);
            addChoiceButton = root.findViewById(R.id.buttonExamAddChoice);
            shortAnswerLayout = root.findViewById(R.id.layoutExamShortAnswer);
            acceptedAnswers = root.findViewById(R.id.inputExamAcceptedAnswers);
            matchingLayout = root.findViewById(R.id.layoutExamMatching);
            matchingPairsContainer = root.findViewById(R.id.containerExamMatchingPairs);
            addMatchingPairButton = root.findViewById(R.id.buttonExamAddMatchingPair);
            score = root.findViewById(R.id.inputExamQuestionScore);
            typeSpinner.setAdapter(spinnerAdapter(new String[]{"Trắc nghiệm một đáp án",
                    "Trả lời ngắn", "Nối cặp"}));
            int initialPosition = "short_answer".equals(initialType) ? 1
                    : "matching".equals(initialType) ? 2 : 0;
            typeSpinner.setSelection(initialPosition, false);
            typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateTypeVisibility();
                }

                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            addChoiceButton.setOnClickListener(view -> addChoice());
            addMatchingPairButton.setOnClickListener(view -> addMatchingPair());
            if (initialPosition == 0) {
                for (int index = 0; index < 4; index++) addChoice();
            } else if (initialPosition == 2) {
                for (int index = 0; index < 2; index++) addMatchingPair();
            }
            updateTypeVisibility();
        }

        private void addChoice() {
            if (choices.size() >= 6) {
                showShortMessage(getString(R.string.management_choice_limit));
                return;
            }
            View row = LayoutInflater.from(ContentFormActivity.this).inflate(
                    R.layout.management_item_question_choice, choicesContainer, false);
            ChoiceFields fields = new ChoiceFields(row);
            fields.correct.setOnClickListener(view -> selectCorrectChoice(this, fields));
            fields.delete.setOnClickListener(view -> removeChoice(fields));
            choices.add(fields);
            choicesContainer.addView(row);
            if (choices.size() == 1) fields.correct.setChecked(true);
            updateChoiceDeleteButtons();
        }

        private void removeChoice(ChoiceFields fields) {
            if (choices.size() <= 2) {
                showShortMessage(getString(R.string.management_exam_two_choices));
                return;
            }
            boolean wasCorrect = fields.correct.isChecked();
            choices.remove(fields);
            choicesContainer.removeView(fields.root);
            if (wasCorrect && !choices.isEmpty()) selectCorrectChoice(this, choices.get(0));
            updateChoiceDeleteButtons();
        }

        private void updateChoiceDeleteButtons() {
            for (ChoiceFields fields : choices) {
                fields.delete.setVisibility(choices.size() > 2 ? View.VISIBLE : View.INVISIBLE);
            }
            addChoiceButton.setEnabled(choices.size() < 6);
        }

        private void addMatchingPair() {
            if (matchingPairs.size() >= 10) {
                showShortMessage(getString(R.string.management_matching_pair_limit));
                return;
            }
            View row = LayoutInflater.from(ContentFormActivity.this).inflate(
                    R.layout.management_item_matching_pair, matchingPairsContainer, false);
            MatchingPairFields fields = new MatchingPairFields(row);
            fields.delete.setOnClickListener(view -> removeMatchingPair(fields));
            matchingPairs.add(fields);
            matchingPairsContainer.addView(row);
            updateMatchingDeleteButtons();
        }

        private void removeMatchingPair(MatchingPairFields fields) {
            if (matchingPairs.size() <= 2) {
                showShortMessage(getString(R.string.management_matching_pairs_required));
                return;
            }
            matchingPairs.remove(fields);
            matchingPairsContainer.removeView(fields.root);
            updateMatchingDeleteButtons();
        }

        private void updateMatchingDeleteButtons() {
            for (MatchingPairFields fields : matchingPairs) {
                fields.delete.setVisibility(matchingPairs.size() > 2
                        ? View.VISIBLE : View.INVISIBLE);
            }
            addMatchingPairButton.setEnabled(matchingPairs.size() < 10);
        }

        private String questionType() {
            int position = typeSpinner.getSelectedItemPosition();
            return position < 0 || position >= questionTypes.length
                    ? "mcq" : questionTypes[position];
        }

        private void setQuestionType(String value) {
            for (int index = 0; index < questionTypes.length; index++) {
                if (questionTypes[index].equals(value)) {
                    typeSpinner.setSelection(index);
                    updateTypeVisibility();
                    return;
                }
            }
        }

        private void updateTypeVisibility() {
            String type = questionType();
            if ("mcq".equals(type) && choices.isEmpty()) {
                for (int index = 0; index < 4; index++) addChoice();
            } else if ("matching".equals(type) && matchingPairs.isEmpty()) {
                for (int index = 0; index < 2; index++) addMatchingPair();
            }
            mcqLayout.setVisibility("mcq".equals(type) ? View.VISIBLE : View.GONE);
            shortAnswerLayout.setVisibility("short_answer".equals(type)
                    ? View.VISIBLE : View.GONE);
            matchingLayout.setVisibility("matching".equals(type)
                    ? View.VISIBLE : View.GONE);
        }

        private void setEnabled(boolean enabled) {
            prompt.setEnabled(enabled);
            score.setEnabled(enabled);
            delete.setEnabled(enabled);
            typeSpinner.setEnabled(enabled);
            acceptedAnswers.setEnabled(enabled);
            addChoiceButton.setEnabled(enabled && choices.size() < 6);
            addMatchingPairButton.setEnabled(enabled && matchingPairs.size() < 10);
            for (ChoiceFields choice : choices) choice.setEnabled(enabled);
            for (MatchingPairFields pair : matchingPairs) pair.setEnabled(enabled);
        }
    }

    private static final class ChoiceFields {
        private final View root;
        private final RadioButton correct;
        private final TextInputEditText input;
        private final TextView delete;
        private String existingId = "";

        private ChoiceFields(View root) {
            this.root = root;
            correct = root.findViewById(R.id.radioQuestionChoiceCorrect);
            input = root.findViewById(R.id.inputQuestionChoiceText);
            delete = root.findViewById(R.id.buttonQuestionChoiceDelete);
        }

        private void setEnabled(boolean enabled) {
            correct.setEnabled(enabled);
            input.setEnabled(enabled);
            delete.setEnabled(enabled);
        }
    }

    private static final class MatchingPairFields {
        private final View root;
        private final TextInputEditText left;
        private final TextInputEditText right;
        private final TextView delete;
        private String leftChoiceId = "";
        private String rightChoiceId = "";

        private MatchingPairFields(View root) {
            this.root = root;
            left = root.findViewById(R.id.inputMatchingLeft);
            right = root.findViewById(R.id.inputMatchingRight);
            delete = root.findViewById(R.id.buttonMatchingPairDelete);
        }

        private void setEnabled(boolean enabled) {
            left.setEnabled(enabled);
            right.setEnabled(enabled);
            delete.setEnabled(enabled);
        }
    }
}
