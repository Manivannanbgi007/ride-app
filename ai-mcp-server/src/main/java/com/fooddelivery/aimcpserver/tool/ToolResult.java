package com.fooddelivery.aimcpserver.tool;

import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.dto.PendingAction;

/**
 * What a tool returns.
 *
 * @param summary       compact JSON/text for the model (the only part it sees)
 * @param display       card for the UI, built from the same data (never from model text); may be null
 * @param pendingAction only set by the tools that prepare a change (prepare_order, prepare_cancel_order...); may be null
 */
public record ToolResult(String summary, Card display, PendingAction pendingAction) {

    public static ToolResult of(String summary, Card display) {
        return new ToolResult(summary, display, null);
    }

    public static ToolResult textOnly(String summary) {
        return new ToolResult(summary, null, null);
    }
}
