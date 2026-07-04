package com.chatbi.copilot.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataSourceReq {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "dbType is required (mysql | postgresql)")
    private String dbType;

    @NotBlank(message = "host is required")
    private String host;

    @NotNull(message = "port is required")
    private Integer port;

    @NotBlank(message = "databaseName is required")
    private String databaseName;

    private String username;

    /** Plaintext on input; encrypted before persisting. Empty on update keeps the existing one. */
    private String password;

    private String jdbcParams;

    private String remark;
}
