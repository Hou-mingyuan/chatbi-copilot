package com.chatbi.copilot.datasource.dto;

import com.chatbi.copilot.datasource.entity.DataSourceConfig;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Safe view of a datasource - never exposes the password.
 */
@Data
public class DataSourceVo {

    private Long id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String jdbcParams;
    private String remark;
    private boolean verifiedReadOnly;
    private boolean canQuery;
    private boolean canExport;
    private boolean canManageSemantic;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DataSourceVo from(DataSourceConfig c) {
        DataSourceVo vo = new DataSourceVo();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setDbType(c.getDbType());
        vo.setHost(c.getHost());
        vo.setPort(c.getPort());
        vo.setDatabaseName(c.getDatabaseName());
        vo.setUsername(c.getUsername());
        vo.setJdbcParams(c.getJdbcParams());
        vo.setRemark(c.getRemark());
        vo.setVerifiedReadOnly(Integer.valueOf(1).equals(c.getVerifiedReadOnly()));
        vo.setLastVerifiedAt(c.getLastVerifiedAt());
        vo.setCreatedAt(c.getCreatedAt());
        vo.setUpdatedAt(c.getUpdatedAt());
        return vo;
    }
}
