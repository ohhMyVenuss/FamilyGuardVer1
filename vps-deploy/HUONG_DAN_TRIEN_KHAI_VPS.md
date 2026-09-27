# 🚀 HƯỚNG DẪN TRIỂN KHAI VÀ VẬN HÀNH HỆ THỐNG FAMILYGUARD TRÊN VPS
**Đồ án môn học:** Lập Trình Mạng (LT Mạng)  
**Địa chỉ VPS mục tiêu:** `103.74.101.176`  
**Mật khẩu VPS:** `AZvpsd6eb!5l@66`  
**Cổng dịch vụ:** WireGuard `51820/UDP`, DNS Firewall `53/UDP`, TCP Control `9000/TCP`, HTTP Block `80/TCP`

---

## 1. Cơ Chế Hoạt Động Cốt Lõi (WireGuard + JavaFX)

Theo đúng mô hình phân tán trong [`struct.md`](file:///d:/OhhhMyVenuss/LT%20Mang/DesktopVer1/struct.md):
```text
[Thiết bị của Trẻ]
(4G / Wi-Fi trường học / Quán cafe)
       │
       │ (Đường hầm mã hóa WireGuard VPN)
       ▼
[Cloud VPS Ubuntu: 103.74.101.176]
  ├── WireGuard Server (Port 51820 UDP) ──────> Gán IP 10.0.0.1 (VPS) & 10.0.0.2 (Con)
  ├── DNS Firewall Core (Port 53 UDP) ────────> Bóc tách tên miền, chặn theo Blacklist / Giờ ngủ
  ├── Mini HTTP Block Server (Port 80 TCP) ───> Trả trang HTML cảnh báo & nút "Xin thêm giờ"
  └── TCP IPC Control Server (Port 9000 TCP) ──> Tiếp nhận lệnh JSON từ máy Phụ Huynh
                               ▲
                               │ (NDJSON qua Internet hoặc VPN)
                               │
            [Ứng dụng JavaFX Bố Mẹ (DesktopVer1)]
            - Quản lý Blacklist / Whitelist
            - Khóa mạng khẩn cấp / Hạn mức giờ
            - Nhận SYNC_LOGS định kỳ hiển thị biểu đồ
```

**Ưu điểm vượt trội:**
1. **Không giới hạn mạng LAN:** Dù trẻ dùng Wi-Fi ở nhà, Wi-Fi trường học hay bật 4G/5G khi ra đường, tính năng Roaming của WireGuard vẫn giữ đường hầm kết nối liên tục về VPS.
2. **Không thể lách luật (Bypass):** Tính năng Force Tunneling ép toàn bộ truy vấn DNS phải qua cổng 53 của VPS, trẻ không thể tự đổi DNS sang 8.8.8.8 hay 1.1.1.1 để vượt rào.
3. **Tiết kiệm tài nguyên:** Lõi C++ và WireGuard Kernel module chỉ tiêu tốn chưa đến 50MB RAM trên VPS 1GB.

---

## 2. Hướng Dẫn Triển Khai Lên VPS Chỉ Với 3 Bước

### Bước 1: Kết nối SSH vào VPS
Mở Terminal hoặc PowerShell trên máy tính của bạn và chạy:
```bash
ssh root@103.74.101.176
```
*(Nhập mật khẩu: `AZvpsd6eb!5l@66`)*

---

### Bước 2: Tải thư mục triển khai lên VPS
Bạn có thể copy thư mục `vps-deploy` từ máy tính lên VPS bằng lệnh `scp` (chạy từ terminal máy tính của bạn):
```bash
# Đứng tại thư mục dự án DesktopVer1 trên máy tính
scp -r vps-deploy root@103.74.101.176:/root/
```

*Hoặc nếu trên VPS đã có Git / clone mã nguồn, chỉ cần chuyển vào thư mục:*
```bash
cd /root/vps-deploy
```

---

### Bước 3: Chạy script tự động triển khai `deploy.sh`
Trên cửa sổ SSH của VPS, gõ:
```bash
chmod +x deploy.sh
sudo ./deploy.sh
```

**Script `deploy.sh` sẽ tự động thực hiện 100% công việc:**
1. Cài đặt các thư viện cần thiết (`g++`, `nlohmann-json3-dev`, `wireguard`, `iptables`, `qrencode`).
2. Kích hoạt chuyển tiếp mạng IPv4 trong nhân Linux (`net.ipv4.ip_forward = 1`).
3. Khởi tạo cặp khóa mật mã (Private/Public Keys) cho Server, Con và Bố Mẹ.
4. Biên dịch mã nguồn C++ `dns_forwarder.cpp` thành tệp thực thi `/opt/familyguard/dns_forwarder`.
5. Tạo và kích hoạt các Systemd Service (`familyguard.service`, `familyguard-http.service`, `wg-quick@wg0`).
6. Cấu hình tường lửa mở các cổng: `51820/UDP`, `53/UDP`, `9000/TCP`, `80/TCP`.
7. **Tự động in MÃ QR cấu hình WireGuard của máy con ngay trên màn hình console!**

---

## 3. Cách Kết Nối Thiết Bị Của Con Bằng Mã QR

1. Trên điện thoại/máy tính bảng của trẻ, tải ứng dụng **WireGuard** từ **App Store** (iOS) hoặc **Google Play** (Android).
2. Mở ứng dụng WireGuard, nhấn dấu `+` và chọn **Scan from QR code** (Quét từ mã QR).
3. Quét mã QR đang hiển thị trên màn hình terminal của VPS.
4. Đặt tên profile (ví dụ: `FamilyGuard`).
5. Gạt nút **Bật VPN**.
   👉 **Từ lúc này, thiết bị của trẻ đã được bảo vệ hoàn toàn:**
   - Mọi lượt lướt web đều được giám sát.
   - Khi vào trang web cấm (như `tiktok.com`), máy sẽ bị chặn ngay lập tức hoặc điều hướng về trang cảnh báo tiếng Việt.

---

## 4. Cách Vận Hành Ứng Dụng JavaFX Của Phụ Huynh

1. Khởi chạy ứng dụng JavaFX trên máy tính phụ huynh:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
   .\mvnw.cmd javafx:run
   ```
2. Ngay khi mở app, luồng ngầm `VpsClientService` sẽ tự động kết nối TCP tới `103.74.101.176:9000`.
3. Nhìn lên thanh Header trên cùng:
   - Khi thấy biểu tượng: `🟢 VPS: Đã kết nối VPS (Online)` hoặc `🟢 Đã xác thực`, nghĩa là đường truyền điều khiển đã thông suốt 100%!
4. **Thử nghiệm các thao tác phụ huynh:**
   - **Thêm tên miền cấm:** Vào tab **🛡️ Bộ lọc nội dung**, nhập `facebook.com` hoặc `tiktok.com` và nhấn **Thêm chặn**. Lệnh `ADD_BLACKLIST` được gửi lên VPS trong 0.1 giây. Máy của trẻ sẽ bị chặn tức thì!
   - **Gỡ tên miền:** Nhấn nút xóa cạnh tên miền, lệnh `REMOVE_BLACKLIST` gửi lên VPS và tên miền được mở lại.
   - **Khóa mạng khẩn cấp:** Tại Dashboard, nhấn nút đỏ `⚡ KHÓA MẠNG KHẨN CẤP`, toàn bộ DNS của con sẽ bị ngắt (RCODE REFUSED) cho đến khi phụ huynh mở lại.
   - **Đồng bộ nhật ký:** Cứ mỗi 60 giây, VPS tự động gửi gói `SYNC_LOGS` chứa các truy cập mới nhất của con về máy bố mẹ, hiển thị trên bảng **📋 Nhật ký truy cập**.

---

## 5. Các Lệnh Kiểm Tra Trạng Thái & Quản Trị Trên VPS

| Mục đích kiểm tra | Lệnh thực thi trên VPS |
| :--- | :--- |
| **Xem log phân giải DNS và lệnh từ cha mẹ theo thời gian thực** | `journalctl -u familyguard.service -f` |
| **Kiểm tra trạng thái WireGuard VPN và các máy con đang nối** | `wg show` |
| **Kiểm tra các cổng mạng đang mở (53, 9000, 51820, 80)** | `netstat -tulnp` hoặc `ss -tulnp` |
| **Chạy script test thử tất cả các lệnh JSON** | `python3 test_client.py` |
| **Khởi động lại toàn bộ dịch vụ FamilyGuard** | `systemctl restart familyguard.service` |
| **Xem lại mã QR của máy con bất cứ lúc nào** | `qrencode -t ansiutf8 < /opt/familyguard/child_client.conf` |
