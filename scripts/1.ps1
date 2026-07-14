# MyLLM — 一键启动 (PowerShell)
# 启动顺序: MySQL → Redis → Chroma → Ollama (可选) → Spring Boot → Vue 3
$ErrorActionPreference = "Continue"
$ROOT = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$HOST_UI = "http://localhost:5173"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  MyLLM 全栈启动 (PowerShell)" -ForegroundColor Cyan
Write-Host "  MySQL + Redis + Chroma + SpringBoot + Vue" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ─── 1. MySQL ───
Write-Host "[1/6] 检查 MySQL..." -ForegroundColor Yellow

$mysqlOk = $false
try {
    $conn = New-Object System.Data.SqlClient.SqlConnection
    # 尝试 TCP 3306 连接
    $tcp = Test-NetConnection -ComputerName localhost -Port 3306 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) { $mysqlOk = $true }
} catch {}

if ($mysqlOk) {
    Write-Host "  ✅ MySQL 已在运行 (3306)" -ForegroundColor Green
} else {
    Write-Host "  ⏳ 尝试启动 MySQL (Windows 服务)..." -ForegroundColor Yellow
    try {
        Start-Service -Name "MySQL" -ErrorAction Stop 2>$null
        Start-Service -Name "MySQL80" -ErrorAction Stop 2>$null
        Start-Service -Name "MySQL8.0" -ErrorAction Stop 2>$null
        Start-Sleep 3
        Write-Host "  ✅ MySQL 服务已启动" -ForegroundColor Green
    } catch {
        Write-Host "  ⚠️  MySQL 未找到 Windows 服务，尝试 Docker..." -ForegroundColor Yellow
        docker start myllm-mysql 2>$null
        if ($LASTEXITCODE -ne 0) {
            docker run -d --name myllm-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.0 2>$null
        }
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✅ MySQL 已启动 (Docker)" -ForegroundColor Green
            Start-Sleep 5
        } else {
            Write-Host "  ❌ MySQL 启动失败，请手动启动后重试" -ForegroundColor Red
            Read-Host "按 Enter 继续(跳过 MySQL)..."
        }
    }
}

# ─── 2. Redis (WSL Alpine 优先，其次 Docker) ───
Write-Host "[2/6] 检查 Redis..." -ForegroundColor Yellow
$redisOk = $false
try { $ping = redis-cli ping 2>$null; if ($ping -eq "PONG") { $redisOk = $true } } catch {}
if ($redisOk) {
    Write-Host "  ✅ Redis 已在运行 (6379)" -ForegroundColor Green
} else {
    Write-Host "  ⏳ 尝试启动 Redis (WSL Alpine)..." -ForegroundColor Yellow
    try {
        wsl -d Alpine -- redis-server --daemonize yes 2>$null
        Start-Sleep 2
        $ping2 = redis-cli ping 2>$null
        if ($ping2 -eq "PONG") {
            Write-Host "  ✅ Redis 已通过 WSL Alpine 启动" -ForegroundColor Green
            $redisOk = $true
        }
    } catch {}
    if (-not $redisOk) {
        Write-Host "  ⏳ WSL 失败，尝试 Docker..." -ForegroundColor Yellow
        docker start myllm-redis 2>$null
        if ($LASTEXITCODE -ne 0) {
            docker run -d --name myllm-redis -p 6379:6379 redis:7-alpine 2>$null
        }
        if ($LASTEXITCODE -eq 0) { Write-Host "  ✅ Redis 已启动 (Docker)" -ForegroundColor Green }
        else { Write-Host "  ❌ Redis 启动失败" -ForegroundColor Red }
    }
}

# ─── 3. Chroma ───
Write-Host "[3/6] 检查 Chroma..." -ForegroundColor Yellow
try {
    $chromaResp = Invoke-WebRequest -Uri "http://127.0.0.1:8000/api/v2/heartbeat" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
    if ($chromaResp.StatusCode -eq 200) { $chromaOk = $true }
} catch { $chromaOk = $false }

