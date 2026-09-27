package org.example.desktopver1.network;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.example.desktopver1.model.Device;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service quản lý luồng ngầm (Background Thread) duy trì kết nối TCP Socket tới VPS
 * và cung cấp các hàm gửi gói tin JSON khi phụ huynh thao tác trên ứng dụng FamilyGuard.
 */
public class VpsClientService {

    public enum ConnectionState {
        DISCONNECTED("Mất kết nối VPS", "#EF4444", "🔴"),
        CONNECTING("Đang kết nối VPS...", "#F59E0B", "🟡"),
        CONNECTED("Đã kết nối Socket VPS", "#3B82F6", "🔵"),
        AUTHENTICATED("Đã xác thực với VPS", "#10B981", "🟢");

        private final String description;
        private final String colorHex;
        private final String icon;

        ConnectionState(String description, String colorHex, String icon) {
            this.description = description;
            this.colorHex = colorHex;
            this.icon = icon;
        }

        public String getDescription() {
            return description;
        }

        public String getColorHex() {
            return colorHex;
        }

        public String getIcon() {
            return icon;
        }
    }

    public interface VpsMessageListener {
        void onStateChanged(ConnectionState newState, String message);
        void onMessageReceived(String rawJson);
        void onMessageSent(String rawJson, boolean success);
    }

    private static VpsClientService instance;

    // Cấu hình kết nối VPS theo yêu cầu đề bài
    public static final String DEFAULT_HOST = "103.74.101.176";
    public static final int DEFAULT_PORT = 9000;
    public static final String DEFAULT_PASS = "AZvpsd6eb!5l@66";

    private final String host;
    private final int port;
    private final String authPass;

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int RECONNECT_DELAY_MS = 5000;

    // Quản lý trạng thái luồng và kết nối
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ObjectProperty<ConnectionState> connectionState = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
    private final StringProperty statusMessage = new SimpleStringProperty("Chưa khởi chạy kết nối.");

    // Hàng đợi bản tin gửi đi (Thread-safe Non-blocking Queue)
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();

    // Các luồng ngầm
    private Thread connectionThread;
    private Thread senderThread;

    // Socket I/O
    private Socket socket;
    private PrintWriter socketOut;
    private BufferedReader socketIn;

    // Danh sách các component đăng ký lắng nghe sự kiện mạng
    private final List<VpsMessageListener> listeners = new CopyOnWriteArrayList<>();

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public VpsClientService() {
        this(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_PASS);
    }

    public VpsClientService(String host, int port, String authPass) {
        this.host = host;
        this.port = port;
        this.authPass = authPass;
    }

    public static synchronized VpsClientService getInstance() {
        if (instance == null) {
            instance = new VpsClientService();
        }
        return instance;
    }

    public static synchronized void setInstance(VpsClientService customInstance) {
        instance = customInstance;
    }

    /**
     * Khởi chạy luồng ngầm duy trì kết nối tới VPS.
     * Tự động tái kết nối định kỳ khi gặp sự cố mạng hoặc Server chưa sẵn sàng.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);

        // 1. Luồng ngầm quản lý kết nối và nhận dữ liệu từ VPS
        connectionThread = new Thread(this::connectionLoop, "VPS-Connection-Maintainer");
        connectionThread.setDaemon(true);
        connectionThread.start();

        // 2. Luồng ngầm gửi dữ liệu từ hàng đợi lên VPS
        senderThread = new Thread(this::senderLoop, "VPS-Message-Sender");
        senderThread.setDaemon(true);
        senderThread.start();

        System.out.println("[VPS-Client] Đã kích hoạt luồng ngầm kết nối tới " + host + ":" + port);
    }

    /**
     * Dừng các luồng ngầm và đóng kết nối an toàn khi thoát ứng dụng.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        closeSocket();

        if (connectionThread != null) {
            connectionThread.interrupt();
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }

        updateState(ConnectionState.DISCONNECTED, "Đã ngắt kết nối an toàn.");
        System.out.println("[VPS-Client] Đã dừng luồng ngầm VPS.");
    }

    /**
     * Vòng lặp chính của luồng duy trì kết nối (Connection Maintainer Thread).
     */
    private void connectionLoop() {
        while (running.get()) {
            try {
                updateState(ConnectionState.CONNECTING, "Đang kết nối tới VPS " + host + ":" + port + "...");
                System.out.println("[VPS-Client] Đang thử kết nối tới " + host + ":" + port + "...");

                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(90000); // Tự động ngắt kết nối và thử lại nếu mất liên lạc quá 90s

                socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                updateState(ConnectionState.CONNECTED, "Đã kết nối Socket tới " + host + ":" + port);
                System.out.println("[VPS-Client] Kết nối thành công! Đang gửi thông tin xác thực...");

                // Gửi bản tin xác thực đầu tiên (Handshake / Auth Packet)
                sendAuthHandshake();

                // Chuyển sang trạng thái Authenticated (sẽ nhận ACK từ server nếu có)
                updateState(ConnectionState.AUTHENTICATED, "Đã xác thực & Sẵn sàng truyền tin với VPS.");

                // Vòng lặp nhận dữ liệu từ VPS
                String incomingLine;
                while (running.get() && (incomingLine = socketIn.readLine()) != null) {
                    processIncomingMessage(incomingLine);
                }

                System.out.println("[VPS-Client] Kết nối với VPS đã bị đóng bởi máy chủ.");
            } catch (IOException e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                System.out.println("[VPS-Client] Lỗi kết nối (" + errMsg + "). Sẽ thử lại sau " + (RECONNECT_DELAY_MS / 1000) + " giây...");
                updateState(ConnectionState.DISCONNECTED, "Không thể kết nối VPS: " + errMsg);
            } catch (Exception e) {
                System.out.println("[VPS-Client] Sự cố ngoài dự kiến: " + e.getMessage());
                updateState(ConnectionState.DISCONNECTED, "Lỗi kết nối: " + e.getMessage());
            } finally {
                closeSocket();
            }

            // Chờ trước khi thử lại vòng lặp kết nối tiếp theo
            if (running.get()) {
                sleepInterruptibly(RECONNECT_DELAY_MS);
            }
        }
    }

