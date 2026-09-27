#!/bin/bash
# =============================================================================
# SCRIPT TRIỂN KHAI TỰ ĐỘNG FAMILYGUARD CORE LÊN UBUNTU VPS
# Đồ án môn học: Lập Trình Mạng
# Mục tiêu: VPS Ubuntu 20.04 / 22.04 / 24.04
# Cách dùng trên VPS:
#   chmod +x deploy.sh
#   sudo ./deploy.sh
# =============================================================================

set -e

# Xác định thư mục chứa script để copy tệp an toàn tuyệt đối
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}======================================================================${NC}"
echo -e "${GREEN}   🛡️  BẮT ĐẦU TRIỂN KHAI FAMILYGUARD CORE LÊN VPS CLOUD UBUNTU     ${NC}"
echo -e "${BLUE}======================================================================${NC}"

# Kiểm tra quyền root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}[!] Vui lòng chạy script này dưới quyền root: sudo ./deploy.sh${NC}"
    exit 1
fi

# 1. Cập nhật hệ thống và cài đặt gói phụ thuộc
echo -e "\n${YELLOW}[1/7] Đang cập nhật gói và cài đặt thư viện cần thiết...${NC}"
apt-get update -y
apt-get install -y g++ nlohmann-json3-dev wireguard wireguard-tools iptables qrencode python3 curl net-tools

# 2. Bật tính năng chuyển tiếp IP (IP Forwarding) trong nhân Linux
echo -e "\n${YELLOW}[2/7] Đang cấu hình chuyển tiếp mạng (IPv4 Forwarding)...${NC}"
sed -i '/net.ipv4.ip_forward=1/s/^#//g' /etc/sysctl.conf
if ! grep -q "net.ipv4.ip_forward=1" /etc/sysctl.conf; then
    echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
fi
sysctl -p

# 3. Khởi tạo thư mục làm việc trên VPS
echo -e "\n${YELLOW}[3/7] Đang tạo thư mục /opt/familyguard...${NC}"
mkdir -p /opt/familyguard
mkdir -p /etc/wireguard

# Copy mã nguồn từ thư mục script
cp "$SCRIPT_DIR/dns_forwarder.cpp" /opt/familyguard/
cp "$SCRIPT_DIR/http_block_server.py" /opt/familyguard/

# 4. Biên dịch mã nguồn C++ DNS Forwarder
echo -e "\n${YELLOW}[4/7] Đang biên dịch dns_forwarder bằng g++ (C++17, O2, pthread)...${NC}"
g++ -std=c++17 -O2 -Wall -Wextra -pthread /opt/familyguard/dns_forwarder.cpp -o /opt/familyguard/dns_forwarder
chmod +x /opt/familyguard/dns_forwarder

# 5. Cấu hình WireGuard VPN Server và tạo Key cho Con & Bố Mẹ
echo -e "\n${YELLOW}[5/7] Đang khởi tạo khóa mật mã và cấu hình WireGuard VPN...${NC}"
cd /etc/wireguard
umask 077

# Tạo Server Keys nếu chưa có
if [ ! -f server_private.key ]; then
    wg genkey | tee server_private.key | wg pubkey > server_public.key
fi
SERVER_PRIV=$(cat server_private.key)
SERVER_PUB=$(cat server_public.key)

# Tạo Child Keys nếu chưa có
if [ ! -f child_private.key ]; then
    wg genkey | tee child_private.key | wg pubkey > child_public.key
fi
CHILD_PRIV=$(cat child_private.key)
CHILD_PUB=$(cat child_public.key)

# Tạo Parent Keys nếu chưa có
if [ ! -f parent_private.key ]; then
    wg genkey | tee parent_private.key | wg pubkey > parent_public.key
fi
PARENT_PRIV=$(cat parent_private.key)
PARENT_PUB=$(cat parent_public.key)

# Lấy card mạng chính của VPS (VD: eth0 hoặc ens3)
DEFAULT_INTERFACE=$(ip route show default | awk '{print $5}' | head -n1)
if [ -z "$DEFAULT_INTERFACE" ]; then
    DEFAULT_INTERFACE="eth0"
fi

# Tạo tệp /etc/wireguard/wg0.conf
cat <<EOF > /etc/wireguard/wg0.conf
[Interface]
Address = 10.0.0.1/24
ListenPort = 51820
PrivateKey = $SERVER_PRIV
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o $DEFAULT_INTERFACE -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o $DEFAULT_INTERFACE -j MASQUERADE

# Máy con (Child Device - 10.0.0.2)
[Peer]
PublicKey = $CHILD_PUB
AllowedIPs = 10.0.0.2/32

