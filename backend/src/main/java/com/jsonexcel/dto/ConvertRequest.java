package com.jsonexcel.dto;

import java.util.List;
import java.util.Map;

/**
 * 转换请求：json 字段承载用户输入的 JSON 文本，其余为导出/解析选项。
 */
public class ConvertRequest {

    /** 用户输入的 JSON 文本 */
    private String json;

    /** 自定义导出列（原始字段名，顺序即导出顺序），为空表示导出全部列 */
    private List<String> columns;

    /** 表头映射：原始字段名 -> 显示名 */
    private Map<String, String> headerAliases;

    /** 导出文件名（不含扩展名） */
    private String fileName = "data";

    /** Excel 工作表名称 */
    private String sheetName = "Sheet1";

    /** 空值占位符 */
    private String emptyValue = "";

    /** 嵌套字段分隔符 */
    private String keySeparator = ".";

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public Map<String, String> getHeaderAliases() {
        return headerAliases;
    }

    public void setHeaderAliases(Map<String, String> headerAliases) {
        this.headerAliases = headerAliases;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getEmptyValue() {
        return emptyValue;
    }

    public void setEmptyValue(String emptyValue) {
        this.emptyValue = emptyValue;
    }

    public String getKeySeparator() {
        return keySeparator;
    }

    public void setKeySeparator(String keySeparator) {
        this.keySeparator = keySeparator;
    }
}
