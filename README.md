# 🛡FamilyGuard - Ứng Dụng Quản Lý Truy Cập Internet Của Trẻ Dành Cho Cha Mẹ

> **Đồ án môn học:** Lập Trình Mạng (LT Mạng)  
> **Phiên bản:** DesktopVer1 (JavaFX + Maven)

---

## 1. Giới thiệu tổng quan
Ứng dụng **FamilyGuard** được thiết kế nhằm cung cấp cho cha mẹ một bảng điều khiển (Dashboard) trực quan, hiện đại và mạnh mẽ để giám sát, phân luồng và quản lý thời lượng cũng như nội dung truy cập Internet của con cái trong gia đình.

---

## 🏗2. Kiến trúc mã nguồn đã được chuẩn hóa (MVC + Service Layer)

Source code đã được tái cấu trúc và chuẩn hóa hoàn toàn theo mô hình phân lớp rõ ràng:

```text
src/main/
├── java/
│   ├── module-info.java                          # Khai báo Java Platform Module System (JPMS, java.sql)
│   └── org/example/desktopver1/
│       ├── ParentalControlApp.java               # Điểm khởi chạy chính (Application Entry Point)
│       ├── controller/
│       │   └── MainController.java               # Điều phối chuyển màn hình, Top Header, Toast Notification
│       ├── database/                             # Tầng lưu trữ cơ sở dữ liệu SQLite
│       │   └── DatabaseManager.java              # Quản lý SQLite JDBC, Schema DDL, Seed dữ liệu giả & CRUD
│       ├── model/                                # Các đối tượng dữ liệu (Data Models)
│       │   ├── Device.java                       # Thiết bị con cái (Tên, IP, MAC, Trạng thái, Bị chặn?)
│       │   ├── AccessLog.java                    # Nhật ký truy vấn mạng (Thời gian, Thiết bị, URL, Phân loại, Hành động)
│       │   ├── CategoryRule.java                 # Quy tắc bộ lọc danh mục (Tên, Biểu tượng, Đã chặn, Thống kê)
│       │   └── TimeSchedule.java                 # Cấu hình giới hạn thời gian (Hạn mức ngày thường/cuối tuần, Khung giờ ngủ)
│       ├── service/
│       │   └── DataService.java                  # Tầng nghiệp vụ kết nối SQLite & State Store giao diện
│       └── view/                                 # Các màn hình giao diện (View Components)
│           ├── DashboardView.java                # Màn hình 1: Tổng quan trạng thái, 4 thẻ KPI, PieChart, Sự kiện gần nhất
│           ├── TimeManagementView.java           # Màn hình 2: Giới hạn giờ chơi, Giờ giới nghiêm ban đêm, Tiến độ các máy
│           ├── ContentFilterView.java            # Màn hình 3: Bộ lọc danh mục nhạy cảm, Blacklist / Whitelist tên miền
│           ├── DeviceManagerView.java            # Màn hình 4: Danh sách thiết bị trong LAN, Ngắt/Mở mạng từng máy
│           └── AccessLogsView.java               # Màn hình 5: Bảng nhật ký TableView chi tiết, Tìm kiếm, Lọc, Xuất CSV
├── resources/
│   └── org/example/desktopver1/
│       └── css/
│           └── style.css                         # Bộ CSS giao diện hiện đại (Flat UI, Tailwind Slate Palette)
└── test/
    └── java/org/example/desktopver1/database/
        ├── DatabaseManagerTest.java              # Unit tests kiểm tra Schema, CRUD & Seeding SQLite
        └── DataServiceDatabaseTest.java          # Integration tests kiểm tra đồng bộ DataService với SQLite
```

---

##  3. Các tính năng & Tối ưu hóa giao diện (UI/UX)

1. **Tổng quan (Dashboard & Cảnh báo)**:
   - **Banner trạng thái bảo vệ**: Nhận biết ngay hệ thống đang "Đang bảo vệ" hay "Tạm dừng".
   - **Nút "⚡ Khóa mạng khẩn cấp" (Panic Pause)**: Ngắt toàn bộ kết nối của tất cả các thiết bị con ngay lập tức chỉ với 1 click.
   - **4 Thẻ số liệu KPI**: Tổng giờ online hôm nay, Số lượt trang web độc hại đã chặn, Số thiết bị đang trực tuyến, Cảnh báo vi phạm.
   - **Biểu đồ tròn (PieChart)**: Trực quan hóa tỷ lệ thời gian truy cập (Học tập, Video, Game online, Mạng xã hội).
   - **Hoạt động gần nhất**: Hiển thị nhanh các sự kiện kiểm duyệt kèm nút chuyển nhanh sang trang Nhật ký.

2. **Quản lý thời gian (Time Management)**:
   - **Hạn mức hàng ngày**: Thanh trượt điều chỉnh số giờ tối đa cho ngày trong tuần (T2 - T6) và cuối tuần (T7, CN).
   - **Giờ giới nghiêm ban đêm (Bedtime Curfew)**: Tự động ngắt mạng từ 21:30 đến 06:00 sáng giúp trẻ ngủ đúng giờ.
   - **Thanh đo tiến độ (ProgressBar)**: Theo dõi trực tiếp thời gian từng thiết bị đã sử dụng trong ngày.
   - **Tính năng thưởng giờ**: Cộng thêm 30 phút khi con hoàn thành tốt bài tập.

