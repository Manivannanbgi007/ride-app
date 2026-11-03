package com.fooddelivery.chatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.chatservice.client.PresenceLookup;
import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.client.UserLookup;
import com.fooddelivery.chatservice.dto.request.OpenConversationRequest;
import com.fooddelivery.chatservice.dto.request.SendMessageRequest;
import com.fooddelivery.chatservice.dto.response.ConversationResponse;
import com.fooddelivery.chatservice.dto.response.OpenedConversation;
import com.fooddelivery.chatservice.dto.response.PageResponse;
import com.fooddelivery.chatservice.dto.response.SentMessage;
import com.fooddelivery.chatservice.entity.Conversation;
import com.fooddelivery.chatservice.entity.StaffConversationSettings;
import com.fooddelivery.chatservice.exception.BusinessRuleException;
import com.fooddelivery.chatservice.exception.DependencyUnavailableException;
import com.fooddelivery.chatservice.exception.ResourceNotFoundException;
import com.fooddelivery.chatservice.mapper.ChatMapper;
import com.fooddelivery.chatservice.repository.ConversationRepository;
import com.fooddelivery.chatservice.repository.ConversationRow;
import com.fooddelivery.chatservice.repository.StaffConversationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** The support team's decisions with the repositories, user-service and notification-service replaced by mocks. */
class StaffChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final long ADMIN = 1L;
    private static final SendMessageRequest REQUEST =
            new SendMessageRequest("On it", "6f1c3a52-0f4b-4d52-9e56-3d6d3f0f0a11");

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final StaffConversationSettingsRepository settingsRepository = mock(StaffConversationSettingsRepository.class);
    private final ChatMessageWriter messageWriter = mock(ChatMessageWriter.class);
    private final UserLookup userLookup = mock(UserLookup.class);
    private final PresenceLookup presenceLookup = mock(PresenceLookup.class);
    private StaffChatService service;

    @BeforeEach
    void setUp() {
        service = new StaffChatService(conversationRepository, settingsRepository, mock(MessageReader.class),
                messageWriter, userLookup, presenceLookup, new ChatMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
        when(messageWriter.append(any(), any(), any())).thenReturn(new SentMessage(null, false));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(call -> {
            Conversation saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
    }

    private static Conversation conversation(long id, long participant, String name) {
        Conversation conversation = new Conversation(participant, name, "CUSTOMER", NOW);
        ReflectionTestUtils.setField(conversation, "id", id);
        return conversation;
    }

    @Test
    void openingAnExistingConversationReturnsItWithoutAskingUserService() {
        Conversation existing = conversation(5L, 7L, "Asha");
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.of(existing));
        when(conversationRepository.findRow(ADMIN, 5L)).thenReturn(Optional.of(new ConversationRow(existing, true, false)));

        OpenedConversation opened = service.open(ADMIN, new OpenConversationRequest(7L));

        assertThat(opened.created()).isFalse();
        assertThat(opened.conversation().pinned()).isTrue();
        verifyNoInteractions(userLookup);
    }

    @Test
    void openingANewConversationCopiesTheUsersNameAndRole() {
        when(conversationRepository.findByParticipantUserId(7L)).thenReturn(Optional.empty());
        when(userLookup.findUser(7L)).thenReturn(Optional.of(new UserInfo(7L, "Asha Rao", "DELIVERY_PARTNER")));
        when(conversationRepository.findRow(eq(ADMIN), anyLong())).thenAnswer(call -> {
            ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(saved.capture());
            return Optional.of(new ConversationRow(saved.getValue(), false, false));
        });

        OpenedConversation opened = service.open(ADMIN, new OpenConversationRequest(7L));

        assertThat(opened.created()).isTrue();
        assertThat(opened.conversation().participant().name()).isEqualTo("Asha Rao");
        assertThat(opened.conversation().participant().role()).isEqualTo("DELIVERY_PARTNER");
        assertThat(opened.conversation().lastMessage()).isNull();
    }

    @Test
    void anAdminCannotBeTheParticipantOfAConversation() {
        when(conversationRepository.findByParticipantUserId(2L)).thenReturn(Optional.empty());
        when(userLookup.findUser(2L)).thenReturn(Optional.of(new UserInfo(2L, "Other Admin", "PLATFORM_ADMIN")));

        assertThatThrownBy(() -> service.open(ADMIN, new OpenConversationRequest(2L)))
                .isInstanceOf(BusinessRuleException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void anUnknownUserIsNotFound() {
        when(conversationRepository.findByParticipantUserId(99L)).thenReturn(Optional.empty());
        when(userLookup.findUser(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(ADMIN, new OpenConversationRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aReplyIsSignedWithTheAdminsNameFromUserService() {
        when(userLookup.findUser(ADMIN)).thenReturn(Optional.of(new UserInfo(ADMIN, "Ravi Kumar", "PLATFORM_ADMIN")));
        when(conversationRepository.lockById(5L)).thenReturn(Optional.of(conversation(5L, 7L, "Asha")));

        service.send(ADMIN, 5L, REQUEST);

        verify(messageWriter).append(any(), eq(REQUEST),
                eq(new ChatMessageWriter.Sender(ADMIN, "Ravi Kumar", "PLATFORM_ADMIN", true)));
    }

    @Test
    void aReplyIsSignedSupportWhenUserServiceCannotTellTheName() {
        when(userLookup.findUser(ADMIN)).thenThrow(new DependencyUnavailableException("user-service",
                new IllegalStateException("down")));
        when(conversationRepository.lockById(5L)).thenReturn(Optional.of(conversation(5L, 7L, "Asha")));

        service.send(ADMIN, 5L, REQUEST);

        verify(messageWriter).append(any(), eq(REQUEST),
                eq(new ChatMessageWriter.Sender(ADMIN, "Support", "PLATFORM_ADMIN", true)));
    }

    @Test
    void aReplyToAnUnknownConversationIsNotFoundAndNothingIsWritten() {
        when(userLookup.findUser(ADMIN)).thenReturn(Optional.empty());
        when(conversationRepository.lockById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(ADMIN, 404L, REQUEST)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(messageWriter);
    }

    @Test
    void theInboxShowsPresenceFromOneCallAndUnknownWhenItFails() {
        Conversation asha = conversation(5L, 7L, "Asha");
        Conversation ben = conversation(6L, 8L, "Ben");
        when(conversationRepository.findInbox(eq(ADMIN), eq("%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new ConversationRow(asha, false, false),
                        new ConversationRow(ben, false, false))));
        when(presenceLookup.findOnline(List.of(7L, 8L))).thenReturn(Optional.of(Set.of(7L)));

        PageResponse<ConversationResponse> online = service.list(ADMIN, null, 0, 20);

        assertThat(online.content()).extracting(c -> c.participant().online()).containsExactly(true, false);

        when(presenceLookup.findOnline(any())).thenReturn(Optional.empty());
        PageResponse<ConversationResponse> unknown = service.list(ADMIN, null, 0, 20);

        assertThat(unknown.content()).extracting(c -> c.participant().online()).containsExactly(null, null);
    }

    @Test
    void pinningCreatesTheAdminsOwnSettingsRow() {
        Conversation asha = conversation(5L, 7L, "Asha");
        when(conversationRepository.findRow(ADMIN, 5L)).thenReturn(Optional.of(new ConversationRow(asha, false, false)));
        when(settingsRepository.findById(any())).thenReturn(Optional.empty());

        ConversationResponse response = service.setPinned(ADMIN, 5L, true);

        assertThat(response.pinned()).isTrue();
        assertThat(response.muted()).isFalse();
        ArgumentCaptor<StaffConversationSettings> saved = ArgumentCaptor.forClass(StaffConversationSettings.class);
        verify(settingsRepository).save(saved.capture());
        assertThat(saved.getValue().getId().getStaffUserId()).isEqualTo(ADMIN);
        assertThat(saved.getValue().getId().getConversationId()).isEqualTo(5L);
    }

    @Test
    void pinningAnUnknownConversationIsNotFound() {
        when(conversationRepository.findRow(ADMIN, 404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPinned(ADMIN, 404L, true)).isInstanceOf(ResourceNotFoundException.class);

        verify(settingsRepository, never()).save(any());
    }
}
