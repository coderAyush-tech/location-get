package com.example.location.app;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {
    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String clientIp = forwardedFor.split(",")[0].trim();
            if (!clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
                return clientIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
