@echo off
chcp 65001 >nul
title MyLLM — 关闭全部（含 Docker）

echo ============================================
echo   全部关闭 — 后端 + 前端 + Docker 容器
echo ============================================

echo [1/5] 关闭后端...
taskkill /FI "WINDOWTITLE eq MyLLM-Backend*" /T /F >nul 2>&1
netsh interface ipv4 show excludedportrange protocol=tcp | findstr 8080 >nul || (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTENING"') do taskkill /PID %%a /F >nul 2>&1
)
echo   ✅ 后端已关闭

echo [2/5] 关闭前端...
taskkill /FI "WINDOWTITLE eq MyLLM-Frontend*" /T /F >nul 2>&1
echo   ✅ 前端已关闭

echo [3/5] 关闭 Chroma Docker...
docker stop myllm-chroma >nul 2>&1 && echo   ✅ Chroma 已关闭 || echo   ⏭  未在运行

echo [4/5] 关闭 Redis Docker...
docker stop myllm-redis >nul 2>&1 && echo   ✅ Redis 已关闭 || echo   ⏭  未在运行

echo [5/5] 清理残留进程...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173.*LISTENING"') do taskkill /PID %%a /F >nul 2>&1
echo   ✅ 端口 5173 已释放

echo.
echo ============================================
echo   全部服务已关闭
echo ============================================
pause
