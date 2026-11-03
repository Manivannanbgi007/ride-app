package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Observability;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * One checkout, seen through the development stack's observability tools: every service is scraped, the business
 * counters move, and the checkout is one trace across the services, found from its request id in the logs.
 * Needs Prometheus, Loki and Tempo: runs only with -Pobservability.
 */
@Tag("observability")
class ObservabilityE2ETest extends E2ETestBase {

    /** Prometheus scrapes every 15 s; Alloy, Loki and Tempo need a few seconds more. */
    private static final Duration PIPELINE = Duration.ofSeconds(60);
    private static final String UPI_ORDERS = "sum(orders_placed_total{payment_method=\"UPI\"})";

    @Test
    void aCheckoutIsCountedLoggedAndTracedAcrossTheServices() {
        assertThat(Observability.prometheus("sum(up{job=\"services\"})")).isEqualTo(11);
        double ordersBefore = Observability.scrapedAfter("order-service", UPI_ORDERS, Instant.now());

        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Traced Kitchen " + unique, city);
        long dosa = Platform.addDish(owner, restaurantId, "Masala Dosa", "120.00", "VEG");
        Actor customer = Platform.registerCustomer("Kavya " + unique, "kavya-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "3 Church Street", city);
        Platform.putInCart(customer, dosa, 2);
        String requestId = "e2e-trace-" + unique;
        Response order = Api.as(customer).header("Idempotency-Key", "trace-" + unique)
                .header("X-Request-Id", requestId)
                .post("/api/v1/orders", Map.of("addressId", addressId, "paymentMethod", "UPI")).expect(201);
        Platform.payByUpi(customer, order.id(), "trace-pay-" + unique);

        // Metrics: exactly one more UPI order, in a scrape made after it
        assertThat(Observability.scrapedAfter("order-service", UPI_ORDERS, Instant.now()) - ordersBefore).isEqualTo(1);

        // Logs carry the request id and the trace id; the trace spans the gateway, the REST calls and Kafka
        String traceId = await("order-service's log lines of the checkout in Loki").atMost(PIPELINE)
                .until(() -> Observability.traceIdOfRequest("order-service", requestId), Optional::isPresent)
                .orElseThrow();
        await("the checkout's trace in Tempo").atMost(PIPELINE).untilAsserted(() ->
                assertThat(Observability.servicesInTrace(traceId)).contains("api-gateway", "order-service",
                        "user-service", "restaurant-service", "catalog-service", "payment-service"));
    }
}
