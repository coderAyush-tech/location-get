package com.example.location.app;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoTimeoutConfig {

    /**
     * Prevent a missing Atlas connection from holding a location request for the
     * Mongo driver's default (roughly 30-second) server-selection timeout.
     */
    @Bean
    MongoClientSettingsBuilderCustomizer locationMongoTimeoutCustomizer() {
        return builder -> builder
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(settings -> settings
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(4, TimeUnit.SECONDS));
    }
}
