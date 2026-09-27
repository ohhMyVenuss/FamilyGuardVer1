#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
FAMILYGUARD - MINI HTTP BLOCK SERVER (PORT 80)
=============================================================================
Đóng vai trò Captive Portal / Trang chặn cảnh báo khi trẻ truy cập web bị cấm.
Theo đặc tả kiến trúc tại struct.md:
- Lắng nghe cổng TCP 80 trên VPS
- Trả về giao diện HTML Cảnh báo hiện đại
- Hỗ trợ nút tương tác "Xin thêm giờ" gửi yêu cầu về hệ thống
=============================================================================
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import datetime
import urllib.parse

PORT = 80

HTML_PAGE = """<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FamilyGuard - Trang Web Bị Chặn</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', -apple-system, Roboto, sans-serif; }
        body { background: #0F172A; color: #F8FAFC; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }
        .card { background: #1E293B; max-width: 520px; width: 100%; border-radius: 16px; padding: 36px; text-align: center; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); border: 1px solid #334155; }
        .icon { font-size: 64px; margin-bottom: 16px; }
        h1 { font-size: 24px; font-weight: 700; color: #EF4444; margin-bottom: 12px; }
        p { color: #94A3B8; font-size: 14px; line-height: 1.6; margin-bottom: 24px; }
        .badge { display: inline-block; background: #374151; color: #F3F4F6; padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; margin-bottom: 24px; word-break: break-all; }
        .btn-group { display: flex; flex-direction: column; gap: 12px; }
        .btn { padding: 12px 20px; border-radius: 8px; font-weight: 600; font-size: 14px; cursor: pointer; text-decoration: none; border: none; transition: all 0.2s; }
        .btn-primary { background: #3B82F6; color: white; }
        .btn-primary:hover { background: #2563EB; }
        .btn-outline { background: transparent; border: 1px solid #475569; color: #CBD5E1; }
        .btn-outline:hover { background: #334155; }
        .footer { margin-top: 24px; font-size: 12px; color: #64748B; border-top: 1px solid #334155; padding-top: 16px; }
        #success-msg { display: none; background: #065F46; color: #A7F3D0; padding: 12px; border-radius: 8px; margin-bottom: 16px; font-size: 13px; font-weight: 600; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">🛡️</div>
        <h1>TRUY CẬP ĐÃ BỊ CHẶN</h1>
        <p>Trang web này nằm trong danh mục hạn chế theo quy tắc bảo vệ an toàn Internet của Cha Mẹ.</p>
        <div class="badge" id="host-name">Trang web không được phép truy cập</div>
        <div id="success-msg">✅ Đã gửi yêu cầu xin thêm 30 phút tới máy Phụ Huynh!</div>
        <div class="btn-group">
            <button class="btn btn-primary" onclick="requestMoreTime()">⏱️ Xin Thêm 30 Phút Truy Cập</button>
            <button class="btn btn-outline" onclick="history.back()">⬅️ Quay Lại Trang Trước</button>
        </div>
        <div class="footer">
            Hệ thống FamilyGuard - Quản trị và giám sát qua WireGuard VPN
        </div>
    </div>
    <script>
        document.getElementById('host-name').innerText = window.location.hostname || "Trang web bị chặn";
        function requestMoreTime() {
            fetch('/api/request-time', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ host: window.location.hostname, time: new Date().toISOString() })
            }).then(() => {
                document.getElementById('success-msg').style.display = 'block';
            }).catch(() => {
                document.getElementById('success-msg').style.display = 'block';
            });
        }
    </script>
</body>
</html>
"""

class BlockHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(HTML_PAGE.encode("utf-8"))

    def do_POST(self):
        if self.path == "/api/request-time":
            content_length = int(self.headers.get("Content-Length", 0))
            post_data = self.rfile.read(content_length)
            now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"[{now}] 🚨 Trẻ em vừa bấm nút: XIN THÊM THỜI GIAN TRUY CẬP từ IP: {self.client_address[0]}")
            
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "SUCCESS", "message": "Request sent"}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Giảm bớt log thừa
        pass

def run():
    server_address = ("0.0.0.0", PORT)
    httpd = HTTPServer(server_address, BlockHandler)
    print(f"[*] FamilyGuard HTTP Block Server đang chạy trên cổng {PORT}...")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()

if __name__ == "__main__":
    run()
