# -*- coding: utf-8 -*-
"""JSON ⇄ Excel 转换服务（Python 版）。

接口与 Java 版完全一致，可直接复用 frontend/dist 前端页面：
- POST /api/convert/preview       JSON → 表格预览
- POST /api/convert/export        JSON → xlsx 下载
- POST /api/convert/excel-to-json Excel → JSON
"""
import io
import os
import tempfile
from urllib.parse import quote

from flask import Flask, jsonify, request, send_file, send_from_directory

import excel_export
import excel_parse
import json_table

PREVIEW_MAX_ROWS = int(os.environ.get("PREVIEW_MAX_ROWS", "200"))
EXCEL_MAX_ROWS = int(os.environ.get("EXCEL_MAX_ROWS", "20000"))
MAX_UPLOAD_MB = int(os.environ.get("MAX_UPLOAD_MB", "20"))
FRONTEND_DIST = os.environ.get(
    "FRONTEND_DIST", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "frontend", "dist")
)

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_MB * 1024 * 1024


@app.after_request
def add_cors(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
    return response


@app.errorhandler(json_table.BadRequest)
@app.errorhandler(excel_parse.BadRequest)
def handle_bad_request(e):
    return jsonify({"message": str(e)}), 400


@app.errorhandler(413)
def handle_too_large(e):
    return jsonify({"message": "上传文件超过 %dMB 限制" % MAX_UPLOAD_MB}), 400


@app.errorhandler(Exception)
def handle_error(e):
    return jsonify({"message": "服务器内部错误：%s" % e}), 500


@app.route("/api/convert/preview", methods=["POST"])
def preview():
    body = request.get_json(silent=True)
    if body is None:
        return jsonify({"message": "请求体不是合法的 JSON"}), 400

    headers, rows = _build_table(body)
    out_headers, out_rows = json_table.apply_column_config(
        headers, rows, body.get("columns"), body.get("headerAliases"))

    preview_rows = out_rows[:PREVIEW_MAX_ROWS]
    return jsonify({
        "headers": out_headers,
        "availableColumns": headers,
        "rows": out_rows,
        "totalRows": len(rows),
        "previewRows": preview_rows,
        "truncated": len(out_rows) > len(preview_rows),
    })


@app.route("/api/convert/export", methods=["POST"])
def export():
    body = request.get_json(silent=True)
    if body is None:
        return jsonify({"message": "请求体不是合法的 JSON"}), 400

    headers, rows = _build_table(body)
    out_headers, out_rows = json_table.apply_column_config(
        headers, rows, body.get("columns"), body.get("headerAliases"))

    file_name = (body.get("fileName") or "").strip() or "data"
    base_name = _encode_filename(file_name)

    buffer = io.BytesIO()
    excel_export.export(out_headers, out_rows,
                        body.get("sheetName") or "",
                        body.get("emptyValue") or "",
                        buffer)
    buffer.seek(0)

    response = send_file(buffer, mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                         as_attachment=False, download_name=file_name + ".xlsx")
    response.headers["Content-Disposition"] = (
        'attachment; filename="%s.xlsx"; filename*=UTF-8\'\'%s.xlsx' % (base_name, base_name))
    return response


@app.route("/api/convert/excel-to-json", methods=["POST"])
def excel_to_json():
    file = request.files.get("file")
    if file is None or not file.filename:
        return jsonify({"message": "请上传 Excel 文件"}), 400

    filename = file.filename
    if not (filename.lower().endswith(".xlsx") or filename.lower().endswith(".xls")):
        return jsonify({"message": "仅支持 .xlsx / .xls 格式的文件"}), 400

    has_header = _form_bool("hasHeader", True)
    expand_nested = _form_bool("expandNested", True)
    auto_type = _form_bool("autoType", True)
    aggregate_merged = _form_bool("aggregateMerged", False)
    key_separator = request.form.get("keySeparator") or "."

    suffix = ".xlsx" if filename.lower().endswith(".xlsx") else ".xls"
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        file.save(tmp.name)
        tmp.close()
        headers, rows, records, sheet_name, data_truncated = excel_parse.parse(
            tmp.name, has_header, expand_nested, auto_type, key_separator,
            aggregate_merged, EXCEL_MAX_ROWS)
    finally:
        try:
            os.unlink(tmp.name)
        except OSError:
            pass

    preview_rows = rows[:PREVIEW_MAX_ROWS]
    return jsonify({
        "headers": headers,
        "rows": rows,
        "sheetName": sheet_name,
        "jsonText": _pretty_json(records),
        "totalRows": len(rows),
        "previewRows": preview_rows,
        "truncated": len(rows) > len(preview_rows),
        "dataTruncated": data_truncated,
    })


def _build_table(body):
    return json_table.build_table(body.get("json"), body.get("keySeparator"))


def _form_bool(name, default):
    value = request.form.get(name)
    if value is None:
        return default
    return str(value).lower() == "true"


def _encode_filename(name):
    return quote(name, safe="").replace("+", "%20")


def _pretty_json(records):
    import json
    return json.dumps(records, ensure_ascii=False, indent=2)


# ---------- 前端静态资源托管（复用 frontend/dist） ----------

@app.route("/", defaults={"path": ""})
@app.route("/<path:path>")
def static_files(path):
    dist = os.path.abspath(FRONTEND_DIST)
    if path and os.path.isfile(os.path.join(dist, path)):
        return send_from_directory(dist, path)
    index = os.path.join(dist, "index.html")
    if os.path.isfile(index):
        return send_from_directory(dist, "index.html")
    return ("前端构建产物不存在，请先在 frontend 目录执行 npm run build", 404)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8081"))
    app.run(host="0.0.0.0", port=port)
