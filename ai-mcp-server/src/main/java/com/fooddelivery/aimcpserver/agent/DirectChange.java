package com.fooddelivery.aimcpserver.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.tool.AgentRun;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

/**
 * The last resort when a model keeps asking instead of preparing a change the user clearly asked for: the agent calls the
 * prepare tool itself with the details read from the user's message.
 * <p>
 * This is as safe as the model calling it: the call goes through the same guarded tool (role check, tool budget, time
 * limit), the tool reads and checks everything on the platform as the user, and it only PREPARES the change. Nothing
 * happens until the user presses the Confirm button.
 */
final class DirectChange {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ERROR_PREFIX = "ERROR: ";
    private static final Pattern CHOICES = Pattern.compile("\\[(.+)]");
    private static final Pattern NOTE = Pattern.compile("\"note\"\\s*:\\s*\"([^\"]+)\"");

    private DirectChange() {
    }

    /**
     * Calls the change's prepare tool and returns the answer for the user, or nothing when the tool's result needs the
     * model to explain it (then the model's own answer is kept).
     */
    static Optional<String> prepare(ChangeRequestGuard.ExpectedChange change, List<ToolCallback> roleTools,
                                    Map<String, Object> toolContext, AgentRun run) {
        Optional<ToolCallback> tool = roleTools.stream()
                .filter(callback -> callback.getToolDefinition().name().equals(change.tool()))
                .findFirst();
        if (tool.isEmpty()) {
            return Optional.empty();
        }
        String result = tool.get().call(JSON.writeValueAsString(change.arguments()), new ToolContext(toolContext));
        return answerFor(result, run);
    }

    /** "Ready for you to confirm: ..." for a prepared change: never says it is done. */
    static String confirmText(PendingAction action) {
        PendingAction.Details details = action.details();
        if (details == null || details.title() == null) {
            return "Ready for you to confirm: check the summary below and press the button to do it.";
        }
        String button = details.confirmLabel() == null ? "the button" : "\"" + details.confirmLabel() + "\"";
        return "Ready for you to confirm: " + details.title() + ". Check the summary below and press " + button
                + " to do it, or dismiss it if you changed your mind.";
    }

    private static Optional<String> answerFor(String result, AgentRun run) {
        if (run.pendingAction() != null) {
            return Optional.of(confirmText(run.pendingAction()));
        }
        if (result == null) {
            return Optional.empty();
        }
        // The platform's refusal, already in plain words ("Order #42 is ACCEPTED, so it cannot be accepted now")
        if (result.startsWith(ERROR_PREFIX)) {
            return Optional.of(result.substring(ERROR_PREFIX.length()));
        }
        Matcher choices = CHOICES.matcher(result);
        if (result.startsWith("AMBIGUOUS") && choices.find()) {
            return Optional.of("More than one matches: " + choices.group(1) + ". Which one do you mean?");
        }
        // "It is already marked sold out. Nothing to change."
        Matcher note = NOTE.matcher(result);
        return note.find() ? Optional.of(note.group(1)) : Optional.empty();
    }
}
