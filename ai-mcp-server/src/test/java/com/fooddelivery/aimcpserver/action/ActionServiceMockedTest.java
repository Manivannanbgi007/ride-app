package com.fooddelivery.aimcpserver.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryActionStore;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * MOCKED: the confirm step of every non-order change. Nothing is called before Confirm; Confirm calls the business API
 * once, as the user, and only for the user (and role) who prepared it.
 */
class ActionServiceMockedTest {

    private static final String PICKUP = "/api/v1/delivery/deliveries/mine/31/pickup";
    private static final String DELIVER = "/api/v1/delivery/deliveries/mine/31/deliver";
    private static final String DELIVERY_OK = "{\"id\":31,\"status\":\"PICKED_UP\"}";

    private StubGateway gateway;
    private Fixtures.MutableClock clock;
    private InMemoryActionStore store;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        clock = new Fixtures.MutableClock(Fixtures.NOW);
        store = new InMemoryActionStore();
        stack = Fixtures.stack(gateway.url(), clock, new InMemoryProposalStore(), store);
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private static ActionService.Plan pickUp() {
        return new ActionService.Plan(ActionKind.MARK_PICKED_UP, null, 31, ActionBody.NONE,
                new PendingAction.Details("Picked up: delivery #31", List.of(new PendingAction.Row("Cash", "Paid online")),
                        "Confirm only once you have the food.", "Picked up"),
                "Delivery #31 is marked picked up.", new Link("Open delivery", "/partner/deliveries/31"));
    }

    @Test
    void mockedProposingStoresTheChangeForThisUserAndCallsNothing() {
        PendingAction pending = stack.actions().propose(Fixtures.PARTNER, pickUp());

        assertThat(pending.actionId()).startsWith("act_");
        assertThat(pending.kind()).isEqualTo("MARK_PICKED_UP");
        assertThat(pending.summary()).isNull();
        assertThat(pending.details().confirmLabel()).isEqualTo("Picked up");
        assertThat(pending.expiresAt()).isEqualTo(Fixtures.NOW.plus(Duration.ofMinutes(10)));
        ActionRequest stored = store.find(pending.actionId()).orElseThrow();
        assertThat(stored.userId()).isEqualTo(8);
        assertThat(stored.role()).isEqualTo(Role.DELIVERY_PARTNER);
        assertThat(stored.status()).isEqualTo(ActionRequest.Status.NEW);
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedAChangeOfAnotherRoleIsNeverStored() {
        assertThatThrownBy(() -> stack.actions().propose(Fixtures.CUSTOMER, pickUp()))
                .isInstanceOf(ToolException.class)
                .satisfies(e -> assertThat(((ToolException) e).getKind()).isEqualTo(ToolException.Kind.FORBIDDEN));
        assertThat(store.size()).isZero();
    }

    @Test
    void mockedConfirmMakesTheChangeOnceAsTheUserAndARepeatIsAReplay() {
        gateway.on("PUT", PICKUP, 200, DELIVERY_OK);
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();

        ConfirmResponse first = stack.actions().confirm(Fixtures.PARTNER, id);
        ConfirmResponse second = stack.actions().confirm(Fixtures.PARTNER, id);

        assertThat(first.status()).isEqualTo(ConfirmResponse.Status.DONE);
        assertThat(first.order()).isNull();
        assertThat(first.message()).isEqualTo("Delivery #31 is marked picked up.");
        assertThat(first.link().path()).isEqualTo("/partner/deliveries/31");
        assertThat(second.status()).isEqualTo(ConfirmResponse.Status.ALREADY_DONE);
        assertThat(gateway.count("PUT", PICKUP)).isEqualTo(1);
        StubGateway.Call call = gateway.calls().get(0);
        assertThat(call.header("Authorization")).isEqualTo("Bearer token-of-user-8");
        assertThat(call.body()).isEmpty();
    }

    @Test
    void mockedTheBodyHasOnlyTheFieldsOfTheChangeWithTheExactCashAmount() {
        gateway.on("PUT", DELIVER, 200, DELIVERY_OK);
        ActionService.Plan deliver = new ActionService.Plan(ActionKind.MARK_DELIVERED, null, 31,
                new ActionBody(null, null, null, new BigDecimal("562.90")), pickUp().details(), "Delivered.", null);
        String id = stack.actions().propose(Fixtures.PARTNER, deliver).actionId();

        stack.actions().confirm(Fixtures.PARTNER, id);

        assertThat(gateway.calls().get(0).body()).isEqualTo("{\"cashCollected\":562.90}");
    }

    @Test
    void mockedSomeoneElsesChangeIsRefusedEvenWithTheSameUserIdUnderAnotherRole() {
        gateway.on("PUT", PICKUP, 200, DELIVERY_OK);
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();

        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.CUSTOMER, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        CallerContext sameIdOtherRole = new CallerContext(8, Role.CUSTOMER, "req-x", "token-of-user-8-as-customer");
        assertThatThrownBy(() -> stack.actions().confirm(sameIdOtherRole, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> stack.actions().cancel(Fixtures.ADMIN, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(gateway.calls()).isEmpty();
        assertThat(store.find(id).orElseThrow().status()).isEqualTo(ActionRequest.Status.NEW);
    }

    @Test
    void mockedUnknownMalformedAndExpiredChangesAreNeverSent() {
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();

        for (String bad : new String[] {"act_00000000-0000-0000-0000-000000000000", "act_nope", "act_",
                "00000000-0000-0000-0000-000000000000"}) {
            assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, bad))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.GONE));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedARefusalOfTheBusinessServiceEndsTheChangeWithItsMessage() {
        gateway.on("PUT", PICKUP, 409, "{\"title\":\"Business conflict\","
                + "\"detail\":\"Delivery 31 is PICKED_UP and cannot be picked up now\"}");
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();

        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be picked up now")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("could not be made");
        assertThat(gateway.count("PUT", PICKUP)).isEqualTo(1);
        assertThat(store.find(id).orElseThrow().status()).isEqualTo(ActionRequest.Status.CANCELLED);
    }

    @Test
    void mockedATemporaryFailureKeepsTheChangeSoConfirmCanBePressedAgain() {
        gateway.on("PUT", PICKUP, 503, "{\"title\":\"Service Unavailable\"}");
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();

        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, id))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Nothing was changed")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(store.find(id).orElseThrow().status()).isEqualTo(ActionRequest.Status.NEW);

        gateway.on("PUT", PICKUP, 200, DELIVERY_OK);
        assertThat(stack.actions().confirm(Fixtures.PARTNER, id).status()).isEqualTo(ConfirmResponse.Status.DONE);
    }

