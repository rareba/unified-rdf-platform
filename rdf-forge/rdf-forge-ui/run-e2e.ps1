# E2E Test Runner for RDF Forge UI
# This script runs Playwright tests against the Docker UI on port 3000

$ErrorActionPreference = "Stop"

Write-Host "RDF Forge E2E Test Runner" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan

# Set environment variables
$env:E2E_SKIP_SERVER = "true"
$env:E2E_BASE_URL = "http://localhost:3000"

# Check if Docker UI is running
Write-Host "`nChecking Docker UI status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:3000" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Docker UI is running on port 3000" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Docker UI is not running on port 3000" -ForegroundColor Red
    Write-Host "Please run: docker compose up -d ui" -ForegroundColor Yellow
    exit 1
}

# Check if backend gateway is running
Write-Host "`nChecking Gateway status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8000/actuator/health" -TimeoutSec 5 -UseBasicParsing
    Write-Host "Gateway is running on port 8000" -ForegroundColor Green
} catch {
    Write-Host "WARNING: Gateway may not be running on port 8000" -ForegroundColor Yellow
}

# Run Playwright tests
Write-Host "`nRunning Playwright tests..." -ForegroundColor Yellow
Write-Host "Base URL: $env:E2E_BASE_URL" -ForegroundColor Gray

# Create screenshots directory
$screenshotDir = Join-Path $PSScriptRoot "playwright-report/screenshots"
if (-not (Test-Path $screenshotDir)) {
    New-Item -ItemType Directory -Path $screenshotDir -Force | Out-Null
}

# Run specific test file
$testFile = $args[0]
if (-not $testFile) {
    $testFile = "full-workflow"
}

Write-Host "`nRunning tests: $testFile" -ForegroundColor Cyan

# Change to script directory
Push-Location $PSScriptRoot

# Run Playwright with headed mode and single worker
# Use the local node_modules playwright
node_modules/.bin/playwright test $testFile --headed --project=chromium --workers=1 --timeout=120000

Pop-Location

$exitCode = $LASTEXITCODE

Write-Host "`n=================================" -ForegroundColor Cyan
if ($exitCode -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
} else {
    Write-Host "Some tests failed. Exit code: $exitCode" -ForegroundColor Red
    Write-Host "`nCheck the screenshots in: $screenshotDir" -ForegroundColor Yellow
    Write-Host "View HTML report: npx playwright show-report" -ForegroundColor Yellow
}

exit $exitCode
