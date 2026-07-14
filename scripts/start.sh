#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "============================================"
echo "  MyLLM 一键启动 (Linux/Mac)"
echo "  Spring Boot + Vue 3 + Chroma + Redis"
echo "============================================"

# ---- 1. Redis ----
echo "[1/5] 检查 Redis..."
if redis-cli ping &>/dev/null; then
    echo "  ✅ Redis 已在运行"
else
    echo "  ⏳ 尝试启动 Redis..."
    redis-server --daemonize yes 2>/dev/null && echo "  ✅ Redis 已启动" || {
        echo "  本地启动失败，尝试 Docker..."
        docker start myllm-redis 2>/dev/null || \
            docker run -d --name myllm-redis -p 6379:6379 redis:7-alpine && \
            echo "  ✅ Redis 已启动 (Docker)"
    }
fi

# ---- 2. Chroma ----
echo "[2/5] 检查 Chroma..."
if curl -s http://127.0.0.1:8000/api/v2/heartbeat &>/dev/null; then
    echo "  ✅ Chroma 已在运行"
else
    echo "  ⏳ 尝试启动 Chroma (Docker)..."
    docker start myllm-chroma 2>/dev/null || \
        docker run -d --name myllm-chroma -p 8000:8000 chromadb/chroma && \
        echo "  ✅ Chroma 已启动 (Docker)"
fi

# ---- 3. Ollama ----
echo "[3/5] 检查 Ollama..."
if curl -s http://localhost:11434/api/tags &>/dev/null; then
    echo "  ✅ Ollama 已在运行"
else
    echo "  ⚠️  请手动启动 Ollama: ollama serve"
fi

# ---- 4. Spring Boot ----
echo "[4/5] 启动后端 Spring Boot..."
cd "$ROOT/myllm"
./mvnw spring-boot:run &
BACKEND_PID=$!

# ---- 5. Vue 3 ----
echo "[5/5] 启动前端 Vue 3..."
cd "$ROOT/vue/myllm-ui"
npm run dev &
FRONTEND_PID=$!

cd "$ROOT"

echo ""
echo "============================================"
echo "  等待服务启动 (10s)..."
echo "============================================"
sleep 10

echo "  Redis:   $(redis-cli ping 2>/dev/null && echo '✅' || echo '❌')"
echo "  Chroma:  $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8000/api/v2/heartbeat 2>/dev/null | grep -q 200 && echo '✅' || echo '❌')"
echo "  Backend: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080 2>/dev/null | grep -qE '200|404' && echo '✅' || echo '❌')"
echo ""
echo "============================================"
echo "  启动完成！打开浏览器:"
echo "  http://localhost:5173"
echo ""
echo "  后端 PID: $BACKEND_PID"
echo "  前端 PID: $FRONTEND_PID"
echo "  退出时请手动 Ctrl+C 关闭"
echo "============================================"

wait
