# -*- coding: utf-8 -*-
"""将 JSON 文本解析为二维表格：数组元素为行，嵌套对象按分隔符扁平化为列。

对应 Java 版 com.jsonexcel.service.JsonTableService。
"""
import json

DEFAULT_SEPARATOR = "."


class BadRequest(Exception):
    """参数/数据错误，对应 Java IllegalArgumentException（HTTP 400）。"""


def build_table(json_text, key_separator):
    if json_text is None or not str(json_text).strip():
        raise BadRequest("JSON 内容不能为空")
    separator = key_separator if key_separator else DEFAULT_SEPARATOR

    try:
        root = json.loads(json_text)
    except ValueError as e:
        raise BadRequest("JSON 解析失败：%s" % e)

    if root is None:
        raise BadRequest("JSON 内容为空或不是合法的 JSON 结构")

    row_nodes = _extract_rows(root)
    flat_rows = []
    for row_node in row_nodes:
        flat = {}
        if isinstance(row_node, dict):
            _flatten("", row_node, flat, separator)
        else:
            flat["value"] = _scalar(row_node)
        flat_rows.append(flat)

    headers = []
    for row in flat_rows:
        for key in row:
            if key not in headers:
                headers.append(key)

    rows = [[row.get(header) for header in headers] for row in flat_rows]
    return headers, rows


def _extract_rows(root):
    if isinstance(root, list):
        return list(root)
    if isinstance(root, dict) and len(root) == 1:
        only = next(iter(root.values()))
        if isinstance(only, list):
            return list(only)
        return [root]
    return [root]


def _flatten(prefix, node, out, separator):
    if node is None:
        if prefix:
            out[prefix] = None
        return
    if isinstance(node, dict):
        if not node:
            if prefix:
                out[prefix] = ""
            return
        for key, value in node.items():
            full_key = key if not prefix else prefix + separator + key
            _flatten(full_key, value, out, separator)
        return
    if isinstance(node, list):
        if not node:
            out[prefix] = ""
            return
        all_scalar = all(not isinstance(item, (dict, list)) for item in node)
        if all_scalar:
            parts = ["" if item is None else _as_text(item) for item in node]
            out[prefix] = ", ".join(parts)
        else:
            out[prefix] = json.dumps(node, ensure_ascii=False, separators=(",", ":"))
        return
    out[prefix] = _scalar(node)


def _as_text(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def _scalar(node):
    if node is None:
        return None
    if isinstance(node, bool):
        return node
    if isinstance(node, (int, float)):
        return node
    return str(node)


def apply_column_config(headers, rows, columns, aliases):
    """应用自定义列配置：按 columns 裁剪并排序列，按 header_aliases 重命名表头。"""
    if not columns:
        selected = list(headers)
    else:
        selected = []
        for column in columns:
            if column is not None and column in headers and column not in selected:
                selected.append(column)
        if not selected:
            raise BadRequest("自定义导出列均不存在于数据字段中，请检查列配置")

    output_headers = []
    for field in selected:
        alias = aliases.get(field) if aliases else None
        output_headers.append(field if alias is None or not alias.strip() else alias.strip())

    index = {field: i for i, field in enumerate(headers)}
    out_rows = []
    for source_row in rows:
        out_rows.append([source_row[index[field]] if field in index else None for field in selected])
    return output_headers, out_rows
