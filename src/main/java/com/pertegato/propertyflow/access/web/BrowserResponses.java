package com.pertegato.propertyflow.access.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public final class BrowserResponses {

    private BrowserResponses() {
    }

    public static void protect(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; "
                        + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    }

    public static void seeOther(HttpServletRequest request, HttpServletResponse response, String localPath) {
        response.setStatus(HttpStatus.SEE_OTHER.value());
        response.setHeader(HttpHeaders.LOCATION, request.getContextPath() + localPath);
    }
}
