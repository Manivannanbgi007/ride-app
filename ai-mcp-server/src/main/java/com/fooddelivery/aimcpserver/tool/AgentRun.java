package com.fooddelivery.aimcpserver.tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.dto.PendingAction;

/**
 * The state of one question: how many tools were used, and what the tools produced for the UI (cards and the
 * pending order). Lives only for the duration of the request; reaches the tools through the ToolContext.
 */
public final class AgentRun {

    /** Key under which the agent puts the run into the ToolContext. */
    public static final String TOOL_CONTEXT_KEY = "run";

    /** What the tool guard does with the next tool call. */
    public enum Admission {
        ALLOWED, BUDGET_EXHAUSTED, ABORT
    }

    // A model that ignores the "no more tools" message is cut off after this many refused calls
    private static final int TOLERATED_REFUSALS = 2;

    private final int maxToolCalls;
    private final long deadlineNanos;
    private final List<Card> cards = new ArrayList<>();
    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private int toolCalls;
    private int refusedCalls;
    private PendingAction pendingAction;

    public AgentRun(int maxToolCalls, long deadlineNanos) {
        this.maxToolCalls = maxToolCalls;
        this.deadlineNanos = deadlineNanos;
    }

    public synchronized Admission admit(String toolName) {
        if (System.nanoTime() - deadlineNanos > 0) {
            return Admission.ABORT;
        }
        if (toolCalls >= maxToolCalls) {
            refusedCalls++;
            return refusedCalls > TOLERATED_REFUSALS ? Admission.ABORT : Admission.BUDGET_EXHAUSTED;
        }
        toolCalls++;
        toolsUsed.add(toolName);
        return Admission.ALLOWED;
    }

    /** Keeps the card (in call order) and the latest pending order of a successful tool call. */
    public synchronized void record(ToolResult result) {
        if (result.display() != null && cards.size() < Card.MAX_CARDS) {
            cards.add(result.display());
        }
        if (result.pendingAction() != null) {
            pendingAction = result.pendingAction();
        }
    }

    public synchronized List<Card> cards() {
        return List.copyOf(cards);
    }

    public synchronized List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    public synchronized int toolCalls() {
        return toolCalls;
    }

    public synchronized PendingAction pendingAction() {
        return pendingAction;
    }
}
