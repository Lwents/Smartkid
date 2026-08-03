package com.example.smartkid.feature.shared.notification;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.smartkid.R;
import com.example.smartkid.data.model.FeatureItem;
import com.google.android.material.card.MaterialCardView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter hiển thị, tìm kiếm và phân biệt trạng thái đã đọc của danh sách thông báo. */
final class NotificationItemAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeatureItem> source = new ArrayList<>();
    private final List<FeatureItem> displayed = new ArrayList<>();

    NotificationItemAdapter(Context context) {
        this.context = context.getApplicationContext();
        this.inflater = LayoutInflater.from(context);
    }

    /** Thay dữ liệu gốc rồi áp dụng lại bộ lọc để đồng bộ danh sách đang hiển thị. */
    void setItems(List<FeatureItem> items) {
        source.clear();
        if (items != null) source.addAll(items);
        filter("");
    }

    /** Tìm thông báo theo tiêu đề, nội dung, loại và ngữ cảnh sau khi đã bỏ dấu. */
    void filter(String keyword) {
        displayed.clear();
        String query = normalize(keyword);
        for (FeatureItem item : source) {
            if (item == null) continue;
            String content = normalize(item.getTitle() + " " + item.getDetail() + " "
                    + NotificationUiFormatter.categoryLabel(item.getSource()) + " "
                    + NotificationUiFormatter.contextLabel(item.getSource(), item.getTitle()));
            if (query.isEmpty() || content.contains(query)) displayed.add(item);
        }
        notifyDataSetChanged();
    }

    /** Đếm thông báo chưa đọc trên dữ liệu gốc, không phụ thuộc từ khóa đang lọc. */
    int getUnreadCount() {
        int count = 0;
        for (FeatureItem item : source) {
            if (item != null && !NotificationUiFormatter.isRead(item.getSource())) count++;
        }
        return count;
    }

    @Override public int getCount() { return displayed.size(); }

    @Override public FeatureItem getItem(int position) {
        return position >= 0 && position < displayed.size() ? displayed.get(position) : null;
    }

    @Override public long getItemId(int position) { return position; }

    /** Tái sử dụng view cũ và gắn màu, biểu tượng, nội dung phù hợp cho từng thông báo. */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.notification_item, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FeatureItem item = getItem(position);
        if (item == null) return convertView;
        boolean read = NotificationUiFormatter.isRead(item.getSource());
        String category = NotificationUiFormatter.categoryLabel(item.getSource());
        String displayTitle = NotificationUiFormatter.displayTitle(
                item.getSource(), item.getTitle());
        holder.title.setText(displayTitle);
        holder.message.setText(item.getDetail());
        holder.category.setText(category);
        holder.time.setText(NotificationUiFormatter.timeLabel(item.getSource()));
        String contextLabel = NotificationUiFormatter.contextLabel(
                item.getSource(), item.getTitle());
        holder.context.setText(contextLabel.replace("\n", " • "));
        holder.context.setVisibility(contextLabel.isEmpty() ? View.GONE : View.VISIBLE);
        holder.unreadDot.setVisibility(read ? View.INVISIBLE : View.VISIBLE);
        holder.title.setTextColor(ContextCompat.getColor(context,
                read ? R.color.smartkid_text : R.color.notification_unread_title));
        holder.card.setCardBackgroundColor(ContextCompat.getColor(context,
                read ? R.color.notification_read_surface : R.color.notification_unread_surface));
        holder.card.setStrokeColor(ContextCompat.getColor(context,
                read ? R.color.notification_read_stroke : R.color.notification_unread_stroke));
        holder.card.setStrokeWidth(read ? 1 : 2);
        String rawCategory = item.getSource().optString("category");
        holder.icon.setImageResource("lesson_question_reply".equals(rawCategory)
                ? R.drawable.notification_ic_reply
                : "exam".equals(rawCategory) ? R.drawable.common_ic_nav_exam
                : R.drawable.notification_ic_notifications);
        holder.itemView.setContentDescription((read ? "Đã đọc. " : "Chưa đọc. ")
                + displayTitle + ". " + item.getDetail());
        return convertView;
    }

    /** Chuẩn hóa chuỗi tìm kiếm: bỏ dấu, đổi về chữ thường và loại khoảng trắng thừa. */
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private static final class ViewHolder {
        final View itemView;
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final TextView message;
        final TextView category;
        final TextView time;
        final TextView context;
        final View unreadDot;

        ViewHolder(View view) {
            itemView = view;
            card = view.findViewById(R.id.cardNotification);
            icon = view.findViewById(R.id.imageNotificationIcon);
            title = view.findViewById(R.id.textNotificationTitle);
            message = view.findViewById(R.id.textNotificationMessage);
            category = view.findViewById(R.id.textNotificationCategory);
            time = view.findViewById(R.id.textNotificationTime);
            context = view.findViewById(R.id.textNotificationContext);
            unreadDot = view.findViewById(R.id.viewNotificationUnread);
        }
    }
}
