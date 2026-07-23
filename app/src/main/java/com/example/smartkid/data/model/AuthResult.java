package com.example.smartkid.data.model;

public class AuthResult {
    private final boolean requiresOtp;
    private final String message;
    private final User user;

    private AuthResult(boolean requiresOtp, String message, User user) {
        this.requiresOtp = requiresOtp;
        this.message = message == null ? "" : message;
        this.user = user;
    }

    public static AuthResult otpRequired(String message) {
        return new AuthResult(true, message, null);
    }

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
