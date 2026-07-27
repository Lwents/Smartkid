package com.example.smartkid.feature.teacher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.smartkid.R;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.model.FeatureItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Teacher-focused question cards with pending/answered state. */
final class TeacherQuestionAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeatureItem> source = new ArrayList<>();
    private final List<FeatureItem> displayed = new ArrayList<>();

    TeacherQuestionAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    void setItems(List<FeatureItem> items) {
        source.clear();
        if (items != null) source.addAll(items);
        filter("");
    }

    void filter(String keyword) {
        displayed.clear();
        String query = normalize(keyword);
        for (FeatureItem item : source) {
            if (item == null) continue;
            JSONObject sourceItem = item.getSource();
            String searchable = TeacherQuestionUiFormatter.studentName(sourceItem) + " "
                    + SafeJson.string(sourceItem, "", "course_title") + " "
                    + SafeJson.string(sourceItem, "", "lesson_title") + " "
                    + SafeJson.string(sourceItem, item.getDetail(), "content");
            if (query.isEmpty() || normalize(searchable).contains(query)) displayed.add(item);
        }
        notifyDataSetChanged();
    }

    int getPendingCount() {
        int count = 0;
        for (FeatureItem item : source) {
            if (item == null) continue;
            if (TeacherQuestionUiFormatter.teacherReplyCount(
                    SafeJson.array(item.getSource(), "replies")) == 0) count++;
        }
        return count;
    }

    int getTotalCount() { return source.size(); }

    @Override public int getCount() { return displayed.size(); }

    @Override
    public FeatureItem getItem(int position) {
        return position >= 0 && position < displayed.size() ? displayed.get(position) : null;
    }

    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.teacher_item_lesson_question, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FeatureItem item = getItem(position);
        if (item == null) return convertView;
        JSONObject sourceItem = item.getSource();
        JSONArray replies = SafeJson.array(sourceItem, "replies");
        int teacherReplies = TeacherQuestionUiFormatter.teacherReplyCount(replies);

        holder.avatar.setText(TeacherQuestionUiFormatter.studentInitial(sourceItem));
        holder.student.setText(TeacherQuestionUiFormatter.studentName(sourceItem));
        holder.time.setText(TeacherQuestionUiFormatter.timeLabel(sourceItem));
        holder.question.setText(SafeJson.string(sourceItem, item.getDetail(), "content"));
        holder.context.setText(TeacherQuestionUiFormatter.contextLabel(sourceItem));
        holder.status.setText(TeacherQuestionUiFormatter.statusLabel(sourceItem));
        holder.status.setBackgroundResource(teacherReplies == 0
                ? R.drawable.teacher_bg_question_pending
                : R.drawable.teacher_bg_question_answered);
        holder.status.setTextColor(ContextCompat.getColor(context, teacherReplies == 0
                ? R.color.teacher_question_pending_text
                : R.color.teacher_question_answered_text));
        holder.action.setText(teacherReplies == 0 ? "Trả lời ngay" : "Xem cuộc trò chuyện");
        return convertView;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private static final class ViewHolder {
        final TextView avatar;
        final TextView student;
        final TextView time;
        final TextView question;
        final TextView context;
        final TextView status;
        final TextView action;

        ViewHolder(View view) {
            avatar = view.findViewById(R.id.textTeacherQuestionAvatar);
            student = view.findViewById(R.id.textTeacherQuestionStudent);
            time = view.findViewById(R.id.textTeacherQuestionTime);
            question = view.findViewById(R.id.textTeacherQuestionContent);
            context = view.findViewById(R.id.textTeacherQuestionContext);
            status = view.findViewById(R.id.textTeacherQuestionStatus);
            action = view.findViewById(R.id.textTeacherQuestionAction);
        }
    }
}
