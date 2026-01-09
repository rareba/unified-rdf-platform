$json = Get-Content register_cube.json -Raw
Invoke-RestMethod -Uri http://localhost:9080/api/v1/cubes -Method Post -Body $json -ContentType 'application/json' | ConvertTo-Json
