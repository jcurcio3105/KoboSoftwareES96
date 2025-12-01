import os
import csv
import uuid
import io
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import xml.etree.ElementTree as ET
import requests
import time

# -----------------------
# CONFIGURE THESE
# -----------------------
CSV_PATH = "data.csv"                           # your CSV path
OUTPUT_ROOT = "instances"                       # folder layout root
KOBOKIT_BASE = "https://kc.kobotoolbox.org"    # or https://kc-eu.kobotoolbox.org
USERNAME = "nmarinaccio"                       # <-- fill in
PASSWORD = "Kobo1923???"                       # <-- fill in
SUBMIT_URL = f"{KOBOKIT_BASE}/submission"

# Form constants (from your accepted XML)
ROOT_TAG = "aYdonybE5yVNCT4j2jCcVz"
FORM_ID_ATTR = "aYdonybE5yVNCT4j2jCcVz"
FORM_VERSION_ATTR = "1 (2025-10-15 23:34:36)"
FORMHUB_UUID = "18b92e4796ce4d41a60feaa54e06520e"
FORM_VERSION_TOKEN = "v8ctrw8YSEH4JbRWcSFLSK"  # __version__

# Namespaces (match your accepted example)
NSMAP = {
    "h": "http://www.w3.org/1999/xhtml",
    "xsd": "http://www.w3.org/2001/XMLSchema",
    "jr": "http://openrosa.org/javarosa",
    "ev": "http://www.w3.org/2001/xml-events",
    "orx": "http://openrosa.org/xforms",
    "odk": "http://www.opendatakit.org/xforms",
}

# CSV column names (your sample had spaces; we normalize and handle variants)
CSV_COL_TIME = "time"          # now contains millisecond values
CSV_COL_RED = "red button"
CSV_COL_GREEN = "green button"

# -----------------------
# NEW: Start datetime config
# -----------------------
# Local date/time for the first logical timestamp
START_DATETIME_STR = "10/22/2025 11:42:32"   # change as needed
START_TIMEZONE = "America/New_York"          # change if needed


# -----------------------
# Helpers
# -----------------------

def normalize_header(h):
    """normalize CSV header: strip spaces, lower-case"""
    return h.strip().lower()


def build_instance_xml(start_iso, end_iso, red_val, green_val, instance_uuid_str):
    """
    Build an XML ElementTree matching your accepted format.
    """
    # Register namespaces so ElementTree adds xmlns attributes on the root
    for prefix, uri in NSMAP.items():
        ET.register_namespace(prefix, uri)

    root = ET.Element(
        ROOT_TAG,
        {
            "id": FORM_ID_ATTR,
            "version": FORM_VERSION_ATTR,
            # xmlns declarations are handled by register_namespace + this:
            f"xmlns:h": NSMAP["h"],
            f"xmlns:xsd": NSMAP["xsd"],
            f"xmlns:jr": NSMAP["jr"],
            f"xmlns:ev": NSMAP["ev"],
            f"xmlns:orx": NSMAP["orx"],
            f"xmlns:odk": NSMAP["odk"],
        },
    )

    formhub = ET.SubElement(root, "formhub")
    uuid_el = ET.SubElement(formhub, "uuid")
    uuid_el.text = FORMHUB_UUID

    start_el = ET.SubElement(root, "start")
    start_el.text = start_iso

    end_el = ET.SubElement(root, "end")
    end_el.text = end_iso

    red_el = ET.SubElement(root, "Red_Button")
    red_el.text = str(int(red_val))  # ensure integer text

    green_el = ET.SubElement(root, "Green_Button")
    green_el.text = str(int(green_val))

    ver_el = ET.SubElement(root, "__version__")
    ver_el.text = FORM_VERSION_TOKEN

    meta = ET.SubElement(root, "meta")
    inst = ET.SubElement(meta, "instanceID")
    inst.text = f"uuid:{instance_uuid_str}"

    # Serialize to bytes with XML declaration
    xml_bytes = ET.tostring(root, encoding="UTF-8", xml_declaration=True, method="xml")
    return xml_bytes


