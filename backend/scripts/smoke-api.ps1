param([string]$ApiBaseUrl = 'http://localhost:8080', [string]$Username = 'clinic_admin')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$secure = Read-Host "Password for $Username" -AsSecureString
try {
    $plain = [System.Net.NetworkCredential]::new('', $secure).Password
    $body = @{ username = $Username; password = $plain } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$ApiBaseUrl/api/users/login" -Method Post -ContentType 'application/json' -Body $body
    if (-not $login.token) { throw 'No token returned.' }
    $headers = @{ Authorization = "Bearer $($login.token)" }
    $me = Invoke-RestMethod -Uri "$ApiBaseUrl/api/users/me" -Headers $headers
    if ($me.role -ne 'ADMIN') { throw 'Use an ADMIN account for this read-only reporting smoke test.' }
    $report = Invoke-RestMethod -Uri "$ApiBaseUrl/api/analytics/dashboard" -Headers $headers
    if ($null -eq $report.summary -or $null -eq $report.daily -or $null -eq $report.quality) { throw 'Incomplete dashboard contract.' }
    $sum = ($report.daily | Measure-Object -Property checkins -Sum).Sum
    if ([long]$sum -ne [long]$report.summary.total) { throw 'Daily total differs from summary.' }
    $csv = Invoke-WebRequest -UseBasicParsing -Uri "$ApiBaseUrl/api/analytics/export.csv" -Headers $headers
    if ($csv.Content -notmatch 'service_date,doctor_id,check_ins') { throw 'CSV header mismatch.' }
    Write-Host "PASS: login, profile, reporting snapshot, daily reconciliation and CSV. Check-ins: $sum."
    Write-Host 'No patient records or accounts were changed. No tokens displayed.'
} finally {
    Remove-Variable secure, plain, body, login, headers -ErrorAction SilentlyContinue
}
