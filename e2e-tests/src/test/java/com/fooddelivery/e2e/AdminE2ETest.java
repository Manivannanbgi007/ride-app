package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.util.List;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The admin side across services: every service's admin actions reach admin-service's platform-wide audit log, and
 * the dashboard and reports are put together from the figures each service keeps.
 */
class AdminE2ETest extends E2ETestBase {

    @Test
    void anAdminsActionsAreInThePlatformWideAuditLogWithWhoWhyAndTheRequest() {
        String unique = Platform.unique();
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.submitRestaurant(owner, "Audit Kitchen " + unique, "E2E-" + unique);
        String requestId = "e2e-audit-" + unique;
        Platform.approveAndActivate(restaurantId, requestId);

        // restaurant-service audits it in its own database; the outbox and Kafka bring it to admin-service
        String search = "/api/v1/admin/audit-logs?entityType=RESTAURANT&entityId=" + restaurantId;
        await().untilAsserted(() -> assertThat(actions(Api.as(Platform.admin()).get(search).expect(200)))
                .containsExactly("RESTAURANT_ACTIVATED", "RESTAURANT_APPROVED"));
        JsonNode approved = Api.as(Platform.admin()).get(search).expect(200).json("/content").valueStream()
                .filter(record -> "RESTAURANT_APPROVED".equals(record.path("action").asString()))
                .findFirst().orElseThrow();
        assertThat(approved.path("service").asString()).isEqualTo("restaurant-service");
        assertThat(approved.path("adminUserId").asLong()).isEqualTo(Platform.admin().userId());
        assertThat(approved.path("oldValue").asString()).isEqualTo("PENDING");
        assertThat(approved.path("newValue").asString()).isEqualTo("APPROVED");
        assertThat(approved.path("reason").asString()).isEqualTo("Documents verified");
        assertThat(approved.path("requestId").asString()).isEqualTo(requestId);

        // One record, with the other things the same admin request did
        Response details = Api.as(Platform.admin()).get("/api/v1/admin/audit-logs/" + approved.path("auditId").asLong())
                .expect(200);
        assertThat(details.text("/record/requestId")).isEqualTo(requestId);
        // Only platform admins read the log
        assertThat(Api.as(owner).get("/api/v1/admin/audit-logs").status()).isEqualTo(403);
    }

    @Test
    void theDashboardAndReportsCountANewOrderFromTheOwningServices() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Report Kitchen " + unique, city);
        long paneer = Platform.addDish(owner, restaurantId, "Paneer Tikka", "249.00", "VEG");
        Actor customer = Platform.registerCustomer("Isha " + unique, "isha-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "9 Infantry Road", city);
        long ordersBefore = dashboard().number("/summary/todaysOrders");

        Platform.putInCart(customer, paneer, 1);
        Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "report-" + unique).expect(201);

        // The dashboard asks the services at once: complete (nobody unavailable), today's orders include the new one
        Response dashboard = dashboard();
        assertThat(dashboard.json("/unavailable").isEmpty()).isTrue();
        assertThat(dashboard.number("/summary/todaysOrders")).isGreaterThan(ordersBefore);
        assertThat(dashboard.json("/charts/ordersPerDay").size()).isEqualTo(7);
        assertThat(dashboard.number("/summary/todaysOrders"))
                .isEqualTo(dashboard.json("/charts/ordersPerDay").get(6).path("orders").asLong());
        assertThat(Api.as(customer).get("/api/v1/admin/dashboard").status()).isEqualTo(403);

        // The order report covers the last 30 days and counts the new order; the revenue report's weeks add up
        Response orders = Api.as(Platform.admin()).get("/api/v1/admin/reports/orders").expect(200);
        assertThat(orders.json("/perDay").size()).isEqualTo(30);
        assertThat(orders.number("/totalOrders")).isPositive();
        assertThat(orders.json("/byStatus/PLACED").asLong()).isPositive();
        Response revenue = Api.as(Platform.admin()).get("/api/v1/admin/reports/revenue?groupBy=WEEK").expect(200);
        BigDecimal weeks = revenue.json("/periods").valueStream()
                .map(period -> period.path("revenue").decimalValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(weeks).isEqualByComparingTo(revenue.amount("/revenue"));
    }

    private static Response dashboard() {
        return Api.as(Platform.admin()).get("/api/v1/admin/dashboard?days=7").expect(200);
    }

    private static List<String> actions(Response page) {
        return page.json("/content").valueStream().map(record -> record.path("action").asString()).toList();
    }
}
