package com.fooddelivery.chatservice.repository;

import java.util.Optional;

import com.fooddelivery.chatservice.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByParticipantUserId(Long participantUserId);

    /**
     * Locks the row until the transaction ends. Everything that changes a conversation (a new message, reading it)
     * takes this lock first, so two requests for one conversation run one after the other: the unread counters stay
     * exact and a repeated clientMessageId is always seen by the second request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.participantUserId = :participantUserId")
    Optional<Conversation> lockByParticipantUserId(@Param("participantUserId") Long participantUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> lockById(@Param("id") Long id);

    /** Conversations with something no admin has read yet (the badge of the inbox). */
    long countByUnreadForStaffGreaterThan(int unread);

    /**
     * The inbox of one admin: his pinned conversations first, then the most recently active ones (conversations
     * without a message last). {@code pattern} is a LIKE pattern in lower case that uses '!' as its escape character
     * (see ChatRules.containsPattern); "%" matches everything (a conversation without a message has an empty
     * preview). Contains-search on the name and the preview is a scan, which is fine for a support inbox
     * (thousands of conversations, not millions); the name index serves sorting by name later.
     * If it ever hurts, a FULLTEXT index is the next step.
     */
    @Query(value = """
            select new com.fooddelivery.chatservice.repository.ConversationRow(
                c, coalesce(s.pinned, false), coalesce(s.muted, false))
            from Conversation c
            left join StaffConversationSettings s on s.id.conversationId = c.id and s.id.staffUserId = :staffUserId
            where lower(c.participantName) like :pattern escape '!'
               or lower(coalesce(c.lastMessagePreview, '')) like :pattern escape '!'
            order by coalesce(s.pinned, false) desc, c.lastMessageAt desc nulls last, c.id desc
            """,
            countQuery = """
            select count(c) from Conversation c
            where lower(c.participantName) like :pattern escape '!'
               or lower(coalesce(c.lastMessagePreview, '')) like :pattern escape '!'
            """)
    Page<ConversationRow> findInbox(@Param("staffUserId") Long staffUserId, @Param("pattern") String pattern,
                                    Pageable pageable);

    @Query("""
            select new com.fooddelivery.chatservice.repository.ConversationRow(
                c, coalesce(s.pinned, false), coalesce(s.muted, false))
            from Conversation c
            left join StaffConversationSettings s on s.id.conversationId = c.id and s.id.staffUserId = :staffUserId
            where c.id = :id
            """)
    Optional<ConversationRow> findRow(@Param("staffUserId") Long staffUserId, @Param("id") Long id);
}
