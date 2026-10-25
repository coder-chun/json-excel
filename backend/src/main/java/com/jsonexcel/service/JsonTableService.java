package com.jsonexcel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonexcel.dto.TableData;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 将 JSON 文本解析为二维表格：数组元素为行，嵌套对象按分隔符扁平化为列。
 */
@Service
public class JsonTableService {

    private static final String DEFAULT_SEPARATOR = ".";

    private final ObjectMapper objectMapper;

    public JsonTableService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TableData buildTable(String json, String keySeparator) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON 内容不能为空");
        }
        String separator = (keySeparator == null || keySeparator.isEmpty()) ? DEFAULT_SEPARATOR : keySeparator;

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败：" + e.getMessage());
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new IllegalArgumentException("JSON 内容为空或不是合法的 JSON 结构");
        }

        List<JsonNode> rowNodes = extractRows(root);
        List<Map<String, Object>> flatRows = new ArrayList<Map<String, Object>>();
        for (JsonNode rowNode : rowNodes) {
            Map<String, Object> flat = new LinkedHashMap<String, Object>();
            if (rowNode != null && rowNode.isObject()) {
                flatten("", rowNode, flat, separator);
            } else {
                flat.put("value", scalar(rowNode));
            }
            flatRows.add(flat);
        }

        LinkedHashSet<String> headerSet = new LinkedHashSet<String>();
        for (Map<String, Object> row : flatRows) {
            headerSet.addAll(row.keySet());
        }
        List<String> headers = new ArrayList<String>(headerSet);

        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (Map<String, Object> row : flatRows) {
            List<Object> values = new ArrayList<Object>(headers.size());
            for (String header : headers) {
                values.add(row.get(header));
            }
            rows.add(values);
        }
        return new TableData(headers, rows);
    }

    private List<JsonNode> extractRows(JsonNode root) {
        List<JsonNode> rowNodes = new ArrayList<JsonNode>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                rowNodes.add(item);
            }
        } else if (root.isObject() && root.size() == 1) {
            JsonNode only = root.elements().next();
            if (only != null && only.isArray()) {
                for (JsonNode item : only) {
                    rowNodes.add(item);
                }
                return rowNodes;
            }
            rowNodes.add(root);
        } else {
            rowNodes.add(root);
        }
        return rowNodes;
    }

    private void flatten(String prefix, JsonNode node, Map<String, Object> out, String separator) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            if (!prefix.isEmpty()) {
                out.put(prefix, null);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            if (!fields.hasNext()) {
                if (!prefix.isEmpty()) {
                    out.put(prefix, "");
                }
                return;
            }
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = prefix.isEmpty() ? entry.getKey() : prefix + separator + entry.getKey();
                flatten(key, entry.getValue(), out, separator);
            }
            return;
        }
        if (node.isArray()) {
            if (node.size() == 0) {
                out.put(prefix, "");
                return;
            }
            boolean allScalar = true;
            for (JsonNode item : node) {
                if (!item.isValueNode()) {
                    allScalar = false;
                    break;
                }
            }
            if (allScalar) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : node) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(item.isNull() ? "" : item.asText());
                }
                out.put(prefix, sb.toString());
            } else {
                out.put(prefix, node.toString());
            }
            return;
        }
        out.put(prefix, scalar(node));
    }

    /**
     * 应用自定义列配置：按 columns 裁剪并排序列，按 headerAliases 重命名表头。
     */
    public TableData applyColumnConfig(TableData table, List<String> columns, Map<String, String> aliases) {
        List<String> sourceHeaders = table.getHeaders();
        List<String> selected = new ArrayList<String>();
        if (columns == null || columns.isEmpty()) {
            selected.addAll(sourceHeaders);
        } else {
            for (String column : columns) {
                if (column != null && sourceHeaders.contains(column) && !selected.contains(column)) {
                    selected.add(column);
                }
            }
            if (selected.isEmpty()) {
                throw new IllegalArgumentException("自定义导出列均不存在于数据字段中，请检查列配置");
            }
        }

        List<String> outputHeaders = new ArrayList<String>(selected.size());
        for (String field : selected) {
            String alias = aliases == null ? null : aliases.get(field);
            outputHeaders.add(alias == null || alias.trim().isEmpty() ? field : alias.trim());
        }

        Map<String, Integer> index = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < sourceHeaders.size(); i++) {
            index.put(sourceHeaders.get(i), i);
        }

        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (List<Object> sourceRow : table.getRows()) {
            List<Object> row = new ArrayList<Object>(selected.size());
            for (String field : selected) {
                Integer position = index.get(field);
                row.add(position == null ? null : sourceRow.get(position));
            }
            rows.add(row);
        }
        return new TableData(outputHeaders, rows);
    }

    private Object scalar(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }
}
