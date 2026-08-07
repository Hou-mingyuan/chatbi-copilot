package com.chatbi.copilot.auth.service;

import com.chatbi.copilot.auth.AppUserPrincipal;
import com.chatbi.copilot.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public AppUserPrincipal required() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw BusinessException.unauthorized("Authentication required");
        }
        return principal;
    }
}
