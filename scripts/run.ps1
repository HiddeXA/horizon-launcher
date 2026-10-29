param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Cmd)
$ErrorActionPreference = 'Continue'
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
New-Item -ItemType Directory -Force -Path logs | Out-Null
$log = Join-Path $Root "logs\$Name.log"
Start-Transcript -Path $log -Force | Out-Null
try {
    Invoke-Expression $Cmd
    Write-Host "`n=== KLAAR ===" -ForegroundColor Green
} catch {
    Write-Host "`n=== FOUT === $($_.Exception.Message)" -ForegroundColor Red
} finally {
    Stop-Transcript | Out-Null
}
