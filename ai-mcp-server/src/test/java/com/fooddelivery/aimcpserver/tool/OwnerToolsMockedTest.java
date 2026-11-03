package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MOCKED: the restaurant-owner tools against a stubbed gateway. */
class OwnerToolsMockedTest {

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        gateway.on("GET", "/api/v1/restaurants/mine", 200, Responses.MY_RESTAURANTS);
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    @Test
    void mockedMyRestaurantsListsIdsAndLinksOnlyActiveRestaurantsToTheirPublicPage() {
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_my_restaurants", "{}");

        assertThat(result).contains("\"id\":7", "Spice Hub", "\"status\":\"ACTIVE\"");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.items().get(0).link().path()).isEqualTo("/restaurants/7");
        assertThat(card.items().get(0).badges()).contains("ACTIVE", "North Indian");
    }

    @Test
    void mockedForeignRestaurantIdIsRefusedWithoutCallingTheBusinessApi() {
        for (String tool : new String[] {"owner_sales_summary", "owner_orders", "owner_top_items",
                "owner_delivery_partners", "owner_menu"}) {
            assertThat(ToolRunner.call(stack, Fixtures.OWNER, tool, "{\"restaurantId\":99}"))
                    .startsWith("ERROR").contains("not one of your restaurants").contains("Spice Hub (id 7)");
        }

        // Only the owner's own restaurant list was read; no orders, sales, menu or delivery endpoint was called
        assertThat(gateway.calls()).allMatch(call -> call.path().equals("/api/v1/restaurants/mine"));
        assertThat(gateway.countPathStartingWith("/api/v1/restaurants/mine/")).isZero();
    }

    @Test
    void mockedSalesSummaryMapsTheRealShape() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/sales-summary", 200, Responses.SALES_SUMMARY);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_sales_summary", "{\"restaurantId\":7}");

        assertThat(result).contains("\"totalOrders\":5", "\"deliveredOrdersValue\":\"900\"", "\"DELIVERED\":3");
        assertThat(gateway.calls().get(1).query()).contains("fromDate=2026-10-09", "toDate=2026-10-09");
        assertThat(gateway.calls().get(1).header("Authorization")).isEqualTo("Bearer token-of-user-3");
        assertThat(((Card.Stats) run.cards().get(0)).stats()).hasSize(3);
    }

    @Test
    void mockedOrdersPendingOrdersAreOldestFirstAndCarryNoLinkBecauseOwnersHaveNoOrderPages() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders", 200, Responses.ORDERS_PAGE);
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/101", 200, Responses.ORDER_CANCELLED_DETAIL);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_orders",
                "{\"restaurantId\":7,\"status\":\"PLACED\"}");

        assertThat(gateway.calls().get(1).query()).contains("status=PLACED", "sort=createdAt%2Casc");
        assertThat(result).doesNotContain("reasons");
        assertThat(((Card.Entities) run.cards().get(0)).items().get(0).link()).isNull();
    }

    @Test
    void mockedTopItemsAndPartnersUseTheNewEndpointContractWithFromAndToParameters() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/top-items", 200, Responses.TOP_ITEMS);
        gateway.on("GET", "/api/v1/restaurants/mine/7/deliveries/partners", 200, Responses.PARTNERS);
        var run = ToolRunner.newRun();

        String items = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_top_items",
                "{\"restaurantId\":7,\"fromDate\":\"2026-10-03\",\"toDate\":\"2026-10-09\",\"limit\":2}");
        String partners = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_delivery_partners",
                "{\"restaurantId\":7,\"fromDate\":\"2026-10-03\",\"toDate\":\"2026-10-09\"}");

        assertThat(items).contains("Chicken Biryani", "\"quantity\":40", "\"revenue\":\"8000\"");
        assertThat(partners).contains("Ravi", "\"delivered\":14", "\"averageMinutes\":\"28.5\"");
        assertThat(gateway.calls().stream().filter(c -> c.path().endsWith("/top-items")).findFirst().orElseThrow().query())
                .contains("from=2026-10-03", "to=2026-10-09", "limit=2");
        assertThat(gateway.calls().stream().filter(c -> c.path().endsWith("/partners")).findFirst().orElseThrow().query())
                .contains("from=2026-10-03", "to=2026-10-09", "limit=5");
        assertThat(run.cards()).hasSize(2).allMatch(card -> card instanceof Card.Table);
    }

    @Test
    void mockedMenuCanListOnlyUnavailableItems() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/menu-items", 200, Responses.OWNER_MENU);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_menu",
                "{\"restaurantId\":7,\"onlyUnavailable\":true}");

        assertThat(result).contains("Paneer Tikka", "\"available\":false").doesNotContain("Chicken Biryani");
        assertThat(((Card.Entities) run.cards().get(0)).items()).hasSize(1);
    }

    @Test
    void mockedOffersOwnerMayOnlyAskForOwnRestaurantsAndGetsNoStorefrontLink() {
        gateway.on("GET", "/api/v1/coupons", 200, Responses.OFFERS);
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "current_offers", "{\"restaurantId\":99}"))
                .contains("not one of your restaurants");
        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "current_offers", "{\"restaurantId\":7}");

        assertThat(result).contains("WELCOME100", "\"discount\":\"100.00 off\"");
        assertThat(gateway.calls().stream().filter(c -> c.path().equals("/api/v1/coupons")).findFirst().orElseThrow().query())
                .isEqualTo("restaurantId=7");
        assertThat(((Card.Entities) run.cards().get(0)).items().get(0).link()).isNull();
    }

    @Test
    void mockedOwnerToolsAreRefusedForCustomersAndAdmins() {
        for (var caller : new com.fooddelivery.aimcpserver.security.CallerContext[] {Fixtures.CUSTOMER, Fixtures.ADMIN}) {
            assertThat(ToolRunner.call(stack, caller, "owner_my_restaurants", "{}")).contains("not available for your role");
            assertThat(ToolRunner.call(stack, caller, "owner_orders", "{\"restaurantId\":7}"))
                    .contains("not available for your role");
        }

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedOwnerTheServicesOwnRefusalIsReportedClearly() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/sales-summary", 403,
                "{\"title\":\"Forbidden\",\"detail\":\"Not your restaurant\"}");

        String result = ToolRunner.call(stack, Fixtures.OWNER, "owner_sales_summary", "{\"restaurantId\":7}");

        assertThat(result).startsWith("ERROR").contains("not allowed for you");
    }
}
