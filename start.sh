#!/bin/sh
# PureTV for macOS and Linux: sets up its own Python environment on first run, then opens the app.
# Made with love by Crazified Studios - crazifiedstudios.com
cd "$(dirname "$0")" || exit 1
command -v python3 >/dev/null 2>&1 || { echo "Python 3 is needed: https://www.python.org/downloads/"; exit 1; }
if [ ! -x .venv/bin/python ]; then
  echo "First run: setting up PureTV..."
  python3 -m venv .venv && .venv/bin/python -m pip install -q -r requirements.txt || exit 1
fi
exec .venv/bin/python app/server.py
