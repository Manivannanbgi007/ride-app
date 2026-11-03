package com.fooddelivery.orderservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemDetails;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogLookupClientTest {

    private MockRestServiceServer catalogService;
    private CatalogLookupClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://catalog-service");
        catalogService = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogLookupClient(builder.build(), new RemoteCalls(CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.of(RetryConfig.custom().retryOnException(new ConnectionFailure()).build()),
                BulkheadRegistry.ofDefaults()));
    }

    @Test
    void readsImageAndCategoryForKnownIdsInOneCall() {
        catalogService.expect(requestTo(startsWith("http://catalog-service/api/v1/internal/menu-items")))
                .andExpect(queryParam("ids", "100", "999"))
                .andRespond(withSuccess("""
                        [{"id": 100, "restaurantId": 10, "name": "Chicken Biryani", "price": 299.00, "foodType": "NON_VEG",
                          "status": "ACTIVE", "available": true, "imageUrl": "http://img/100.jpg", "categoryName": "Mains"}]
                        """, MediaType.APPLICATION_JSON));

        Map<Long, MenuItemDetails> details = client.findDetails(List.of(100L, 999L));

        assertThat(details).containsOnlyKeys(100L);
        assertThat(details.get(100L).imageUrl()).isEqualTo("http://img/100.jpg");
        assertThat(details.get(100L).categoryName()).isEqualTo("Mains");
        catalogService.verify();
    }

    @Test
    void aFailingCatalogGivesAnEmptyResultInsteadOfAnError() {
        catalogService.expect(requestTo(startsWith("http://catalog-service/"))).andRespond(withServerError());

        assertThat(client.findDetails(List.of(100L))).isEmpty();
    }

    @Test
    void noIdsMeansNoCall() {
        assertThat(client.findDetails(List.of())).isEmpty();
        catalogService.verify();
    }
}
