# KoboSoftwareES96
# KoboClicker

KoboClicker is a Python tool that simulates button presses and uploads the resulting data to KoboToolbox, a platform used for humanitarian and field data collection. It represents the data handling component of a planned clicker device system. The script converts CSV data into Kobo-compatible XML submissions and uploads them for testing and integration.

## Features
- Simulates button click events  
- Generates Kobo-compatible XML form instances  
- Uploads data to KoboToolbox  
- Serves as the data bridge for future hardware integration  

## Requirements
- Python 3.1+  
- requests library  

Install dependencies manually (optional if you use `install.sh`):
```bash
pip install requests
```

## Setup (Recommended)

Clone the repo, make the installer executable, and run it:
```bash
chmod +x install.sh
./install.sh
```

This creates a local virtual environment `.venv/` and installs required packages (`requests` and `backports.zoneinfo` if needed).

## Usage

Activate the environment:
```bash
source .venv/bin/activate
```

Move into the working directory:
```bash
cd csv_to_kobo
```

Prepare or edit `data.csv` (use the exact column names: `time`, `red button`, `green button`).

Run the uploader:
```bash
python uploadInstances.py
```

## Configuration

Edit the top of `csv_to_kobo/uploadInstances.py`:
```python
# -----------------------
# CONFIGURE THESE
# -----------------------
CSV_PATH = "data.csv"                           # your CSV path
OUTPUT_ROOT = "instances"                       # output folder for XMLs
KOBOKIT_BASE = "https://kc.kobotoolbox.org"    # or https://kc-eu.kobotoolbox.org
USERNAME = "your_username_here"
PASSWORD = "your_password_here"
SUBMIT_URL = f"{KOBOKIT_BASE}/submission"
```

Optional: use environment variables instead of hardcoding:
```bash
export KOBO_BASE_URL="https://kc.kobotoolbox.org"
export KOBO_USERNAME="your_username"
export KOBO_PASSWORD="your_password"
```

## Project Files
- `install.sh` — creates `.venv/` and installs dependencies  
- `csv_to_kobo/uploadInstances.py` — builds and uploads XML instances to KoboToolbox  
- `csv_to_kobo/data.csv` — sample CSV input  
- `csv_to_kobo/instances/` — generated XML submissions (ignored by git)  

## Common Commands
```bash
# recreate/refresh environment
./install.sh

# activate / deactivate
source .venv/bin/activate
deactivate

# quick dependency test
python -c "import requests; print('requests OK')"
```

## .gitignore (recommended)
At the repository root:
```
.venv/
__pycache__/
*.pyc
csv_to_kobo/instances/
```

## Notes
This version includes only the Python data upload component.  
Future versions will integrate the Android app and microcontroller firmware for full device functionality.
