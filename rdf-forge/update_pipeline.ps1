$json = Get-Content update_pipeline.json -Raw
Invoke-RestMethod -Uri http://localhost:9080/api/v1/pipelines/efe804d1-a42e-4ddc-80be-c5c49459e9ff -Method Put -Body $json -ContentType 'application/json'
