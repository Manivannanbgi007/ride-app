package com.fooddelivery.aimcpserver.agent;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.dto.ChatMessage;
import com.fooddelivery.aimcpserver.dto.ChatRequest;
import com.fooddelivery.aimcpserver.dto.ChatResponse;
import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.tool.AgentAbortedException;
import com.fooddelivery.aimcpserver.tool.AgentRun;
import com.fooddelivery.aimcpserver.tool.ToolCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * One question, one answer. The model gets only the tools of the caller's verified role; the caller (never a tool
 * argument or the prompt) reaches the tools through the ToolContext. Around the model: at most N tool calls, a
 * history size limit, a total time limit, one retry for an empty or malformed answer, and friendly messages for
 * every failure (no stack traces).
 */
@Slf4j
@Service
public class AgentService {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    private static final String FALLBACK_NO_ANSWER = "Sorry, I could not put together an answer. "
            + "Please try again or rephrase your question.";
    private static final String UNGROUNDED = "I could not retrieve that information right now. Please try again or rephrase.";
    private static final String FALLBACK_WITH_DATA = "Here is what I found.";
    private static final String CHANGE_GUARD_METRIC = "ai.guard.change";
    private static final String OUT_OF_STEPS = "I could not finish this question within my limits, so this is what I "
            + "found so far. Please ask a narrower question for more.";
    private static final String TOO_SLOW = "That took too long, so I stopped. Here is what I found so far. "
            + "Please try a simpler or narrower question.";

    private final ChatClient chatClient;
    private final ToolCatalog tools;
    private final AppProperties properties;
    private final ExecutorService executor;
    private final MeterRegistry registry;
    private final Clock clock;
    private final PromptContext promptContext;

