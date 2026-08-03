package com.example.smartkid.feature.teacher.course.builder;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.remote.MultipartFilePart;
import com.example.smartkid.data.repository.ManagementRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.util.Locale;

/** Bottom sheet chỉnh sửa bài học ngay tại chỗ trong Course Builder. */
public final class LessonEditorBottomSheet extends BottomSheetDialogFragment {

    /** Trả về bài học đã cập nhật (JSON từ server) để builder refresh row. */
    public interface OnSavedListener {
        void onSaved(FeatureItem updated);
    }

    public interface OnDeletedListener {
        void onDeleted(String lessonId);
    }

    private static final String ARG_LESSON_ID = "lesson_id";
    private static final String ARG_LESSON_TITLE = "lesson_title";

    private static final String[] TYPE_LABELS_RES = {
            "video", "text", "exercise", "pdf", "document"
    };

    private ManagementRepository repository;
    private OnSavedListener savedListener;
    private OnDeletedListener deletedListener;

    private String lessonId;
    private String lessonTitle;

    private Spinner contentTypeSpinner;
    private String[] contentTypeValues;
    private View videoSourceGroup;
    private View videoFileRow;
    private TextView videoFileName;
    private View documentSourceGroup;
    private TextView documentFileName;
    private EditText textContentInput;
    private EditText noteInput;
    private SwitchMaterial requiresExerciseSwitch;
    private SwitchMaterial publishedSwitch;
    private TextView statusView;
    private MaterialButton doneButton;

    private Uri selectedVideoUri;
    private Uri selectedDocumentUri;
    private ActivityResultLauncher<String[]> videoPicker;
    private ActivityResultLauncher<String[]> documentPicker;
    private boolean hasExistingDocument;
    private boolean saving;

