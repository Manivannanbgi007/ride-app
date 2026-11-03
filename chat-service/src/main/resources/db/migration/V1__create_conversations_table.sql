-- Support chat: one conversation per customer, restaurant owner or delivery partner (the "participant"); the other
-- side is always "Support", the shared inbox of all platform admins. Name and role are snapshots taken when the
-- conversation is created, so the inbox never needs user-service to be listed.
CREATE TABLE conversations
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    -- One conversation per participant (the unique key also settles two simultaneous first messages)
    participant_user_id      BIGINT       NOT NULL,
    participant_name         VARCHAR(120) NOT NULL,
    participant_role         VARCHAR(30)  NOT NULL,
    -- Denormalized from the latest message, so the inbox is one query. NULL until the first message.
    last_message_at          DATETIME(6)  NULL,
    last_message_preview     VARCHAR(200) NULL,
    last_message_from_staff  BOOLEAN      NULL,
    -- Participant messages no admin has read yet / support messages the participant has not read yet
    unread_for_staff         INT          NOT NULL DEFAULT 0,
    unread_for_participant   INT          NOT NULL DEFAULT 0,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    version                  BIGINT       NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT uk_conversations_participant UNIQUE (participant_user_id)
);

-- Inbox order (newest first)
CREATE INDEX idx_conversations_last_message_at ON conversations (last_message_at);
CREATE INDEX idx_conversations_participant_name ON conversations (participant_name);
