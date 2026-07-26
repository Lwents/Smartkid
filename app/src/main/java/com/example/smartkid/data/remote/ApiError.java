package com.example.smartkid.data.remote;

/**
 * Một lỗi khi gọi API, đã được chuyển thành thông báo đọc được cho người dùng.
 */
public class ApiError {
    private final int statusCode;
    private final String message;
    private final boolean sessionExpired;

    /** Tạo lỗi kèm mã HTTP, thông báo và cờ hết phiên đăng nhập. */
    public ApiError(int statusCode, String message, boolean sessionExpired) {
        this.statusCode = statusCode;
        this.message = message == null || message.trim().isEmpty()
                ? "Có lỗi xảy ra, vui lòng thử lại" : message.trim();
        this.sessionExpired = sessionExpired;
    }

    /** Mã HTTP (0 nếu lỗi mạng, chưa tới được server). */
    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    /** Đúng khi phiên hết hạn: màn hình sẽ đưa người dùng về đăng nhập. */
    public boolean isSessionExpired() {
        return sessionExpired;
    }
}
