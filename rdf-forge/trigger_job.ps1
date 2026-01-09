$json = '{"pipelineId": "efe804d1-a42e-4ddc-80be-c5c49459e9ff", "variables": {}, "priority": 5, "dryRun": false}'
Invoke-RestMethod -Uri http://localhost:9080/api/v1/jobs -Method Post -Body $json -ContentType 'application/json' | ConvertTo-Json
