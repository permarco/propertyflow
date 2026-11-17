package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class StaffWebSession {

    public static final String LOGIN = "/anmelden";
    public static final String ACCOUNT = "propertyflow.staff.accountId";
    static final String CODE_EMAIL = "propertyflow.staff.codeEmail";
    public static final String RETURN_TO = "propertyflow.staff.returnTo";
    public static final String CSRF = "csrf";

    public static final String STAFF_LIST = "/mitarbeiter/faelle";
    private static final Pattern STAFF_TARGET = Pattern.compile(STAFF_LIST + "(?:/[A-Za-z0-9_-]+)?");

    private StaffWebSession() {
    }

    public static UUID administrativeActor(HttpServletRequest request, String csrf, boolean write) {
        HttpSession session = StaffWebSession.cookieSession(request);
        if (session == null || !(session.getAttribute(StaffWebSession.ACCOUNT) instanceof UUID id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (write && (csrf == null || !csrf.equals(session.getAttribute(StaffWebSession.CSRF)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return id;
    }

    public static String safeTarget(String value) {
        if (value != null && value.length() <= 2048) {
            try {
                URI uri = URI.create(value);
                if (!uri.isAbsolute() && uri.getRawAuthority() == null && uri.getRawFragment() == null
                        && uri.getRawPath() != null && STAFF_TARGET.matcher(uri.getRawPath()).matches()) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
                return STAFF_LIST;
            }
        }
        return STAFF_LIST;
    }

    public static HttpSession cookieSession(HttpServletRequest request) {
        return request.isRequestedSessionIdFromCookie() && !request.isRequestedSessionIdFromURL()
                ? request.getSession(false) : null;
    }

    public static String safeListTarget(String value) {
        String target = safeTarget(value);
        return STAFF_LIST.equals(URI.create(target).getRawPath()) ? target : STAFF_LIST;
    }

    static void protectResponse(HttpServletResponse response) {
        BrowserResponses.protect(response);
    }

    static void redirect(HttpServletRequest request, HttpServletResponse response, String target) {
        BrowserResponses.seeOther(request, response, target);
    }
}
