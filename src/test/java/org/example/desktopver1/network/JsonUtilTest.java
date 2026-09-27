package org.example.desktopver1.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử JsonUtil (Serialization và Builder thuần)")
class JsonUtilTest {

    @Test
    @DisplayName("Kiểm tra serialize Map sang JSON đúng chuẩn")
    void testMapToJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", "TOGGLE_PROTECTION");
        map.put("enabled", true);
        map.put("count", 42);

        String json = JsonUtil.toJson(map);
        assertTrue(json.contains("\"action\": \"TOGGLE_PROTECTION\""));
        assertTrue(json.contains("\"enabled\": true"));
        assertTrue(json.contains("\"count\": 42"));
        assertTrue(json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    @DisplayName("Kiểm tra escape các ký tự đặc biệt trong JSON")
    void testEscape() {
        String input = "Hello \"World\"\nLine 2\\Path";
        String escaped = JsonUtil.escape(input);
        assertEquals("Hello \\\"World\\\"\\nLine 2\\\\Path", escaped);
    }

    @Test
    @DisplayName("Kiểm tra Fluent Builder tạo JSON cho các thao tác phụ huynh")
    void testJsonBuilder() {
        String json = JsonUtil.builder()
                .put("action", "DEVICE_BLOCK")
                .put("deviceId", "DEV-01")
                .put("mac", "AA:BB:CC:DD:EE:FF")
                .put("blocked", true)
                .put("auth_pass", "AZvpsd6eb!5l@66")
                .build();

        assertTrue(json.contains("\"action\": \"DEVICE_BLOCK\""));
        assertTrue(json.contains("\"deviceId\": \"DEV-01\""));
        assertTrue(json.contains("\"mac\": \"AA:BB:CC:DD:EE:FF\""));
        assertTrue(json.contains("\"blocked\": true"));
        assertTrue(json.contains("\"auth_pass\": \"AZvpsd6eb!5l@66\""));
    }
}