def submit_xml(session, xml_bytes, display_name="submission.xml"):
    files = {
        "xml_submission_file": (display_name, io.BytesIO(xml_bytes), "text/xml"),
    }
    r = session.post(SUBMIT_URL, files=files, timeout=60)
    return r


# -----------------------
# Main flow
# -----------------------

def main():
    os.makedirs(OUTPUT_ROOT, exist_ok=True)

    # Build a session for submissions
    session = requests.Session()
    session.auth = (USERNAME, PASSWORD)
    session.headers.update({
        "User-Agent": "kobo-bulk-uploader/2.0",
        "X-OpenRosa-Version": "1.0",
    })

    # Parse the start datetime once
    start_dt = datetime.strptime(START_DATETIME_STR, "%m/%d/%Y %H:%M:%S").replace(
        tzinfo=ZoneInfo(START_TIMEZONE)
    )

    # Read CSV
    with open(CSV_PATH, "r", newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        # Normalize fieldnames
        normalized_fieldnames = [normalize_header(h) for h in reader.fieldnames]
        # Map original header -> normalized
        header_map = {orig: normalize_header(orig) for orig in reader.fieldnames}

        # Build rows with normalized keys
        rows = []
        for raw in reader:
            norm = {}
            for k, v in raw.items():
                norm[header_map[k]] = (v or "").strip()
            rows.append(norm)

    # Validate required columns
    needed = {CSV_COL_TIME, CSV_COL_RED, CSV_COL_GREEN}
    if not needed.issubset(set(normalized_fieldnames)):
        raise ValueError(
            f"CSV is missing required columns. Needed: {sorted(needed)}. "
            f"Found: {sorted(normalized_fieldnames)}"
        )

    # Find the base millisecond timestamp (first valid row)
    base_ms = None
    for row in rows:
        time_str = row[CSV_COL_TIME]
        if not time_str:
            continue
        try:
            ms_val = int(time_str)
        except ValueError:
            continue
        base_ms = ms_val
        break

    if base_ms is None:
        raise ValueError("No valid millisecond timestamps found in CSV 'time' column.")

    # Iterate rows, create XMLs, write to folder structure, and submit
    for idx, row in enumerate(rows, start=1):
        time_str = row[CSV_COL_TIME]
        red_val = row[CSV_COL_RED]
        green_val = row[CSV_COL_GREEN]

        if not time_str:
            print(f"[SKIP] Row {idx}: missing Time (ms)")
            continue

        try:
            ms_val = int(time_str)
        except ValueError as e:
            print(f"[SKIP] Row {idx}: bad Time '{time_str}': {e}")
            continue

        elapsed_ms = ms_val - base_ms
        if elapsed_ms < 0:
            print(f"[WARN] Row {idx}: timestamp {ms_val} < base {base_ms}, clamping to 0.")
            elapsed_ms = 0

        # Calculate the actual datetime from start_dt + elapsed_ms
        dt_with_offset = start_dt + timedelta(milliseconds=elapsed_ms)
        iso_time = dt_with_offset.isoformat(timespec="seconds")

        # unique instanceID
        inst_uuid = str(uuid.uuid4())

        # same start and end
        xml_bytes = build_instance_xml(
            start_iso=iso_time,
            end_iso=iso_time,
            red_val=red_val,
            green_val=green_val,
            instance_uuid_str=inst_uuid,
        )

        # Write file to instances/instanceN/instanceN.xml
        folder = os.path.join(OUTPUT_ROOT, f"instance{idx}")
        os.makedirs(folder, exist_ok=True)
        file_path = os.path.join(folder, f"instance{idx}.xml")
        with open(file_path, "wb") as xf:
            xf.write(xml_bytes)

        # Submit
        try:
            r = submit_xml(session, xml_bytes, display_name=os.path.basename(file_path))
            print(f"{file_path}: {r.status_code}\n{r.text.strip()}\n{'-'*60}")
        except Exception as e:
            print(f"{file_path}: ERROR submitting: {e}\n{'-'*60}")

        # Be polite to the server
        time.sleep(0.2)


if __name__ == "__main__":
    main()
