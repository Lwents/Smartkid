package com.example.smartkid.ui.courses;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.model.Lesson;

import java.util.ArrayList;
import java.util.List;

public class LessonAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Lesson> lessons = new ArrayList<>();

    public LessonAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setLessons(List<Lesson> values) {
        lessons.clear();
        if (values != null) {
            lessons.addAll(values);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return lessons.size();
    }

    @Override
    public Lesson getItem(int position) {
        return position >= 0 && position < lessons.size() ? lessons.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_lesson, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Lesson lesson = getItem(position);
            if (lesson == null) {
                holder.title.setText(R.string.invalid_lesson);
                holder.type.setText("");
                holder.status.setText("");
                return convertView;
            }
            holder.title.setText(lesson.getTitle());
            holder.type.setText(displayType(lesson.getType()));
            holder.status.setText(lesson.isCompleted()
                    ? R.string.lesson_completed : R.string.lesson_not_completed);
            holder.status.setSelected(lesson.isCompleted());
        } catch (Exception exception) {
            AppLogger.error(parent.getContext(), "LessonAdapter",
                    "Không thể hiển thị bài học", exception);
            if (convertView == null) {
                TextView fallback = new TextView(parent.getContext());
                fallback.setText(R.string.invalid_lesson);
                convertView = fallback;
            }
        }
        return convertView;
    }

    private String displayType(String type) {
        if ("video".equalsIgnoreCase(type)) {
            return "Video";
        }
        if ("quiz".equalsIgnoreCase(type) || "exercise".equalsIgnoreCase(type)) {
            return "Bài tập";
        }
        if ("pdf".equalsIgnoreCase(type) || "doc".equalsIgnoreCase(type)) {
            return "Tài liệu";
        }
        return "Nội dung đọc";
    }

    private static class ViewHolder {
        final TextView title;
        final TextView type;
        final TextView status;

        ViewHolder(View view) {
            title = view.findViewById(R.id.textLessonTitle);
            type = view.findViewById(R.id.textLessonType);
            status = view.findViewById(R.id.textLessonStatus);
            if (title == null || type == null || status == null) {
                throw new IllegalStateException("Layout bài học thiếu thành phần bắt buộc");
            }
        }
    }
}
