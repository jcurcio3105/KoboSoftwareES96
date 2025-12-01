# backend/uploader.py
import os
import csv
import uuid
import io
import json
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import xml.etree.ElementTree as ET
import requests
import time
from urllib.parse import urlparse

# -----------------------
# CONSTANTS
# -----------------------
FORM_VERSION_ATTR = "1 (2025-10-15 23:34:36)"
FORMHUB_UUID = "18b92e4796ce4d41a60feaa54e06520e"
FORM_VERSION_TOKEN = "v8ctrw8YSEH4JbRWcSFLSK"  # __version__

NSMAP = {
    "h": "http://www.w3.org/1999/xhtml",
    "xsd": "http://www.w3.org/2001/XMLSchema",
    "jr": "http://openrosa.org/javarosa",
    "ev": "http://www.w3.org/2001/xml-events",
    "orx": "http://openrosa.org/xforms",
    "odk": "http://www.opendatakit.org/xforms",
}

CSV_COL_TIME = "time"
CSV_BUTTONS = ["button1", "button2", "button3", "button4", "button5"]

EXCLUDED_NAMES = {
    "start",
    "end",
    "__version__",
    "meta/instanceid",
    "meta",
    "formhub/uuid",
    "_submission_time",
    "_submitted_by",
    "_id",
    "_uuid",
    "validation",
    "version",
    "id",
    "uuid",
}

# -----------------------
# NEW: Start datetime config
# -----------------------
# Local date/time corresponding to the first logical timestamp
# Adjust these to whatever you want to anchor the milliseconds to.
START_DATETIME_STR = "10/22/2025 11:42:32"   # MM/DD/YYYY HH:MM:SS
START_TIMEZONE = "America/New_York"

# -----------------------
# Helpers
# -----------------------
def normalize_header(h):
    return (h or "").strip().lower()

# NOTE: old absolute-timestamp parser removed; we now use milliseconds
# def parse_csv_time_to_iso8601_local(s):
#     dt_naive = datetime.strptime(s.strip(), "%m/%d/%Y %H:%M:%S")
#     dt_local = dt_naive.replace(tzinfo=ZoneInfo("America/New_York"))
#     return dt_local.isoformat(timespec="seconds")

def derive_kc_base_from_link(link: str) -> str:
    host = ""
    try:
        host = urlparse(link).netloc.lower()
    except Exception:
        pass
    if "kf-eu." in host:
        return "https://kc-eu.kobotoolbox.org"
    return "https://kc.kobotoolbox.org"

def derive_kf_base_from_link(link: str) -> str:
    host = ""
    try:
        host = urlparse(link).netloc.lower()
    except Exception:
        pass
    if "kf-eu." in host:
        return "https://kf-eu.kobotoolbox.org"
    return "https://kf.kobotoolbox.org"

def parse_form_id_from_link(link: str) -> str:
    if not link:
        raise ValueError("Empty survey link.")
    needle = "/forms/"
    idx = link.find(needle)
    if idx == -1:
        raise ValueError("Could not find '/forms/' in link.")
    tail = link[idx + len(needle):]
    for sep in ["/", "?", "#"]:
        cut = tail.find(sep)
        if cut != -1:
            tail = tail[:cut]
    if not tail:
        raise ValueError("Form ID segment was empty.")
    return tail

def get_field_name(item: dict) -> str:
    name = (item.get("name") or "").strip()
    if not name:
        name = (item.get("$autoname") or "").strip()
    if not name:
        xpath = (item.get("$xpath") or "").strip()
        if xpath:
            name = xpath.split("/")[-1].strip()
    return name

def get_field_label(item: dict) -> str:
    label = item.get("label", "")
    if isinstance(label, list) and label:
        first = label[0]
        if isinstance(first, dict):
            return (first.get("text") or "").strip()
        return str(first).strip()
    if isinstance(label, dict):
        return str(next(iter(label.values()), "")).strip()
    return str(label).strip()

def kf_get_custom_fields(kf_base: str, form_uid: str, username: str, password: str):
    url = f"{kf_base}/api/v2/assets/{form_uid}/?format=json"
    r = requests.get(url, auth=(username, password), timeout=60)
    if r.status_code != 200:
        raise RuntimeError(f"Failed to fetch form definition ({r.status_code}): {r.text}")

    data = r.json()
    survey = None
    if isinstance(data, dict):
        survey = (data.get("content") or {}).get("survey")
        if survey is None:
            survey = data.get("survey")

    if not isinstance(survey, list):
        raise RuntimeError("Form definition did not include a 'survey' array.")

    items = []
    for q in survey:
        if not isinstance(q, dict):
            continue

        qtype = (q.get("type") or "").strip().lower()
        if qtype in {"begin_group", "end_group", "begin_repeat", "end_repeat", "note"}:
            continue

        name = get_field_name(q)
        if not name:
            continue

        name_norm = name.lower()
        if name_norm in EXCLUDED_NAMES:
            continue

        label_text = get_field_label(q)
        items.append({"name": name, "label": label_text, "type": qtype})

    if len(items) > 5:
        raise RuntimeError(
            f"Form has {len(items)} custom fields (max allowed is 5)."
        )

    return items

