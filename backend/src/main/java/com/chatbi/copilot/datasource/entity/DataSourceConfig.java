package com.chatbi.copilot.datasource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ds_config")
public class DataSourceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** mysql | postgresql */
    private String dbType;

    private String host;

    private Integer port;

    private String databaseName;

    private String username;

    /** AES-GCM encrypted at rest. */
    private String password;

    /** Extra JDBC params, e.g. useSSL=false&serverTimezone=UTC */
    private String jdbcParams;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
