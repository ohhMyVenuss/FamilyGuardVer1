#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
FAMILYGUARD - MÁY CHỦ GIÁM SÁT TRUY CẬP INTERNET (VPS SERVER DAEMON)
=============================================================================
Đồ án môn học: Lập Trình Mạng
Địa chỉ VPS mục tiêu: 103.74.101.176:9000
Mật khẩu bảo mật:     AZvpsd6eb!5l@66

Hướng dẫn chạy trên VPS Linux hoặc máy cục bộ:
    python vps_server.py
hoặc:
    python3 vps_server.py --port 9000
=============================================================================
"""

import sys
import socket
import threading
import json
import datetime
import argparse

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 9000
AUTH_PASSWORD = "AZvpsd6eb!5l@66"

# Danh sách client đang kết nối
active_clients = []
clients_lock = threading.Lock()

def log(msg, level="INFO"):
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    prefix = {
        "INFO":  "\033[94m[*]\033[0m",
        "AUTH":  "\033[92m[✓ AUTH]\033[0m",
        "CMD":   "\033[93m[⚡ CMD]\033[0m",
        "WARN":  "\033[91m[! WARN]\033[0m",
        "ALERT": "\033[95m[🚨 ALERT]\033[0m"
    }.get(level, "[*]")
    print(f"{now} {prefix} {msg}")

def handle_client(client_socket, client_address):
    ip, port = client_address
    log(f"Kết nối mới từ: {ip}:{port}", "INFO")
    authenticated = False

    with clients_lock:
        active_clients.append(client_socket)

    try:
        # Buffer đọc từng dòng ký tự UTF-8
        buffer = ""
        while True:
            data = client_socket.recv(4096)
            if not data:
                break
            
            buffer += data.decode("utf-8", errors="replace")
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue

                try:
                    payload = json.loads(line)
                except json.JSONDecodeError:
                    log(f"[{ip}] Nhận dữ liệu không phải JSON: {line}", "WARN")
                    continue

                action = payload.get("action", "")

                # 1. Xử lý bản tin xác thực đầu tiên (AUTH)
                if action == "AUTH":
                    sent_pass = payload.get("password") or payload.get("auth_key")
                    if sent_pass == AUTH_PASSWORD:
                        authenticated = True
                        log(f"[{ip}] Xác thực THÀNH CÔNG với mật khẩu bí mật!", "AUTH")
                        resp = {
                            "status": "AUTH_SUCCESS",
                            "message": "Xác thực thành công! VPS sẵn sàng nhận lệnh điều khiển.",
                            "vps_ip": "103.74.101.176",
                            "server_time": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                        }
                        send_response(client_socket, resp)
                    else:
                        log(f"[{ip}] Xác thực THẤT BẠI! Mật khẩu không đúng: {sent_pass}", "WARN")
                        resp = {
                            "status": "AUTH_FAILED",
                            "message": "Mật khẩu xác thực không đúng!"
                        }
                        send_response(client_socket, resp)
                        return

                # 2. Xử lý các lệnh điều khiển từ phụ huynh
                elif authenticated:
                    log(f"[{ip}] Nhận lệnh thao tác từ Phụ Huynh: {action}", "CMD")
                    print(json.dumps(payload, ensure_ascii=False, indent=2))

                    # Tường lửa Linux iptables tầng Layer 3/4
                    import subprocess, re
                    if action == "DEVICE_BLOCK":
                        dev_ip = payload.get("ip")
                        blocked = payload.get("blocked", True)
                        if dev_ip and re.match(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$", dev_ip):
                            if blocked:
                                subprocess.run(f"iptables -C FORWARD -s {dev_ip} -j DROP 2>/dev/null || iptables -I FORWARD -s {dev_ip} -j DROP", shell=True)
                                subprocess.run(f"conntrack -D -s {dev_ip} 2>/dev/null", shell=True)
                                log(f"[FIREWALL] Đã DROP gói tin & xóa conntrack IP: {dev_ip}", "CMD")
                            else:
                                subprocess.run(f"while iptables -D FORWARD -s {dev_ip} -j DROP 2>/dev/null; do :; done", shell=True)
                                log(f"[FIREWALL] Đã mở lại FORWARD cho IP: {dev_ip}", "CMD")

                    elif action == "EMERGENCY_PAUSE":
                        pause = payload.get("paused", payload.get("enabled", True))
                        if pause:
                            subprocess.run("iptables -C FORWARD -i wg0 -j DROP 2>/dev/null || iptables -I FORWARD -i wg0 -j DROP", shell=True)
                            subprocess.run("conntrack -F 2>/dev/null", shell=True)
                            log("[FIREWALL] Đã kích hoạt KHÓA MẠNG KHẨN CẤP (DROP wg0)", "CMD")
                        else:
                            subprocess.run("while iptables -D FORWARD -i wg0 -j DROP 2>/dev/null; do :; done", shell=True)
                            log("[FIREWALL] Đã mở lại mạng toàn hệ thống", "CMD")

                    resp = {
                        "status": "SUCCESS",
                        "action_ack": action,
                        "message": f"VPS đã thực thi thành công lệnh: {action}",
                        "timestamp": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    }
                    send_response(client_socket, resp)

                else:
                    log(f"[{ip}] Từ chối lệnh vì client chưa gửi gói AUTH!", "WARN")
                    resp = {"status": "UNAUTHORIZED", "message": "Vui lòng xác thực trước khi gửi lệnh."}
                    send_response(client_socket, resp)

    except (ConnectionResetError, BrokenPipeError):
        log(f"Client {ip}:{port} đã ngắt kết nối đột ngột.", "INFO")
    except Exception as e:
        log(f"Lỗi khi xử lý client {ip}:{port}: {e}", "WARN")
    finally:
        with clients_lock:
            if client_socket in active_clients:
                active_clients.remove(client_socket)
        try:
            client_socket.close()
        except:
            pass
        log(f"Đã đóng phiên kết nối của {ip}:{port}.", "INFO")

def send_response(sock, payload_dict):
    try:
        msg = json.dumps(payload_dict, ensure_ascii=False) + "\n"
        sock.sendall(msg.encode("utf-8"))
    except Exception as e:
        log(f"Không thể gửi phản hồi tới client: {e}", "WARN")

def broadcast_alert(alert_dict):
    """Gửi cảnh báo hoặc thông báo tới tất cả phụ huynh đang kết nối."""
    msg = json.dumps(alert_dict, ensure_ascii=False) + "\n"
    with clients_lock:
        for s in active_clients:
            try:
                s.sendall(msg.encode("utf-8"))
            except:
                pass

def main():
    parser = argparse.ArgumentParser(description="FamilyGuard VPS Monitoring Server")
    parser.add_argument("--host", default=DEFAULT_HOST, help="Địa chỉ IP lắng nghe (mặc định 0.0.0.0)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Cổng lắng nghe (mặc định 9000)")
    args = parser.parse_args()

    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_sock.bind((args.host, args.port))
        server_sock.listen(10)
        log(f"===========================================================", "INFO")
        log(f"🛡️  FAMILYGUARD VPS SERVER ĐANG CHẠY TRÊN CỔNG: {args.port}", "INFO")
        log(f"🔑 Mật khẩu xác thực: {AUTH_PASSWORD}", "INFO")
        log(f"📡 Đang chờ ứng dụng cha mẹ kết nối tới...", "INFO")
        log(f"===========================================================", "INFO")

        while True:
            client_sock, client_addr = server_sock.accept()
            t = threading.Thread(target=handle_client, args=(client_sock, client_addr), daemon=True)
            t.start()

    except KeyboardInterrupt:
        log("Đang dừng máy chủ VPS...", "INFO")
    except Exception as e:
        log(f"Không thể khởi động VPS Server: {e}", "WARN")
    finally:
        server_sock.close()

if __name__ == "__main__":
    main()
