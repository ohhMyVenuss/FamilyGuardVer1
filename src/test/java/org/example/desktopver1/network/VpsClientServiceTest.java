package org.example.desktopver1.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Luồng Ngầm VpsClientService (Background Thread & TCP Socket)")
class VpsClientServiceTest {

    private ServerSocket mockServer;
    private int mockPort;
    private final BlockingQueue<String> receivedOnServer = new LinkedBlockingQueue<>();
    private Thread serverThread;
    private volatile boolean serverRunning = true;

    private static final String TEST_DB_FILE = "target/test_vps_client.db";
    private VpsClientService clientService;
    private org.example.desktopver1.database.DatabaseManager testDbManager;
    private org.example.desktopver1.service.DataService testDataService;

    @BeforeEach
    void startMockServer() throws Exception {
        java.io.File dbFile = new java.io.File(TEST_DB_FILE);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        testDbManager = new org.example.desktopver1.database.DatabaseManager("jdbc:sqlite:" + TEST_DB_FILE);
        testDataService = new org.example.desktopver1.service.DataService(testDbManager, null);
        org.example.desktopver1.service.DataService.setInstance(testDataService);

        mockServer = new ServerSocket(0); // Cổng ngẫu nhiên khả dụng
        mockPort = mockServer.getLocalPort();
        serverRunning = true;

        serverThread = new Thread(() -> {
            try {
                while (serverRunning && !mockServer.isClosed()) {
                    Socket clientSocket = mockServer.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);

                    String line;
                    while ((line = reader.readLine()) != null) {
                        receivedOnServer.offer(line);
                        // Phản hồi lại ACK cho client
                        writer.println("{\"status\": \"ACK\", \"received\": true}");
                    }
                }
            } catch (Exception ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        serverRunning = false;
        if (clientService != null) {
            clientService.stop();
        }
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
        org.example.desktopver1.service.DataService.setInstance(null);
        java.io.File dbFile = new java.io.File(TEST_DB_FILE);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    @DisplayName("Luồng ngầm tự động kết nối, gửi bản tin AUTH chứa mật khẩu và gửi lệnh JSON")
    void testConnectionAndSendJson() throws Exception {
        String testPass = "AZvpsd6eb!5l@66";
        clientService = new VpsClientService("127.0.0.1", mockPort, testPass);

        CountDownLatch authLatch = new CountDownLatch(1);
        clientService.addListener(new VpsClientService.VpsMessageListener() {
            @Override
            public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                if (newState == VpsClientService.ConnectionState.AUTHENTICATED) {
                    authLatch.countDown();
                }
            }

            @Override
            public void onMessageReceived(String rawJson) {
            }

            @Override
            public void onMessageSent(String rawJson, boolean success) {
            }
        });

        // Khởi chạy luồng ngầm
        clientService.start();

        // Chờ kết nối thành công và xác thực
        boolean authenticated = authLatch.await(5, TimeUnit.SECONDS);
        assertTrue(authenticated, "Luồng ngầm phải kết nối và chuyển sang AUTHENTICATED thành công");

        // 1. Kiểm tra gói tin xác thực đầu tiên được gửi lên server
        String authPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(authPacket, "Server phải nhận được gói tin AUTH");
        assertTrue(authPacket.contains("\"action\": \"AUTH\""));
        assertTrue(authPacket.contains("\"auth_key\": \"" + testPass + "\""));

        // 2. Thao tác phụ huynh: Gửi lệnh Bật/Tắt bảo vệ (SET_STATUS)
        clientService.sendProtectionToggle(true);
        String statusPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(statusPacket, "Server phải nhận được gói tin SET_STATUS");
        assertTrue(statusPacket.contains("\"action\": \"SET_STATUS\""));
        assertTrue(statusPacket.contains("\"enabled\": true"));

        // 3. Thao tác phụ huynh: Thêm Blacklist (ADD_BLACKLIST)
        clientService.sendBlacklistDomain("tiktok.com", "ADD");
        String addBlacklistPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(addBlacklistPacket, "Server phải nhận được gói tin ADD_BLACKLIST");
        assertTrue(addBlacklistPacket.contains("\"action\": \"ADD_BLACKLIST\""));
        assertTrue(addBlacklistPacket.contains("\"domain\": \"tiktok.com\""));

        // 4. Thao tác phụ huynh: Gửi lệnh Cắt mạng thiết bị con
        clientService.sendDeviceBlock("DEV-01", "Laptop Con", "192.168.1.100", "00:11:22:33:44:55", true);
        String blockPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(blockPacket, "Server phải nhận được gói tin DEVICE_BLOCK");
        assertTrue(blockPacket.contains("\"action\": \"DEVICE_BLOCK\""));
        assertTrue(blockPacket.contains("\"deviceId\": \"DEV-01\""));
        assertTrue(blockPacket.contains("\"mac\": \"00:11:22:33:44:55\""));
        assertTrue(blockPacket.contains("\"blocked\": true"));

        // 4. Thao tác phụ huynh: Gửi chuỗi JSON tùy ý qua sendJson
        String customJson = "{\"action\": \"CUSTOM_FILTER\", \"domain\": \"tiktok.com\", \"mode\": \"STRICT\"}";
        clientService.sendJson(customJson);
        String customPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(customPacket, "Server phải nhận được chuỗi JSON gửi qua sendJson()");
        assertEquals(customJson, customPacket);
    }

    @Test
    @DisplayName("Kiểm tra xử lý mảng JSON SYNC_LOGS bằng Gson, chuyển thành object và lưu SQLite")
    void testGsonSyncLogsAndDatabaseStorage() throws Exception {
        String testPass = "AZvpsd6eb!5l@66";
        clientService = new VpsClientService("127.0.0.1", mockPort, testPass);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch syncLatch = new CountDownLatch(1);

        clientService.addListener(new VpsClientService.VpsMessageListener() {
            @Override
            public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                if (newState == VpsClientService.ConnectionState.AUTHENTICATED) {
                    authLatch.countDown();
                }
            }

            @Override
            public void onMessageReceived(String rawJson) {
                if (rawJson != null && rawJson.contains("SYNC_LOGS")) {
                    syncLatch.countDown();
                }
            }

            @Override
            public void onMessageSent(String rawJson, boolean success) {
            }
        });

        clientService.start();
        assertTrue(authLatch.await(5, TimeUnit.SECONDS));

        // Chuẩn bị gói SYNC_LOGS gửi từ server
        String syncJson = "{\"action\":\"SYNC_LOGS\",\"logs\":["
                + "{\"client_ip\":\"10.0.0.2\",\"domain\":\"tiktok.com\",\"status\":\"BLOCKED\",\"timestamp\":\"2026-09-27 18:00:00\"},"
                + "{\"client_ip\":\"10.0.0.2\",\"domain\":\"olm.vn\",\"status\":\"ALLOWED\",\"timestamp\":\"2026-09-27 18:00:02\"}"
                + "]}";

        // Gửi qua mock server socket
        receivedOnServer.poll(3, TimeUnit.SECONDS); // AUTH
        // Client Service tự xử lý incoming message từ server mock
        // Dùng reflection hoặc mock server writer để gửi dòng JSON xuống socket client
        // Gửi trực tiếp qua parseAndSyncLogs
        java.lang.reflect.Method method = VpsClientService.class.getDeclaredMethod("parseAndSyncLogs", String.class);
        method.setAccessible(true);
        method.invoke(clientService, syncJson);

        // Kiểm tra dữ liệu trong DataService và SQLite
        org.example.desktopver1.service.DataService dataService = org.example.desktopver1.service.DataService.getInstance();
        boolean foundTiktok = dataService.getAccessLogs().stream().anyMatch(l -> "tiktok.com".equals(l.getDomain()) && "ĐÃ CHẶN".equals(l.getAction()));
        boolean foundOlm = dataService.getAccessLogs().stream().anyMatch(l -> "olm.vn".equals(l.getDomain()) && "CHO PHÉP".equals(l.getAction()));

        assertTrue(foundTiktok, "Phải parse và nạp được bản ghi tiktok.com bị chặn vào danh sách");
        assertTrue(foundOlm, "Phải parse và nạp được bản ghi olm.vn được cho phép vào danh sách");

        // Kiểm tra lưu vào SQLite
        java.util.List<org.example.desktopver1.model.AccessLog> dbLogs = testDbManager.getAllAccessLogs();
        assertTrue(dbLogs.stream().anyMatch(l -> "tiktok.com".equals(l.getDomain())), "Bản ghi tiktok.com phải được lưu thành công vào SQLite");
    }

    @Test
    @DisplayName("Kiểm tra gửi yêu cầu CREATE_WIREGUARD_PEER và xử lý phản hồi cấp mã QR từ VPS")
    void testWireGuardPeerCreation() throws Exception {
        String testPass = "AZvpsd6eb!5l@66";
        clientService = new VpsClientService("127.0.0.1", mockPort, testPass);

        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch wgLatch = new CountDownLatch(1);
        final VpsClientService.WireGuardPeerResult[] receivedResult = new VpsClientService.WireGuardPeerResult[1];

        clientService.addListener(new VpsClientService.VpsMessageListener() {
            @Override
            public void onStateChanged(VpsClientService.ConnectionState newState, String message) {
                if (newState == VpsClientService.ConnectionState.AUTHENTICATED) {
                    authLatch.countDown();
                }
            }

            @Override
            public void onMessageReceived(String rawJson) {
            }

            @Override
            public void onMessageSent(String rawJson, boolean success) {
            }

            @Override
            public void onWireGuardPeerCreated(VpsClientService.WireGuardPeerResult result) {
                receivedResult[0] = result;
                wgLatch.countDown();
            }
        });

        clientService.start();
        assertTrue(authLatch.await(5, TimeUnit.SECONDS));

        // 1. Kiểm tra gửi yêu cầu tạo Peer
        clientService.sendCreateWireGuardPeer("iPhone Bé Minh", "Điện thoại");
        receivedOnServer.poll(3, TimeUnit.SECONDS); // Lấy bản tin AUTH
        String wgPacket = receivedOnServer.poll(5, TimeUnit.SECONDS);
        assertNotNull(wgPacket, "Server phải nhận được gói tin CREATE_WIREGUARD_PEER");
        assertTrue(wgPacket.contains("\"action\": \"CREATE_WIREGUARD_PEER\""));
        assertTrue(wgPacket.contains("\"deviceName\": \"iPhone Bé Minh\""));

        // 2. Mô phỏng VPS phản hồi kết quả cấp cấu hình thành công
        String mockResponse = "{\"success\":true,\"action\":\"CREATE_WIREGUARD_PEER\",\"deviceName\":\"iPhone Bé Minh\",\"deviceType\":\"Điện thoại\",\"assignedIp\":\"10.0.0.2\",\"configText\":\"[Interface]\\nPrivateKey = test\\nAddress = 10.0.0.2/24\",\"qrBase64\":\"iVBORw0KGgoAAAANSUhEUg==\",\"message\":\"Thành công\"}";
        java.lang.reflect.Method processMethod = VpsClientService.class.getDeclaredMethod("processIncomingMessage", String.class);
        processMethod.setAccessible(true);
        processMethod.invoke(clientService, mockResponse);

        assertTrue(wgLatch.await(5, TimeUnit.SECONDS), "Listener phải nhận được sự kiện onWireGuardPeerCreated");
        assertNotNull(receivedResult[0]);
        assertTrue(receivedResult[0].success);
        assertEquals("10.0.0.2", receivedResult[0].assignedIp);
        assertEquals("iPhone Bé Minh", receivedResult[0].deviceName);
        assertNotNull(receivedResult[0].configText);
        assertEquals("iVBORw0KGgoAAAANSUhEUg==", receivedResult[0].qrBase64);
    }
}
