package com.fooddelivery.notificationservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fooddelivery.notificationservice.client.RestaurantServiceClient;
import com.fooddelivery.notificationservice.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

/** Who may open a live connection, and what an owner is allowed to be sent. */
class LiveHandshakeInterceptorTest {

    private final RestaurantServiceClient restaurants = mock(RestaurantServiceClient.class);
    private final LiveHandshakeInterceptor interceptor = new LiveHandshakeInterceptor(restaurants);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final Map<String, Object> attributes = new HashMap<>();

    @Test
    void aCustomerIsAcceptedWithoutAskingForRestaurants() {
        assertThat(handshake("7", "CUSTOMER")).isTrue();

        LiveClient client = (LiveClient) attributes.get(LiveHandshakeInterceptor.CLIENT_ATTRIBUTE);
        assertThat(client.userId()).isEqualTo(7L);
        assertThat(client.isPlatformAdmin()).isFalse();
        verifyNoInteractions(restaurants);
    }

    @Test
    void aRestaurantOwnerIsOnlySentTheRestaurantsRestaurantServiceSaysAreTheirs() {
        when(restaurants.findOwnedRestaurantIds(5L)).thenReturn(Set.of(10L, 11L));

        assertThat(handshake("5", "RESTAURANT_ADMIN")).isTrue();

        LiveClient client = (LiveClient) attributes.get(LiveHandshakeInterceptor.CLIENT_ATTRIBUTE);
        assertThat(client.ownsRestaurant(10L)).isTrue();
        assertThat(client.ownsRestaurant(11L)).isTrue();
        assertThat(client.ownsRestaurant(12L)).isFalse();
    }

    @Test
    void anOwnerStillConnectsWhenRestaurantServiceIsDownButOwnsNothingYet() {
        when(restaurants.findOwnedRestaurantIds(5L)).thenThrow(new RemoteServiceException("restaurant-service",
                new RuntimeException("down")));

        assertThat(handshake("5", "RESTAURANT_ADMIN")).isTrue();

        LiveClient client = (LiveClient) attributes.get(LiveHandshakeInterceptor.CLIENT_ATTRIBUTE);
        assertThat(client.ownsRestaurant(10L)).isFalse();
    }

    @Test
    void aPlatformAdminIsAccepted() {
        assertThat(handshake("1", "PLATFORM_ADMIN")).isTrue();

        assertThat(((LiveClient) attributes.get(LiveHandshakeInterceptor.CLIENT_ATTRIBUTE)).isPlatformAdmin()).isTrue();
    }

    @Test
    void withoutAUsableTokenTheConnectionIsRefusedWith401() {
        String[][] callers = {{null, null}, {"abc", "CUSTOMER"}, {"-3", "CUSTOMER"}, {"0", "CUSTOMER"},
            {"7", "SUPERUSER"}, {"7", null}};
        for (String[] caller : callers) {
            Map<String, Object> fresh = new HashMap<>();
            ServerHttpResponse refused = mock(ServerHttpResponse.class);

            assertThat(interceptor.beforeHandshake(request(caller[0], caller[1]), refused, mock(WebSocketHandler.class),
                    fresh)).as(String.join("/", String.valueOf(caller[0]), String.valueOf(caller[1]))).isFalse();

            verify(refused).setStatusCode(HttpStatus.UNAUTHORIZED);
            assertThat(fresh).isEmpty();
        }
    }

    private boolean handshake(String userId, String role) {
        return interceptor.beforeHandshake(request(userId, role), response, mock(WebSocketHandler.class), attributes);
    }

    /** The handshake of a caller whose verified access token names this user and role (null user: no token). */
    private static ServerHttpRequest request(String userId, String role) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        if (userId != null) {
            Jwt.Builder token = Jwt.withTokenValue("token").header("alg", "RS256").subject(userId);
            if (role != null) {
                token.claim("role", role);
            }
            when(request.getPrincipal()).thenReturn(new JwtAuthenticationToken(token.build()));
        }
        return request;
    }
}
