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

/** MOCKED: the customer tools against a stubbed gateway. */
class CustomerToolsMockedTest {

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        gateway.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
        gateway.on("GET", "/api/v1/search/restaurants", 200, Responses.SEARCH_RESTAURANTS);
        gateway.on("GET", "/api/v1/search/foods", 200, Responses.SEARCH_FOODS);
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private StubGateway.Call searchCall() {
        return gateway.calls().stream().filter(c -> c.path().equals("/api/v1/search/restaurants")).findFirst().orElseThrow();
    }

    @Test
    void mockedSearchRestaurantsLocationComesOnlyFromTheVerifiedSavedAddress() {
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "search_restaurants",
                "{\"query\":\"biryani\",\"addressId\":5,\"radiusKm\":3}");

        assertThat(searchCall().query()).contains("q=biryani", "lat=18.5204", "lon=73.8567", "radiusKm=3.0");
        assertThat(result).contains("\"distanceKm\":1.2", "within 3.0 km of the saved address");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.items().get(0).link().path()).isEqualTo("/restaurants/7");
    }

    @Test
    void mockedSearchRestaurantsRefusesAnAddressThatIsNotTheCustomersOwn() {
        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"addressId\":999}");

        assertThat(result).startsWith("ERROR").contains("not one of the customer's saved addresses");
        assertThat(gateway.count("GET", "/api/v1/search/restaurants")).isZero();
    }

    @Test
    void mockedSearchRestaurantsRefusesAnAddressWithoutMapLocationAndRadiusWithoutAddress() {
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"addressId\":6}"))
                .contains("no map location");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"radiusKm\":3}"))
                .contains("radiusKm needs nearMe=true or an addressId");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"addressId\":5,\"radiusKm\":30}"))
                .contains("radiusKm must be between 1 and 25");

        assertThat(gateway.count("GET", "/api/v1/search/restaurants")).isZero();
    }

    @Test
    void mockedSearchRestaurantsWithoutAddressSearchesByTextAndCityOnly() {
        ToolRunner.call(stack, Fixtures.CUSTOMER, "search_restaurants", "{\"query\":\"a&status=PENDING\",\"city\":\"Pune\"}");

        // The "&" is encoded: it cannot add a second parameter
        assertThat(searchCall().query()).contains("q=a%26status%3DPENDING", "city=Pune").doesNotContain("lat=");
        assertThat(gateway.calls().stream().noneMatch(c -> c.path().equals("/api/v1/users/me/addresses"))).isTrue();
    }

    @Test
    void mockedSearchFoodsMapsFiltersAndTheRealShape() {
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "search_foods",
                "{\"query\":\"biryani\",\"foodType\":\"non_veg\",\"minPrice\":100,\"maxPrice\":300,"
                        + "\"sort\":\"PRICE_LOW_TO_HIGH\",\"restaurantId\":7}");

        StubGateway.Call call = gateway.calls().get(0);
        assertThat(call.query()).contains("foodType=NON_VEG", "minPrice=100.0", "maxPrice=300.0",
                "sort=PRICE_LOW_TO_HIGH", "restaurantId=7");
        assertThat(result).contains("Chicken Biryani", "\"menuItemId\":31", "\"restaurantId\":7");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.entityType()).isEqualTo("FOOD");
        assertThat(card.items().get(0).imageUrl()).isEqualTo("https://img.example/b.png");
    }

    @Test
    void mockedSearchFoodsRejectsInvalidArguments() {
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_foods", "{\"foodType\":\"MEAT\"}"))
                .contains("foodType must be one of");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_foods", "{\"minPrice\":500,\"maxPrice\":100}"))
                .contains("minPrice must not be greater");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_foods", "{\"limit\":0}")).contains("limit must be");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "search_foods", "{\"restaurantId\":0}"))
                .contains("restaurantId must be a positive");

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedRestaurantMenuShowsAvailabilityAndCategories() {
        gateway.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "restaurant_menu", "{\"restaurantId\":7}");

        assertThat(result).contains("\"restaurant\":\"Spice Hub\"", "\"itemCount\":2", "\"menuItemId\":31",
                "\"available\":false");
        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.items().get(1).badges()).contains("Unavailable");
    }

    @Test
    void mockedMyAddressesKeepsCoordinatesAwayFromTheModel() {
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "my_addresses", "{}");

        assertThat(result).contains("\"id\":5", "12 Lake View", "\"isDefault\":true", "\"hasMapLocation\":true",
                "\"hasMapLocation\":false");
        assertThat(result).doesNotContain("18.5204").doesNotContain("latitude");
        assertThat(run.cards()).isEmpty();
    }

    @Test
    void mockedMyOrdersListsOwnOrdersWithCustomerStorefrontLinks() {
        gateway.on("GET", "/api/v1/orders", 200, Responses.MY_ORDERS);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "my_orders", "{\"status\":\"PLACED\"}");

        assertThat(result).contains("\"totalOrders\":1", "\"id\":777", "\"status\":\"PLACED\"");
        assertThat(gateway.calls().get(0).query()).contains("status=PLACED", "size=5");
        assertThat(((Card.Entities) run.cards().get(0)).items().get(0).link().path()).isEqualTo("/orders/777");
        assertThat(gateway.calls().get(0).header("Authorization")).isEqualTo("Bearer token-of-user-1");
    }

    @Test
    void mockedMyOrderStatusCombinesTheOrderAndItsDeliveryWithoutThePartnersPhone() {
        gateway.on("GET", "/api/v1/orders/777", 200, Responses.ORDER_DETAIL);
        gateway.on("GET", "/api/v1/delivery/orders/777", 200, Responses.TRACKING);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "my_order_status", "{\"orderId\":777}");

        assertThat(result).contains("\"status\":\"PREPARING\"", "Chicken Biryani x2", "\"total\":\"460\"",
                "\"partner\":\"Ravi\"", "ASSIGNED");
        assertThat(result).doesNotContain("9999999999");
    }

    @Test
    void mockedMyOrderStatusADeliveryThatDoesNotExistYetIsNotAnError() {
        gateway.on("GET", "/api/v1/orders/777", 200, Responses.ORDER_DETAIL);
        gateway.on("GET", "/api/v1/delivery/orders/777", 404, "{\"detail\":\"No delivery yet\"}");

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "my_order_status", "{\"orderId\":777}");

        assertThat(result).doesNotStartWith("ERROR").contains("not started yet");
    }

    @Test
    void mockedMyOrderStatusSomeoneElsesOrderIsTheServicesNotFound() {
        gateway.on("GET", "/api/v1/orders/5", 404, "{\"title\":\"Resource not found\",\"detail\":\"Order 5 not found\"}");

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "my_order_status", "{\"orderId\":5}");

        assertThat(result).startsWith("ERROR").contains("Not found");
    }

    @Test
    void mockedCustomerToolsAreRefusedForAdminsAndOwners() {
        for (var caller : new com.fooddelivery.aimcpserver.security.CallerContext[] {Fixtures.ADMIN, Fixtures.OWNER}) {
            for (String tool : new String[] {"search_restaurants", "search_foods", "restaurant_menu", "my_addresses",
                    "my_orders", "my_order_status", "prepare_order"}) {
                assertThat(ToolRunner.call(stack, caller, tool, "{}")).contains("not available for your role");
            }
        }

        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedOffersCustomersGetAStorefrontLink() {
        gateway.on("GET", "/api/v1/coupons", 200, Responses.OFFERS);
        var run = ToolRunner.newRun();

        ToolRunner.call(stack, Fixtures.CUSTOMER, run, "current_offers", "{}");

        Card.Entities card = (Card.Entities) run.cards().get(0);
        assertThat(card.entityType()).isEqualTo("OFFER");
        assertThat(card.items().get(0).id()).isEqualTo("WELCOME100");
        assertThat(card.items().get(0).link().path()).isEqualTo("/offers");
    }
}
