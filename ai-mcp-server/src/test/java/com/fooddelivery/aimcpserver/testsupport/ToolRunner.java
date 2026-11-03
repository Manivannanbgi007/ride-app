package com.fooddelivery.aimcpserver.testsupport;

import java.time.Duration;
import java.util.Map;

import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.tool.AgentRun;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

/** Calls a tool the way the model does: by name, with JSON arguments and the request's tool context. */
public final class ToolRunner {

    private ToolRunner() {
    }

    public static AgentRun newRun() {
        return new AgentRun(6, System.nanoTime() + Duration.ofSeconds(60).toNanos());
    }

    public static String call(Fixtures.Stack stack, CallerContext caller, AgentRun run, String tool, String arguments) {
        ToolCallback callback = stack.catalog().all().stream()
                .filter(candidate -> candidate.getToolDefinition().name().equals(tool))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("no tool " + tool));
        return callback.call(arguments, new ToolContext(Map.of(CallerContext.TOOL_CONTEXT_KEY, caller,
                AgentRun.TOOL_CONTEXT_KEY, run)));
    }

    public static String call(Fixtures.Stack stack, CallerContext caller, String tool, String arguments) {
        return call(stack, caller, newRun(), tool, arguments);
    }
}
