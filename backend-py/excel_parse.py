# -*- coding: utf-8 -*-
"""读取 Excel（.xlsx/.xls）为记录列表与表格行，支持合并单元格聚合与嵌套键展开。

对应 Java 版 com.jsonexcel.service.ExcelParseService。
"""
import datetime
import re
from decimal import Decimal

import xlrd
from openpyxl import load_workbook

DATE_PATTERN = "%Y-%m-%d %H:%M:%S"
DEFAULT_SEPARATOR = "."
MAX_SIGNED_LONG = 1.0e15


class BadRequest(Exception):
    """参数/数据错误（HTTP 400）。"""


class _Region(object):
    def __init__(self, first_row, first_col, last_row, last_col):
        self.first_row = first_row
        self.first_col = first_col
        self.last_row = last_row
        self.last_col = last_col

    def contains(self, row, col):
        return self.first_row <= row <= self.last_row and self.first_col <= col <= self.last_col


class _DataRow(object):
    def __init__(self, raw_values, fill_values, new_source):
        self.raw_values = raw_values
        self.fill_values = fill_values
        self.new_source = new_source


def parse(file_name, has_header, expand_nested, auto_type, key_separator, aggregate_merged, max_rows=20000):
    """返回 (headers, rows, records, sheet_name, data_truncated)。"""
    if file_name.lower().endswith(".xlsx"):
        sheet = _OpenpyxlSheet(file_name)
    elif file_name.lower().endswith(".xls"):
        sheet = _XlrdSheet(file_name)
    else:
        raise BadRequest("仅支持 .xlsx / .xls 格式的文件")

    separator = key_separator if key_separator else DEFAULT_SEPARATOR
    return _parse_sheet(sheet, bool(has_header), bool(expand_nested), bool(auto_type),
                        separator, bool(aggregate_merged), max_rows)


class _OpenpyxlSheet(object):
    def __init__(self, file_name):
        try:
            wb = load_workbook(file_name, data_only=True)
        except Exception as e:
            raise BadRequest("Excel 解析失败：%s" % e)
        ws = None
        for name in wb.sheetnames:
            candidate = wb[name]
            if candidate.max_row and candidate.max_row > 0:
                ws = candidate
                break
        if ws is None:
            raise BadRequest("Excel 文件中没有可解析的工作表")
        self.name = ws.title
        self.max_row = ws.max_row - 1 if ws.max_row else -1  # 转 0 基
        self.max_col = ws.max_column - 1 if ws.max_column else -1
        self._ws = ws
        self._merges = []
        for r in ws.merged_cells.ranges:
            self._merges.append(_Region(r.min_row - 1, r.min_col - 1, r.max_row - 1, r.max_col - 1))

    def row_count(self):
        return self.max_row + 1

    def cell(self, row, col):
        value = self._ws.cell(row=row + 1, column=col + 1).value
        return _normalize_openpyxl(value)

    def row_width(self, row):
        return self.max_col + 1

    def merged_regions(self):
        return self._merges


class _XlrdSheet(object):
    def __init__(self, file_name):
        try:
            book = xlrd.open_workbook(file_name, formatting_info=True)
        except Exception as e:
            raise BadRequest("Excel 解析失败：%s" % e)
        sheet = None
        for idx in range(book.nsheets):
            candidate = book.sheet_by_index(idx)
            if candidate.nrows > 0:
                sheet = candidate
                break
        if sheet is None:
            raise BadRequest("Excel 文件中没有可解析的工作表")
        self.name = sheet.name
        self._sheet = sheet
        self._merges = []
        for rlo, rhi, clo, chi in sheet.merged_cells:
            self._merges.append(_Region(rlo, clo, rhi - 1, chi - 1))
        self._datemode = book.datemode

    def row_count(self):
        return self._sheet.nrows

    def cell(self, row, col):
        if row >= self._sheet.nrows or col >= self._sheet.ncols:
            return None
        c = self._sheet.cell(row, col)
        return _normalize_xlrd(c, self._datemode)

    def row_width(self, row):
        return self._sheet.ncols

    def merged_regions(self):
        return self._merges


def _normalize_openpyxl(value):
    if value is None:
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, datetime.datetime):
        return ("date", value)
    if isinstance(value, datetime.date):
        return ("date", datetime.datetime(value.year, value.month, value.day))
    if isinstance(value, (int, float)):
        return value
    text = str(value)
    return text


def _normalize_xlrd(cell, datemode):
    ctype = cell.ctype
    if ctype in (xlrd.XL_CELL_EMPTY, xlrd.XL_CELL_BLANK, xlrd.XL_CELL_ERROR):
        return None
    if ctype == xlrd.XL_CELL_BOOLEAN:
        return bool(cell.value)
    if ctype == xlrd.XL_CELL_DATE:
        try:
            dt = xlrd.xldate.xldate_as_datetime(cell.value, datemode)
            return ("date", dt)
        except Exception:
            return cell.value
    if ctype == xlrd.XL_CELL_NUMBER:
        return cell.value
    text = str(cell.value).strip()
    return text


def _read_cell(raw, auto_type):
    """对齐 Java readCell：NUMERIC 按 autoType 决定数值或文本，日期统一格式化字符串。"""
    if raw is None:
        return None
    if isinstance(raw, tuple) and len(raw) == 2 and raw[0] == "date":
        if auto_type:
            return raw[1].strftime(DATE_PATTERN)
        return raw[1].strftime(DATE_PATTERN)
    if isinstance(raw, bool):
        return raw if auto_type else ("true" if raw else "false")
    if isinstance(raw, (int, float)):
        if not auto_type:
            return _plain_number(raw)
        if isinstance(raw, int):
            return raw
        number = float(raw)
        if number == int(number) and abs(number) < MAX_SIGNED_LONG:
            return int(number)
        return number
    text = str(raw).strip()
    return text if text else None


