@echo off
setlocal
cd /d "%~dp0"

set "PYTHON_CMD="
where py >nul 2>&1
if not errorlevel 1 set "PYTHON_CMD=py"
if defined PYTHON_CMD (
    py --version >nul 2>&1
    if errorlevel 1 set "PYTHON_CMD="
)
if not defined PYTHON_CMD (
    where python >nul 2>&1
    if not errorlevel 1 (
        python --version >nul 2>&1
        if not errorlevel 1 set "PYTHON_CMD=python"
    )
)

if not defined PYTHON_CMD (
    echo Python was not found.
    echo Install Python 3.11 or newer, then open this file again.
    echo During installation, select "Add Python to PATH".
    pause
    exit /b 1
)

echo Installing or checking the API packages...
%PYTHON_CMD% -m pip install -r requirements.txt
if errorlevel 1 (
    echo Could not install the API packages. Check your internet connection and try again.
    pause
    exit /b 1
)

echo.
echo Starting Pencils and Stencils API at http://localhost:8000
echo Keep this window open while using the Android emulator.
echo Press Ctrl+C to stop the API.
echo.
%PYTHON_CMD% -m uvicorn api:app --host 0.0.0.0 --port 8000
pause
