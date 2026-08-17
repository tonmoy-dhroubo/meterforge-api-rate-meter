# MeterForge Burst Traffic Simulation Script (PowerShell)
# Demonstrates atomic multi-policy rate limiting against the local Gateway (:8890).

param (
    [int]$Count = 10,
    [string]$GatewayUrl = "http://localhost:8890/v1/forecast/tokyo",
    [string]$ApiKey = "mf_dev_nsdemo123456_seedednorthstardemosecretkey9999"
)

$DisplayKey = if ($ApiKey.Length -gt 20) { $ApiKey.Substring(0, 20) + "..." } else { $ApiKey }
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  MeterForge Burst Traffic Dispatcher" -ForegroundColor Cyan
Write-Host "  Target: $GatewayUrl" -ForegroundColor Gray
Write-Host "  Count:  $Count concurrent requests" -ForegroundColor Gray
Write-Host "  Key:    $DisplayKey" -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Cyan

$jobs = @()
$scriptBlock = {
    param($url, $key, $idx)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $headers = @{ "X-API-Key" = $key }
        $resp = Invoke-WebRequest -Uri $url -Headers $headers -Method GET -SkipHttpErrorCheck
        $sw.Stop()
        return [PSCustomObject]@{
            Index = $idx
            StatusCode = [int]$resp.StatusCode
            StatusDescription = $resp.StatusDescription
            Remaining = $resp.Headers["X-RateLimit-Remaining"]
            RetryAfter = $resp.Headers["Retry-After"]
            LatencyMs = $sw.ElapsedMilliseconds
        }
    } catch {
        $sw.Stop()
        return [PSCustomObject]@{
            Index = $idx
            StatusCode = 0
            StatusDescription = $_.Exception.Message
            Remaining = $null
            RetryAfter = $null
            LatencyMs = $sw.ElapsedMilliseconds
        }
    }
}

for ($i = 1; $i -le $Count; $i++) {
    $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $GatewayUrl, $ApiKey, $i
}

Write-Host "`nWaiting for $Count requests to complete..." -ForegroundColor Yellow
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

$allowed = ($results | Where-Object { $_.StatusCode -eq 200 }).Count
$limited = ($results | Where-Object { $_.StatusCode -eq 429 }).Count
$errors = ($results | Where-Object { $_.StatusCode -ne 200 -and $_.StatusCode -ne 429 }).Count
$avgLatency = ($results | Measure-Object -Property LatencyMs -Average).Average

Write-Host "`n--- Execution Summary ---" -ForegroundColor Green
$results | Sort-Object Index | ForEach-Object {
    $color = if ($_.StatusCode -eq 200) { "Green" } elseif ($_.StatusCode -eq 429) { "Red" } else { "Yellow" }
    $remText = if ($_.Remaining) { " (Remaining: $($_.Remaining))" } else { "" }
    $retryText = if ($_.RetryAfter) { " (Retry-After: $($_.RetryAfter)s)" } else { "" }
    Write-Host "  Request #$($_.Index.ToString().PadLeft(2)): HTTP $($_.StatusCode) - $($_.LatencyMs)ms$remText$retryText" -ForegroundColor $color
}

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  Total Sent:     $Count" -ForegroundColor White
Write-Host "  Allowed (200):  $allowed" -ForegroundColor Green
Write-Host "  Limited (429):  $limited" -ForegroundColor Red
Write-Host "  Other Errors:   $errors" -ForegroundColor Yellow
Write-Host "  Avg Latency:    $([math]::Round($avgLatency, 1)) ms" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan
