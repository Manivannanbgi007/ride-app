package com.fooddelivery.aimcpclient.service;

import java.util.List;
import java.util.UUID;

import com.fooddelivery.aimcpclient.dto.response.ConversationResponse;
import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reading and clearing the caller's own conversation (the user id is part of the storage key). */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationStore store;

    /** Empty list when the conversation does not exist, expired, or belongs to someone else (never revealed). */
    public ConversationResponse view(CallerIdentity caller, UUID conversationId) {
        if (conversationId == null) {
            return new ConversationResponse(null, List.of());
        }
        return new ConversationResponse(conversationId, store.load(caller.userId(), conversationId));
    }

    public void delete(CallerIdentity caller, UUID conversationId) {
        if (conversationId != null) {
            store.delete(caller.userId(), conversationId);
        }
    }
}
