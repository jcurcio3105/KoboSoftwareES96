#!/usr/bin/env bash
# install.sh — set up a clean Python env and install dependencies for KoboClicker
set -euo pipefail

# Allow override: PYTHON=/path/to/python ./install.sh
PYTHON="${PYTHON:-python3}"

if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "ERROR: $PYTHON not found. Set PYTHON to your Python 3 path and re-run." >&2
  exit 1
fi

# Create a local virtual environment
"$PYTHON" -m venv .venv
# shellcheck disable=SC1091
source .venv/bin/activate

python -m pip install --upgrade pip

# Base dependencies
pkgs=(requests)

# Python < 3.9 needs zoneinfo backport
pyver=$(python - <<'PY'
import sys
print(f"{sys.version_info.major}.{sys.version_info.minor}")
PY
)
case "$pyver" in
  3.[0-8]) pkgs+=(backports.zoneinfo) ;;
esac

python -m pip install "${pkgs[@]}"

echo
echo "Installed packages: ${pkgs[*]}"
echo "Virtual env created at .venv"
echo "Activate later with: source .venv/bin/activate"