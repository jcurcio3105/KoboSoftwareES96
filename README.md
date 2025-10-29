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
Install dependencies:
pip install requests

## Files
- uploadInstances.py — main upload and data formatting script
- data.csv — example click data
- Kobo Testing_YYYY-MM-DD_HH-MM-SS.xml — sample Kobo form submission

## Usage
1. Install Python 3.1 or later.
2. Clone or download this repository:
git clone https://github.com/jcurcio3105/KoboSoftwareES96.git
cd KoboSoftwareES96/csv_to_kobo
3. Prepare or edit `data.csv`.
4. Run the script:
python uploadInstances.py

## Configuration
Edit `uploadInstances.py` to set:
- KoboToolbox API endpoint
- Form ID or upload URL
- Authentication credentials (if required)

## Notes
This version includes only the Python data upload component. Android and microcontroller int