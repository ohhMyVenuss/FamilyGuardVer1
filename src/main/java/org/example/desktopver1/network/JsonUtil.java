package org.example.desktopver1.network;

import java.util.Map;

/**
 * Tiện ích hỗ trợ tạo và xử lý chuỗi JSON thuần (Zero-dependency).
 * Đảm bảo tương thích hoàn hảo với Java 24 Platform Module System (JPMS).
 */
public class JsonUtil {

    /**
     * Escape các ký tự đặc biệt trong chuỗi theo chuẩn JSON RFC 8259.
     */
    public static String escape(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Chuyển đổi Map dữ liệu thành chuỗi JSON tiêu chuẩn.
     */
    public static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("\"").append(escape(entry.getKey())).append("\": ");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static String valueToJson(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String s) {
            return "\"" + escape(s) + "\"";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castMap = (Map<String, Object>) m;
            return toJson(castMap);
        }
        return "\"" + escape(val.toString()) + "\"";
    }

    /**
     * Builder giúp xây dựng chuỗi JSON trực quan theo Fluent Pattern.
     */
    public static class Builder {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        public Builder put(String key, String value) {
            appendKey(key);
            sb.append(value == null ? "null" : "\"" + escape(value) + "\"");
            return this;
        }

        public Builder put(String key, Number value) {
            appendKey(key);
            sb.append(value == null ? "null" : value.toString());
            return this;
        }

        public Builder put(String key, Boolean value) {
            appendKey(key);
            sb.append(value == null ? "null" : value.toString());
            return this;
        }

        public Builder putRaw(String key, String rawJson) {
            appendKey(key);
            sb.append(rawJson == null ? "null" : rawJson);
            return this;
        }

        private void appendKey(String key) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("\"").append(escape(key)).append("\": ");
            first = false;
        }

        public String build() {
            return sb.toString() + "}";
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
