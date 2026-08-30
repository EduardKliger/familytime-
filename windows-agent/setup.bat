@echo off
echo ========================================
echo   FamilyTime Windows Agent Setup
echo ========================================
echo.

REM ── 1. Find this script's directory ──────────────────────────────────────────
set AGENT_DIR=%~dp0
set BLOCKER=%AGENT_DIR%blocker.py
set PYTHON="C:\Users\%USERNAME%\.local\bin\python3.11.exe"

if not exist %PYTHON% (
    echo ERROR: Python not found at %PYTHON%
    echo Edit PYTHON path in this file to match your Python location.
    pause & exit /b 1
)

REM ── 2. Create a hidden VBScript launcher (runs blocker.py with no console) ──
set VBS=%AGENT_DIR%start_blocker.vbs
echo Set WShell = CreateObject("WScript.Shell") > "%VBS%"
echo WShell.Run "cmd /c %PYTHON% ""%BLOCKER%"" > ""%AGENT_DIR%blocker.log"" 2>&1", 0, False >> "%VBS%"

REM ── 3. Add to Windows startup folder ─────────────────────────────────────────
set STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set SHORTCUT=%STARTUP%\FamilyTimeblocker.vbs
copy "%VBS%" "%SHORTCUT%" >nul

echo.
echo Setup complete!
echo The blocker will start automatically at Windows login.
echo.
echo Starting the blocker now...
cscript //nologo "%VBS%"
echo.
echo Done. The blocker is running in the background.
pause