    public AgentService(ChatClient chatClient, ToolCatalog tools, AppProperties properties, ExecutorService agentExecutor,
                        MeterRegistry registry, Clock clock, PromptContext promptContext) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.properties = properties;
        this.executor = agentExecutor;
        this.registry = registry;
        this.clock = clock;
        this.promptContext = promptContext;
    }

    public ChatResponse chat(CallerContext caller, ChatRequest request) {
        long start = System.nanoTime();
        AppProperties.Agent limits = properties.agent();
        AgentRun run = new AgentRun(limits.maxToolCalls(), start + limits.maxDuration().toNanos());
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Future<String> future = executor.submit(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return ask(caller, request, run);
            } finally {
                MDC.clear();
            }
        });
        String text;
        String outcome = "ok";
        try {
            text = future.get(limits.maxDuration().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            text = TOO_SLOW;
            outcome = "timeout";
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw ApiException.unavailable("The request was interrupted.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AgentAbortedException) {
                text = OUT_OF_STEPS;
                outcome = "limit";
            } else {
                record(caller, "error", start);
                throw unavailable(e.getCause());
            }
        }
        record(caller, outcome, start);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        log.info("agent role={} outcome={} toolCalls={} cards={} durationMs={}", caller.role(), outcome,
                run.toolCalls(), run.cards().size(), durationMs);
        return new ChatResponse(text, run.cards(), List.of(), run.pendingAction(), run.toolsUsed(),
                properties.ollama().model(), durationMs, run.toolCalls());
    }

    /**
     * Runs the model and returns the answer text. Two guards (code, not prompt) check the answer:
     * <ul>
     *   <li>ChangeRequestGuard: the user asked for a change but the model asked a question instead of preparing it.
     *       One retry with a note that names the prepare tool, then the prepare tool is called directly. An answer
     *       that calls a prepared change "done" is replaced.</li>
     *   <li>AnswerGuard: the model answered from memory without calling a tool, with figures nobody supplied. One retry
     *       that says a tool must be called, then a safe message with no invented data.</li>
     * </ul>
     */
    private String ask(CallerContext caller, ChatRequest request, AgentRun run) {
        List<Message> messages = buildMessages(caller, request);
        Map<String, Object> toolContext = Map.of(CallerContext.TOOL_CONTEXT_KEY, caller, AgentRun.TOOL_CONTEXT_KEY, run);
        String answer = generate(caller, messages, toolContext, run);
        Optional<ChangeRequestGuard.ExpectedChange> expected = ChangeRequestGuard
                .expectedChange(caller.role(), request.message())
                .filter(change -> tools.namesForRole(caller.role()).contains(change.tool()));
        if (expected.isPresent() && nothingPrepared(run)) {
            answer = insistOnPreparing(caller, expected.get(), messages, answer, toolContext, run);
        }
        if (run.pendingAction() != null && ChangeRequestGuard.claimsDone(answer)) {
            // The button is there, but the words said "done": nothing is done before the user presses it
            registry.counter(CHANGE_GUARD_METRIC, "action", "reworded").increment();
            answer = DirectChange.confirmText(run.pendingAction());
        }
        List<String> known = knownTexts(request);
        if (run.toolCalls() == 0 && AnswerGuard.isUngrounded(answer, known)) {
            registry.counter("ai.guard.ungrounded", "action", "retry").increment();
            log.warn("Answer with unsupported figures and no tool call: retrying once");
            List<Message> again = new ArrayList<>(messages);
            again.add(UserMessage.builder().text(AnswerGuard.directive(tools.namesForRole(caller.role()))).build());
            answer = generate(caller, again, toolContext, run);
            if (run.toolCalls() == 0 && AnswerGuard.isUngrounded(answer, known)) {
                registry.counter("ai.guard.ungrounded", "action", "blocked").increment();
                log.warn("Answer with unsupported figures and no tool call: blocked");
                return UNGROUNDED;
            }
        }
        return answer;
    }

    /**
     * The user asked for a change, the model prepared nothing: one retry with a note that names the tool and the details
     * from the user's message; if the model still prepares nothing and the details are complete, the agent calls the
     * prepare tool itself (it only prepares: the user still confirms with the button).
     */
    private String insistOnPreparing(CallerContext caller, ChangeRequestGuard.ExpectedChange change,
                                     List<Message> messages, String firstAnswer, Map<String, Object> toolContext,
                                     AgentRun run) {
        registry.counter(CHANGE_GUARD_METRIC, "action", "retry").increment();
        log.info("Change requested but nothing prepared: retrying once with {}", change.tool());
        List<Message> again = new ArrayList<>(messages);
        again.add(new AssistantMessage(firstAnswer));
        again.add(UserMessage.builder().text(ChangeRequestGuard.directive(change)).build());
        String answer = generate(caller, again, toolContext, run);
        if (nothingPrepared(run) && change.complete()) {
            registry.counter(CHANGE_GUARD_METRIC, "action", "direct").increment();
            log.info("Still nothing prepared: calling {} with the details of the user's message", change.tool());
            answer = DirectChange.prepare(change, tools.forRole(caller.role()), toolContext, run).orElse(answer);
        }
        return answer;
    }

    // A prepare tool that ran (even one that answered ERROR or NEED_INPUT) means the model did try the change
    private static boolean nothingPrepared(AgentRun run) {
        return run.pendingAction() == null && run.toolsUsed().stream().noneMatch(tool -> tool.contains("prepare"));
    }

    // What the answer may legitimately repeat without a tool: the user's own words, the conversation and today's date
    private List<String> knownTexts(ChatRequest request) {
        List<String> texts = new ArrayList<>();
        texts.add(request.message());
        texts.add(LocalDate.now(clock).toString());
        trimHistory(request.history()).forEach(turn -> texts.add(turn.text()));
        return texts;
    }

    private String generate(CallerContext caller, List<Message> messages, Map<String, Object> toolContext, AgentRun run) {
        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                String answer = null;
                try {
                    answer = cleanAnswer(callModel(caller, messages, toolContext));
                } catch (IllegalStateException e) {
                    // A model may invent a tool name or ask for one of another role: it is simply not available
                    if (e.getMessage() == null || !e.getMessage().contains("No ToolCallback found")) {
                        throw e;
                    }
                    log.warn("The model asked for a tool that is not available (attempt {})", attempt);
                }
                if (answer != null) {
                    return answer;
                }
                log.warn("Empty or malformed model answer (attempt {})", attempt);
            }
        } catch (AgentAbortedException e) {
            return OUT_OF_STEPS;
        }
        return run.cards().isEmpty() && run.pendingAction() == null ? FALLBACK_NO_ANSWER : FALLBACK_WITH_DATA;
    }

    private String callModel(CallerContext caller, List<Message> messages, Map<String, Object> toolContext) {
        org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt()
                .messages(messages)
                .tools((Object[]) tools.forRole(caller.role()).toArray(new ToolCallback[0]))
                .toolContext(toolContext)
                .call()
                .chatResponse();
        Generation generation = response == null ? null : response.getResult();
        AssistantMessage output = generation == null ? null : generation.getOutput();
        return output == null ? null : output.getText();
    }

    private List<Message> buildMessages(CallerContext caller, ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SystemPrompts.forRole(caller.role(), LocalDate.now(clock),
                properties.timezone()) + promptContext.blockFor(caller)));
        for (ChatMessage turn : trimHistory(request.history())) {
            messages.add(turn.role() == ChatMessage.Speaker.USER
                    ? UserMessage.builder().text(turn.text()).build() : new AssistantMessage(turn.text()));
        }
        messages.add(UserMessage.builder().text(request.message()).build());
        return messages;
    }

    /** Keeps the newest turns: at most N entries and at most M characters (the model's context is small). */
    List<ChatMessage> trimHistory(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int maxEntries = properties.agent().maxHistory();
        int budget = properties.agent().maxHistoryChars();
        List<ChatMessage> kept = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0 && kept.size() < maxEntries; i--) {
            ChatMessage turn = history.get(i);
            budget -= turn.text().length();
            if (budget < 0) {
                break;
            }
            kept.add(0, turn);
        }
        return kept;
    }

    /** The visible answer, or null when the model returned nothing usable (empty, only thoughts, a leaked tool call). */
    static String cleanAnswer(String raw) {
        if (raw == null) {
            return null;
        }
        String text = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        if (text.isEmpty() || text.contains("<tool_call>") || text.startsWith("{\"name\"")) {
            return null;
        }
        return text;
    }

    private void record(CallerContext caller, String outcome, long startNanos) {
        String role = caller.role().name().toLowerCase(Locale.ROOT);
        registry.counter("ai.agent.requests", "role", role, "outcome", outcome).increment();
        registry.timer("ai.agent.duration", "role", role).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    /** Ollama down, model missing, too slow...: one clear 503 instead of a stack trace. */
    private ApiException unavailable(Throwable failure) {
        boolean sawNetworkFailure = false;
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ApiException api) {
                return api;
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return ApiException.unavailable("The AI model took too long to answer. Try a shorter question or try "
                        + "again in a moment.");
            }
            if (current instanceof ConnectException) {
                return ApiException.unavailable("The AI model is not running right now. Please try again later.");
            }
            if (current instanceof RestClientResponseException response && response.getStatusCode().value() == 404) {
                return modelMissing();
            }
            if (current.getMessage() != null && current.getMessage().toLowerCase(Locale.ROOT).contains("not found")
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("model")) {
                return modelMissing();
            }
            sawNetworkFailure |= current instanceof ResourceAccessException;
            current = current.getCause();
        }
        if (sawNetworkFailure) {
            return ApiException.unavailable("The AI model is not reachable right now. Please try again later.");
        }
        log.warn("Agent failed: {}", failure == null ? "unknown" : failure.getClass().getName());
        log.debug("Agent failure details", failure);
        return ApiException.unavailable("The assistant ran into a problem. Please try again in a moment.");
    }

    private ApiException modelMissing() {
        return ApiException.unavailable("The AI model " + properties.ollama().model()
                + " is not installed yet (it may still be downloading). Please try again later.");
    }
}
