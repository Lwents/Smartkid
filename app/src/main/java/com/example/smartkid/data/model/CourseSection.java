package com.example.smartkid.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Một chương của khóa học, kèm các bài học thuộc chương đó.
 * Máy chủ trả về trong trường "sections" của API chi tiết khóa học.
 */
public class CourseSection {
    private final String id;
    private final String title;
    private final List<Lesson> lessons;

    public CourseSection(String id, String title, List<Lesson> lessons) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.lessons = lessons == null ? new ArrayList<>() : new ArrayList<>(lessons);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Lesson> getLessons() {
        return Collections.unmodifiableList(lessons);
    }
}
