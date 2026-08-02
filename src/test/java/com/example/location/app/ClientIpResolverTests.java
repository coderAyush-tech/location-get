package com.example.location.app;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTests {
    private final ClientIpResolver resolver = new ClientIpResolver(new String[]{
            "127.0.0.0/8", "10.0.0.0/8", "::1/128"
    });

    @Test
    void usesFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "106.222.248.114, 10.0.0.1");

        assertEquals("106.222.248.114", resolver.resolve(request));
    }

    @Test
    void fallsBackToRealIpAndRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.7");
        assertEquals("203.0.113.7", resolver.resolve(request));

        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", resolver.resolve(localRequest));
    }

    @Test
    void ignoresForwardedHeadersFromUntrustedRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.25");
        request.addHeader("X-Forwarded-For", "106.222.248.114");

        assertEquals("198.51.100.25", resolver.resolve(request));
    }

    @Test
    void ignoresClientPrependedSpoofAndUsesNearestUntrustedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 106.222.248.114, 10.0.0.4");

        assertEquals("106.222.248.114", resolver.resolve(request));
    }
}