def _plain_number(value):
    d = Decimal(repr(value)).normalize()
    return format(d, "f")


def _is_blank_row(sheet, row, column_count):
    width = max(column_count, sheet.row_width(row))
    for c in range(width):
        value = _read_cell(sheet.cell(row, c), True)
        if value is not None and str(value).strip():
            return False
    return True


def _parse_sheet(sheet, has_header, expand_nested, auto_type, separator, aggregate_merged, max_rows):
    data_start = 0
    headers = []

    if has_header:
        header_row = 0
        width = sheet.row_width(header_row)
        for i in range(width):
            value = _read_cell(sheet.cell(header_row, i), True)
            name = "" if value is None else str(value).strip()
            headers.append(name if name else "column_%d" % (i + 1))
        data_start = 1

    vertical_merges = [
        r for r in sheet.merged_regions()
        if r.last_row > r.first_row and r.last_row >= data_start
    ]

    rows_data = []
    prev_fill = None
    raw_row_count = 0
    data_truncated = False

    for r in range(data_start, sheet.row_count()):
        if _is_blank_row(sheet, r, len(headers)):
            continue
        if raw_row_count >= max_rows:
            data_truncated = True
            break
        if not has_header and not headers:
            width = max(sheet.row_width(r), 0)
            headers = ["column_%d" % (i + 1) for i in range(width)]
        if not headers:
            continue

        width = len(headers)
        raw_values = [None] * width
        fill_values = [None] * width
        new_source = [False] * width

        for c in range(width):
            raw = _read_cell(sheet.cell(r, c), auto_type)
            raw_values[c] = raw
            region = _find_region(vertical_merges, r, c)
            if region is not None:
                anchor = _read_cell(sheet.cell(region.first_row, region.first_col), auto_type)
                fill_values[c] = anchor
                new_source[c] = (r == region.first_row)
            elif raw is None:
                fill_values[c] = prev_fill[c] if prev_fill else None
                new_source[c] = False
            else:
                fill_values[c] = raw
                new_source[c] = (prev_fill is None or prev_fill[c] != raw)

        prev_fill = fill_values
        rows_data.append(_DataRow(raw_values, fill_values, new_source))
        raw_row_count += 1

    attr_columns = []
    if aggregate_merged:
        attr_columns = _detect_attribute_columns(rows_data, vertical_merges, len(headers))

    records = []
    table_rows = []
    if attr_columns:
        _assemble_aggregated(records, table_rows, rows_data, headers, attr_columns, expand_nested, separator)
    else:
        _assemble_plain(records, table_rows, rows_data, headers, expand_nested, separator)

    return headers, table_rows, records, sheet.name, data_truncated


def _find_region(regions, row, col):
    for region in regions:
        if region.contains(row, col):
            return region
    return None


def _detect_attribute_columns(rows_data, vertical_merges, column_count):
    attrs = [False] * column_count
    for region in vertical_merges:
        for c in range(region.first_col, min(region.last_col, column_count - 1) + 1):
            attrs[c] = True

    blank_seen = [False] * column_count
    has_carry = [False] * column_count
    has_value = [False] * column_count
    has_adjacent_dup = [False] * column_count
    prev_raw = [None] * column_count

    for data_row in rows_data:
        for c in range(column_count):
            raw = data_row.raw_values[c]
            if raw is None:
                blank_seen[c] = True
                continue
            has_value[c] = True
            if blank_seen[c]:
                has_carry[c] = True
            if prev_raw[c] is not None and prev_raw[c] == raw:
                has_adjacent_dup[c] = True
            prev_raw[c] = raw

    for c in range(column_count):
        if not has_value[c] or has_carry[c] or has_adjacent_dup[c]:
            attrs[c] = True
    return [i for i, flag in enumerate(attrs) if flag]


def _assemble_aggregated(records, table_rows, rows_data, headers, attr_columns, expand_nested, separator):
    groups = []
    current = None
    for data_row in rows_data:
        break_group = current is None
        if not break_group:
            for c in attr_columns:
                if data_row.new_source[c]:
                    break_group = True
                    break
        if break_group:
            current = []
            groups.append(current)
        current.append(data_row)

    for group in groups:
        record = {}
        table_row = []
        for c in range(len(headers)):
            values = [r.raw_values[c] for r in group if r.raw_values[c] is not None]
            if c in attr_columns:
                value = group[0].fill_values[c]
            elif values:
                value = values
            else:
                value = None
            table_row.append(value)
            _put_field(record, headers[c], value, expand_nested, separator)
        records.append(record)
        table_rows.append(table_row)


def _assemble_plain(records, table_rows, rows_data, headers, expand_nested, separator):
    for data_row in rows_data:
        record = {}
        for c in range(len(headers)):
            value = data_row.raw_values[c]
            _put_field(record, headers[c], value, expand_nested, separator)
        records.append(record)
        table_rows.append(list(data_row.raw_values))


def _put_field(record, key, value, expand_nested, separator):
    if not expand_nested or separator not in key:
        record[key] = value
        return
    parts = re.split(re.escape(separator), key)
    current = record
    for i, part in enumerate(parts):
        if i == len(parts) - 1:
            current[part] = value
        else:
            existing = current.get(part)
            if not isinstance(existing, dict):
                existing = {}
                current[part] = existing
            current = existing
