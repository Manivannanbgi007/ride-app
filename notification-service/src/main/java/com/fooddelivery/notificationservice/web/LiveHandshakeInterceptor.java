package com.fooddelivery.notificationservice.web;

import java.util.Map;
import java.util.Set;

import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.entity.Role;
import com.fooddelivery.notificationservice.exception.AuthenticationFailedException;
import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Decides, before a live connection opens, who the client is: the user of the verified access token (SecurityConfig
 * checks it; a browser sends it as ?access_token= because it cannot set headers on a WebSocket). Without one the
 * connection is refused with 401. A restaurant owner's restaurants are asked from restaurant-service, so what an owner is sent
 * is limited to restaurants that really are theirs, whatever the browser says.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiveHandshakeInterceptor implements HandshakeInterceptor {

    /** The attribute the accepted client is stored under, for the connection handler. */
    public static final String CLIENT_ATTRIBUTE = "liveClient";

    private final RestaurantServiceClient restaurantServiceClient;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        Long userId;
        Role role;
        try {
            if (!(request.getPrincipal() instanceof JwtAuthenticationToken token)) {
                throw new AuthenticationFailedException("No access token.");
            }
            String subject = token.getToken().getSubject();
            String roleName = token.getToken().getClaimAsString("role");
            if (subject == null || roleName == null) {
                throw new AuthenticationFailedException("No user in the access token.");
            }
            userId = Long.valueOf(subject);
            role = Role.valueOf(roleName);
            if (userId <= 0) {
                throw new AuthenticationFailedException("Not a user id.");
            }
        } catch (AuthenticationFailedException | IllegalArgumentException e) {
            log.info("Live connection refused: {}", e.getClass().getSimpleName());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(CLIENT_ATTRIBUTE, new LiveClient(userId, role, restaurantsOf(userId, role)));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {
        // nothing to do
    }

    /** Only a restaurant owner has restaurants. If restaurant-service is down the owner still connects (see below). */
    private Set<Long> restaurantsOf(Long userId, Role role) {
        if (role != Role.RESTAURANT_ADMIN) {
            return Set.of();
        }
        try {
            return restaurantServiceClient.findOwnedRestaurantIds(userId);
        } catch (RemoteServiceException e) {
            // They get what concerns their restaurants' reviews, and the orders of a restaurant once it changes; a
            // reconnect (the application reconnects by itself) asks again
            log.warn("Restaurants of user {} could not be read for a live connection: {}", userId, e.getMessage());
            return Set.of();
        }
    }
}
