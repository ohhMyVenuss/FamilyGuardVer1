@echo off
chcp 65001 > nul
echo ======================================================================
echo   DANG CAP NHAT MA NGUON dns_forwarder.cpp LEN VPS (103.74.101.176)
echo ======================================================================
echo.
echo * Mat khau VPS neu duoc hoi: AZvpsd6eb!5l@66
echo.

scp "vps-deploy\dns_forwarder.cpp" root@103.74.101.176:/opt/familyguard/dns_forwarder.cpp
if %errorlevel% neq 0 (
    echo [!] Upload that bai. Vui long kiem tra lai ket noi hoac mat khau.
    pause
    exit /b %errorlevel%
)

echo.
echo [OK] Da tai file len /opt/familyguard/dns_forwarder.cpp thanh cong!
echo [*] Dang bien dich va khoi dong lai service tren VPS...
echo.

ssh root@103.74.101.176 "apt-get install -y conntrack && g++ -std=c++17 -O2 -Wall -Wextra -pthread /opt/familyguard/dns_forwarder.cpp -o /opt/familyguard/dns_forwarder && systemctl restart familyguard.service && echo '==> DA BIEN DICH VA KHOI DONG LAI THANH CONG!'"

echo.
echo ======================================================================
echo   HOAN TAT! He thong da san sang chan ca App va Web.
echo ======================================================================
pause
