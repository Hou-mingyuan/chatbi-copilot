package com.chatbi.copilot.export;

import com.chatbi.copilot.text2sql.dto.ColumnMeta;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceTest {
    private final ExcelExportService service = new ExcelExportService(null, null);

    @Test
    void preservesLargeNumbersAndNeutralizesFormulaLikeText() throws Exception {
        List<ColumnMeta> columns = List.of(
                new ColumnMeta("huge_id", "NUMERIC", null),
                new ColumnMeta("amount", "DECIMAL", null),
                new ColumnMeta("note", "VARCHAR", null));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("huge_id", new BigInteger("123456789012345678901"));
        row.put("amount", new BigDecimal("185551.00"));
        row.put("note", "=HYPERLINK(\"https://invalid\",\"x\")");

        byte[] bytes = service.toXlsx(columns, List.of(row));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var exported = workbook.getSheetAt(0).getRow(1);
            assertThat(exported.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(exported.getCell(0).getStringCellValue()).isEqualTo("123456789012345678901");
            assertThat(exported.getCell(1).getNumericCellValue()).isEqualTo(185551.0);
            assertThat(exported.getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(exported.getCell(2).getStringCellValue()).startsWith("'=");
        }
    }
}
