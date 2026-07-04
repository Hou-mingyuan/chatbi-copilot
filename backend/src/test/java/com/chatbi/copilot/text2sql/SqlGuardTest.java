package com.chatbi.copilot.text2sql;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import com.chatbi.copilot.text2sql.service.SqlGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    private SqlGuard guard;

    @BeforeEach
    void setup() {
        SqlGuardProperties props = new SqlGuardProperties();
        props.setDefaultLimit(500);
        props.setMaxLimit(5000);
        guard = new SqlGuard(props);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    @Test
    @DisplayName("A plain SELECT gets a default LIMIT injected")
    void injectsDefaultLimit() {
        String sql = guard.sanitize("select id, amount from orders");
        assertTrue(containsIgnoreCase(sql, "limit 500"), sql);
    }

    @Test
    @DisplayName("An oversized LIMIT is clamped to maxLimit")
    void clampsOversizedLimit() {
        String sql = guard.sanitize("select * from orders limit 999999");
        assertTrue(containsIgnoreCase(sql, "limit 5000"), sql);
        assertFalse(sql.contains("999999"), sql);
    }

    @Test
    @DisplayName("A reasonable existing LIMIT is preserved")
    void preservesReasonableLimit() {
        String sql = guard.sanitize("select * from orders limit 10");
        assertTrue(containsIgnoreCase(sql, "limit 10"), sql);
    }

    @Test
    @DisplayName("WITH ... SELECT (CTE) is allowed")
    void allowsCte() {
        String sql = guard.sanitize("with t as (select status, count(*) c from orders group by status) select * from t");
        assertTrue(containsIgnoreCase(sql, "limit 500"), sql);
    }

    @Test
    @DisplayName("Aggregation queries pass")
    void allowsAggregation() {
        String sql = guard.sanitize("select status, count(*) cnt from orders group by status order by cnt desc");
        assertTrue(containsIgnoreCase(sql, "select"), sql);
        assertTrue(containsIgnoreCase(sql, "limit"), sql);
    }

    @Test
    @DisplayName("DML/DDL statements are rejected")
    void rejectsDmlDdl() {
        assertThrows(BusinessException.class, () -> guard.sanitize("delete from orders"));
        assertThrows(BusinessException.class, () -> guard.sanitize("update orders set amount = 0"));
        assertThrows(BusinessException.class, () -> guard.sanitize("insert into orders(id) values(1)"));
        assertThrows(BusinessException.class, () -> guard.sanitize("drop table orders"));
        assertThrows(BusinessException.class, () -> guard.sanitize("truncate table orders"));
    }

    @Test
    @DisplayName("Stacked statements are rejected")
    void rejectsStackedStatements() {
        assertThrows(BusinessException.class, () -> guard.sanitize("select 1; drop table orders"));
    }

    @Test
    @DisplayName("INTO OUTFILE / dangerous IO is rejected")
    void rejectsOutfile() {
        assertThrows(BusinessException.class,
                () -> guard.sanitize("select * from orders into outfile '/tmp/x.txt'"));
    }

    @Test
    @DisplayName("Timing/DoS functions are rejected")
    void rejectsSleep() {
        assertThrows(BusinessException.class, () -> guard.sanitize("select sleep(10)"));
    }

    @Test
    @DisplayName("Markdown fences around the SQL are stripped")
    void stripsMarkdownFences() {
        String sql = guard.sanitize("```sql\nselect * from orders\n```");
        assertTrue(containsIgnoreCase(sql, "select * from orders"), sql);
        assertFalse(sql.contains("`"), sql);
    }

    @Test
    @DisplayName("Empty SQL is rejected")
    void rejectsEmpty() {
        assertThrows(BusinessException.class, () -> guard.sanitize("   "));
    }
}
