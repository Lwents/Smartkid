package com.example.smartkid.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CourseListResult {
    private final List<Course> courses;
    private final boolean fromCache;
    private final String notice;

    public CourseListResult(List<Course> courses, boolean fromCache, String notice) {
        this.courses = courses == null ? new ArrayList<>() : new ArrayList<>(courses);
        this.fromCache = fromCache;
        this.notice = notice == null ? "" : notice;
    }

    public List<Course> getCourses() {
        return Collections.unmodifiableList(courses);
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public String getNotice() {
        return notice;
    }
}
