package com.example.smartkid.data.model;

public class LessonContent {
    private final String id;
    private final String title;
    private final String contentType;
    private final String videoUrl;
    private final String documentUrl;
    private final String textContent;
    private final boolean completed;

    public LessonContent(String id, String title, String contentType, String videoUrl,
                         String documentUrl, String textContent, boolean completed) {
        this.id = safe(id);
        this.title = safe(title);
        this.contentType = safe(contentType);
        this.videoUrl = safe(videoUrl);
        this.documentUrl = safe(documentUrl);
        this.textContent = safe(textContent);
        this.completed = completed;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContentType() {
        return contentType;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public String getTextContent() {
        return textContent;
    }

    public boolean isCompleted() {
        return completed;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
