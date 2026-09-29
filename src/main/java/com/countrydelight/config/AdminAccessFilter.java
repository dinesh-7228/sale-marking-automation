package com.countrydelight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class AdminAccessFilter extends OncePerRequestFilter {
    private final boolean required;
    private final String username;
    private final String password;

    public AdminAccessFilter(
            @Value("${app.access.required:true}") boolean required,
            @Value("${app.access.username:}") String username,
            @Value("${app.access.password:}") String password) {
        this.required = required;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("/healthz".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!required) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isSafeMethod(request.getMethod()) && isCrossSite(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request blocked");
            return;
        }

        if (username.isBlank() || password.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Admin access is not configured");
            return;
        }

        if (matchesAuthorization(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("WWW-Authenticate", "Basic realm=\"CM Automation Admin\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    private boolean matchesAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }

        try {
            String credentials = new String(
                    Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            if (separator < 0) {
                return false;
            }
            return same(credentials.substring(0, separator), username)
                    && same(credentials.substring(separator + 1), password);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean same(String actual, String expected) {
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    // Browsers attach the Basic credentials automatically, so a hostile page could POST to /api/*.
    // Sec-Fetch-Site is set by every current browser and is absent for curl/scripts, so this costs nothing.
    // ponytail: header-only CSRF check; move to a session cookie + token if legacy browsers must be covered.
    private static boolean isCrossSite(HttpServletRequest request) {
        return "cross-site".equals(request.getHeader("Sec-Fetch-Site"));
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
