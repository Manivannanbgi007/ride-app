package com.fooddelivery.aimcpserver.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.exception.ApiException;
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
import org.springframework.http.HttpStatus;

/** MOCKED: the order proposal lifecycle (prepare, confirm exactly once, cancel, expiry) against a stubbed gateway. */
class ProposalServiceMockedTest {

    private static final String PREPARE_ARGS = """
            {"restaurant":"Spice Hub","items":[{"dish":"chicken biryani","quantity":2}],"address":"5",
             "paymentMethod":"cash_on_delivery"}
            """;

    private StubGateway gateway;
    private InMemoryProposalStore store;
    private Fixtures.MutableClock clock;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        store = new InMemoryProposalStore();
        clock = new Fixtures.MutableClock(Fixtures.NOW);
        stack = Fixtures.stack(gateway.url(), clock, store);
        gateway.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
        gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
        gateway.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
        gateway.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
        gateway.on("PUT", "/api/v1/cart/items/31", 200, Responses.PRICED_CART);
        gateway.on("DELETE", "/api/v1/cart", 204, null);
        gateway.on("POST", "/api/v1/orders", 201, Responses.ORDER_CREATED);
        priceTheCart();
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    /** Prepares through the tool, like the model would, and returns the pending action. */
    private PendingAction prepare() {
        var run = ToolRunner.newRun();
        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order", PREPARE_ARGS);
        assertThat(result).as("prepare_order result").doesNotStartWith("ERROR");
        return run.pendingAction();
    }

    private void priceTheCart() {
        // First read: empty (decides to price). Second read: the priced cart.
        List<String> answers = new ArrayList<>(List.of(Responses.EMPTY_CART, Responses.PRICED_CART));
        gateway.on("GET", "/api/v1/cart", call -> new StubGateway.Reply(200,
                answers.size() > 1 ? answers.remove(0) : answers.get(0)));
    }

