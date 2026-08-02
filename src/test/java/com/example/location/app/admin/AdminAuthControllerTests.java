package com.example.location.app.admin;

import com.example.location.app.ApiExceptionHandler;
import com.example.location.app.ClientIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthControllerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminProperties properties = properties(5);
        AdminTokenService tokenService = new AdminTokenService(properties, new ObjectMapper());
        AdminLoginRateLimiter rateLimiter = new AdminLoginRateLimiter(properties);
        ClientIpResolver clientIpResolver = new ClientIpResolver(new String[]{"127.0.0.0/8"});
        AdminAuthService authService = new AdminAuthService(
                properties,
                tokenService,
                rateLimiter,
                clientIpResolver
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminAuthController(authService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new AdminSecurityHeadersFilter())
                .build();
    }

    @Test
    void correctLoginReturnsShortLivedSignedToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.admin.username").value("admin"));
    }

    @Test
    void unknownUsernameAndWrongPasswordReturnSameGenericProblem() throws Exception {
        MvcResult unknownUser = login("missing", "correct-password")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrongPassword = login("admin", "incorrect")
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(unknownUser.getResponse().getContentAsString(), wrongPassword.getResponse().getContentAsString());
        assertFalse(unknownUser.getResponse().getContentAsString().contains("missing"));
    }

    @Test
    void repeatedFailuresAreRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            login("admin", "incorrect").andExpect(status().isUnauthorized());
        }

        login("admin", "incorrect")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Too many login attempts"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }

    static AdminProperties properties(int maxFailures) {
        return new AdminProperties(
                "admin",
                new BCryptPasswordEncoder(4).encode("correct-password"),
                "0123456789abcdef0123456789abcdef0123456789abcdef",
                900,
                maxFailures,
                900
        );
    }
}
