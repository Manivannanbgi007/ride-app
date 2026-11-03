package com.fooddelivery.aimcpserver.action;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.client.GatewayException;
import com.fooddelivery.aimcpserver.config.AppProperties;
import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.Link;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.exception.ApiException;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.AuditLog;
import com.fooddelivery.aimcpserver.support.ToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Every change other than placing an order (cancel an order, accept it, mark a delivery picked up...), in two separate
 * steps like the order: propose (called by a prepare tool after it checked the change makes sense: changes nothing) and
 * confirm (called by the BFF after the user pressed Confirm: makes the change once, through the business API, as that
 * user). The business service checks permissions, ownership and the status again when the change is made.
 */
@Slf4j
@Service
public class ActionService {

    /** Action ids start with this; order proposals have plain UUIDs. */
    public static final String ID_PREFIX = "act_";

    private static final long CONFIRM_WAIT_MILLIS = 15_000;
    private static final long CONFIRM_POLL_MILLIS = 100;
    private static final String UNAVAILABLE = "Confirmation is temporarily unavailable. Please try again in a moment.";
    private static final String NOT_FOUND = "This action was not found or has expired.";

    private final GatewayClient gateway;
    private final ActionStore store;
    private final AppProperties properties;
    private final Clock clock;

    public ActionService(GatewayClient gateway, ActionStore store, AppProperties properties, Clock clock) {
        this.gateway = gateway;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * A checked change, as the prepare tool built it: only numeric ids and a fixed set of body fields.
     *
     * @param restaurantId the owner's restaurant, for the owner's changes only
     */
    public record Plan(ActionKind kind, Long restaurantId, long targetId, ActionBody body,
                       PendingAction.Details details, String doneMessage, Link link) {
    }

    public static boolean isActionId(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    // ---------------------------------------------------------------- propose

    /** Stores the change for this caller and returns what the Confirm and Cancel buttons need. Changes nothing. */
    public PendingAction propose(CallerContext caller, Plan plan) {
        if (plan.kind().role() != caller.role()) {
            throw new ToolException(ToolException.Kind.FORBIDDEN, "This change is not available for your role.");
        }
        // Fails here, not at confirm, when an id is missing
        plan.kind().path(plan.restaurantId(), plan.targetId());
        Instant now = Instant.now(clock);
        ActionRequest action = new ActionRequest(ID_PREFIX + UUID.randomUUID(), caller.userId(), caller.role(),
                plan.kind(), plan.restaurantId(), plan.targetId(), plan.body() == null ? ActionBody.NONE : plan.body(),
                plan.details(), plan.doneMessage(), plan.link(), ActionRequest.Status.NEW, now,
                now.plus(properties.proposal().ttl()), null);
        try {
            store.save(action, properties.proposal().ttl().plus(properties.proposal().keepAfterExpiry()));
        } catch (ActionStoreException e) {
            log.warn("Action not saved: {}", e.getMessage());
            throw new ToolException(ToolException.Kind.UNAVAILABLE,
                    "The change could not be prepared right now (temporary problem). Please try again in a moment.");
        }
        audit("proposed", action, "ok");
        return action.pendingAction();
    }

    // ---------------------------------------------------------------- confirm

    /**
     * Makes the change, exactly once. Calling it again (a double click, a retry, two tabs) answers ALREADY_DONE: a lock
     * in Redis lets one caller at a time in, and a done action is never sent again.
     */
    public ConfirmResponse confirm(CallerContext caller, String actionId) {
        try {
            long giveUpAt = System.nanoTime() + CONFIRM_WAIT_MILLIS * 1_000_000;
            while (true) {
                ActionRequest action = requireOwn(caller, actionId);
                ConfirmResponse replay = replayOrReject(action);
                if (replay != null) {
                    return replay;
                }
                if (store.tryLock(action.id(), properties.proposal().lockTtl())) {
                    try {
                        return confirmLocked(caller, requireOwn(caller, actionId));
                    } finally {
                        releaseQuietly(action.id());
                    }
                }
                if (System.nanoTime() > giveUpAt) {
                    throw ApiException.conflict("This change is being confirmed by another request. "
                            + "Check again in a moment.");
                }
                pause();
            }
        } catch (ActionStoreException e) {
            log.warn("Action store failed during confirm: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Confirmation unavailable", UNAVAILABLE);
        }
    }

    private ConfirmResponse confirmLocked(CallerContext caller, ActionRequest action) {
        // Another request may have finished between our check and getting the lock
        ConfirmResponse replay = replayOrReject(action);
        if (replay != null) {
            return replay;
        }
        try {
            gateway.put(caller, action.path(), action.body().toRequest());
        } catch (GatewayException e) {
            throw failed(action, e);
        }
        ActionRequest done = action.done();
        try {
            store.save(done, properties.proposal().confirmedKeep());
        } catch (ActionStoreException e) {
            // The change is made: say so. A repeated Confirm is refused by the business service (already changed)
            log.warn("Done action not saved: {}", e.getMessage());
        }
        audit("confirmed", done, "ok");
        return response(ConfirmResponse.Status.DONE, done);
    }

    // A transient failure leaves the action usable (the user presses Confirm again); a refusal ends it
    private ApiException failed(ActionRequest action, GatewayException e) {
        if (e.isTransientFailure()) {
            audit("failed", action, "transient " + e.getStatus());
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Not changed",
                    e.getMessage() + " Nothing was changed; you can press Confirm again.");
        }
        store.save(action.cancelled(e.getMessage()), properties.proposal().confirmedKeep());
        audit("failed", action, "refused " + e.getStatus());
        HttpStatus status = e.getStatus() == 400 || e.getStatus() == 409 ? HttpStatus.valueOf(e.getStatus())
                : HttpStatus.UNPROCESSABLE_CONTENT;
        return new ApiException(status, "Change refused", e.getMessage());
    }

    private ConfirmResponse replayOrReject(ActionRequest action) {
        if (action.status() == ActionRequest.Status.DONE) {
            audit("replayed", action, "already done");
            return response(ConfirmResponse.Status.ALREADY_DONE, action);
        }
        if (action.status() == ActionRequest.Status.CANCELLED) {
            throw ApiException.conflict(action.failure() == null
                    ? "This change was cancelled." : "This change could not be made: " + action.failure());
        }
        if (!Instant.now(clock).isBefore(action.expiresAt())) {
            audit("refused", action, "expired");
            throw ApiException.gone("This change has expired. Ask the assistant to prepare it again.");
        }
        return null;
    }

    private static ConfirmResponse response(ConfirmResponse.Status status, ActionRequest action) {
        return new ConfirmResponse(status, null, action.doneMessage(), action.link());
    }

    // ---------------------------------------------------------------- cancel

    /** Cancelling is allowed until the change is made; cancelling twice is fine. */
    public void cancel(CallerContext caller, String actionId) {
        try {
            long giveUpAt = System.nanoTime() + CONFIRM_WAIT_MILLIS * 1_000_000;
            while (!store.tryLock(requireOwn(caller, actionId).id(), properties.proposal().lockTtl())) {
                if (System.nanoTime() > giveUpAt) {
                    throw ApiException.conflict("This change is being confirmed right now, so it cannot be cancelled.");
                }
                pause();
            }
            try {
                ActionRequest action = requireOwn(caller, actionId);
                if (action.status() == ActionRequest.Status.DONE) {
                    throw ApiException.conflict("This change was already made.");
                }
                if (action.status() == ActionRequest.Status.NEW) {
                    store.save(action.cancelled(null), properties.proposal().confirmedKeep());
                    audit("cancelled", action, "ok");
                }
            } finally {
                releaseQuietly(actionId);
            }
        } catch (ActionStoreException e) {
            log.warn("Action store failed during cancel: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cancellation unavailable", UNAVAILABLE);
        }
    }

    // ---------------------------------------------------------------- shared

    /**
     * The action of this caller: unknown or malformed ids are 404; someone else's action, or one prepared under another
     * role (the account's role changed since), is 403.
     */
    private ActionRequest requireOwn(CallerContext caller, String actionId) {
        if (!isWellFormed(actionId)) {
            throw ApiException.notFound(NOT_FOUND);
        }
        ActionRequest action = store.find(actionId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (action.userId() != caller.userId() || action.role() != caller.role() || action.kind().role() != caller.role()) {
            AuditLog.action("refused", action.id(), action.kind().name(), caller.userId(), caller.role(),
                    action.target(), "not owner");
            throw ApiException.forbidden("This change belongs to another user.");
        }
        return action;
    }

    private static boolean isWellFormed(String actionId) {
        if (!isActionId(actionId)) {
            return false;
        }
        String uuid = actionId.substring(ID_PREFIX.length());
        try {
            return UUID.fromString(uuid).toString().equals(uuid.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void audit(String event, ActionRequest action, String outcome) {
        AuditLog.action(event, action.id(), action.kind().name(), action.userId(), action.role(), action.target(),
                outcome);
    }

    private void releaseQuietly(String id) {
        try {
            store.unlock(id);
        } catch (ActionStoreException e) {
            log.warn("Action lock not released (it expires by itself): {}", e.getMessage());
        }
    }

    private static void pause() {
        try {
            Thread.sleep(CONFIRM_POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("The request was interrupted.");
        }
    }
}
