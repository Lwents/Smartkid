package com.example.smartkid.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Khóa học kèm nội dung, dùng cho màn chi tiết khóa học.
 *
 * Giữ hai cách nhìn trên cùng một dữ liệu:
 * - getSections(): chia theo chương, để hiển thị đúng cấu trúc giáo viên đã tạo.
 * - getLessons(): danh sách phẳng theo đúng thứ tự học, để tìm bài trước / bài sau.
 */
public class CourseDetail {
    private final Course course;
    private final List<CourseSection> sections;
    private final List<Lesson> lessons;

    public CourseDetail(Course course, List<CourseSection> sections) {
        this.course = course;
        this.sections = sections == null ? new ArrayList<>() : new ArrayList<>(sections);
        this.lessons = new ArrayList<>();
        for (CourseSection section : this.sections) {
            this.lessons.addAll(section.getLessons());
        }
    }

    public Course getCourse() {
        return course;
    }

    public List<CourseSection> getSections() {
        return Collections.unmodifiableList(sections);
    }

    public List<Lesson> getLessons() {
        return Collections.unmodifiableList(lessons);
    }

    /** Một bài chỉ mở khi tất cả bài đứng trước nó trong khóa đã hoàn thành. */
    public boolean isLessonUnlocked(String lessonId) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            return false;
        }
        boolean previousLessonsCompleted = true;
        for (Lesson lesson : lessons) {
            if (lessonId.equals(lesson.getId())) {
                return previousLessonsCompleted;
            }
            previousLessonsCompleted = previousLessonsCompleted && lesson.isCompleted();
        }
        return false;
    }

    /** Bài chưa hoàn thành đầu tiên đang chặn bài cần mở, hoặc null nếu không bị chặn. */
    public Lesson getBlockingLesson(String lessonId) {
        if (lessonId == null || lessonId.trim().isEmpty()) {
            return null;
        }
        Lesson firstIncomplete = null;
        for (Lesson lesson : lessons) {
            if (lessonId.equals(lesson.getId())) {
                return firstIncomplete;
            }
            if (firstIncomplete == null && !lesson.isCompleted()) {
                firstIncomplete = lesson;
            }
        }
        return null;
    }
}
