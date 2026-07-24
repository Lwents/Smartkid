package com.example.smartkid.data.model;

import org.json.JSONObject;

/** Dữ liệu hiển thị chung cho các danh sách nhỏ như đề thi và thông báo. */
public class FeatureItem {
    private final String id;
    private final String title;
    private final String subtitle;
    private final String detail;
    private final String status;
    private final JSONObject source;

    public FeatureItem(String id, String title, String subtitle, String detail,
                       String status, JSONObject source) {
        this.id = safe(id);
        this.title = safe(title).isEmpty() ? "Chưa có tiêu đề" : safe(title);
        this.subtitle = safe(subtitle);
        this.detail = safe(detail);
        this.status = safe(status);
        this.source = source == null ? new JSONObject() : source;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getDetail() { return detail; }
    public String getStatus() { return status; }
    public JSONObject getSource() { return source; }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
