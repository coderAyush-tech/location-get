package com.example.location.app.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LiveAudioWebSocketConfig implements WebSocketConfigurer {
    private final LiveAudioWebSocketHandler handler;
    public LiveAudioWebSocketConfig(LiveAudioWebSocketHandler handler) { this.handler = handler; }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/live-audio").setAllowedOriginPatterns("https://bestue.netlify.app", "http://localhost:5173");
    }
}
