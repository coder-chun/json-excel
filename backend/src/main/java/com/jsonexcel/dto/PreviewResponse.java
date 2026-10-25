package com.jsonexcel.dto;

import java.util.List;

/**
 * 预览接口响应体。
 */
public class PreviewResponse {

    /** 表头（已应用列配置与别名映射） */
    private List<String> headers;

    /** 可用的原始字段名（未应用列配置，供前端列配置面板使用） */
    private List<String> availableColumns;

    /** 预览数据行 */
    private List<List<Object>> rows;

    /** 总行数 */
    private int totalRows;

    /** 本次返回的预览行数 */
    private int previewRows;

    /** 预览行数是否被截断 */
    private boolean truncated;

    public List<String> getAvailableColumns() {
        return availableColumns;
    }

    public void setAvailableColumns(List<String> availableColumns) {
        this.availableColumns = availableColumns;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public void setRows(List<List<Object>> rows) {
        this.rows = rows;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(int previewRows) {
        this.previewRows = previewRows;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
