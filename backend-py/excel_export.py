# -*- coding: utf-8 -*-
"""将二维表格写出为 xlsx 工作簿（openpyxl 实现，样式与 Java POI 版一致）。

对应 Java 版 com.jsonexcel.service.ExcelExportService。
"""
import re

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

MAX_COLUMN_WIDTH = 60

_SIDE = Side(style="thin")
_BORDER = Border(top=_SIDE, bottom=_SIDE, left=_SIDE, right=_SIDE)
_HEADER_FILL = PatternFill(fill_type="solid", fgColor="4F46E5")


def export(headers, rows, sheet_name, empty_value, out):
    workbook = Workbook(write_only=False)
    sheet = workbook.active
    sheet.title = sanitize_sheet_name(sheet_name)

    header_style = _header_style()
    text_style = _data_style("left")
    number_style = _data_style("right")
    placeholder = "" if empty_value is None else empty_value

    column_count = max(len(headers), 1)
    widths = [display_length(headers[i] if i < len(headers) else "") for i in range(column_count)]

    sheet.row_dimensions[1].height = 22.0 / 0.75  # 22pt ≈ 29.3 个 Excel 行高单位
    for i, header in enumerate(headers):
        cell = sheet.cell(row=1, column=i + 1, value=header)
        cell.font = header_style["font"]
        cell.fill = header_style["fill"]
        cell.alignment = header_style["alignment"]
        cell.border = _BORDER

    for data_row in rows:
        row_index = sheet.max_row + 1
        for i in range(column_count):
            value = data_row[i] if i < len(data_row) else None
            cell = sheet.cell(row=row_index, column=i + 1)
            if value is None:
                if placeholder:
                    cell.value = placeholder
                    cell.alignment = text_style
                    cell.border = _BORDER
                continue
            if isinstance(value, bool):
                cell.value = value
                cell.alignment = text_style
                cell.border = _BORDER
            elif isinstance(value, (int, float)):
                cell.value = value
                cell.alignment = number_style
                cell.border = _BORDER
            else:
                text = str(value)
                cell.value = text
                cell.alignment = text_style
                cell.border = _BORDER
                widths[i] = max(widths[i], display_length(text))

    for i in range(column_count):
        # POI 以 1/256 字符为单位；openpyxl 列宽单位即字符数
        width = min(MAX_COLUMN_WIDTH, max(8, widths[i] + 2))
        sheet.column_dimensions[sheet.cell(row=1, column=i + 1).column_letter].width = width

    sheet.freeze_panes = "A2"
    workbook.save(out)


def _header_style():
    return {
        "font": Font(bold=True, color="FFFFFF", size=11),
        "fill": _HEADER_FILL,
        "alignment": Alignment(horizontal="center", vertical="center"),
    }


def _data_style(horizontal):
    return Alignment(horizontal=horizontal, vertical="center")


def sanitize_sheet_name(name):
    cleaned = re.sub(r"[\\/?*\[\]:]", "_", name.strip()) if name else ""
    if not cleaned:
        cleaned = "Sheet1"
    return cleaned[:31]


def display_length(text):
    if text is None:
        return 0
    return sum(2 if ord(c) > 0x2E80 else 1 for c in text)
