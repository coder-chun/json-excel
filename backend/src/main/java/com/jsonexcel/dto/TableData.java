package com.jsonexcel.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的二维表格数据。
 */
public class TableData {

    private final List<String> headers;

    private final List<List<Object>> rows;

    public TableData(List<String> headers, List<List<Object>> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public static TableData empty() {
        return new TableData(new ArrayList<String>(), new ArrayList<List<Object>>());
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public int getColumnCount() {
        return headers.size();
    }

    public int getRowCount() {
        return rows.size();
    }
}
