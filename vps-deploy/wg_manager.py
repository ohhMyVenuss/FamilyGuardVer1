#!/usr/bin/env python3
# =============================================================================
# FAMILYGUARD WIREGUARD PEER MANAGER
# Tự động cấp IP, sinh cặp khóa, nạp vào WireGuard và tạo mã QR Base64
# =============================================================================

import sys
import os
import re
import json
import base64
import subprocess
import urllib.request

WG_CONF_PATH = "/etc/wireguard/wg0.conf"
SERVER_PUB_KEY_PATH = "/etc/wireguard/server_public.key"
ENDPOINT_CACHE_PATH = "/etc/wireguard/server_endpoint.txt"
DEFAULT_PUBLIC_IP = "103.74.101.176"
WG_PORT = 51820
DNS_SERVER_IP = "10.0.0.1"

def get_server_public_ip():
    """Lấy IP Public của VPS (có cache)"""
    if os.path.exists(ENDPOINT_CACHE_PATH):
        try:
            with open(ENDPOINT_CACHE_PATH, "r", encoding="utf-8") as f:
                ip = f.read().strip()
                if ip:
                    return ip
        except Exception:
            pass

    # Thử lấy qua ifconfig.me
    try:
        req = urllib.request.Request("http://ifconfig.me", headers={"User-Agent": "curl/7.68.0"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            ip = resp.read().decode("utf-8").strip()
            if ip and re.match(r"^\d+\.\d+\.\d+\.\d+$", ip):
                try:
                    with open(ENDPOINT_CACHE_PATH, "w", encoding="utf-8") as f:
                        f.write(ip)
                except Exception:
                    pass
                return ip
    except Exception:
        pass

    return DEFAULT_PUBLIC_IP

def get_server_public_key():
    """Lấy Public Key của Server WireGuard"""
    if os.path.exists(SERVER_PUB_KEY_PATH):
        try:
            with open(SERVER_PUB_KEY_PATH, "r", encoding="utf-8") as f:
                key = f.read().strip()
                if key:
                    return key
        except Exception:
            pass

    # Thử lấy từ lệnh wg show wg0 public-key
    try:
        out = subprocess.check_output(["wg", "show", "wg0", "public-key"], stderr=subprocess.DEVNULL)
        key = out.decode("utf-8").strip()
        if key:
            return key
    except Exception:
        pass

    raise RuntimeError("Không tìm thấy Server Public Key. Hãy kiểm tra WireGuard server.")

def find_used_ips():
    """Tìm tất cả IP 10.0.0.X đã được cấp trong wg0.conf và wg show"""
    used = {1} # 10.0.0.1 là của Server

    if os.path.exists(WG_CONF_PATH):
        try:
            with open(WG_CONF_PATH, "r", encoding="utf-8") as f:
                content = f.read()
                matches = re.findall(r"AllowedIPs\s*=\s*10\.0\.0\.(\d+)", content)
                for m in matches:
                    used.add(int(m))
        except Exception:
            pass

    try:
        out = subprocess.check_output(["wg", "show", "wg0", "allowed-ips"], stderr=subprocess.DEVNULL)
        for line in out.decode("utf-8").splitlines():
            matches = re.findall(r"10\.0\.0\.(\d+)", line)
            for m in matches:
                used.add(int(m))
    except Exception:
        pass

    return used

def get_next_available_ip():
    """Tìm IP trống tiếp theo trong dải 10.0.0.2 - 10.0.0.254"""
    used = find_used_ips()
    for host in range(2, 255):
        if host not in used:
            return f"10.0.0.{host}"
    raise RuntimeError("Dải mạng WireGuard (10.0.0.0/24) đã hết địa chỉ IP khả dụng!")

def generate_qr_base64(text):
    """Sinh mã QR dạng Base64 PNG bằng qrencode"""
    try:
        p = subprocess.Popen(
            ["qrencode", "-s", "6", "-t", "PNG", "-o", "-"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        png_data, err = p.communicate(input=text.encode("utf-8"))
        if p.returncode == 0 and png_data:
            return base64.b64encode(png_data).decode("utf-8")
    except Exception as e:
        sys.stderr.write(f"[WARN] Lỗi sinh QR qua qrencode: {e}\n")
    return ""

def create_peer(device_name):
    """Quy trình tự động hóa tạo WireGuard Peer"""
    if not device_name or not device_name.strip():
        device_name = "ThietBiCon"
    device_name = device_name.strip()

    assigned_ip = get_next_available_ip()
    server_pub_key = get_server_public_key()
    server_ip = get_server_public_ip()

    # 1. Sinh Private Key và Public Key cho client
    priv_out = subprocess.check_output(["wg", "genkey"])
    client_priv_key = priv_out.decode("utf-8").strip()

    pub_out = subprocess.check_output(["wg", "pubkey"], input=client_priv_key.encode("utf-8"))
    client_pub_key = pub_out.decode("utf-8").strip()

    # 2. Cập nhật WireGuard runtime (áp dụng ngay không cần restart wg-quick)
    try:
        subprocess.run(
            ["wg", "set", "wg0", "peer", client_pub_key, "allowed-ips", f"{assigned_ip}/32"],
            check=True,
            stderr=subprocess.PIPE
        )
    except Exception as e:
        sys.stderr.write(f"[WARN] Không thể chạy 'wg set': {e}\n")

    # 3. Ghi vào file /etc/wireguard/wg0.conf để duy trì khi reboot
    peer_block = f"\n# Peer: {device_name}\n[Peer]\nPublicKey = {client_pub_key}\nAllowedIPs = {assigned_ip}/32\n"
    if os.path.exists(WG_CONF_PATH):
        try:
            with open(WG_CONF_PATH, "a", encoding="utf-8") as f:
                f.write(peer_block)
        except Exception as e:
            sys.stderr.write(f"[WARN] Không thể ghi wg0.conf: {e}\n")

    # 4. Tạo nội dung file cấu hình WireGuard cho máy con (.conf)
    config_text = f"""[Interface]
PrivateKey = {client_priv_key}
Address = {assigned_ip}/24
DNS = {DNS_SERVER_IP}

[Peer]
PublicKey = {server_pub_key}
Endpoint = {server_ip}:{WG_PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
"""

    # 5. Sinh mã QR Base64 PNG
    qr_base64 = generate_qr_base64(config_text)

    # 6. Trả về kết quả JSON
    result = {
        "success": True,
        "device_name": device_name,
        "assigned_ip": assigned_ip,
        "client_pub_key": client_pub_key,
        "config_text": config_text,
        "qr_base64": qr_base64,
        "message": f"Cấp IP {assigned_ip} thành công cho {device_name}"
    }
    return result

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"success": False, "error": "Cần chỉ định lệnh (VD: create <device_name>)"}))
        sys.exit(1)

    action = sys.argv[1].lower()

    if action == "create":
        dev_name = sys.argv[2] if len(sys.argv) > 2 else "ThietBiCon"
        try:
            res = create_peer(dev_name)
            print(json.dumps(res))
        except Exception as e:
            print(json.dumps({"success": False, "error": str(e)}))
            sys.exit(1)
    else:
        print(json.dumps({"success": False, "error": f"Lệnh không hỗ trợ: {action}"}))
        sys.exit(1)

if __name__ == "__main__":
    main()
