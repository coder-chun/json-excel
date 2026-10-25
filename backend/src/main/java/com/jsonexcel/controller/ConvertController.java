package com.jsonexcel.controller;

import com.jsonexcel.dto.ConvertRequest;
import com.jsonexcel.dto.ExcelParseResponse;
import com.jsonexcel.dto.PreviewResponse;
import com.jsonexcel.dto.TableData;
import com.jsonexcel.service.ExcelExportService;
import com.jsonexcel.service.ExcelParseService;
import com.jsonexcel.service.JsonTableService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON 与 Excel 双向转换接口。
 */
@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    private final JsonTableService jsonTableService;
    private final ExcelExportService excelExportService;
    private final ExcelParseService excelParseService;

    @Value("${convert.preview-max-rows:200}")
    private int previewMaxRows;

    @Value("${convert.excel-max-rows:20000}")
    private int excelMaxRows;

    public ConvertController(JsonTableService jsonTableService,
                             ExcelExportService excelExportService,
                             ExcelParseService excelParseService) {
        this.jsonTableService = jsonTableService;
        this.excelExportService = excelExportService;
        this.excelParseService = excelParseService;
    }

    /**
     * 解析 JSON 并返回表格预览数据（应用列配置与表头映射）。
     */
    @PostMapping("/preview")
    public PreviewResponse preview(@RequestBody ConvertRequest request) {
        TableData raw = jsonTableService.buildTable(request.getJson(), request.getKeySeparator());
        TableData table = jsonTableService.applyColumnConfig(
                raw, request.getColumns(), request.getHeaderAliases());

        int limit = previewMaxRows > 0 ? previewMaxRows : 200;
        List<List<Object>> rows = table.getRows();
        List<List<Object>> previewRows = new ArrayList<List<Object>>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            previewRows.add(rows.get(i));
        }

        PreviewResponse response = new PreviewResponse();
        response.setHeaders(table.getHeaders());
        response.setAvailableColumns(raw.getHeaders());
        response.setRows(previewRows);
        response.setTotalRows(table.getRowCount());
        response.setPreviewRows(previewRows.size());
        response.setTruncated(rows.size() > previewRows.size());
        return response;
    }

    /**
     * 解析 JSON 并导出为 xlsx 文件（应用列配置与表头映射）。
     */
    @PostMapping("/export")
    public void export(@RequestBody ConvertRequest request, HttpServletResponse response) throws IOException {
        TableData raw = jsonTableService.buildTable(request.getJson(), request.getKeySeparator());
        TableData table = jsonTableService.applyColumnConfig(
                raw, request.getColumns(), request.getHeaderAliases());

        String baseName = request.getFileName() == null || request.getFileName().trim().isEmpty()
                ? "data" : request.getFileName().trim();
        String encoded = URLEncoder.encode(baseName + ".xlsx", "UTF-8").replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        excelExportService.export(table, request.getSheetName(), request.getEmptyValue(), response.getOutputStream());
    }

    /**
     * 上传 Excel 并转换为 JSON（Excel -> JSON）。
     */
    @PostMapping(value = "/excel-to-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelParseResponse excelToJson(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "hasHeader", required = false) Boolean hasHeader,
                                          @RequestParam(value = "expandNested", required = false) Boolean expandNested,
                                          @RequestParam(value = "autoType", required = false) Boolean autoType,
                                          @RequestParam(value = "keySeparator", required = false) String keySeparator,
                                          @RequestParam(value = "aggregateMerged", required = false) Boolean aggregateMerged)
            throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("仅支持 .xlsx / .xls 格式的文件");
        }

        int maxRows = excelMaxRows > 0 ? excelMaxRows : 20000;
        InputStream in = file.getInputStream();
        ExcelParseService.ParsedExcel parsed;
        try {
            parsed = excelParseService.parse(in,
                    hasHeader == null || hasHeader,
                    expandNested == null || expandNested,
                    autoType == null || autoType,
                    keySeparator,
                    maxRows,
                    aggregateMerged != null && aggregateMerged);
        } finally {
            in.close();
        }

        int limit = previewMaxRows > 0 ? previewMaxRows : 200;
        List<List<Object>> rows = parsed.getRows();
        List<List<Object>> previewRows = new ArrayList<List<Object>>();
        for (int i = 0; i < rows.size() && i < limit; i++) {
            previewRows.add(rows.get(i));
        }

        ExcelParseResponse response = new ExcelParseResponse();
        response.setHeaders(parsed.getHeaders());
        response.setRows(previewRows);
        response.setSheetName(parsed.getSheetName());
        response.setJsonText(parsed.toJsonText());
        response.setTotalRows(rows.size());
        response.setPreviewRows(previewRows.size());
        response.setTruncated(rows.size() > previewRows.size());
        response.setDataTruncated(parsed.isDataTruncated());
        return response;
    }
}
