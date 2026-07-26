package com.example.smartkid.common.util;

/**
 * Server trả đường dẫn media dạng tương đối ("/media/videos/x.mp4"). Trình phát cần
 * URL tuyệt đối, nếu không video sẽ chỉ hiện khung đen. Helper này ghép đường dẫn
 * đó với host backend đang dùng (local hoặc VPS, do ApiEnvironment chốt lúc chạy).
 */
public final class MediaUrl {
    private MediaUrl() {
    }

    /** Đổi '/media/x.mp4' thành URL đầy đủ theo server đang dùng; nếu đã là http(s) thì giữ nguyên. */
    public static String absolute(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        String root = AppConstants.getApiBaseUrl();
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.endsWith("/api")) root = root.substring(0, root.length() - 4);
        return root + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
    }
}
