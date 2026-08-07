package com.chatbi.copilot.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export", description = "Export query results")
@RestController
@RequestMapping("/export")
public class ExportController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelExportService excelExportService;

    public ExportController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @Operation(summary = "Export an authorized query snapshot as .xlsx")
    @GetMapping("/excel/{queryId}")
    public ResponseEntity<byte[]> excel(@PathVariable Long queryId) {
        byte[] bytes = excelExportService.export(queryId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=chatbi-query-" + queryId + ".xlsx")
                .body(bytes);
    }
}
