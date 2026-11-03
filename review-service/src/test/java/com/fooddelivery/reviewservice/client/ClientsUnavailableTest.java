package com.fooddelivery.reviewservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fooddelivery.reviewservice.exception.DependencyUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** What the two clients do when the other service cannot be reached (nothing listens on port 1). */
class ClientsUnavailableTest {

    private final RestClient unreachable = RestClient.builder().baseUrl("http://localhost:1").build();

    @Test
    void aReviewIsNeverAcceptedWithoutCheckingTheOrder() {
        OrderServiceClient client = new OrderServiceClient(unreachable);

        assertThatThrownBy(() -> client.findOrder(1L))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageStartingWith("order-service is unavailable");
    }

    @Test
    void theCatalogOnlyDecoratesSoItsAbsenceIsNotAnError() {
        CatalogServiceClient client = new CatalogServiceClient(unreachable);

        assertThat(client.findMenuItems(List.of(1L, 2L))).isEmpty();
    }

    @Test
    void noIdsNoCall() {
        assertThat(new CatalogServiceClient(unreachable).findMenuItems(List.of())).isEmpty();
    }
}
