package com.fooddelivery.notificationservice.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fooddelivery.notificationservice.exception.InvalidRequestException;
import com.fooddelivery.notificationservice.web.LiveSessions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API (chat-service): who is online. The API Gateway has no route for /api/v1/internal/**.
 * <p>
 * "Online" means at least one open live connection on THIS instance. With several instances the answer covers this
 * instance's connections only; a multi-instance deployment needs a shared presence store (see
 * {@link LiveSessions#onlineAmong}).
 */
@Tag(name = "Internal: presence", description = "For other services only; not exposed through the API Gateway")
@RestController
@RequestMapping("/api/v1/internal/presence")
@ConditionalOnProperty(prefix = "app.realtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalPresenceController {

    static final int MAX_USER_IDS = 100;

    private final LiveSessions sessions;

    public InternalPresenceController(LiveSessions sessions) {
        this.sessions = sessions;
    }

    /** The user is among {@code online} when they have an open live connection. */
    public record PresenceResponse(List<Long> online) {
    }

    @Operation(summary = "Which of these users (1 to 100 ids, comma separated) are connected right now")
    @GetMapping
    public PresenceResponse presence(@RequestParam("userIds") String userIds) {
        Set<Long> ids = parse(userIds);
        return new PresenceResponse(List.copyOf(sessions.onlineAmong(ids)));
    }

    private static Set<Long> parse(String userIds) {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            for (String part : userIds.split(",", -1)) {
                ids.add(Long.parseLong(part.trim()));
            }
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("userIds must be a comma-separated list of numeric ids");
        }
        if (ids.size() > MAX_USER_IDS) {
            throw new InvalidRequestException("userIds takes at most " + MAX_USER_IDS + " ids");
        }
        return ids;
    }
}
