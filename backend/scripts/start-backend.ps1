# Windows PowerShell 5.1 compatible. No SQL migration, reseed, or admin bootstrap.
param(
    [string]$DatabaseUrl = '',
    [string]$DatabaseUser = '',
    [string]$FrontendOrigins = 'http://localhost:5174,http://127.0.0.1:5174'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$names = @('DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','CORS_ORIGINS','BOOTSTRAP_ADMIN_USERNAME','BOOTSTRAP_ADMIN_PASSWORD','SPRING_JPA_HIBERNATE_DDL_AUTO','SPRING_SQL_INIT_MODE')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
if (-not $DatabaseUrl) { $DatabaseUrl = $env:DB_URL }
if (-not $DatabaseUrl) { $DatabaseUrl = 'jdbc:postgresql://localhost:5432/patient_queue' }
if (-not $DatabaseUser) { $DatabaseUser = $env:DB_USERNAME }
if (-not $DatabaseUser) { $DatabaseUser = 'postgres' }
$configDir = Join-Path $env:LOCALAPPDATA 'PQueue'
$jwtPath = Join-Path $configDir 'jwt-secret.dpapi'
$oldPath = Join-Path $env:LOCALAPPDATA 'CareFlowOP\jwt-secret.dpapi'
$secret = $env:JWT_SECRET
if (-not $secret -and (Test-Path -LiteralPath $jwtPath)) {
    $secure = ConvertTo-SecureString ((Get-Content -LiteralPath $jwtPath -Raw).Trim())
    $secret = [System.Net.NetworkCredential]::new('', $secure).Password
} elseif (-not $secret -and (Test-Path -LiteralPath $oldPath)) {
    $secure = ConvertTo-SecureString ((Get-Content -LiteralPath $oldPath -Raw).Trim())
    $secret = [System.Net.NetworkCredential]::new('', $secure).Password
    Write-Host 'Reusing the secret previously stored by CareFlowOP.'
}
if (-not $secret) {
    Write-Host 'First launch: paste your EXISTING JWT secret to preserve issued tokens.'
    Write-Host 'Leave blank to generate a new key. Existing accounts/data remain; old tokens require sign-in again.'
    $entered = Read-Host 'Existing JWT secret, or Enter for a new one' -AsSecureString
    $secret = [System.Net.NetworkCredential]::new('', $entered).Password
    if (-not $secret) {
        $bytes = New-Object byte[] 48
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $secret = [Convert]::ToBase64String($bytes)
    }
}
if ([Text.Encoding]::UTF8.GetByteCount($secret) -lt 32 -or $secret.StartsWith('test_only_') -or $secret.StartsWith('default_dev_secret')) {
    throw 'Use your real local JWT key with at least 32 UTF-8 bytes, not the test key.'
}
if (Test-Path -LiteralPath $jwtPath) {
    $existingSecure = ConvertTo-SecureString ((Get-Content -LiteralPath $jwtPath -Raw).Trim())
    $existing = [System.Net.NetworkCredential]::new('', $existingSecure).Password
    if ($existing -cne $secret) { throw 'JWT_SECRET differs from the saved key. Resolve this explicitly; this script will not silently rotate it.' }
} else {
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    (ConvertTo-SecureString $secret -AsPlainText -Force) | ConvertFrom-SecureString | Set-Content -LiteralPath $jwtPath -Encoding Ascii
}
try {
    $env:DB_URL = $DatabaseUrl
    $env:DB_USERNAME = $DatabaseUser
    if (-not $env:DB_PASSWORD) {
        $dbSecure = Read-Host 'PostgreSQL password' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecure).Password
    }
    $env:JWT_SECRET = $secret
    $env:CORS_ORIGINS = $FrontendOrigins
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
    $env:SPRING_SQL_INIT_MODE = 'never'
    Remove-Item Env:\BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Write-Host "Starting PQueue against $DatabaseUrl. Schema validation only; no migrations."
    Push-Location $root
    try {
        if (Get-Command mvn -ErrorAction SilentlyContinue) { & mvn spring-boot:run }
        else { & (Join-Path $root 'mvnw.cmd') spring-boot:run }
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    Remove-Variable secret, secure, entered, existing, existingSecure, dbSecure -ErrorAction SilentlyContinue
}
