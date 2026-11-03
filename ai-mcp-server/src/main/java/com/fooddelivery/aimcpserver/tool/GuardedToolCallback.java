package com.fooddelivery.aimcpserver.tool;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.support.ToolText;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps every tool (for the agent and for MCP) with the same safety net:
 * role check, tool budget, per-call timeout, error text instead of exceptions, result cleaning and size cap,
 * metrics and structured logging (tool name, outcome, duration, ids only: never arguments or personal data).
 * What the model receives is the tool's summary; the card goes to the AgentRun, so it never passes through the model.
 */
@Slf4j
public class GuardedToolCallback implements ToolCallback {

    private static final String BUDGET_MESSAGE = "TOOL LIMIT REACHED: no more tool calls are allowed for this question. "
            + "Answer now with only the information you already have and say what is still missing.";

    private final ToolCallback delegate;
    private final Set<Role> roles;
    private final Duration timeout;
    private final ExecutorService executor;
    private final CallerResolver callerResolver;
    private final MeterRegistry registry;
    private final int maxResultChars;

    public GuardedToolCallback(ToolCallback delegate, Set<Role> roles, Duration timeout, ExecutorService executor,
                               CallerResolver callerResolver, MeterRegistry registry, int maxResultChars) {
        this.delegate = delegate;
        this.roles = roles;
        this.timeout = timeout;
        this.executor = executor;
        this.callerResolver = callerResolver;
        this.registry = registry;
        this.maxResultChars = maxResultChars;
    }

    public Set<Role> roles() {
        return roles;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, new ToolContext(Map.of()));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = delegate.getToolDefinition().name();
        long start = System.nanoTime();
        Optional<CallerContext> caller = callerResolver.find(toolContext);
        if (caller.isEmpty()) {
            return finish(name, "forbidden", start, error("Authentication required: the caller's identity is missing."));
        }
        if (!roles.contains(caller.get().role())) {
            return finish(name, "forbidden", start, error("This tool is not available for your role."));
        }
        AgentRun run = toolContext.getContext().get(AgentRun.TOOL_CONTEXT_KEY) instanceof AgentRun r ? r : null;
        if (run != null) {
            AgentRun.Admission admission = run.admit(name);
            if (admission == AgentRun.Admission.ABORT) {
                registry.counter("ai.tool.calls", "tool", name, "outcome", "aborted").increment();
                throw new AgentAbortedException("The tool budget or the time limit of this question is used up.");
            }
            if (admission == AgentRun.Admission.BUDGET_EXHAUSTED) {
                return finish(name, "budget", start, BUDGET_MESSAGE);
            }
        }
        return runWithTimeout(name, toolInput, toolContext, run, start);
    }

    private String runWithTimeout(String name, String toolInput, ToolContext toolContext, AgentRun run, long start) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Future<String> future = executor.submit(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return delegate.call(toolInput, toolContext);
            } finally {
                MDC.clear();
            }
        });
        try {
            ToolResult result = ToolResultConverter.decode(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
            if (run != null) {
                run.record(result);
            }
            return finish(name, "ok", start, ToolText.capResult(result.summary(), maxResultChars));
        } catch (TimeoutException e) {
            future.cancel(true);
            return finish(name, "timeout", start, error("The data source took too long to answer. Try again shortly."));
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AgentAbortedException("The question was cancelled.");
        } catch (ExecutionException e) {
            return failure(name, start, e.getCause());
        }
    }

    // Every failure becomes a short message the model can explain; stack traces stay out of the answer
    private String failure(String name, long start, Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof NeedInputException needInput) {
                return finish(name, "need_input", start, needInput.getMessage());
            }
            if (current instanceof ToolException tool) {
                return finish(name, tool.getKind().name().toLowerCase(Locale.ROOT), start,
                        error(tool.getMessage()));
            }
            if (current instanceof GatewayException gateway) {
                String outcome = gateway.isTimedOut() ? "timeout" : gateway.isTransientFailure() ? "unavailable" : "failed";
                return finish(name, outcome, start, error(gateway.getMessage()));
            }
            current = current.getCause();
        }
        log.warn("Tool {} failed unexpectedly: {}", name, cause == null ? "unknown" : cause.getClass().getSimpleName());
        return finish(name, "failed", start, error("The tool failed unexpectedly. Tell the user it could not be answered now."));
    }

    private String finish(String name, String outcome, long startNanos, String text) {
        long nanos = System.nanoTime() - startNanos;
        registry.counter("ai.tool.calls", "tool", name, "outcome", outcome).increment();
        registry.timer("ai.tool.duration", "tool", name).record(nanos, TimeUnit.NANOSECONDS);
        log.info("tool={} outcome={} durationMs={}", name, outcome, TimeUnit.NANOSECONDS.toMillis(nanos));
        return text;
    }

    private static String error(String message) {
        return "ERROR: " + message;
    }
}
