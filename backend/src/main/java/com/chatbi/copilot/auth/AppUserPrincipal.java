package com.chatbi.copilot.auth;

import java.security.Principal;
import java.util.Set;

public record AppUserPrincipal(
        Long userId,
        String username,
        String displayName,
        String sessionId,
        String csrfHash,
        Set<Role> roles
) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
