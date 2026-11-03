package com.fooddelivery.aimcpserver.support;

/** A tool failure whose message is safe to show to the model (and through it to the user). */
public class ToolException extends RuntimeException {

    /** What went wrong, also the "outcome" tag of the ai.tool.calls metric. */
    public enum Kind {
        INVALID, FORBIDDEN, FAILED, UNAVAILABLE, TIMEOUT
    }

    private final Kind kind;

    public ToolException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static ToolException invalid(String message) {
        return new ToolException(Kind.INVALID, message);
    }
}
