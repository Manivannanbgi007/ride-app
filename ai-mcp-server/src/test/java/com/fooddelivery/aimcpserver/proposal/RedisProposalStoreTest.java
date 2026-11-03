package com.fooddelivery.aimcpserver.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fooddelivery.aimcpserver.dto.ConfirmResponse;
import com.fooddelivery.aimcpserver.dto.PendingAction;
import com.fooddelivery.aimcpserver.testsupport.Fixtures;
import com.fooddelivery.aimcpserver.testsupport.Responses;
import com.fooddelivery.aimcpserver.testsupport.StubGateway;
import com.fooddelivery.aimcpserver.testsupport.ToolRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/** Against a real Redis (Testcontainers): the proposal store, its expiry, the SET NX lock and a concurrent confirm. */
@Testcontainers(disabledWithoutDocker = true)
class RedisProposalStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10.2-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static RedisProposalStore store;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        store = new RedisProposalStore(new StringRedisTemplate(factory), JsonMapper.builder().build());
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    private static Proposal proposal(String id) {
        PendingAction.Summary summary = new PendingAction.Summary("Spice Hub",
                List.of(new PendingAction.Line("Chicken Biryani", 2, new BigDecimal("200.00"), new BigDecimal("400.00"))),
                "12 Lake View, Pune", "CASH_ON_DELIVERY", new BigDecimal("400.00"), BigDecimal.ZERO,
                new BigDecimal("40.00"), new BigDecimal("20.00"), new BigDecimal("460.00"), "INR", null, List.of("w"));
        Instant now = Instant.parse("2026-10-09T06:00:00Z");
        return new Proposal(id, 1, Proposal.Status.NEW, now, now.plus(Duration.ofMinutes(10)), 7,
                List.of(new Proposal.Line(31, 2)), 5, "CASH_ON_DELIVERY", null, "less spicy", summary, null, null, null,
                null, null);
    }

    @Test
    void roundTripKeepsEveryFieldAndExpiresAfterTheTtl() throws Exception {
        String id = UUID.randomUUID().toString();
        store.save(proposal(id), Duration.ofSeconds(1));

        Proposal read = store.find(id).orElseThrow();

        assertThat(read).isEqualTo(proposal(id));
        assertThat(read.summary().total()).isEqualByComparingTo("460.00");
        Thread.sleep(1500);
        assertThat(store.find(id)).isEmpty();
    }

    @Test
    void lockIsSetIfAbsentExpiresAndCanBeReleased() throws Exception {
        String id = UUID.randomUUID().toString();

        assertThat(store.tryLock(id, Duration.ofSeconds(1))).isTrue();
        assertThat(store.tryLock(id, Duration.ofSeconds(1))).isFalse();
        Thread.sleep(1300);
        assertThat(store.tryLock(id, Duration.ofSeconds(30))).isTrue();
        store.unlock(id);
        assertThat(store.tryLock(id, Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void lockExactlyOneOfManyConcurrentCallersWins() throws Exception {
        String id = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tasks.add(() -> store.tryLock(id, Duration.ofSeconds(30)));
        }

        long winners = 0;
        for (Future<Boolean> result : pool.invokeAll(tasks)) {
            winners += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
    }

    @Test
    void confirmOverRealRedisCreatesExactlyOneOrderUnderConcurrentClicks() throws Exception {
        try (StubGateway gateway = new StubGateway()) {
            List<String> carts = new ArrayList<>(List.of(Responses.EMPTY_CART, Responses.PRICED_CART));
            gateway.on("GET", "/api/v1/restaurants", 200, Responses.RESTAURANT_LIST);
            gateway.on("GET", "/api/v1/restaurants/7", 200, Responses.RESTAURANT_DETAILS);
            gateway.on("GET", "/api/v1/restaurants/7/menu", 200, Responses.MENU);
            gateway.on("GET", "/api/v1/users/me/addresses", 200, Responses.ADDRESSES);
            gateway.on("PUT", "/api/v1/cart/items/31", 200, Responses.PRICED_CART);
            gateway.on("DELETE", "/api/v1/cart", 204, null);
            gateway.on("GET", "/api/v1/cart", call -> new StubGateway.Reply(200,
                    carts.size() > 1 ? carts.remove(0) : carts.get(0)));
            gateway.on("POST", "/api/v1/orders", call -> {
                sleep(200);
                return new StubGateway.Reply(201, Responses.ORDER_CREATED);
            });
            Fixtures.Stack stack = Fixtures.stack(gateway.url(), Fixtures.clock(), store);
            var run = ToolRunner.newRun();
            ToolRunner.call(stack, Fixtures.CUSTOMER, run, "prepare_order",
                    "{\"restaurant\":\"Spice Hub\",\"items\":[{\"dish\":\"chicken biryani\",\"quantity\":2}],\"address\":\"5\","
                            + "\"paymentMethod\":\"CASH_ON_DELIVERY\"}");
            String actionId = run.pendingAction().actionId();
            ExecutorService pool = Executors.newFixedThreadPool(6);
            List<Callable<ConfirmResponse>> clicks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                clicks.add(() -> stack.proposals().confirm(Fixtures.CUSTOMER, actionId));
            }

            List<Future<ConfirmResponse>> results = pool.invokeAll(clicks);
            pool.shutdown();

            long created = 0;
            for (Future<ConfirmResponse> result : results) {
                created += result.get().status() == ConfirmResponse.Status.CREATED ? 1 : 0;
            }
            assertThat(created).isEqualTo(1);
            assertThat(gateway.count("POST", "/api/v1/orders")).isEqualTo(1);
            assertThat(store.find(actionId).orElseThrow().status()).isEqualTo(Proposal.Status.CONFIRMED);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
