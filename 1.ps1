$root = $PSScriptRoot
Start-Process cmd -ArgumentList "/k cd /d $root && chroma run" -WindowStyle Normal
Start-Process cmd -ArgumentList "/k cd /d $root\vue\myllm-ui && npm run dev" -WindowStyle Normal
Start-Process cmd -ArgumentList "/k cd /d $root\myllm && mvnw spring-boot:run" -WindowStyle Normal
Write-Host "all serve have run"