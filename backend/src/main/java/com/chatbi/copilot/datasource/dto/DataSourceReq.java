package com.chatbi.copilot.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DataSourceReq {

    @NotBlank(message = "name is required")
    @Size(max = 128, message = "name is too long")
    private String name;

    @NotBlank(message = "dbType is required (mysql | postgresql)")
    @Pattern(regexp = "(?i)mysql|postgresql|postgres", message = "dbType must be mysql or postgresql")
    private String dbType;

    @NotBlank(message = "host is required")
    @Size(max = 255, message = "host is too long")
    private String host;

    @NotNull(message = "port is required")
    @Min(value = 1, message = "port must be between 1 and 65535")
    @Max(value = 65535, message = "port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "databaseName is required")
    @Size(max = 128, message = "databaseName is too long")
    private String databaseName;

    @NotBlank(message = "username is required")
    @Size(max = 128, message = "username is too long")
    private String username;

    /** Plaintext on input; encrypted before persisting. Empty on update keeps the existing one. */
    @Size(max = 200, message = "password is too long")
    private String password;

    @Size(max = 512, message = "jdbcParams is too long")
    private String jdbcParams;

    @Size(max = 512, message = "remark is too long")
    private String remark;
}
