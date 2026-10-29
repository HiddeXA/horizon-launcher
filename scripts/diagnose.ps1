<#
.SYNOPSIS
  Voert de regels uit logs\commands.txt uit op de tv en toont de uitvoer. Handig om te testen.
  Per regel:
    <shellopdracht>          adb shell <opdracht>
    adb <opdracht>           een adb-opdracht, bijv. adb pull /sdcard/a.png logs/a.png
    sleep N                  N seconden wachten
    ps <powershell>          PowerShell op de pc
    waitlog TAG SEC TEKST    wacht tot TEKST in logcat verschijnt (stopt bij time-out)
    require <tekst>          stopt als de app vooraan die tekst niet bevat (gebruik dit vóór toetsaanslagen)
  Regels die met # beginnen worden overgeslagen.
#>
param([string]$Ip, [string]$Adb, [string]$File)
. "$PSScriptRoot\common.ps1"
if (-not $File) { $File = Join-Path $ProjectRoot 'logs\commands.txt' }
if (-not (Test-Path $File)) { throw "Geen testlijst gevonden: $File" }
$Adb = Resolve-Adb $Adb
$target = Connect-Device $Adb (Get-TvAddress $Ip)
foreach ($line in Get-Content $File) {
    if (-not $line.Trim() -or $line.StartsWith('#')) { continue }
    Write-Host "`n>>> $line" -ForegroundColor Cyan
    if ($line -match '^adb (.*)$') {
        $parts = $Matches[1] -split ' '
        Write-Host (Invoke-Adb $Adb -s $target @parts)
    } elseif ($line -match '^waitlog (\S+) (\d+) (.+)$') {
        $tag = $Matches[1]; $max = [int]$Matches[2]; $text = $Matches[3]; $ok = $false
        for ($t = 0; $t -lt $max; $t += 2) {
            $log = Invoke-Shell $Adb $target "logcat -d -s ${tag}:*"
            if ($log -match [regex]::Escape($text)) { $ok = $true; break }
            Start-Sleep -Seconds 2
        }
        Write-Host ("  " + $(if ($ok) { "gevonden na $t s" } else { "NIET gevonden binnen $max s" }))
        if (-not $ok) { Write-Host 'Test gestopt (wachtte vergeefs).' -ForegroundColor Red; break }
    } elseif ($line -match '^require (.+)$') {
        $top = Invoke-Shell $Adb $target "dumpsys activity activities | grep topResumedActivity"
        if ($top -notmatch [regex]::Escape($Matches[1])) {
            Write-Host "Test gestopt: verwacht $($Matches[1]) vooraan, maar het is: $top" -ForegroundColor Red; break
        }
        Write-Host '  ok'
    } elseif ($line -match '^ps (.*)$') {
        try { Write-Host (Invoke-Expression $Matches[1] | Out-String) } catch { Write-Host "FOUT: $($_.Exception.Message)" }
    } elseif ($line -match '^sleep (\d+(\.\d+)?)$') {
        Start-Sleep -Milliseconds ([int]([double]$Matches[1] * 1000))
    } else {
        Write-Host (Invoke-Shell $Adb $target $line)
    }
}
