package com.fooddelivery.chatservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.chatservice.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByConversationIdAndClientMessageId(Long conversationId, String clientMessageId);

    /** Rate limit: messages written by one sender since a point in time. */
    long countBySenderUserIdAndCreatedAtAfter(Long senderUserId, Instant since);

    /**
     * Newest first: messages older than {@code beforeId} (pass Long.MAX_VALUE for "from the end") whose text contains
     * the lower-case LIKE pattern ('!' is the escape character; "%" matches all). Callers ask for one more row than
     * they show, to know whether there is an older page.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.id < :beforeId and lower(m.body) like :pattern escape '!'
            order by m.id desc
            """)
    List<Message> findPage(@Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId,
                           @Param("pattern") String pattern, Pageable pageable);

    /** Marks the unread messages of one side as read; returns how many changed. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Message m set m.readAt = :now
            where m.conversationId = :conversationId and m.fromStaff = :fromStaff and m.readAt is null
            """)
    int markRead(@Param("conversationId") Long conversationId, @Param("fromStaff") boolean fromStaff,
                 @Param("now") Instant now);

    /** The newest message one side wrote: what "read up to" means for the other side. */
    @Query("select max(m.id) from Message m where m.conversationId = :conversationId and m.fromStaff = :fromStaff")
    Optional<Long> findLatestIdBySide(@Param("conversationId") Long conversationId,
                                      @Param("fromStaff") boolean fromStaff);
}
