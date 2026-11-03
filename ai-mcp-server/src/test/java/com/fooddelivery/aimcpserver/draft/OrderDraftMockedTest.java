package com.fooddelivery.aimcpserver.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MOCKED: the order the customer is still describing is kept on the server between turns, so a small model that
 * forgets the earlier messages can finish the order with just the new detail. The gateway is a local stub.
 */
class OrderDraftMockedTest {

    private static final String ONE_ADDRESS = """
            [{"id":5,"type":"HOME","line1":"12 Lake View","line2":null,"city":"Pune","state":"MH","postalCode":"411001",
              "latitude":18.5,"longitude":73.8,"defaultAddress":true}]
            """;
    private static final CallerContext OTHER_CUSTOMER = new CallerContext(99, Role.CUSTOMER, "req-other", "token-of-user-99");

    private StubGateway gateway;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
        gateway.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
        gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
        gateway.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        gateway.on("GET", "/api/v1/users/me/addresses", 200, ONE_ADDRESS);
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
    void mockedAMissingPaymentKeepsTheDraftAndTheNextTurnNeedsOnlyThePayment() {
        String first = prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}]}");

        assertThat(first).startsWith("NEED_INPUT").contains("pay");
        OrderDraft saved = stack.draftStore().find(Fixtures.CUSTOMER.userId()).orElseThrow();
        assertThat(saved.restaurant()).isEqualTo("Spice Hub");
        assertThat(saved.items()).containsExactly(new OrderDraft.Item("chicken biryani", 2));
        assertThat(saved.missing()).contains("pay");
        assertThat(stack.draftStore().keptFor(Fixtures.CUSTOMER.userId())).isEqualTo(Duration.ofMinutes(15));
        assertThat(stack.drafts().promptBlock(Fixtures.CUSTOMER.userId()))
                .contains("ORDER IN PROGRESS", "Spice Hub", "2 x chicken biryani", "call prepare_order NOW");

        // The second turn gives ONLY the payment method: restaurant and dishes come from the saved draft
        var run = ToolRunner.newRun();
        String second = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order",
                "{\"paymentMethod\":\"CASH_ON_DELIVERY\"}");

        assertThat(second).contains("PROPOSED_NOT_PLACED");
        assertThat(run.pendingAction().summary().restaurantName()).isEqualTo("Spice Hub");
        assertThat(run.pendingAction().summary().items()).hasSize(1);
        assertThat(run.pendingAction().summary().items().get(0).quantity()).isEqualTo(2);
        assertThat(stack.draftStore().find(Fixtures.CUSTOMER.userId())).isEmpty();
        assertThat(stack.drafts().promptBlock(Fixtures.CUSTOMER.userId())).isEmpty();
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedADraftBelongsToOneCustomerOnly() {
        prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}]}");

        assertThat(stack.drafts().promptBlock(OTHER_CUSTOMER.userId())).isEmpty();
        // Another customer answering "cash on delivery" has no restaurant and no dishes: they are asked, nothing is borrowed
        String other = ToolRunner.call(stack, OTHER_CUSTOMER, "prepare_order", "{\"paymentMethod\":\"CARD\"}");
        assertThat(other).startsWith("NEED_INPUT").contains("which restaurant");
        assertThat(stack.draftStore().find(Fixtures.CUSTOMER.userId())).isPresent();
    }

    @Test
    void mockedNewDetailsReplaceTheSavedOnesAndTheRestIsKept() {
        prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}]}");

        String result = prepare("{\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":3}],\"paymentMethod\":\"UPI\"}");

        assertThat(result).contains("PROPOSED_NOT_PLACED");
        assertThat(gateway.count("PUT", "/api/v1/cart/items/31")).isEqualTo(1);
        assertThat(gateway.calls().stream().filter(c -> c.method().equals("PUT") && c.path().equals("/api/v1/cart/items/31"))
                .map(StubGateway.Call::body).findFirst().orElseThrow()).contains("3");
    }

    @Test
    void mockedAnErrorClearsTheDraftSoTheCustomerStartsAgain() {
        prepare("{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}]}");
        assertThat(stack.draftStore().find(Fixtures.CUSTOMER.userId())).isPresent();
        gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_CLOSED);

        String result = prepare("{\"paymentMethod\":\"UPI\"}");

        assertThat(result).startsWith("ERROR").contains("closed");
        assertThat(stack.draftStore().find(Fixtures.CUSTOMER.userId())).isEmpty();
    }

    @Test
    void mockedNoRestaurantAtAllIsAQuestionNotAGuess() {
        String result = prepare("{\"paymentMethod\":\"UPI\"}");

        assertThat(result).startsWith("NEED_INPUT").contains("which restaurant");
        assertThat(gateway.count("GET", "/api/v1/restaurants")).isZero();
    }

    @Test
    void mockedARestaurantWithoutDishesIsAQuestionAboutTheDishes() {
        String result = prepare("{\"restaurant\":\"Spice Hub\"}");

        assertThat(result).startsWith("NEED_INPUT").contains("which dishes");
        assertThat(stack.drafts().promptBlock(Fixtures.CUSTOMER.userId())).contains("(not said yet)");
    }

    @Test
    void mockedAnOwnersRestaurantsAreInThePromptAndAForeignIdGetsAHint() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, Responses.MY_RESTAURANTS);

        assertThat(stack.promptContext().blockFor(Fixtures.OWNER)).contains("Spice Hub (id 7)")
                .contains("leave restaurantId out");
        assertThat(stack.promptContext().blockFor(Fixtures.ADMIN)).isEmpty();

        String refused = ToolRunner.call(stack, Fixtures.OWNER, "owner_orders", "{\"restaurantId\":73}");
        assertThat(refused).contains("not one of your restaurants").contains("Spice Hub (id 7)")
                .contains("without restaurantId");
        assertThat(gateway.count("GET", "/api/v1/restaurants/mine/73/orders")).isZero();
    }

    @Test
    void mockedAStoreThatFailsNeverBreaksTheConversation() {
        OrderDraftService broken = new OrderDraftService(new DraftStore() {
            @Override
            public void save(long userId, OrderDraft draft, Duration keepFor) {
                throw new DraftStoreException("redis down", null);
            }

            @Override
            public java.util.Optional<OrderDraft> find(long userId) {
                throw new DraftStoreException("redis down", null);
            }

            @Override
            public void delete(long userId) {
                throw new DraftStoreException("redis down", null);
            }
        }, Fixtures.clock());

        assertThat(broken.promptBlock(1)).isEmpty();
        broken.remember(1, new OrderDraft("x", List.of(), null, null, null, null, null, null), "q");
        broken.forget(1);
        assertThat(broken.merge(1, "Spice Hub", List.of(), null, null, null, null).restaurant()).isEqualTo("Spice Hub");
    }
}
