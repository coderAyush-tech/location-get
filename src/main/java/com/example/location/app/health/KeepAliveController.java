package com.example.location.app.health;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class KeepAliveController {
    private static final String TOKEN_HEADER = "X-Keep-Alive-Token";

    private final KeepAliveService keepAliveService;

    public KeepAliveController(KeepAliveService keepAliveService) {
        this.keepAliveService = keepAliveService;
    }

    @GetMapping("/keep-alive")
    ResponseEntity<KeepAliveResponse> keepAlive(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(keepAliveService.ping(token));
    }
}
