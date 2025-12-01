$headers = @{
    "Content-Type" = "application/json"
}
$body = '{"pipelineId": "22222222-2222-2222-2222-222222222222", "variables": {}, "priority": 1, "dryRun": true}'
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8000/api/v1/jobs" -Method Post -Headers $headers -Body $body
    Write-Host "Job Created: $($response.id)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "Response: $($reader.ReadToEnd())"
    }
}