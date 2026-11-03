-- Plain-text messages. The sender's name and role are snapshots, like the ones of the conversation.
CREATE TABLE messages
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id   BIGINT        NOT NULL,
    -- Chosen by the client (a UUID): sending the same one again returns the saved message instead of a duplicate
    client_message_id VARCHAR(36)   NOT NULL,
    sender_user_id    BIGINT        NOT NULL,
    sender_name       VARCHAR(120)  NOT NULL,
    sender_role       VARCHAR(30)   NOT NULL,
    from_staff        BOOLEAN       NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    -- When the OTHER side read it; NULL while unread
    read_at           DATETIME(6)   NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT uk_messages_client_message UNIQUE (conversation_id, client_message_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

-- Paging a conversation by id, newest first
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id, id);
-- Rate limit: how many messages did this sender write in the last minute
CREATE INDEX idx_messages_sender_created ON messages (sender_user_id, created_at);

-- Each admin's own pin and mute of a conversation (the inbox itself is shared)
CREATE TABLE staff_conversation_settings
(
    staff_user_id   BIGINT  NOT NULL,
    conversation_id BIGINT  NOT NULL,
    pinned          BOOLEAN NOT NULL DEFAULT FALSE,
    muted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_staff_conversation_settings PRIMARY KEY (staff_user_id, conversation_id),
    CONSTRAINT fk_staff_settings_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE
);
