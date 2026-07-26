package com.example.smartkid.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Khóa học kèm danh sách bài học, dùng cho màn chi tiết khóa học.
 */
public class CourseDetail {
    private final Course course;
    private final List<Lesson> lessons;

    public CourseDetail(Course course, List<Lesson> lessons) {
        this.course = course;
        this.lessons = lessons == null ? new ArrayList<>() : new ArrayList<>(lessons);
    }

    public Course getCourse() {
        return course;
    }

    public List<Lesson> getLessons() {
        return Collections.unmodifiableList(lessons);
    }
}
