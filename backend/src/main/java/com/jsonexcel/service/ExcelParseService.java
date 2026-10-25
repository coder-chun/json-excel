package com.jsonexcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析 Excel 为二维表格与 JSON 文本（Excel -> JSON）。
 */
@Service
public class ExcelParseService {

    private final ObjectMapper objectMapper;

    public ExcelParseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析工作簿：首行为表头（可选），每行数据生成一个 JSON 对象。
     *
     * @param in           xlsx/xls 输入流
     * @param hasHeader    首行是否为表头
     * @param expandNested 是否将 a.b 形式的表头还原为嵌套对象
     * @param autoType     是否自动识别数值/布尔类型（否则全部输出字符串）
     * @param separator    嵌套字段分隔符
     * @param maxRows      最多转换的数据行数
     */
    public ParsedExcel parse(InputStream in, boolean hasHeader, boolean expandNested,
                            boolean autoType, String separator, int maxRows) throws Exception {
        return parse(in, hasHeader, expandNested, autoType, separator, maxRows, false);
    }

    /**
     * 解析工作簿（支持合并单元格聚合）。
     *
     * @param aggregateMerged 是否按合并单元格聚合：合并/延续列作为对象属性，明细列收为数组
     */
    public ParsedExcel parse(InputStream in, boolean hasHeader, boolean expandNested,
                            boolean autoType, String separator, int maxRows,
                            boolean aggregateMerged) throws Exception {
        String sep = (separator == null || separator.isEmpty()) ? "." : separator;
        Workbook workbook = WorkbookFactory.create(in);
        try {
            Sheet sheet = firstSheet(workbook);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件中没有可解析的工作表");
            }

            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            List<String> headers = new ArrayList<String>();
            int dataStart = firstRowNum;
            if (hasHeader) {
                Row headerRow = sheet.getRow(firstRowNum);
                if (headerRow == null) {
                    throw new IllegalArgumentException("Excel 首行为空，无法读取表头");
                }
                int width = headerRow.getLastCellNum();
                for (int i = 0; i < width; i++) {
                    Object value = readCell(headerRow.getCell(i), true);
                    String name = value == null ? "" : String.valueOf(value).trim();
                    headers.add(name.isEmpty() ? "column_" + (i + 1) : name);
                }
                dataStart = firstRowNum + 1;
            }

            List<CellRangeAddress> verticalMerges = new ArrayList<CellRangeAddress>();
            for (CellRangeAddress region : sheet.getMergedRegions()) {
                if (region.getLastRow() > region.getFirstRow() && region.getLastRow() >= dataStart) {
                    verticalMerges.add(region);
                }
            }

            List<DataRow> dataRows = new ArrayList<DataRow>();
            boolean dataTruncated = false;
            List<Object> prevFill = null;
            int rawRowCount = 0;

            for (int r = dataStart; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, headers.size())) {
                    continue;
                }
                if (rawRowCount >= maxRows) {
                    dataTruncated = true;
                    break;
                }
                if (!hasHeader && headers.isEmpty()) {
                    int width = Math.max(row.getLastCellNum(), 0);
                    for (int i = 0; i < width; i++) {
                        headers.add("column_" + (i + 1));
                    }
                }
                if (headers.isEmpty()) {
                    continue;
                }

