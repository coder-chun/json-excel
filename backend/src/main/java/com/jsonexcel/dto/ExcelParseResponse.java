package com.jsonexcel.dto;

import java.util.List;

/**
 * Excel 转 JSON 响应体：既包含表格预览数据，也包含生成的 JSON 文本。
 */
public class ExcelParseResponse {

    /** 表头 */
    private List<String> headers;

    /** 预览数据行 */
    private List<List<Object>> rows;

    /** 工作表名 */
    private String sheetName;

    /** 解析出的 JSON 文本 */
    private String jsonText;

    /** 总行数 */
    private int totalRows;

    /** 本次返回的预览行数 */
    private int previewRows;

    /** 预览行数是否被截断 */
    private boolean truncated;

    /** 转换是否因行数上限被截断 */
    private boolean dataTruncated;

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

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getJsonText() {
        return jsonText;
    }

    public void setJsonText(String jsonText) {
        this.jsonText = jsonText;
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

    public boolean isDataTruncated() {
        return dataTruncated;
    }

    public void setDataTruncated(boolean dataTruncated) {
        this.dataTruncated = dataTruncated;
    }
}
