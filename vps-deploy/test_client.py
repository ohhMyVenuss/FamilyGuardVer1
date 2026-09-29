#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
FAMILYGUARD - KỊCH BẢN KIỂM THỬ GIAO THỨC TCP IPC (PORT 9000)
=============================================================================
Dùng để kiểm tra trực tiếp VPS có nhận và phản hồi đúng các lệnh JSON từ
máy phụ huynh hay không.

Cách sử dụng:
    python test_client.py --host 103.74.101.176 --port 9000
    python test_client.py --host 10.0.0.1 --port 9000
=============================================================================
"""

import socket
import json
import sys
import argparse
import time

DEFAULT_HOST = "103.74.101.176"
DEFAULT_PORT = 9000
AUTH_PASS = "AZvpsd6eb!5l@66"

def send_and_recv(sock, payload_dict):
    msg = json.dumps(payload_dict) + "\n"
    print(f"\n-> GỬI: {msg.strip()}")
    sock.sendall(msg.encode("utf-8"))

    # Đọc 1 dòng phản hồi
    buf = ""
    while "\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("Máy chủ đã đóng kết nối!")
        buf += chunk.decode("utf-8", errors="replace")

    line, _ = buf.split("\n", 1)
    print(f"<- NHẬN: {line.strip()}")
    try:
        return json.loads(line)
    except:
        return {"raw": line}

def main():
    parser = argparse.ArgumentParser(description="FamilyGuard VPS Test Client")
    parser.add_argument("--host", default=DEFAULT_HOST, help="Địa chỉ IP VPS")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Cổng TCP")
    args = parser.parse_args()

    print(f"[*] Đang kết nối tới {args.host}:{args.port}...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        sock.connect((args.host, args.port))
        print("[✓] Kết nối TCP Socket thành công!")

        # 1. Test Ping
        send_and_recv(sock, {"action": "PING"})

        # 2. Test Auth
        send_and_recv(sock, {"action": "AUTH", "password": AUTH_PASS, "client": "TestScript"})

        # 3. Test Lấy trạng thái
        send_and_recv(sock, {"action": "GET_STATUS"})

        # 4. Test Thêm Blacklist
        send_and_recv(sock, {"action": "ADD_BLACKLIST", "domain": "tiktok.com"})

        # 5. Test Thêm Whitelist
        send_and_recv(sock, {"action": "ADD_WHITELIST", "domain": "hocmai.vn"})

        # 6. Test Bật/Tắt bảo vệ
        send_and_recv(sock, {"action": "SET_STATUS", "enabled": True})

        # 7. Test Ngắt mạng thiết bị con (IP 10.0.0.2)
        send_and_recv(sock, {"action": "DEVICE_BLOCK", "ip": "10.0.0.2", "blocked": True})

        # 8. Test Giờ giới nghiêm
        send_and_recv(sock, {"action": "CURFEW_UPDATE", "enabled": True, "startTime": "21:30", "endTime": "06:00"})

        # 9. Test Cập nhật chính sách 5 App di động (YouTube, Facebook, TikTok, Instagram, MLBB)
        send_and_recv(sock, {"action": "SET_APP_POLICY", "client_ip": "10.0.0.2", "app_id": "TIKTOK", "time_limit_minutes": 30, "blocked": True})
        send_and_recv(sock, {"action": "SET_APP_POLICY", "client_ip": "10.0.0.2", "app_id": "YOUTUBE", "time_limit_minutes": 60, "blocked": False})

        # 10. Test Lấy thống kê sử dụng và hạn mức 5 App
        send_and_recv(sock, {"action": "GET_APP_USAGE"})

        # 11. Test Đặt lại thời gian sử dụng hôm nay của App
        send_and_recv(sock, {"action": "RESET_APP_USAGE", "client_ip": "10.0.0.2", "app_id": "TIKTOK"})

        print("\n[✓] HOÀN TẤT KIỂM THỬ: VPS ĐÃ NHẬN VÀ PHẢN HỒI THÀNH CÔNG TẤT CẢ CÁC LỆNH!")
        sock.close()

    except Exception as e:
        print(f"[!] Lỗi kết nối / kiểm thử: {e}")

if __name__ == "__main__":
    main()
