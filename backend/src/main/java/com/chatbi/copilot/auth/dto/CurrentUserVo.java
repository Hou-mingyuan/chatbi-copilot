package com.chatbi.copilot.auth.dto;

import com.chatbi.copilot.auth.AppUserPrincipal;

import java.util.Set;
import java.util.TreeSet;

public record CurrentUserVo(Long id, String username, String displayName, Set<String> roles) {
    public static CurrentUserVo from(AppUserPrincipal principal) {
        Set<String> names = new TreeSet<>();
        principal.roles().forEach(role -> names.add(role.name()));
        return new CurrentUserVo(principal.userId(), principal.username(), principal.displayName(), names);
    }
}
