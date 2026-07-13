@echo off
chcp 65001 >nul
title MyLLM — 一键启动

echo ============================================
echo   MyLLM 全栈启动脚本
echo   Spring Boot + Vue 3 + Chroma + Redis
echo ============================================
echo.

:: ========== 1. 检查并启动 Redis ==========
echo [1/5] 检查 Redis...
redis-cli ping >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✅ Redis 已在运行
) else (
    echo   ⏳ 尝试启动 Redis (Docker)...
    docker start myllm-redis >nul 2>&1
    if %errorlevel% neq 0 (
        echo   ⏳ 创建 Redis 容器...
        docker run -d --name myllm-redis -p 6379:6379 redis:7-alpine >nul 2>&1
        if %errorlevel% neq 0 (
            echo   ⚠️  Redis Docker 启动失败，请确认 Redis 已运行
        ) else (
            echo   ✅ Redis 已启动 (Docker)
        )
    ) else (
        echo   ✅ Redis 已启动 (Docker)
    )
)

:: ========== 2. 检查并启动 Chroma ==========
echo [2/5] 检查 Chroma...
curl -s http://127.0.0.1:8000/api/v2/heartbeat >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✅ Chroma 已在运行
) else (
    echo   ⏳ 尝试启动 Chroma (Docker)...
    docker start myllm-chroma >nul 2>&1
    if %errorlevel% neq 0 (
        echo   ⏳ 创建 Chroma 容器...
        docker run -d --name myllm-chroma -p 8000:8000 chromadb/chroma >nul 2>&1
        if %errorlevel% neq 0 (
            echo   ⚠️  Chroma Docker 启动失败
        ) else (
            echo   ✅ Chroma 已启动 (Docker)
        )
    ) else (
        echo   ✅ Chroma 已启动 (Docker)
    )
)

:: ========== 3. 检查并启动 Ollama ==========
echo [3/5] 检查 Ollama...
curl -s http://localhost:11434/api/tags >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✅ Ollama 已在运行
) else (
    echo   ⚠️  请手动启动 Ollama: ollama serve
)

:: ========== 4. 启动后端 Spring Boot ==========
echo [4/5] 启动后端 Spring Boot...
start "MyLLM-Backend" cmd /k "cd /d E:\document\1myweb\myllm && mvnw spring-boot:run"

:: ========== 5. 启动前端 Vue 3 ==========
echo [5/5] 启动前端 Vue 3...
start "MyLLM-Frontend" cmd /k "cd /d E:\document\1myweb\vue\myllm-ui && npm run dev"

:: ========== 等待启动完成 ==========
echo.
echo ============================================
echo   等待服务启动 (约 10 秒)...
echo ============================================
timeout /t 10 /nobreak >nul

:: ========== 检查状态 ==========
echo.
echo ============================================
echo   服务状态检查
echo ============================================

echo Redis   :  尝试连接...
redis-cli ping >nul 2>&1 && echo   ✅ 6379    || echo   ❌ 未启动
echo Chroma  :  尝试连接...
curl -s -o nul -w "%%{http_code}" http://127.0.0.1:8000/api/v2/heartbeat 2>nul | findstr "200" >nul && echo   ✅ 8000    || echo   ❌ 未启动
echo Backend :  尝试连接...
curl -s -o nul -w "%%{http_code}" http://localhost:8080 2>nul | findstr "200 404" >nul && echo   ✅ 8080    || echo   ❌ 未启动
echo Frontend:  (Vite 启动中，端口 5173)...

echo.
echo ============================================
echo   启动完成！打开浏览器访问:
echo   http://localhost:5173
echo ============================================
echo.
pause
