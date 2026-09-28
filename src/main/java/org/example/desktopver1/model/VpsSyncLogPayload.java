package org.example.desktopver1.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Lớp DTO ánh xạ mảng JSON SYNC_LOGS nhận được từ luồng Socket TCP VPS (Cổng 9000).
 * Được parse tự động bằng thư viện Gson.
 */
public class VpsSyncLogPayload {

    @SerializedName("action")
    private String action;

    @SerializedName("logs")
    private List<VpsLogEntry> logs;

    public VpsSyncLogPayload() {
    }

    public VpsSyncLogPayload(String action, List<VpsLogEntry> logs) {
        this.action = action;
        this.logs = logs;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<VpsLogEntry> getLogs() {
        return logs;
    }

    public void setLogs(List<VpsLogEntry> logs) {
        this.logs = logs;
    }

    public static class VpsLogEntry {
        @SerializedName("client_ip")
        private String clientIp;

        @SerializedName("domain")
        private String domain;

        @SerializedName("status")
        private String status;

        @SerializedName("timestamp")
        private String timestamp;

        public VpsLogEntry() {
        }

        public VpsLogEntry(String clientIp, String domain, String status, String timestamp) {
            this.clientIp = clientIp;
            this.domain = domain;
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
