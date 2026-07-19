#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $Root

if (-not (Test-Path ".env")) {
  Copy-Item ".env.example" ".env"
  Write-Host "Created .env (LLM_PROVIDER=mock, keys optional)."
}

Write-Host "Starting docker compose (mock LLM + demo MySQL)..."
docker compose up -d --build

$baseUrl = if ($env:CHATBI_SMOKE_BASE_URL) { $env:CHATBI_SMOKE_BASE_URL } else { "http://localhost:8080" }
Write-Host "Running mock demo smoke..."
node scripts/smoke-mock-demo.mjs $baseUrl
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$backendPort = if ($env:BACKEND_HOST_PORT) { $env:BACKEND_HOST_PORT } else { "8080" }
$frontendPort = if ($env:FRONTEND_HOST_PORT) { $env:FRONTEND_HOST_PORT } else { "8888" }
Write-Host @"

Mock demo is up.
  Web UI:  http://localhost:${frontendPort}/
  API:     http://localhost:${backendPort}/api/health

Try asking: 「各产品类目的销售额占比」
Follow-up: 「只看华东大区」
Stop: docker compose down
"@
