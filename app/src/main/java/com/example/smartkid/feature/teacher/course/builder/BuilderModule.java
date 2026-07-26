package com.example.smartkid.feature.teacher.course.builder;

import com.example.smartkid.data.model.FeatureItem;

import java.util.ArrayList;
import java.util.List;

/** Một chương trong builder: giữ id/tiêu đề và danh sách bài học đã nạp. */
final class BuilderModule {
    final String id;
    String title;
    final List<FeatureItem> lessons = new ArrayList<>();

    BuilderModule(String id, String title) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
    }

    BuilderModule(FeatureItem item) {
        this(item == null ? "" : item.getId(), item == null ? "" : item.getTitle());
    }
}
