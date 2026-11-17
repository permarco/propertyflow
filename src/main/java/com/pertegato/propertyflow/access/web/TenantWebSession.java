package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

public final class TenantWebSession {
    public static final String ACCOUNT = "propertyflow.tenant.account";

    private TenantWebSession() {
    }

    public static UUID account(HttpServletRequest request) {
        HttpSession session = StaffWebSession.cookieSession(request);
        return session != null && session.getAttribute(ACCOUNT) instanceof UUID id ? id : null;
    }
}
