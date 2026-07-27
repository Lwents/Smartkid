package com.example.smartkid.feature.student.course;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.smartkid.R;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.data.model.CourseSection;
import com.example.smartkid.data.model.Lesson;

import java.util.ArrayList;
import java.util.List;

/**
 * Danh sách nội dung khóa học, hiển thị đúng cấu trúc giáo viên đã tạo:
 * tiêu đề chương rồi mới tới các bài học của chương đó.
 *
 * Danh sách được làm phẳng thành các "dòng": dòng chương (không bấm được) và
 * dòng bài học (bấm để mở bài).
 */
public class LessonAdapter extends BaseAdapter {

    private static final int LOAI_CHUONG = 0;
    private static final int LOAI_BAI_HOC = 1;

    /** Một dòng trong danh sách: hoặc là tên chương, hoặc là một bài học. */
    private static class Dong {
        final int loai;
        final String tieuDeChuong;
        final int soBai;
        final Lesson baiHoc;
        final boolean daMoKhoa;

        Dong(String tieuDeChuong, int soBai, boolean daMoKhoa) {
            this.loai = LOAI_CHUONG;
            this.tieuDeChuong = tieuDeChuong;
            this.soBai = soBai;
            this.baiHoc = null;
            this.daMoKhoa = daMoKhoa;
        }

        Dong(Lesson baiHoc, boolean daMoKhoa) {
            this.loai = LOAI_BAI_HOC;
            this.tieuDeChuong = null;
            this.soBai = 0;
            this.baiHoc = baiHoc;
            this.daMoKhoa = daMoKhoa;
        }
    }

    private final LayoutInflater inflater;
    private final List<Dong> dongs = new ArrayList<>();

    public LessonAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    /**
     * Đổ dữ liệu theo chương. Luôn hiện tiêu đề chương, kể cả khóa chỉ có một
     * chương, để học sinh thấy đúng cấu trúc chương → bài mà giáo viên đã tạo.
     * Chương không có bài nào thì bỏ qua để không chừa tiêu đề trống.
     */
    public void setSections(List<CourseSection> sections) {
        dongs.clear();
        boolean cacBaiTruocDaHoanThanh = true;
        if (sections != null) {
            for (CourseSection section : sections) {
                if (section.getLessons().isEmpty()) {
                    continue;
                }
                dongs.add(new Dong(section.getTitle(), section.getLessons().size(),
                        cacBaiTruocDaHoanThanh));
                for (Lesson lesson : section.getLessons()) {
                    dongs.add(new Dong(lesson, cacBaiTruocDaHoanThanh));
                    cacBaiTruocDaHoanThanh = cacBaiTruocDaHoanThanh
                            && lesson.isCompleted();
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return dongs.size();
    }

    /** Trả về bài học ở vị trí này; dòng tiêu đề chương trả về null. */
    @Override
    public Lesson getItem(int position) {
        if (position < 0 || position >= dongs.size()) {
            return null;
        }
        return dongs.get(position).baiHoc;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return dongs.get(position).loai;
    }

    /** Dòng tiêu đề chương không cho bấm, chỉ dòng bài học mới mở được. */
    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == LOAI_BAI_HOC
                && dongs.get(position).daMoKhoa;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            Dong dong = dongs.get(position);
            if (dong.loai == LOAI_CHUONG) {
                return taoDongChuong(dong, convertView, parent);
            }
            return taoDongBaiHoc(dong, convertView, parent);
        } catch (Exception exception) {
            AppLogger.error(parent.getContext(), "LessonAdapter",
                    "Không thể hiển thị nội dung khóa học", exception);
            TextView fallback = new TextView(parent.getContext());
            fallback.setText(R.string.invalid_lesson);
            return fallback;
        }
    }

    private View taoDongChuong(Dong dong, View convertView, ViewGroup parent) {
        // convertView tái dùng từ dòng bài học sẽ có tag; khi đó phải dựng lại view.
        if (convertView == null || convertView.getTag() != null) {
            convertView = inflater.inflate(R.layout.course_item_section, parent, false);
        }
        ((TextView) convertView.findViewById(R.id.textSectionTitle)).setText(dong.tieuDeChuong);
        ((TextView) convertView.findViewById(R.id.textSectionCount)).setText(dong.daMoKhoa
                ? parent.getContext().getString(R.string.section_lesson_count, dong.soBai)
                : parent.getContext().getString(R.string.section_locked));
        convertView.setAlpha(dong.daMoKhoa ? 1f : 0.55f);
        return convertView;
    }

    private View taoDongBaiHoc(Dong dong, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null || convertView.getTag() == null) {
            convertView = inflater.inflate(R.layout.course_item_lesson, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        Lesson lesson = dong.baiHoc;
        if (lesson == null) {
            holder.title.setText(R.string.invalid_lesson);
            holder.type.setText("");
            holder.status.setText("");
            return convertView;
        }
        holder.title.setText(lesson.getTitle());
        holder.type.setText(displayType(lesson.getType()));
        if (!dong.daMoKhoa) {
            holder.status.setText(R.string.lesson_locked_status);
            holder.status.setTextColor(parent.getContext().getColor(R.color.smartkid_text_secondary));
        } else if (lesson.isCompleted()) {
            holder.status.setText(R.string.lesson_completed);
            holder.status.setTextColor(parent.getContext().getColor(R.color.smartkid_success));
        } else {
            holder.status.setText(R.string.lesson_not_completed);
            holder.status.setTextColor(parent.getContext().getColor(R.color.smartkid_success));
        }
        convertView.setAlpha(dong.daMoKhoa ? 1f : 0.55f);
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
