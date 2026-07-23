package com.example.smartkid.data.remote;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.HttpHeaderParser;
import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;
import com.example.smartkid.common.util.SafeJson;
import com.example.smartkid.data.local.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lớp duy nhất làm việc với Volley. Có gắn JWT, refresh token và chuẩn hóa lỗi.
 */
public final class ApiClient {
    private static volatile ApiClient instance;

    private final Context appContext;
    private final RequestQueue requestQueue;
    private final SessionManager sessionManager;
    private final Object refreshLock = new Object();
    private final List<Runnable> pendingRetries = new ArrayList<>();
    private final List<Runnable> pendingRefreshFailures = new ArrayList<>();
    private boolean refreshingToken;

    private ApiClient(Context context) {
        appContext = context.getApplicationContext();
        requestQueue = Volley.newRequestQueue(appContext);
        sessionManager = new SessionManager(appContext);
    }

    public static void initialize(Context context) {
        getInstance(context);
    }

    public static ApiClient getInstance(Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    if (context == null) {
                        throw new IllegalStateException("ApiClient cần Context để khởi tạo");
                    }
                    instance = new ApiClient(context);
                }
            }
        }
        return instance;
    }

    public void get(String endpoint, boolean authenticated, ApiCallback<JSONObject> callback) {
        request(Request.Method.GET, endpoint, null, authenticated, callback);
    }

    public void getArray(String endpoint, boolean authenticated, ApiCallback<JSONArray> callback) {
        ApiCallback<JSONArray> safeCallback = callback == null ? noOpArrayCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverArrayError(safeCallback,
                    new ApiError(0, "Đường dẫn API không hợp lệ", false));
            return;
        }
        executeArray(endpoint, authenticated, true, safeCallback);
    }

    /** GET cho endpoint có thể trả về object hoặc array (các API quản trị cũ không đồng nhất). */
    public void getValue(String endpoint, boolean authenticated, ApiCallback<Object> callback) {
        ApiCallback<Object> safeCallback = callback == null ? noOpValueCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverValueError(safeCallback, new ApiError(0, "Đường dẫn API không hợp lệ", false));
            return;
        }
        executeValue(endpoint, authenticated, true, safeCallback);
    }

    public void post(String endpoint, JSONObject body, boolean authenticated,
                     ApiCallback<JSONObject> callback) {
        request(Request.Method.POST, endpoint, body, authenticated, callback);
    }

    public void put(String endpoint, JSONObject body, boolean authenticated,
                    ApiCallback<JSONObject> callback) {
        request(Request.Method.PUT, endpoint, body, authenticated, callback);
    }

    public void patch(String endpoint, JSONObject body, boolean authenticated,
                      ApiCallback<JSONObject> callback) {
        request(Request.Method.PATCH, endpoint, body, authenticated, callback);
    }

    public void delete(String endpoint, JSONObject body, boolean authenticated,
                       ApiCallback<JSONObject> callback) {
        request(Request.Method.DELETE, endpoint, body, authenticated, callback);
    }

    public void request(int method, String endpoint, JSONObject body, boolean authenticated,
                        ApiCallback<JSONObject> callback) {
        ApiCallback<JSONObject> safeCallback = callback == null ? noOpCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverError(safeCallback,
                    new ApiError(0, "Đường dẫn API không hợp lệ", false));
            return;
        }
        execute(method, endpoint, body, authenticated, true, safeCallback);
    }

    private void execute(int method, String endpoint, JSONObject body, boolean authenticated,
                         boolean allowRefresh, ApiCallback<JSONObject> callback) {
        try {
            String url = buildUrl(endpoint);
            JsonObjectRequest request = new JsonObjectRequest(method, url, body,
                    response -> deliverSuccess(callback, response),
                    volleyError -> {
                        ApiError apiError = mapError(volleyError);
                        if (authenticated && allowRefresh && apiError.getStatusCode() == 401
                                && !endpoint.contains(AppConstants.REFRESH_ENDPOINT)) {
                            queueForTokenRefresh(() -> execute(method, endpoint, body,
                                            true, false, callback),
                                    () -> deliverError(callback,
                                            new ApiError(401, "Phiên đăng nhập đã hết hạn", true)));
                        } else {
                            deliverError(callback, apiError);
                        }
                    }) {
                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                    if (response != null && (response.data == null || response.data.length == 0)) {
                        return Response.success(new JSONObject(),
                                HttpHeaderParser.parseCacheHeaders(response));
                    }
                    return super.parseNetworkResponse(response);
                }

                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Accept", "application/json");
                    headers.put("Content-Type", "application/json; charset=utf-8");
                    if (authenticated) {
                        String accessToken = sessionManager.getAccessToken();
                        if (!accessToken.isEmpty()) {
                            headers.put("Authorization", "Bearer " + accessToken);
                        }
                    }
                    return headers;
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS,
                    0,
                    1f
            ));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể tạo yêu cầu API", exception);
            deliverError(callback,
                    new ApiError(0, "Không thể tạo yêu cầu kết nối", false));
        }
    }

    private void executeArray(String endpoint, boolean authenticated, boolean allowRefresh,
                              ApiCallback<JSONArray> callback) {
        try {
            JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, buildUrl(endpoint), null,
                    response -> deliverArraySuccess(callback, response),
                    volleyError -> {
                        ApiError apiError = mapError(volleyError);
                        if (authenticated && allowRefresh && apiError.getStatusCode() == 401
                                && !endpoint.contains(AppConstants.REFRESH_ENDPOINT)) {
                            queueForTokenRefresh(() -> executeArray(endpoint, true, false, callback),
                                    () -> deliverArrayError(callback,
                                            new ApiError(401, "Phiên đăng nhập đã hết hạn", true)));
                        } else {
                            deliverArrayError(callback, apiError);
                        }
                    }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Accept", "application/json");
                    if (authenticated) {
                        String accessToken = sessionManager.getAccessToken();
                        if (!accessToken.isEmpty()) {
                            headers.put("Authorization", "Bearer " + accessToken);
                        }
                    }
                    return headers;
                }
            };
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS, 0, 1f));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể tạo yêu cầu mảng API", exception);
            deliverArrayError(callback,
                    new ApiError(0, "Không thể tạo yêu cầu kết nối", false));
        }
    }

    private void executeValue(String endpoint, boolean authenticated, boolean allowRefresh,
                              ApiCallback<Object> callback) {
        try {
            JsonValueRequest request = new JsonValueRequest(buildUrl(endpoint),
                    response -> deliverValueSuccess(callback, response),
                    volleyError -> {
                        ApiError apiError = mapError(volleyError);
                        if (authenticated && allowRefresh && apiError.getStatusCode() == 401) {
                            queueForTokenRefresh(() -> executeValue(endpoint, true, false, callback),
                                    () -> deliverValueError(callback,
                                            new ApiError(401, "Phiên đăng nhập đã hết hạn", true)));
                        } else {
                            deliverValueError(callback, apiError);
                        }
                    }, authenticated ? sessionManager.getAccessToken() : "");
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS, 0, 1f));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể tạo yêu cầu JSON", exception);
            deliverValueError(callback, new ApiError(0, "Không thể tạo yêu cầu kết nối", false));
        }
    }

    private void queueForTokenRefresh(Runnable retry, Runnable failure) {
        boolean shouldStartRefresh = false;
        synchronized (refreshLock) {
            pendingRetries.add(retry);
            pendingRefreshFailures.add(failure);
            if (!refreshingToken) {
                refreshingToken = true;
                shouldStartRefresh = true;
            }
        }
        if (shouldStartRefresh) {
            refreshAccessToken();
        }
    }

    private void refreshAccessToken() {
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken.isEmpty()) {
            finishRefreshFailure(new ApiError(401, "Phiên đăng nhập đã hết hạn", true));
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("refresh", refreshToken);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                    buildUrl(AppConstants.REFRESH_ENDPOINT), body,
                    response -> {
                        String accessToken = SafeJson.string(response, "", "access", "access_token");
                        if (accessToken.isEmpty()) {
                            finishRefreshFailure(new ApiError(401,
                                    "Server không trả về access token mới", true));
                            return;
                        }
                        sessionManager.updateAccessToken(accessToken);
                        finishRefreshSuccess();
                    }, error -> finishRefreshFailure(new ApiError(401,
                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", true)));
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS, 0, 1f));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể refresh token", exception);
            finishRefreshFailure(new ApiError(401, "Không thể làm mới phiên đăng nhập", true));
        }
    }

    private void finishRefreshSuccess() {
        List<Runnable> retries;
        synchronized (refreshLock) {
            retries = new ArrayList<>(pendingRetries);
            pendingRetries.clear();
            pendingRefreshFailures.clear();
            refreshingToken = false;
        }
        for (Runnable retry : retries) {
            try {
                retry.run();
            } catch (Exception exception) {
                AppLogger.error(appContext, "ApiClient", "Không thể gửi lại yêu cầu", exception);
            }
        }
    }

    private void finishRefreshFailure(ApiError error) {
        List<Runnable> failures;
        synchronized (refreshLock) {
            failures = new ArrayList<>(pendingRefreshFailures);
            pendingRetries.clear();
            pendingRefreshFailures.clear();
            refreshingToken = false;
        }
        sessionManager.clear();
        for (Runnable failure : failures) {
            try {
                failure.run();
            } catch (Exception exception) {
                AppLogger.error(appContext, "ApiClient", "Không thể báo lỗi refresh", exception);
            }
        }
    }

    private String buildUrl(String endpoint) {
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        String baseUrl = AppConstants.API_BASE_URL.endsWith("/")
                ? AppConstants.API_BASE_URL : AppConstants.API_BASE_URL + "/";
        return baseUrl + normalizedEndpoint;
    }

    private ApiError mapError(VolleyError error) {
        try {
            NetworkResponse response = error == null ? null : error.networkResponse;
            int statusCode = response == null ? 0 : response.statusCode;
            String serverMessage = extractServerMessage(response);
            if (!serverMessage.isEmpty()) {
                return new ApiError(statusCode, serverMessage, statusCode == 401);
            }
            if (error instanceof TimeoutError) {
                return new ApiError(0, "Server phản hồi quá lâu, vui lòng thử lại", false);
            }
            if (error instanceof NoConnectionError) {
                return new ApiError(0, "Không có kết nối mạng hoặc server đang tạm dừng", false);
            }
            if (error instanceof ParseError) {
                return new ApiError(statusCode, "Dữ liệu server trả về không đúng định dạng", false);
            }
            if (error instanceof ServerError) {
                return new ApiError(statusCode, "Server gặp lỗi, vui lòng thử lại sau", false);
            }
            if (statusCode == 401) {
                return new ApiError(401, "Phiên đăng nhập không hợp lệ", true);
            }
            if (statusCode == 403) {
                return new ApiError(403, "Bạn không có quyền thực hiện chức năng này", false);
            }
            if (statusCode == 404) {
                return new ApiError(404, "Không tìm thấy dữ liệu yêu cầu", false);
            }
            return new ApiError(statusCode, "Không thể kết nối tới server", false);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể phân tích lỗi mạng", exception);
            return new ApiError(0, "Lỗi kết nối không xác định", false);
        }
    }

    private String extractServerMessage(NetworkResponse response) {
        if (response == null || response.data == null || response.data.length == 0) {
            return "";
        }
        try {
            String raw = new String(response.data, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);
            String direct = SafeJson.string(json, "", "detail", "message", "error");
            if (!direct.isEmpty()) {
                return direct;
            }
            JSONArray names = json.names();
            if (names != null && names.length() > 0) {
                Object firstValue = json.opt(names.optString(0));
                if (firstValue instanceof JSONArray) {
                    return ((JSONArray) firstValue).optString(0, "");
                }
                return firstValue == null ? "" : String.valueOf(firstValue);
            }
        } catch (Exception ignored) {
            // Nội dung lỗi không phải JSON, dùng thông báo theo status code.
        }
        return "";
    }

    private ApiCallback<JSONObject> noOpCallback() {
        return new ApiCallback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject data) {
                // Không có nơi nhận kết quả.
            }

            @Override
            public void onError(ApiError error) {
                AppLogger.error(appContext, "ApiClient",
                        error == null ? "Lỗi API không xác định" : error.getMessage(), null);
            }
        };
    }

    private ApiCallback<JSONArray> noOpArrayCallback() {
        return new ApiCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray data) {
                // Không có nơi nhận kết quả.
            }

            @Override
            public void onError(ApiError error) {
                AppLogger.error(appContext, "ApiClient",
                        error == null ? "Lỗi API không xác định" : error.getMessage(), null);
            }
        };
    }

    private ApiCallback<Object> noOpValueCallback() {
        return new ApiCallback<Object>() {
            @Override public void onSuccess(Object data) { }
            @Override public void onError(ApiError error) {
                AppLogger.error(appContext, "ApiClient",
                        error == null ? "Lỗi API không xác định" : error.getMessage(), null);
            }
        };
    }

    private void deliverSuccess(ApiCallback<JSONObject> callback, JSONObject data) {
        try {
            callback.onSuccess(data == null ? new JSONObject() : data);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient",
                    "Lỗi khi xử lý dữ liệu trả về từ API", exception);
        }
    }

    private void deliverError(ApiCallback<JSONObject> callback, ApiError error) {
        try {
            callback.onError(error == null
                    ? new ApiError(0, "Lỗi API không xác định", false) : error);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient",
                    "Lỗi khi xử lý thông báo API", exception);
        }
    }

    private void deliverArraySuccess(ApiCallback<JSONArray> callback, JSONArray data) {
        try {
            callback.onSuccess(data == null ? new JSONArray() : data);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient",
                    "Lỗi khi xử lý mảng dữ liệu trả về từ API", exception);
        }
    }

    private void deliverArrayError(ApiCallback<JSONArray> callback, ApiError error) {
        try {
            callback.onError(error == null
                    ? new ApiError(0, "Lỗi API không xác định", false) : error);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient",
                    "Lỗi khi xử lý thông báo API mảng", exception);
        }
    }

    private void deliverValueSuccess(ApiCallback<Object> callback, Object data) {
        try {
            callback.onSuccess(data == null ? new JSONObject() : data);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể xử lý JSON API", exception);
        }
    }

    private void deliverValueError(ApiCallback<Object> callback, ApiError error) {
        try {
            callback.onError(error == null
                    ? new ApiError(0, "Lỗi API không xác định", false) : error);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể xử lý lỗi JSON API", exception);
        }
    }

    private static final class JsonValueRequest extends Request<Object> {
        private final Response.Listener<Object> listener;
        private final String accessToken;

        JsonValueRequest(String url, Response.Listener<Object> listener,
                         Response.ErrorListener errorListener, String accessToken) {
            super(Method.GET, url, errorListener);
            this.listener = listener;
            this.accessToken = accessToken == null ? "" : accessToken;
        }

        @Override
        protected Response<Object> parseNetworkResponse(NetworkResponse response) {
            try {
                String charset = HttpHeaderParser.parseCharset(response.headers, "UTF-8");
                String raw = new String(response.data, charset);
                Object value = new JSONTokener(raw).nextValue();
                if (!(value instanceof JSONObject) && !(value instanceof JSONArray)) {
                    return Response.error(new ParseError(new IllegalArgumentException(
                            "Phản hồi không phải JSON object/array")));
                }
                return Response.success(value, HttpHeaderParser.parseCacheHeaders(response));
            } catch (Exception exception) {
                return Response.error(new ParseError(exception));
            }
        }

        @Override protected void deliverResponse(Object response) { listener.onResponse(response); }

        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            if (!accessToken.isEmpty()) headers.put("Authorization", "Bearer " + accessToken);
            return headers;
        }
    }
}
