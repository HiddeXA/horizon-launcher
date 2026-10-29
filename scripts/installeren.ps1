<#
.SYNOPSIS
  Installeert de gebouwde APK op de tv, compileert hem vooraf (speed-profile)
  en zet hem als homescherm. Het Google-homescherm blijft gewoon geïnstalleerd.
.EXAMPLE
  .\scripts\installeren.ps1                 # adres uit tv-adres.txt, of vragen
  .\scripts\installeren.ps1 -Ip 192.168.1.20:37123
#>
param(
    [string]$Ip,
    [string]$Adb,
    [string]$Apk,
    [switch]$NietAlsHomescherm
)
. "$PSScriptRoot\common.ps1"
if (-not $Apk) { $Apk = Join-Path $ProjectRoot 'launcher\app\build\outputs\apk\release\app-release.apk' }
$Adb    = Resolve-Adb $Adb
$target = Connect-Device $Adb (Get-TvAddress $Ip)

if (-not (Test-Path $Apk)) { throw "APK niet gevonden: $Apk. Draai eerst 2-bouwen." }

Write-Host 'Installeren...' -ForegroundColor Cyan
$out = Invoke-Adb $Adb -s $target install -r $Apk
if ($out -notmatch 'Success') { throw "Installeren mislukt: $out" }

# Kanaalbeelden van apps mogen lezen (top shelf). Faalt stil als de tv dit niet toestaat.
Write-Host ('Kanaal-permissie: ' + (Invoke-Shell $Adb $target 'pm grant nl.thuis.launcher com.android.providers.tv.permission.READ_EPG_DATA'))

# Vooraf compileren: geen trage eerste keer en geen JIT tijdens scrollen.
Write-Host 'Vooraf compileren (kan 30 s duren)...' -ForegroundColor Cyan
# Eerst één keer starten: de profileinstaller schrijft dan het compileerprofiel.
Invoke-Shell $Adb $target 'am start -n nl.thuis.launcher/.MainActivity' | Out-Null
Start-Sleep -Seconds 12
# Direct na installeren wordt de eerste poging soms genegeerd, dus herhalen tot de status klopt.
foreach ($try in 1..5) {
    Invoke-Shell $Adb $target 'cmd package compile -m speed-profile -f nl.thuis.launcher' | Out-Null
    Start-Sleep -Seconds 2
    $st = Invoke-Shell $Adb $target "dumpsys package nl.thuis.launcher | grep -o 'status=[a-z-]*' | head -1"
    Write-Host "  poging ${try}: $st"
    if ($st -match 'speed') { break }
    Start-Sleep -Seconds 5
}

if (-not $NietAlsHomescherm) {
    Write-Host (Invoke-Shell $Adb $target 'cmd package set-home-activity nl.thuis.launcher/.MainActivity')
}
Invoke-Shell $Adb $target 'am force-stop nl.thuis.launcher' | Out-Null
Invoke-Shell $Adb $target 'am start -n nl.thuis.launcher/.MainActivity' | Out-Null
Start-Sleep -Seconds 3
$procId = Invoke-Shell $Adb $target 'pidof nl.thuis.launcher'
if ($procId -match '^\d+') {
    Write-Host "Launcher draait (pid $procId). Kijk op je tv!" -ForegroundColor Green
} else {
    $crash = Invoke-Shell $Adb $target 'logcat -d -b crash -t 40'
    throw "Launcher draait niet. Crashlog:`n$crash"
}
