@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Name %~n0 -Cmd ".\scripts\diagnose.ps1"
echo.
findstr /c:"=== FOUT" "logs\%~n0.log" >nul && (echo Er ging iets mis, zie hierboven. & pause) || (echo Klaar, dit venster sluit vanzelf. & timeout /t 8 >nul)
