@echo off
REM Schnelltest fuer vitalstoff_summary.py mit dem Beispielbericht "stefan.htm".
REM Erzeugt zusammenfassung_Stefan.html (voll) und zusammenfassung_Stefan_fazit.html (Fazit)
REM und oeffnet danach beide Berichte im Standardbrowser.

cd /d "%~dp0"

"%LOCALAPPDATA%\Python\bin\python.exe" vitalstoff_summary.py stefan.htm
if errorlevel 1 (
    echo.
    echo Fehler beim Ausfuehren des Skripts - siehe Meldung oben.
    pause
    exit /b 1
)

echo.
echo Oeffne beide Berichte im Browser...
start "" "zusammenfassung_Stefan.html"
start "" "zusammenfassung_Stefan_fazit.html"

pause
