package com.example.smartkid.data.model;

/**
 * Kết quả đăng nhập: hoặc thành công (có User), hoặc cần nhập thêm mã OTP.
 */
public class AuthResult {
    private final boolean requiresOtp;
    private final String message;
    private final User user;

    private AuthResult(boolean requiresOtp, String message, User user) {
        this.requiresOtp = requiresOtp;
        this.message = message == null ? "" : message;
        this.user = user;
    }

    /** Server yêu cầu nhập mã OTP để hoàn tất đăng nhập. */
    public static AuthResult otpRequired(String message) {
        return new AuthResult(true, message, null);
    }

    /** Đăng nhập xong, kèm thông tin người dùng. */
    public static AuthResult success(User user) {
        return new AuthResult(false, "", user);
    }

    public boolean isRequiresOtp() {
        return requiresOtp;
    }

    public String getMessage() {
        return message;
    }

    public User getUser() {
        return user;
    }
}
