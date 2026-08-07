package com.chatbi.copilot.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.entity.UserRole;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.auth.mapper.UserRoleMapper;
import com.chatbi.copilot.config.AuthProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DemoAuthInitializer implements ApplicationRunner {
    private final AuthProperties properties;
    private final AppUserMapper userMapper;
    private final UserRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DemoAuthInitializer(AuthProperties properties, AppUserMapper userMapper,
                               UserRoleMapper roleMapper, PasswordEncoder passwordEncoder,
                               JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isDemoAuthEnabled()) {
            return;
        }
        AppUser admin = ensureUser("admin", "系统管理员", properties.getDemoAdminPassword(), Set.of(Role.ADMIN));
        ensureUser("analyst", "业务分析员", properties.getDemoAnalystPassword(), Set.of(Role.ANALYST));
        ensureUser("viewer", "只读访客", properties.getDemoViewerPassword(), Set.of(Role.VIEWER));

        // Preserve legacy local metadata by assigning previously ownerless records to the demo admin.
        jdbcTemplate.update("UPDATE ds_config SET created_by = ? WHERE created_by IS NULL", admin.getId());
        jdbcTemplate.update("UPDATE query_history SET user_id = ? WHERE user_id IS NULL", admin.getId());
        jdbcTemplate.update("UPDATE favorite SET user_id = ? WHERE user_id IS NULL", admin.getId());
    }

    private AppUser ensureUser(String username, String displayName, String password, Set<Role> roles) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
        if (user == null) {
            user = new AppUser();
            user.setUsername(username);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setEnabled(1);
            userMapper.insert(user);
        }
        for (Role role : roles) {
            Long count = roleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, user.getId())
                    .eq(UserRole::getRole, role.name()));
            if (count == null || count == 0) {
                roleMapper.insert(new UserRole(user.getId(), role.name()));
            }
        }
        return user;
    }
}
