package com.fooddelivery.aimcpserver.action;

import java.time.Instant;

import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.security.Role;

/**
 * A change the assistant prepared and the user may confirm with a button. Stored in Redis and bound to the user and
 * role that asked for it; nothing changes until Confirm.
 *
 * @param restaurantId the owner's restaurant (owner changes only), else null
 * @param targetId     the order, delivery or menu item the change is about
 * @param details      what the user saw before confirming
 * @param doneMessage  what the user is told once the change is made
 * @param link         where to see the result
 * @param failure      why a permanent failure cancelled the action, else null
 */
public record ActionRequest(
        String id,
        long userId,
        Role role,
        ActionKind kind,
        Long restaurantId,
        long targetId,
        ActionBody body,
        PendingAction.Details details,
        String doneMessage,
        Link link,
        Status status,
        Instant createdAt,
        Instant expiresAt,
        String failure
) {

    public enum Status {
        NEW, DONE, CANCELLED
    }

    public ActionRequest done() {
        return new ActionRequest(id, userId, role, kind, restaurantId, targetId, body, details, doneMessage, link,
                Status.DONE, createdAt, expiresAt, null);
    }

    public ActionRequest cancelled(String reason) {
        return new ActionRequest(id, userId, role, kind, restaurantId, targetId, body, details, doneMessage, link,
                Status.CANCELLED, createdAt, expiresAt, reason);
    }

    public String path() {
        return kind.path(restaurantId, targetId);
    }

    /** "id 42" or "restaurant 5 id 9" (what the kind is about): for the audit log. */
    public String target() {
        return (restaurantId == null ? "" : "restaurant " + restaurantId + " ") + "id " + targetId;
    }

    public PendingAction pendingAction() {
        return new PendingAction(id, kind.name(), null, details, expiresAt);
    }
}
