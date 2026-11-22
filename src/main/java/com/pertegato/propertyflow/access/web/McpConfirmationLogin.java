package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

public final class McpConfirmationLogin {

    public static final String TENANT_PATH = "/mieter/mcp/bestaetigen/";
    public static final String STAFF_PATH = "/mitarbeiter/mcp/bestaetigen/";
    private static final String PENDING_TENANT = McpConfirmationLogin.class.getName() + ".tenant";
    private static final String PENDING_STAFF = McpConfirmationLogin.class.getName() + ".staff";

    private McpConfirmationLogin() {
    }

    public static void requireLogin(HttpServletRequest request, HttpServletResponse response, boolean staff, UUID operation) {
        request.getSession().setAttribute(staff ? PENDING_STAFF : PENDING_TENANT, operation);
        BrowserResponses.seeOther(request, response, staff ? StaffWebSession.LOGIN : "/mieter/anmelden");
    }

    static String takeReturnTarget(HttpSession session, boolean staff, String fallback) {
        String attribute = staff ? PENDING_STAFF : PENDING_TENANT;
        Object target = session.getAttribute(attribute);
        session.removeAttribute(attribute);
        return target instanceof UUID id ? (staff ? STAFF_PATH : TENANT_PATH) + id : fallback;
    }
}