# Máy phụ huynh (Parent Device - 10.0.0.3)
[Peer]
PublicKey = $PARENT_PUB
AllowedIPs = 10.0.0.3/32
EOF

# Lấy địa chỉ IP Public của VPS
PUBLIC_IP=$(curl -s -4 ifconfig.me || echo "103.74.101.176")

# Tạo tệp cấu hình WireGuard cho Con: /opt/familyguard/child_client.conf
cat <<EOF > /opt/familyguard/child_client.conf
[Interface]
PrivateKey = $CHILD_PRIV
Address = 10.0.0.2/24
DNS = 10.0.0.1

[Peer]
PublicKey = $SERVER_PUB
Endpoint = $PUBLIC_IP:51820
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
EOF

# Tạo tệp cấu hình WireGuard cho Bố Mẹ: /opt/familyguard/parent_client.conf
cat <<EOF > /opt/familyguard/parent_client.conf
[Interface]
PrivateKey = $PARENT_PRIV
Address = 10.0.0.3/24

[Peer]
PublicKey = $SERVER_PUB
Endpoint = $PUBLIC_IP:51820
AllowedIPs = 10.0.0.0/24
PersistentKeepalive = 25
EOF

# Khởi động WireGuard service
systemctl stop wg-quick@wg0 2>/dev/null || true
systemctl enable wg-quick@wg0
systemctl start wg-quick@wg0

# Quay trở lại thư mục làm việc ban đầu
cd "$SCRIPT_DIR"

# 6. Thiết lập Systemd Service tự động chạy 24/7
echo -e "\n${YELLOW}[6/7] Đang dọn dẹp tiến trình cũ và cài đặt Systemd Services...${NC}"
systemctl stop familyguard.service 2>/dev/null || true
systemctl stop familyguard-http.service 2>/dev/null || true
pkill -9 -f dns_forwarder 2>/dev/null || true
fuser -k 9000/tcp 2>/dev/null || true
fuser -k 53/udp 2>/dev/null || true
sleep 1

cp "$SCRIPT_DIR/familyguard.service" /etc/systemd/system/
cp "$SCRIPT_DIR/familyguard-http.service" /etc/systemd/system/

systemctl daemon-reload
systemctl enable familyguard.service
systemctl restart familyguard.service

systemctl enable familyguard-http.service
systemctl restart familyguard-http.service

# 7. Mở Firewall (UFW / iptables)
echo -e "\n${YELLOW}[7/7] Đang mở các cổng mạng cần thiết trên tường lửa...${NC}"
if command -v ufw >/dev/null 2>&1; then
    ufw allow 22/tcp || true
    ufw allow 51820/udp || true
    ufw allow 53/udp || true
    ufw allow 9000/tcp || true
    ufw allow 80/tcp || true
fi

echo -e "\n${BLUE}======================================================================${NC}"
echo -e "${GREEN}   ✅ TRIỂN KHAI THÀNH CÔNG FAMILYGUARD TRÊN VPS!                     ${NC}"
echo -e "${BLUE}======================================================================${NC}"
echo -e "🌐 IP VPS Public:       ${GREEN}$PUBLIC_IP${NC}"
echo -e "⚡ Cổng TCP Control:    ${GREEN}9000${NC} (Nhận lệnh từ app JavaFX)"
echo -e "🛡️ Cổng DNS Firewall:   ${GREEN}53/UDP${NC} (Lọc web thông minh)"
echo -e "🔒 Cổng WireGuard VPN:  ${GREEN}51820/UDP${NC}"
echo -e "🚫 Cổng HTTP Block:     ${GREEN}80/TCP${NC} (Trang cảnh báo)"
echo -e "🔑 Mật khẩu xác thực:   ${GREEN}AZvpsd6eb!5l@66${NC}"
echo -e "----------------------------------------------------------------------"
echo -e "📱 Tệp cấu hình máy con:    /opt/familyguard/child_client.conf"
echo -e "💻 Tệp cấu hình máy bố mẹ:  /opt/familyguard/parent_client.conf"
echo -e "----------------------------------------------------------------------"
echo -e "${YELLOW}Dưới đây là MÃ QR CẤU HÌNH WIREGUARD CHO MÁY CON (Quét bằng app WireGuard):${NC}"
echo -e "----------------------------------------------------------------------"
qrencode -t ansiutf8 < /opt/familyguard/child_client.conf
echo -e "----------------------------------------------------------------------"
echo -e "Để xem log hệ thống thời gian thực, gõ:"
echo -e "  journalctl -u familyguard.service -f"
echo -e "======================================================================"
