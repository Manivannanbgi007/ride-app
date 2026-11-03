package com.fooddelivery.aimcpclient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fooddelivery.aimcpclient.config.AppProperties;
import com.fooddelivery.aimcpclient.dto.response.StoredMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The conversation store against a real Redis (Testcontainers); the "Redis is down" case uses a MOCKED template. */
class ConversationStoreRedisTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private ConversationStore store;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redisContainer.stop();
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        store = new ConversationStore(redis, JSON, properties());
    }

    @Test
    void messagesComeBackInOrderWithTheirCardsAndPendingAction() {
        UUID conversation = UUID.randomUUID();
        store.append(1, conversation, List.of(
                message("USER", "hi", null, null),
                message("ASSISTANT", "hello", json("[{\"type\":\"restaurant\"}]"), json("{\"actionId\":\"act-1\"}"))));

        List<StoredMessage> loaded = store.load(1, conversation);

        assertThat(loaded).extracting(StoredMessage::text).containsExactly("hi", "hello");
        assertThat(loaded.get(1).cards().get(0).get("type").asString()).isEqualTo("restaurant");
        assertThat(loaded.get(1).pendingAction().get("actionId").asString()).isEqualTo("act-1");
        assertThat(store.exists(1, conversation)).isTrue();
    }

    @Test
    void lastMessagesReturnsTheNewestOnesOldestFirst() {
        UUID conversation = UUID.randomUUID();
        for (int i = 1; i <= 10; i++) {
            store.append(1, conversation, List.of(message("USER", "m" + i, null, null)));
        }

        assertThat(store.lastMessages(1, conversation, 3)).extracting(StoredMessage::text).containsExactly("m8", "m9", "m10");
    }

    @Test
    void atMost40MessagesAreKeptAndTheOldestAreDropped() {
        UUID conversation = UUID.randomUUID();
        for (int i = 1; i <= 50; i++) {
            store.append(1, conversation, List.of(message("USER", "m" + i, null, null)));
        }

        List<StoredMessage> loaded = store.load(1, conversation);

        assertThat(loaded).hasSize(40);
        assertThat(loaded.get(0).text()).isEqualTo("m11");
        assertThat(loaded.get(39).text()).isEqualTo("m50");
    }

    @Test
    void atMost5ConversationsPerUserAreKeptAndTheOldestIsDropped() throws InterruptedException {
        UUID oldest = UUID.randomUUID();
        store.append(1, oldest, List.of(message("USER", "first", null, null)));
        List<UUID> others = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (UUID id : others) {
            TimeUnit.MILLISECONDS.sleep(5); // the index is ordered by last use in milliseconds
            store.append(1, id, List.of(message("USER", "x", null, null)));
        }
        assertThat(store.exists(1, oldest)).isTrue();

        TimeUnit.MILLISECONDS.sleep(5);
        store.append(1, UUID.randomUUID(), List.of(message("USER", "sixth", null, null)));

        assertThat(store.exists(1, oldest)).isFalse();
        assertThat(others).allMatch(id -> store.exists(1, id));
    }

    @Test
    void oneUserCannotReadAnotherUsersConversation() {
        UUID conversation = UUID.randomUUID();
        store.append(1, conversation, List.of(message("USER", "private", null, null)));

        assertThat(store.exists(2, conversation)).isFalse();
        assertThat(store.load(2, conversation)).isEmpty();
        assertThat(store.load(1, conversation)).hasSize(1);
    }

    @Test
    void aConversationExpiresAfterTheTtlAndUseRenewsIt() {
        UUID conversation = UUID.randomUUID();
        store.append(1, conversation, List.of(message("USER", "hi", null, null)));
        String key = "ai:conv:1:" + conversation;
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(7_000L, 7_200L);

        redis.expire(key, Duration.ofSeconds(30));
        store.load(1, conversation);

        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(7_000L);
    }

    @Test
    void clearingAPendingActionLeavesTheRestOfTheMessageAndFindsTheConversation() {
        UUID conversation = UUID.randomUUID();
        store.append(1, conversation, List.of(
                message("ASSISTANT", "proposal", json("[{\"c\":1}]"), json("{\"actionId\":\"act-9\",\"total\":10}"))));

        Optional<UUID> found = store.clearPendingAction(1, "act-9");

        assertThat(found).contains(conversation);
        StoredMessage stored = store.load(1, conversation).get(0);
        assertThat(stored.pendingAction()).isNull();
        assertThat(stored.text()).isEqualTo("proposal");
        assertThat(stored.cards()).isNotNull();
        assertThat(store.clearPendingAction(1, "act-9")).isEmpty();
        assertThat(store.clearPendingAction(2, "act-9")).isEmpty();
    }

    @Test
    void longTextsAreCutAndHugeCardsAreLeftOut() {
        UUID conversation = UUID.randomUUID();
        String hugeCards = "[\"" + "y".repeat(70_000) + "\"]";
        store.append(1, conversation, List.of(
                message("ASSISTANT", "t".repeat(9_000), json(hugeCards), null)));

        StoredMessage stored = store.load(1, conversation).get(0);

        assertThat(stored.text()).hasSize(4_000).endsWith("...");
        assertThat(stored.cards()).isNull();
    }

    @Test
    void deleteRemovesTheConversation() {
        UUID conversation = UUID.randomUUID();
        store.append(1, conversation, List.of(message("USER", "hi", null, null)));

        store.delete(1, conversation);

        assertThat(store.exists(1, conversation)).isFalse();
        assertThat(store.load(1, conversation)).isEmpty();
    }

    @Test
    void whenRedisIsDownThereIsNoHistoryAndNoError() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForList()).thenThrow(new RedisConnectionFailureException("MOCKED: Redis is down"));
        when(broken.opsForZSet()).thenThrow(new RedisConnectionFailureException("MOCKED: Redis is down"));
        ConversationStore downStore = new ConversationStore(broken, JSON, properties());
        UUID conversation = UUID.randomUUID();

        assertThat(downStore.load(1, conversation)).isEmpty();
        assertThat(downStore.lastMessages(1, conversation, 12)).isEmpty();
        assertThat(downStore.exists(1, conversation)).isFalse();
        assertThatCode(() -> downStore.append(1, conversation, List.of(message("USER", "hi", null, null))))
                .doesNotThrowAnyException();
        assertThat(downStore.clearPendingAction(1, "a")).isEmpty();
    }

    private static StoredMessage message(String role, String text, JsonNode cards, JsonNode pendingAction) {
        return new StoredMessage(UUID.randomUUID().toString(), role, text, cards, null, pendingAction, Instant.now());
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    private static AppProperties properties() {
        return new AppProperties(null, null,
                new AppProperties.Conversation(Duration.ofHours(2), 40, 5, 12, 4_000, 65_536), null);
    }
}
