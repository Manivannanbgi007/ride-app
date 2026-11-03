package com.fooddelivery.notificationservice.controller;

import com.fooddelivery.notificationservice.dto.response.NotificationResponse;
import com.fooddelivery.notificationservice.dto.response.PageResponse;
import com.fooddelivery.notificationservice.dto.response.UnreadCountResponse;
import com.fooddelivery.notificationservice.service.InboxService;
import com.fooddelivery.notificationservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inbox", description = "The caller's in-app notifications (any role)")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @Operation(summary = "My notifications, newest first")
    @GetMapping
    public PageResponse<NotificationResponse> getInbox(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable,
            CallerInfo caller) {
        return inboxService.getInbox(caller.userId(), unreadOnly, pageable);
    }

    @Operation(summary = "How many of my notifications are unread (the badge on the bell)")
    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(CallerInfo caller) {
        return inboxService.countUnread(caller.userId());
    }

    @Operation(summary = "Mark one notification as read")
    @PutMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable Long notificationId, CallerInfo caller) {
        return inboxService.markRead(caller.userId(), notificationId);
    }

    @Operation(summary = "Mark all my notifications as read")
    @PutMapping("/read-all")
    public UnreadCountResponse markAllRead(CallerInfo caller) {
        return inboxService.markAllRead(caller.userId());
    }
}
