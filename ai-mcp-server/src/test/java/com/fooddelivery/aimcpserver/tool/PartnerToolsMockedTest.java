package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MOCKED: the delivery partner (rider) tools against a stubbed gateway (the delivery-service response shapes). */
class PartnerToolsMockedTest {

    private static final String MINE = "/api/v1/delivery/deliveries/mine";

    /** delivery-service DeliveryResponse: a cash-on-delivery order the partner has accepted */
    private static final String ACCEPTED_DELIVERY = """
            {"id":31,"orderId":42,"status":"ACCEPTED","restaurantId":7,"restaurantName":"Spice Hub",
             "pickupAddress":{"line1":"100 Feet Road","line2":"Indiranagar","city":"Bengaluru","postalCode":"560038",
               "latitude":12.97,"longitude":77.64},
             "customerId":1,"customerName":"Priya Sharma","customerPhone":"+919876543210",
             "dropoffAddress":{"line1":"12 MG Road","line2":"Flat 4","city":"Bengaluru","postalCode":"560001"},
             "paymentMethod":"CASH_ON_DELIVERY","cashToCollect":562.90,"currency":"INR",
             "partnerUserId":8,"partnerName":"Ravi Kumar","readyAt":"2026-10-09T07:00:00Z",
             "acceptedAt":"2026-10-09T07:02:00Z","pickedUpAt":null,"deliveredAt":null,
             "timeline":[{"event":"ASSIGNED","status":"ASSIGNED","note":"Offered to Ravi Kumar","at":"2026-10-09T07:01:00Z"},
                         {"event":"ACCEPTED","status":"ACCEPTED","note":null,"at":"2026-10-09T07:02:00Z"}]}
            """;

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    @Test
    void mockedCurrentDeliveryGivesWhereToGoWhomToMeetAndWhatToCollect() {
        gateway.on("GET", MINE + "/current", 200, ACCEPTED_DELIVERY);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.PARTNER, run, "partner_current_delivery", "{}");

        assertThat(result).contains("\"deliveryId\":31", "\"orderId\":42", "\"status\":\"ACCEPTED\"",
                "\"pickupAddress\":\"100 Feet Road, Indiranagar, Bengaluru 560038\"",
                "\"dropoffAddress\":\"12 MG Road, Flat 4, Bengaluru 560001\"", "\"customer\":\"Priya Sharma\"",
                "\"customerPhone\":\"+919876543210\"", "\"cashToCollect\":\"562.9 INR\"",
                "no delivery instructions");
        // Coordinates never reach the model
        assertThat(result).doesNotContain("12.97").doesNotContain("latitude");
        assertThat(gateway.calls().get(0).header("Authorization")).isEqualTo("Bearer token-of-user-8");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.entityType()).isEqualTo("DELIVERY");
        assertThat(card.items().get(0).link().path()).isEqualTo("/partner/deliveries/31");
        assertThat(card.items().get(0).meta()).anySatisfy(meta -> assertThat(meta.value()).isEqualTo("Collect 562.9 INR"));
    }

    @Test
    void mockedNoCurrentDeliveryIsSaidPlainly() {
        gateway.on("GET", MINE + "/current", 204, "");
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.PARTNER, run, "partner_current_delivery", "{}");

        assertThat(result).contains("\"currentDelivery\":\"none\"");
        assertThat(run.cards()).isEmpty();
    }

    @Test
    void mockedAnotherPartnersDeliveryIsNotFoundAndABadIdNeverReachesThePlatform() {
        // delivery-service answers 404 for a delivery that is not the caller's (no stub route = 404 here)
        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "partner_delivery_details", "{\"deliveryId\":999}"))
                .startsWith("ERROR").contains("Not found");
        assertThat(gateway.calls().get(0).path()).isEqualTo(MINE + "/999");

        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "partner_delivery_details", "{\"deliveryId\":-1}"))
                .startsWith("ERROR").contains("positive number");
        assertThat(gateway.calls()).hasSize(1);
    }

    @Test
    void mockedMyDeliveriesFiltersByStatusNewestFirstWithLinks() {
        gateway.on("GET", MINE, 200, """
                {"content":[{"id":31,"orderId":42,"status":"DELIVERED","restaurantName":"Spice Hub",
                   "pickupCity":"Bengaluru","dropoffCity":"Bengaluru","cashToCollect":0,
                   "deliveredAt":"2026-10-09T07:30:00Z"}],
                 "page":0,"size":10,"totalElements":12,"totalPages":2}
                """);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.PARTNER, run, "partner_my_deliveries",
                "{\"status\":\"delivered\",\"limit\":10}");

        assertThat(result).contains("\"totalDeliveries\":12", "\"deliveryId\":31", "\"status\":\"DELIVERED\"");
        assertThat(gateway.calls().get(0).query()).contains("status=DELIVERED", "size=10", "sort=createdAt%2Cdesc");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.items().get(0).link().path()).isEqualTo("/partner/deliveries/31");
        assertThat(card.items().get(0).meta()).anySatisfy(meta -> assertThat(meta.value()).isEqualTo("Paid online"));

        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "partner_my_deliveries", "{\"status\":\"LOST\"}"))
                .startsWith("ERROR").contains("ASSIGNED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED");
        assertThat(gateway.calls()).hasSize(1);
    }

    @Test
    void mockedStatsCountTodaysDeliveriesInCodeInTheBusinessTimeZoneAndSayThereAreNoEarnings() {
        gateway.on("GET", "/api/v1/delivery/partners/me", 200, """
                {"userId":8,"fullName":"Ravi Kumar","vehicleType":"MOTORCYCLE","vehicleNumber":"KA01AB1234",
                 "city":"Bengaluru","status":"AVAILABLE","completedDeliveries":15}
                """);
        // The fixed clock is 2026-10-09 in Asia/Kolkata (UTC+5:30)
        gateway.on("GET", MINE, 200, """
                {"content":[
                   {"id":3,"status":"DELIVERED","deliveredAt":"2026-10-09T08:00:00Z"},
                   {"id":2,"status":"DELIVERED","deliveredAt":"2026-10-08T19:00:00Z"},
                   {"id":1,"status":"DELIVERED","deliveredAt":"2026-10-08T18:00:00Z"}],
                 "totalElements":3}
                """);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.PARTNER, run, "partner_my_stats", "{}");

        // 19:00 UTC on the 8th is 00:30 on the 9th in Kolkata (today); 18:00 UTC is 23:30 on the 8th (yesterday)
        assertThat(result).contains("\"deliveredToday\":2", "\"completedDeliveriesTotal\":15",
                "\"availability\":\"AVAILABLE\"", "\"earnings\":\"not recorded by the platform\"");
        assertThat(gateway.calls().get(1).query()).contains("status=DELIVERED", "sort=deliveredAt%2Cdesc");
        assertThat(((Card.Stats) run.cards().get(0)).stats()).hasSize(3);
    }

    @Test
    void mockedOtherRolesCannotUseTheRiderToolsAndThePlatformIsNotCalled() {
        for (var caller : new com.fooddelivery.aimcpserver.security.CallerContext[] {Fixtures.CUSTOMER, Fixtures.OWNER,
                Fixtures.ADMIN}) {
            assertThat(ToolRunner.call(stack, caller, "partner_current_delivery", "{}"))
                    .startsWith("ERROR").contains("not available for your role");
        }
        assertThat(gateway.calls()).isEmpty();
    }
}
