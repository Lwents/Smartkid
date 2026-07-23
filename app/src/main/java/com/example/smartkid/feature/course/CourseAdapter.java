package com.example.smartkid.feature.course;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.Course;
import com.example.smartkid.domain.BusinessRules;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class CourseAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<Course> allCourses = new ArrayList<>();
    private final List<Course> displayedCourses = new ArrayList<>();
    private String currentKeyword = "";
    private boolean ascending = true;

    public CourseAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.inflater = LayoutInflater.from(context);
    }

    public void setCourses(List<Course> courses) {
        allCourses.clear();
        if (courses != null) {
            allCourses.addAll(courses);
        }
        applyFilterAndSort();
    }

    public void filter(String keyword) {
        currentKeyword = keyword == null ? "" : keyword;
        applyFilterAndSort();
    }

    public boolean toggleSort() {
        ascending = !ascending;
        applyFilterAndSort();
        return ascending;
    }

    @Override
    public int getCount() {
        return displayedCourses.size();
    }

    @Override
    public Course getItem(int position) {
        return position >= 0 && position < displayedCourses.size()
                ? displayedCourses.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        try {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.course_item_course, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Course course = getItem(position);
            if (course == null) {
                holder.title.setText(R.string.invalid_course);
                return convertView;
            }
            holder.title.setText(course.getTitle());
            String meta = course.getSubject();
            if (!course.getGrade().isEmpty()) {
                meta += meta.isEmpty() ? course.getGrade() : " • " + course.getGrade();
            }
            holder.meta.setText(meta.isEmpty() ? context.getString(R.string.updating) : meta);
            holder.teacher.setText(course.getTeacherName().isEmpty()
                    ? context.getString(R.string.teacher_updating)
                    : context.getString(R.string.teacher_format, course.getTeacherName()));
            holder.progress.setProgress(course.getProgress());
            holder.progressText.setText(context.getString(R.string.progress_percent,
                    course.getProgress()));
            holder.lessonCount.setText(context.getResources().getQuantityString(
                    R.plurals.lesson_count, course.getLessonsCount(),
                    course.getLessonsCount()));

            holder.thumbnail.setImageResource(R.drawable.course_ic_placeholder);
            if (!course.getThumbnailUrl().isEmpty()) {
                Picasso.get()
                        .load(course.getThumbnailUrl())
                        .placeholder(R.drawable.course_ic_placeholder)
                        .error(R.drawable.course_ic_placeholder)
                        .fit()
                        .centerCrop()
                        .into(holder.thumbnail);
            }
        } catch (Exception exception) {
            AppLogger.error(context, "CourseAdapter", "Không thể hiển thị khóa học", exception);
            if (convertView == null) {
                convertView = new TextView(parent.getContext());
                ((TextView) convertView).setText(R.string.invalid_course);
            }
        }
        return convertView;
    }

    private void applyFilterAndSort() {
        displayedCourses.clear();
        List<Course> filtered = BusinessRules.filterCourses(allCourses, currentKeyword);
        displayedCourses.addAll(BusinessRules.sortCoursesByTitle(filtered, ascending));
        notifyDataSetChanged();
    }

    private static class ViewHolder {
        final ImageView thumbnail;
        final TextView title;
        final TextView meta;
        final TextView teacher;
        final TextView lessonCount;
        final TextView progressText;
        final ProgressBar progress;

        ViewHolder(View view) {
            thumbnail = view.findViewById(R.id.imageCourseThumbnail);
            title = view.findViewById(R.id.textCourseTitle);
            meta = view.findViewById(R.id.textCourseMeta);
            teacher = view.findViewById(R.id.textCourseTeacher);
            lessonCount = view.findViewById(R.id.textLessonCount);
            progressText = view.findViewById(R.id.textCourseProgress);
            progress = view.findViewById(R.id.progressCourse);
        }
    }
}
