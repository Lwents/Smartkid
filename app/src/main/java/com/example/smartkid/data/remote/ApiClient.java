package com.example.smartkid.data.remote;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp duy nhất làm việc với Volley. Có gắn JWT, refresh token và chuẩn hóa lỗi.
 */
public final class ApiClient {
    private static volatile ApiClient instance;

    private final Context appContext;
    private final RequestQueue requestQueue;
    private final SessionManager sessionManager;
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object refreshLock = new Object();
    private final List<Runnable> pendingRetries = new ArrayList<>();
    private final List<java.util.function.Consumer<ApiError>> pendingRefreshFailures =
            new ArrayList<>();
    private boolean refreshingToken;

    private ApiClient(Context context) {
        appContext = context.getApplicationContext();
        requestQueue = Volley.newRequestQueue(appContext);
        sessionManager = new SessionManager(appContext);
    }

    /** Khởi tạo sẵn ở lúc app mở để lần gọi đầu không bị chậm. */
    public static void initialize(Context context) {
        getInstance(context);
    }

    /** Lấy thể hiện dùng chung (singleton) cho toàn app. */
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

    /** GET trả về một object JSON. */
    public void get(String endpoint, boolean authenticated, ApiCallback<JSONObject> callback) {
        request(Request.Method.GET, endpoint, null, authenticated, callback);
    }

