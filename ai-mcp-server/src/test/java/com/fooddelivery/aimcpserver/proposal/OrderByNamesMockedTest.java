package com.fooddelivery.aimcpserver.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MOCKED: prepare_order resolves restaurant, dishes and address from the customer's words, in code. */
class OrderByNamesMockedTest {

    private static final String ONE_ADDRESS = """
            [{"id":5,"type":"HOME","line1":"12 Lake View","line2":null,"city":"Pune","state":"MH","postalCode":"411001",
              "latitude":18.5,"longitude":73.8,"defaultAddress":true}]
            """;

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
        gateway.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
        gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
        gateway.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        gateway.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
        gateway.on("PUT", "/api/v1/cart/items/31", 200, Responses.PRICED_CART);
        gateway.on("DELETE", "/api/v1/cart", 204, null);
        List<String> carts = new ArrayList<>(List.of(Responses.EMPTY_CART, Responses.PRICED_CART));
        gateway.on("GET", "/api/v1/cart", call -> new StubGateway.Reply(200,
                carts.size() > 1 ? carts.remove(0) : carts.get(0)));
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private String prepare(String args) {
        return ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order", args);
    }

    @Test
    void mockedNamesAreResolvedIgnoringCaseAndAccentsAndANamedAddress() {
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order",
                "{\"restaurant\":\"SPICE hub\",\"items\":[{\"dish\":\"Chicken  BIRYANI\",\"quantity\":2}],"
                        + "\"address\":\"home\",\"paymentMethod\":\"cash_on_delivery\"}");

        assertThat(result).contains("PROPOSED_NOT_PLACED");
        PendingAction pending = run.pendingAction();
        assertThat(pending.summary().restaurantName()).isEqualTo("Spice Hub");
        assertThat(pending.summary().deliveryAddress()).startsWith("12 Lake View");
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedAccentInsensitiveRestaurantMatchAndExtraWordsFromTheUser() {
        assertThat(prepare("{\"restaurant\":\"cafe spice garden\",\"items\":[{\"dish\":\"x\",\"quantity\":1}],"
                + "\"paymentMethod\":\"UPI\"}")).doesNotContain("No restaurant matches");
        // The user added words that are not part of the name: the name inside the words still matches
        gateway.on("GET", "/api/v1/restaurants/8", 404, "{\"detail\":\"closed\"}");
        assertThat(prepare("{\"restaurant\":\"the Spice Hub place\",\"items\":[{\"dish\":\"chicken biryani\","
                + "\"quantity\":1}],\"address\":\"5\",\"paymentMethod\":\"UPI\"}")).contains("PROPOSED_NOT_PLACED");
    }

    @Test
    void mockedSeveralMatchingRestaurantsBecomeAQuestionWithTheNames() {
        String result = prepare("{\"restaurant\":\"spice\",\"items\":[{\"dish\":\"x\",\"quantity\":1}]}");

        assertThat(result).startsWith("AMBIGUOUS").contains("Spice Hub (id 7)").contains("Spice Garden (id 8)");
        assertThat(result).doesNotStartWith("ERROR");
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedUnknownRestaurantIsAnErrorWithoutListingAnything() {
        gateway.on("GET", "/api/v1/restaurants", 200, "{\"content\":[]}");

        String result = prepare("{\"restaurant\":\"Nowhere Grill\",\"items\":[{\"dish\":\"x\",\"quantity\":1}]}");

        assertThat(result).startsWith("ERROR").contains("No restaurant matches \"Nowhere Grill\"");
    }

    @Test
    void mockedAmbiguousDishIsAQuestionAndNumericIdsStillWork() {
        gateway.on("GET", "/api/v1/restaurants/7/menu", 200, """
                {"restaurantId":7,"restaurantName":"Spice Hub","sections":[{"categoryId":1,"categoryName":"B","items":[
                 {"id":31,"name":"Chicken Biryani","price":200,"foodType":"NON_VEG","available":true},
                 {"id":34,"name":"Chicken Biryani Special","price":250,"foodType":"NON_VEG","available":true},
                 {"id":35,"name":"Chicken Tikka","price":250,"foodType":"NON_VEG","available":true}]}]}
                """);

        String ambiguous = prepare("{\"restaurant\":\"7\",\"items\":[{\"dish\":\"chicken\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}");
        String exactWins = prepare("{\"restaurant\":\"7\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}");
        String byId = prepare("{\"restaurant\":\"7\",\"items\":[{\"dish\":\"31\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}");

        assertThat(ambiguous).startsWith("AMBIGUOUS").contains("Chicken Biryani (id 31)").contains("Chicken Tikka (id 35)");
        assertThat(exactWins).contains("PROPOSED_NOT_PLACED");
        assertThat(byId).contains("PROPOSED_NOT_PLACED");
    }

    @Test
    void mockedUnavailableOrUnknownDishesAreClearErrors() {
        assertThat(prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"veg biryani\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}")).startsWith("ERROR").contains("not available");
        assertThat(prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"sushi\",\"quantity\":1}],"
                + "\"address\":\"5\",\"paymentMethod\":\"UPI\"}")).startsWith("ERROR").contains("No dish matching");
    }

    @Test
    void mockedTheOnlySavedAddressIsUsedAndShownWithoutAskingForAnId() {
        gateway.on("GET", "/api/v1/users/me/addresses", 200, ONE_ADDRESS);
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order",
                "{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}],"
                        + "\"paymentMethod\":\"CASH_ON_DELIVERY\"}");

        assertThat(result).contains("PROPOSED_NOT_PLACED", "12 Lake View");
        assertThat(run.pendingAction().summary().deliveryAddress()).contains("12 Lake View");
    }

    @Test
    void mockedSeveralAddressesWithoutAChoiceAreAQuestionWithLabelsAndNoOrder() {
        String result = prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}],"
                + "\"paymentMethod\":\"CASH_ON_DELIVERY\"}");

        assertThat(result).startsWith("NEED_INPUT").contains("HOME: 12 Lake View", "WORK: Tech Park")
                .contains("never ask for ids");
        assertThat(gateway.count("PUT", "/api/v1/cart/items/31")).isZero();
    }

    @Test
    void mockedNoSavedAddressPointsToTheAddressesPage() {
        gateway.on("GET", "/api/v1/users/me/addresses", 200, "[]");

        String result = prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}],"
                + "\"paymentMethod\":\"UPI\"}");

        assertThat(result).startsWith("NEED_INPUT").contains("no saved address").contains("/account/addresses");
    }

    @Test
    void mockedMissingPaymentAndAddressAreAskedTogetherWithTheOptions() {
        String result = prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}]}");

        assertThat(result).startsWith("NEED_INPUT").contains("saved address").contains("how the customer wants to pay")
                .contains("CASH_ON_DELIVERY", "CARD", "UPI", "WALLET");
    }

    @Test
    void mockedTheToolDescriptionTellsTheModelToRelayQuestionsAndNeverAskForIds() {
        var tool = stack.catalog().all().stream()
                .filter(t -> t.getToolDefinition().name().equals("prepare_order")).findFirst().orElseThrow();

        assertThat(tool.getToolDefinition().description()).contains("NEED_INPUT", "AMBIGUOUS", "never look up or ask for ids");
        assertThat(tool.getToolDefinition().inputSchema()).contains("restaurant", "dish", "paymentMethod")
                .doesNotContain("addressId").doesNotContain("menuItemId");
    }
}