    /**
     * Vòng lặp gửi thông điệp trong hàng đợi (Sender Thread).
     */
    private void senderLoop() {
        while (running.get()) {
            try {
                String jsonMessage = sendQueue.take();
                boolean sent = false;

                if (isConnected() && socketOut != null) {
                    try {
                        socketOut.println(jsonMessage);
                        socketOut.flush();
                        sent = !socketOut.checkError();
                        if (sent) {
                            System.out.println("[VPS-Client] -> Đã gửi JSON: " + jsonMessage);
                        } else {
                            System.err.println("[VPS-Client] ! Lỗi khi ghi dữ liệu ra socket -> Buộc ngắt kết nối để tự động kết nối lại.");
                            closeSocket();
                        }
                    } catch (Exception e) {
                        System.err.println("[VPS-Client] ! Lỗi truyền tin: " + e.getMessage());
                        closeSocket();
                    }
                } else {
                    System.out.println("[VPS-Client] ! Chưa kết nối VPS, bản tin được lưu trong hàng đợi hoặc bỏ qua: " + jsonMessage);
                }

                final boolean finalSent = sent;
                notifySent(jsonMessage, finalSent);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Gửi gói tin xác thực bí mật lên VPS khi vừa thiết lập TCP Socket.
     */
    private void sendAuthHandshake() {
        String authJson = JsonUtil.builder()
                .put("action", "AUTH")
                .put("auth_key", authPass)
                .put("password", authPass)
                .put("client_type", "PARENT_DESKTOP_APP")
                .put("app_name", "FamilyGuard")
                .put("version", "1.0")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();

        if (socketOut != null) {
            socketOut.println(authJson);
            socketOut.flush();
            System.out.println("[VPS-Client] -> Đã gửi gói tin xác thực (AUTH).");
        }
    }

    /**
     * Xử lý gói tin phản hồi nhận được từ VPS.
     * Tự động giải mã và nạp danh sách DNS logs khi nhận được gói "SYNC_LOGS" định kỳ từ VPS.
     */
    private void processIncomingMessage(String rawJson) {
        System.out.println("[VPS-Client] <- Nhận từ VPS: " + rawJson);

        // Tự động phân tích và đồng bộ bản ghi DNS logs từ VPS về ứng dụng phụ huynh
        if (rawJson != null && rawJson.contains("\"SYNC_LOGS\"")) {
            parseAndSyncLogs(rawJson);
        }

        // Báo cho các listener đã đăng ký
        for (VpsMessageListener listener : listeners) {
            try {
                listener.onMessageReceived(rawJson);
            } catch (Exception e) {
                System.err.println("[VPS-Client] Lỗi listener khi nhận tin: " + e.getMessage());
            }
        }
    }

    /**
     * Giải mã gói SYNC_LOGS định kỳ 60s từ dns_forwarder.cpp và lưu vào AccessLogs của DataService.
     */
    private void parseAndSyncLogs(String rawJson) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\{\\s*\"client_ip\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"domain\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"status\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"timestamp\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
                + "|"
                + "\\{\\s*\"domain\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"status\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"client_ip\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"timestamp\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
            );
            java.util.regex.Matcher matcher = pattern.matcher(rawJson);
            int count = 0;
            while (matcher.find()) {
                String domain, status, clientIp, timestamp;
                if (matcher.group(1) != null) {
                    clientIp = matcher.group(1);
                    domain = matcher.group(2);
                    status = matcher.group(3);
                    timestamp = matcher.group(4);
                } else {
                    domain = matcher.group(5);
                    status = matcher.group(6);
                    clientIp = matcher.group(7);
                    timestamp = matcher.group(8);
                }

                String action = "BLOCKED".equalsIgnoreCase(status) ? "ĐÃ CHẶN" : "CHO PHÉP";
                String id = "VPS-DNS-" + System.currentTimeMillis() + "-" + (++count);
                org.example.desktopver1.model.AccessLog log = new org.example.desktopver1.model.AccessLog(
                    id,
                    timestamp,
                    "Thiết bị (" + clientIp + ")",
                    domain,
                    "DNS Firewall",
                    action,
                    "Đồng bộ từ VPS"
                );

                runOnFxThread(() -> {
                    org.example.desktopver1.service.DataService.getInstance().addAccessLog(log);
                });
            }

            if (count > 0) {
                final int total = count;
                System.out.println("[VPS-Client] Đã đồng bộ thành công " + total + " nhật ký DNS từ VPS vào bảng điều khiển phụ huynh.");
            }
        } catch (Exception e) {
            System.err.println("[VPS-Client] Lỗi khi phân tích gói SYNC_LOGS: " + e.getMessage());
        }
    }

    private void sleepInterruptibly(int ms) {
        try {
            int elapsed = 0;
            while (elapsed < ms && running.get()) {
                Thread.sleep(100);
                elapsed += 100;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void closeSocket() {
        try {
            if (socketOut != null) {
                socketOut.close();
                socketOut = null;
            }
            if (socketIn != null) {
                socketIn.close();
                socketIn = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (IOException ignored) {
        }
    }

    private void updateState(ConnectionState newState, String message) {
        runOnFxThread(() -> {
            connectionState.set(newState);
            statusMessage.set(message);
        });

        for (VpsMessageListener listener : listeners) {
            try {
                listener.onStateChanged(newState, message);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifySent(String json, boolean success) {
        for (VpsMessageListener listener : listeners) {
            try {
                listener.onMessageSent(json, success);
            } catch (Exception ignored) {
            }
        }
    }

    private void runOnFxThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // Trường hợp chạy dưới Unit Test không có JavaFX Runtime
            action.run();
        }
    }

    // =========================================================================
    // CÁC HÀM GỬI CHUỖI JSON LÊN VPS KHI PHỤ HUYNH THAO TÁC TRÊN GIAO DIỆN
    // =========================================================================

    /**
     * Hàm cơ bản gửi một chuỗi JSON tùy ý lên VPS qua hàng đợi ngầm.
     * Hàm này trả về ngay lập tức, không gây lag/đơ giao diện phụ huynh.
     *
     * @param jsonString chuỗi JSON cần gửi
     */
    public void sendJson(String jsonString) {
        if (jsonString != null && !jsonString.trim().isEmpty()) {
            sendQueue.offer(jsonString.trim());
        }
    }

    /**
     * Gửi lệnh Bật/Tắt chế độ bảo vệ toàn hệ thống theo chuẩn SET_STATUS của VPS daemon.
     */
    public void sendProtectionToggle(boolean active) {
        String json = JsonUtil.builder()
                .put("action", "SET_STATUS")
                .put("enabled", active)
                .build();
        sendJson(json);
    }

    /**
     * Gửi lệnh Khóa mạng khẩn cấp (Panic Pause) tất cả thiết bị.
     */
    public void sendEmergencyPause(boolean pause) {
        String json = JsonUtil.builder()
                .put("action", "SET_STATUS")
                .put("enabled", !pause)
                .build();
        sendJson(json);
    }

    /**
     * Gửi lệnh Cắt mạng hoặc Khôi phục mạng cho một thiết bị con cụ thể.
     */
    public void sendDeviceBlock(String deviceId, String deviceName, String ip, String mac, boolean blocked) {
        String json = JsonUtil.builder()
                .put("action", "DEVICE_BLOCK")
                .put("deviceId", deviceId)
                .put("deviceName", deviceName)
                .put("ip", ip)
                .put("mac", mac)
                .put("blocked", blocked)
                .put("sender", "PARENT")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();
        sendJson(json);
    }

    /**
     * Gửi thao tác Cập nhật danh sách đen (Blacklist domain).
     * Chuẩn hóa theo đúng giao thức dns_forwarder.cpp trên VPS:
     * - Thêm domain: {"action":"ADD_BLACKLIST", "domain":"..."}
     * - Xóa domain: {"action":"REMOVE_BLACKLIST", "domain":"..."}
     *
     * @param domain tên miền (VD: tiktok.com)
     * @param operation "ADD" hoặc "REMOVE"
     */
    public void sendBlacklistDomain(String domain, String operation) {
        String action = "REMOVE".equalsIgnoreCase(operation) ? "REMOVE_BLACKLIST" : "ADD_BLACKLIST";
        String json = JsonUtil.builder()
                .put("action", action)
                .put("domain", domain)
                .build();
        sendJson(json);
    }

    /**
     * Gửi thao tác Cập nhật danh sách trắng (Whitelist domain).
     *
     * @param domain tên miền (VD: hocmai.vn)
     * @param operation "ADD" hoặc "REMOVE"
     */
    public void sendWhitelistDomain(String domain, String operation) {
        String action = "REMOVE".equalsIgnoreCase(operation) ? "REMOVE_WHITELIST" : "ADD_WHITELIST";
        String json = JsonUtil.builder()
                .put("action", action)
                .put("domain", domain)
                .build();
        sendJson(json);
    }

    /**
     * Gửi toàn bộ trạng thái hệ thống và danh sách lọc từ SQLite lên VPS (SYNC_RULES)
     */
    public void sendSyncRules(boolean filterEnabled, boolean emergencyPause, List<String> blacklist, List<String> whitelist, List<String> blockedIps) {
        StringBuilder bl = new StringBuilder("[");
        if (blacklist != null) {
            for (int i = 0; i < blacklist.size(); i++) {
                if (i > 0) bl.append(",");
                bl.append("\"").append(JsonUtil.escape(blacklist.get(i))).append("\"");
            }
        }
        bl.append("]");

        StringBuilder wl = new StringBuilder("[");
        if (whitelist != null) {
            for (int i = 0; i < whitelist.size(); i++) {
                if (i > 0) wl.append(",");
                wl.append("\"").append(JsonUtil.escape(whitelist.get(i))).append("\"");
            }
        }
        wl.append("]");

        StringBuilder ips = new StringBuilder("[");
        if (blockedIps != null) {
            for (int i = 0; i < blockedIps.size(); i++) {
                if (i > 0) ips.append(",");
                ips.append("\"").append(JsonUtil.escape(blockedIps.get(i))).append("\"");
            }
        }
        ips.append("]");

        String json = "{\"action\":\"SYNC_RULES\","
                + "\"filter_enabled\":" + filterEnabled + ","
                + "\"emergency_pause\":" + emergencyPause + ","
                + "\"blacklist\":" + bl.toString() + ","
                + "\"whitelist\":" + wl.toString() + ","
                + "\"blocked_ips\":" + ips.toString() + "}";
        sendJson(json);
    }

    /**
     * Gửi thao tác Bật/Tắt chặn danh mục nội dung nhạy cảm.
     */
    public void sendCategoryRuleUpdate(String ruleId, String ruleName, boolean blocked) {
        String json = JsonUtil.builder()
                .put("action", "CATEGORY_RULE_UPDATE")
                .put("ruleId", ruleId)
                .put("ruleName", ruleName)
                .put("blocked", blocked)
                .put("sender", "PARENT")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();
        sendJson(json);
    }

    /**
     * Gửi cấu hình hạn mức thời gian dùng máy (Daily Limits).
     */
    public void sendTimeLimits(double weekdayHours, double weekendHours) {
        String json = JsonUtil.builder()
                .put("action", "TIME_LIMIT_UPDATE")
                .put("weekdayHours", weekdayHours)
                .put("weekendHours", weekendHours)
                .put("sender", "PARENT")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();
        sendJson(json);
    }

    /**
     * Gửi cấu hình giờ giới nghiêm ban đêm (Bedtime Curfew).
     */
    public void sendCurfew(boolean enabled, String startTime, String endTime) {
        String json = JsonUtil.builder()
                .put("action", "CURFEW_UPDATE")
                .put("enabled", enabled)
                .put("startTime", startTime)
                .put("endTime", endTime)
                .put("sender", "PARENT")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();
        sendJson(json);
    }

    /**
     * Gửi thông tin thêm thiết bị mới trong gia đình lên VPS.
     */
    public void sendAddDevice(Device device) {
        String json = JsonUtil.builder()
                .put("action", "ADD_DEVICE")
                .put("deviceId", device.getId())
                .put("name", device.getName())
                .put("type", device.getType())
                .put("ip", device.getIpAddress())
                .put("mac", device.getMacAddress())
                .put("blocked", device.isBlocked())
                .put("sender", "PARENT")
                .put("timestamp", LocalDateTime.now().format(timeFormatter))
                .build();
        sendJson(json);
    }

    // =========================================================================
    // GETTERS & LISTENERS
    // =========================================================================

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public ConnectionState getConnectionState() {
        return connectionState.get();
    }

    public ReadOnlyObjectProperty<ConnectionState> connectionStateProperty() {
        return connectionState;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    public ReadOnlyStringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void addListener(VpsMessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(VpsMessageListener listener) {
        listeners.remove(listener);
    }
}
