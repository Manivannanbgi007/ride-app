package com.fooddelivery.chatservice.controller;

import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.ConversationResponse;
import com.fooddelivery.chatservice.dto.response.MessagePageResponse;
import com.fooddelivery.chatservice.dto.response.MessageResponse;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.service.ParticipantChatService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Talk to Support (CUSTOMER, RESTAURANT_ADMIN, DELIVERY_PARTNER). "
        + "There is no conversation id: the caller always reaches his own conversation")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ParticipantChatService chatService;

    @Operation(summary = "My conversation with Support", description = "404 when I have never written (show an empty chat)")
    @GetMapping("/conversation")
    public ConversationResponse getConversation(CallerInfo caller) {
        return chatService.getConversation(caller.userId());
    }

    @Operation(summary = "My messages, the latest page first",
            description = "The latest `limit` messages older than beforeId, oldest first; pass nextBeforeId as beforeId "
                    + "for the page before. Empty page when I have no conversation")
    @GetMapping("/messages")
    public MessagePageResponse messages(
            @Parameter(description = "Only messages with a smaller id; omit for the newest")
            @RequestParam(required = false) Long beforeId,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
            CallerInfo caller) {
        return chatService.messages(caller.userId(), beforeId, limit);
    }

    @Operation(summary = "Write to Support",
            description = "Plain text, 1 to 2000 characters after trimming. Creates the conversation on the first message. "
                    + "Sending the same clientMessageId again returns the saved message with 200 instead of 201. "
                    + "429 above 30 messages a minute, 503 when the sender cannot be identified (nothing saved)")
    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody SendMessageRequest request, CallerInfo caller) {
        SentMessage sent = chatService.send(caller.userId(), request);
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(sent.message());
    }

    @Operation(summary = "I read Support's messages",
            description = "Turns their ticks double; silent when nothing was unread")
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(CallerInfo caller) {
        chatService.markRead(caller.userId());
    }
}
