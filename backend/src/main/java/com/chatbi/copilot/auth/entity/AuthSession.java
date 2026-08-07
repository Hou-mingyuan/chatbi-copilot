package com.chatbi.copilot.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_session")
public class AuthSession {
    @TableId
    private String id;
    private Long userId;
    private String tokenHash;
    private String csrfHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastSeenAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
