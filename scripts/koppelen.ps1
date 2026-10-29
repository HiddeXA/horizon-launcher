<#
.SYNOPSIS
  Koppelt deze pc eenmalig met de tv via Draadloze foutopsporing.
  Op de tv: Ontwikkelaarsopties > Draadloze foutopsporing > "Apparaat koppelen met koppelingscode".
.EXAMPLE
  .\scripts\koppelen.ps1                       # vraagt alles
  .\scripts\koppelen.ps1 -PairAddress 192.168.1.20:41234 -Code 123456
#>
param([string]$PairAddress, [string]$Code, [string]$Adb)
. "$PSScriptRoot\common.ps1"
$Adb = Resolve-Adb $Adb
if (-not $PairAddress) { $PairAddress = (Read-Host 'Koppel-adres dat de tv toont (IP:poort)').Trim() }
if (-not $Code)        { $Code        = (Read-Host 'Koppelingscode (6 cijfers)').Trim() }
Invoke-Adb $Adb start-server | Out-Null
$out = Invoke-Adb $Adb pair $PairAddress $Code
Write-Host $out
if ($out -notmatch 'Successfully paired') { throw "Koppelen mislukt: $out" }
Write-Host 'Gekoppeld.' -ForegroundColor Green
Write-Host ''
Write-Host 'Let op: het adres om te verbinden is een ANDERE poort dan het koppel-adres.' -ForegroundColor Cyan
$ip = Get-TvAddress (Read-Host 'IP-adres en poort onder "Draadloze foutopsporing" (IP:poort)').Trim()
Connect-Device $Adb $ip | Out-Null
Write-Host 'Klaar. Draai nu 4-installeren.' -ForegroundColor Green
