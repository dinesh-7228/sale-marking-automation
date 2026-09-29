package com.countrydelight.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAccessFilterTest {
    @Test
    void blocksRequestsWhenAccessIsNotConfigured() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "", "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/environment");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
    }

    @Test
    void acceptsConfiguredBasicCredentials() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "admin", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/environment");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String credentials = Base64.getEncoder().encodeToString(
                "admin:secret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void keepsHealthCheckPublic() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "", "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/healthz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void blocksCrossSiteWrites() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "admin", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/order/place-and-mark");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String credentials = Base64.getEncoder().encodeToString(
                "admin:secret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);
        request.addHeader("Sec-Fetch-Site", "cross-site");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsSameOriginWrites() throws Exception {
        AdminAccessFilter filter = new AdminAccessFilter(true, "admin", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/order/place-and-mark");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String credentials = Base64.getEncoder().encodeToString(
                "admin:secret".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + credentials);
        request.addHeader("Sec-Fetch-Site", "same-origin");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