    @Test
    void mockedTwoPressesAtTheSameTimeMakeTheChangeOnce() throws Exception {
        gateway.on("PUT", PICKUP, call -> {
            sleep(300);
            return new StubGateway.Reply(200, DELIVERY_OK);
        });
        String id = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ConfirmResponse> press = () -> stack.actions().confirm(Fixtures.PARTNER, id);
            List<Future<ConfirmResponse>> presses = pool.invokeAll(List.of(press, press));
            List<ConfirmResponse.Status> statuses = new ArrayList<>();
            for (Future<ConfirmResponse> result : presses) {
                statuses.add(result.get().status());
            }
            assertThat(statuses).containsExactlyInAnyOrder(ConfirmResponse.Status.DONE,
                    ConfirmResponse.Status.ALREADY_DONE);
        } finally {
            pool.shutdownNow();
        }
        assertThat(gateway.count("PUT", PICKUP)).isEqualTo(1);
    }

    @Test
    void mockedACancelledChangeCannotBeConfirmedAndADoneOneCannotBeCancelled() {
        gateway.on("PUT", PICKUP, 200, DELIVERY_OK);
        String dropped = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();
        stack.actions().cancel(Fixtures.PARTNER, dropped);
        stack.actions().cancel(Fixtures.PARTNER, dropped);
        assertThatThrownBy(() -> stack.actions().confirm(Fixtures.PARTNER, dropped))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cancelled");

        String done = stack.actions().propose(Fixtures.PARTNER, pickUp()).actionId();
        stack.actions().confirm(Fixtures.PARTNER, done);
        assertThatThrownBy(() -> stack.actions().cancel(Fixtures.PARTNER, done))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already made");
        assertThat(gateway.count("PUT", PICKUP)).isEqualTo(1);
    }

    @Test
    void mockedWhenRedisIsDownNothingIsPreparedOrChanged() {
        ActionStore broken = new ActionStore() {
            @Override
            public void save(ActionRequest action, Duration keepFor) {
                throw new ActionStoreException("down", null);
            }

            @Override
            public Optional<ActionRequest> find(String id) {
                throw new ActionStoreException("down", null);
            }

            @Override
            public boolean tryLock(String id, Duration lockFor) {
                throw new ActionStoreException("down", null);
            }

            @Override
            public void unlock(String id) {
                throw new ActionStoreException("down", null);
            }
        };
        Fixtures.Stack down = Fixtures.stack(gateway.url(), clock, new InMemoryProposalStore(), broken);

        assertThatThrownBy(() -> down.actions().propose(Fixtures.PARTNER, pickUp()))
                .isInstanceOf(ToolException.class)
                .satisfies(e -> assertThat(((ToolException) e).getKind()).isEqualTo(ToolException.Kind.UNAVAILABLE));
        assertThatThrownBy(() -> down.actions().confirm(Fixtures.PARTNER, "act_00000000-0000-0000-0000-000000000000"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(gateway.calls()).isEmpty();
    }

    @Test
    void mockedEveryKindBuildsItsFixedPathFromIdsOnly() {
        assertThat(ActionKind.CANCEL_MY_ORDER.path(null, 42)).isEqualTo("/api/v1/orders/42/cancel");
        assertThat(ActionKind.MARK_READY.path(5L, 42)).isEqualTo("/api/v1/restaurants/mine/5/orders/42/ready");
        assertThat(ActionKind.SET_ITEM_AVAILABILITY.path(5L, 9))
                .isEqualTo("/api/v1/restaurants/mine/5/menu-items/9/availability");
        assertThat(ActionKind.REASSIGN_DELIVERY.path(null, 11)).isEqualTo("/api/v1/admin/deliveries/11/reassign");
        assertThatThrownBy(() -> ActionKind.ACCEPT_ORDER.path(null, 42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionKind.ACCEPT_DELIVERY.path(null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
