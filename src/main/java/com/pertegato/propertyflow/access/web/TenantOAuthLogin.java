package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.regex.Pattern;

/** Übergibt nur den fest definierten OAuth-Einstieg an die bestehende Mieteranmeldung. */
public final class TenantOAuthLogin {

    public static final String AUTHORIZE = "/oauth2/authorize";
    private static final String PENDING = TenantOAuthLogin.class.getName() + ".query";
    private static final String STAFF_PENDING = TenantOAuthLogin.class.getName() + ".staffQuery";
    private static final Pattern QUERY = Pattern.compile("[\\x21-\\x7e]{1,4096}");

    private TenantOAuthLogin() {
    }

    public static void requireLogin(HttpServletRequest request, HttpServletResponse response) {
        requireLogin(request, response, false);
    }

    public static void requireLogin(HttpServletRequest request, HttpServletResponse response, boolean staff) {
        String query = request.getQueryString();
        if (!"GET".equals(request.getMethod()) || query == null || !QUERY.matcher(query).matches()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.getSession().setAttribute(staff ? STAFF_PENDING : PENDING, query);
        BrowserResponses.protect(response);
        BrowserResponses.seeOther(request, response, staff ? StaffWebSession.LOGIN : "/mieter/anmelden");
    }

    static String takeReturnTarget(HttpSession session) {
        return takeReturnTarget(session, PENDING, "/mieter/faelle");
    }

    static String takeStaffReturnTarget(HttpSession session, String fallback) {
        return takeReturnTarget(session, STAFF_PENDING, fallback);
    }

    private static String takeReturnTarget(HttpSession session, String attribute, String fallback) {
        Object pending = session.getAttribute(attribute);
        session.removeAttribute(attribute);
        return pending instanceof String query && QUERY.matcher(query).matches() ? AUTHORIZE + "?" + query : fallback;
    }
}
