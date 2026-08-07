package com.chatbi.copilot.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatbi.copilot.audit.service.AuditService;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.dto.UserCreateRequest;
import com.chatbi.copilot.auth.dto.UserUpdateRequest;
import com.chatbi.copilot.auth.dto.UserVo;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.entity.AuthSession;
import com.chatbi.copilot.auth.entity.UserRole;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.auth.mapper.AuthSessionMapper;
import com.chatbi.copilot.auth.mapper.UserRoleMapper;
import com.chatbi.copilot.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,79}");
    private static final Pattern STRONG_PASSWORD = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,200}$");

    private final AppUserMapper userMapper;
    private final UserRoleMapper roleMapper;
    private final AuthSessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public AdminUserService(AppUserMapper userMapper, UserRoleMapper roleMapper,
                            AuthSessionMapper sessionMapper, PasswordEncoder passwordEncoder,
                            CurrentUserService currentUser, AuditService audit) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.sessionMapper = sessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<UserVo> list() {
        return userMapper.selectList(new LambdaQueryWrapper<AppUser>().orderByAsc(AppUser::getId))
                .stream().map(this::toVo).toList();
    }

    @Transactional
    public UserVo create(UserCreateRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new BusinessException("Username contains unsupported characters");
        }
        requireStrongPassword(request.getPassword());
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Username already exists");
        }
        Set<Role> roles = parseRoles(request.getRoles());
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.getDisplayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(1);
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
        audit.record("USER_CREATE", "USER", user.getId(), "SUCCESS",
                Map.of("username", username, "roles", roles));
        return toVo(user);
    }

    @Transactional
    public UserVo update(Long id, UserUpdateRequest request) {
        AppUser user = requiredUser(id);
        if (id.equals(currentUser.required().userId()) && !request.isEnabled()) {
            throw new BusinessException("You cannot disable your own account");
        }
        Set<Role> roles = parseRoles(request.getRoles());
        if (id.equals(currentUser.required().userId()) && !roles.contains(Role.ADMIN)) {
            throw new BusinessException("You cannot remove your own administrator role");
        }
        user.setDisplayName(request.getDisplayName().trim());
        user.setEnabled(request.isEnabled() ? 1 : 0);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            requireStrongPassword(request.getPassword());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateById(user);
        replaceRoles(id, roles);
        if (!request.isEnabled() || (request.getPassword() != null && !request.getPassword().isBlank())) {
            revokeSessions(id);
        }
        audit.record("USER_UPDATE", "USER", id, "SUCCESS",
                Map.of("enabled", request.isEnabled(), "roles", roles));
        return toVo(user);
    }

    private AppUser requiredUser(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("User not found");
        }
        return user;
    }

    private void replaceRoles(Long userId, Set<Role> roles) {
        roleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        roles.forEach(role -> roleMapper.insert(new UserRole(userId, role.name())));
    }

    private Set<Role> parseRoles(Set<String> roleNames) {
        try {
            return roleNames.stream().map(name -> Role.valueOf(name.trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            throw new BusinessException("Unknown role. Allowed roles: ADMIN, ANALYST, VIEWER");
        }
    }

    private void requireStrongPassword(String password) {
        if (!STRONG_PASSWORD.matcher(password).matches()) {
            throw new BusinessException("Password must contain upper/lowercase letters, a number and a symbol");
        }
    }

    private void revokeSessions(Long userId) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId)
                .isNull(AuthSession::getRevokedAt)
                .set(AuthSession::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private UserVo toVo(AppUser user) {
        Set<String> roles = roleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRole).collect(Collectors.toUnmodifiableSet());
        return new UserVo(user.getId(), user.getUsername(), user.getDisplayName(),
                Integer.valueOf(1).equals(user.getEnabled()), roles, user.getCreatedAt(), user.getUpdatedAt());
    }
}
