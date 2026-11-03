package com.fooddelivery.aimcpserver.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * A fake model for MOCKED tests: it follows a script instead of thinking. Like the real Ollama model it answers each
 * request with either text or a tool call; Spring AI's tool-calling loop (in the ChatClient) then runs the tool through
 * the real guard and asks the model again, so the whole tool path (guard, role checks, cards) is real.
 */
public class ScriptedChatModel implements ChatModel {

    /** One scripted move. */
    public sealed interface Step permits Call, Say, Fail {
    }

    public record Call(String tool, String argumentsJson) implements Step {
    }

    public record Say(String text) implements Step {
    }

    public record Fail(RuntimeException error) implements Step {
    }

    private final Deque<Step> script = new ArrayDeque<>();
    private final List<List<String>> offeredTools = new CopyOnWriteArrayList<>();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final List<String> toolResults = new CopyOnWriteArrayList<>();

    public ScriptedChatModel script(Step... steps) {
        script.clear();
        script.addAll(List.of(steps));
        offeredTools.clear();
        prompts.clear();
        toolResults.clear();
        return this;
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    /** What each tool call returned to the model, in order. */
    public List<String> toolResults() {
        return toolResults;
    }

    /** The tools offered in the last model request. */
    public List<String> lastOfferedTools() {
        return offeredTools.isEmpty() ? List.of() : offeredTools.get(offeredTools.size() - 1);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            offeredTools.add(options.getToolCallbacks().stream().map(cb -> cb.getToolDefinition().name()).toList());
        }
        List<Message> messages = prompt.getInstructions();
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof ToolResponseMessage responses) {
            responses.getResponses().forEach(response -> toolResults.add(response.responseData()));
        }
        Step step = script.poll();
        if (step instanceof Fail fail) {
            throw fail.error();
        }
        if (step instanceof Call call) {
            AssistantMessage message = AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", call.tool(),
                            call.argumentsJson()))).build();
            return new ChatResponse(List.of(new Generation(message)));
        }
        String text = step instanceof Say say ? say.text() : "";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    // The ChatClient starts every request from the model's options
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