    /** Tạo bottom sheet cùng ID và tiêu đề lesson cần chỉnh sửa. */
    public static LessonEditorBottomSheet newInstance(String lessonId, String lessonTitle) {
        LessonEditorBottomSheet sheet = new LessonEditorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_LESSON_ID, lessonId);
        args.putString(ARG_LESSON_TITLE, lessonTitle);
        sheet.setArguments(args);
        return sheet;
    }

    /** Đăng ký callback để màn cha nhận lesson sau khi lưu. */
    public void setOnSavedListener(OnSavedListener listener) {
        this.savedListener = listener;
    }

    /** Đăng ký callback để màn cha loại lesson sau khi xóa. */
    public void setOnDeletedListener(OnDeletedListener listener) {
        this.deletedListener = listener;
    }

    /** Đọc arguments và khởi tạo Repository cho bottom sheet. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        lessonId = args == null ? "" : safe(args.getString(ARG_LESSON_ID));
        lessonTitle = args == null ? "" : safe(args.getString(ARG_LESSON_TITLE));
        repository = new ManagementRepository(requireContext());
        videoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::handleSelectedVideo);
        documentPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::handleSelectedDocument);
    }

    @NonNull
    /** Cấu hình dialog chiếm chiều rộng phù hợp và hỗ trợ bàn phím. */
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), getTheme());
    }

    @Nullable
    /** Tạo layout chỉnh sửa nội dung lesson. */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.teacher_sheet_lesson_editor, container, false);
    }

    /** Ánh xạ view, gắn file picker và tải dữ liệu lesson hiện tại. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        bindContentTypeSpinner();
        ((TextView) view.findViewById(R.id.textSheetLessonTitle)).setText(lessonTitle);
        view.findViewById(R.id.buttonSheetLessonClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.buttonSheetLessonDelete).setOnClickListener(v -> confirmDelete());
        doneButton.setOnClickListener(v -> save());
        loadLesson();
    }

    /** Ánh xạ tiêu đề, loại nội dung, URL, tệp và các nút lưu/xóa. */
    private void bindViews(View view) {
        contentTypeSpinner = view.findViewById(R.id.spinnerSheetLessonContentType);
        videoSourceGroup = view.findViewById(R.id.groupSheetVideoSource);
        videoFileRow = view.findViewById(R.id.rowSheetVideoFile);
        videoFileName = view.findViewById(R.id.textSheetVideoFile);
        documentSourceGroup = view.findViewById(R.id.groupSheetDocumentSource);
        documentFileName = view.findViewById(R.id.textSheetDocumentFile);
        textContentInput = view.findViewById(R.id.editSheetTextContent);
        noteInput = view.findViewById(R.id.editSheetLessonNote);
        requiresExerciseSwitch = view.findViewById(R.id.switchSheetRequiresExercise);
        publishedSwitch = view.findViewById(R.id.switchSheetPublished);
        statusView = view.findViewById(R.id.textSheetLessonStatus);
        doneButton = view.findViewById(R.id.buttonSheetLessonDone);
        view.findViewById(R.id.buttonSheetVideoFilePick).setOnClickListener(v -> pickVideo());
        view.findViewById(R.id.buttonSheetDocumentFilePick).setOnClickListener(v -> pickDocument());
    }

    /** Cấu hình Spinner loại nội dung và cập nhật các trường phụ thuộc. */
    private void bindContentTypeSpinner() {
        String[] labels = {
                getString(R.string.teacher_content_type_video),
                getString(R.string.teacher_content_type_text),
                getString(R.string.teacher_content_type_exercise),
                getString(R.string.teacher_content_type_pdf),
                getString(R.string.teacher_content_type_document)
        };
        contentTypeValues = TYPE_LABELS_RES;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contentTypeSpinner.setAdapter(adapter);
        contentTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateContentFields();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    /** Hiện/ẩn trường URL, video hoặc tài liệu theo loại lesson được chọn. */
    private void updateContentFields() {
        boolean video = "video".equals(selectedContentType());
        boolean text = "text".equals(selectedContentType());
        boolean document = "pdf".equals(selectedContentType())
                || "document".equals(selectedContentType());
        // Bài học chỉ nhận video dạng file upload.
        videoSourceGroup.setVisibility(video ? View.VISIBLE : View.GONE);
        videoFileRow.setVisibility(video ? View.VISIBLE : View.GONE);
        textContentInput.setVisibility(text ? View.VISIBLE : View.GONE);
        documentSourceGroup.setVisibility(document ? View.VISIBLE : View.GONE);
    }

    private String selectedContentType() {
        int pos = contentTypeSpinner.getSelectedItemPosition();
        if (contentTypeValues == null || pos < 0 || pos >= contentTypeValues.length) {
            return "lesson";
        }
        return contentTypeValues[pos];
    }

    private void selectContentType(String value) {
        if (contentTypeValues == null) return;
        for (int i = 0; i < contentTypeValues.length; i++) {
            if (contentTypeValues[i].equals(value)) {
                contentTypeSpinner.setSelection(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Tải lesson hiện có để điền vào form.
    // ------------------------------------------------------------------

    /** Tải JSON lesson hiện tại để điền vào form. */
    private void loadLesson() {
        if (lessonId.isEmpty()) return;
        repository.loadObject("content/lessons/" + lessonId + "/",
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable() || data == null) return;
                        prefill(data);
                    }

                    @Override
                    public void onError(ApiError error) {
                        // Giữ giá trị mặc định nếu tải lỗi; giáo viên vẫn có thể sửa và lưu.
                    }
                });
    }

    /** Điền dữ liệu server vào các trường mà không làm phát sinh trạng thái thay đổi giả. */
    private void prefill(JSONObject data) {
        String type = SafeJson.string(data, "lesson", "content_type", "type");
        selectContentType(type);
        noteInput.setText(SafeJson.string(data, "", "introduction"));
        textContentInput.setText(SafeJson.string(data, "", "text_content"));
        String document = SafeJson.string(data, "", "document_file");
        hasExistingDocument = !document.isEmpty();
        if (hasExistingDocument) documentFileName.setText("Tài liệu hiện tại đã được tải lên");
        requiresExerciseSwitch.setChecked(
                SafeJson.bool(data, false, "requires_exercise_completion"));
        publishedSwitch.setChecked(SafeJson.bool(data, false, "published"));
        updateContentFields();
    }

    // ------------------------------------------------------------------
    // Chọn tệp video
    // ------------------------------------------------------------------

    private void pickVideo() {
        try {
            videoPicker.launch(new String[]{"video/*"});
        } catch (Exception exception) {
            AppLogger.error(requireContext(), "LessonEditorBottomSheet",
                    "Không thể mở bộ chọn video", exception);
        }
    }

    /** Kiểm tra video người dùng chọn và lưu URI chờ upload. */
    private void handleSelectedVideo(Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // URI vẫn đọc được trong phiên hiện tại.
        }
        selectedVideoUri = uri;
        videoFileName.setText(fileDisplayName(uri));
    }

    /** Mở trình chọn tài liệu cho lesson dạng file. */
    private void pickDocument() {
        try {
            if ("pdf".equals(selectedContentType())) {
                documentPicker.launch(new String[]{"application/pdf"});
            } else {
                documentPicker.launch(new String[]{"application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.oasis.opendocument.text"});
            }
        } catch (Exception exception) {
            AppLogger.error(requireContext(), "LessonEditorBottomSheet",
                    "Không thể mở bộ chọn tài liệu", exception);
        }
    }

    /** Kiểm tra tài liệu và lưu URI để gửi multipart khi lưu. */
    private void handleSelectedDocument(Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        selectedDocumentUri = uri;
        documentFileName.setText(fileDisplayName(uri));
    }

    // ------------------------------------------------------------------
    // Lưu bằng PATCH; chuyển sang multipart khi người dùng chọn tệp.
    // ------------------------------------------------------------------

    /** Kiểm tra form, tạo JSON và chọn request PATCH thường hoặc multipart. */
    private void save() {
        if (saving || lessonId.isEmpty()) return;
        String contentType = selectedContentType();
        JSONObject body = new JSONObject();
        try {
            body.put("content_type", contentType);
            body.put("introduction", safe(text(noteInput)));
            body.put("requires_exercise_completion", requiresExerciseSwitch.isChecked());
            body.put("published", publishedSwitch.isChecked());
            if ("text".equals(contentType)) {
                String textContent = safe(text(textContentInput));
                if (textContent.isEmpty()) {
                    textContentInput.setError(getString(R.string.management_text_content_required));
                    return;
                }
                body.put("text_content", textContent);
            }
        } catch (Exception exception) {
            showStatus(getString(R.string.management_create_prepare_error));
            return;
        }

        boolean documentType = "pdf".equals(contentType) || "document".equals(contentType);
        if (documentType && selectedDocumentUri == null && !hasExistingDocument) {
            showStatus(getString(R.string.management_document_file_required));
            return;
        }
        boolean hasFile = ("video".equals(contentType) && selectedVideoUri != null)
                || (documentType && selectedDocumentUri != null);
        setSaving(true);
        if (hasFile) {
            java.util.List<MultipartFilePart> files = new java.util.ArrayList<>();
            if ("video".equals(contentType) && selectedVideoUri != null) {
                files.add(new MultipartFilePart("video_file", selectedVideoUri));
            }
            if (documentType && selectedDocumentUri != null) {
                files.add(new MultipartFilePart("document_file", selectedDocumentUri));
            }
            repository.multipartAction(Request.Method.PATCH,
                    "content/lessons/" + lessonId + "/", body, files, saveCallback());
        } else {
            repository.action(Request.Method.PATCH,
                    "content/lessons/" + lessonId + "/", body, saveCallback());
        }
    }

    /** Xử lý kết quả lưu, báo màn cha và đóng bottom sheet. */
    private ApiCallback<JSONObject> saveCallback() {
        return new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject updated) {
                if (!isUsable()) return;
                setSaving(false);
                JSONObject source = updated == null ? new JSONObject() : updated;
                String title = SafeJson.string(source, lessonTitle, "title");
                FeatureItem item = new FeatureItem(lessonId, title, "", "", "", source);
                if (savedListener != null) savedListener.onSaved(item);
                dismiss();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                setSaving(false);
                showStatus(error == null ? getString(R.string.unknown_error) : error.getMessage());
            }
        };
    }

    /** Yêu cầu xác nhận trước khi xóa lesson. */
    private void confirmDelete() {
        if (saving || lessonId.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa bài học này?")
                .setMessage("Bài học và tiến độ liên quan sẽ bị xóa. Thao tác này không thể hoàn tác.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Xóa bài học", (dialog, which) -> deleteLesson())
                .show();
    }

    /** Gọi DELETE lesson rồi thông báo cho màn cha cập nhật danh sách. */
    private void deleteLesson() {
        setSaving(true);
        repository.action(Request.Method.DELETE, "content/lessons/" + lessonId + "/",
                new JSONObject(), new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        setSaving(false);
                        if (deletedListener != null) deletedListener.onDeleted(lessonId);
                        dismiss();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        setSaving(false);
                        showStatus(error == null ? getString(R.string.unknown_error)
                                : error.getMessage());
                    }
                });
    }

    // ------------------------------------------------------------------
    // Các hàm hỗ trợ
    // ------------------------------------------------------------------

private String fileDisplayName(Uri uri) {
        String name = "";
        try (android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = safe(cursor.getString(index));
            }
        } catch (Exception ignored) {
        }
        return name.isEmpty() ? safe(uri.getLastPathSegment()) : name;
    }

    /** Khóa form và hiển thị tiến trình trong lúc lưu/xóa. */
    private void setSaving(boolean value) {
        saving = value;
        doneButton.setEnabled(!value);
        doneButton.setText(value ? R.string.sheet_lesson_saving : R.string.sheet_done);
    }

    /** Hiển thị lỗi kiểm tra hoặc lỗi API trong bottom sheet. */
    private void showStatus(String message) {
        statusView.setText(message == null ? getString(R.string.unknown_error) : message);
        statusView.setVisibility(View.VISIBLE);
    }

    /** Kiểm tra Fragment vẫn gắn với Activity trước khi cập nhật UI. */
    private boolean isUsable() {
        return isAdded() && getContext() != null;
    }

    private String text(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
