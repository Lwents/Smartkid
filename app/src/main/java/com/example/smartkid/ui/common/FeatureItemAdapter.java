package com.example.smartkid.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.data.model.FeatureItem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adapter dùng lại cho danh sách dữ liệu API, không tạo dữ liệu mẫu. */
public class FeatureItemAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<FeatureItem> source = new ArrayList<>();
    private final List<FeatureItem> displayed = new ArrayList<>();

    public FeatureItemAdapter(Context context) {
        this.context = context.getApplicationContext();
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<FeatureItem> items) {
        source.clear();
        if (items != null) source.addAll(items);
        filter("");
    }

    public void filter(String keyword) {
        displayed.clear();
        String query = normalize(keyword);
        for (FeatureItem item : source) {
            if (item == null) continue;
            String content = normalize(item.getTitle() + " " + item.getSubtitle()
                    + " " + item.getDetail() + " " + item.getStatus());
            if (query.isEmpty() || content.contains(query)) displayed.add(item);
        }
        notifyDataSetChanged();
    }

    @Override public int getCount() { return displayed.size(); }

    @Override
    public FeatureItem getItem(int position) {
        return position >= 0 && position < displayed.size() ? displayed.get(position) : null;
    }

    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        try {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_feature, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            FeatureItem item = getItem(position);
            if (item == null) throw new IllegalStateException("Mục dữ liệu không hợp lệ");
            holder.title.setText(item.getTitle());
            bindOptional(holder.subtitle, item.getSubtitle());
            bindOptional(holder.detail, item.getDetail());
            bindOptional(holder.status, item.getStatus());
        } catch (Exception exception) {
            AppLogger.error(context, "FeatureItemAdapter", "Không thể hiển thị mục", exception);
            if (convertView == null) {
                TextView fallback = new TextView(parent.getContext());
                fallback.setPadding(16, 16, 16, 16);
                fallback.setText(R.string.unknown_error);
                convertView = fallback;
            }
        }
        return convertView;
    }

    private void bindOptional(TextView view, String value) {
        view.setText(value);
        view.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private static final class ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView detail;
        final TextView status;

        ViewHolder(View view) {
            title = view.findViewById(R.id.textFeatureTitle);
            subtitle = view.findViewById(R.id.textFeatureSubtitle);
            detail = view.findViewById(R.id.textFeatureDetail);
            status = view.findViewById(R.id.textFeatureStatus);
        }
    }
}
