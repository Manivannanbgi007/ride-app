package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MOCKED: tools hand the model finished figures (counts, reasons) and ask questions instead of needing ids. */
class GroundedToolsMockedTest {

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

    private static String cancelledPage(int count, int total) {
        StringBuilder content = new StringBuilder();
        for (int id = 1; id <= count; id++) {
            content.append(id > 1 ? "," : "").append("{\"id\":").append(id)
                    .append(",\"restaurantName\":\"Spice Hub\",\"status\":\"CANCELLED\",\"paymentMethod\":\"UPI\","
                            + "\"totalAmount\":100,\"itemCount\":1,\"createdAt\":\"2026-10-09T05:10:00Z\"}");
        }
        return "{\"content\":[" + content + "],\"totalElements\":" + total + "}";
    }

    private void stubReasons(AtomicInteger running, AtomicInteger peak, String prefix) {
        for (int id = 1; id <= 25; id++) {
            String reason = id <= 13 ? "Ordered twice" : id <= 20 ? "Changed their mind" : "Too slow";
            gateway.on("GET", prefix + "/" + id, call -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                running.decrementAndGet();
                return new StubGateway.Reply(200, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"" + reason + "\"}");
            });
        }
    }

    @Test
    void mockedAdminCancelledReasonsAreCountedInCodeFor20OrdersFourAtATime() {
        gateway.on("GET", "/api/v1/admin/orders", 200, cancelledPage(25, 68));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        stubReasons(running, peak, "/api/v1/admin/orders");

        String result = ToolRunner.call(stack, Fixtures.ADMIN, "admin_list_orders", "{\"status\":\"CANCELLED\"}");

        assertThat(result).contains("\"reasons\":[{\"reason\":\"Ordered twice\",\"count\":13},"
                + "{\"reason\":\"Changed their mind\",\"count\":7}]");
        assertThat(result).contains("\"reasonsBasedOn\":\"20 of 68 cancelled or rejected orders\"", "\"totalFound\":68")
                .contains("among the 20 orders I checked").doesNotContain("Too slow");
        assertThat(gateway.countPathStartingWith("/api/v1/admin/orders/")).isEqualTo(20);
        assertThat(peak.get()).isBetween(2, 4);
        assertThat(gateway.calls().get(0).query()).contains("size=20");
        // Only 10 orders are listed (the default limit) although 20 were read
        assertThat(result.split("\"restaurant\"", -1).length - 1).isEqualTo(10);
    }

    @Test
    void mockedOwnerCancelledReasonsAreCountedToo() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, Responses.MY_RESTAURANTS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders", 200, cancelledPage(12, 12));
        stubReasons(new AtomicInteger(), new AtomicInteger(), "/api/v1/restaurants/mine/7/orders");

        String result = ToolRunner.call(stack, Fixtures.OWNER, "owner_orders", "{\"status\":\"REJECTED\"}");

        assertThat(result).contains("\"reasons\":[{\"reason\":\"Ordered twice\",\"count\":12}]",
                "\"reasonsBasedOn\":\"all 12 cancelled or rejected orders\"");
    }

    @Test
    void mockedAFailedReasonLookupIsLeftOutOfTheCounts() {
        gateway.on("GET", "/api/v1/admin/orders", 200, cancelledPage(3, 3));
        gateway.on("GET", "/api/v1/admin/orders/1", 200, "{\"cancellationReason\":\"A\"}");
        gateway.on("GET", "/api/v1/admin/orders/2", 500, "{}");
        gateway.on("GET", "/api/v1/admin/orders/3", 200, "{\"cancellationReason\":\"A\"}");

        String result = ToolRunner.call(stack, Fixtures.ADMIN, "admin_list_orders", "{\"status\":\"CANCELLED\"}");

        assertThat(result).contains("\"count\":2", "\"reasonsBasedOn\":\"2 of 3 cancelled or rejected orders\"");
    }

    @Test
    void mockedDerivedTotalsAreGivenReadyMade() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);
        gateway.on("GET", "/api/v1/restaurants/mine", 200, Responses.MY_RESTAURANTS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/sales-summary", 200, Responses.SALES_SUMMARY);

        String stats = ToolRunner.call(stack, Fixtures.ADMIN, "admin_order_stats", "{}");
        String sales = ToolRunner.call(stack, Fixtures.OWNER, "owner_sales_summary", "{}");

        assertThat(stats).contains("\"cancelledOrRejected\":3", "\"cancelledOnly\":2", "\"rejectedOnly\":1",
                "\"inProgress\":2");
        assertThat(sales).contains("\"delivered\":3", "\"waitingForDecision\":2", "\"cancelledOrRejected\":0",
                "\"inProgress\":0");
    }

    @Test
    void mockedOwnerWithOneRestaurantNeverHasToSayWhichOne() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, Responses.MY_RESTAURANTS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/deliveries/partners", 200, Responses.PARTNERS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/top-items", 200, Responses.TOP_ITEMS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/menu-items", 200, Responses.OWNER_MENU);

        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "owner_delivery_partners", "{}")).contains("Ravi");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "owner_top_items", "{}")).contains("Chicken Biryani");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "owner_menu", "{}")).contains("Paneer Tikka");
    }

    @Test
    void mockedOwnerWithSeveralRestaurantsIsAskedWhichOneAsANormalResultAndForeignIdsStayRefused() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, """
                [{"id":7,"name":"Spice Hub","status":"ACTIVE"},{"id":9,"name":"Dosa Corner","status":"ACTIVE"}]""");

        String ask = ToolRunner.call(stack, Fixtures.OWNER, "owner_top_items", "{}");
        String foreign = ToolRunner.call(stack, Fixtures.OWNER, "owner_top_items", "{\"restaurantId\":99}");

        assertThat(ask).doesNotStartWith("ERROR").startsWith("NEED_INPUT").contains("Spice Hub (id 7)", "Dosa Corner (id 9)");
        assertThat(foreign).startsWith("ERROR").contains("not one of your restaurants");
        assertThat(gateway.countPathStartingWith("/api/v1/restaurants/mine/")).isZero();
    }

    @Test
    void mockedNearMeUsesTheOnlySavedAddressAndSaysWhichOne() {
        gateway.on("GET", "/api/v1/users/me/addresses", 200, """
                [{"id":5,"type":"HOME","line1":"12 Lake View","city":"Pune","latitude":18.5,"longitude":73.8}]""");
        gateway.on("GET", "/api/v1/search/restaurants", 200, Responses.SEARCH_RESTAURANTS);

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"nearMe\":true}");

        assertThat(result).contains("HOME: 12 Lake View, Pune", "\"distanceKm\":1.2");
        assertThat(gateway.calls().get(1).query()).contains("lat=18.5", "lon=73.8", "radiusKm=5.0");
    }

    @Test
    void mockedNearMeWithSeveralAddressesReturnsTheLabelsAsAQuestion() {
        gateway.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"nearMe\":true}");

        assertThat(result).doesNotStartWith("ERROR").startsWith("NEED_INPUT").contains("HOME: 12 Lake View",
                "WORK: Tech Park", "[no map location]");
        assertThat(gateway.count("GET", "/api/v1/search/restaurants")).isZero();
    }

    @Test
    void mockedNearMeWithoutAddressOrCoordinatesIsClearAndInventsNothing() {
        gateway.on("GET", "/api/v1/users/me/addresses", 200, "[]");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"nearMe\":true}"))
                .startsWith("NEED_INPUT").contains("no saved address", "city or name");

        gateway.on("GET", "/api/v1/users/me/addresses", 200, """
                [{"id":6,"type":"WORK","line1":"Tech Park","city":"Pune","latitude":null,"longitude":null}]""");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"nearMe\":true}"))
                .contains("no map location; I can search by city or name instead");
        assertThat(gateway.count("GET", "/api/v1/search/restaurants")).isZero();
    }
}
