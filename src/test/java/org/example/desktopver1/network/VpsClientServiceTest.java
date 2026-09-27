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

    private VpsClientService clientService;

    @BeforeEach
    void startMockServer() throws Exception {
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
}
