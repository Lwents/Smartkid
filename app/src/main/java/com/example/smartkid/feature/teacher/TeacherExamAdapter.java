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

/** Teacher exam cards with state, settings and result counts visible before opening actions. */
final class TeacherExamAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeatureItem> source = new ArrayList<>();
    private final List<FeatureItem> displayed = new ArrayList<>();

    TeacherExamAdapter(Context context) {
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
            String content = normalize(item.getTitle() + " " + item.getSubtitle()
                    + " " + item.getStatus());
            if (query.isEmpty() || content.contains(query)) displayed.add(item);
        }
        notifyDataSetChanged();
    }

    int getTotalCount() { return source.size(); }

    int getPublishedCount() {
        int count = 0;
        for (FeatureItem item : source) {
            if (item != null && item.getSource().optBoolean("published", false)) count++;
        }
        return count;
    }

    @Override public int getCount() { return displayed.size(); }

    @Override public FeatureItem getItem(int position) {
        return position >= 0 && position < displayed.size() ? displayed.get(position) : null;
    }

    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.teacher_item_exam, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        FeatureItem item = getItem(position);
        if (item == null) return convertView;
        JSONObject sourceItem = item.getSource();
        JSONArray questions = sourceItem.optJSONArray("questions");
        int questionCount = questions == null ? 0 : questions.length();
        JSONObject settings = sourceItem.optJSONObject("settings");
        int durationSeconds = settings == null ? 0
                : SafeJson.integer(settings, 0, "duration_seconds", "time_limit_seconds");
        int submissions = SafeJson.integer(sourceItem, 0, "submissions");
        boolean published = sourceItem.optBoolean("published", false);

        holder.title.setText(item.getTitle());
        int durationMinutes = Math.max(1, durationSeconds / 60);
        String duration = durationSeconds > 0
                ? context.getResources().getQuantityString(
                        R.plurals.teacher_exam_duration_minutes,
                        durationMinutes,
                        durationMinutes
                )
                : context.getString(R.string.teacher_exam_unlimited);
        holder.meta.setText(context.getResources().getQuantityString(
                R.plurals.teacher_exam_meta_format,
                questionCount,
                questionCount,
                duration,
                questionTypeLabel(SafeJson.string(sourceItem, "mcq", "type"))
        ));
        holder.results.setText(submissions == 0
                ? context.getString(R.string.teacher_exam_no_submissions)
                : context.getResources().getQuantityString(
                        R.plurals.teacher_exam_submission_result,
                        submissions,
                        submissions,
                        formatScore(SafeJson.decimal(
                                sourceItem, 0, "avgScore", "avg_score"))
                ));
        holder.status.setText(published
                ? R.string.teacher_status_open : R.string.teacher_status_draft);
        holder.status.setBackgroundResource(published
                ? R.drawable.teacher_bg_exam_published : R.drawable.teacher_bg_exam_draft);
        holder.status.setTextColor(ContextCompat.getColor(context, published
                ? R.color.teacher_exam_published_text : R.color.teacher_exam_draft_text));
        return convertView;
    }

    private static String questionTypeLabel(String type) {
        if ("short_answer".equals(type)) return "Trả lời ngắn";
        if ("matching".equals(type)) return "Nối cặp";
        return "Trắc nghiệm";
    }

    private static String formatScore(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.US, "%.1f", value);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private static final class Holder {
        final TextView title;
        final TextView meta;
        final TextView results;
        final TextView status;

        Holder(View view) {
            title = view.findViewById(R.id.textTeacherExamTitle);
            meta = view.findViewById(R.id.textTeacherExamMeta);
            results = view.findViewById(R.id.textTeacherExamResults);
            status = view.findViewById(R.id.textTeacherExamStatus);
        }
    }
}
