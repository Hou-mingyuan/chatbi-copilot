package com.chatbi.copilot.datasource;

import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.datasource.service.JdbcParameterPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcParameterPolicyTest {
    private final JdbcParameterPolicy policy = new JdbcParameterPolicy();

    @Test
    void acceptsDocumentedConnectionParameters() {
        assertThat(policy.sanitize("mysql", "sslMode=VERIFY_IDENTITY&connectTimeout=5000"))
                .isEqualTo("sslMode=VERIFY_IDENTITY&connectTimeout=5000");
        assertThat(policy.sanitize("postgresql", "currentSchema=analytics&sslmode=require"))
                .isEqualTo("currentSchema=analytics&sslmode=require");
    }

    @Test
    void rejectsParametersThatChangeExecutionSemantics() {
        assertThatThrownBy(() -> policy.sanitize("mysql", "allowMultiQueries=true"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.sanitize("mysql", "sessionVariables=sql_mode=ANSI"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.sanitize("postgresql", "options=-csearch_path=pg_catalog"))
                .isInstanceOf(BusinessException.class);
    }
}
