package com.fooddelivery.searchservice;

import static com.fooddelivery.searchservice.ReviewServiceStub.MENU_ITEMS;
import static com.fooddelivery.searchservice.ReviewServiceStub.RESTAURANTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fooddelivery.searchservice.ReviewServiceStub.Rated;
import com.fooddelivery.searchservice.client.RatingFeedClient;
import com.fooddelivery.searchservice.config.AppProperties;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.dto.response.RatingSyncResult;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import com.fooddelivery.searchservice.service.RatingSyncService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The rating sync against a real Elasticsearch and a stand-in for review-service's feed (see ReviewServiceStub). Before
 * each test the feed is empty and every leftover rating is reset, so a run's counts are exactly this test's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RatingSyncIntegrationTest {

    private static final ReviewServiceStub REVIEW = new ReviewServiceStub();
    private static final String SYNC = "/api/v1/admin/search/ratings/sync";

    @Autowired
    private RatingSyncService sync;
    @Autowired
    private RatingFeedClient feedClient;
    @Autowired
    private AppProperties properties;
    @Autowired
    private ElasticsearchOperations operations;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ElasticsearchClient client;

    @BeforeEach
    void emptyFeedAndNoLeftoverRatings() throws IOException {
        REVIEW.stop();
        REVIEW.start();
        sync.sync();
        REVIEW.reset();
    }

    @AfterAll
    static void stopReviewService() {
        REVIEW.stop();
    }

    @Test
    void appliesTheFeedToIndexedDocumentsIgnoresUnknownIdsAndCreatesNothing() {
        long r1 = restaurant(null, 0);
        long r2 = restaurant(null, 0);
        long unknownRestaurant = randomId();
        long f1 = food(null, 0);
        long unknownFood = randomId();
        REVIEW.rate(RESTAURANTS, new Rated(r1, 4.3, 9), new Rated(unknownRestaurant, 5.0, 2), new Rated(r2, 3.0, 1));
        REVIEW.rate(MENU_ITEMS, new Rated(f1, 4.7, 21), new Rated(unknownFood, 1.0, 1));
        long restaurantsBefore = count(RestaurantDocument.class);
        long foodsBefore = count(FoodDocument.class);

        RatingSyncResult result = sync.sync();

        assertThat(result).isEqualTo(new RatingSyncResult(2, 1, 0, 0));
        assertRestaurant(r1, 4.3, 9);
        assertRestaurant(r2, 3.0, 1);
        assertFood(f1, 4.7, 21);
        // Ids that are not indexed are ignored: no half-empty document was created
        assertThat(operations.exists(String.valueOf(unknownRestaurant), RestaurantDocument.class)).isFalse();
        assertThat(operations.exists(String.valueOf(unknownFood), FoodDocument.class)).isFalse();
        assertThat(count(RestaurantDocument.class)).isEqualTo(restaurantsBefore);
        assertThat(count(FoodDocument.class)).isEqualTo(foodsBefore);
        // The other fields of the documents are untouched
        RestaurantDocument stored = operations.get(String.valueOf(r1), RestaurantDocument.class);
        assertThat(stored.getName()).isEqualTo("Rated " + r1);
        assertThat(stored.getCity()).isEqualTo("Syncville");
        assertThat(stored.getSourceVersion()).isEqualTo(1L);
        // Read in pages: the stub gives two ids at a time, the next page starts after the last id it gave
        List<String> restaurantRequests = REVIEW.requests().stream().filter(r -> r.startsWith(RESTAURANTS)).toList();
        assertThat(restaurantRequests).hasSize(2);
        assertThat(restaurantRequests.getFirst()).isEqualTo(RESTAURANTS + "?afterId=0&size=1000");
        assertThat(restaurantRequests.get(1)).startsWith(RESTAURANTS + "?afterId=").doesNotContain("afterId=0");
    }

    @Test
    void aRunThatChangesNothingRewritesNothingAndAChangedRatingIsWritten() throws IOException {
        long id = restaurant(null, 0);
        REVIEW.rate(RESTAURANTS, new Rated(id, 4.0, 5));
        sync.sync();
        long written = seqNo(RestaurantDocument.INDEX, id);

        RatingSyncResult again = sync.sync();

        assertThat(again.restaurantsUpdated()).isEqualTo(1);
        assertThat(seqNo(RestaurantDocument.INDEX, id)).isEqualTo(written);

        REVIEW.rate(RESTAURANTS, new Rated(id, 4.2, 6));
        sync.sync();
        assertRestaurant(id, 4.2, 6);
        assertThat(seqNo(RestaurantDocument.INDEX, id)).isGreaterThan(written);
    }

    @Test
    void documentsThatLostAllTheirReviewsGoBackToNoRatings() {
        // Rated by an earlier run, maybe before a restart: only the index remembers it
        long kept = restaurant(4.0, 5);
        long gone = restaurant(2.5, 3);
        long unrated = restaurant(null, 0);
        long goneFood = food(3.5, 8);
        long keptFood = food(4.4, 2);
        REVIEW.rate(RESTAURANTS, new Rated(kept, 4.0, 5));
        REVIEW.rate(MENU_ITEMS, new Rated(keptFood, 4.4, 2));

        // A service instance that never saw those documents: nothing is kept in memory
        RatingSyncResult result = new RatingSyncService(feedClient, operations, properties).sync();

        assertThat(result).isEqualTo(new RatingSyncResult(1, 1, 1, 1));
        RestaurantDocument reset = operations.get(String.valueOf(gone), RestaurantDocument.class);
        assertThat(reset).isNotNull();
        assertThat(reset.getRatingAverage()).isNull();
        assertThat(reset.getRatingCount()).isZero();
        assertThat(reset.getName()).isEqualTo("Rated " + gone);
        FoodDocument resetFood = operations.get(String.valueOf(goneFood), FoodDocument.class);
        assertThat(resetFood.getRatingAverage()).isNull();
        assertThat(resetFood.getRatingCount()).isZero();
        assertRestaurant(kept, 4.0, 5);
        assertFood(keptFood, 4.4, 2);
        assertThat(operations.get(String.valueOf(unrated), RestaurantDocument.class).getRatingCount()).isZero();
        // Nothing left to reset: the next run resets nothing
        assertThat(sync.sync()).isEqualTo(new RatingSyncResult(1, 1, 0, 0));
    }

    @Test
    void anEmptyFeedResetsEveryRating() {
        long restaurant = restaurant(4.0, 5);
        long food = food(4.0, 5);

        RatingSyncResult result = sync.sync();

        assertThat(result).isEqualTo(new RatingSyncResult(0, 0, 1, 1));
        assertThat(operations.get(String.valueOf(restaurant), RestaurantDocument.class).getRatingCount()).isZero();
        assertThat(operations.get(String.valueOf(food), FoodDocument.class).getRatingAverage()).isNull();
    }

    @Test
    void whenReviewServiceFailsTheOldValuesStayAndNothingIsZeroed() {
        long restaurant = restaurant(null, 0);
        long food = food(null, 0);
        REVIEW.rate(RESTAURANTS, new Rated(restaurant, 4.1, 6));
        REVIEW.rate(MENU_ITEMS, new Rated(food, 3.2, 4));
        sync.sync();

        // The feed answers 500: the scheduled run only logs, the admin's run says so, the stored ratings stay
        REVIEW.failAll(500);
        assertThatCode(sync::syncOnSchedule).doesNotThrowAnyException();
        assertThatThrownBy(sync::sync).isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("review-service is unavailable");
        assertRestaurant(restaurant, 4.1, 6);
        assertFood(food, 3.2, 4);

        // No body at all is not an answer either
        REVIEW.failAll(204);
        assertThatThrownBy(sync::sync).isInstanceOf(SourceUnavailableException.class);
        assertRestaurant(restaurant, 4.1, 6);

        // The service is down (connection refused)
        REVIEW.stop();
        assertThatCode(sync::syncOnSchedule).doesNotThrowAnyException();
        assertThatThrownBy(sync::sync).isInstanceOf(SourceUnavailableException.class);
        assertRestaurant(restaurant, 4.1, 6);
        assertFood(food, 3.2, 4);
    }

    @Test
    void aFeedThatFailsHalfWayChangesNothingAtAll() throws IOException {
        long restaurant = restaurant(null, 0);
        long gone = food(3.0, 3);
        REVIEW.rate(RESTAURANTS, new Rated(restaurant, 4.9, 3));
        REVIEW.fail(MENU_ITEMS, 500);

        assertThatThrownBy(sync::sync).isInstanceOf(SourceUnavailableException.class);

        // The restaurants answered, the dishes did not: no restaurant rating was written, no dish rating was reset
        assertThat(operations.get(String.valueOf(restaurant), RestaurantDocument.class).getRatingCount()).isZero();
        assertFood(gone, 3.0, 3);

        // Back up: the next run catches up
        REVIEW.recover();
        sync.syncOnSchedule();
        assertRestaurant(restaurant, 4.9, 3);
        assertThat(operations.get(String.valueOf(gone), FoodDocument.class).getRatingCount()).isZero();
    }

    @Test
    void theScheduledRunDoesTheSameAsTheAdminsRun() {
        long id = food(null, 0);
        REVIEW.rate(MENU_ITEMS, new Rated(id, 4.6, 12));

        sync.syncOnSchedule();

        assertFood(id, 4.6, 12);
    }

    @Test
    void aFeedThatDoesNotMoveOnIsAFailureNotAnEndlessLoop() {
        REVIEW.rate(RESTAURANTS, new Rated(1, 4.0, 1), new Rated(2, 4.0, 1), new Rated(3, 4.0, 1));
        REVIEW.stuck(RESTAURANTS);

        assertThatThrownBy(sync::sync).isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("does not advance");
        assertThat(REVIEW.requests()).hasSize(1);
    }

    @Test
    void everyPageIsReadUntilTheLastOne() {
        long[] ids = {randomId(), randomId(), randomId()};
        REVIEW.rate(MENU_ITEMS, new Rated(ids[0], 4.0, 1), new Rated(ids[1], 4.0, 1), new Rated(ids[2], 4.0, 1));
        REVIEW.pageSize(1);

        assertThat(sync.sync()).isEqualTo(new RatingSyncResult(0, 0, 0, 0));

        assertThat(REVIEW.requests().stream().filter(r -> r.startsWith(MENU_ITEMS))).hasSize(3);
    }

    @Test
    void theAdminRunsTheSyncAndGetsTheCounts() throws Exception {
        long restaurant = restaurant(null, 0);
        long goneFood = food(2.0, 2);
        REVIEW.rate(RESTAURANTS, new Rated(restaurant, 4.4, 8), new Rated(randomId(), 3.0, 1));

        mockMvc.perform(admin(post(SYNC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantsUpdated").value(1))
                .andExpect(jsonPath("$.foodsUpdated").value(0))
                .andExpect(jsonPath("$.restaurantsReset").value(0))
                .andExpect(jsonPath("$.foodsReset").value(1));
        assertRestaurant(restaurant, 4.4, 8);
        assertThat(operations.get(String.valueOf(goneFood), FoodDocument.class).getRatingCount()).isZero();
    }

    @Test
    void theAdminGets503WhenReviewServiceIsDown() throws Exception {
        long restaurant = restaurant(4.0, 5);
        REVIEW.stop();

        mockMvc.perform(admin(post(SYNC)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Source unavailable"));
        assertRestaurant(restaurant, 4.0, 5);
    }

    @Test
    void onlyPlatformAdminsMayRunTheSync() throws Exception {
        long restaurant = restaurant(4.0, 5);

        mockMvc.perform(post(SYNC)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(SYNC).with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(SYNC).with(TestTokens.as("7", "RESTAURANT_ADMIN")))
                .andExpect(status().isForbidden());

        // None of them ran it: the empty feed would have reset this rating
        assertRestaurant(restaurant, 4.0, 5);
        assertThat(REVIEW.requests()).isEmpty();
    }

    // ---- helpers

    private long restaurant(Double average, int count) {
        RestaurantDocument document = new RestaurantDocument();
        document.setId(randomId());
        document.setName("Rated " + document.getId());
        document.setCity("Syncville");
        document.setCuisines(List.of("Test"));
        document.setStatus("ACTIVE");
        document.setSourceVersion(1L);
        document.setIndexedAt(Instant.now());
        document.setRatingAverage(average);
        document.setRatingCount(count);
        operations.save(document);
        operations.indexOps(RestaurantDocument.class).refresh();
        return document.getId();
    }

    private long food(Double average, int count) {
        FoodDocument document = new FoodDocument();
        document.setId(randomId());
        document.setName("Dish " + document.getId());
        document.setPrice(100.0);
        document.setAvailable(true);
        document.setStatus("ACTIVE");
        document.setSourceVersion(1L);
        document.setIndexedAt(Instant.now());
        document.setRatingAverage(average);
        document.setRatingCount(count);
        operations.save(document);
        operations.indexOps(FoodDocument.class).refresh();
        return document.getId();
    }

    private void assertRestaurant(long id, double average, int count) {
        RestaurantDocument stored = operations.get(String.valueOf(id), RestaurantDocument.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRatingAverage()).isEqualTo(average);
        assertThat(stored.getRatingCount()).isEqualTo(count);
    }

    private void assertFood(long id, double average, int count) {
        FoodDocument stored = operations.get(String.valueOf(id), FoodDocument.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getRatingAverage()).isEqualTo(average);
        assertThat(stored.getRatingCount()).isEqualTo(count);
    }

    private long count(Class<?> type) {
        operations.indexOps(type).refresh();
        return operations.count(org.springframework.data.elasticsearch.core.query.Query.findAll(), type);
    }

    /** The write counter of a document: changes whenever the document is written, even with the same content. */
    private long seqNo(String index, long id) throws IOException {
        return client.get(g -> g.index(index).id(String.valueOf(id)), Map.class).seqNo();
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(TestTokens.as("1", "PLATFORM_ADMIN"));
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(3_000_000_000L, 9_000_000_000L);
    }
}
