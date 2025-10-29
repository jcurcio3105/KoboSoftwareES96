import os
import csv
import uuid
import io
import json
from datetime import datetime
from zoneinfo import ZoneInfo
import xml.etree.ElementTree as ET
import requests
import time
from urllib.parse import urlparse

# -----------------------
# GUI (Tkinter)
# -----------------------
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

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

# CSV columns (ALWAYS 6, per requirements)
CSV_COL_TIME = "time"  # case-insensitive after strip
CSV_BUTTONS = ["button1", "button2", "button3", "button4", "button5"]

# Kobo default/meta fields to exclude from mapping UI
EXCLUDED_NAMES = {
    "start",
    "end",
    "__version__",
    "meta/instanceid",
    "meta",
    "formhub/uuid",
    # server-managed; not typically in the survey spec, but exclude anyway
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
# Helpers
# -----------------------
def normalize_header(h):
    return (h or "").strip().lower()

def parse_csv_time_to_iso8601_local(s):
    """
    Input like '10/22/2025 11:42:32' (MM/DD/YYYY HH:MM:SS, local).
    Output ISO8601 with America/New_York offset, e.g. '2025-10-22T11:42:32-04:00'.
    """
    dt_naive = datetime.strptime(s.strip(), "%m/%d/%Y %H:%M:%S")
    dt_local = dt_naive.replace(tzinfo=ZoneInfo("America/New_York"))
    return dt_local.isoformat(timespec="seconds")

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
    """
    From 'https://kf.kobotoolbox.org/#/forms/aYdonybE5yVNCT4j2jCcVz'
    extract 'aYdonybE5yVNCT4j2jCcVz'
    """
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
    """
    Prefer explicit 'name'. If missing, use '$autoname', else '$xpath' (last segment).
    """
    name = (item.get("name") or "").strip()
    if not name:
        name = (item.get("$autoname") or "").strip()
    if not name:
        xpath = (item.get("$xpath") or "").strip()
        if xpath:
            name = xpath.split("/")[-1].strip()
    return name

def get_field_label(item: dict) -> str:
    """
    Normalize Kobo label which can be str | dict | list.
    """
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
    """
    Fetch form content and extract *custom* fields:
    - Derive field name from name → $autoname → $xpath(last segment)
    - Skip structural types (groups/repeats/notes)
    - Exclude known defaults/meta by *derived* name (case-insensitive)
    Returns: [{'name': 'Red_Button', 'label': 'Red Button', 'type': 'integer'}, ...]
    """
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
        # skip groups/repeats/notes (non answer-bearing)
        if qtype in {"begin_group", "end_group", "begin_repeat", "end_repeat", "note"}:
            continue

        # derive name from name/$autoname/$xpath
        name = get_field_name(q)
        if not name:
            continue  # can't submit without a concrete node name

        name_norm = name.lower()

        # exclude known metadata/defaults by derived name
        if name_norm in EXCLUDED_NAMES:
            continue

        # label normalization
        label_text = get_field_label(q)

        items.append({"name": name, "label": label_text, "type": qtype})

    # hard cap: no more than 5 custom fields
    if len(items) > 5:
        raise RuntimeError(
            f"Form has {len(items)} custom fields (max allowed is 5 for this uploader). "
            "Please remove or ignore extra fields."
        )

    return items

def build_instance_xml_dynamic(root_tag, form_id_attr, start_iso, end_iso,
                               mapped_values: dict, instance_uuid_str: str):
    """
    Build XML using dynamic mapped values for custom fields.
    mapped_values: dict of { field_name_in_form : string_value_from_csv }
    """
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

    # formhub uuid block (kept to mirror accepted structure)
    formhub = ET.SubElement(root, "formhub")
    uuid_el = ET.SubElement(formhub, "uuid")
    uuid_el.text = FORMHUB_UUID

    # Kobo default timestamps (per your original behavior)
    ET.SubElement(root, "start").text = start_iso
    ET.SubElement(root, "end").text = end_iso

    # Dynamic custom fields
    for field_name, val in mapped_values.items():
        # field_name must match the "name" from the form (not label)
        if not field_name:
            continue
        el = ET.SubElement(root, field_name)
        # Write raw text; let Kobo handle type validation
        el.text = "" if val is None else str(val)

    # version token
    ET.SubElement(root, "__version__").text = FORM_VERSION_TOKEN

    # meta/instanceID
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

# -----------------------
# Core uploader
# -----------------------
def run_upload_dynamic(username, password, survey_link, csv_path, output_root, mapping):
    """
    mapping: dict { form_field_name -> csv_button_name } (csv_button_name in CSV_BUTTONS)
    """
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

    # Read CSV
    with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            raise ValueError("CSV has no header row.")

        header_map = {orig: normalize_header(orig) for orig in reader.fieldnames}
        normalized_fieldnames = list(header_map.values())

        # Validate required columns exist
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

    results = []
    for idx, row in enumerate(rows, start=1):
        time_str = row.get(CSV_COL_TIME, "")
        if not time_str:
            msg = f"[SKIP] Row {idx}: missing Time"
            print(msg)
            results.append(msg)
            continue

        try:
            iso_time = parse_csv_time_to_iso8601_local(time_str)
        except Exception as e:
            msg = f"[SKIP] Row {idx}: bad Time '{time_str}': {e}"
            print(msg)
            results.append(msg)
            continue

        # Build values for custom fields from mapping
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

# -----------------------
# Tkinter UI
# -----------------------
def choose_csv(var):
    path = filedialog.askopenfilename(
        title="Select CSV file",
        filetypes=[("CSV files", "*.csv"), ("All files", "*.*")]
    )
    if path:
        var.set(path)

def center_window(win, width=560, height=360):
    win.update_idletasks()
    x = (win.winfo_screenwidth() // 2) - (width // 2)
    y = (win.winfo_screenheight() // 2) - (height // 2)
    win.geometry(f"{width}x{height}+{x}+{y}")

def popup_credentials(on_success):
    # First popup
    root = tk.Tk()
    root.title("Upload to Kobotoolbox")
    root.resizable(False, False)
    center_window(root, 560, 340)

    frame = ttk.Frame(root, padding=20)
    frame.grid(row=0, column=0, sticky="nsew")
    root.grid_rowconfigure(0, weight=1)
    root.grid_columnconfigure(0, weight=1)
    for c in range(2):
        frame.grid_columnconfigure(c, weight=1)

    username_var = tk.StringVar()
    password_var = tk.StringVar()
    link_var = tk.StringVar()
    csv_var = tk.StringVar()
    output_var = tk.StringVar(value="instances")

    ttk.Label(frame, text="Username (required):").grid(row=0, column=0, sticky="e", padx=8, pady=6)
    ttk.Entry(frame, textvariable=username_var, width=36, justify="center").grid(row=0, column=1, sticky="w", padx=8, pady=6)

    ttk.Label(frame, text="Password (required):").grid(row=1, column=0, sticky="e", padx=8, pady=6)
    ttk.Entry(frame, textvariable=password_var, show="*", width=36, justify="center").grid(row=1, column=1, sticky="w", padx=8, pady=6)

    ttk.Label(frame, text="Survey link (required):").grid(row=2, column=0, sticky="e", padx=8, pady=6)
    ttk.Entry(frame, textvariable=link_var, width=36, justify="center").grid(row=2, column=1, sticky="w", padx=8, pady=6)

    ttk.Label(frame, text="CSV file (required):").grid(row=3, column=0, sticky="e", padx=8, pady=6)
    csv_row = ttk.Frame(frame)
    csv_row.grid(row=3, column=1, sticky="w", padx=8, pady=6)
    ttk.Entry(csv_row, textvariable=csv_var, width=27, justify="center").grid(row=0, column=0, padx=(0,6))
    ttk.Button(csv_row, text="Browse…", command=lambda: choose_csv(csv_var)).grid(row=0, column=1)

    ttk.Label(frame, text="Output folder (required):").grid(row=4, column=0, sticky="e", padx=8, pady=6)
    ttk.Entry(frame, textvariable=output_var, width=36, justify="center").grid(row=4, column=1, sticky="w", padx=8, pady=6)

    def submit_first():
        username = username_var.get().strip()
        password = password_var.get().strip()
        link = link_var.get().strip()
        csv_path = csv_var.get().strip()
        output_root = output_var.get().strip()

        if not (username and password and link and csv_path and output_root):
            messagebox.showerror("Missing information", "All fields are required.")
            return
        if not os.path.isfile(csv_path):
            messagebox.showerror("CSV not found", "The specified CSV file does not exist.")
            return

        # Validate CSV columns here to fail early
        try:
            with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
                reader = csv.DictReader(f)
                if not reader.fieldnames:
                    raise ValueError("CSV has no header row.")
                normalized = [normalize_header(h) for h in reader.fieldnames]
                required = [CSV_COL_TIME] + CSV_BUTTONS
                missing = [c for c in required if c not in normalized]
                if missing:
                    raise ValueError(f"CSV missing required columns: {missing}")
        except Exception as e:
            messagebox.showerror("CSV error", str(e))
            return

        try:
            form_uid = parse_form_id_from_link(link)
        except Exception as e:
            messagebox.showerror("Invalid survey link", f"Could not parse Form ID from link.\n\nError: {e}")
            return

        # Fetch custom form fields
        try:
            kf_base = derive_kf_base_from_link(link)
            fields = kf_get_custom_fields(kf_base, form_uid, username, password)
        except Exception as e:
            messagebox.showerror("Form fetch error", str(e))
            return

        # Enforce max 5 custom fields (already in fetch, but double-check)
        if len(fields) > 5:
            messagebox.showerror("Too many fields", f"Form exposes {len(fields)} custom fields (max 5).")
            return

        # Success → close this popup and open mapper
        root.destroy()
        on_success(username, password, link, csv_path, output_root, fields)

    btns = ttk.Frame(frame)
    btns.grid(row=5, column=0, columnspan=2, pady=(14, 0))
    btns.grid_columnconfigure(0, weight=1)
    btns.grid_columnconfigure(1, weight=1)

    ttk.Button(btns, text="Submit", command=submit_first).grid(row=0, column=0, padx=10)
    ttk.Button(btns, text="Quit", command=root.destroy).grid(row=0, column=1, padx=10)

    root.mainloop()

def popup_mapping(username, password, link, csv_path, output_root, custom_fields):
    """
    Second popup: show each custom field with a dropdown to assign button1..button5
    """
    win = tk.Tk()
    win.title("Map CSV columns to form fields")
    win.resizable(False, False)

    # Size depends on number of fields
    height = 220 + 48 * max(1, len(custom_fields))
    center_window(win, 680, height)

    frame = ttk.Frame(win, padding=18)
    frame.grid(row=0, column=0, sticky="nsew")
    win.grid_rowconfigure(0, weight=1)
    win.grid_columnconfigure(0, weight=1)

    # Header
    ttk.Label(
        frame,
        text="Assign CSV columns to your custom form fields",
        font=("TkDefaultFont", 10, "bold")
    ).grid(row=0, column=0, columnspan=3, pady=(0, 10))

    ttk.Label(frame, text="Form Field").grid(row=1, column=0, sticky="w")
    ttk.Label(frame, text="(name)").grid(row=1, column=1, sticky="w")
    ttk.Label(frame, text="CSV Column").grid(row=1, column=2, sticky="w")

    # Dropdown choices (buttons only)
    button_choices = ["(Unused)"] + CSV_BUTTONS

    # Build one row per custom field
    var_by_field = {}
    start_row = 2
    for idx, fld in enumerate(custom_fields):
        row = start_row + idx
        label = fld.get("label") or fld["name"]
        name = fld["name"]

        ttk.Label(frame, text=label).grid(row=row, column=0, sticky="w", padx=(0,10), pady=4)
        ttk.Label(frame, text=name, foreground="#555").grid(row=row, column=1, sticky="w", padx=(0,10), pady=4)

        v = tk.StringVar(value="(Unused)")
        combo = ttk.Combobox(frame, textvariable=v, values=button_choices, state="readonly", width=16, justify="center")
        combo.grid(row=row, column=2, sticky="w", pady=4)
        var_by_field[name] = v

    next_row = start_row + len(custom_fields)

    # If there are no custom fields, let the user know they can still upload (timestamps only)
    if len(custom_fields) == 0:
        ttk.Label(
            frame,
            text="No custom fields detected in this form. You can still upload; only timestamps/meta will be submitted.",
            foreground="#555"
        ).grid(row=next_row, column=0, columnspan=3, sticky="w", pady=(8, 0))
        note_row = next_row + 1
    else:
        # Footnote about Time
        ttk.Label(
            frame,
            text="Note: 'Time' column is fixed and used for start/end timestamps.",
            foreground="#555"
        ).grid(row=next_row, column=0, columnspan=3, sticky="w", pady=(8, 0))
        note_row = next_row + 1

    # Output console
    output_text = tk.Text(frame, height=6, width=78, wrap="word")
    output_text.grid(row=note_row, column=0, columnspan=3, pady=(12, 6))

    def do_upload():
        # Build mapping and enforce uniqueness (no duplicated button selection)
        chosen = []
        mapping = {}
        for field_name, var in var_by_field.items():
            val = var.get()
            if val and val != "(Unused)":
                mapping[field_name] = val
                chosen.append(val)

        if len(set(chosen)) != len(chosen):
            messagebox.showerror("Invalid mapping", "Each CSV button can be assigned to at most one form field.")
            return

        try:
            output_text.delete("1.0", "end")
            output_text.insert("end", "Starting upload…\n\n")
            win.update_idletasks()

            results = run_upload_dynamic(username, password, link, csv_path, output_root, mapping)
            output_text.insert("end", results + "\n")
            messagebox.showinfo("Done", "Upload process completed. See details below.")
        except Exception as e:
            messagebox.showerror("Upload error", str(e))

    # Buttons
    btns = ttk.Frame(frame)
    btns.grid(row=note_row + 1, column=0, columnspan=3, pady=(6, 0))
    btns.grid_columnconfigure(0, weight=1)
    btns.grid_columnconfigure(1, weight=1)

    ttk.Button(btns, text="Upload", command=do_upload).grid(row=0, column=0, padx=10)
    ttk.Button(btns, text="Close", command=win.destroy).grid(row=0, column=1, padx=10)

    win.mainloop()

# -----------------------
# Entry point
# -----------------------
if __name__ == "__main__":
    popup_credentials(on_success=popup_mapping)
