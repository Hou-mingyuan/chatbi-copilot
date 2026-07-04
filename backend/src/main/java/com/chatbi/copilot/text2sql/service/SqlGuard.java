package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.SqlGuardProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Read-only SQL safety guard. Guarantees a single {@code SELECT}, blocks DML/DDL, stacked
 * statements and dangerous functions (OUTFILE / sleep / load_file ...), and force-injects a LIMIT.
 *
 * <p>Strategy: parse the SQL and, when parsing succeeds, trust the AST (a SELECT AST cannot contain
 * DML). Only if parsing fails do we fall back to a strict keyword scan, so legitimate queries whose
 * identifiers happen to look like keywords are not falsely rejected.
 */
@Slf4j
@Component
public class SqlGuard {

    private static final Pattern STARTS_WITH_SELECT = Pattern.compile("(?i)^\\s*(with|select)\\b");
    private static final Pattern LIMIT_PRESENT = Pattern.compile("(?i)\\blimit\\b");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)(\\binto\\s+outfile\\b|\\binto\\s+dumpfile\\b|\\bload_file\\s*\\(|\\bbenchmark\\s*\\(|\\bsleep\\s*\\(|\\bpg_sleep\\s*\\()");
    private static final Pattern FORBIDDEN_DML = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|grant|revoke|merge|call|exec|execute|use|attach|detach|shutdown|rename|vacuum|reindex|lock|kill|copy)\\b");

    private final SqlGuardProperties props;

    public SqlGuard(SqlGuardProperties props) {
        this.props = props;
    }

    /**
     * Validate and normalize a candidate SQL string.
     *
     * @return the sanitized, limit-injected SQL
     * @throws BusinessException if the SQL is not a safe read-only SELECT
     */
    public String sanitize(String rawSql) {
        String sql = clean(rawSql);
        if (sql.isEmpty()) {
            throw new BusinessException("Empty SQL");
        }

        String scannable = stripLiterals(stripComments(sql));

        // 1) single statement only
        String trimmed = scannable.strip();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.contains(";")) {
            throw new BusinessException("Multiple SQL statements are not allowed");
        }

        // 2) dangerous constructs are always rejected
        if (DANGEROUS.matcher(scannable).find()) {
            throw new BusinessException("Dangerous SQL construct detected (file/IO or timing function)");
        }

        // 3) must look like a read query
        if (!STARTS_WITH_SELECT.matcher(sql).find()) {
            throw new BusinessException("Only read-only SELECT queries are allowed");
        }

        // 4) prefer AST validation; fall back to a strict keyword scan if parsing fails
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                throw new BusinessException("Only SELECT queries are allowed");
            }
            return enforceLimitViaAst(select);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception parseError) {
            log.warn("SQL parse failed, using keyword-scan fallback: {}", parseError.getMessage());
            if (FORBIDDEN_DML.matcher(scannable).find()) {
                throw new BusinessException("Only read-only SELECT queries are allowed");
            }
            return enforceLimitViaString(sql);
        }
    }

    private String enforceLimitViaAst(Select select) {
        if (select instanceof PlainSelect plain) {
            applyLimit(plain.getLimit(), plain::setLimit);
        } else if (select instanceof SetOperationList setOp) {
            applyLimit(setOp.getLimit(), setOp::setLimit);
        } else {
            return enforceLimitViaString(select.toString());
        }
        return select.toString();
    }

    private interface LimitSetter {
        void set(Limit limit);
    }

    private void applyLimit(Limit existing, LimitSetter setter) {
        if (existing == null) {
            setter.set(newLimit(props.getDefaultLimit()));
            return;
        }
        Expression rowCount = existing.getRowCount();
        if (rowCount instanceof LongValue lv) {
            if (lv.getValue() > props.getMaxLimit()) {
                existing.setRowCount(new LongValue(props.getMaxLimit()));
            }
        } else if (rowCount == null) {
            existing.setRowCount(new LongValue(props.getDefaultLimit()));
        }
    }

    private Limit newLimit(long n) {
        Limit limit = new Limit();
        limit.setRowCount(new LongValue(n));
        return limit;
    }

    private String enforceLimitViaString(String sql) {
        if (LIMIT_PRESENT.matcher(stripLiterals(stripComments(sql))).find()) {
            return sql;
        }
        return sql + " LIMIT " + props.getDefaultLimit();
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        // strip markdown code fences if the model wrapped the SQL
        s = s.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ")
                .replaceAll("#[^\\n]*", " ");
    }

    private String stripLiterals(String sql) {
        return sql.replaceAll("'(?:''|[^'])*'", "''")
                .replaceAll("\"(?:\"\"|[^\"])*\"", "\"\"")
                .replaceAll("`[^`]*`", "``");
    }
}
