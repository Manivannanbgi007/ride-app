package com.fooddelivery.aimcpserver.tool;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

/**
 * All tools, each wrapped by the GuardedToolCallback. The agent asks for the tools of one verified role (a role never
 * sees the tools of another role); the MCP endpoint lists all of them and refuses a call whose caller may not use it.
 */
@Component
public class ToolCatalog {

    private final List<GuardedToolCallback> tools = new ArrayList<>();

    public ToolCatalog(List<AssistantTools> toolObjects, AppProperties properties, ExecutorService agentExecutor,
                       CallerResolver callerResolver, MeterRegistry registry) {
        AppProperties.Agent agent = properties.agent();
        for (AssistantTools toolObject : toolObjects) {
            Map<String, ToolPolicy> policies = policiesOf(toolObject);
            for (ToolCallback callback : ToolCallbacks.from(toolObject)) {
                String name = callback.getToolDefinition().name();
                ToolPolicy policy = policies.get(name);
                if (policy == null) {
                    throw new IllegalStateException("Tool " + name + " has no @ToolPolicy");
                }
                Duration timeout = policy.timeoutSeconds() > 0
                        ? Duration.ofSeconds(policy.timeoutSeconds()) : agent.toolTimeout();
                tools.add(new GuardedToolCallback(callback, EnumSet.copyOf(Arrays.asList(policy.roles())), timeout,
                        agentExecutor, callerResolver, registry, agent.maxToolResultChars()));
            }
        }
    }

    /** The tools offered to the model for this role, in a stable order. */
    public List<ToolCallback> forRole(Role role) {
        return tools.stream().filter(tool -> tool.roles().contains(role)).map(ToolCallback.class::cast).toList();
    }

    public List<String> namesForRole(Role role) {
        return forRole(role).stream().map(tool -> tool.getToolDefinition().name()).toList();
    }

    public List<ToolCallback> all() {
        return List.copyOf(tools);
    }

    public Set<Role> rolesOf(String toolName) {
        return tools.stream().filter(tool -> tool.getToolDefinition().name().equals(toolName))
                .findFirst().map(GuardedToolCallback::roles).orElse(Set.of());
    }

    private static Map<String, ToolPolicy> policiesOf(Object toolObject) {
        Map<String, ToolPolicy> result = new HashMap<>();
        for (Method method : AopUtils.getTargetClass(toolObject).getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                result.put(tool.name().isEmpty() ? method.getName() : tool.name(), method.getAnnotation(ToolPolicy.class));
            }
        }
        return result;
    }
}
