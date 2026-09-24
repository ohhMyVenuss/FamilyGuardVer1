@echo off
title Khoi chay FamilyGuard - He thong quan ly Internet tre em
chcp 65001 >nul
echo =====================================================================
echo    FamilyGuard - Ứng dụng Quản lý & Giám sát Truy cập Internet
echo    Đồ án: Lập Trình Mạng
echo =====================================================================
echo.

if not defined JAVA_HOME (
    set "JAVA_HOME=C:\Program Files\Java\jdk-24"
)

echo [INFO] Đang khởi chạy ứng dụng FamilyGuard...
call mvnw.cmd javafx:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Có lỗi khi chạy ứng dụng. Vui lòng kiểm tra lại cấu hình Java JDK.
    pause
)
