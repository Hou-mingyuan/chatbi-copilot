package com.chatbi.copilot.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_role")
public class UserRole {
    @TableId("user_id")
    private Long userId;
    private String role;
}