                int width = headers.size();
                DataRow dataRow = new DataRow(width);
                for (int c = 0; c < width; c++) {
                    Object raw = readCell(row.getCell(c), autoType);
                    dataRow.rawValues.add(raw);
                    CellRangeAddress region = findRegion(verticalMerges, r, c);
                    if (region != null) {
                        Object fill = readCell(sheet.getRow(region.getFirstRow()).getCell(region.getFirstColumn()), autoType);
                        dataRow.fillValues.add(fill);
                        dataRow.newSource[c] = r == region.getFirstRow();
                    } else if (raw == null) {
                        dataRow.fillValues.add(prevFill == null ? null : prevFill.get(c));
                        dataRow.newSource[c] = false;
                    } else {
                        dataRow.fillValues.add(raw);
                        dataRow.newSource[c] = prevFill == null || !valueEquals(prevFill.get(c), raw);
                    }
                }
                prevFill = dataRow.fillValues;
                dataRows.add(dataRow);
                rawRowCount++;
            }

            List<List<Object>> tableRows = new ArrayList<List<Object>>();
            List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
            boolean[] attrColumns = aggregateMerged
                    ? detectAttributeColumns(dataRows, verticalMerges, headers.size()) : null;
            if (attrColumns != null && anyTrue(attrColumns)) {
                assembleAggregated(dataRows, attrColumns, headers, expandNested, sep, records, tableRows);
            } else {
                assemblePlain(dataRows, headers, expandNested, sep, records, tableRows);
            }

            ParsedExcel result = new ParsedExcel();
            result.setSheetName(sheet.getSheetName());
            result.setHeaders(headers);
            result.setRows(tableRows);
            result.setRecords(records);
            result.setDataTruncated(dataTruncated);
            return result;
        } finally {
            workbook.close();
        }
    }

    private Sheet firstSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                return sheet;
            }
        }
        return null;
    }

    private boolean isBlankRow(Row row, int columnCount) {
        int width = Math.max(columnCount, row.getLastCellNum());
        for (int i = 0; i < width; i++) {
            Object value = readCell(row.getCell(i), true);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Object readCell(Cell cell, boolean autoType) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cell.getDateCellValue());
                }
                double number = cell.getNumericCellValue();
                if (!autoType) {
                    return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
                }
                if (number == Math.floor(number) && !Double.isInfinite(number) && Math.abs(number) < 1.0e15) {
                    return (long) number;
                }
                return number;
            case BOOLEAN:
                boolean bool = cell.getBooleanCellValue();
                return autoType ? (Object) bool : String.valueOf(bool);
            case STRING:
                String text = cell.getStringCellValue();
                if (text == null) {
                    return null;
                }
                text = text.trim();
                return text.isEmpty() ? null : text;
            case ERROR:
                return null;
            default:
                return null;
        }
    }

    private void putField(Map<String, Object> record, String key, Object value,
                          boolean expandNested, String separator) {
        if (!expandNested || key.indexOf(separator) < 0) {
            record.put(key, value);
            return;
        }
        String[] parts = key.split(Pattern.quote(separator), -1);
        Map<String, Object> current = record;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(parts[i], next);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) next;
            current = nested;
        }
        current.put(parts[parts.length - 1], value);
    }

    /** 一行数据的中间结构：原始值、合并填充值、是否为新值源。 */
    private static class DataRow {

        final List<Object> rawValues;
        final List<Object> fillValues;
        final boolean[] newSource;

        DataRow(int width) {
            this.rawValues = new ArrayList<Object>(width);
            this.fillValues = new ArrayList<Object>(width);
            this.newSource = new boolean[width];
        }
    }

    private CellRangeAddress findRegion(List<CellRangeAddress> regions, int row, int col) {
        for (CellRangeAddress region : regions) {
            if (region.isInRange(row, col)) {
                return region;
            }
        }
        return null;
    }

    private boolean valueEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean anyTrue(boolean[] flags) {
        for (boolean flag : flags) {
            if (flag) {
                return true;
            }
        }
        return false;
    }

    /**
     * 属性列（输出标量）= 有纵向合并的列 ∪ 空白延续的列 ∪ 存在相邻重复值的列；
     * 其余（每行非空且互异）为明细列，输出数组。
     */
    private boolean[] detectAttributeColumns(List<DataRow> dataRows,
                                             List<CellRangeAddress> verticalMerges, int width) {
        boolean[] attr = new boolean[width];
        for (CellRangeAddress region : verticalMerges) {
            for (int c = region.getFirstColumn(); c <= region.getLastColumn() && c < width; c++) {
                attr[c] = true;
            }
        }

        boolean[] blankSeen = new boolean[width];
        boolean[] hasCarry = new boolean[width];
        boolean[] hasValue = new boolean[width];
        boolean[] hasAdjacentDup = new boolean[width];
        Object[] prevRaw = new Object[width];
        for (DataRow dataRow : dataRows) {
            for (int c = 0; c < width; c++) {
                Object raw = dataRow.rawValues.get(c);
                if (raw == null) {
                    blankSeen[c] = true;
                } else {
                    hasValue[c] = true;
                    if (blankSeen[c]) {
                        hasCarry[c] = true;
                    }
                    if (valueEquals(prevRaw[c], raw)) {
                        hasAdjacentDup[c] = true;
                    }
                }
                prevRaw[c] = raw;
            }
        }
        for (int c = 0; c < width; c++) {
            if (!hasValue[c] || hasCarry[c] || hasAdjacentDup[c]) {
                attr[c] = true;
            }
        }
        return attr;
    }

    /** 逐行平铺装配（普通模式 / 无可用分组结构时的退化模式）。 */
    private void assemblePlain(List<DataRow> dataRows, List<String> headers,
                               boolean expandNested, String sep,
                               List<Map<String, Object>> records, List<List<Object>> tableRows) {
        for (DataRow dataRow : dataRows) {
            Map<String, Object> record = new LinkedHashMap<String, Object>();
            for (int c = 0; c < headers.size(); c++) {
                putField(record, headers.get(c), dataRow.rawValues.get(c), expandNested, sep);
            }
            records.add(record);
            tableRows.add(new ArrayList<Object>(dataRow.rawValues));
        }
    }

    /** 分组聚合装配：属性列取组首填充值，明细列收组内非空值为数组。 */
    private void assembleAggregated(List<DataRow> dataRows, boolean[] attrColumns,
                                    List<String> headers, boolean expandNested, String sep,
                                    List<Map<String, Object>> records, List<List<Object>> tableRows) {
        List<List<DataRow>> groups = new ArrayList<List<DataRow>>();
        List<DataRow> current = null;
        for (DataRow dataRow : dataRows) {
            boolean breakGroup = current == null;
            for (int c = 0; c < attrColumns.length && !breakGroup; c++) {
                breakGroup = attrColumns[c] && dataRow.newSource[c];
            }
            if (breakGroup) {
                current = new ArrayList<DataRow>();
                groups.add(current);
            }
            current.add(dataRow);
        }

        for (List<DataRow> group : groups) {
            Map<String, Object> record = new LinkedHashMap<String, Object>();
            List<Object> tableRow = new ArrayList<Object>(headers.size());
            DataRow first = group.get(0);
            for (int c = 0; c < headers.size(); c++) {
                if (attrColumns[c]) {
                    Object value = first.fillValues.get(c);
                    putField(record, headers.get(c), value, expandNested, sep);
                    tableRow.add(value);
                } else {
                    List<Object> items = new ArrayList<Object>();
                    for (DataRow dataRow : group) {
                        Object value = dataRow.rawValues.get(c);
                        if (value != null) {
                            items.add(value);
                        }
                    }
                    putField(record, headers.get(c), items, expandNested, sep);
                    tableRow.add(items);
                }
            }
            records.add(record);
            tableRows.add(tableRow);
        }
    }

    /**
     * 解析结果：表格数据 + JSON 文本。
     */
    public static class ParsedExcel {

        private String sheetName;
        private List<String> headers;
        private List<List<Object>> rows;
        private List<Map<String, Object>> records;
        private boolean dataTruncated;

        public String toJsonText() throws Exception {
            return objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(records);
        }

        private ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
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

        public List<Map<String, Object>> getRecords() {
            return records;
        }

        public void setRecords(List<Map<String, Object>> records) {
            this.records = records;
        }

        public boolean isDataTruncated() {
            return dataTruncated;
        }

        public void setDataTruncated(boolean dataTruncated) {
            this.dataTruncated = dataTruncated;
        }
    }
}
