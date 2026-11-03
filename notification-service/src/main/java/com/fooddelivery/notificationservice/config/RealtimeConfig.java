package com.fooddelivery.notificationservice.config;

import com.fooddelivery.notificationservice.web.LiveHandshakeInterceptor;
import com.fooddelivery.notificationservice.web.LiveSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * The live updates endpoint (WebSocket). The API Gateway passes {@code /ws/**} through to this service.
 * Only the React application's addresses may open a connection (app.realtime.allowed-origins).
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RealtimeConfig implements WebSocketConfigurer {

    public static final String LIVE_ENDPOINT = "/ws/v1/live";

    private final LiveSocketHandler handler;
    private final LiveHandshakeInterceptor handshakeInterceptor;
    private final RealtimeProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, LIVE_ENDPOINT)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
}
