package com.example.smartkid.data.model;

public class Lesson {
    private final String id;
    private final String title;
    private final String type;
    private final boolean completed;

    public Lesson(String id, String title, String type, boolean completed) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null || title.trim().isEmpty()
                ? "Bài học chưa đặt tên" : title.trim();
        this.type = type == null || type.trim().isEmpty() ? "text" : type.trim();
        this.completed = completed;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public boolean isCompleted() {
        return completed;
    }
}
