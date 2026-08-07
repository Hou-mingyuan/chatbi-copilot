package com.chatbi.copilot.datasource.service.readonly;

import java.sql.Connection;
import java.sql.SQLException;

public interface ReadOnlyAccountVerifier {
    boolean supports(String dbType);

    ReadOnlyVerification verify(Connection connection) throws SQLException;
}
