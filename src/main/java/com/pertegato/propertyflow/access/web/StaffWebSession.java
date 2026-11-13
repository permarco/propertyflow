package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

final class StaffWebSession {

    static final String LOGIN = "/anmelden";
    static final String ACCOUNT = "propertyflow.staff.accountId";
    static final String CODE_EMAIL = "propertyflow.staff.codeEmail";
    static final String RETURN_TO = "propertyflow.staff.returnTo";
    static final String CSRF = "csrf";

    private static final String STAFF_LIST = "/mitarbeiter/faelle";
    private static final Pattern STAFF_TARGET = Pattern.compile(STAFF_LIST + "(?:/[A-Za-z0-9_-]+)?");

    private StaffWebSession() {
    }

    static String safeTarget(String value) {
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

    static void protectResponse(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                        + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    }

    static void redirect(HttpServletRequest request, HttpServletResponse response, String target) {
        response.setStatus(HttpStatus.SEE_OTHER.value());
        response.setHeader(HttpHeaders.LOCATION, request.getContextPath() + target);
    }
}
