package com.chatbi.copilot.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.auth.Role;
import com.chatbi.copilot.auth.dto.CurrentUserVo;
import com.chatbi.copilot.auth.dto.LoginResponse;
import com.chatbi.copilot.auth.entity.AppUser;
import com.chatbi.copilot.auth.entity.AuthSession;
import com.chatbi.copilot.auth.entity.UserRole;
import com.chatbi.copilot.auth.mapper.AppUserMapper;
import com.chatbi.copilot.auth.mapper.AuthSessionMapper;
import com.chatbi.copilot.auth.mapper.UserRoleMapper;
import com.chatbi.copilot.common.BusinessException;
import com.chatbi.copilot.config.AuthProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private final AppUserMapper userMapper;
    private final UserRoleMapper roleMapper;
    private final AuthSessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final LoginRateLimiter rateLimiter;

    public AuthService(AppUserMapper userMapper, UserRoleMapper roleMapper,
                       AuthSessionMapper sessionMapper, PasswordEncoder passwordEncoder,
                       AuthProperties properties, LoginRateLimiter rateLimiter) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.sessionMapper = sessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public SessionLogin login(String username, String password, String remoteAddress) {
        String normalized = normalizeUsername(username);
        String rateKey = normalized + "|" + (remoteAddress == null ? "unknown" : remoteAddress);
        rateLimiter.check(rateKey);
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, normalized));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.failed(rateKey);
            throw BusinessException.unauthorized("Invalid username or password");
        }
        rateLimiter.succeeded(rateKey);

        String rawToken = TokenHasher.randomToken();
        String csrfToken = TokenHasher.randomToken();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setTokenHash(TokenHasher.sha256(rawToken));
        session.setCsrfHash(TokenHasher.sha256(csrfToken));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(properties.getSessionHours()));
        session.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        sessionMapper.insert(session);

        AppUserPrincipal principal = principal(user, session);
        return new SessionLogin(rawToken,
                new LoginResponse(CurrentUserVo.from(principal), csrfToken,
                        properties.getSessionHours() * 3600L));
    }

    public AppUserPrincipal authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        AuthSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, TokenHasher.sha256(rawToken)));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            return null;
        }
        AppUser user = userMapper.selectById(session.getUserId());
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            return null;
        }
        if (session.getLastSeenAt() == null || session.getLastSeenAt().plusMinutes(5).isBefore(now)) {
            sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                    .eq(AuthSession::getId, session.getId())
                    .set(AuthSession::getLastSeenAt, now));
        }
        return principal(user, session);
    }

    public void logout(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getId, sessionId)
                .isNull(AuthSession::getRevokedAt)
                .set(AuthSession::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private AppUserPrincipal principal(AppUser user, AuthSession session) {
        List<UserRole> rows = roleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, user.getId()));
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (UserRole row : rows) {
            try {
                roles.add(Role.valueOf(row.getRole()));
            } catch (IllegalArgumentException ignored) {
                // Unknown persisted roles grant no authority.
            }
        }
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                session.getId(), session.getCsrfHash(), Set.copyOf(roles));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public record SessionLogin(String rawToken, LoginResponse response) {
    }
}
