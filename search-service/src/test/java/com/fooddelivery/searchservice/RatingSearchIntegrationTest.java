package com.fooddelivery.searchservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ratings in search results, against a real Elasticsearch: the hits show them, "minRating" filters by them, "sort=rating"
 * orders by them, and rebuilding a document from an event keeps them. The ratings are written into the documents
 * directly here (the rating sync that normally does it has its own test). Each test uses its own city.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RatingSearchIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String RESTAURANTS = "/api/v1/search/restaurants";
    private static final String FOODS = "/api/v1/search/foods";
    private static final String CITY = "city";
    private static final String SORT = "sort";
    private static final String MIN_RATING = "minRating";
    private static final String RESULT_IDS = "$.results[*].id";
    private static final String TOTAL_HITS = "$.totalHits";
    private static final String FIRST_ID = "$.results[0].id";
    private static final String FIRST_AVERAGE = "$.results[0].ratingAverage";
    private static final String FIRST_COUNT = "$.results[0].ratingCount";
    private static final String UNRATED = "unrated";
    private static final String OLD = "old";
    private static final double LAT = 12.9756;
    private static final double LON = 77.6066;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ElasticsearchOperations operations;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private String city;

    @BeforeEach
    void ownCity() {
        city = "City" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- restaurants

    /**
     * Names are in the alphabetical order of the default listing: the two unrated ones come first there, so "unrated last"
     * and "ties by the normal order" cannot pass by luck.
     */
    private Map<String, Long> restaurantsWithRatings() {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put(UNRATED, restaurant("Aaa Never Rated", null, 0, LAT, LON));
        ids.put(OLD, oldRestaurantWithoutRatingFields("Aab Indexed Before Ratings"));
        ids.put("a", restaurant("Bravo", 4.5, 10, LAT, LON));
        ids.put("b", restaurant("Charlie", 4.5, 30, LAT, LON));
        ids.put("c", restaurant("Delta", 4.8, 2, LAT, LON));
        ids.put("d", restaurant("Echo", 3.0, 100, LAT + 0.01, LON));
        ids.put("g", restaurant("Foxtrot", 4.5, 10, LAT, LON));
        operations.indexOps(RestaurantDocument.class).refresh();
        return ids;
    }

    @Test
    void restaurantHitsShowTheirRatingAndUnratedOnesShowNone() throws Exception {
        Map<String, Long> ids = restaurantsWithRatings();

        ResultActions result = mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "4.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_ID).value(ids.get("c")))
                .andExpect(jsonPath(FIRST_AVERAGE).value(4.8))
                .andExpect(jsonPath(FIRST_COUNT).value(2));
        log.info("Restaurant hit with a rating: {}", result.andReturn().getResponse().getContentAsString());

        // Without a rating: no average, count 0 (also for a document written before the fields existed)
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("q", "Never"))
                .andExpect(jsonPath(FIRST_ID).value(ids.get(UNRATED)))
                .andExpect(jsonPath(FIRST_AVERAGE).value(nullValue()))
                .andExpect(jsonPath(FIRST_COUNT).value(0));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("q", "Indexed"))
                .andExpect(jsonPath(FIRST_ID).value(ids.get(OLD)))
                .andExpect(jsonPath(FIRST_AVERAGE).value(nullValue()))
                .andExpect(jsonPath(FIRST_COUNT).value(0));
    }

    @Test
    void withoutTheNewParametersRestaurantsAreListedByNameAsBefore() throws Exception {
        Map<String, Long> ids = restaurantsWithRatings();
        Object[] byName = {ids.get(UNRATED), ids.get(OLD), ids.get("a"), ids.get("b"), ids.get("c"), ids.get("d"),
                ids.get("g")};

        mockMvc.perform(get(RESTAURANTS).param(CITY, city))
                .andExpect(jsonPath(TOTAL_HITS).value(7))
                .andExpect(jsonPath(RESULT_IDS).value(contains(byName)));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "RELEVANCE"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(byName)));
    }

    @Test
    void minRatingKeepsOnlyRestaurantsRatedAtLeastThatHigh() throws Exception {
        Map<String, Long> ids = restaurantsWithRatings();

        // By name, as without the filter; unrated ones are gone
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "4.5"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("a"), ids.get("b"), ids.get("c"), ids.get("g"))));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "4"))
                .andExpect(jsonPath(TOTAL_HITS).value(4));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "4.6"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("c"))));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "1"))
                .andExpect(jsonPath(TOTAL_HITS).value(5));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "5"))
                .andExpect(jsonPath(TOTAL_HITS).value(0));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(MIN_RATING, "0.9")).andExpect(status().isBadRequest());
    }

    @Test
    void sortByRatingIsBestFirstThenMostRatingsThenTheNormalOrderAndUnratedLast() throws Exception {
        Map<String, Long> ids = restaurantsWithRatings();
        Object[] expected = {ids.get("c"), ids.get("b"), ids.get("a"), ids.get("g"), ids.get("d"), ids.get(UNRATED),
                ids.get(OLD)};

        // 4.8; 4.5 x 30; 4.5 x 10 (Bravo before Foxtrot, by name); 3.0; then the unrated, by name
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "RATING"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(expected)));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "rating"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(expected)));

        // The same order page by page
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "rating").param("size", "3").param("page", "1"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("g"), ids.get("d"), ids.get(UNRATED))));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "rating").param("size", "3").param("page", "2"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get(OLD))));

        // With a filter, and with nothing found
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "rating").param(MIN_RATING, "4.5"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("c"), ids.get("b"), ids.get("a"), ids.get("g"))));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param(SORT, "rating").param("cuisine", "nothing"))
                .andExpect(jsonPath(TOTAL_HITS).value(0));
    }

    @Test
    void sortByRatingStillReportsTheDistanceAndUsesItAsTheTieBreak() throws Exception {
        long near = restaurant("Zulu Near", 4.0, 5, LAT, LON);
        long far = restaurant("Alpha Far", 4.0, 5, LAT + 0.05, LON);
        long best = restaurant("Mike Best", 4.9, 1, LAT + 0.1, LON);
        long unrated = restaurant("Yankee", null, 0, LAT + 0.001, LON);
        operations.indexOps(RestaurantDocument.class).refresh();

        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("lat", String.valueOf(LAT))
                        .param("lon", String.valueOf(LON)).param(SORT, "RATING"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(best, near, far, unrated)))
                .andExpect(jsonPath("$.results[0].distanceKm").value(closeTo(11.1, 0.5)))
                .andExpect(jsonPath("$.results[1].distanceKm").value(lessThan(0.1)))
                .andExpect(jsonPath("$.results[2].distanceKm").value(closeTo(5.6, 0.5)));
        // Without sort=rating the nearest comes first, as before
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("lat", String.valueOf(LAT))
                        .param("lon", String.valueOf(LON)))
                .andExpect(jsonPath(RESULT_IDS).value(contains(near, unrated, far, best)))
                .andExpect(jsonPath("$.results[3].distanceKm").value(closeTo(11.1, 0.5)));
    }

    @Test
    void sortByRatingWithWordsKeepsTheMatchingAndPutsUnratedLast() throws Exception {
        String word = "Kebab" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[0-9]", "x");
        long low = restaurant(word + " Low", 3.5, 20, LAT, LON);
        long unrated = restaurant(word + " Fresh", null, 0, LAT, LON);
        long high = restaurant(word + " High", 4.7, 3, LAT, LON);
        restaurant("Something Else", 5.0, 50, LAT, LON);
        operations.indexOps(RestaurantDocument.class).refresh();

        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("q", word).param(SORT, "RATING"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(high, low, unrated)));
        mockMvc.perform(get(RESTAURANTS).param(CITY, city).param("q", word).param(MIN_RATING, "4"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(high)));
    }

    // ---- dishes

    private Map<String, Long> foodsWithRatings(long restaurantId) {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put(UNRATED, food(restaurantId, "Aaa Never Rated", "100.00", "VEG", null, 0));
        ids.put(OLD, oldFoodWithoutRatingFields(restaurantId, "Aab Indexed Before Ratings"));
        ids.put("a", food(restaurantId, "Bravo", "300.00", "VEG", 4.5, 10));
        ids.put("b", food(restaurantId, "Charlie", "250.00", "NON_VEG", 4.5, 30));
        ids.put("c", food(restaurantId, "Delta", "200.00", "VEG", 4.8, 2));
        ids.put("d", food(restaurantId, "Echo", "150.00", "NON_VEG", 3.0, 100));
        ids.put("g", food(restaurantId, "Foxtrot", "350.00", "VEG", 4.5, 10));
        operations.indexOps(FoodDocument.class).refresh();
        return ids;
    }

    @Test
    void dishHitsShowTheirRatingAndDishesFilterByIt() throws Exception {
        Map<String, Long> ids = foodsWithRatings(randomId());

        ResultActions result = mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "4.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FIRST_ID).value(ids.get("c")))
                .andExpect(jsonPath(FIRST_AVERAGE).value(4.8))
                .andExpect(jsonPath(FIRST_COUNT).value(2));
        log.info("Dish hit with a rating: {}", result.andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(FOODS).param(CITY, city).param("q", "Never"))
                .andExpect(jsonPath(FIRST_ID).value(ids.get(UNRATED)))
                .andExpect(jsonPath(FIRST_AVERAGE).value(nullValue()))
                .andExpect(jsonPath(FIRST_COUNT).value(0));
        mockMvc.perform(get(FOODS).param(CITY, city).param("q", "Indexed"))
                .andExpect(jsonPath(FIRST_ID).value(ids.get(OLD)))
                .andExpect(jsonPath(FIRST_AVERAGE).value(nullValue()))
                .andExpect(jsonPath(FIRST_COUNT).value(0));

        // minRating: by name as without it, unrated ones gone
        mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "4.5"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("a"), ids.get("b"), ids.get("c"), ids.get("g"))));
        mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "4.6"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("c"))));
        mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "1"))
                .andExpect(jsonPath(TOTAL_HITS).value(5));
        mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "6")).andExpect(status().isBadRequest());

        // Together with the other filters
        mockMvc.perform(get(FOODS).param(CITY, city).param(MIN_RATING, "4.5").param("foodType", "VEG")
                        .param("maxPrice", "320"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("a"), ids.get("c"))));
    }

    @Test
    void withoutTheNewParametersDishesAreOrderedAsBefore() throws Exception {
        Map<String, Long> ids = foodsWithRatings(randomId());

        mockMvc.perform(get(FOODS).param(CITY, city))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get(UNRATED), ids.get(OLD), ids.get("a"),
                        ids.get("b"), ids.get("c"), ids.get("d"), ids.get("g"))));
        mockMvc.perform(get(FOODS).param(CITY, city).param(SORT, "PRICE_LOW_TO_HIGH"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get(UNRATED), ids.get(OLD), ids.get("d"),
                        ids.get("c"), ids.get("b"), ids.get("a"), ids.get("g"))));
        mockMvc.perform(get(FOODS).param(CITY, city).param(SORT, "PRICE_HIGH_TO_LOW"))
                .andExpect(jsonPath(FIRST_ID).value(ids.get("g")));
    }

    @Test
    void dishesSortByRatingBestFirstThenMostRatingsThenTheNormalOrderAndUnratedLast() throws Exception {
        long restaurantId = randomId();
        Map<String, Long> ids = foodsWithRatings(restaurantId);
        Object[] expected = {ids.get("c"), ids.get("b"), ids.get("a"), ids.get("g"), ids.get("d"), ids.get(UNRATED),
                ids.get(OLD)};

        mockMvc.perform(get(FOODS).param(CITY, city).param(SORT, "RATING"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(expected)));
        mockMvc.perform(get(FOODS).param(CITY, city).param(SORT, "rating"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(expected)));
        mockMvc.perform(get(FOODS).param(CITY, city).param(SORT, "rating").param("size", "4").param("page", "1"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("d"), ids.get(UNRATED), ids.get(OLD))));
        mockMvc.perform(get(FOODS).param("restaurantId", String.valueOf(restaurantId)).param(SORT, "rating")
                        .param(MIN_RATING, "4.5").param("foodType", "VEG"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(ids.get("c"), ids.get("a"), ids.get("g"))));
    }

    @Test
    void dishesWithWordsSortByRatingAndStillOnlyShowWhatCanBeOrdered() throws Exception {
        long restaurantId = randomId();
        String word = "Tikka" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[0-9]", "x");
        long low = food(restaurantId, word + " Low", "100.00", "VEG", 3.0, 8);
        long unrated = food(restaurantId, word + " Fresh", "100.00", "VEG", null, 0);
        long high = food(restaurantId, word + " High", "100.00", "VEG", 4.9, 4);
        FoodDocument soldOut = foodDocument(restaurantId, word + " Soldout", "100.00", "VEG", 5.0, 99);
        soldOut.setAvailable(false);
        operations.save(soldOut);
        operations.indexOps(FoodDocument.class).refresh();

        mockMvc.perform(get(FOODS).param(CITY, city).param("q", word).param(SORT, "RATING"))
                .andExpect(jsonPath(RESULT_IDS).value(contains(high, low, unrated)));
    }

    // ---- a document rebuilt from an event keeps its rating

    @Test
    void eventsThatRebuildARestaurantOrADishKeepTheirRating() throws Exception {
        long restaurantId = randomId();
        long itemId = randomId();
        long categoryId = randomId();
        publish(restaurantEvent(restaurantId, 1, "Spice Hub"));
        publish(category(categoryId, 1, "Starters"));
        publish(menuItem(itemId, 1, restaurantId, categoryId, "Paneer Tikka", "249.00"));
        awaitRestaurantName(restaurantId, "Spice Hub");
        awaitFoodPrice(itemId, 249.00);
        // The sync wrote ratings into both
        setRating(RestaurantDocument.INDEX, restaurantId, 4.2, 7);
        setRating(FoodDocument.INDEX, itemId, 3.9, 12);

        // The restaurant is renamed (its dish's copy is refreshed by update-by-query), the category is renamed, then
        // the dish itself changes: none of it may wipe a rating
        publish(restaurantEvent(restaurantId, 2, "Spice Hub Express"));
        awaitRestaurantName(restaurantId, "Spice Hub Express");
        publish(category(categoryId, 2, "Appetizers"));
        publish(menuItem(itemId, 2, restaurantId, categoryId, "Paneer Tikka Masala", "269.00"));
        awaitFoodPrice(itemId, 269.00);

        RestaurantDocument restaurant = operations.get(String.valueOf(restaurantId), RestaurantDocument.class);
        assertThat(restaurant.getName()).isEqualTo("Spice Hub Express");
        assertThat(restaurant.getRatingAverage()).isEqualTo(4.2);
        assertThat(restaurant.getRatingCount()).isEqualTo(7);
        await().atMost(TIMEOUT).untilAsserted(() -> {
            FoodDocument food = operations.get(String.valueOf(itemId), FoodDocument.class);
            assertThat(food.getName()).isEqualTo("Paneer Tikka Masala");
            assertThat(food.getRestaurantName()).isEqualTo("Spice Hub Express");
            assertThat(food.getCategoryName()).isEqualTo("Appetizers");
            assertThat(food.getRatingAverage()).isEqualTo(3.9);
            assertThat(food.getRatingCount()).isEqualTo(12);
        });
        // And search shows it
        operations.indexOps(FoodDocument.class).refresh();
        mockMvc.perform(get(FOODS).param("restaurantId", String.valueOf(restaurantId)).param(MIN_RATING, "3.9"))
                .andExpect(jsonPath(FIRST_ID).value(itemId))
                .andExpect(jsonPath(FIRST_AVERAGE).value(3.9))
                .andExpect(jsonPath(FIRST_COUNT).value(12));
    }

    @Test
    void aNewDocumentFromAnEventStartsWithoutRatings() throws Exception {
        long restaurantId = randomId();
        publish(restaurantEvent(restaurantId, 1, "Brand New"));
        awaitRestaurantName(restaurantId, "Brand New");

        RestaurantDocument restaurant = operations.get(String.valueOf(restaurantId), RestaurantDocument.class);
        assertThat(restaurant.getRatingAverage()).isNull();
        assertThat(restaurant.getRatingCount()).isZero();
    }

    // ---- helpers

    private long restaurant(String name, Double average, int count, double lat, double lon) {
        long id = randomId();
        RestaurantDocument document = new RestaurantDocument();
        document.setId(id);
        document.setName(name);
        document.setDescription(name + " serves fresh food");
        document.setCuisines(List.of("Test"));
        document.setCity(city);
        document.setLocation(new GeoPoint(lat, lon));
        document.setStatus("ACTIVE");
        document.setSourceVersion(1L);
        document.setIndexedAt(Instant.now());
        document.setRatingAverage(average);
        document.setRatingCount(count);
        operations.save(document);
        return id;
    }

    /** As indexed before ratings existed: the source has no rating fields at all. */
    private long oldRestaurantWithoutRatingFields(String name) {
        long id = randomId();
        String source = """
                {"id":%d,"name":"%s","description":"old","cuisines":["Test"],"city":"%s","status":"ACTIVE",
                 "location":{"lat":%s,"lon":%s},"sourceVersion":1,"indexedAt":"2026-01-01T00:00:00.000Z"}
                """.formatted(id, name, city, LAT, LON);
        IndexQuery query = new IndexQueryBuilder().withId(String.valueOf(id)).withSource(source).build();
        operations.index(query, IndexCoordinates.of(RestaurantDocument.INDEX));
        return id;
    }

    private FoodDocument foodDocument(long restaurantId, String name, String price, String foodType, Double average,
                                      int count) {
        FoodDocument document = new FoodDocument();
        document.setId(randomId());
        document.setName(name);
        document.setDescription("Slow cooked, served hot");
        document.setPrice(new BigDecimal(price).doubleValue());
        document.setFoodType(foodType);
        document.setAvailable(true);
        document.setStatus("ACTIVE");
        document.setCategoryId(1L);
        document.setCategoryName("Mains");
        document.setRestaurantId(restaurantId);
        document.setRestaurantName("Rated Kitchen");
        document.setRestaurantCity(city);
        document.setRestaurantStatus("ACTIVE");
        document.setSourceVersion(1L);
        document.setIndexedAt(Instant.now());
        document.setRatingAverage(average);
        document.setRatingCount(count);
        return document;
    }

    private long food(long restaurantId, String name, String price, String foodType, Double average, int count) {
        FoodDocument document = foodDocument(restaurantId, name, price, foodType, average, count);
        operations.save(document);
        return document.getId();
    }

    private long oldFoodWithoutRatingFields(long restaurantId, String name) {
        long id = randomId();
        String source = """
                {"id":%d,"name":"%s","description":"old","price":120.0,"foodType":"VEG","available":true,"status":"ACTIVE",
                 "categoryId":1,"categoryName":"Mains","restaurantId":%d,"restaurantName":"Rated Kitchen",
                 "restaurantCity":"%s","restaurantStatus":"ACTIVE","sourceVersion":1,"indexedAt":"2026-01-01T00:00:00.000Z"}
                """.formatted(id, name, restaurantId, city);
        IndexQuery query = new IndexQueryBuilder().withId(String.valueOf(id)).withSource(source).build();
        operations.index(query, IndexCoordinates.of(FoodDocument.INDEX));
        return id;
    }

    /** What the rating sync does to a document: only these two fields change. */
    private void setRating(String index, long id, double average, int count) {
        UpdateQuery update = UpdateQuery.builder(String.valueOf(id))
                .withDocument(Document.from(Map.of("ratingAverage", average, "ratingCount", count))).build();
        operations.update(update, IndexCoordinates.of(index));
        operations.indexOps(IndexCoordinates.of(index)).refresh();
    }

    private void awaitRestaurantName(long id, String name) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            RestaurantDocument stored = operations.get(String.valueOf(id), RestaurantDocument.class);
            assertThat(stored).isNotNull();
            assertThat(stored.getName()).isEqualTo(name);
        });
    }

    private void awaitFoodPrice(long id, double price) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            FoodDocument stored = operations.get(String.valueOf(id), FoodDocument.class);
            assertThat(stored).isNotNull();
            assertThat(stored.getPrice()).isEqualTo(price);
        });
    }

    private RestaurantChanged restaurantEvent(long id, long version, String name) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("UPDATED").setRestaurantId(id).setVersion(version).setOwnerUserId(3L)
                .setName(name).setCity(city).setStatus("ACTIVE").setDescription(name + " serves fresh food")
                .setCuisines(List.of("Test"))
                .setAddress(RestaurantAddress.newBuilder().setLine1("45 Brigade Road").setCity(city)
                        .setState("Karnataka").setPostalCode("560025").setCountry("India")
                        .setLatitude(LAT).setLongitude(LON).build())
                .build();
    }

    private static CategoryChanged category(long id, long version, String name) {
        return CategoryChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("UPDATED").setCategoryId(id).setVersion(version).setName(name)
                .setDisplayOrder(1).setStatus("ACTIVE").build();
    }

    private static MenuItemChanged menuItem(long id, long version, long restaurantId, long categoryId, String name,
                                            String price) {
        return MenuItemChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("UPDATED").setMenuItemId(id).setVersion(version)
                .setRestaurantId(restaurantId).setCategoryId(categoryId).setName(name)
                .setDescription("Slow cooked, served hot").setPrice(new BigDecimal(price)).setFoodType("VEG")
                .setAvailable(true).setStatus("ACTIVE").build();
    }

    private void publish(SpecificRecord event) throws Exception {
        String topic;
        String key;
        if (event instanceof RestaurantChanged restaurant) {
            topic = Topics.RESTAURANT_CHANGED;
            key = String.valueOf(restaurant.getRestaurantId());
        } else if (event instanceof CategoryChanged category) {
            topic = Topics.CATEGORY_CHANGED;
            key = String.valueOf(category.getCategoryId());
        } else {
            topic = Topics.MENU_ITEM_CHANGED;
            key = String.valueOf(((MenuItemChanged) event).getMenuItemId());
        }
        kafkaTemplate.send(topic, key, event).get();
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(3_000_000_000L, 9_000_000_000L);
    }
}
