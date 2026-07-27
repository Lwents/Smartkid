package com.example.smartkid.feature.teacher.course.builder;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;

import java.util.List;
import java.util.Locale;

/** Inner adapter: bài học bên trong một chương. Đồng nhất một loại row để kéo-thả sạch. */
final class BuilderLessonAdapter extends RecyclerView.Adapter<BuilderLessonAdapter.LessonViewHolder> {

    interface Listener {
        void onLessonClicked(String moduleId, FeatureItem lesson);
        void onLessonDragStart(RecyclerView.ViewHolder holder);
    }

    private final String moduleId;
    private final List<FeatureItem> lessons;
    private final Listener listener;

    BuilderLessonAdapter(String moduleId, List<FeatureItem> lessons, Listener listener) {
        this.moduleId = moduleId;
        this.lessons = lessons;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return lessons.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public LessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.teacher_item_builder_lesson, parent, false);
        return new LessonViewHolder(view);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onBindViewHolder(@NonNull LessonViewHolder holder, int position) {
        FeatureItem lesson = lessons.get(position);
        holder.title.setText(lesson.getTitle());
        String type = SafeJson.string(lesson.getSource(), "lesson", "content_type", "type");
        boolean published = SafeJson.bool(lesson.getSource(), false, "published");
        holder.meta.setText(String.format(Locale.getDefault(), "%s • %s",
                typeLabel(holder, type),
                holder.itemView.getContext().getString(published
                        ? R.string.status_published : R.string.status_draft)));
        holder.icon.setImageResource(type.contains("video") ? R.drawable.role_ic_course
                : type.contains("exercise") ? R.drawable.role_ic_exam
                : R.drawable.admin_ic_course);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onLessonClicked(moduleId, lesson);
        });
        holder.handle.setOnClickListener(v -> {
            if (listener != null) listener.onLessonDragStart(holder);
        });
        holder.handle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) {
                v.performClick();
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    private String typeLabel(LessonViewHolder holder, String value) {
        String type = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (type.contains("video")) return holder.itemView.getContext()
                .getString(R.string.teacher_content_type_video);
        if (type.contains("exercise")) return holder.itemView.getContext()
                .getString(R.string.teacher_content_type_exercise);
        if (type.contains("pdf")) return holder.itemView.getContext()
                .getString(R.string.teacher_content_type_pdf);
        if (type.contains("document")) return holder.itemView.getContext()
                .getString(R.string.teacher_content_type_document);
        return holder.itemView.getContext().getString(R.string.teacher_content_type_text);
    }

    static final class LessonViewHolder extends RecyclerView.ViewHolder {
        final ImageView handle;
        final ImageView icon;
        final TextView title;
        final TextView meta;

        LessonViewHolder(@NonNull View itemView) {
            super(itemView);
            handle = itemView.findViewById(R.id.handleTeacherBuilderLesson);
            icon = itemView.findViewById(R.id.imageTeacherBuilderLessonType);
            title = itemView.findViewById(R.id.textTeacherBuilderLessonTitle);
            meta = itemView.findViewById(R.id.textTeacherBuilderLessonMeta);
        }
    }
}
