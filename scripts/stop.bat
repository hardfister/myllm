@echo off
chcp 65001 >nul
title MyLLM — 关闭所有服务

echo ============================================
echo   关闭 MyLLM 全部服务
echo ============================================

echo [1/4] 关闭后端...
taskkill /FI "WINDOWTITLE eq MyLLM-Backend*" /T /F >nul 2>&1
echo   ✅ 后端已关闭

echo [2/4] 关闭前端...
taskkill /FI "WINDOWTITLE eq MyLLM-Frontend*" /T /F >nul 2>&1
echo   ✅ 前端已关闭

echo [3/4] 保留 Chroma Docker (数据不清除)...
echo   ⏭  跳过 (如需停止请手动: docker stop myllm-chroma)

echo [4/4] 保留 Redis Docker (缓存不清除)...
echo   ⏭  跳过 (如需停止请手动: docker stop myllm-redis)

echo.
echo ============================================
echo   已关闭后端和前端
echo   Docker 容器保留运行中
echo ============================================
pause