    /** GET trả về một mảng JSON. */
    public void getArray(String endpoint, boolean authenticated, ApiCallback<JSONArray> callback) {
        ApiCallback<JSONArray> safeCallback = callback == null ? noOpArrayCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverArrayError(safeCallback,
                    new ApiError(0, "Chức năng này đang lỗi, vui lòng thử lại sau", false));
            return;
        }
        executeArray(endpoint, authenticated, true, safeCallback);
    }

    /** GET cho endpoint có thể trả về object hoặc array (các API quản trị cũ không đồng nhất). */
    public void getValue(String endpoint, boolean authenticated, ApiCallback<Object> callback) {
        ApiCallback<Object> safeCallback = callback == null ? noOpValueCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverValueError(safeCallback, new ApiError(0, "Chức năng này đang lỗi, vui lòng thử lại sau", false));
            return;
        }
        executeValue(endpoint, authenticated, true, safeCallback);
    }

    /** POST tạo mới dữ liệu. */
    public void post(String endpoint, JSONObject body, boolean authenticated,
                     ApiCallback<JSONObject> callback) {
        request(Request.Method.POST, endpoint, body, authenticated, callback);
    }

    /** PUT thay toàn bộ dữ liệu. */
    public void put(String endpoint, JSONObject body, boolean authenticated,
                    ApiCallback<JSONObject> callback) {
        request(Request.Method.PUT, endpoint, body, authenticated, callback);
    }

    /** PATCH sửa một phần dữ liệu. */
    public void patch(String endpoint, JSONObject body, boolean authenticated,
                      ApiCallback<JSONObject> callback) {
        request(Request.Method.PATCH, endpoint, body, authenticated, callback);
    }

    /** DELETE xóa dữ liệu. */
    public void delete(String endpoint, JSONObject body, boolean authenticated,
                       ApiCallback<JSONObject> callback) {
        request(Request.Method.DELETE, endpoint, body, authenticated, callback);
    }

    /** Điểm vào chung cho mọi phương thức HTTP dạng JSON. */
    public void request(int method, String endpoint, JSONObject body, boolean authenticated,
                        ApiCallback<JSONObject> callback) {
        ApiCallback<JSONObject> safeCallback = callback == null ? noOpCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverError(safeCallback,
                    new ApiError(0, "Chức năng này đang lỗi, vui lòng thử lại sau", false));
            return;
        }
        execute(method, endpoint, body, authenticated, true, safeCallback);
    }

    /** Gửi kèm file (ảnh bìa, video bài học) theo chuẩn multipart. */
    public void multipart(int method, String endpoint, JSONObject fields,
                          List<MultipartFilePart> files, boolean authenticated,
                          ApiCallback<JSONObject> callback) {
        ApiCallback<JSONObject> safeCallback = callback == null ? noOpCallback() : callback;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            deliverError(safeCallback, new ApiError(0, "Chức năng này đang lỗi, vui lòng thử lại sau", false));
            return;
        }
        executeMultipart(method, endpoint, fields, files, authenticated, true, safeCallback);
    }

    /** Tự dựng body multipart và gửi ở luồng riêng vì file có thể rất lớn. */
    private void executeMultipart(int method, String endpoint, JSONObject fields,
                                  List<MultipartFilePart> files, boolean authenticated,
                                  boolean allowRefresh, ApiCallback<JSONObject> callback) {
        uploadExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String boundary = "SmartKid-" + UUID.randomUUID();
                connection = (HttpURLConnection) new URL(buildUrl(endpoint)).openConnection();
                connection.setRequestMethod(methodName(method));
                connection.setConnectTimeout(AppConstants.NETWORK_TIMEOUT_MS);
                connection.setReadTimeout(10 * 60 * 1000);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setChunkedStreamingMode(64 * 1024);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);
                if (authenticated) {
                    String accessToken = sessionManager.getAccessToken();
                    if (!accessToken.isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                    }
                }

                try (OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
                    writeMultipartFields(output, boundary, fields);
                    writeMultipartFiles(output, boundary, files);
                    writeUtf8(output, "--" + boundary + "--\r\n");
                    output.flush();
                }

                int statusCode = connection.getResponseCode();
                String raw = readResponse(connection, statusCode);
                if (statusCode >= 200 && statusCode < 300) {
                    JSONObject response = raw.trim().isEmpty() ? new JSONObject()
                            : new JSONObject(raw);
                    mainHandler.post(() -> deliverSuccess(callback, response));
                } else if (authenticated && allowRefresh && statusCode == 401) {
                    mainHandler.post(() -> queueForTokenRefresh(
                            () -> executeMultipart(method, endpoint, fields, files,
                                    true, false, callback),
                            refreshError -> deliverError(callback, refreshError)));
                } else {
                    ApiError apiError = responseError(statusCode, raw);
                    mainHandler.post(() -> deliverError(callback, apiError));
                }
            } catch (Exception exception) {
                AppLogger.error(appContext, "ApiClient", "Không thể upload tệp", exception);
                mainHandler.post(() -> deliverError(callback,
                        new ApiError(0, "Không thể tải tệp lên máy chủ", false)));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /** Ghi các trường chữ vào body multipart. */
    private void writeMultipartFields(OutputStream output, String boundary,
                                      JSONObject fields) throws Exception {
        if (fields == null) return;
        JSONArray names = fields.names();
        if (names == null) return;
        for (int index = 0; index < names.length(); index++) {
            String name = names.optString(index, "");
            if (name.isEmpty() || fields.isNull(name)) continue;
            Object value = fields.opt(name);
            writeUtf8(output, "--" + boundary + "\r\n");
            writeUtf8(output, "Content-Disposition: form-data; name=\""
                    + headerValue(name) + "\"\r\n\r\n");
            writeUtf8(output, String.valueOf(value));
            writeUtf8(output, "\r\n");
        }
    }

    /** Ghi nội dung từng file vào body multipart. */
    private void writeMultipartFiles(OutputStream output, String boundary,
                                     List<MultipartFilePart> files) throws Exception {
        if (files == null || files.isEmpty()) return;
        ContentResolver resolver = appContext.getContentResolver();
        byte[] buffer = new byte[64 * 1024];
        for (MultipartFilePart part : files) {
            if (part == null || part.getUri() == null || part.getFieldName().isEmpty()) continue;
            Uri uri = part.getUri();
            String fileName = displayName(resolver, uri);
            String mimeType = resolver.getType(uri);
            if (mimeType == null || mimeType.trim().isEmpty()) {
                mimeType = "application/octet-stream";
            }
            writeUtf8(output, "--" + boundary + "\r\n");
            writeUtf8(output, "Content-Disposition: form-data; name=\""
                    + headerValue(part.getFieldName()) + "\"; filename=\""
                    + headerValue(fileName) + "\"\r\n");
            writeUtf8(output, "Content-Type: " + mimeType + "\r\n\r\n");
            try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            writeUtf8(output, "\r\n");
        }
    }

    /** Lấy tên file người dùng chọn để gửi kèm lên server. */
    private String displayName(ContentResolver resolver, Uri uri) {
        String name = "upload";
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) name = cursor.getString(column);
            }
        } catch (Exception ignored) {
            // The provider may not expose a display name.
        }
        return name == null || name.trim().isEmpty() ? "upload" : name.trim();
    }

    /** Đọc nội dung phản hồi, kể cả khi server trả mã lỗi. */
    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream source = statusCode >= 200 && statusCode < 400
                ? connection.getInputStream() : connection.getErrorStream();
        if (source == null) return "";
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Đổi phản hồi lỗi của server thành thông báo tiếng Việt cho người dùng. */
    private ApiError responseError(int statusCode, String raw) {
        try {
            JSONObject json = new JSONObject(raw == null ? "" : raw);
            String message = SafeJson.string(json, "", "detail", "message", "error");
            if (message.isEmpty()) {
                JSONArray names = json.names();
                if (names != null && names.length() > 0) {
                    Object value = json.opt(names.optString(0));
                    message = value instanceof JSONArray
                            ? ((JSONArray) value).optString(0, "") : String.valueOf(value);
                }
            }
            if (!message.isEmpty()) return new ApiError(statusCode, message, statusCode == 401);
        } catch (Exception ignored) {
            // Fall back to the normalized status message below.
        }
        return new ApiError(statusCode, thongBaoTheoMa(statusCode), statusCode == 401);
    }

    /**
     * Thông báo dự phòng khi máy chủ trả về nội dung không phải JSON (ví dụ trang
     * lỗi HTML). Trước đây mọi trường hợp đều báo "Không thể tải tệp lên máy chủ",
     * kể cả khi người dùng chỉ đang mở bài học, nên rất khó hiểu.
     */
    private String thongBaoTheoMa(int statusCode) {
        if (statusCode == 400) return "Thông tin gửi lên không hợp lệ, vui lòng kiểm tra lại";
        if (statusCode == 401) return "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại";
        if (statusCode == 403) return "Bạn không có quyền thực hiện chức năng này";
        if (statusCode == 404) return "Không tìm thấy dữ liệu yêu cầu";
        if (statusCode == 408) return "Máy chủ phản hồi quá lâu, vui lòng thử lại";
        if (statusCode == 413) return "Tệp vượt quá dung lượng máy chủ cho phép";
        if (statusCode == 429) return "Bạn thao tác quá nhanh, vui lòng chờ một chút";
        if (statusCode >= 500) return "Máy chủ đang gặp sự cố, vui lòng thử lại sau";
        return "Không thực hiện được, vui lòng thử lại";
    }

    /** Đổi mã phương thức của Volley sang chữ (GET, POST...). */
    private String methodName(int method) {
        if (method == Request.Method.POST) return "POST";
        if (method == Request.Method.PUT) return "PUT";
        if (method == Request.Method.PATCH) return "PATCH";
        throw new IllegalArgumentException("Multipart chỉ hỗ trợ POST, PUT hoặc PATCH");
    }

    /** Ghi chuỗi UTF-8 để không lỗi font tiếng Việt. */
    private void writeUtf8(OutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Làm sạch giá trị header, tránh ký tự xuống dòng gây lỗi request. */
    private String headerValue(String value) {
        return value == null ? "" : value.replace("\r", "_")
                .replace("\n", "_").replace("\"", "_");
    }

    /** Thực thi request JSON: gắn token, xử lý 401, trả kết quả về luồng giao diện. */
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
                                    refreshError -> deliverError(callback, refreshError));
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
                    endpoint.contains("activities/ai/")
                            ? AppConstants.AI_NETWORK_TIMEOUT_MS
                            : AppConstants.NETWORK_TIMEOUT_MS,
                    0,
                    1f
            ));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể tạo yêu cầu API", exception);
            deliverError(callback,
                    new ApiError(0, "Không thể kết nối, vui lòng thử lại", false));
        }
    }

    /** Thực thi request trả mảng JSON. */
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
                                    refreshError -> deliverArrayError(callback, refreshError));
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
                    new ApiError(0, "Không thể kết nối, vui lòng thử lại", false));
        }
    }

    /** Thực thi request chưa biết kiểu dữ liệu trả về. */
    private void executeValue(String endpoint, boolean authenticated, boolean allowRefresh,
                              ApiCallback<Object> callback) {
        try {
            JsonValueRequest request = new JsonValueRequest(buildUrl(endpoint),
                    response -> deliverValueSuccess(callback, response),
                    volleyError -> {
                        ApiError apiError = mapError(volleyError);
                        if (authenticated && allowRefresh && apiError.getStatusCode() == 401) {
                            queueForTokenRefresh(() -> executeValue(endpoint, true, false, callback),
                                    refreshError -> deliverValueError(callback, refreshError));
                        } else {
                            deliverValueError(callback, apiError);
                        }
                    }, authenticated ? sessionManager.getAccessToken() : "");
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS, 0, 1f));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể tạo yêu cầu JSON", exception);
            deliverValueError(callback, new ApiError(0, "Không thể kết nối, vui lòng thử lại", false));
        }
    }

    /** Xếp request bị 401 vào hàng chờ; chỉ làm mới token một lần rồi chạy lại tất cả. */
    private void queueForTokenRefresh(Runnable retry,
                                      java.util.function.Consumer<ApiError> failure) {
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

    /** Gọi API refresh để lấy access token mới bằng refresh token đã lưu. */
    private void refreshAccessToken() {
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken.isEmpty()) {
            finishRefreshFailure(new ApiError(401, "Phiên đăng nhập đã hết hạn", true), true);
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
                                    "Server không trả về access token mới", true), true);
                            return;
                        }
                        String rotatedRefresh = SafeJson.string(response, "",
                                "refresh", "refresh_token");
                        sessionManager.updateTokens(accessToken, rotatedRefresh);
                        finishRefreshSuccess();
                    }, error -> {
                        NetworkResponse networkResponse =
                                error == null ? null : error.networkResponse;
                        if (networkResponse == null) {
                            // Lỗi mạng tạm thời (timeout, mất kết nối): không hủy phiên,
                            // chỉ báo lỗi để người dùng thử lại.
                            finishRefreshFailure(new ApiError(0,
                                    "Không thể làm mới phiên do mất kết nối, vui lòng thử lại",
                                    false), false);
                            return;
                        }
                        finishRefreshFailure(new ApiError(401,
                                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", true), true);
                    });
            request.setRetryPolicy(new DefaultRetryPolicy(
                    AppConstants.NETWORK_TIMEOUT_MS, 0, 1f));
            requestQueue.add(request);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể refresh token", exception);
            finishRefreshFailure(new ApiError(0, "Không thể làm mới phiên đăng nhập", false), false);
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

    private void finishRefreshFailure(ApiError error, boolean clearSession) {
        List<java.util.function.Consumer<ApiError>> failures;
        synchronized (refreshLock) {
            failures = new ArrayList<>(pendingRefreshFailures);
            pendingRetries.clear();
            pendingRefreshFailures.clear();
            refreshingToken = false;
        }
        if (clearSession) {
            sessionManager.clear();
        }
        for (java.util.function.Consumer<ApiError> failure : failures) {
            try {
                failure.accept(error);
            } catch (Exception exception) {
                AppLogger.error(appContext, "ApiClient", "Không thể báo lỗi refresh", exception);
            }
        }
    }

    private String buildUrl(String endpoint) {
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint;
        String resolved = AppConstants.getApiBaseUrl();
        String baseUrl = resolved.endsWith("/") ? resolved : resolved + "/";
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
                return new ApiError(0, "Máy chủ phản hồi quá lâu, vui lòng thử lại", false);
            }
            if (error instanceof NoConnectionError) {
                return new ApiError(0, "Không có mạng hoặc máy chủ đang tạm nghỉ", false);
            }
            if (error instanceof ParseError) {
                return new ApiError(statusCode, "Dữ liệu nhận về bị lỗi, vui lòng thử lại", false);
            }
            if (error instanceof ServerError) {
                return new ApiError(statusCode, "Máy chủ đang gặp sự cố, vui lòng thử lại sau", false);
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
            return new ApiError(statusCode, "Không kết nối được tới máy chủ", false);
        } catch (Exception exception) {
            AppLogger.error(appContext, "ApiClient", "Không thể phân tích lỗi mạng", exception);
            return new ApiError(0, "Kết nối bị lỗi, vui lòng thử lại", false);
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
                        error == null ? "Không thực hiện được, vui lòng thử lại" : error.getMessage(), null);
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
                        error == null ? "Không thực hiện được, vui lòng thử lại" : error.getMessage(), null);
            }
        };
    }

    private ApiCallback<Object> noOpValueCallback() {
        return new ApiCallback<Object>() {
            @Override public void onSuccess(Object data) { }
            @Override public void onError(ApiError error) {
                AppLogger.error(appContext, "ApiClient",
                        error == null ? "Không thực hiện được, vui lòng thử lại" : error.getMessage(), null);
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
                    ? new ApiError(0, "Không thực hiện được, vui lòng thử lại", false) : error);
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
                    ? new ApiError(0, "Không thực hiện được, vui lòng thử lại", false) : error);
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
                    ? new ApiError(0, "Không thực hiện được, vui lòng thử lại", false) : error);
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