if ($chromaOk) {
    Write-Host "  ✅ Chroma 已在运行 (8000)" -ForegroundColor Green
} else {
    Write-Host "  ⏳ 尝试启动 Chroma (Docker)..." -ForegroundColor Yellow
    docker start myllm-chroma 2>$null
    if ($LASTEXITCODE -ne 0) {
        docker run -d --name myllm-chroma -p 8000:8000 chromadb/chroma 2>$null
    }
    if ($LASTEXITCODE -eq 0) { Write-Host "  ✅ Chroma 已启动 (Docker)" -ForegroundColor Green }
    else { Write-Host "  ⚠️  Chroma Docker 启动失败" -ForegroundColor Yellow }
}

# ─── 4. Ollama ───
Write-Host "[4/6] 检查 Ollama..." -ForegroundColor Yellow
try {
    $ollamaResp = Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
    if ($ollamaResp.StatusCode -eq 200) {
        Write-Host "  ✅ Ollama 已在运行 (11434)" -ForegroundColor Green
    }
} catch {
    Write-Host "  ⚠️  请手动启动 Ollama: ollama serve" -ForegroundColor Yellow
}

# ─── 5. Spring Boot ───
Write-Host "[5/6] 启动后端 Spring Boot..." -ForegroundColor Yellow
$backendJob = Start-Job -Name "MyLLM-Backend" -ScriptBlock {
    param($root)
    Set-Location "$root\myllm"
    mvnw spring-boot:run 2>&1 | Out-File "$root\tmp-backend.log"
} -ArgumentList $ROOT
Write-Host "  🟢 后端启动中 (PID: $($backendJob.Id))... 日志: tmp-backend.log" -ForegroundColor Green

# ─── 6. Vue 3 ───
Write-Host "[6/6] 启动前端 Vue 3..." -ForegroundColor Yellow
$frontendJob = Start-Job -Name "MyLLM-Frontend" -ScriptBlock {
    param($root)
    Set-Location "$root\vue\myllm-ui"
    npm run dev 2>&1 | Out-File "$root\tmp-frontend.log"
} -ArgumentList $ROOT
Write-Host "  🟢 前端启动中 (PID: $($frontendJob.Id))... 日志: tmp-frontend.log" -ForegroundColor Green

# ─── 等待启动 ───
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  等待服务启动 (约 15 秒)..." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

for ($i = 15; $i -gt 0; $i--) {
    Write-Host -NoNewline "`r  剩余 $i 秒... "
    Start-Sleep 1
}
Write-Host ""

# ─── 状态检查 ───
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  服务状态检查" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

function Test-Port {
    param($name, $host, $port, $url)
    if ($url) {
        try { $r = Invoke-WebRequest -Uri $url -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop; return $r.StatusCode -eq 200 }
        catch { return $false }
    }
    $t = Test-NetConnection -ComputerName $host -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    return $t.TcpTestSucceeded
}

$checks = @(
    @{N="MySQL 3306"; H="localhost"; P=3306},
    @{N="Redis 6379"; H="localhost"; P=6379},
    @{N="Chroma 8000"; H="127.0.0.1"; P=8000; U="http://127.0.0.1:8000/api/v2/heartbeat"},
    @{N="Ollama 11434"; H="localhost"; P=11434; U="http://localhost:11434/api/tags"},
    @{N="Backend 8080"; H="localhost"; P=8080},
    @{N="Frontend 5173"; H="localhost"; P=5173}
)

$allOk = $true
foreach ($c in $checks) {
    $ok = Test-Port -name $c.N -host $c.H -port $c.P -url $c.U
    if ($ok) { Write-Host "  ✅ $($c.N)" -ForegroundColor Green }
    else     { Write-Host "  ❌ $($c.N) — 未就绪" -ForegroundColor Red; $allOk = $false }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
if ($allOk) {
    Write-Host "  全部就绪！打开浏览器: $HOST_UI" -ForegroundColor Green
} else {
    Write-Host "  部分服务未就绪，请检查日志:" -ForegroundColor Yellow
    Write-Host "    后端:  $ROOT\tmp-backend.log" -ForegroundColor Yellow
    Write-Host "    前端:  $ROOT\tmp-frontend.log" -ForegroundColor Yellow
}
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "按 Ctrl+C 关闭后端/前端后台进程，或运行 scripts/stop-all.ps1" -ForegroundColor Gray
Read-Host "按 Enter 退出 (后台服务继续运行)"