    @Test
    void mockedPrepareOfAnEmptyCartPricesItThroughTheCartAndAlwaysEmptiesItAgain() {
        priceTheCart();

        var run = ToolRunner.newRun();
        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order", PREPARE_ARGS);

        PendingAction pending = run.pendingAction();
        assertThat(pending.kind()).isEqualTo("PLACE_ORDER");
        assertThat(pending.summary().restaurantName()).isEqualTo("Spice Hub");
        assertThat(pending.summary().deliveryAddress()).isEqualTo("12 Lake View, Apt 4, Pune, 411001");
        assertThat(pending.summary().total()).isEqualByComparingTo("460.00");
        assertThat(pending.summary().deliveryFee()).isEqualByComparingTo("40.00");
        assertThat(pending.summary().tax()).isEqualByComparingTo("20.00");
        assertThat(pending.summary().currency()).isEqualTo("INR");
        assertThat(pending.summary().paymentMethod()).isEqualTo("CASH_ON_DELIVERY");
        assertThat(pending.summary().items()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Chicken Biryani");
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.lineTotal()).isEqualByComparingTo("400.00");
        });
        assertThat(pending.expiresAt()).isEqualTo(Fixtures.NOW.plus(Duration.ofMinutes(10)));
        assertThat(result).contains("PROPOSED_NOT_PLACED", pending.actionId(), "NOTHING IS ORDERED YET");
        // No order was created, and the cart was emptied after pricing (also the order of the calls)
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
        assertThat(gateway.count("DELETE", "/api/v1/cart")).isEqualTo(1);
        List<String> sequence = gateway.calls().stream().filter(c -> c.path().startsWith("/api/v1/cart"))
                .map(c -> c.method() + " " + c.path()).toList();
        assertThat(sequence).containsExactly("GET /api/v1/cart", "PUT /api/v1/cart/items/31", "GET /api/v1/cart",
                "DELETE /api/v1/cart");
        Proposal stored = store.find(pending.actionId()).orElseThrow();
        assertThat(stored.status()).isEqualTo(Proposal.Status.NEW);
        assertThat(stored.userId()).isEqualTo(1);
    }

    @Test
    void mockedPrepareWithAFilledCartDoesNotTouchItAndWarnsInstead() {
        gateway.on("GET", "/api/v1/cart", 200, Responses.FULL_CART);

        var run = ToolRunner.newRun();
        String withCoupon = PREPARE_ARGS.replace("}\n", ",\"couponCode\":\"save10\"}\n");
        ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order", withCoupon);

        PendingAction.Summary summary = run.pendingAction().summary();
        assertThat(summary.total()).isNull();
        assertThat(summary.deliveryFee()).isNull();
        assertThat(summary.itemsTotal()).isEqualByComparingTo("400.00");
        assertThat(summary.warnings()).anyMatch(w -> w.contains("will be replaced")).anyMatch(w -> w.contains("SAVE10"));
        assertThat(gateway.count("PUT", "/api/v1/cart/items/31")).isZero();
        assertThat(gateway.count("DELETE", "/api/v1/cart")).isZero();
    }

    @Test
    void mockedPrepareACouponTheCartRefusesStopsThePreparationAndTheCartIsStillEmptied() {
        gateway.on("PUT", "/api/v1/cart/coupon", 422, "{\"detail\":\"Minimum order amount is 500\"}");

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("}\n", ",\"couponCode\":\"BIG500\"}\n"));

        assertThat(result).startsWith("ERROR").contains("Coupon BIG500 cannot be used").contains("Minimum order amount");
        assertThat(gateway.count("DELETE", "/api/v1/cart")).isEqualTo(1);
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedPrepareValidatesEverythingBeforeStoringAnything() {
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("chicken biryani", "pizza"))).contains("No dish matching");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("chicken biryani", "veg biryani"))).contains("Veg Biryani is not available");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("\"address\":\"5\"", "\"address\":\"99\""))).contains("NEED_INPUT");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("cash_on_delivery", "bitcoin"))).contains("paymentMethod must be one of");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("\"quantity\":2", "\"quantity\":21"))).contains("quantity must be between 1 and 20");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("}\n", ",\"couponCode\":\"a b\"}\n"))).contains("couponCode must be");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order",
                PREPARE_ARGS.replace("[{\"dish\":\"chicken biryani\",\"quantity\":2}]", "[]"))).contains("which dishes");
        gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_CLOSED);
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order", PREPARE_ARGS)).contains("is closed right now");
        gateway.on("GET", "/api/v1/restaurants/7", 404, "{\"detail\":\"Restaurant 7 not found\"}");
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order", PREPARE_ARGS.replace("Spice Hub", "7")))
                .contains("Not found");

        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
        assertThat(gateway.count("PUT", "/api/v1/cart/items/31")).isZero();
    }

    @Test
    void mockedPrepareRefusesMoreThan20LinesAndMergesDuplicates() {
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i <= 21; i++) {
            lines.append(i > 1 ? "," : "").append("{\"dish\":\"dish ").append(i).append("\",\"quantity\":1}");
        }
        String tooMany = "{\"restaurant\":\"Spice Hub\",\"items\":[" + lines + "],\"address\":\"5\",\"paymentMethod\":\"UPI\"}";

        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order", tooMany)).contains("at most 20 different items");

        String duplicates = "{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":12},"
                + "{\"dish\":\"chicken biryani\",\"quantity\":12}],\"address\":\"5\",\"paymentMethod\":\"UPI\"}";
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "prepare_order", duplicates)).contains("At most 20 of one item");
    }

    @Test
    void mockedPrepareAnOnlinePaymentIsMentionedAsAWarning() {
        var run = ToolRunner.newRun();

        ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order", PREPARE_ARGS.replace("cash_on_delivery", "UPI"));

        assertThat(run.pendingAction().summary().warnings()).anyMatch(w -> w.contains("online payment"));
    }

    @Test
    void mockedConfirmCreatesExactlyOneOrderWithTheProposalIdAsIdempotencyKey() {
        priceTheCart();
        PendingAction pending = prepare();

        ConfirmResponse first = stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId());
        ConfirmResponse second = stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId());

        assertThat(first.status()).isEqualTo(ConfirmResponse.Status.CREATED);
        assertThat(first.order().id()).isEqualTo(777);
        assertThat(first.order().status()).isEqualTo("PLACED");
        assertThat(first.order().total()).isEqualByComparingTo("460.00");
        assertThat(first.order().currency()).isEqualTo("INR");
        assertThat(first.link().path()).isEqualTo("/orders/777");
        assertThat(first.link().label()).isEqualTo("View order");
        assertThat(second.status()).isEqualTo(ConfirmResponse.Status.ALREADY_CREATED);
        assertThat(second.order().id()).isEqualTo(777);
        assertThat(gateway.count("POST", "/api/v1/orders")).isEqualTo(1);
        StubGateway.Call post = gateway.calls().stream().filter(c -> c.method().equals("POST")).findFirst().orElseThrow();
        assertThat(post.header("Idempotency-Key")).isEqualTo(pending.actionId());
        assertThat(post.body()).contains("\"addressId\":5", "CASH_ON_DELIVERY");
        assertThat(post.header("Authorization")).isEqualTo("Bearer token-of-user-1");
        assertThat(store.find(pending.actionId()).orElseThrow().status()).isEqualTo(Proposal.Status.CONFIRMED);
    }

    @Test
    void mockedConfirmConcurrentDoubleClickStillCreatesOneOrder() throws Exception {
        priceTheCart();
        PendingAction pending = prepare();
        AtomicInteger posts = new AtomicInteger();
        gateway.on("POST", "/api/v1/orders", call -> {
            posts.incrementAndGet();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new StubGateway.Reply(201, Responses.ORDER_CREATED);
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<ConfirmResponse>> clicks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            clicks.add(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()));
        }

        List<Future<ConfirmResponse>> results = pool.invokeAll(clicks);
        pool.shutdown();

        List<ConfirmResponse.Status> statuses = new ArrayList<>();
        for (Future<ConfirmResponse> future : results) {
            ConfirmResponse response = future.get();
            assertThat(response.order().id()).isEqualTo(777);
            statuses.add(response.status());
        }
        assertThat(posts.get()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == ConfirmResponse.Status.CREATED)).hasSize(1);
        assertThat(statuses.stream().filter(s -> s == ConfirmResponse.Status.ALREADY_CREATED)).hasSize(7);
    }

    @Test
    void mockedConfirmAnExpiredProposalIs410AndNothingIsOrdered() {
        PendingAction pending = prepare();
        clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedConfirmUnknownMalformedAndForeignProposalsAreRefused() {
        PendingAction pending = prepare();
        CallerContext stranger = new CallerContext(99, Role.CUSTOMER, "r", "token-of-user-99");

        assertThatThrownBy(() -> stack.proposals().confirm(stranger, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, java.util.UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, "../../etc/passwd"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> stack.proposals().cancel(stranger, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedCancelACancelledProposalCannotBeConfirmedAndCancellingTwiceIsFine() {
        PendingAction pending = prepare();

        stack.proposals().cancel(Fixtures.CUSTOMER, pending.actionId());
        stack.proposals().cancel(Fixtures.CUSTOMER, pending.actionId());

        assertThat(store.find(pending.actionId()).orElseThrow().status()).isEqualTo(Proposal.Status.CANCELLED);
        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(gateway.count("POST", "/api/v1/orders")).isZero();
    }

    @Test
    void mockedCancelAfterTheOrderWasPlacedItIsAConflict() {
        PendingAction pending = prepare();
        stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId());

        assertThatThrownBy(() -> stack.proposals().cancel(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void mockedConfirmATransientFailureKeepsTheProposalUsable() {
        PendingAction pending = prepare();
        List<Integer> answers = new ArrayList<>(List.of(503));
        gateway.on("POST", "/api/v1/orders", call -> answers.isEmpty()
                ? new StubGateway.Reply(201, Responses.ORDER_CREATED) : new StubGateway.Reply(answers.remove(0), "{}"));

        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("press Confirm again");
                });
        assertThat(store.find(pending.actionId()).orElseThrow().status()).isEqualTo(Proposal.Status.NEW);

        ConfirmResponse retry = stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId());

        assertThat(retry.status()).isEqualTo(ConfirmResponse.Status.CREATED);
    }

    @Test
    void mockedConfirmARefusalByTheOrderApiIsReturnedWithItsMessageAndEndsTheProposal() {
        PendingAction pending = prepare();
        gateway.on("POST", "/api/v1/orders", 422, "{\"detail\":\"Restaurant is closed\"}");

        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getMessage()).isEqualTo("Restaurant is closed");
                });
        assertThat(store.find(pending.actionId()).orElseThrow().status()).isEqualTo(Proposal.Status.CANCELLED);
        assertThatThrownBy(() -> stack.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void mockedRedisDownPrepareSaysSoAndConfirmIs503() {
        PendingAction pending = prepare();
        ProposalStore broken = new ProposalStore() {
            @Override
            public void save(Proposal proposal, Duration keepFor) {
                throw new ProposalStoreException("down", null);
            }

            @Override
            public java.util.Optional<Proposal> find(String id) {
                throw new ProposalStoreException("down", null);
            }

            @Override
            public boolean tryLock(String id, Duration lockFor) {
                throw new ProposalStoreException("down", null);
            }

            @Override
            public void unlock(String id) {
                throw new ProposalStoreException("down", null);
            }
        };
        Fixtures.Stack down = Fixtures.stack(gateway.url(), clock, broken);

        assertThat(ToolRunner.call(down, Fixtures.CUSTOMER, "prepare_order", PREPARE_ARGS)).startsWith("ERROR")
                .contains("could not be prepared right now");
        assertThatThrownBy(() -> down.proposals().confirm(Fixtures.CUSTOMER, pending.actionId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
