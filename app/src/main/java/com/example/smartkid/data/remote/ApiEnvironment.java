package com.example.smartkid.data.remote;

import android.net.Uri;

import com.example.smartkid.common.util.AppConstants;
import com.example.smartkid.common.util.AppLogger;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chốt URL API lúc khởi động: chỉ backend chạy ngay trên máy dev (qua emulator) mới dùng local,
 * còn mọi thiết bị khác — kể cả điện thoại thật — luôn dùng VPS công khai. Không phụ thuộc wifi
 * hay IP LAN. Chỉ probe một lần cho mỗi lần chạy app.
 *
 * <p>Ứng viên thử lần lượt, chọn cái đầu tiên kết nối được:
 * <ol>
 *   <li>Loopback của emulator ({@code 10.0.2.2}, giữ nguyên port + path của URL local trong
 *       cấu hình) — chỉ tồn tại bên trong emulator trên máy dev.</li>
 *   <li>VPS công khai — dùng cho điện thoại thật và mọi máy khác.</li>
 * </ol>
 * Điện thoại thật không bao giờ tới được {@code 10.0.2.2} nên tự động rơi về VPS.
 * Probe bằng TCP socket tới host:port nên nhanh và không phụ thuộc backend có endpoint health.
 */
public final class ApiEnvironment {
    private static final int PROBE_TIMEOUT_MS = 800;
    private static final String EMULATOR_LOOPBACK_HOST = "10.0.2.2";

    private ApiEnvironment() {
        // Lớp tiện ích.
    }

    /** Probe nền rồi chốt {@link AppConstants#setApiBaseUrl(String)}. Không block luồng gọi. */
    public static void resolveAsync() {
        final List<String> candidates = buildCandidates();
        final String fallback = AppConstants.API_FALLBACK_URL;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String chosen = fallback;
            for (String candidate : candidates) {
                if (canReach(candidate)) {
                    chosen = candidate;
                    break;
                }
            }
            AppConstants.setApiBaseUrl(chosen);
            executor.shutdown();
        });
    }

    /** Danh sách URL thử theo thứ tự ưu tiên, đã loại trùng lặp, VPS luôn ở cuối. */
    static List<String> buildCandidates() {
        List<String> candidates = new ArrayList<>();
        String local = AppConstants.API_LOCAL_URL;
        String fallback = AppConstants.API_FALLBACK_URL;

        // Chỉ loopback emulator (10.0.2.2) mới tính là "trên máy dev"; bỏ qua IP LAN thô để điện
        // thoại thật không bao giờ dính local mà luôn dùng VPS.
        addUnique(candidates, emulatorVariant(local));
        addUnique(candidates, fallback);
        return candidates;
    }

    /** Đổi host của URL local thành loopback của emulator, giữ nguyên scheme/port/path. */
    private static String emulatorVariant(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(url.trim());
            String host = uri.getHost();
            if (host == null) return null;
            // Đã là loopback (mặc định build) thì giữ nguyên; nếu không thì đổi host sang loopback.
            if (host.equalsIgnoreCase(EMULATOR_LOOPBACK_HOST)) return url.trim();
            Uri.Builder builder = uri.buildUpon();
            String encoded = uri.getPort() > 0
                    ? EMULATOR_LOOPBACK_HOST + ":" + uri.getPort()
                    : EMULATOR_LOOPBACK_HOST;
            builder.encodedAuthority(encoded);
            return builder.build().toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private static void addUnique(List<String> target, String url) {
        if (url == null || url.trim().isEmpty()) return;
        String value = url.trim();
        if (!target.contains(value)) target.add(value);
    }

    private static boolean canReach(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return false;
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
                return true;
            }
        } catch (Exception exception) {
            AppLogger.error(null, "ApiEnvironment",
                    "Không kết nối được " + url, exception);
            return false;
        }
    }
}
