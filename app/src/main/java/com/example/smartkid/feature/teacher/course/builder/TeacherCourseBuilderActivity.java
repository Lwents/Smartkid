package com.example.smartkid.feature.teacher.course.builder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.example.smartkid.R;
import com.example.smartkid.common.navigation.UserRole;
import com.example.smartkid.common.ui.BaseActivity;
import com.example.smartkid.common.ui.LiquidGlassUi;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.FeatureItem;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiError;
import com.example.smartkid.data.repository.ManagementRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Course Builder 1 màn: chương + bài học, kéo-thả sắp xếp, tự động lưu. */
public final class TeacherCourseBuilderActivity extends BaseActivity
        implements BuilderModuleAdapter.Listener {

    public static final String EXTRA_COURSE_ID = "builder_course_id";
    public static final String EXTRA_COURSE_TITLE = "builder_course_title";

    private ManagementRepository repository;
    private RecyclerView recycler;
    private BuilderModuleAdapter adapter;
    private ItemTouchHelper moduleTouchHelper;
    private ProgressBar progress;
    private TextView status;
    private TextView empty;
    private View autosaveBadge;
    private TextView autosaveText;
    private TextView titleView;

    private final List<BuilderModule> modules = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable hideBadgeRunnable;

    private String courseId;
    private String courseTitle;
    private int loadGeneration;

    /** Khởi tạo trình xây dựng khóa học và kiểm tra quyền Teacher. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (!isTeacher()) {
                showErrorDialog(getString(R.string.teacher_permission_required));
                finish();
                return;
            }
            setContentView(R.layout.teacher_activity_course_builder);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.teacherCourseBuilderRoot,
                    R.drawable.admin_bg_screen, true);
            courseId = safe(getIntent() == null ? null : getIntent().getStringExtra(EXTRA_COURSE_ID));
            courseTitle = safe(getIntent() == null ? null
                    : getIntent().getStringExtra(EXTRA_COURSE_TITLE));
            if (courseId.isEmpty()) {
                showErrorDialog(getString(R.string.invalid_course));
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            bindViews();
            loadModules();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherCourseBuilderActivity",
                    "Không thể mở trình dựng khóa học", exception);
            showErrorDialog(getString(R.string.builder_load_error));
        }
    }

    /** Chỉ cho phép role Teacher truy cập các thao tác soạn khóa học. */
    private boolean isTeacher() {
        return UserRole.fromString(new SessionManager(this).getUser().getRole()).isTeacher();
    }

    /** Ánh xạ RecyclerView module, toolbar, trạng thái lưu và các nút tạo. */
    private void bindViews() {
        recycler = findViewById(R.id.recyclerTeacherBuilder);
        progress = findViewById(R.id.progressTeacherBuilder);
        status = findViewById(R.id.textTeacherBuilderStatus);
        empty = findViewById(R.id.textTeacherBuilderEmpty);
        autosaveBadge = findViewById(R.id.badgeTeacherBuilderAutosave);
        autosaveText = findViewById(R.id.textTeacherBuilderAutosave);
        titleView = findViewById(R.id.textTeacherBuilderTitle);
        titleView.setText(courseTitle);

        MaterialToolbar toolbar = findViewById(R.id.toolbarTeacherCourseBuilder);
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new BuilderModuleAdapter(modules, this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        moduleTouchHelper = new ItemTouchHelper(new ModuleReorderCallback());
        moduleTouchHelper.attachToRecyclerView(recycler);

        findViewById(R.id.buttonTeacherBuilderAddModule).setOnClickListener(v -> promptAddModule());
        findViewById(R.id.buttonTeacherBuilderEditTitle).setOnClickListener(v -> promptEditCourseTitle());
    }

    // ------------------------------------------------------------------
    // Tải cấu trúc module và lesson của khóa học.
    // ------------------------------------------------------------------

    /** Tải module của khóa học và bắt đầu một generation tải mới. */
    private void loadModules() {
        int generation = ++loadGeneration;
        setLoading(true);
        status.setVisibility(View.GONE);
        repository.load("content/courses/" + courseId + "/modules/",
                new ApiCallback<List<FeatureItem>>() {
                    @Override
                    public void onSuccess(List<FeatureItem> loaded) {
                        if (!isUsable() || generation != loadGeneration) return;
                        int previousCount = modules.size();
                        modules.clear();
                        if (previousCount > 0) {
                            adapter.notifyItemRangeRemoved(0, previousCount);
                        }
                        if (loaded != null) {
                            for (FeatureItem item : loaded) {
                                if (item != null && !item.getId().isEmpty()) {
                                    modules.add(new BuilderModule(item));
                                }
                            }
                        }
                        if (!modules.isEmpty()) {
                            adapter.notifyItemRangeInserted(0, modules.size());
                        }
                        updateEmpty();
                        if (modules.isEmpty()) {
                            setLoading(false);
                            return;
                        }
                        loadLessonsForAll(generation);
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable() || generation != loadGeneration) return;
                        setLoading(false);
                        showStatus(error == null ? getString(R.string.builder_load_error)
                                : error.getMessage());
                    }
                });
    }

    /** Tải lesson cho từng module và bỏ callback thuộc generation cũ. */
    private void loadLessonsForAll(int generation) {
        final int[] pending = {modules.size()};
        for (int i = 0; i < modules.size(); i++) {
            final BuilderModule module = modules.get(i);
            final int index = i;
            repository.load("content/modules/" + module.id + "/lessons/",
                    new ApiCallback<List<FeatureItem>>() {
                        @Override
                        public void onSuccess(List<FeatureItem> lessons) {
                            if (!isUsable() || generation != loadGeneration) return;
                            module.lessons.clear();
                            if (lessons != null) {
                                for (FeatureItem lesson : lessons) {
                                    if (lesson != null && !lesson.getId().isEmpty()) {
                                        module.lessons.add(lesson);
                                    }
                                }
                            }
                            adapter.notifyItemChanged(index);
                            if (--pending[0] <= 0) setLoading(false);
                        }

                        @Override
                        public void onError(ApiError error) {
                            if (!isUsable() || generation != loadGeneration) return;
                            if (--pending[0] <= 0) setLoading(false);
                        }
                    });
        }
    }

    // ------------------------------------------------------------------
    // Thêm chương / bài học
    // ------------------------------------------------------------------

    /** Hiển thị form nhập tên module trước khi tạo. */
    private void promptAddModule() {
        final EditText input = new EditText(this);
        input.setHint(R.string.builder_add_module_hint);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_add_module)
                .setView(padded(input))
                .setPositiveButton(R.string.builder_add_module, (d, w) -> {
                    String title = input.getText() == null ? "" : input.getText().toString().trim();
                    if (title.isEmpty()) title = getString(R.string.builder_module_default_title);
                    createModule(title);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Gọi API tạo module mới ở cuối khóa học. */
    private void createModule(String title) {
        showSaving();
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("course", courseId);
            body.put("position", modules.size());
        } catch (Exception ignored) {
        }
        repository.action(Request.Method.POST, "content/courses/" + courseId + "/modules/", body,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject created) {
                        if (!isUsable()) return;
                        if (created != null && !safe(created.optString("id", "")).isEmpty()) {
                            BuilderModule module = new BuilderModule(
                                    new FeatureItem(created.optString("id"), created.optString("title"),
                                            "", "", "", created));
                            modules.add(module);
                            adapter.notifyItemInserted(modules.size() - 1);
                            updateEmpty();
                            showSaved();
                        } else {
                            showSaveError();
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        if (error != null && error.isSessionExpired()) handleApiError(error);
                        else showSaveError();
                    }
                });
    }

    /** Tạo lesson mới trong module từ thao tác của adapter. */
    @Override
    public void onAddLesson(BuilderModule module, String title, EditText input, ProgressBar progressBar) {
        input.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
            body.put("module", module.id);
            body.put("content_type", "lesson");
            body.put("position", module.lessons.size());
        } catch (Exception ignored) {
        }
        repository.action(Request.Method.POST, "content/modules/" + module.id + "/lessons/", body,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject created) {
                        if (!isUsable()) return;
                        input.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                        if (created != null && !safe(created.optString("id", "")).isEmpty()) {
                            input.setText("");
                            module.lessons.add(new FeatureItem(created.optString("id"),
                                    created.optString("title"), "", "", "", created));
                            int index = modules.indexOf(module);
                            if (index >= 0) adapter.notifyItemChanged(index);
                            showSaved();
                        } else {
                            showSaveError();
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        input.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                        if (error != null && error.isSessionExpired()) handleApiError(error);
                        else showSaveError();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Sắp xếp lại thứ tự
    // ------------------------------------------------------------------

    /** Gửi thứ tự module hiện tại lên server sau thao tác kéo thả. */
    private void persistModuleOrder() {
        showSaving();
        JSONObject orderMap = new JSONObject();
        try {
            for (int i = 0; i < modules.size(); i++) {
                orderMap.put(modules.get(i).id, i);
            }
        } catch (Exception ignored) {
        }
        JSONObject body = new JSONObject();
        try {
            body.put("order_map", orderMap);
        } catch (Exception ignored) {
        }
        repository.action(Request.Method.POST,
                "content/courses/" + courseId + "/modules/reorder/", body, saveCallback());
    }

    /** Gửi thứ tự lesson mới của một module lên server. */
    @Override
    public void onLessonReordered(BuilderModule module) {
        showSaving();
        JSONObject orderMap = new JSONObject();
        try {
            for (int i = 0; i < module.lessons.size(); i++) {
                orderMap.put(module.lessons.get(i).getId(), i);
            }
        } catch (Exception ignored) {
        }
        JSONObject body = new JSONObject();
        try {
            body.put("order_map", orderMap);
        } catch (Exception ignored) {
        }
        repository.action(Request.Method.POST,
                "content/modules/" + module.id + "/lessons/reorder/", body, saveCallback());
    }

    // ------------------------------------------------------------------
    // Chỉnh sửa tiêu đề
    // ------------------------------------------------------------------

    /** Hiển thị menu đổi tên hoặc xóa module được chọn. */
    @Override
    public void onModuleMenuClicked(BuilderModule module) {
        final EditText input = new EditText(this);
        input.setText(module.title);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_edit_title)
                .setView(padded(input))
                .setPositiveButton(R.string.save, (d, w) -> {
                    String title = input.getText() == null ? "" : input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    module.title = title;
                    int index = modules.indexOf(module);
                    if (index >= 0) adapter.notifyItemChanged(index);
                    patchModuleTitle(module, title);
                })
                .setNeutralButton(R.string.builder_delete, (d, w) -> confirmDeleteModule(module))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Cập nhật tên module bằng PATCH và đồng bộ lại model cục bộ. */
    private void patchModuleTitle(BuilderModule module, String title) {
        showSaving();
        JSONObject body = new JSONObject();
        try {
            body.put("title", title);
        } catch (Exception ignored) {
        }
        repository.action(Request.Method.PATCH, "content/modules/" + module.id + "/", body,
                saveCallback());
    }

    /** Yêu cầu xác nhận trước khi xóa module cùng các lesson bên trong. */
    private void confirmDeleteModule(BuilderModule module) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.builder_delete_module_confirm)
                .setPositiveButton(R.string.builder_delete, (d, w) -> deleteModule(module))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Xóa module trên server rồi loại nó khỏi RecyclerView. */
    private void deleteModule(BuilderModule module) {
        showSaving();
        int index = modules.indexOf(module);
        repository.action(Request.Method.DELETE, "content/modules/" + module.id + "/", null,
                new ApiCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isUsable()) return;
                        int idx = modules.indexOf(module);
                        if (idx >= 0) {
                            modules.remove(idx);
                            adapter.notifyItemRemoved(idx);
                        }
                        updateEmpty();
                        showSaved();
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable()) return;
                        if (error != null && error.isSessionExpired()) handleApiError(error);
                        else showSaveError();
                    }
                });
    }

    /** Hiển thị form đổi tên khóa học và gửi PATCH khi xác nhận. */
    private void promptEditCourseTitle() {
        final EditText input = new EditText(this);
        input.setText(courseTitle);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.builder_edit_title)
                .setView(padded(input))
                .setPositiveButton(R.string.save, (d, w) -> {
                    String title = input.getText() == null ? "" : input.getText().toString().trim();
                    if (title.isEmpty()) return;
                    courseTitle = title;
                    titleView.setText(title);
                    showSaving();
                    JSONObject body = new JSONObject();
                    try {
                        body.put("title", title);
                    } catch (Exception ignored) {
                    }
                    repository.action(Request.Method.PATCH, "content/courses/" + courseId + "/",
                            body, saveCallback());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------
    // Chạm vào bài học để mở bảng chỉnh sửa phía dưới
    // ------------------------------------------------------------------

    /** Mở bottom sheet chỉnh sửa lesson được giáo viên chọn. */
    @Override
    public void onLessonClicked(String moduleId, FeatureItem lesson) {
        LessonEditorBottomSheet sheet = LessonEditorBottomSheet.newInstance(
                lesson.getId(), lesson.getTitle());
        sheet.setOnSavedListener(updated -> {
            for (BuilderModule module : modules) {
                if (!module.id.equals(moduleId)) continue;
                for (int i = 0; i < module.lessons.size(); i++) {
                    if (module.lessons.get(i).getId().equals(lesson.getId())) {
                        module.lessons.set(i, updated);
                        int index = modules.indexOf(module);
                        if (index >= 0) adapter.notifyItemChanged(index);
                        break;
                    }
                }
                break;
            }
            showSaved();
        });
        sheet.setOnDeletedListener(deletedId -> {
            for (BuilderModule module : modules) {
                if (!module.id.equals(moduleId)) continue;
                for (int index = module.lessons.size() - 1; index >= 0; index--) {
                    if (module.lessons.get(index).getId().equals(deletedId)) {
                        module.lessons.remove(index);
                    }
                }
                int moduleIndex = modules.indexOf(module);
                if (moduleIndex >= 0) adapter.notifyItemChanged(moduleIndex);
                break;
            }
            showSaved();
        });
        sheet.show(getSupportFragmentManager(), "lesson_editor");
    }

    /** Bắt đầu kéo module khi người dùng giữ drag handle. */
    @Override
    public void onModuleDragStart(RecyclerView.ViewHolder holder) {
        if (moduleTouchHelper != null) moduleTouchHelper.startDrag(holder);
    }

    // ------------------------------------------------------------------
    // Các hàm hỗ trợ
    // ------------------------------------------------------------------

    /** Tạo callback chung để báo trạng thái lưu và tải lại dữ liệu khi cần. */
    private ApiCallback<JSONObject> saveCallback() {
        return new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isUsable()) return;
                showSaved();
            }

            @Override
            public void onError(ApiError error) {
                if (!isUsable()) return;
                if (error != null && error.isSessionExpired()) handleApiError(error);
                else showSaveError();
            }
        };
    }

    private View padded(EditText input) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input);
        return frame;
    }

    /** Hiển thị trạng thái đang đồng bộ thay đổi. */
    private void showSaving() {
        autosaveText.setText(R.string.builder_saving);
        autosaveBadge.setVisibility(View.VISIBLE);
        if (hideBadgeRunnable != null) mainHandler.removeCallbacks(hideBadgeRunnable);
    }

    /** Báo đã lưu và tự ẩn nhãn sau một khoảng ngắn. */
    private void showSaved() {
        autosaveText.setText(R.string.builder_autosaved);
        autosaveBadge.setVisibility(View.VISIBLE);
        if (hideBadgeRunnable != null) mainHandler.removeCallbacks(hideBadgeRunnable);
        hideBadgeRunnable = () -> {
            if (autosaveBadge != null) autosaveBadge.setVisibility(View.INVISIBLE);
        };
        mainHandler.postDelayed(hideBadgeRunnable, 2000);
    }

    /** Hiển thị lỗi khi không thể đồng bộ cấu trúc khóa học. */
    private void showSaveError() {
        showStatus(getString(R.string.builder_save_error));
        autosaveBadge.setVisibility(View.INVISIBLE);
    }

    /** Khóa thao tác xây dựng trong lúc tải toàn bộ cây nội dung. */
    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /** Hiển thị hướng dẫn tạo module khi khóa học chưa có nội dung. */
    private void updateEmpty() {
        empty.setVisibility(modules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Hiển thị thông báo nghiệp vụ của trình xây dựng khóa học. */
    private void showStatus(String message) {
        status.setText(message == null ? getString(R.string.unknown_error) : message);
        status.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> {
            if (status != null) status.setVisibility(View.GONE);
        }, 4000);
    }

    /** Xác nhận Activity còn hợp lệ trước khi callback cập nhật RecyclerView. */
    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** Kéo-thả chương trong outer RecyclerView. */
    private final class ModuleReorderCallback extends ItemTouchHelper.Callback {
        private boolean moved;

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                              RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
            java.util.Collections.swap(modules, from, to);
            adapter.notifyItemMoved(from, to);
            moved = true;
            return true;
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (moved) {
                persistModuleOrder();
                moved = false;
            }
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        }
    }
}
