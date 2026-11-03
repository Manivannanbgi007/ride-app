package com.fooddelivery.aimcpserver.tool;

/**
 * Thrown by the tool guard to stop a model that keeps calling tools after its budget (or the time limit) is used up.
 * It is not a Spring AI tool failure, so it ends the model loop instead of being handed back to the model.
 */
public class AgentAbortedException extends RuntimeException {

    public AgentAbortedException(String message) {
        super(message);
    }
}
