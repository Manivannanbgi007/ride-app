package com.fooddelivery.aimcpserver.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fooddelivery.aimcpserver.dto.ChatRequest;
import com.fooddelivery.aimcpserver.dto.ChatResponse;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Call;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Say;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

/**
 * MOCKED: a model that asks "Shall I...?" instead of preparing the change the user asked for gets one retry that names
 * the prepare tool; the prompts say the button is the confirmation and that changes need no restaurant.
 */
class ChangeRequestGuardMockedTest {

    private static final String ORDER_42 = """
            {"id":42,"customerId":1,"customerName":"Priya Sharma","restaurantId":7,"restaurantName":"Spice Hub",
             "status":"PLACED","paymentMethod":"UPI","paymentStatus":"PAID",
             "items":[{"name":"Chicken Biryani","quantity":2}],"price":{"totalAmount":562.90,"currency":"INR"}}
            """;

    private StubGateway gateway;
    private ScriptedChatModel model;
    private Fixtures.Stack stack;
    private AgentService agent;

    @BeforeEach
    void setUp() {
        gateway = new StubGateway();
        model = new ScriptedChatModel();
        stack = Fixtures.stack(gateway.url(), Fixtures.clock(), new InMemoryProposalStore());
        agent = new AgentService(ChatClient.create(model), stack.catalog(), stack.properties(), stack.executor(),
                stack.registry(), stack.clock(), stack.promptContext());
        gateway.on("GET", "/api/v1/restaurants/mine", 200,
                "[{\"id\":5,\"name\":\"Dosa Point\"},{\"id\":7,\"name\":\"Spice Hub\"}]");
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/42", 200, ORDER_42);
        gateway.on("GET", "/api/v1/restaurants/mine/7/menu-items", 200,
                "[{\"id\":9,\"name\":\"Paneer Tikka\",\"price\":220.00,\"available\":true}]");
        gateway.on("GET", "/api/v1/restaurants/mine/5/menu-items", 200,
                "[{\"id\":3,\"name\":\"Masala Dosa\",\"price\":120.00,\"available\":true}]");
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private double retries() {
        return counter("retry");
    }

    private double counter(String action) {
        var counter = stack.registry().find("ai.guard.change").tag("action", action).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void mockedAnOwnerAskedInsteadOfPreparingGetsTheButtonAfterOneRetry() {
        model.script(new Say("Do you want me to accept order 42?"),
                new Call("owner_prepare_order_step", "{\"orderId\":42,\"step\":\"ACCEPT\"}"),
                new Say("Order #42 is ready to accept: check the summary and press Accept."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("Accept order 42", List.of()));

        assertThat(response.pendingAction()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("ACCEPT_ORDER");
        assertThat(response.text()).contains("press Accept");
        assertThat(retries()).isEqualTo(1);
        // The retry showed the model its own question, then the note naming the tool
        assertThat(model.prompts().get(1).getInstructions()).extracting(Message::getText)
                .contains("Do you want me to accept order 42?");
        assertThat(model.prompts().get(1).getUserMessage().getText())
                .contains("call owner_prepare_order_step now", "that button is the confirmation");
    }

    @Test
    void mockedASoldOutDishIsPreparedAfterOneRetry() {
        model.script(new Say("Should I mark Paneer Tikka as sold out? Which restaurant is it?"),
                new Call("owner_prepare_item_availability", "{\"dish\":\"Paneer Tikka\",\"available\":false}"),
                new Say("Paneer Tikka is ready to be marked sold out: press the button below."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("Paneer Tikka is sold out", List.of()));

        assertThat(response.pendingAction().kind()).isEqualTo("SET_ITEM_AVAILABILITY");
        assertThat(retries()).isEqualTo(1);
    }

    @Test
    void mockedAPartnerAskedInsteadOfPreparingAlsoGetsTheButton() {
        gateway.on("GET", "/api/v1/delivery/deliveries/mine/current", 200,
                "{\"id\":31,\"orderId\":42,\"status\":\"ACCEPTED\",\"restaurantName\":\"Spice Hub\",\"cashToCollect\":0}");
        model.script(new Say("Great! Shall I mark the delivery as picked up?"),
                new Call("partner_prepare_delivery_step", "{\"step\":\"PICKED_UP\"}"),
                new Say("Press Picked up below."));

        ChatResponse response = agent.chat(Fixtures.PARTNER, new ChatRequest("I picked up the food", List.of()));

        assertThat(response.pendingAction().kind()).isEqualTo("MARK_PICKED_UP");
        assertThat(retries()).isEqualTo(1);
    }

    @Test
    void mockedAPreparedChangeNeedsNoRetry() {
        model.script(new Call("owner_prepare_order_step", "{\"orderId\":42,\"step\":\"ACCEPT\"}"),
                new Say("Press Accept below."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("accept order 42", List.of()));

        assertThat(response.pendingAction().kind()).isEqualTo("ACCEPT_ORDER");
        assertThat(retries()).isZero();
        assertThat(model.prompts()).hasSize(2);
    }

    @Test
    void mockedAPrepareToolThatAnsweredAnErrorIsNotRetried() {
        model.script(new Call("owner_prepare_order_step", "{\"orderId\":99,\"step\":\"ACCEPT\"}"),
                new Say("Order #99 is not one of your orders."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("accept order 99", List.of()));

        assertThat(response.pendingAction()).isNull();
        assertThat(response.text()).isEqualTo("Order #99 is not one of your orders.");
        assertThat(retries()).isZero();
    }

    @Test
    void mockedAMissingReasonIsAskedForAfterTheToolSaysSoAndThereIsOnlyOneRetry() {
        model.script(new Say("Are you sure you want to reject order 42?"),
                new Call("owner_prepare_order_step", "{\"orderId\":42,\"step\":\"REJECT\"}"),
                new Say("Why do you reject order #42? The customer will see the reason."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("reject order 42", List.of()));

        assertThat(response.pendingAction()).isNull();
        assertThat(response.text()).contains("Why do you reject order #42?");
        assertThat(model.toolResults()).anySatisfy(result -> assertThat(result).startsWith("NEED_INPUT"));
        assertThat(retries()).isEqualTo(1);
    }

    @Test
    void mockedAModelThatKeepsAskingGetsTheChangePreparedByTheAgentWithTheOwnersWords() {
        model.script(new Say("I need to know which restaurant \"chicken\" is from: Dosa Point or Spice Hub?"),
                new Say("Could you please confirm the restaurant?"));

        ChatResponse response = agent.chat(Fixtures.OWNER,
                new ChatRequest("Reject order 42, we ran out of chicken", List.of()));

        assertThat(response.pendingAction()).isNotNull();
        assertThat(response.pendingAction().kind()).isEqualTo("REJECT_ORDER");
        assertThat(response.pendingAction().details().rows())
                .anySatisfy(row -> assertThat(row.value()).contains("we ran out of chicken"));
        assertThat(response.text()).startsWith("Ready for you to confirm:").doesNotContain("Dosa Point");
        assertThat(counter("direct")).isEqualTo(1);
        // The agent prepared it after the model's second answer: no third model call
        assertThat(model.prompts()).hasSize(2);
    }

    @Test
    void mockedADirectlyPreparedChangeThatThePlatformRefusesIsExplainedInPlainWords() {
        gateway.on("GET", "/api/v1/restaurants/mine/7/orders/42", 200, ORDER_42.replace("\"PLACED\"", "\"ACCEPTED\""));
        model.script(new Say("Shall I accept order 42?"), new Say("Please confirm."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("accept order 42", List.of()));

        assertThat(response.pendingAction()).isNull();
        assertThat(response.text()).startsWith("Order #42 is ACCEPTED, so it cannot be accepted now");
        assertThat(counter("direct")).isEqualTo(1);
    }

    @Test
    void mockedARejectionWithoutAReasonIsNeverPreparedByTheAgentItself() {
        model.script(new Say("Shall I reject order 42?"), new Say("Why do you want to reject order 42?"));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("reject order 42", List.of()));

        assertThat(response.pendingAction()).isNull();
        assertThat(response.text()).isEqualTo("Why do you want to reject order 42?");
        assertThat(counter("direct")).isZero();
    }

    @Test
    void mockedAnAnswerThatCallsAPreparedChangeDoneIsReplaced() {
        model.script(new Call("owner_prepare_order_step", "{\"orderId\":42,\"step\":\"ACCEPT\"}"),
                new Say("Order #42 has been accepted."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("accept order 42", List.of()));

        assertThat(response.pendingAction().kind()).isEqualTo("ACCEPT_ORDER");
        assertThat(response.text()).startsWith("Ready for you to confirm: " + response.pendingAction().details().title())
                .contains("press \"" + response.pendingAction().details().confirmLabel() + "\"")
                .doesNotContain("has been accepted");
        assertThat(counter("reworded")).isEqualTo(1);
    }

    @Test
    void mockedAReportQuestionIsNeverPushedTowardsAChange() {
        model.script(new Say("For which period would you like the count?"));

        ChatResponse response = agent.chat(Fixtures.OWNER,
                new ChatRequest("How many orders did I accept?", List.of()));

        assertThat(response.text()).isEqualTo("For which period would you like the count?");
        assertThat(retries()).isZero();
        assertThat(model.prompts()).hasSize(1);
    }

    @Test
    void mockedTheOwnerPromptPutsChangesFirstAndNeverAsksWhichRestaurantForThem() {
        model.script(new Say("ok"));
        agent.chat(Fixtures.OWNER, new ChatRequest("hi", List.of()));

        String system = model.prompts().get(0).getInstructions().get(0).getText();
        assertThat(system).contains("the button under your answer IS the confirmation",
                "Do not ask which restaurant: the tool finds the order",
                "Changes (accept, reject, ready, sold out) need no restaurant");
        // In the owner block, the change procedures come before the reports
        int ownerBlock = system.indexOf("You help a restaurant owner");
        assertThat(system.indexOf("owner_prepare_order_step", ownerBlock))
                .isLessThan(system.indexOf("owner_sales_summary", ownerBlock));
    }

    @Test
    void mockedEveryPrepareToolSaysTheButtonIsTheConfirmation() {
        for (Role role : Role.values()) {
            // prepare_order (the customer's order summary) has its own, longer instruction to call it directly
            List<ToolCallback> prepareTools = stack.catalog().forRole(role).stream()
                    .filter(tool -> tool.getToolDefinition().name().contains("prepare"))
                    .filter(tool -> !tool.getToolDefinition().name().equals("prepare_order"))
                    .toList();
            assertThat(prepareTools).as(role.name()).isNotEmpty().allSatisfy(tool ->
                    assertThat(tool.getToolDefinition().description()).contains("the button is the confirmation"));
        }
    }
}
