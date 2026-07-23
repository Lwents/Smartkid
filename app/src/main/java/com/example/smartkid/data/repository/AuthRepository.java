package com.example.smartkid.data.repository;

import android.content.Context;

import com.example.smartkid.core.AppConstants;
import com.example.smartkid.core.AppLogger;
import com.example.smartkid.core.SafeJson;
import com.example.smartkid.data.local.SessionManager;
import com.example.smartkid.data.model.AuthResult;
import com.example.smartkid.data.model.User;
import com.example.smartkid.data.remote.ApiCallback;
import com.example.smartkid.data.remote.ApiClient;
import com.example.smartkid.data.remote.ApiError;

import org.json.JSONObject;

public class AuthRepository {
    private final Context appContext;
    private final ApiClient apiClient;
    private final SessionManager sessionManager;

    public AuthRepository(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context không được để trống");
        }
        appContext = context.getApplicationContext();
        apiClient = ApiClient.getInstance(appContext);
        sessionManager = new SessionManager(appContext);
    }

    public void login(String identifier, String password, String otp,
                      ApiCallback<AuthResult> callback) {
        try {
            JSONObject body = new JSONObject();
            String normalizedIdentifier = identifier == null ? "" : identifier.trim();
            if (normalizedIdentifier.contains("@")) {
                body.put("email", normalizedIdentifier);
            } else {
                body.put("username", normalizedIdentifier);
            }
            body.put("password", password == null ? "" : password);
            if (otp != null && !otp.trim().isEmpty()) {
                body.put("otp", otp.trim());
            }

            apiClient.post(AppConstants.LOGIN_ENDPOINT, body, false,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            try {
                                if (SafeJson.bool(response, false, "requires_otp", "requiresOtp")) {
                                    callback.onSuccess(AuthResult.otpRequired(
                                            SafeJson.string(response,
                                                    "Mã OTP đã được gửi, vui lòng kiểm tra email",
                                                    "detail", "message")));
                                    return;
                                }

                                String access = SafeJson.string(response, "",
                                        "access", "access_token", "token");
                                String refresh = SafeJson.string(response, "",
                                        "refresh", "refresh_token");
                                if (access.isEmpty() || refresh.isEmpty()) {
                                    callback.onError(new ApiError(0,
                                            "Phản hồi đăng nhập thiếu access hoặc refresh token", false));
                                    return;
                                }

                                User user = parseUser(response.optJSONObject("user"));
                                sessionManager.saveSession(access, refresh, user);
                                callback.onSuccess(AuthResult.success(user));
                            } catch (Exception exception) {
                                AppLogger.error(appContext, "AuthRepository",
                                        "Không thể đọc phản hồi đăng nhập", exception);
                                callback.onError(new ApiError(0,
                                        "Dữ liệu đăng nhập từ server không hợp lệ", false));
                            }
                        }

                        @Override
                        public void onError(ApiError error) {
                            callback.onError(error);
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "AuthRepository", "Không thể tạo dữ liệu đăng nhập", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu đăng nhập", false));
        }
    }

    public void loadProfile(ApiCallback<User> callback) {
        apiClient.get(AppConstants.PROFILE_ENDPOINT, true, new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    User user = parseUser(response);
                    sessionManager.updateUser(user);
                    callback.onSuccess(user);
                } catch (Exception exception) {
                    AppLogger.error(appContext, "AuthRepository", "Không thể đọc hồ sơ", exception);
                    callback.onError(new ApiError(0, "Dữ liệu hồ sơ không hợp lệ", false));
                }
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        });
    }

    public void loadAccountProfile(ApiCallback<JSONObject> callback) {
        apiClient.get("account/profile/", true, callback);
    }

    public void updateAccountProfile(JSONObject values, ApiCallback<JSONObject> callback) {
        apiClient.patch("account/profile/", values == null ? new JSONObject() : values,
                true, callback);
    }

    public void register(String username, String email, String phone, String password,
                         ApiCallback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username == null ? "" : username.trim());
            body.put("email", email == null ? "" : email.trim());
            body.put("phone", phone == null ? "" : phone.trim());
            body.put("password", password == null ? "" : password);
            body.put("role", "student");
            apiClient.post(AppConstants.REGISTER_ENDPOINT, body, false,
                    messageCallback("Đăng ký thành công. Bạn có thể đăng nhập ngay.", callback));
        } catch (Exception exception) {
            AppLogger.error(appContext, "AuthRepository", "Không thể tạo yêu cầu đăng ký", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu đăng ký", false));
        }
    }

    public void requestPasswordReset(String email, ApiCallback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email == null ? "" : email.trim());
            apiClient.post(AppConstants.FORGOT_PASSWORD_ENDPOINT, body, false,
                    messageCallback("Đã gửi hướng dẫn đặt lại mật khẩu đến email.", callback));
        } catch (Exception exception) {
            AppLogger.error(appContext, "AuthRepository", "Không thể tạo yêu cầu quên mật khẩu", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu quên mật khẩu", false));
        }
    }

    public void resetPassword(String email, String token, String newPassword,
                              ApiCallback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email == null ? "" : email.trim());
            body.put("reset_token", token == null ? "" : token.trim());
            body.put("new_password", newPassword == null ? "" : newPassword);
            apiClient.post(AppConstants.RESET_PASSWORD_ENDPOINT, body, false,
                    messageCallback("Đặt lại mật khẩu thành công.", callback));
        } catch (Exception exception) {
            AppLogger.error(appContext, "AuthRepository", "Không thể tạo yêu cầu đặt lại mật khẩu", exception);
            callback.onError(new ApiError(0, "Không thể chuẩn bị yêu cầu đặt lại mật khẩu", false));
        }
    }

    private ApiCallback<JSONObject> messageCallback(String fallback, ApiCallback<String> callback) {
        return new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                callback.onSuccess(SafeJson.string(data, fallback, "detail", "message"));
            }

            @Override
            public void onError(ApiError error) {
                callback.onError(error);
            }
        };
    }

    public void logout(ApiCallback<Boolean> callback) {
        String refresh = sessionManager.getRefreshToken();
        try {
            JSONObject body = new JSONObject();
            body.put("refresh", refresh);
            apiClient.post(AppConstants.LOGOUT_ENDPOINT, body, true,
                    new ApiCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            sessionManager.clear();
                            callback.onSuccess(true);
                        }

                        @Override
                        public void onError(ApiError error) {
                            // Dù server không phản hồi, thiết bị vẫn phải kết thúc phiên an toàn.
                            sessionManager.clear();
                            callback.onSuccess(true);
                        }
                    });
        } catch (Exception exception) {
            AppLogger.error(appContext, "AuthRepository", "Không thể tạo yêu cầu đăng xuất", exception);
            sessionManager.clear();
            callback.onSuccess(true);
        }
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    private User parseUser(JSONObject object) {
        JSONObject safeObject = object == null ? new JSONObject() : object;
        String username = SafeJson.string(safeObject, "Học viên", "username", "name");
        return new User(
                SafeJson.string(safeObject, "", "id", "user_id"),
                username,
                SafeJson.string(safeObject, username,
                        "fullName", "full_name", "display_name", "name"),
                SafeJson.string(safeObject, "", "email"),
                SafeJson.string(safeObject, "student", "role"),
                SafeJson.string(safeObject, "", "class_name", "className")
        );
    }
}
