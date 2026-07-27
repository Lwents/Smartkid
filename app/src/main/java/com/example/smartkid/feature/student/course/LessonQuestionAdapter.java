package com.example.smartkid.feature.student.course;

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

import java.util.ArrayList;
import java.util.List;

/** Student-friendly cards for lesson questions. */
final class LessonQuestionAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final Context context;
    private final List<FeatureItem> items = new ArrayList<>();

    LessonQuestionAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    void setItems(List<FeatureItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }

    @Override
    public FeatureItem getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.course_item_lesson_question, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FeatureItem item = getItem(position);
        if (item == null) return convertView;
        JSONObject source = item.getSource();
        boolean owner = SafeJson.bool(source, false, "is_owner");
        JSONArray replies = SafeJson.array(source, "replies");
        int teacherReplies = LessonDiscussionUiFormatter.teacherReplyCount(replies);
        int likes = SafeJson.integer(source, 0, "reactions_count");

        holder.avatar.setText(owner ? "Em" : "Bạn");
        holder.avatar.setBackgroundResource(owner
                ? R.drawable.discussion_bg_avatar_student
                : R.drawable.discussion_bg_avatar_friend);
        holder.author.setText(LessonDiscussionUiFormatter.questionAuthor(source));
        holder.time.setText(LessonDiscussionUiFormatter.timeLabel(source));
        holder.time.setVisibility(holder.time.getText().length() == 0 ? View.GONE : View.VISIBLE);
        holder.content.setText(item.getDetail());
        holder.status.setText(LessonDiscussionUiFormatter.questionStatus(source));
        holder.status.setBackgroundResource(teacherReplies > 0
                ? R.drawable.discussion_bg_status_answered
                : R.drawable.discussion_bg_status_pending);
        holder.status.setTextColor(ContextCompat.getColor(context, teacherReplies > 0
                ? R.color.discussion_answered_text
                : R.color.discussion_pending_text));
        holder.replyHint.setText(replies.length() == 0
                ? context.getString(R.string.lesson_open_conversation)
                : context.getResources().getQuantityString(
                        R.plurals.lesson_reply_count,
                        replies.length(),
                        replies.length()
                ));
        holder.likes.setText(context.getResources().getQuantityString(
                R.plurals.lesson_like_count,
                likes,
                likes
        ));
        holder.likes.setVisibility(likes > 0 ? View.VISIBLE : View.GONE);
        return convertView;
    }

    private static final class ViewHolder {
        final TextView avatar;
        final TextView author;
        final TextView time;
        final TextView content;
        final TextView status;
        final TextView likes;
        final TextView replyHint;

        ViewHolder(View view) {
            avatar = view.findViewById(R.id.textLessonQuestionAvatar);
            author = view.findViewById(R.id.textLessonQuestionAuthor);
            time = view.findViewById(R.id.textLessonQuestionTime);
            content = view.findViewById(R.id.textLessonQuestionContent);
            status = view.findViewById(R.id.textLessonQuestionStatus);
            likes = view.findViewById(R.id.textLessonQuestionLikes);
            replyHint = view.findViewById(R.id.textLessonQuestionReplyHint);
        }
    }
}
