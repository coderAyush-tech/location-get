package com.example.location.app.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<AdminLoginResponse> login(
            @RequestBody(required = false) AdminLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authService.login(request, servletRequest));
    }
}
