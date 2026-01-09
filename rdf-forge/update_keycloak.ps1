# Get admin token
$tokenResponse = Invoke-RestMethod -Uri "http://localhost:8080/realms/master/protocol/openid-connect/token" -Method Post -Body @{
    client_id = "admin-cli"
    username = "admin"
    password = "admin"
    grant_type = "password"
}
$token = $tokenResponse.access_token
Write-Host "Got token"

# Get the client ID for rdf-forge-ui
$headers = @{ Authorization = "Bearer $token" }
$clients = Invoke-RestMethod -Uri "http://localhost:8080/admin/realms/rdfforge/clients" -Headers $headers
$client = $clients | Where-Object { $_.clientId -eq "rdf-forge-ui" }
Write-Host "Found client: $($client.id)"

# Update the client with new redirect URIs
$client.redirectUris = @("http://localhost:3000/*", "http://localhost:4200/*", "http://localhost:9080/*")
$client.webOrigins = @("http://localhost:3000", "http://localhost:4200", "http://localhost:9080")

$body = $client | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "http://localhost:8080/admin/realms/rdfforge/clients/$($client.id)" -Method Put -Headers $headers -Body $body -ContentType "application/json"
Write-Host "Updated client redirect URIs"
