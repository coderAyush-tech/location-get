package com.example.location.app.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {
    public static final String ADMIN_USERNAME_ATTRIBUTE = "admin.username";

    private final AdminTokenService tokenService;

    public AdminAuthorizationInterceptor(AdminTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw unauthorized();
        }

        AdminTokenService.AdminPrincipal principal = tokenService.verify(token);
        if (!"ADMIN".equals(principal.role())) {
            throw new AdminApiException(
                    HttpStatus.FORBIDDEN,
                    "Access denied",
                    "Administrator access is required."
            );
        }
        request.setAttribute(ADMIN_USERNAME_ATTRIBUTE, principal.username());
        return true;
    }

    private AdminApiException unauthorized() {
        return new AdminApiException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid Bearer token is required."
        );
    }
}
