package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.websocket.ChatWebSocketHandler;
import org.example.websocket.SupportWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final SupportWebSocketHandler supportWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/api/v1/ws/chat")
                .setAllowedOriginPatterns("*");
        registry.addHandler(supportWebSocketHandler, "/api/v1/ws/support")
                .setAllowedOriginPatterns("*");
    }
}
