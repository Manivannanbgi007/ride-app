package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Map;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;

/**
 * The saga's compensations: an order that ends early gives back what was taken (a payment is refunded), and the
 * customer may cancel only while the restaurant has not started.
 */
class CancellationAndRefundE2ETest extends E2ETestBase {

    @Test
    void aPaidOrderTheRestaurantDeclinesIsRefundedWithoutAnyoneAsking() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Busy Kitchen " + unique, city);
        long biryani = Platform.addDish(owner, restaurantId, "Mutton Biryani", "399.00", "NON_VEG");
        Actor customer = Platform.registerCustomer("Divya " + unique, "divya-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "7 Residency Road", city);

        Platform.putInCart(customer, biryani, 1);
        long orderId = Platform.checkout(customer, addressId, "UPI", "decline-" + unique).expect(201).id();
        long paymentId = Platform.payByUpi(customer, orderId, "decline-pay-" + unique).id();
        await().untilAsserted(() -> assertThat(Platform.order(customer, orderId).text("/status")).isEqualTo("PLACED"));

        Response rejected = Api.as(owner).put("/api/v1/restaurants/mine/" + restaurantId + "/orders/" + orderId + "/reject",
                Map.of("reason", "Out of mutton")).expect(200);
        assertThat(rejected.text("/status")).isEqualTo("REJECTED");

        // OrderCancelled -> payment-service refunds -> PaymentStatusChanged -> order-service records it
        await().untilAsserted(() -> assertThat(Platform.order(customer, orderId).text("/paymentStatus"))
                .isEqualTo("REFUNDED"));
        Response details = Api.as(Platform.admin()).get("/api/v1/admin/payments/" + paymentId).expect(200);
        assertThat(details.text("/payment/status")).isEqualTo("REFUNDED");
        assertThat(details.text("/refunds/0/requestedByType")).isEqualTo("SYSTEM");
        await().untilAsserted(() -> assertThat(Platform.inboxTitles(customer, orderId))
                .contains("Order #" + orderId + " was declined"));
    }

    @Test
    void theCustomerMayCancelOnlyUntilTheRestaurantAccepts() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Quick Kitchen " + unique, city);
        long kebab = Platform.addDish(owner, restaurantId, "Seekh Kebab", "220.00", "NON_VEG");
        Actor customer = Platform.registerCustomer("Esha " + unique, "esha-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "2 Brunton Road", city);

        Platform.putInCart(customer, kebab, 1);
        long early = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "early-" + unique).expect(201).id();
        Response cancelled = Api.as(customer).put("/api/v1/orders/" + early + "/cancel", Map.of("reason", "Ordered twice"))
                .expect(200);
        assertThat(cancelled.text("/status")).isEqualTo("CANCELLED");

        Platform.putInCart(customer, kebab, 1);
        long late = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "late-" + unique).expect(201).id();
        Api.as(owner).put("/api/v1/restaurants/mine/" + restaurantId + "/orders/" + late + "/accept").expect(200);
        Response tooLate = Api.as(customer).put("/api/v1/orders/" + late + "/cancel", Map.of("reason", "Too slow"));
        assertThat(tooLate.status()).isEqualTo(409);
        assertThat(Platform.order(customer, late).text("/status")).isEqualTo("ACCEPTED");
        // The customer hears that the cancellation in time went through
        await().untilAsserted(() -> assertThat(Platform.inboxTitles(customer, early))
                .contains("Order #" + early + " cancelled"));
    }
}
