<#
.SYNOPSIS
  Zet alles klaar om de launcher te bouwen (zonder Android Studio) en bouwt de APK.
  Alles komt in de map tools-cache in de projectmap; er wordt niets systeembreed geïnstalleerd.
  Eerste keer: ±1,5 GB downloaden, 5-15 minuten. Daarna gaat bouwen in een minuut.
.EXAMPLE
  2-bouwen.cmd  (of: powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1)
#>
param([string]$Task = 'assembleRelease')   # bijv. testReleaseUnitTest voor de unit tests
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # maakt Invoke-WebRequest veel sneller

$Root  = Split-Path $PSScriptRoot -Parent
$Cache = Join-Path $Root 'tools-cache'
$Sdk   = Join-Path $Cache 'android-sdk'
New-Item -ItemType Directory -Force -Path $Cache, $Sdk | Out-Null

function Get-Zip($Url, $Name, $Dest) {
    $zip = Join-Path $Cache $Name
    if (-not (Test-Path $zip)) {
        Write-Host "Downloaden: $Name" -ForegroundColor Cyan
        Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
    }
    if (-not (Test-Path $Dest)) {
        Write-Host "Uitpakken: $Name" -ForegroundColor Cyan
        Expand-Archive -Path $zip -DestinationPath $Dest -Force
    }
}

# 1. Java 17 (Microsoft Build of OpenJDK)
$JdkDir = Join-Path $Cache 'jdk'
Get-Zip 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' 'jdk17.zip' $JdkDir
$env:JAVA_HOME = (Get-ChildItem $JdkDir -Directory | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Android command-line tools -> android-sdk\cmdline-tools\latest
$Latest = Join-Path $Sdk 'cmdline-tools\latest'
if (-not (Test-Path "$Latest\bin\sdkmanager.bat")) {
    $tmp = Join-Path $Cache 'cmdline-tmp'
    Get-Zip 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' 'cmdline-tools.zip' $tmp
    New-Item -ItemType Directory -Force -Path (Split-Path $Latest) | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') $Latest
    Remove-Item $tmp -Recurse -Force
}
$env:ANDROID_HOME = $Sdk

# 3. SDK-pakketten (licenties automatisch accepteren)
$sdkm = "$Latest\bin\sdkmanager.bat"
if (-not (Test-Path "$Sdk\platforms\android-35")) {
    Write-Host 'Android SDK-pakketten installeren...' -ForegroundColor Cyan
    $yes = ('y' + [Environment]::NewLine) * 30
    $yes | & $sdkm --sdk_root=$Sdk --licenses | Out-Null
    & $sdkm --sdk_root=$Sdk 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' | Out-Null
}
$Adb = "$Sdk\platform-tools\adb.exe"

# 4. Gradle
$GradleDir = Join-Path $Cache 'gradle'
Get-Zip 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' 'gradle.zip' $GradleDir
$Gradle = Join-Path (Get-ChildItem $GradleDir -Directory | Select-Object -First 1).FullName 'bin\gradle.bat'

# 5. local.properties: sdk.dir toevoegen (API-sleutels blijven staan)
$Launcher = Join-Path $Root 'launcher'
$lp = Join-Path $Launcher 'local.properties'
$sdkLine = 'sdk.dir=' + ($Sdk -replace '\\', '\\' -replace ':', '\:')
$lines = if (Test-Path $lp) { Get-Content $lp | Where-Object { $_ -notmatch '^sdk\.dir=' } } else { @() }
@($sdkLine) + $lines | Set-Content $lp -Encoding ASCII

# 6. Bouwen
Write-Host "Gradle: $Task..." -ForegroundColor Cyan
Push-Location $Launcher
try {
    & $Gradle --no-daemon --console=plain $Task
    if ($LASTEXITCODE -ne 0) { throw "Bouwen mislukt (exitcode $LASTEXITCODE)" }
} finally { Pop-Location }

if ($Task -ne 'assembleRelease') { Write-Host "`nKlaar: $Task" -ForegroundColor Green; return }
$Apk = Join-Path $Launcher 'app\build\outputs\apk\release\app-release.apk'
Write-Host "`nKlaar: $Apk" -ForegroundColor Green
Write-Host "ADB:   $Adb" -ForegroundColor Green
