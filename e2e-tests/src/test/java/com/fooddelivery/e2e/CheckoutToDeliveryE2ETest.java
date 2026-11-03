package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.util.List;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Mailpit;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;

/**
 * The main business flow across the services: cart and checkout (order-service, priced by catalog-service), payment
 * (payment-service), the restaurant's work (restaurant-service), delivery (delivery-service) and what the customer
 * hears about it (notification-service), all tied together by Kafka events.
 */
class CheckoutToDeliveryE2ETest extends E2ETestBase {

    @Test
    void anOnlineOrderIsPaidPreparedAndDelivered() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Biryani House " + unique, city);
        long biryani = Platform.addDish(owner, restaurantId, "Chicken Biryani", "299.00", "NON_VEG");
        String email = "asha-" + unique + "@example.com";
        Actor customer = Platform.registerCustomer("Asha " + unique, email);
        long addressId = Platform.addHomeAddress(customer, "8 Lavelle Road", city);
        Actor rider = Platform.availablePartner("Ravi " + unique, city);

        // Checkout: priced from the live menu, then waiting for the online payment
        Platform.putInCart(customer, biryani, 2);
        Response order = Platform.checkout(customer, addressId, "UPI", "checkout-" + unique).expect(201);
        long orderId = order.id();
        assertThat(order.text("/status")).isEqualTo("PAYMENT_PENDING");
        assertThat(order.amount("/price/itemsTotal")).isEqualByComparingTo("598.00");
        BigDecimal total = order.amount("/price/totalAmount");

        // Payment-service charges the total; order-service hears of it (PaymentStatusChanged) and places the order
        Response payment = Platform.payByUpi(customer, orderId, "pay-" + unique);
        assertThat(payment.status()).isBetween(200, 201);
        assertThat(payment.text("/status")).isEqualTo("SUCCESS");
        assertThat(payment.amount("/amount")).isEqualByComparingTo(total);
        await().untilAsserted(() -> assertThat(Platform.order(customer, orderId).text("/status")).isEqualTo("PLACED"));

        // The restaurant prepares it; delivery-service offers it to the partner waiting in this city
        Platform.restaurantPrepares(owner, restaurantId, orderId);
        Response offer = Platform.awaitOffer(rider);
        long deliveryId = offer.id();
        assertThat(offer.text("/status")).isEqualTo("ASSIGNED");
        assertThat(offer.text("/dropoffAddress/line1")).isEqualTo("8 Lavelle Road");
        Api.as(rider).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/accept").expect(200);

        // The customer sees who brings the food
        Response tracking = Api.as(customer).get("/api/v1/delivery/orders/" + orderId).expect(200);
        assertThat(tracking.text("/partnerName")).isEqualTo("Ravi " + unique);

        Api.as(rider).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/pickup").expect(200);
        await().untilAsserted(() -> assertThat(Platform.order(customer, orderId).text("/status"))
                .isEqualTo("OUT_FOR_DELIVERY"));
        assertThat(Platform.deliver(rider, deliveryId, null).expect(200).text("/status")).isEqualTo("DELIVERED");
        await().untilAsserted(() -> assertThat(Platform.order(customer, orderId).text("/status")).isEqualTo("DELIVERED"));

        // Every step is in the order's timeline, in order, and it was paid online
        Response delivered = Platform.order(customer, orderId);
        assertThat(delivered.text("/paymentStatus")).isEqualTo("PAID");
        assertThat(delivered.json("/timeline").valueStream().map(entry -> entry.path("status").asString()).toList())
                .containsSubsequence("PAYMENT_PENDING", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP",
                        "OUT_FOR_DELIVERY", "DELIVERED");

        // ...and the customer was told along the way: in the app, and by e-mail
        String no = "Order #" + orderId;
        await().untilAsserted(() -> assertThat(Platform.inboxTitles(customer, orderId))
                .contains(no + " placed", no + " accepted", no + " is on its way", no + " delivered"));
        await().untilAsserted(() -> assertThat(Mailpit.subjectsTo(email)).contains(no + " placed"));
    }

    @Test
    void aCashOrderGoesStraightToTheRestaurantAndThePartnerCollectsTheCash() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Cash Kitchen " + unique, city);
        long thali = Platform.addDish(owner, restaurantId, "Veg Thali", "250.00", "VEG");
        Actor customer = Platform.registerCustomer("Bala " + unique, "bala-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "3 Museum Road", city);
        Actor rider = Platform.availablePartner("Anil " + unique, city);

        Platform.putInCart(customer, thali, 1);
        Response order = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "cash-" + unique).expect(201);
        long orderId = order.id();
        assertThat(order.text("/status")).isEqualTo("PLACED");
        BigDecimal total = order.amount("/price/totalAmount");

        Platform.restaurantPrepares(owner, restaurantId, orderId);
        Response offer = Platform.awaitOffer(rider);
        assertThat(offer.amount("/cashToCollect")).isEqualByComparingTo(total);
        long deliveryId = offer.id();
        for (String step : List.of("accept", "pickup")) {
            Api.as(rider).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/" + step).expect(200);
        }

        // Handing over the food without the cash is refused; with it, the order is delivered and paid
        assertThat(Platform.deliver(rider, deliveryId, null).status()).isEqualTo(422);
        Platform.deliver(rider, deliveryId, total).expect(200);
        await().untilAsserted(() -> {
            Response delivered = Platform.order(customer, orderId);
            assertThat(delivered.text("/status")).isEqualTo("DELIVERED");
            assertThat(delivered.text("/paymentStatus")).isEqualTo("PAID");
        });
        Response partner = Api.as(rider).get("/api/v1/delivery/partners/me").expect(200);
        assertThat(partner.text("/status")).isEqualTo("AVAILABLE");
        assertThat(partner.number("/completedDeliveries")).isEqualTo(1);
    }

    @Test
    void aRepeatedCheckoutWithTheSameKeyPlacesOneOrder() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Retry Kitchen " + unique, city);
        long dosa = Platform.addDish(owner, restaurantId, "Masala Dosa", "120.00", "VEG");
        Actor customer = Platform.registerCustomer("Chitra " + unique, "chitra-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "5 Infantry Road", city);
        Platform.putInCart(customer, dosa, 3);

        // The network drops the answer and the app sends the same checkout again
        Response first = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "same-" + unique).expect(201);
        Response again = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "same-" + unique).expect(200);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.header("Idempotent-Replayed")).isEqualTo("true");
        // The same key with a different request is a mistake, not a retry
        assertThat(Platform.checkout(customer, addressId, "UPI", "same-" + unique).status()).isEqualTo(422);
        Response history = Api.as(customer).get("/api/v1/orders").expect(200);
        assertThat(history.number("/totalElements")).isEqualTo(1);
    }
}
