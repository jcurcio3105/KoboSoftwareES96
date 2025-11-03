# backend/app.py
from flask import Flask, request, jsonify
from uploader import (
    run_upload_dynamic,
    kf_get_custom_fields,
    parse_form_id_from_link,
    derive_kf_base_from_link
)
import os

app = Flask(__name__)

@app.route("/get_fields", methods=["POST"])
def get_fields():
    """
    Flutter -> POST /get_fields
    JSON body:
      {
        "username": "...",
        "password": "...",
        "survey_link": "..."
      }
    Response: { "fields": [ { "name": ..., "label": ... }, ... ] }
    """
    data = request.get_json()
    username = data.get("username")
    password = data.get("password")
    survey_link = data.get("survey_link")

    if not username or not password or not survey_link:
        return jsonify({"error": "Missing required parameters"}), 400

    try:
        # Extract Kobo form ID and API base URL
        form_uid = parse_form_id_from_link(survey_link)
        kf_base = derive_kf_base_from_link(survey_link)
        fields = kf_get_custom_fields(kf_base, form_uid, username, password)

        return jsonify({"fields": fields})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/upload", methods=["POST"])
def upload():
    """
    Flutter -> POST /upload
    JSON body:
      {
        "username": "...",
        "password": "...",
        "survey_link": "...",
        "csv_path": "...",
        "output_root": "...",
        "mapping": { "form_field_name": "button1", ... }
      }
    Response: { "result": "text log" }
    """
    data = request.get_json()
    username = data.get("username")
    password = data.get("password")
    survey_link = data.get("survey_link")
    csv_path = data.get("csv_path")
    output_root = data.get("output_root", "instances")
    mapping = data.get("mapping", {})

    if not all([username, password, survey_link, csv_path]):
        return jsonify({"error": "Missing required parameters"}), 400

    if not os.path.isfile(csv_path):
        return jsonify({"error": f"CSV file not found: {csv_path}"}), 400

    try:
        result_text = run_upload_dynamic(
            username,
            password,
            survey_link,
            csv_path,
            output_root,
            mapping
        )
        return jsonify({"result": result_text})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