def build_instance_xml_dynamic(root_tag, form_id_attr, start_iso, end_iso,
                               mapped_values: dict, instance_uuid_str: str):
    for prefix, uri in NSMAP.items():
        ET.register_namespace(prefix, uri)

    root = ET.Element(
        root_tag,
        {
            "id": form_id_attr,
            "version": FORM_VERSION_ATTR,
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

    ET.SubElement(root, "start").text = start_iso
    ET.SubElement(root, "end").text = end_iso

    for field_name, val in mapped_values.items():
        if not field_name:
            continue
        el = ET.SubElement(root, field_name)
        el.text = "" if val is None else str(val)

    ET.SubElement(root, "__version__").text = FORM_VERSION_TOKEN

    meta = ET.SubElement(root, "meta")
    inst = ET.SubElement(meta, "instanceID")
    inst.text = f"uuid:{instance_uuid_str}"

    xml_bytes = ET.tostring(root, encoding="UTF-8", xml_declaration=True, method="xml")
    return xml_bytes

def submit_xml(session, submit_url, xml_bytes, display_name="submission.xml"):
    files = {
        "xml_submission_file": (display_name, io.BytesIO(xml_bytes), "text/xml"),
    }
    r = session.post(submit_url, files=files, timeout=60)
    return r

def run_upload_dynamic(username, password, survey_link, csv_path, output_root, mapping):
    form_id = parse_form_id_from_link(survey_link)
    kc_base = derive_kc_base_from_link(survey_link)
    submit_url = f"{kc_base}/submission"

    ROOT_TAG = form_id
    FORM_ID_ATTR = form_id

    os.makedirs(output_root, exist_ok=True)

    session = requests.Session()
    session.auth = (username, password)
    session.headers.update({
        "User-Agent": "kobo-bulk-uploader/2.0",
        "X-OpenRosa-Version": "1.0",
    })

    # Parse the anchor start datetime once
    start_dt = datetime.strptime(START_DATETIME_STR, "%m/%d/%Y %H:%M:%S").replace(
        tzinfo=ZoneInfo(START_TIMEZONE)
    )

    with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            raise ValueError("CSV has no header row.")

        header_map = {orig: normalize_header(orig) for orig in reader.fieldnames}
        normalized_fieldnames = list(header_map.values())

        required = [CSV_COL_TIME] + CSV_BUTTONS
        missing = [c for c in required if c not in normalized_fieldnames]
        if missing:
            raise ValueError(f"CSV missing required columns: {missing}")

        rows = []
        for raw in reader:
            norm = {}
            for k, v in raw.items():
                norm[header_map[k]] = (v or "").strip()
            rows.append(norm)

    # Determine base_ms from the first row with a valid millisecond timestamp
    base_ms = None
    for row in rows:
        time_str = row.get(CSV_COL_TIME, "")
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

    results = []
    for idx, row in enumerate(rows, start=1):
        time_str = row.get(CSV_COL_TIME, "")
        if not time_str:
            msg = f"[SKIP] Row {idx}: missing Time (ms)"
            print(msg)
            results.append(msg)
            continue

        try:
            ms_val = int(time_str)
        except ValueError as e:
            msg = f"[SKIP] Row {idx}: bad Time '{time_str}': {e}"
            print(msg)
            results.append(msg)
            continue

        elapsed_ms = ms_val - base_ms
        if elapsed_ms < 0:
            # If timestamps go backwards, clamp to 0 but warn
            msg_warn = f"[WARN] Row {idx}: timestamp {ms_val} < base {base_ms}, clamping elapsed_ms to 0."
            print(msg_warn)
            results.append(msg_warn)
            elapsed_ms = 0

        dt_with_offset = start_dt + timedelta(milliseconds=elapsed_ms)
        iso_time = dt_with_offset.isoformat(timespec="seconds")

        mapped_values = {}
        for form_field, csv_button in mapping.items():
            if not csv_button:
                continue
            mapped_values[form_field] = row.get(csv_button, "")

        inst_uuid = str(uuid.uuid4())
        xml_bytes = build_instance_xml_dynamic(
            root_tag=ROOT_TAG,
            form_id_attr=FORM_ID_ATTR,
            start_iso=iso_time,
            end_iso=iso_time,
            mapped_values=mapped_values,
            instance_uuid_str=inst_uuid,
        )

        folder = os.path.join(output_root, f"instance{idx}")
        os.makedirs(folder, exist_ok=True)
        file_path = os.path.join(folder, f"instance{idx}.xml")
        with open(file_path, "wb") as xf:
            xf.write(xml_bytes)

        try:
            r = submit_xml(session, submit_url, xml_bytes, display_name=os.path.basename(file_path))
            msg = f"{file_path}: {r.status_code}\n{r.text.strip()}\n{'-'*60}"
            print(msg)
            results.append(msg)
        except Exception as e:
            msg = f"{file_path}: ERROR submitting: {e}\n{'-'*60}"
            print(msg)
            results.append(msg)

        time.sleep(0.2)

    return "\n".join(results)
