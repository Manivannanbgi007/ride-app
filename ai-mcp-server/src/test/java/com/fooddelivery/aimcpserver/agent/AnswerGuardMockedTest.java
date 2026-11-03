package com.fooddelivery.aimcpserver.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fooddelivery.aimcpserver.dto.ChatMessage;
import com.fooddelivery.aimcpserver.dto.ChatRequest;
import com.fooddelivery.aimcpserver.dto.ChatResponse;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.InMemoryProposalStore;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Call;
import com.fooddelivery.aimcpserver.testsupport.ScriptedChatModel.Say;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/** MOCKED: the AnswerGuard (figures without a tool call are invented) and the prompt procedures. */
class AnswerGuardMockedTest {

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
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private double counter(String action) {
        var counter = stack.registry().find("ai.guard.ungrounded").tag("action", action).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void mockedFiguresExtractionIgnoresListNumberingButKeepsPricesDatesAndCodes() {
        assertThat(AnswerGuard.figures("1. Tea\n2) Coffee\n- 3. Milk")).isEmpty();
        assertThat(AnswerGuard.figures("Use BIRYANI10 for 25% off, total 450.50 on 2026-10-09, 13 orders"))
                .containsExactly("biryani10", "25", "450.50", "2026-10-09", "13");
    }

    @Test
    void mockedInventedOffersWithoutAToolCallAreRetriedAndTheToolAnswerIsUsed() {
        gateway.on("GET", "/api/v1/coupons", 200, Responses.OFFERS);
        model.script(new Say("Today: BIRYANI10 gives 10% off and VEG25 gives 25% off."),
                new Call("current_offers", "{}"), new Say("WELCOME100 gives 100.00 off orders above 300."));

        ChatRequest question = new ChatRequest("What food offers are available today?", List.of());
        ChatResponse response = agent.chat(Fixtures.CUSTOMER, question);

        assertThat(response.toolsUsed()).containsExactly("current_offers");
        assertThat(response.text()).contains("WELCOME100").doesNotContain("BIRYANI10");
        assertThat(counter("retry")).isEqualTo(1);
        assertThat(counter("blocked")).isZero();
        // The retry carried a directive that names the tools of the role
        String directive = model.prompts().get(1).getUserMessage().getText();
        assertThat(directive).contains("You must call a tool", "current_offers", "do not answer from memory");
    }

    @Test
    void mockedAnAnswerThatStaysUngroundedIsReplacedBySafeTextWithNoInventedData() {
        model.script(new Say("Butter Chicken sold 120 orders."), new Say("Butter Chicken sold 120 orders."));

        ChatResponse response = agent.chat(Fixtures.OWNER, new ChatRequest("What are my most popular items?", List.of()));

        assertThat(response.text()).isEqualTo("I could not retrieve that information right now. Please try again or rephrase.");
        assertThat(response.text()).doesNotContain("120");
        assertThat(response.toolCalls()).isZero();
        assertThat(counter("retry")).isEqualTo(1);
        assertThat(counter("blocked")).isEqualTo(1);
    }

    @Test
    void mockedFiguresFromTheUsersWordsOrTheConversationAreNotInvented() {
        model.script(new Say("You asked about order 4242 and the 2 biryani from earlier."));

        ChatResponse response = agent.chat(Fixtures.CUSTOMER, new ChatRequest("what about order 4242?",
                List.of(new ChatMessage(ChatMessage.Speaker.USER, "I want 2 biryani"))));

        assertThat(response.text()).startsWith("You asked about order 4242");
        assertThat(model.prompts()).hasSize(1);
        assertThat(counter("retry")).isZero();
    }

    @Test
    void mockedTodaysDateAndAnswersWithoutFiguresPassWithoutRetry() {
        model.script(new Say("Today is 2026-10-09. I can help with orders, offers and restaurants."));

        ChatResponse response = agent.chat(Fixtures.CUSTOMER, new ChatRequest("hello", List.of()));

        assertThat(response.text()).startsWith("Today is 2026-10-09");
        assertThat(model.prompts()).hasSize(1);
    }

    @Test
    void mockedWhenAToolWasCalledDerivedFiguresAreNotBlocked() {
        gateway.on("GET", "/api/v1/admin/orders/stats", 200, Responses.ADMIN_STATS);
        model.script(new Call("admin_order_stats", "{}"), new Say("7 of 12 orders were delivered: 58 percent."));

        ChatResponse response = agent.chat(Fixtures.ADMIN, new ChatRequest("delivery rate today?", List.of()));

        assertThat(response.text()).contains("58 percent");
        assertThat(counter("retry")).isZero();
    }

    @Test
    void mockedSystemPromptsCarryTheProceduresForEveryRole() {
        for (var caller : List.of(Fixtures.CUSTOMER, Fixtures.OWNER, Fixtures.ADMIN)) {
            model.script(new Say("ok"));
            agent.chat(caller, new ChatRequest("hi", List.of()));
            String system = model.prompts().get(0).getInstructions().get(0).getText();
            assertThat(system).contains("you MUST call a tool in the current turn", "Never ask the user for an internal id",
                    "NEED_INPUT", "among the N orders I checked");
        }
        model.script(new Say("ok"));
        agent.chat(Fixtures.CUSTOMER, new ChatRequest("hi", List.of()));
        assertThat(model.prompts().get(0).getInstructions().get(0).getText()).contains("always call current_offers",
                "nearMe=true", "call prepare_order at once");
        model.script(new Say("ok"));
        agent.chat(Fixtures.OWNER, new ChatRequest("hi", List.of()));
        assertThat(model.prompts().get(0).getInstructions().get(0).getText()).contains("exactly one restaurant");
    }
}
