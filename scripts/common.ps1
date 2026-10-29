# Gedeelde helpers voor de scripts. Wordt ingeladen met: . "$PSScriptRoot\common.ps1"

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$TvAddressFile = Join-Path $ProjectRoot 'tv-adres.txt'   # staat in .gitignore

# Roept adb aan. Windows PowerShell 5.1 ziet gewone meldingen van adb op stderr
# (bijv. "daemon not running; starting now") anders als fout.
function Invoke-Adb {
    # Eenvoudige functie (geen param-blok), zodat adb-opties als -s gewoon doorgegeven worden.
    $exe = $args[0]
    $rest = @($args | Select-Object -Skip 1)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { return ((& $exe @rest 2>&1 | ForEach-Object { "$_" }) | Out-String).Trim() }
    finally { $ErrorActionPreference = $old }
}

function Resolve-Adb {
    param([string]$Adb)
    if ($Adb -and (Test-Path $Adb)) { return $Adb }
    $cached = Join-Path $ProjectRoot 'tools-cache\android-sdk\platform-tools\adb.exe'
    if (Test-Path $cached) { return (Resolve-Path $cached).Path }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "adb.exe niet gevonden. Draai eerst 2-bouwen (dat zet adb in tools-cache) of geef -Adb <pad> mee."
}

# Het adres van de tv (IP:poort uit het menu Draadloze foutopsporing).
# Volgorde: meegegeven -Ip, anders tv-adres.txt, anders vragen. Een nieuw adres wordt bewaard.
function Get-TvAddress {
    param([string]$Ip)
    if (-not $Ip -and (Test-Path $TvAddressFile)) { $Ip = (Get-Content $TvAddressFile -TotalCount 1).Trim() }
    if (-not $Ip) {
        Write-Host 'Op de tv: Instellingen > Systeem > Ontwikkelaarsopties > Draadloze foutopsporing.' -ForegroundColor Cyan
        Write-Host 'Daar staat "IP-adres en poort", bijvoorbeeld 192.168.1.20:37123.' -ForegroundColor Cyan
        $Ip = (Read-Host 'IP-adres en poort van je tv').Trim()
    }
    if (-not $Ip) { throw 'Geen adres opgegeven.' }
    Set-Content -Path $TvAddressFile -Value $Ip -Encoding ASCII
    return $Ip
}

function Connect-Device {
    param([string]$Adb, [string]$Ip)
    $target = if ($Ip -match ':\d+$') { $Ip } else { "${Ip}:5555" }
    Invoke-Adb $Adb start-server | Out-Null
    $out = Invoke-Adb $Adb connect $target
    if ($out -notmatch 'connected') {
        throw "Kon niet verbinden met ${target}: $out`nDe poort verandert na een herstart van de tv. Verwijder tv-adres.txt en probeer opnieuw."
    }
    $state = Invoke-Adb $Adb -s $target get-state
    if ($state -ne 'device') {
        throw "Apparaat staat op '$state'. Bevestig de melding op je tv (Altijd toestaan) en probeer opnieuw."
    }
    Write-Host "Verbonden met $target" -ForegroundColor Green
    return $target
}

function Invoke-Shell {
    param([string]$Adb, [string]$Target, [string]$Command)
    return Invoke-Adb $Adb -s $Target shell $Command
}
