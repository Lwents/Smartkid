package com.example.smartkid.feature.teacher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

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
import com.example.smartkid.data.repository.ManagementRepository;
import com.example.smartkid.common.ui.form.ContentFormActivity;
import com.example.smartkid.common.ui.form.ExerciseScope;
import com.example.smartkid.feature.teacher.course.TeacherLessonCreateActivity;
import com.example.smartkid.feature.teacher.course.TeacherModuleCreateActivity;
import com.example.smartkid.feature.teacher.exercise.TeacherExerciseEditorActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quản lý chuỗi chương -> bài học -> bài luyện tập của một khóa học. */
public final class TeacherCourseContentActivity extends BaseActivity {
    public static final String EXTRA_COURSE_ID = "teacher_content_course_id";
    public static final String EXTRA_COURSE_TITLE = "teacher_content_course_title";

    private ManagementRepository repository;
    private ProgressBar progress;
    private TextView status;
    private TextView empty;
    private LinearLayout modulesContainer;
    private String courseId;
    private String courseTitle;
    private int loadGeneration;
    private int nextModulePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (!isTeacher()) {
                showErrorDialog(getString(R.string.teacher_permission_required));
                finish();
                return;
            }
            setContentView(R.layout.teacher_activity_course_content);
            LiquidGlassUi.useStatusBarBackdrop(this, R.id.teacherCourseContentRoot,
                    R.drawable.admin_bg_screen, true);
            courseId = safe(getIntent() == null ? null
                    : getIntent().getStringExtra(EXTRA_COURSE_ID));
            courseTitle = safe(getIntent() == null ? null
                    : getIntent().getStringExtra(EXTRA_COURSE_TITLE));
            if (courseId.isEmpty()) {
                showErrorDialog(getString(R.string.invalid_course));
                finish();
                return;
            }
            repository = new ManagementRepository(this);
            bindViews();
            bindHeader();
            findViewById(R.id.buttonTeacherAddModule).setOnClickListener(view ->
                    openCreate("teacher_modules", courseId, courseId, courseTitle,
                            nextModulePosition));
            loadModules();
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherCourseContentActivity",
                    "Không thể mở nội dung khóa học", exception);
            showErrorDialog(getString(R.string.teacher_course_content_open_error));
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (repository != null && !courseId.isEmpty()) loadModules();
    }

    private boolean isTeacher() {
        return UserRole.fromString(new SessionManager(this).getUser().getRole()).isTeacher();
    }

    private void bindViews() {
        progress = findViewById(R.id.progressTeacherCourseContent);
        status = findViewById(R.id.textTeacherCourseContentStatus);
        empty = findViewById(R.id.textTeacherCourseContentEmpty);
        modulesContainer = findViewById(R.id.containerTeacherCourseModules);
        if (progress == null || status == null || empty == null || modulesContainer == null) {
            throw new IllegalStateException("Màn nội dung khóa học thiếu thành phần bắt buộc");
        }
    }

    private void bindHeader() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarTeacherCourseContent);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.setTitle("");
        if (!courseTitle.isEmpty()) {
            ((TextView) findViewById(R.id.textTeacherCourseContentTitle)).setText(courseTitle);
        }
    }

    private void loadModules() {
        int generation = ++loadGeneration;
        setLoading(true);
        status.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        modulesContainer.removeAllViews();
        repository.load("content/courses/" + courseId + "/modules/",
                new ApiCallback<List<FeatureItem>>() {
                    @Override
                    public void onSuccess(List<FeatureItem> modules) {
                        if (!isUsable() || generation != loadGeneration) return;
                        setLoading(false);
                        empty.setVisibility(modules == null || modules.isEmpty()
                                ? View.VISIBLE : View.GONE);
                        nextModulePosition = nextPosition(modules);
                        if (modules == null) return;
                        for (int index = 0; index < modules.size(); index++) {
                            renderModule(modules.get(index), index, generation);
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable() || generation != loadGeneration) return;
                        setLoading(false);
                        showStatus(error == null ? getString(R.string.unknown_error)
                                : error.getMessage());
                    }
                });
    }

    private void renderModule(FeatureItem module, int index, int generation) {
        if (module == null || module.getId().isEmpty()) return;
        View row = LayoutInflater.from(this).inflate(
                R.layout.teacher_item_course_module, modulesContainer, false);
        TextView title = row.findViewById(R.id.textTeacherModuleTitle);
        TextView meta = row.findViewById(R.id.textTeacherModuleMeta);
        LinearLayout lessons = row.findViewById(R.id.containerTeacherModuleLessons);
        title.setText(module.getTitle());
        meta.setText(R.string.teacher_loading_lessons);
        row.findViewById(R.id.buttonTeacherAddLesson).setEnabled(false);
        modulesContainer.addView(row);
        loadLessons(module, index, generation, meta, lessons);
    }

    private void loadLessons(FeatureItem module, int moduleIndex, int generation,
                             TextView meta, LinearLayout container) {
        repository.load("content/modules/" + module.getId() + "/lessons/",
                new ApiCallback<List<FeatureItem>>() {
                    @Override
                    public void onSuccess(List<FeatureItem> lessons) {
                        if (!isUsable() || generation != loadGeneration) return;
                        container.removeAllViews();
                        int count = lessons == null ? 0 : lessons.size();
                        meta.setText(getResources().getQuantityString(
                                R.plurals.teacher_lesson_count, count, count));
                        View moduleRow = (View) container.getParent().getParent();
                        View addLesson = moduleRow.findViewById(R.id.buttonTeacherAddLesson);
                        addLesson.setEnabled(true);
                        addLesson.setOnClickListener(view -> openCreate("teacher_lessons",
                                module.getId(), courseId, module.getTitle(),
                                nextPosition(lessons)));
                        if (lessons == null) return;
                        for (int lessonIndex = 0; lessonIndex < lessons.size(); lessonIndex++) {
                            renderLesson(container, lessons.get(lessonIndex), moduleIndex,
                                    lessonIndex, generation);
                        }
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable() || generation != loadGeneration) return;
                        meta.setText(error == null ? getString(R.string.teacher_lessons_load_error)
                                : error.getMessage());
                    }
                });
    }

    private void renderLesson(LinearLayout container, FeatureItem lesson,
                              int moduleIndex, int lessonIndex, int generation) {
        if (lesson == null || lesson.getId().isEmpty()) return;
        View row = LayoutInflater.from(this).inflate(
                R.layout.teacher_item_course_lesson, container, false);
        TextView title = row.findViewById(R.id.textTeacherLessonTitle);
        TextView meta = row.findViewById(R.id.textTeacherLessonMeta);
        TextView exerciseStatus = row.findViewById(R.id.textTeacherLessonExerciseStatus);
        ImageView icon = row.findViewById(R.id.imageTeacherLessonType);
        MaterialButton exerciseButton = row.findViewById(R.id.buttonTeacherLessonExercise);
        String type = SafeJson.string(lesson.getSource(), "lesson", "content_type", "type");
        boolean published = SafeJson.bool(lesson.getSource(), false, "published");
        title.setText(lesson.getTitle());
        meta.setText(getString(R.string.teacher_lesson_meta_format,
                typeLabel(type), published ? getString(R.string.status_published)
                        : getString(R.string.status_draft), moduleIndex + 1, lessonIndex + 1));
        icon.setImageResource(type.contains("video") ? R.drawable.role_ic_course
                : type.contains("exercise") ? R.drawable.role_ic_exam
                : R.drawable.admin_ic_course);
        exerciseStatus.setText(R.string.teacher_exercise_loading_status);
        exerciseButton.setText(R.string.teacher_exercise_loading_button);
        exerciseButton.setEnabled(false);
        container.addView(row);
        loadLessonExercises(lesson, row, generation);
    }

    private void loadLessonExercises(FeatureItem lesson, View lessonRow, int generation) {
        if (lesson == null || lesson.getId().isEmpty()) return;
        TextView exerciseStatus = lessonRow.findViewById(
                R.id.textTeacherLessonExerciseStatus);
        MaterialButton exerciseButton = lessonRow.findViewById(
                R.id.buttonTeacherLessonExercise);
        exerciseStatus.setText(R.string.teacher_exercise_loading_status);
        exerciseButton.setText(R.string.teacher_exercise_loading_button);
        exerciseButton.setEnabled(false);
        exerciseButton.setOnClickListener(null);

        repository.load("activities/exercises/?lesson_id=" + lesson.getId(),
                new ApiCallback<List<FeatureItem>>() {
                    @Override
                    public void onSuccess(List<FeatureItem> exercises) {
                        if (!isUsable() || generation != loadGeneration) return;
                        bindLessonExercises(lesson, exerciseStatus, exerciseButton,
                                matchingLessonExercises(lesson.getId(), exercises));
                    }

                    @Override
                    public void onError(ApiError error) {
                        if (!isUsable() || generation != loadGeneration) return;
                        exerciseStatus.setText(error == null || safe(error.getMessage()).isEmpty()
                                ? getString(R.string.teacher_exercise_load_error)
                                : error.getMessage());
                        exerciseButton.setText(R.string.teacher_retry_exercise_button);
                        exerciseButton.setEnabled(true);
                        exerciseButton.setOnClickListener(view -> {
                            view.setEnabled(false);
                            loadLessonExercises(lesson, lessonRow, generation);
                        });
                    }
                });
    }

    private List<FeatureItem> matchingLessonExercises(String lessonId,
                                                      List<FeatureItem> exercises) {
        List<FeatureItem> matches = new ArrayList<>();
        String expectedLessonId = safe(lessonId);
        if (expectedLessonId.isEmpty() || exercises == null) return matches;
        for (FeatureItem exercise : exercises) {
            if (exercise == null) continue;
            Object lessonValue = exercise.getSource().opt("lesson");
            String linkedLessonId = "";
            if (lessonValue instanceof JSONObject) {
                linkedLessonId = SafeJson.string(
                        (JSONObject) lessonValue, "", "id", "uuid");
            } else if (lessonValue != null && lessonValue != JSONObject.NULL) {
                linkedLessonId = safe(String.valueOf(lessonValue));
            }
            if (expectedLessonId.equals(linkedLessonId)) matches.add(exercise);
        }
        return matches;
    }

    private void bindLessonExercises(FeatureItem lesson, TextView exerciseStatus,
                                     MaterialButton exerciseButton,
                                     List<FeatureItem> exercises) {
        int count = exercises == null ? 0 : exercises.size();
        if (count == 0) {
            exerciseStatus.setText(R.string.teacher_exercise_none);
            exerciseButton.setText(R.string.teacher_add_exercise_button);
            exerciseButton.setEnabled(true);
            exerciseButton.setOnClickListener(view -> {
                view.setEnabled(false);
                if (!openCreate("teacher_exercises", lesson.getId(), courseId,
                        lesson.getTitle(), 0, "")) {
                    view.setEnabled(true);
                }
            });
            return;
        }

        exerciseButton.setText(R.string.teacher_edit_exercise_button);
        exerciseButton.setEnabled(true);
        if (count == 1) {
            FeatureItem exercise = exercises.get(0);
            exerciseStatus.setText(getString(R.string.teacher_exercise_summary_format,
                    exercise.getTitle(), exerciseStatusLabel(exercise)));
            exerciseButton.setOnClickListener(view -> {
                view.setEnabled(false);
                if (!openExerciseEditor(lesson, exercise)) view.setEnabled(true);
            });
            return;
        }

        exerciseStatus.setText(getString(R.string.teacher_exercise_multiple_format, count));
        exerciseButton.setOnClickListener(view -> showExercisePicker(
                lesson, exercises, exerciseButton));
    }

    private void showExercisePicker(FeatureItem lesson, List<FeatureItem> exercises,
                                    MaterialButton exerciseButton) {
        if (exercises == null || exercises.isEmpty()) return;
        String[] labels = new String[exercises.size()];
        for (int index = 0; index < exercises.size(); index++) {
            FeatureItem exercise = exercises.get(index);
            labels[index] = exercise.getTitle() + " • " + exerciseStatusLabel(exercise);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.teacher_exercise_picker_title)
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= exercises.size()) return;
                    exerciseButton.setEnabled(false);
                    if (!openExerciseEditor(lesson, exercises.get(which))) {
                        exerciseButton.setEnabled(true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String exerciseStatusLabel(FeatureItem exercise) {
        boolean published = exercise != null && SafeJson.bool(
                exercise.getSource(), false, "published");
        return getString(published ? R.string.status_published : R.string.status_draft);
    }

    private boolean openExerciseEditor(FeatureItem lesson, FeatureItem exercise) {
        if (lesson == null || exercise == null || exercise.getId().isEmpty()) return false;
        return openCreate("teacher_exercises", lesson.getId(), courseId,
                lesson.getTitle(), 0, exercise.getId());
    }

    private boolean openCreate(String kind, String parentId, String linkedCourseId,
                               String parentTitle, int position) {
        return openCreate(kind, parentId, linkedCourseId, parentTitle, position, "");
    }

    private boolean openCreate(String kind, String parentId, String linkedCourseId,
                               String parentTitle, int position, String editId) {
        try {
            Intent intent;
            if ("teacher_modules".equals(kind)) {
                intent = new Intent(this, TeacherModuleCreateActivity.class);
            } else if ("teacher_lessons".equals(kind)) {
                intent = new Intent(this, TeacherLessonCreateActivity.class);
            } else {
                intent = new Intent(this, TeacherExerciseEditorActivity.class);
                intent.putExtra(TeacherExerciseEditorActivity.EXTRA_SCOPE,
                        ExerciseScope.LESSON_EXERCISE.name());
            }
            intent.putExtra(ContentFormActivity.EXTRA_PARENT_ID, parentId);
            intent.putExtra(ContentFormActivity.EXTRA_PARENT_TITLE, parentTitle);
            intent.putExtra(ContentFormActivity.EXTRA_COURSE_ID, linkedCourseId);
            intent.putExtra(ContentFormActivity.EXTRA_POSITION, position);
            if (!safe(editId).isEmpty()) {
                intent.putExtra(ContentFormActivity.EXTRA_EDIT_ID, editId);
            }
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            AppLogger.error(this, "TeacherCourseContentActivity",
                    "Không thể mở biểu mẫu nội dung", exception);
            showErrorDialog(getString(R.string.management_create_open_error));
            return false;
        }
    }

    private String typeLabel(String value) {
        String type = safe(value).toLowerCase(Locale.ROOT);
        if (type.contains("video")) return getString(R.string.teacher_content_type_video);
        if (type.contains("pdf")) return getString(R.string.teacher_content_type_pdf);
        if (type.contains("document")) return getString(R.string.teacher_content_type_document);
        if (type.contains("exercise")) return getString(R.string.teacher_content_type_exercise);
        if (type.contains("exploration")) return getString(R.string.teacher_content_type_exploration);
        return getString(R.string.teacher_content_type_text);
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonTeacherAddModule).setEnabled(!loading);
    }

    private void showStatus(String message) {
        status.setText(message == null ? getString(R.string.unknown_error) : message);
        status.setVisibility(View.VISIBLE);
    }

    private boolean isUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private static int nextPosition(List<FeatureItem> items) {
        int maxPosition = -1;
        if (items != null) {
            for (int index = 0; index < items.size(); index++) {
                FeatureItem item = items.get(index);
                if (item == null) continue;
                maxPosition = Math.max(maxPosition,
                        SafeJson.integer(item.getSource(), index, "position"));
            }
        }
        return maxPosition + 1;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
