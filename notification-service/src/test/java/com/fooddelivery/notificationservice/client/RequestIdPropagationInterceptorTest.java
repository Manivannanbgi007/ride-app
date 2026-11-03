package com.fooddelivery.notificationservice.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fooddelivery.notificationservice.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RequestIdPropagationInterceptorTest {

    private final RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://user-service")
            .requestInterceptor(new RequestIdPropagationInterceptor());
    private final MockRestServiceServer userService = MockRestServiceServer.bindTo(builder).build();
    private final RestClient restClient = builder.build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsTheCurrentRequestIdToTheCalledService() {
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "req-abc-123");
        userService.expect(requestTo("http://user-service/ping"))
                .andExpect(header("X-Request-Id", "req-abc-123"))
                .andRespond(withSuccess());

        restClient.get().uri("/ping").retrieve().toBodilessEntity();

        userService.verify();
    }

    @Test
    void sendsNoHeaderOutsideOfARequest() {
        userService.expect(requestTo("http://user-service/ping"))
                .andExpect(headerDoesNotExist("X-Request-Id"))
                .andRespond(withSuccess());

        restClient.get().uri("/ping").retrieve().toBodilessEntity();

        userService.verify();
    }
}
