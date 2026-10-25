package com.jsonexcel.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelParseServiceTest {

    private final ExcelParseService service = new ExcelParseService(null);

    /**
     * 截图场景：采购单号/日期/仓库/数量/收货地址纵向合并，序号与设备ID逐行明细。
     */
    private byte[] mergedFixture() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("S");
        Row header = sheet.createRow(0);
        String[] cols = {"序号", "采购单号", "日期", "仓库", "T3-红色", "收货地址", "设备ID"};
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }
        for (int i = 0; i < 8; i++) {
            Row row = sheet.createRow(1 + i);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(6).setCellValue("2030001" + (90 + i));
        }
        String[][] groups = {
                {"PO-001", "2020-11-11", "北京一区", "20", "李胜杰 15201507250 北京市通州区"},
                {"PO-002", "2020-11-11", "北京三区", "20", "张亚坤 17710361349 北京市朝阳区"}
        };
        for (int g = 0; g < 2; g++) {
            int firstRow = 1 + g * 4;
            for (int c = 1; c <= 5; c++) {
                if (c == 4) {
                    sheet.getRow(firstRow).createCell(c).setCellValue(20);
                } else {
                    sheet.getRow(firstRow).createCell(c).setCellValue(groups[g][c - 1]);
                }
                sheet.addMergedRegion(new CellRangeAddress(firstRow, firstRow + 3, c, c));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    @Test
    void aggregateMergedMergesGroupAndCollectsDetailArray() throws Exception {
        Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(mergedFixture()));
        Sheet sheet = wb.getSheetAt(0);
        wb.close();

        ExcelParseService.ParsedExcel parsed = service.parse(
                new ByteArrayInputStream(mergedFixture()), true, true, true, ".", 100, true);

        assertEquals(7, parsed.getHeaders().size());
        assertEquals(2, parsed.getRecords().size());

        Map<String, Object> first = parsed.getRecords().get(0);
        assertEquals("PO-001", first.get("采购单号"));
        assertEquals("2020-11-11", first.get("日期"));
        assertEquals("北京一区", first.get("仓库"));
        assertEquals(20L, first.get("T3-红色"));
        assertEquals("李胜杰 15201507250 北京市通州区", first.get("收货地址"));

        List<?> ids = (List<?>) first.get("设备ID");
        assertEquals(4, ids.size());
        assertEquals("203000190", ids.get(0));
        assertEquals("203000193", ids.get(3));

        List<?> seq = (List<?>) first.get("序号");
        assertEquals(4, seq.size());
        assertEquals(1L, seq.get(0));

        Map<String, Object> second = parsed.getRecords().get(1);
        assertEquals("PO-002", second.get("采购单号"));
        assertEquals("北京三区", second.get("仓库"));
        List<?> ids2 = (List<?>) second.get("设备ID");
        assertEquals(4, ids2.size());
        assertEquals("203000194", ids2.get(0));

        assertEquals(2, parsed.getRows().size());
    }

    @Test
    void aggregateDisabledKeepsRowPerLine() throws Exception {
        ExcelParseService.ParsedExcel parsed = service.parse(
                new ByteArrayInputStream(mergedFixture()), true, true, true, ".", 100, false);
        assertEquals(8, parsed.getRecords().size());
    }

    @Test
    void blankCarryDownGroupsWithoutMergedCells() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("S");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("日期");
        header.createCell(1).setCellValue("设备ID");
        String[][] data = {
                {"2020-11-11", "d1"}, {null, "d2"},
                {"2020-11-12", "d3"}, {null, "d4"}, {null, "d5"}
        };
        for (int i = 0; i < data.length; i++) {
            Row row = sheet.createRow(1 + i);
            if (data[i][0] != null) {
                row.createCell(0).setCellValue(data[i][0]);
            }
            row.createCell(1).setCellValue(data[i][1]);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        ExcelParseService.ParsedExcel parsed = service.parse(
                new ByteArrayInputStream(out.toByteArray()), true, true, true, ".", 100, true);

        assertEquals(2, parsed.getRecords().size());
        Map<String, Object> first = parsed.getRecords().get(0);
        assertEquals("2020-11-11", first.get("日期"));
        List<?> ids = (List<?>) first.get("设备ID");
        assertEquals(2, ids.size());
        assertEquals("2020-11-12", parsed.getRecords().get(1).get("日期"));
        assertEquals(3, ((List<?>) parsed.getRecords().get(1).get("设备ID")).size());
    }

    /**
     * 用户真实数据形态：tab 包裹的字符串、"\t \t" 占位、留空延续、全空列、混合日期格式。
     */
    @Test
    void messyRealWorldSheetAggregatesIntoGroups() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("S");
        Row header = sheet.createRow(0);
        String[] cols = {"序号", "采购单号", "日期", "仓库", "T3-红色", "收货地址", "设备ID", "卡板号/箱号"};
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }
        // 组1 20行（北京一区）、组2 20行（北京三区）、组3 10行（北京四区）、组4 10行（设备ID 全空）
        int[][] groupRows = {{1, 20}, {21, 40}, {41, 50}, {51, 60}};
        String[] dates = {"\t2020-11-11\t", "\t2020-11-11\t", "\t2020-11-11\t", "2020-11-11 00:00:00"};
        String[] warehouses = {"北京一区", "北京三区", "北京四区", "北京市朝阳"};
        int[] quantities = {20, 20, 10, 10};
        for (int g = 0; g < 4; g++) {
            for (int seq = groupRows[g][0]; seq <= groupRows[g][1]; seq++) {
                Row row = sheet.createRow(seq);
                row.createCell(0).setCellValue("\t" + seq + "\t");
                if (seq == groupRows[g][0]) {
                    row.createCell(2).setCellValue(dates[g]);
                    row.createCell(3).setCellValue(warehouses[g]);
                    row.createCell(4).setCellValue(quantities[g]);
                    row.createCell(5).setCellValue("地址" + (g + 1));
                } else {
                    row.createCell(4).setCellValue("\t \t");
                }
                if (g < 3) {
                    row.createCell(6).setCellValue(203000000 + seq);
                }
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        ExcelParseService.ParsedExcel parsed = service.parse(
                new ByteArrayInputStream(out.toByteArray()), true, true, true, ".", 100, true);

        assertEquals(4, parsed.getRecords().size());
        Map<String, Object> first = parsed.getRecords().get(0);
        assertEquals(null, first.get("采购单号"));
        assertEquals("2020-11-11", first.get("日期"));
        assertEquals("北京一区", first.get("仓库"));
        assertEquals(20L, first.get("T3-红色"));
        assertEquals(null, first.get("卡板号/箱号"));
        List<?> ids = (List<?>) first.get("设备ID");
        assertEquals(20, ids.size());
        assertEquals(203000001L, ids.get(0));
        assertEquals(203000020L, ids.get(19));
        List<?> seqs = (List<?>) first.get("序号");
        assertEquals(20, seqs.size());
        assertEquals("1", seqs.get(0));
        assertEquals("20", seqs.get(19));

        assertEquals("北京三区", parsed.getRecords().get(1).get("仓库"));
        assertEquals(20, ((List<?>) parsed.getRecords().get(1).get("设备ID")).size());
        assertEquals("北京四区", parsed.getRecords().get(2).get("仓库"));
        assertEquals(10L, parsed.getRecords().get(2).get("T3-红色"));
        assertEquals(10, ((List<?>) parsed.getRecords().get(2).get("设备ID")).size());

        Map<String, Object> last = parsed.getRecords().get(3);
        assertEquals("2020-11-11 00:00:00", last.get("日期"));
        assertEquals("北京市朝阳", last.get("仓库"));
        assertTrue(((List<?>) last.get("设备ID")).isEmpty());
    }

    @Test
    void flatDistinctTableFallsBackToPlainRows() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("S");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("a");
        header.createCell(1).setCellValue("b");
        for (int i = 0; i < 3; i++) {
            Row row = sheet.createRow(1 + i);
            row.createCell(0).setCellValue("v" + i);
            row.createCell(1).setCellValue("w" + i);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();

        ExcelParseService.ParsedExcel parsed = service.parse(
                new ByteArrayInputStream(out.toByteArray()), true, true, true, ".", 100, true);

        assertEquals(3, parsed.getRecords().size());
        assertEquals("v0", parsed.getRecords().get(0).get("a"));
        assertTrue(parsed.getRecords().get(0).get("b") instanceof String);
    }
}
