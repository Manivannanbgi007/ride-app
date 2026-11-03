package com.fooddelivery.chatservice.controller;

import com.fooddelivery.chatservice.dto.request.MuteRequest;
import com.fooddelivery.chatservice.dto.request.OpenConversationRequest;
import com.fooddelivery.chatservice.dto.request.PinRequest;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.ConversationResponse;
import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.dto.response.MessageResponse;
import com.fooddelivery.chatservice.dto.response.OpenedConversation;
import com.fooddelivery.chatservice.dto.response.PageResponse;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.dto.response.UnreadCountResponse;
import com.fooddelivery.chatservice.service.StaffChatService;
import com.fooddelivery.chatservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: chat", description = "The shared support inbox (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final StaffChatService chatService;

    @Operation(summary = "The inbox",
            description = "My pinned conversations first, then the latest activity first (conversations without a "
                    + "message last). search: case-insensitive text in the name or the last message. "
                    + "Page size at most 50. participant.online is null when presence is unknown")
    @GetMapping("/conversations")
    public PageResponse<ConversationResponse> list(
            @Parameter(description = "Text in the name or the last message")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number, from 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Conversations per page, at most 50")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            CallerInfo caller) {
        return chatService.list(caller.userId(), search, page, size);
    }

    @Operation(summary = "Start a conversation with a user, or open the existing one",
            description = "201 when created, 200 when it existed. 404 for an unknown user, 422 for a platform admin")
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> open(@Valid @RequestBody OpenConversationRequest request,
                                                     CallerInfo caller) {
        OpenedConversation opened = chatService.open(caller.userId(), request);
        return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK).body(opened.conversation());
    }

    @Operation(summary = "One conversation")
    @GetMapping("/conversations/{conversationId}")
    public ConversationResponse get(@PathVariable Long conversationId, CallerInfo caller) {
        return chatService.get(caller.userId(), conversationId);
    }

    @Operation(summary = "Messages of a conversation, the latest page first",
            description = "Same paging as the participant API. q: only messages containing the text "
                    + "(case-insensitive), still paged")
    @GetMapping("/conversations/{conversationId}/messages")
    public MessagePageResponse messages(
            @PathVariable Long conversationId,
            @Parameter(description = "Only messages with a smaller id; omit for the newest")
            @RequestParam(required = false) Long beforeId,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
            @Parameter(description = "Text the message must contain") @RequestParam(required = false) String q) {
        return chatService.messages(conversationId, beforeId, limit, q);
    }

    @Operation(summary = "Answer as Support",
            description = "Signed with my name. Plain text, 1 to 2000 characters after trimming. Sending the same "
                    + "clientMessageId again returns the saved message with 200 instead of 201")
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable Long conversationId,
                                                @Valid @RequestBody SendMessageRequest request, CallerInfo caller) {
        SentMessage sent = chatService.send(caller.userId(), conversationId, request);
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(sent.message());
    }

    @Operation(summary = "I read the participant's messages",
            description = "The inbox is shared: this clears the unread count for every admin")
    @PostMapping("/conversations/{conversationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long conversationId) {
        chatService.markRead(conversationId);
    }

    @Operation(summary = "Pin or unpin a conversation (only for me)")
    @PutMapping("/conversations/{conversationId}/pin")
    public ConversationResponse pin(@PathVariable Long conversationId, @Valid @RequestBody PinRequest request,
                                    CallerInfo caller) {
        return chatService.setPinned(caller.userId(), conversationId, request.pinned());
    }

    @Operation(summary = "Mute or unmute a conversation (only for me)")
    @PutMapping("/conversations/{conversationId}/mute")
    public ConversationResponse mute(@PathVariable Long conversationId, @Valid @RequestBody MuteRequest request,
                                     CallerInfo caller) {
        return chatService.setMuted(caller.userId(), conversationId, request.muted());
    }

    @Operation(summary = "Conversations waiting for an answer",
            description = "How many conversations have participant messages no admin has read (not messages)")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return chatService.unreadCount();
    }
}
