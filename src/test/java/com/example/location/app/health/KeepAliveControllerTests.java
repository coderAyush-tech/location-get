package com.example.location.app.health;

import com.example.location.app.ApiExceptionHandler;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KeepAliveControllerTests {
    @Mock
    private MongoTemplate mongoTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KeepAliveService service = new KeepAliveService(mongoTemplate, "test-keep-alive-token");
        mockMvc = MockMvcBuilders
                .standaloneSetup(new KeepAliveController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authorizedMonitorReceivesBackendAndMongoStatus() throws Exception {
        when(mongoTemplate.executeCommand(any(Document.class)))
                .thenReturn(new Document("ok", 1.0));

        mockMvc.perform(get("/api/health/keep-alive")
                        .header("X-Keep-Alive-Token", "test-keep-alive-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.backend").value("up"))
                .andExpect(jsonPath("$.mongodb").value("up"))
                .andExpect(jsonPath("$.checkedAt").exists());
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/health/keep-alive"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Keep-alive check failed"))
                .andExpect(jsonPath("$.detail").value("Invalid keep-alive token."));
    }
}
