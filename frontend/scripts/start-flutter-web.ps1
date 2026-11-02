param([string]$ApiBaseUrl = 'http://localhost:8080', [int]$WebPort = 5174)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Get-Command flutter -ErrorAction SilentlyContinue)) { throw 'Flutter is not on PATH.' }
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & flutter run -d chrome "--web-port=$WebPort" '--web-hostname=localhost' "--dart-define=API_BASE_URL=$ApiBaseUrl"
    if ($LASTEXITCODE -ne 0) { throw "Flutter exited with code $LASTEXITCODE" }
} finally { Pop-Location }
