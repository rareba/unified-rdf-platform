# Sample Cube Pipeline Execution Script
# This script creates and runs a pipeline that:
# 1. Loads CSV data
# 2. Builds a cube structure with dimensions and measures
# 3. Creates observations from the data
# 4. Builds a SHACL cube shape
# 5. Validates the cube
# 6. Publishes to GraphDB

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token = $null,
    [switch]$UseKeycloak = $false,
    [switch]$WaitForCompletion = $true
)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Info($msg) { Write-Host $msg -ForegroundColor Cyan }
function Write-Success($msg) { Write-Host $msg -ForegroundColor Green }
function Write-Error($msg) { Write-Host $msg -ForegroundColor Red }
function Write-Warning($msg) { Write-Host $msg -ForegroundColor Yellow }

Write-Info "=========================================="
Write-Info "RDF Forge Sample Cube Pipeline Execution"
Write-Info "=========================================="
Write-Info ""

# Validate base URL
if (-not $BaseUrl) {
    Write-Error "BaseUrl is required"
    exit 1
}

# Get authentication token
$headers = @{}
if ($Token) {
    $headers["Authorization"] = "Bearer $Token"
    Write-Info "Using provided authentication token"
} elseif ($UseKeycloak) {
    Write-Info "Requesting Keycloak token..."
    $keycloakUrl = "http://localhost:8082/realms/rdf-forge/protocol/openid-connect/token"
    $body = @{
        grant_type = "password"
        client_id = "rdf-forge-client"
        username = "admin"
        password = "admin"
    }
    try {
        $tokenResponse = Invoke-RestMethod -Uri $keycloakUrl -Method POST -Body $body -ContentType "application/x-www-form-urlencoded"
        $Token = $tokenResponse.access_token
        $headers["Authorization"] = "Bearer $Token"
        Write-Success "Successfully authenticated with Keycloak"
    } catch {
        Write-Error "Failed to obtain Keycloak token: $_"
        exit 1
    }
} else {
    Write-Warning "No authentication token provided. Some operations may fail if authentication is required."
}

$headers["Content-Type"] = "application/json"

# Step 1: Load sample data (create if not exists)
Write-Info ""
Write-Info "Step 1: Checking/Creating sample data..."
$sampleDataExists = Test-Path -Path "./swiss_economy.csv"
if (-not $sampleDataExists) {
    Write-Info "Creating sample CSV data..."
    $csvContent = @"
Year,Region,Sector,Value,Status
2020,Zurich,Finance,150.5,D
2020,Geneva,Technology,89.3,D
2020,Bern,Healthcare,45.2,D
2020,Basel,Pharma,120.7,D
2021,Zurich,Finance,155.2,D
2021,Geneva,Technology,95.8,D
2021,Bern,Healthcare,47.9,D
2021,Basel,Pharma,128.4,D
2022,Zurich,Finance,160.1,D
2022,Geneva,Technology,102.5,D
2022,Bern,Healthcare,50.3,D
2022,Basel,Pharma,135.9,D
2023,Zurich,Finance,168.7,D
2023,Geneva,Technology,110.2,D
2023,Bern,Healthcare,53.1,D
2023,Basel,Pharma,142.6,D
"@
    $csvContent | Out-File -FilePath "./swiss_economy.csv" -Encoding UTF8
    Write-Success "Sample CSV data created: ./swiss_economy.csv"
} else {
    Write-Info "Sample data already exists"
}

# Step 2: Create the pipeline
Write-Info ""
Write-Info "Step 2: Creating pipeline..."
$pipelineDefinition = Get-Content -Path "./sample-cube-pipeline.json" -Raw | ConvertFrom-Json -AsHashtable

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/pipelines" -Method POST -Body (ConvertTo-Json -Depth 10 $pipelineDefinition) -Headers $headers
    $pipelineId = $response.id
    Write-Success "Pipeline created with ID: $pipelineId"
} catch {
    Write-Error "Failed to create pipeline: $_"
    Write-Error "Response: $_.ErrorDetails"
    exit 1
}

# Step 3: Execute the pipeline
Write-Info ""
Write-Info "Step 3: Executing pipeline..."
$runBody = @{
    variables = @{}
}
try {
    $runResponse = Invoke-RestMethod -Uri "$BaseUrl/api/pipelines/$pipelineId/run" -Method POST -Body (ConvertTo-Json $runBody) -Headers $headers
    $jobId = $runResponse.jobId
    Write-Success "Pipeline execution started with Job ID: $jobId"
} catch {
    Write-Error "Failed to start pipeline execution: $_"
    exit 1
}

# Step 4: Monitor execution via polling
Write-Info ""
Write-Info "Step 4: Monitoring execution..."
if ($WaitForCompletion) {
    $maxAttempts = 60
    $attempt = 0
    $completed = $false
    $jobStatus = "UNKNOWN"

    while ($attempt -lt $maxAttempts -and -not $completed) {
        $attempt++
        Start-Sleep -Seconds 2

        try {
            $jobResponse = Invoke-RestMethod -Uri "$BaseUrl/api/jobs/$jobId" -Method GET -Headers $headers
            $jobStatus = $jobResponse.status
            Write-Info "Attempt $attempt/$maxAttempts - Job Status: $jobStatus"

            if ($jobStatus -in @("COMPLETED", "FAILED", "CANCELLED")) {
                $completed = $true
            }
        } catch {
            Write-Warning "Failed to get job status: $_"
        }
    }

    if (-not $completed) {
        Write-Error "Pipeline did not complete within the timeout period"
        exit 1
    }

    Write-Info ""
    Write-Info "Job completed with status: $jobStatus"
} else {
    Write-Info "Running asynchronously. Check job status at: $BaseUrl/api/jobs/$jobId"
}

# Step 5: Get job logs
Write-Info ""
Write-Info "Step 5: Retrieving job logs..."
try {
    $logsResponse = Invoke-RestMethod -Uri "$BaseUrl/api/jobs/$jobId/logs" -Method GET -Headers $headers
    Write-Info "Job logs:"
    $logsResponse | ForEach-Object {
        Write-Host "[$_.level] $_.message"
    }
} catch {
    Write-Warning "Could not retrieve job logs: $_"
}

# Step 6: Get job result and metrics
Write-Info ""
Write-Info "Step 6: Execution summary..."
try {
    $jobDetails = Invoke-RestMethod -Uri "$BaseUrl/api/jobs/$jobId" -Method GET -Headers $headers
    $metrics = @{
        "Job ID" = $jobId
        "Pipeline ID" = $pipelineId
        "Status" = $jobDetails.status
        "Start Time" = $jobDetails.startedAt
        "End Time" = $jobDetails.completedAt
        "Duration" = if ($jobDetails.duration) { "$($jobDetails.duration)ms" } else { "N/A" }
        "Records Processed" = $jobDetails.recordsProcessed
        "Triplestore Rows" = $jobDetails.triplestoreRows
    }

    Write-Info ""
    Write-Info "=========================================="
    Write-Info "Pipeline Execution Summary"
    Write-Info "=========================================="
    foreach ($key in $metrics.Keys) {
        Write-Info "$key : $($metrics[$key])"
    }

    # Output success or failure message
    Write-Info "=========================================="
    if ($jobDetails.status -eq "COMPLETED") {
        Write-Success "Pipeline executed successfully!"
        Write-Success ""
        Write-Success "The Cube is now available at:"
        Write-Success "  Dataset: https://example.org/cube/swiss-economy"
        Write-Success "  Graph: https://example.org/graph/swiss-economy"
        Write-Success "  Constraint: https://example.org/cube/swiss-economy/constraint"
    } else {
        Write-Error "Pipeline execution failed with status: $($jobDetails.status)"
        if ($jobDetails.errorMessage) {
            Write-Error "Error: $($jobDetails.errorMessage)"
        }
        exit 1
    }
} catch {
    Write-Error "Failed to retrieve job details: $_"
    exit 1
}

Write-Info ""
Write-Info "=========================================="
Write-Info "Test Complete"
Write-Info "=========================================="
