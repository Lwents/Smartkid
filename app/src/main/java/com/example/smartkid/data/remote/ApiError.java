package com.example.smartkid.data.remote;

public class ApiError {
    private final int statusCode;
    private final String message;
    private final boolean sessionExpired;

    public ApiError(int statusCode, String message, boolean sessionExpired) {
        this.statusCode = statusCode;
        this.message = message == null || message.trim().isEmpty()
                ? "Có lỗi xảy ra, vui lòng thử lại" : message.trim();
        this.sessionExpired = sessionExpired;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSessionExpired() {
        return sessionExpired;
    }
}
