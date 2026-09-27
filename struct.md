# Tổng quan Kiến trúc Hệ thống Family Guard

Hệ thống **Family Guard** được thiết kế theo mô hình phân tán (Distributed Architecture) kết hợp kiến trúc Client-Server, tận dụng công nghệ đường hầm mạng (VPN Tunneling) và giao thức phân giải tên miền (DNS) để kiểm soát, giám sát luồng truy cập Internet. Hệ thống loại bỏ hoàn toàn việc phải cài đặt các ứng dụng giám sát phức tạp (Native App) trên thiết bị của trẻ, thay vào đó kiểm soát luồng dữ liệu trực tiếp từ Tầng mạng (Network Layer).

## 1. Cấu trúc các thành phần hệ thống

Hệ thống bao gồm 3 nút mạng chính hoạt động độc lập và giao tiếp với nhau thông qua máy chủ trung tâm (VPS):

### A. Thiết bị của Con (Client / End-User)
* **Thành phần cài đặt:** Chỉ yêu cầu duy nhất ứng dụng **WireGuard VPN** (có sẵn trên iOS, Android, Windows, macOS).
* **Cơ chế hoạt động:** Khi kích hoạt VPN, toàn bộ lưu lượng mạng (Traffic) và các truy vấn phân giải tên miền (DNS Query) của thiết bị sẽ bị đóng gói và ép chuyển hướng (force-route) qua đường hầm mã hóa đến Máy chủ trung tâm (VPS).
* **Giao diện tương tác:** Nếu thiết bị truy cập vào một trang web bị cấm, truy vấn sẽ bị điều hướng (DNS Spoofing) về IP ảo của VPS, từ đó trình duyệt sẽ hiển thị trang HTML Cảnh báo (Captive Portal / Block Page).

### B. Lõi Mạng Trung Tâm (Cloud VPS Ubuntu)
Đây là "trái tim" của hệ thống, xử lý hàng ngàn luồng dữ liệu theo thời gian thực với tài nguyên tối ưu (1GB RAM). Lõi mạng bao gồm các tiến trình chạy song song:
* **WireGuard Server:** Lắng nghe ở cổng UDP `51820`, cấp phát IP nội bộ (ví dụ: `10.0.0.1` cho Server, `10.0.0.2` cho thiết bị của con).
* **Java Core - DNS Proxy (UDP Port 53):** Lắng nghe các gói tin DNS từ máy con. Tách byte nhị phân để lấy tên miền, đối chiếu với danh sách cấm (Blacklist) được lưu trên RAM (`HashSet`). Nếu hợp lệ, chuyển tiếp truy vấn ra `8.8.8.8` (Google DNS); nếu vi phạm, giả mạo gói phản hồi trả về địa chỉ IP của VPS (`10.0.0.1`).
* **Java Core - TCP IPC Server (TCP Port 9000):** Đóng vai trò là cầu nối giao tiếp thời gian thực với phần mềm của phụ huynh. Tiếp nhận lệnh JSON (thêm/xóa tên miền, thiết lập thời gian) và đồng bộ mảng dữ liệu nhật ký (Log Sync) về máy phụ huynh.
* **Mini HTTP Server (TCP Port 80):** Trả về trang HTML cảnh báo và xử lý các API yêu cầu (như nút "Xin thêm giờ") từ trình duyệt của con.

### C. Phần mềm Quản trị (Máy tính Phụ Huynh)
* **Nền tảng:** Ứng dụng Desktop phát triển bằng **JavaFX**.
* **Cơ sở dữ liệu cục bộ (SQLite):** Lưu trữ dài hạn các quy tắc (Rules) và toàn bộ lịch sử truy cập (Access Logs) để tránh làm quá tải VPS. Dữ liệu từ SQLite được dùng để render các biểu đồ thống kê (PieChart, BarChart).
* **Giao thức giao tiếp (TCP Client):** Chạy ngầm một luồng kết nối liên tục (persistent connection) đến TCP Port 9000 của VPS, đảm bảo cập nhật lệnh và nhận thông báo theo thời gian thực (Real-time).

---

## 2. Tại sao lựa chọn WireGuard VPN?

Trong quá trình thiết kế giải pháp mạng, **WireGuard** được lựa chọn làm giao thức lõi thay vì OpenVPN hay IPsec do những đặc tính kỹ thuật vượt trội, đặc biệt phù hợp với bài toán quản lý thiết bị gia đình:

* **Bắt buộc định tuyến (Force Tunneling) hoàn hảo:** WireGuard cho phép cấu hình `AllowedIPs = 0.0.0.0/0, ::/0` dễ dàng. Tính năng này ép thiết bị của trẻ không thể bỏ qua (bypass) hệ thống DNS nội bộ để sử dụng DNS ngoài (như 1.1.1.1 hay 8.8.8.8), đảm bảo mọi truy vấn bắt buộc phải đi qua cổng kiểm duyệt UDP 53 của hệ thống.
* **Hiệu suất cao & Tiết kiệm tài nguyên:** Khác với OpenVPN chứa hàng trăm ngàn dòng code, WireGuard chỉ có khoảng 4.000 dòng code chạy trực tiếp trong nhân (Kernel-space) của hệ điều hành Linux. Điều này giúp hệ thống hoạt động cực kỳ mượt mà, không gây hiện tượng nghẽn cổ chai (bottleneck) hay tràn RAM trên Cloud VPS dung lượng nhỏ (1GB).
* **Mã hóa tối ưu (Cryptokey Routing):** WireGuard sử dụng các thuật toán mã hóa hiện đại và siêu nhẹ (như ChaCha20, Poly1305, Curve25519). Nhờ đó, tốc độ lướt web và xem video trên thiết bị của con không bị suy giảm đáng kể khi bật VPN.
* **Tính cơ động (Roaming):** Thiết bị di động của trẻ thường xuyên thay đổi mạng (từ Wi-Fi ở nhà sang 4G khi ra đường, hoặc Wi-Fi trường học). WireGuard xử lý việc chuyển đổi địa chỉ IP này (Roaming) một cách liền mạch, kết nối VPN không bị ngắt quãng và không yêu cầu thực hiện lại quá trình bắt tay (Handshake) phức tạp.
* **Loại bỏ chi phí phát triển Mobile App:** Thay vì phải code hai ứng dụng Native phức tạp cho iOS và Android (cần xin quyền truy cập Accessibility hay MDM rất khắt khe để giám sát mạng), việc sử dụng app WireGuard có sẵn trên mọi Store giúp hệ thống đạt chuẩn "Plug-and-Play", triển khai ngay lập tức chỉ bằng một mã QR cấu hình.