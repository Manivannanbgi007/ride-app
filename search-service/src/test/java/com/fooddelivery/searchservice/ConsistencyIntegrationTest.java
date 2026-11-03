package com.fooddelivery.searchservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.searchservice.client.SourceOfTruthClient;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.SourceVersions;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import com.fooddelivery.searchservice.service.IndexVersionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The consistency check and repair against a real Elasticsearch and Kafka. restaurant-service and catalog-service are
 * faked ("MySQL" is a map the test controls); the index is real, and so is the indexer that rebuilds it from events.
 * The fake MySQL starts as an exact copy of whatever the index holds (other tests share it), so only the differences
 * a test makes show up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ConsistencyIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ElasticsearchOperations operations;
    @Autowired
    private IndexVersionReader reader;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @MockitoBean
    private SourceOfTruthClient source;

    /** What "MySQL" holds: id -> version, per index */
    private final Map<SearchedIndex, TreeMap<Long, Long>> mysql = new EnumMap<>(SearchedIndex.class);

    @BeforeEach
    void mysqlStartsAsACopyOfTheIndex() {
        for (SearchedIndex searched : SearchedIndex.values()) {
            TreeMap<Long, Long> rows = new TreeMap<>();
            long after = 0;
            List<IndexVersionReader.Entry> page;
            do {
                page = reader.readAfter(searched, after, 500);
                page.forEach(entry -> rows.put(entry.id(), entry.version()));
                after = page.isEmpty() ? after : page.getLast().id();
            } while (!page.isEmpty());
            mysql.put(searched, rows);
        }
        when(source.versions(any(), anyLong(), anyInt())).thenAnswer(call -> {
            SearchedIndex searched = call.getArgument(0);
            long after = call.getArgument(1);
            int size = call.getArgument(2);
            return new SourceVersions(mysql.get(searched).tailMap(after, false).entrySet().stream().limit(size)
                    .map(e -> new SourceVersions.Entry(e.getKey(), e.getValue())).toList(), null);
        });
        when(source.exists(any(), anyLong())).thenAnswer(call -> mysql.get((SearchedIndex) call.getArgument(0))
                .containsKey((Long) call.getArgument(1)));
    }

    @Test
    void theReportNamesEveryDifferenceAndOnlyTheOnesThatAreThere() throws Exception {
        long id = randomId();
        index(id, 3);                     // consistent
        mysql.get(SearchedIndex.RESTAURANT).put(id, 3L);
        index(id + 1, 3);                 // an orphan: MySQL has no such restaurant
        index(id + 2, 3);                 // stale: MySQL is at version 5
        mysql.get(SearchedIndex.RESTAURANT).put(id + 2, 5L);
        index(id + 3, 3);                 // ahead: MySQL is at version 2
        mysql.get(SearchedIndex.RESTAURANT).put(id + 3, 2L);
        mysql.get(SearchedIndex.RESTAURANT).put(id + 4, 1L);   // missing from the index
        mysql.get(SearchedIndex.RESTAURANT).put(id + 5, 1L);   // missing from the index

        JsonNode report = json(get("/api/v1/admin/search/consistency"));

        assertThat(report.get("consistent").asBoolean()).isFalse();
        JsonNode restaurants = indexOf(report, "restaurant");
        assertThat(restaurants.get("missing").asInt()).isEqualTo(2);
        assertThat(restaurants.get("stale").asInt()).isEqualTo(1);
        assertThat(restaurants.get("ahead").asInt()).isEqualTo(1);
        assertThat(restaurants.get("orphans").asInt()).isEqualTo(1);
        assertThat(restaurants.get("sampleOrphans").get(0).asLong()).isEqualTo(id + 1);
        assertThat(restaurants.get("sampleMissing")).extracting(JsonNode::asLong).containsExactly(id + 4, id + 5);
        // The other indexes match exactly
        assertThat(indexOf(report, "food").get("missing").asInt()).isZero();
        assertThat(indexOf(report, "category").get("orphans").asInt()).isZero();
        // A check only looks
        assertThat(operations.exists(String.valueOf(id + 1), RestaurantDocument.class)).isTrue();
        verify(source, never()).republish(any(), any());
    }

    @Test
    void aRowCreatedWhileTheCheckRanIsNotRemovedAsAnOrphan() throws Exception {
        long id = randomId();
        index(id, 1);
        // The list of MySQL was read before the row existed, the answer to "is it there?" comes after it was created
        when(source.exists(SearchedIndex.RESTAURANT, id)).thenReturn(true);

        JsonNode result = json(post("/api/v1/admin/search/consistency/repair"));

        assertThat(repairOf(result, "restaurant").get("deleted").asInt()).isZero();
        assertThat(operations.exists(String.valueOf(id), RestaurantDocument.class)).isTrue();
    }

    @Test
    void repairRemovesWhatMysqlDoesNotHaveAndAsksOnlyTheOwnerOfTheAffectedIndexToPublishAgain() throws Exception {
        long id = randomId();
        index(id, 3);                                           // an orphan
        index(id + 1, 9);                                       // ahead of MySQL
        mysql.get(SearchedIndex.RESTAURANT).put(id + 1, 2L);
        when(source.republish(eq(SearchedIndex.RESTAURANT), any())).thenReturn(7);

        JsonNode result = json(post("/api/v1/admin/search/consistency/repair"));

        assertThat(result.get("before").get("consistent").asBoolean()).isFalse();
        assertThat(repairOf(result, "restaurant").get("deleted").asInt()).isEqualTo(2);
        assertThat(repairOf(result, "restaurant").get("republished").asInt()).isEqualTo(7);
        assertThat(repairOf(result, "food").get("republished").asInt()).isZero();

        assertThat(operations.exists(String.valueOf(id), RestaurantDocument.class)).isFalse();
        assertThat(operations.exists(String.valueOf(id + 1), RestaurantDocument.class)).isFalse();
        verify(source).republish(eq(SearchedIndex.RESTAURANT), any());
        verify(source, never()).republish(eq(SearchedIndex.FOOD), any());
        verify(source, never()).republish(eq(SearchedIndex.CATEGORY), any());
    }

    @Test
    void aLostEventIsRecoveredEndToEndWhenTheOwnerRepublishesAndTheRealIndexerRebuildsTheDocument() throws Exception {
        long id = randomId();
        mysql.get(SearchedIndex.RESTAURANT).put(id, 4L);         // MySQL has it; the event never reached the index
        // The owning service, asked to republish, publishes the current state to Kafka as it really does
        when(source.republish(eq(SearchedIndex.RESTAURANT), any())).thenAnswer(call -> {
            kafkaTemplate.send(Topics.RESTAURANT_CHANGED, String.valueOf(id), restaurantEvent(id, 4)).get();
            return 1;
        });
        assertThat(indexOf(json(get("/api/v1/admin/search/consistency")), "restaurant").get("missing").asInt()).isEqualTo(1);

        mockMvc.perform(admin(post("/api/v1/admin/search/consistency/repair"))).andExpect(status().isOk());

        // Seconds later the document is there at the right version and the check is clean
        await().atMost(TIMEOUT).untilAsserted(() -> mockMvc.perform(admin(get("/api/v1/admin/search/consistency")))
                .andExpect(jsonPath("$.consistent").value(true)));
        RestaurantDocument rebuilt = operations.get(String.valueOf(id), RestaurantDocument.class);
        assertThat(rebuilt).isNotNull();
        assertThat(rebuilt.getSourceVersion()).isEqualTo(4L);
        assertThat(rebuilt.getName()).isEqualTo("Recovered Kitchen");
    }

    @Test
    void whenAnOwnerCannotBeReachedTheAnswerIs503AndNothingIsDeleted() throws Exception {
        long id = randomId();
        index(id, 3);                                           // would be an orphan
        doThrow(new SourceUnavailableException("restaurant-service", new RuntimeException("connection refused")))
                .when(source).versions(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt());

        mockMvc.perform(admin(post("/api/v1/admin/search/consistency/repair")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Source unavailable"));
        mockMvc.perform(admin(get("/api/v1/admin/search/consistency"))).andExpect(status().isServiceUnavailable());

        assertThat(operations.exists(String.valueOf(id), RestaurantDocument.class)).isTrue();
        verify(source, never()).republish(any(), any());
    }

    @Test
    void onlyPlatformAdminsMayCheckOrRepair() throws Exception {
        mockMvc.perform(get("/api/v1/admin/search/consistency")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/search/consistency").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/search/consistency/repair")
                        .with(TestTokens.as("7", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());

        verify(source, never()).versions(any(), anyLong(), anyInt());
        verify(source, never()).republish(any(), any());
    }

    // ---- helpers

    private void index(long id, long version) {
        RestaurantDocument restaurant = new RestaurantDocument();
        restaurant.setId(id);
        restaurant.setName("Drift Test " + id);
        restaurant.setCity("Driftville");
        restaurant.setCuisines(List.of("Test"));
        restaurant.setStatus("ACTIVE");
        restaurant.setSourceVersion(version);
        restaurant.setIndexedAt(Instant.now());
        operations.save(restaurant);
        operations.indexOps(RestaurantDocument.class).refresh();
    }

    private static RestaurantChanged restaurantEvent(long id, long version) {
        return RestaurantChanged.newBuilder()
                .setEventId(java.util.UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("REPUBLISHED").setRestaurantId(id).setVersion(version).setOwnerUserId(3L)
                .setName("Recovered Kitchen").setCity("Driftville").setStatus("ACTIVE")
                .setDescription("Rebuilt from MySQL").setCuisines(List.of("Test"))
                .setAddress(RestaurantAddress.newBuilder().setLine1("1 Test Road").setCity("Driftville")
                        .setState("Karnataka").setPostalCode("560001").setCountry("India").build())
                .build();
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Calls the API as a platform admin and reads the answer. */
    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return JSON.readTree(mockMvc.perform(admin(request)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static JsonNode indexOf(JsonNode report, String index) {
        return find(report.get("indexes"), index);
    }

    private static JsonNode repairOf(JsonNode result, String index) {
        return find(result.get("indexes"), index);
    }

    private static JsonNode find(JsonNode indexes, String index) {
        for (JsonNode entry : indexes) {
            if (index.equals(entry.get("index").asString())) {
                return entry;
            }
        }
        throw new AssertionError("No " + index + " in " + indexes);
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as("1", "PLATFORM_ADMIN"));
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_900_000_000L);
    }
}
