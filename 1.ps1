# MyLLM one-click start
# Order: Redis (WSL Alpine) -> Chroma -> Spring Boot -> Vue 3
$root = $PSScriptRoot

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  MyLLM Start Script" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# ==========================================================
# Step 1: Redis via Docker Desktop WSL (线性安全版)
# ==========================================================
Write-Host "[1/4] Redis (WSL)..." -ForegroundColor Yellow

# 不管三七二十一，直接让 WSL 在后台拉起 Redis。
# 如果已经启动了，它会自动忽略；如果没启动，它会直接启动。
wsl -d docker-desktop -u root -- redis-server --protected-mode no --bind 0.0.0.0 --daemonize yes 2>$null

# 稳妥起见，强制等待 2 秒
Start-Sleep 2

Write-Host "  [OK] Redis process state handled." -ForegroundColor Green

# ==========================================================
# Step 2: Chroma
# ==========================================================
Write-Host "[2/4] Chroma..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d `"$root`" && chroma run" -WindowStyle Normal
Write-Host "  [OK] Chroma launching (port 8000)" -ForegroundColor Green

# ==========================================================
# Step 3: Spring Boot
# ==========================================================
Write-Host "[3/4] Spring Boot..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d `"$root\myllm`" && mvnw spring-boot:run" -WindowStyle Normal
Write-Host "  [OK] Backend launching (port 8080)" -ForegroundColor Green

# ==========================================================
# Step 4: Vue 3
# ==========================================================
Write-Host "[4/4] Vue 3..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k cd /d `"$root\vue\myllm-ui`" && npm run dev" -WindowStyle Normal
Write-Host "  [OK] Frontend launching (port 5173)" -ForegroundColor Green

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  All services launched" -ForegroundColor Green
Write-Host "  Redis: 6379 | Chroma: 8000 | Backend: 8080 | Frontend: 5173" -ForegroundColor Gray
Write-Host "  Open: http://localhost:5173" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan