# Test Configuration
$baseUrl = "http://localhost:8000/api/v1"
$projectId = "11111111-1111-1111-1111-111111111111"
$userId = "00000000-0000-0000-0000-000000000000"

function Test-Endpoint {
    param (
        [string]$Method,
        [string]$Uri,
        [hashtable]$Body = $null,
        [string]$Description
    )

    Write-Host "Testing: $Description" -ForegroundColor Cyan
    try {
        $params = @{
            Uri = "$baseUrl$Uri"
            Method = $Method
            ContentType = "application/json"
            ErrorAction = "Stop"
        }
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
        }

        $response = Invoke-RestMethod @params
        Write-Host "SUCCESS" -ForegroundColor Green
        return $response
    } catch {
        Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                Write-Host "Response Body: $($reader.ReadToEnd())" -ForegroundColor Yellow
            }
        }
        return $null
    }
}

# Wait for Gateway
Write-Host "Waiting for Gateway to be ready..." -ForegroundColor Gray
Start-Sleep -Seconds 10

# 1. Health Check
Test-Endpoint -Method Get -Uri "/actuator/health" -Description "Gateway Health Check"

# 2. Data Sources
$dataSources = Test-Endpoint -Method Get -Uri "/data?projectId=$projectId" -Description "List Data Sources"

# 3. Dimensions
$dimensionBody = @{
    projectId = $projectId
    name = "Test Dimension"
    description = "A test dimension"
    type = "CATEGORICAL"
    hierarchyType = "NONE"
    createdBy = $userId
}
$dim = Test-Endpoint -Method Post -Uri "/dimensions" -Body $dimensionBody -Description "Create Dimension"
if ($dim) {
    $dimId = $dim.id
    Test-Endpoint -Method Get -Uri "/dimensions/$dimId" -Description "Get Dimension"
    
    # Add Value
    $valBody = @{
        code = "V1"
        label = "Value 1"
    }
    Test-Endpoint -Method Post -Uri "/dimensions/$dimId/values" -Body $valBody -Description "Add Dimension Value"
}

# 4. Shapes
$shapeBody = @{
    projectId = $projectId
    uri = "http://example.org/shapes/test"
    name = "Test Shape"
    targetClass = "http://example.org/Test"
    content = "@prefix sh: <http://www.w3.org/ns/shacl#> . @prefix ex: <http://example.org/> . ex:TestShape a sh:NodeShape ; sh:targetClass ex:Test ."
    createdBy = $userId
}
$shape = Test-Endpoint -Method Post -Uri "/shapes" -Body $shapeBody -Description "Create SHACL Shape"

# 5. Pipelines
$pipelineBody = @{
    projectId = $projectId
    name = "End-to-End Test Pipeline"
    description = "Created by CLI test"
    definitionFormat = "JSON"
    definition = '{"steps": []}'
    variables = @{}
    createdBy = $userId
}
$pipeline = Test-Endpoint -Method Post -Uri "/pipelines" -Body $pipelineBody -Description "Create Pipeline"

if ($pipeline) {
    $pipelineId = $pipeline.id
    
    # 6. Jobs (Dry Run)
    $dryRunJobBody = @{
        pipelineId = $pipelineId
        variables = @{}
        priority = 5
        dryRun = $true
    }
    $dryRunJob = Test-Endpoint -Method Post -Uri "/jobs" -Body $dryRunJobBody -Description "Create Dry Run Job"
    
    if ($dryRunJob) {
        Start-Sleep -Seconds 2
        Test-Endpoint -Method Get -Uri "/jobs/$($dryRunJob.id)" -Description "Check Dry Run Job Status"
    }

    # 7. Jobs (Real)
    $realJobBody = @{
        pipelineId = $pipelineId
        variables = @{}
        priority = 10
        dryRun = $false
    }
    $realJob = Test-Endpoint -Method Post -Uri "/jobs" -Body $realJobBody -Description "Create Real Job"
     if ($realJob) {
        Start-Sleep -Seconds 2
        Test-Endpoint -Method Get -Uri "/jobs/$($realJob.id)" -Description "Check Real Job Status"
    }
}

# 8. Triplestores
$tsBody = @{
    projectId = $projectId
    name = "Test Fuseki"
    type = "FUSEKI"
    url = "http://fuseki:3030/ds"
    authType = "NONE"
    createdBy = $userId
}
$ts = Test-Endpoint -Method Post -Uri "/triplestores" -Body $tsBody -Description "Create Triplestore Connection"

if ($ts) {
    Test-Endpoint -Method Get -Uri "/triplestores/$($ts.id)/health" -Description "Test Triplestore Connection"
}

Write-Host "Test Suite Completed" -ForegroundColor Magenta