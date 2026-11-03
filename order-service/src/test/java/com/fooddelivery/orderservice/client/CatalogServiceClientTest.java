package com.fooddelivery.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemSummary;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogServiceClientTest {

    private MockRestServiceServer catalogService;
    private CatalogServiceClient catalogServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog-service");
        catalogService = MockRestServiceServer.bindTo(builder).build();
        catalogServiceClient = new CatalogServiceClient(builder.build(), new RemoteCalls(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().retryOnException(new ConnectionFailure()).build()),
                BulkheadRegistry.ofDefaults()));
    }

    @Test
    void fetchesManyItemsInOneCall() {
        catalogService.expect(requestTo(startsWith("http://catalog-service/api/v1/internal/menu-items")))
                .andExpect(queryParam("ids", "100", "101"))
                .andRespond(withSuccess("""
                        [{"id": 100, "restaurantId": 10, "name": "Chicken Biryani", "price": 299.00,
                          "foodType": "NON_VEG", "status": "ACTIVE", "available": true},
                         {"id": 101, "restaurantId": 10, "name": "Kulfi", "price": 89.00,
                          "foodType": "VEG", "status": "ACTIVE", "available": false}]
                        """, MediaType.APPLICATION_JSON));

        Map<Long, MenuItemSummary> items = catalogServiceClient.findMenuItems(List.of(100L, 101L));

        assertThat(items).containsOnlyKeys(100L, 101L);
        assertThat(items.get(100L).isOrderable()).isTrue();
        assertThat(items.get(101L).isOrderable()).isFalse();
        catalogService.verify();
    }

    @Test
    void noIdsMeansNoCall() {
        assertThat(catalogServiceClient.findMenuItems(List.of())).isEmpty();
        catalogService.verify();
    }

    @Test
    void failureBecomesRemoteServiceException() {
        catalogService.expect(requestTo(startsWith("http://catalog-service/"))).andRespond(withServerError());

        var menuItemIds = List.of(100L);
        assertThatThrownBy(() -> catalogServiceClient.findMenuItems(menuItemIds))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("catalog-service is unavailable");
    }
}
