package com.example.location.app.health;

import java.time.Instant;

public record KeepAliveResponse(
        String backend,
        String mongodb,
        Instant checkedAt
) {
}
