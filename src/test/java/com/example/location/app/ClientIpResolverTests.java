package com.example.location.app;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTests {
    @Test
    void usesFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "106.222.248.114, 10.0.0.1");

        assertEquals("106.222.248.114", ClientIpResolver.resolve(request));
    }

    @Test
    void fallsBackToRealIpAndRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.7");
        assertEquals("203.0.113.7", ClientIpResolver.resolve(request));

        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", ClientIpResolver.resolve(localRequest));
    }
}
