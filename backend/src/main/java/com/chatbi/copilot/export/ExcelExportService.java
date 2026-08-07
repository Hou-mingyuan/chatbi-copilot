package com.chatbi.copilot.export;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.history.dto.QuerySnapshot;
import com.chatbi.copilot.history.service.HistoryService;
import com.chatbi.copilot.permission.Capability;
import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ExcelExportService {

    private static final int EXCEL_EXACT_DIGITS = 15;

    private final HistoryService historyService;
    private final AuditService audit;

    public ExcelExportService(HistoryService historyService, AuditService audit) {
        this.historyService = historyService;
        this.audit = audit;
    }

    public byte[] export(Long queryId) {
        QuerySnapshot snapshot = historyService.snapshot(queryId, Capability.EXPORT);
        byte[] bytes = toXlsx(snapshot.columns(), snapshot.rows());
        audit.record("QUERY_EXPORT", "QUERY", queryId, "SUCCESS",
                Map.of("rowCount", snapshot.rowCount(), "truncated", snapshot.truncated()));
        return bytes;
    }

    public byte[] toXlsx(List<ColumnMeta> columns, List<Map<String, Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Result");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(safeText(columns.get(i).getName()));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 22 * 256);
            }

            int rowIdx = 1;
            for (Map<String, Object> row : rows) {
                Row xlsRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = xlsRow.createCell(i);
                    setCellValue(cell, row.get(columns.get(i).getName()));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Excel export generation failed", e);
            throw new BusinessException("Excel export could not be generated");
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal decimal) {
            if (decimal.precision() <= EXCEL_EXACT_DIGITS) {
                cell.setCellValue(decimal.doubleValue());
            } else {
                cell.setCellValue(decimal.toPlainString());
            }
        } else if (value instanceof BigInteger integer) {
            if (integer.abs().toString().length() <= EXCEL_EXACT_DIGITS) {
                cell.setCellValue(integer.doubleValue());
            } else {
                cell.setCellValue(integer.toString());
            }
        } else if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            if (String.valueOf(Math.abs(number)).length() <= EXCEL_EXACT_DIGITS) {
                cell.setCellValue(number);
            } else {
                cell.setCellValue(String.valueOf(number));
            }
        } else if (value instanceof Number n) {
            double number = n.doubleValue();
            if (Double.isFinite(number)) {
                cell.setCellValue(number);
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(safeText(String.valueOf(value)));
        }
    }

    private String safeText(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
