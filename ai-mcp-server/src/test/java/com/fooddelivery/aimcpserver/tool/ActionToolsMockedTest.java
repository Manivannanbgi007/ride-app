package com.fooddelivery.aimcpserver.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryActionStore;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MOCKED: the tools that prepare a change, one role at a time. Each reads and checks, then stores the change; none of
 * them ever calls a changing (PUT) API: that only happens when the user presses Confirm.
 */
class ActionToolsMockedTest {

    private static final String ORDER_PLACED_PAID = """
            {"id":42,"customerId":1,"customerName":"Priya Sharma","restaurantId":7,"restaurantName":"Spice Hub",
             "status":"PLACED","paymentMethod":"UPI","paymentStatus":"PAID",
             "items":[{"name":"Chicken Biryani","quantity":2},{"name":"Garlic Naan","quantity":1}],
             "price":{"totalAmount":562.90,"currency":"INR"}}
            """;
    private static final String PARTNER_DELIVERY = "/api/v1/delivery/deliveries/mine";

    private StubGateway gateway;
    private InMemoryActionStore actionStore;
    private Fixtures.Stack stack;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        actionStore = new InMemoryActionStore();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore(), actionStore);
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private long changingCalls() {
        return gateway.calls().stream().filter(call -> !call.method().equals("GET")).count();
    }

    // ---------------------------------------------------------------- customer

    @Test
    void mockedACustomerCancellationIsPreparedWithTheRefundNoteAndConfirmedOnlyByTheButton() {
        gateway.on("GET", "/api/v1/orders/42", 200, ORDER_PLACED_PAID);
        gateway.on("PUT", "/api/v1/orders/42/cancel", 200, "{\"id\":42,\"status\":\"CANCELLED\"}");
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_cancel_order",
                "{\"orderId\":42,\"reason\":\"Ordered by mistake\"}");

        assertThat(result).contains("\"status\":\"PREPARED_NOT_DONE\"", "\"change\":\"Cancel order #42\"",
                "NOTHING HAS CHANGED YET", "refunded automatically");
        PendingAction pending = run.pendingAction();
        assertThat(pending.kind()).isEqualTo("CANCEL_MY_ORDER");
        assertThat(pending.details().confirmLabel()).isEqualTo("Cancel order");
        assertThat(pending.details().rows()).extracting(PendingAction.Row::value)
                .contains("Spice Hub", "Chicken Biryani x2, Garlic Naan x1", "562.90 INR", "UPI, paid",
                        "Ordered by mistake");
        assertThat(changingCalls()).isZero();

        ConfirmResponse done = stack.actions().confirm(Fixtures.CUSTOMER, pending.actionId());

        assertThat(done.message()).isEqualTo("Order #42 has been cancelled. The refund has been started.");
        assertThat(done.link().path()).isEqualTo("/orders/42");
        StubGateway.Call put = gateway.calls().get(gateway.calls().size() - 1);
        assertThat(put.path()).isEqualTo("/api/v1/orders/42/cancel");
        assertThat(put.body()).isEqualTo("{\"reason\":\"Ordered by mistake\"}");
        assertThat(put.header("Authorization")).isEqualTo("Bearer token-of-user-1");
    }

    @Test
    void mockedAnAcceptedOrderOrSomeoneElsesOrderIsNotPrepared() {
        gateway.on("GET", "/api/v1/orders/42", 200, ORDER_PLACED_PAID.replace("\"PLACED\"", "\"ACCEPTED\""));
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_cancel_order", "{\"orderId\":42}"))
                .startsWith("ERROR").contains("ACCEPTED", "can no longer be cancelled");
        // order-service answers 404 for another customer's order (no stub route = 404)
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_cancel_order", "{\"orderId\":43}"))
                .startsWith("ERROR").contains("Not found");
        assertThat(run.pendingAction()).isNull();
        assertThat(actionStore.size()).isZero();
    }

    @Test
    void mockedACustomerWithoutAReasonGetsTheDefaultReason() {
        gateway.on("GET", "/api/v1/orders/42", 200, ORDER_PLACED_PAID.replace("\"PAID\"", "\"PENDING\"")
                .replace("\"UPI\"", "\"CASH_ON_DELIVERY\""));
        var run = ToolRunner.newRun();

        ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_cancel_order", "{\"orderId\":42}");

        assertThat(run.pendingAction().details().note()).isNull();
        assertThat(actionStore.find(run.pendingAction().actionId()).orElseThrow().body().reason())
                .isEqualTo("Cancelled by the customer with the assistant");
    }

    // ---------------------------------------------------------------- owner

    @Test
    void mockedTheOwnerStepIsPreparedForTheRestaurantTheOrderBelongsTo() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200,
                "[{\"id\":5,\"name\":\"Dosa Point\"},{\"id\":7,\"name\":\"Spice Hub\"}]");
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/42", 200,
                ORDER_PLACED_PAID.replace("\"PLACED\"", "\"PREPARING\""));
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"mark_ready\"}");

        assertThat(result).contains("PREPARED_NOT_DONE", "Mark ready for pickup: order #42", "delivery partner");
        assertThat(run.pendingAction().kind()).isEqualTo("MARK_READY");
        // Restaurant 5 was asked first and answered 404: the order is not there
        assertThat(gateway.count("GET", "/api/v1/restaurants/mine/5/orders/42")).isEqualTo(1);
        assertThat(actionStore.find(run.pendingAction().actionId()).orElseThrow().path())
                .isEqualTo("/api/v1/restaurants/mine/7/orders/42/ready");
        assertThat(changingCalls()).isZero();
    }

    @Test
    void mockedRejectingNeedsAReasonAndAWrongStepIsRefused() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, "[{\"id\":7,\"name\":\"Spice Hub\"}]");
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/42", 200, ORDER_PLACED_PAID);
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"REJECT\"}")).startsWith("NEED_INPUT").contains("why");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"MARK_READY\"}")).startsWith("ERROR").contains("PLACED", "PREPARING");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"DELIVER\"}")).startsWith("ERROR").contains("ACCEPT, REJECT");
        assertThat(run.pendingAction()).isNull();

        ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"REJECT\",\"reason\":\"Out of chicken\"}");
        assertThat(actionStore.find(run.pendingAction().actionId()).orElseThrow().body().reason())
                .isEqualTo("Out of chicken");
    }

    @Test
    void mockedAnOrderOfAnotherRestaurantIsNotFound() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, "[{\"id\":7,\"name\":\"Spice Hub\"}]");

        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "owner_prepare_order_step",
                "{\"orderId\":99,\"step\":\"ACCEPT\"}")).startsWith("ERROR").contains("not an order of your restaurants");
        assertThat(actionStore.size()).isZero();
    }

    @Test
    void mockedADishIsFoundByNameAndOnlyARealChangeIsPrepared() {
        gateway.on("GET", "/api/v1/restaurants/mine", 200, "[{\"id\":7,\"name\":\"Spice Hub\"}]");
        gateway.on("GET", "/api/v1/restaurants/mine/7/menu-items", 200, """
                [{"id":9,"name":"Paneer Tikka","price":220.00,"available":true},
                 {"id":10,"name":"Paneer Butter Masala","price":260.00,"available":true},
                 {"id":11,"name":"Garlic Naan","price":40.00,"available":false}]
                """);
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_item_availability",
                "{\"dish\":\"paneer\",\"available\":false}")).startsWith("AMBIGUOUS")
                .contains("Paneer Tikka (Spice Hub)", "Paneer Butter Masala (Spice Hub)");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_item_availability",
                "{\"dish\":\"garlic naan\",\"available\":false}")).contains("NO_CHANGE_NEEDED", "already marked sold out");
        assertThat(run.pendingAction()).isNull();

        String result = ToolRunner.call(stack, Fixtures.OWNER, run, "owner_prepare_item_availability",
                "{\"dish\":\"Paneer Tikka\",\"available\":false}");

        assertThat(result).contains("Mark Paneer Tikka sold out");
        assertThat(run.pendingAction().details().confirmLabel()).isEqualTo("Mark sold out");
        var stored = actionStore.find(run.pendingAction().actionId()).orElseThrow();
        assertThat(stored.path()).isEqualTo("/api/v1/restaurants/mine/7/menu-items/9/availability");
        assertThat(stored.body().available()).isFalse();
        assertThat(changingCalls()).isZero();
    }

    // ---------------------------------------------------------------- partner

    @Test
    void mockedDeliveringACashOrderSendsTheExactAmountAfterTheReminder() {
        gateway.on("GET", PARTNER_DELIVERY + "/current", 200, """
                {"id":31,"orderId":42,"status":"PICKED_UP","restaurantName":"Spice Hub",
                 "pickupAddress":{"line1":"100 Feet Road","city":"Bengaluru"},"customerName":"Priya Sharma",
                 "dropoffAddress":{"line1":"12 MG Road","city":"Bengaluru"},
                 "paymentMethod":"CASH_ON_DELIVERY","cashToCollect":562.90,"currency":"INR"}
                """);
        gateway.on("PUT", PARTNER_DELIVERY + "/31/deliver", 200, "{\"id\":31,\"status\":\"DELIVERED\"}");
        var run = ToolRunner.newRun();

        String result = ToolRunner.call(stack, Fixtures.PARTNER, run, "partner_prepare_delivery_step",
                "{\"step\":\"DELIVERED\"}");

        assertThat(result).contains("Delivered: delivery #31 (order #42)",
                "Confirm only after collecting exactly 562.90 INR in cash.");
        assertThat(run.pendingAction().details().rows()).extracting(PendingAction.Row::value)
                .contains("Collect 562.90 INR", "12 MG Road, Bengaluru");
        assertThat(changingCalls()).isZero();

        stack.actions().confirm(Fixtures.PARTNER, run.pendingAction().actionId());

        StubGateway.Call put = gateway.calls().get(gateway.calls().size() - 1);
        assertThat(put.path()).isEqualTo(PARTNER_DELIVERY + "/31/deliver");
        assertThat(put.body()).isEqualTo("{\"cashCollected\":562.90}");
    }

    @Test
    void mockedAStepThatDoesNotFitTheDeliveryOrNoDeliveryIsRefused() {
        gateway.on("GET", PARTNER_DELIVERY + "/31", 200, "{\"id\":31,\"orderId\":42,\"status\":\"PICKED_UP\"}");
        gateway.on("GET", PARTNER_DELIVERY + "/current", 204, "");

        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "partner_prepare_delivery_step",
                "{\"step\":\"ACCEPT\",\"deliveryId\":31}")).startsWith("ERROR").contains("PICKED_UP", "ASSIGNED");
        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "partner_prepare_delivery_step",
                "{\"step\":\"PICKED_UP\"}")).startsWith("ERROR").contains("no current delivery");
        assertThat(actionStore.size()).isZero();
    }

    // ---------------------------------------------------------------- admin

    @Test
    void mockedAnAdminCancellationNeedsAReason() {
        gateway.on("GET", "/api/v1/admin/orders/42", 200, ORDER_PLACED_PAID.replace("\"PLACED\"", "\"PREPARING\""));
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_prepare_cancel_order", "{\"orderId\":42}"))
                .startsWith("NEED_INPUT").contains("audit log");
        assertThat(gateway.calls()).isEmpty();

        ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_prepare_cancel_order",
                "{\"orderId\":42,\"reason\":\"Restaurant closed early\"}");

        assertThat(run.pendingAction().kind()).isEqualTo("ADMIN_CANCEL_ORDER");
        assertThat(run.pendingAction().details().note()).contains("refunded automatically");
    }

    @Test
    void mockedAReassignmentGoesToANamedAvailablePartnerOnly() {
        gateway.on("GET", "/api/v1/admin/deliveries/11", 200, """
                {"id":11,"orderId":25,"status":"ASSIGNED","restaurantName":"Biryani House",
                 "partnerUserId":13,"partnerName":"Ravi Kumar"}
                """);
        gateway.on("GET", "/api/v1/admin/delivery-partners", 200, """
                {"content":[{"userId":21,"fullName":"Anil Rao","city":"Bengaluru","status":"AVAILABLE"}]}
                """);
        gateway.on("GET", "/api/v1/admin/delivery-partners/22", 200,
                "{\"userId\":22,\"fullName\":\"Meera Nair\",\"city\":\"Bengaluru\",\"status\":\"BUSY\"}");
        var run = ToolRunner.newRun();

        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_prepare_reassign_delivery",
                "{\"deliveryId\":11,\"partner\":\"22\",\"reason\":\"Partner broke down\"}"))
                .startsWith("ERROR").contains("Meera Nair is BUSY");

        ToolRunner.call(stack, Fixtures.ADMIN, run, "admin_prepare_reassign_delivery",
                "{\"deliveryId\":11,\"partner\":\"anil\",\"reason\":\"Partner broke down\"}");

        assertThat(run.pendingAction().details().rows()).extracting(PendingAction.Row::value)
                .contains("Ravi Kumar (id 13)", "Anil Rao (id 21, Bengaluru)");
        var stored = actionStore.find(run.pendingAction().actionId()).orElseThrow();
        assertThat(stored.body().partnerUserId()).isEqualTo(21L);
        assertThat(stored.path()).isEqualTo("/api/v1/admin/deliveries/11/reassign");
        assertThat(gateway.calls().stream().filter(c -> c.path().equals("/api/v1/admin/delivery-partners")))
                .allSatisfy(call -> assertThat(call.query()).contains("q=anil"));
    }

    // ---------------------------------------------------------------- isolation

    @Test
    void mockedNoRoleCanPrepareAnotherRolesChange() {
        assertThat(ToolRunner.call(stack, Fixtures.CUSTOMER, "owner_prepare_order_step",
                "{\"orderId\":42,\"step\":\"ACCEPT\"}")).startsWith("ERROR");
        assertThat(ToolRunner.call(stack, Fixtures.OWNER, "admin_prepare_cancel_order",
                "{\"orderId\":42,\"reason\":\"x\"}")).startsWith("ERROR");
        assertThat(ToolRunner.call(stack, Fixtures.PARTNER, "prepare_cancel_order", "{\"orderId\":42}"))
                .startsWith("ERROR");
        assertThat(ToolRunner.call(stack, Fixtures.ADMIN, "partner_prepare_delivery_step", "{\"step\":\"ACCEPT\"}"))
                .startsWith("ERROR");
        assertThat(gateway.calls()).isEmpty();
        assertThat(actionStore.size()).isZero();
    }
}
