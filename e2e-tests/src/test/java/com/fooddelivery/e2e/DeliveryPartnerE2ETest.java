package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.util.Map;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Delivery partners across their two services, through the API Gateway (same URLs as before the split):
 * delivery-partner-service owns who the partner is (profile, suspension, audit), delivery-service what they are doing
 * (online, on a delivery). The two keep each other informed with DeliveryPartnerChanged and
 * DeliveryPartnerStatusChanged.
 */
class DeliveryPartnerE2ETest extends E2ETestBase {

    private static final String PARTNERS = "/api/v1/admin/delivery-partners";

    @Test
    void aSuspendedPartnerGetsNoDeliveriesUntilReactivated() {
        String unique = Platform.unique();
        String city = "E2E-Partner-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Dosa Corner " + unique, city);
        long dosa = Platform.addDish(owner, restaurantId, "Masala Dosa", "120.00", "VEG");
        Actor customer = Platform.registerCustomer("Nila " + unique, "nila-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "4 Residency Road", city);
        Actor kiran = Platform.availablePartner("Kiran " + unique, city);

        // Admins find the partner with the status delivery-service reported (the reassign dialog's search)
        await().untilAsserted(() -> assertThat(Api.as(Platform.admin())
                .get(PARTNERS + "?status=AVAILABLE&city=" + city).expect(200).number("/totalElements")).isEqualTo(1));

        // Suspended in delivery-partner-service (audited); delivery-service stops offering deliveries
        Response suspended = Api.as(Platform.admin()).put(PARTNERS + "/" + kiran.userId() + "/deactivate",
                Map.of("reason", "Documents expired " + unique)).expect(200);
        assertThat(suspended.text("/status")).isEqualTo("SUSPENDED");
        await("delivery-service suspends partner " + kiran.userId()).until(() -> Api.as(kiran)
                .put("/api/v1/delivery/partners/me/availability", Map.of("status", "OFFLINE")).status() == 409);
        assertThat(Api.as(kiran).get("/api/v1/delivery/partners/me").expect(200).text("/status")).isEqualTo("SUSPENDED");

        // An order of the city goes to another partner, never to the suspended one
        Platform.putInCart(customer, dosa, 1);
        long orderId = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "partner-" + unique).expect(201).id();
        Platform.restaurantPrepares(owner, restaurantId, orderId);
        Actor meena = Platform.availablePartner("Meena " + unique, city);
        Response offer = Platform.awaitOffer(meena);
        assertThat(Api.as(kiran).get("/api/v1/delivery/deliveries/mine/current").status()).isEqualTo(204);

        // Meena delivers; delivery-partner-service learns her new count
        long deliveryId = offer.id();
        BigDecimal cash = offer.amount("/cashToCollect");
        Api.as(meena).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/accept").expect(200);
        Api.as(meena).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/pickup").expect(200);
        Platform.deliver(meena, deliveryId, cash).expect(200);
        await().untilAsserted(() -> {
            Response profile = Api.as(meena).get("/api/v1/delivery/partners/me").expect(200);
            assertThat(profile.number("/completedDeliveries")).isEqualTo(1);
            assertThat(profile.text("/status")).isEqualTo("AVAILABLE");
        });

        // Reactivated: Kiran goes online again
        Api.as(Platform.admin()).put(PARTNERS + "/" + kiran.userId() + "/activate",
                Map.of("reason", "New documents " + unique)).expect(200);
        await("delivery-service reactivates partner " + kiran.userId()).until(() -> Api.as(kiran)
                .put("/api/v1/delivery/partners/me/availability", Map.of("status", "AVAILABLE")).status() == 200);

        // Both decisions are in the partner's audit trail and in the platform-wide audit log
        Response trail = Api.as(Platform.admin()).get(PARTNERS + "/" + kiran.userId() + "/audit-logs").expect(200);
        assertThat(trail.json("/content").valueStream().map(entry -> entry.path("action").asString()))
                .containsExactly("DELIVERY_PARTNER_ACTIVATED", "DELIVERY_PARTNER_DEACTIVATED");
        String platformLog = "/api/v1/admin/audit-logs?entityType=DELIVERY_PARTNER&entityId=" + kiran.userId();
        await().untilAsserted(() -> assertThat(Api.as(Platform.admin()).get(platformLog).expect(200)
                .json("/content").valueStream().map(entry -> entry.path("service").asString()))
                .containsOnly("delivery-partner-service").hasSize(2));

        // The other half of the admin pages stays with delivery-service: Meena's deliveries
        JsonNode deliveries = Api.as(Platform.admin()).get(PARTNERS + "/" + meena.userId() + "/deliveries")
                .expect(200).json("/content");
        assertThat(deliveries.get(0).path("orderId").asLong()).isEqualTo(orderId);
    }
}
