package com.fooddelivery.aimcpserver.support;

import com.fooddelivery.aimcpserver.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The audit trail of every change the assistant prepares and every button press that follows, on its own logger
 * ("ai.audit") so it can be kept and searched apart from the service log. Only ids, kinds and outcomes are written:
 * never the user's text, reasons, names or phone numbers. The request id and trace id come with every line (MDC).
 */
public final class AuditLog {

    private static final Logger LOG = LoggerFactory.getLogger("ai.audit");

    private AuditLog() {
    }

    /**
     * @param event    proposed, confirmed, replayed, failed, cancelled or refused
     * @param outcome  a short machine-readable result (an HTTP status, "ok", "expired"...)
     */
    public static void action(String event, String actionId, String kind, long userId, Role role, String target,
                              String outcome) {
        LOG.info("event={} actionId={} kind={} userId={} role={} target={} outcome={}", event, actionId, kind, userId,
                role, target, outcome);
    }
}
