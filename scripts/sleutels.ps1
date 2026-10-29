<#
.SYNOPSIS
  Vraagt je TMDB-token, OMDb-sleutel en (optioneel) je Xtream-gegevens en zet ze in launcher\local.properties.
  Dat bestand blijft op deze pc en gaat nooit mee naar git. Daarna: 2-bouwen en 4-installeren.
  Je kunt alles ook later op de tv zelf invullen of wijzigen (tandwiel in de launcher).
#>
$ErrorActionPreference = 'Stop'
$lp = Join-Path (Split-Path $PSScriptRoot -Parent) 'launcher\local.properties'
$lines = if (Test-Path $lp) { @(Get-Content $lp) } else { @() }

function Get-Current($name) { ($lines | Where-Object { $_ -like "$name=*" } | Select-Object -First 1) -replace "^$name=", '' }
function Ask($label, $name) {
    $cur = Get-Current $name
    $hint = if ($cur) { ' (Enter = huidige houden)' } else { '' }
    $v = (Read-Host "$label$hint").Trim()
    if (-not $v) { return $cur }
    return $v
}

Write-Host ''
Write-Host '1) TMDB: themoviedb.org -> Instellingen -> API -> "API Read Access Token" (de lange, begint met eyJ)' -ForegroundColor Cyan
$tmdb = Ask 'Plak je TMDB API Read Access Token' 'tmdb.token'
if ($tmdb -and $tmdb -notmatch '^eyJ') { Write-Warning 'Dit lijkt niet op een Read Access Token (die begint met eyJ). Je gebruikt mogelijk de korte API-key; neem de lange.' }

Write-Host ''
Write-Host '2) OMDb: de key uit de bevestigingsmail van omdbapi.com (8 tekens). Let op: eerst de activatielink in die mail klikken.' -ForegroundColor Cyan
$omdb = Ask 'Plak je OMDb API key' 'omdb.key'

Write-Host ''
Write-Host '3) Horizon (optioneel): de Xtream-gegevens van je eigen IPTV-abonnement.' -ForegroundColor Cyan
Write-Host '   Laat leeg en druk Enter om over te slaan of de huidige te houden.'
$xServer = Ask 'Server-adres (bijv. http://provider.tv:8080)' 'xtream.server'
$xUser   = Ask 'Gebruikersnaam' 'xtream.user'
$curPass = Get-Current 'xtream.pass'
$sec = Read-Host ('Wachtwoord' + $(if ($curPass) { ' (Enter = huidige houden)' } else { '' })) -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
$xPass = if ($plain) { $plain } elseif ($curPass) { [regex]::Unescape($curPass) } else { '' }  # bestaande waarde terug naar gewone tekst

# local.properties-formaat: backslash verdubbelen en niet-ASCII tekens als \uXXXX (Java leest het bestand als Latin-1).
function Esc($v) {
    if ($null -eq $v) { return '' }
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $v.ToCharArray()) {
        if ($ch -eq [char]92) { [void]$sb.Append('\\') }
        elseif ([int]$ch -gt 126) { [void]$sb.Append(('\u{0:x4}' -f [int]$ch)) }
        else { [void]$sb.Append($ch) }
    }
    $sb.ToString()
}

$keep = $lines | Where-Object { $_ -notmatch '^(tmdb\.token|omdb\.key|xtream\.server|xtream\.user|xtream\.pass)=' }
@($keep) + @("tmdb.token=$tmdb", "omdb.key=$omdb", "xtream.server=$(Esc $xServer)", "xtream.user=$(Esc $xUser)", "xtream.pass=$(Esc $xPass)") |
    Set-Content $lp -Encoding ASCII

# Snelle controle of de sleutels werken (alleen een statuscode, er wordt niets gelogd).
try {
    $r = Invoke-WebRequest -UseBasicParsing -Uri 'https://api.themoviedb.org/3/configuration' -Headers @{ Authorization = "Bearer $tmdb" }
    Write-Host "TMDB: OK ($($r.StatusCode))" -ForegroundColor Green
} catch { Write-Host "TMDB: werkt NIET ($($_.Exception.Message))" -ForegroundColor Red }
if ($omdb) {
    try {
        $j = Invoke-RestMethod -Uri "https://www.omdbapi.com/?i=tt0111161&apikey=$omdb"
        if ($j.Response -eq 'True') { Write-Host 'OMDb: OK' -ForegroundColor Green }
        else { Write-Host "OMDb: werkt NIET ($($j.Error))" -ForegroundColor Red }
    } catch { Write-Host "OMDb: werkt NIET ($($_.Exception.Message))" -ForegroundColor Red }
}
if ($xServer -and $xUser -and $xPass) {
    try {
        $base = if ($xServer -match '^https?://') { $xServer.TrimEnd('/') } else { 'http://' + $xServer.TrimEnd('/') }
        $u = "$base/player_api.php?username=$([uri]::EscapeDataString($xUser))&password=$([uri]::EscapeDataString($xPass))"
        $r = Invoke-RestMethod -Uri $u -TimeoutSec 15
        if ($r.user_info.auth -eq 1) {
            Write-Host "Horizon: inloggen OK (account $($r.user_info.status), verloopt: $(if ($r.user_info.exp_date) { [DateTimeOffset]::FromUnixTimeSeconds([long]$r.user_info.exp_date).ToString('d-M-yyyy') } else { 'onbekend' }))" -ForegroundColor Green
        } else { Write-Host 'Horizon: inloggen MISLUKT (gebruikersnaam of wachtwoord klopt niet)' -ForegroundColor Red }
    } catch { Write-Host "Horizon: server niet bereikbaar ($($_.Exception.Message -replace [regex]::Escape($xPass), '***'))" -ForegroundColor Red }
}
Write-Host "`nOpgeslagen in $lp. Draai nu 2-bouwen en daarna 4-installeren." -ForegroundColor Green
