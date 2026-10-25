package com.jsonexcel.service;

import com.jsonexcel.dto.TableData;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 将二维表格写出为 xlsx 工作簿。
 */
@Service
public class ExcelExportService {

    private static final int MAX_COLUMN_WIDTH = 60;

    public void export(TableData table, String sheetName, String emptyValue, OutputStream out) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle textStyle = buildDataStyle(workbook, HorizontalAlignment.LEFT);
            CellStyle numberStyle = buildDataStyle(workbook, HorizontalAlignment.RIGHT);
            String placeholder = emptyValue == null ? "" : emptyValue;

            int columnCount = Math.max(table.getColumnCount(), 1);
            int[] widths = new int[columnCount];
            for (int i = 0; i < columnCount; i++) {
                String header = i < table.getColumnCount() ? table.getHeaders().get(i) : "";
                widths[i] = displayLength(header);
            }

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22f);
            for (int i = 0; i < table.getColumnCount(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(table.getHeaders().get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (java.util.List<Object> dataRow : table.getRows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columnCount; i++) {
                    Object value = i < dataRow.size() ? dataRow.get(i) : null;
                    Cell cell = row.createCell(i);
                    if (value == null) {
                        if (!placeholder.isEmpty()) {
                            cell.setCellValue(placeholder);
                            cell.setCellStyle(textStyle);
                        }
                        continue;
                    }
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(numberStyle);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                        cell.setCellStyle(textStyle);
                    } else {
                        String text = String.valueOf(value);
                        cell.setCellValue(text);
                        cell.setCellStyle(textStyle);
                        widths[i] = Math.max(widths[i], displayLength(text));
                    }
                }
            }

            for (int i = 0; i < columnCount; i++) {
                int width = Math.min(MAX_COLUMN_WIDTH, Math.max(8, widths[i] + 2)) * 256;
                sheet.setColumnWidth(i, width);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            out.flush();
        } finally {
            workbook.close();
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 79, (byte) 70, (byte) 229}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle buildDataStyle(Workbook workbook, HorizontalAlignment alignment) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String sanitizeSheetName(String name) {
        String cleaned = name == null ? "" : name.trim().replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (cleaned.isEmpty()) {
            cleaned = "Sheet1";
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private int displayLength(String text) {
        if (text == null) {
            return 0;
        }
        int length = 0;
        for (char c : text.toCharArray()) {
            length += c > 0x2E80 ? 2 : 1;
        }
        return length;
    }
}
