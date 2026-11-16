@echo off
rem PureTV: sets up its own Python environment on first run, then opens the app in your browser.
cd /d "%~dp0"
where python >nul 2>nul || (echo Python 3 is needed. Install it from python.org and run this again. & pause & exit /b 1)
if not exist ".venv\Scripts\python.exe" (
  echo First run: setting up PureTV...
  python -m venv .venv || (pause & exit /b 1)
  ".venv\Scripts\python.exe" -m pip install -q -r requirements.txt || (pause & exit /b 1)
)
".venv\Scripts\python.exe" app\server.py
pause
