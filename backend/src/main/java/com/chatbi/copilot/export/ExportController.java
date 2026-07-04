package com.chatbi.copilot.export;

import com.chatbi.copilot.text2sql.dto.QueryExecResult;
import com.chatbi.copilot.text2sql.dto.RunSqlRequest;
import com.chatbi.copilot.text2sql.service.Text2SqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export", description = "Export query results")
@RestController
@RequestMapping("/export")
public class ExportController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Text2SqlService text2SqlService;
    private final ExcelExportService excelExportService;

    public ExportController(Text2SqlService text2SqlService, ExcelExportService excelExportService) {
        this.text2SqlService = text2SqlService;
        this.excelExportService = excelExportService;
    }

    @Operation(summary = "Export the result of a (guarded) SQL query as .xlsx")
    @PostMapping("/excel")
    public ResponseEntity<byte[]> excel(@Valid @RequestBody RunSqlRequest req) {
        QueryExecResult exec = text2SqlService.executeForExport(req.getDatasourceId(), req.getSql());
        byte[] bytes = excelExportService.toXlsx(exec.getColumns(), exec.getRows());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chatbi-export.xlsx")
                .body(bytes);
    }
}
