$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer <token>"
}
$body = '{"pipelineId": "22222222-2222-2222-2222-222222222222", "variables": {}, "priority": 1, "dryRun": true}'
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/jobs" -Method Post -Headers $headers -Body $body