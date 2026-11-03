package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.Containers;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What a client sees while a service is down, and that the platform recovers on its own once it is back.
 * These tests stop containers of the running platform: they run only with -Presilience (mvnw verify -Presilience).
 */
@Tag("resilience")
class ResilienceE2ETest extends E2ETestBase {

    /** Long enough for a restarted service to boot and for the circuit breakers to close again. */
    private static final Duration RECOVERY = Duration.ofSeconds(120);

    @Test
    void withoutCatalogServiceTheCartSaysTryAgainLaterAndOtherApisKeepWorking() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Sturdy Kitchen " + unique, city);
        long thali = Platform.addDish(owner, restaurantId, "Veg Thali", "250.00", "VEG");
        Actor customer = Platform.registerCustomer("Jaya " + unique, "jaya-" + unique + "@example.com");
        Platform.putInCart(customer, thali, 2);

        Containers.stop("catalog-service");
        try {
            // order-service cannot price the cart: 503 with a hint when to try again (not an error page, not a hang)
            Response cart = Api.as(customer).get("/api/v1/cart");
            assertThat(cart.status()).isEqualTo(503);
            assertThat(cart.header("Retry-After")).isEqualTo("10");
            assertThat(cart.text("/title")).isEqualTo("Dependency unavailable");
            // What does not need catalog-service still works
            assertThat(Api.as(customer).get("/api/v1/orders").status()).isEqualTo(200);
        } finally {
            Containers.start("catalog-service");
        }
        await("the cart after catalog-service is back").atMost(RECOVERY).untilAsserted(() -> {
            Response cart = Api.as(customer).get("/api/v1/cart");
            assertThat(cart.status()).isEqualTo(200);
            assertThat(cart.amount("/price/itemsTotal")).isEqualByComparingTo("500.00");
        });
    }

    @Test
    void withoutAWholeServiceTheGatewayAnswersForIt() {
        Containers.stop("promotion-service");
        try {
            Response coupons = Api.anonymous().get("/api/v1/coupons?restaurantId=1");
            assertThat(coupons.status()).isIn(503, 504);
            assertThat(coupons.text("/service")).isEqualTo("promotion-service");
            assertThat(coupons.header("Retry-After")).isEqualTo("10");
            // The other services are not affected
            assertThat(Api.anonymous().get("/api/v1/categories").status()).isEqualTo(200);
        } finally {
            Containers.start("promotion-service");
        }
        await("promotion-service through the gateway again").atMost(RECOVERY)
                .until(() -> Api.anonymous().get("/api/v1/coupons?restaurantId=1").status() == 200);
    }
}
