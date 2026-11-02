param([string]$DatabaseUrl = '', [string]$DatabaseUser = '')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$names = @('DB_URL','DB_USERNAME','DB_PASSWORD','BOOTSTRAP_ADMIN_USERNAME','BOOTSTRAP_ADMIN_PASSWORD','SPRING_JPA_HIBERNATE_DDL_AUTO','SPRING_SQL_INIT_MODE')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
if (-not $DatabaseUrl) { $DatabaseUrl = $env:DB_URL }
if (-not $DatabaseUrl) { $DatabaseUrl = 'jdbc:postgresql://localhost:5432/patient_queue' }
if (-not $DatabaseUser) { $DatabaseUser = $env:DB_USERNAME }
if (-not $DatabaseUser) { $DatabaseUser = 'postgres' }
try {
    $env:DB_URL = $DatabaseUrl; $env:DB_USERNAME = $DatabaseUser
    if (-not $env:DB_PASSWORD) {
        $secure = Read-Host 'PostgreSQL password for the synthetic development database' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
    }
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
    $env:SPRING_SQL_INIT_MODE = 'never'
    Remove-Item Env:\BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Write-Host 'Unit tests plus read-only schema/context check. Opt-in database integration tests remain separate.'
    Push-Location $root
    try {
        if (Get-Command mvn -ErrorAction SilentlyContinue) { & mvn clean test }
        else { & (Join-Path $root 'mvnw.cmd') clean test }
        if ($LASTEXITCODE -ne 0) { throw "Tests failed with code $LASTEXITCODE. Do not start the new frontend yet." }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    Remove-Variable secure -ErrorAction SilentlyContinue
}