3. **Bộ lọc nội dung (Content Filtering)**:
   - **Lọc theo 6 danh mục nhạy cảm**: Web 18+, Cờ bạc cá cược, Game online, Mạng xã hội & Hẹn hò, Bạo lực & Vũ khí, Video ngắn gây nghiện.
   - **Quản lý Blacklist (Tên miền cấm)**: Thêm/xóa trực tiếp tên miền muốn chặn.
   - **Quản lý Whitelist (Tên miền cho phép)**: Danh sách website học tập ưu tiên luôn được truy cập.
   - **Ép buộc SafeSearch**: Tự động lọc hình ảnh và video người lớn trên Google, Bing và YouTube.
   - **Cấu hình Family DNS**: Hỗ trợ Cloudflare Family 1.1.1.3, AdGuard DNS, NextDNS.

4. **Quản lý thiết bị (Device Management)**:
   - Hiển thị thông tin từng thiết bị: Tên máy, Loại thiết bị (Desktop, Laptop, Tablet, Smartphone), Địa chỉ IP, Địa chỉ MAC, Trạng thái kết nối.
   - **Ngắt / Mở mạng từng thiết bị riêng biệt**.
   - Hỗ trợ mô phỏng **Quét mạng LAN (Scan LAN)** và **Thêm thiết bị mới**.

5. **Nhật ký truy cập (Access Logs)**:
   - Bảng **TableView** hiện đại với các huy hiệu màu (Badge đỏ: "ĐÃ CHẶN", Badge xanh: "CHO PHÉP").
   - **Tìm kiếm tức thì** theo từ khóa (tên miền, thiết bị, phân loại).
   - **Bộ lọc theo trạng thái**: Xem tất cả, chỉ xem trang bị chặn, chỉ xem trang cho phép.
   - Hỗ trợ làm mới dữ liệu, dọn dẹp nhật ký và mô phỏng xuất báo cáo CSV.

6. **Hệ thống Toast Popup**:
   - Mọi thao tác (bật/tắt bảo vệ, khóa mạng, thêm tên miền...) đều có thông báo nổi nhẹ nhàng ở góc màn hình.

---

## Hướng dẫn chạy ứng dụng

### Cách 1: Click chạy nhanh trên Windows
Chỉ cần nhấp đúp vào tệp tin **`run.bat`** ở thư mục gốc của dự án.

### Cách 2: Chạy bằng lệnh qua Terminal / PowerShell
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd javafx:run
```

### Cách 3: Đóng gói tệp JAR
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd clean package -DskipTests
```
Tệp JAR sau khi đóng gói sẽ nằm tại `target/DesktopVer1-1.0-SNAPSHOT.jar`.

### Cách 4: Chạy toàn bộ Test tự động với dữ liệu giả SQLite & Mock VPS (JUnit 5)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\mvnw.cmd test
```
Hệ thống sẽ chạy toàn bộ 18 ca kiểm thử tự động trên `DatabaseManagerTest`, `DataServiceDatabaseTest`, `JsonUtilTest` và `VpsClientServiceTest` (bao gồm kiểm thử Gson parse SYNC_LOGS và lưu trữ SQLite).

---

## 🌐 4. Module Lập Trình Mạng: Luồng Ngầm & Giao Thức Kết Nối VPS

Hệ thống đã triển khai luồng ngầm mạng đạt chuẩn đề tài **Lập Trình Mạng**:
- **Địa chỉ VPS đích**: `103.74.101.176:9000`
- **Mật khẩu xác thực**: `AZvpsd6eb!5l@66`
- **Kiến trúc luồng ngầm (Background Thread)**:
  - `Connection Maintainer Thread` (Daemon): Khởi chạy khi mở app, tự động bắt tay TCP Socket tới VPS. Khi mạng chập chờn hoặc máy chủ khởi động lại, luồng tự động thử kết nối lại định kỳ mỗi 5 giây mà không làm đơ giao diện JavaFX.
  - `Message Sender Thread`: Quản lý `LinkedBlockingQueue` an toàn đa luồng. Khi phụ huynh thao tác trên UI, lệnh JSON được đẩy vào hàng đợi và truyền tới VPS ngay lập tức.
  - `Gói tin bắt tay xác thực (AUTH)`: Khi vừa kết nối Socket, app tự động gửi gói tin xác thực bí mật chứa mật khẩu.
  - `Live Status Badge`: Hiển thị trạng thái kết nối trực tiếp trên thanh Header (🔴 Mất kết nối VPS / 🟡 Đang kết nối / 🟢 Đã xác thực).

### Các lệnh JSON phát sinh từ thao tác của Phụ Huynh:
1. **Khóa mạng khẩn cấp**: `{"action": "EMERGENCY_PAUSE", "target": "ALL_DEVICES", "paused": true, "timestamp": "..."}`
2. **Bật/Tắt bảo vệ**: `{"action": "TOGGLE_PROTECTION", "enabled": true, "timestamp": "..."}`
3. **Cắt mạng thiết bị con**: `{"action": "DEVICE_BLOCK", "deviceId": "DEV-01", "mac": "...", "blocked": true, "timestamp": "..."}`
4. **Cập nhật Blacklist**: `{"action": "BLACKLIST_UPDATE", "domain": "facebook.com", "operation": "ADD", "timestamp": "..."}`
5. **Cập nhật Whitelist**: `{"action": "WHITELIST_UPDATE", "domain": "hocmai.vn", "operation": "ADD", "timestamp": "..."}`
6. **Chặn danh mục**: `{"action": "CATEGORY_RULE_UPDATE", "ruleId": "CAT-01", "blocked": true, "timestamp": "..."}`
7. **Cấu hình hạn mức giờ & giờ giới nghiêm**: `{"action": "TIME_LIMIT_UPDATE", ...}`, `{"action": "CURFEW_UPDATE", ...}`

### Khởi chạy Server lắng nghe trên VPS (Python Daemon):
```bash
python vps_server.py --port 9000
```

