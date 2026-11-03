package com.fooddelivery.searchservice;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.searchservice.document.FoodDocument;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real Elasticsearch and Kafka (Testcontainers). The tests publish the events
 * restaurant-service and catalog-service would publish, then search through the REST API. Each test uses its own
 * city (and fresh ids), so data of one test never shows up in another test's results.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchServiceIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // Near MG Road, Bengaluru
    private static final double LAT = 12.9756;
    private static final double LON = 77.6066;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ElasticsearchOperations operations;

    private String city;

    @BeforeEach
    void ownCity() {
        city = "City" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void restaurantsAreFoundByWordsWithTyposByCuisineAndByDistance() throws Exception {
        long spiceHub = randomId();
        long dosaCorner = randomId();
        long suspended = randomId();
        publish(restaurant(spiceHub, 3, "Spice Hub", "ACTIVE", List.of("Biryani", "North Indian"), LAT, LON));
        // About 2.5 km away
        publish(restaurant(dosaCorner, 1, "Dosa Corner", "ACTIVE", List.of("South Indian"), LAT + 0.02, LON + 0.01));
        publish(restaurant(suspended, 5, "Biryani Palace", "SUSPENDED", List.of("Biryani"), LAT, LON));

        // Only ACTIVE restaurants are found; without words they are listed by name
        awaitAsserted(() -> search("/api/v1/search/restaurants").param("city", city.toLowerCase()), result -> result
                .andExpect(jsonPath("$.totalHits").value(2))
                .andExpect(jsonPath("$.results[*].name").value(contains("Dosa Corner", "Spice Hub")))
                .andExpect(jsonPath("$.facets.cuisines[*].value")
                        .value(containsInAnyOrder("Biryani", "North Indian", "South Indian"))));

        // A typo, a word beginning, a cuisine in another letter case
        mockMvc.perform(search("/api/v1/search/restaurants").param("city", city).param("q", "biryni"))
                .andExpect(jsonPath("$.results[*].id").value(contains(spiceHub)));
        mockMvc.perform(search("/api/v1/search/restaurants").param("city", city).param("q", "dos"))
                .andExpect(jsonPath("$.results[*].id").value(contains(dosaCorner)));
        mockMvc.perform(search("/api/v1/search/restaurants").param("city", city).param("cuisine", "south indian"))
                .andExpect(jsonPath("$.results[*].id").value(contains(dosaCorner)));

        // Near me: nearest first with the distance; with a radius, only what is close enough
        mockMvc.perform(search("/api/v1/search/restaurants").param("city", city)
                        .param("lat", String.valueOf(LAT)).param("lon", String.valueOf(LON)))
                .andExpect(jsonPath("$.results[*].id").value(contains(spiceHub, dosaCorner)))
                .andExpect(jsonPath("$.results[0].distanceKm").value(lessThan(0.1)))
                .andExpect(jsonPath("$.results[1].distanceKm").value(org.hamcrest.Matchers.closeTo(2.5, 0.3)));
        mockMvc.perform(search("/api/v1/search/restaurants").param("city", city)
                        .param("lat", String.valueOf(LAT)).param("lon", String.valueOf(LON)).param("radiusKm", "1"))
                .andExpect(jsonPath("$.results[*].id").value(contains(spiceHub)));
        mockMvc.perform(search("/api/v1/search/restaurants").param("lat", "12.9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dishesShowOnlyWhatCanBeOrderedAndFollowTheirRestaurant() throws Exception {
        long restaurantId = randomId();
        long categoryId = randomId();
        publish(restaurant(restaurantId, 1, "Spice Hub", "ACTIVE", List.of("Biryani"), LAT, LON));
        publish(category(categoryId, 0, "Biryani Specials", "ACTIVE", "UPDATED"));
        long chicken = randomId();
        long veg = randomId();
        publish(menuItem(chicken, 0, restaurantId, categoryId, "Chicken Biryani", "299.00", "NON_VEG", true,
                "ACTIVE", "CREATED"));
        publish(menuItem(veg, 0, restaurantId, categoryId, "Veg Biryani", "219.00", "VEG", true, "ACTIVE", "CREATED"));
        // Sold out today, and hidden by the owner: not orderable, not found
        publish(menuItem(randomId(), 0, restaurantId, categoryId, "Egg Biryani", "239.00", "EGG", false, "ACTIVE", "CREATED"));
        publish(menuItem(randomId(), 0, restaurantId, categoryId, "Mutton Biryani", "399.00", "NON_VEG", true,
                "DISABLED", "CREATED"));

        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city).param("q", "biryani"), result -> result
                .andExpect(jsonPath("$.totalHits").value(2))
                .andExpect(jsonPath("$.results[*].id").value(containsInAnyOrder(chicken, veg)))
                .andExpect(jsonPath("$.results[0].restaurantName").value("Spice Hub"))
                .andExpect(jsonPath("$.results[0].categoryName").value("Biryani Specials"))
                .andExpect(jsonPath("$.facets.foodTypes[*].value").value(containsInAnyOrder("VEG", "NON_VEG"))));

        mockMvc.perform(search("/api/v1/search/foods").param("city", city).param("foodType", "VEG"))
                .andExpect(jsonPath("$.results[*].id").value(contains(veg)));
        mockMvc.perform(search("/api/v1/search/foods").param("city", city).param("sort", "PRICE_HIGH_TO_LOW"))
                .andExpect(jsonPath("$.results[*].id").value(contains(chicken, veg)))
                .andExpect(jsonPath("$.results[0].price").value(299.00));
        mockMvc.perform(search("/api/v1/search/foods").param("city", city).param("maxPrice", "250"))
                .andExpect(jsonPath("$.results[*].id").value(contains(veg)));
        mockMvc.perform(search("/api/v1/search/foods").param("minPrice", "300").param("maxPrice", "100"))
                .andExpect(status().isBadRequest());

        // The restaurant is renamed: its dishes show the new name
        publish(restaurant(restaurantId, 2, "Spice Hub Express", "ACTIVE", List.of("Biryani"), LAT, LON));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.results[*].restaurantName").value(contains("Spice Hub Express", "Spice Hub Express"))));

        // The restaurant is suspended: its dishes disappear from search
        publish(restaurant(restaurantId, 3, "Spice Hub Express", "SUSPENDED", List.of("Biryani"), LAT, LON));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.totalHits").value(0)));
    }

    @Test
    void lateRepeatedAndOutdatedEventsNeverMakeTheIndexGoBack() throws Exception {
        long restaurantId = randomId();
        long itemId = randomId();
        long categoryId = randomId();

        // The dish arrives before its restaurant (two topics): hidden until the restaurant is known
        publish(menuItem(itemId, 1, restaurantId, categoryId, "Paneer Tikka", "249.00", "VEG", true, "ACTIVE", "UPDATED"));
        publish(category(categoryId, 0, "Starters", "ACTIVE", "CREATED"));
        await().atMost(TIMEOUT).until(() -> operations.exists(String.valueOf(itemId), FoodDocument.class));
        mockMvc.perform(search("/api/v1/search/foods").param("city", city)).andExpect(jsonPath("$.totalHits").value(0));
        publish(restaurant(restaurantId, 1, "Tandoor House", "ACTIVE", List.of("North Indian"), LAT, LON));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.results[*].id").value(contains(itemId)))
                .andExpect(jsonPath("$.results[0].restaurantName").value("Tandoor House"))
                .andExpect(jsonPath("$.results[0].categoryName").value("Starters")));

        // A newer price, then an older event that arrives late (or is replayed): the newer price stays
        publish(menuItem(itemId, 2, restaurantId, categoryId, "Paneer Tikka", "269.00", "VEG", true, "ACTIVE", "PRICE_CHANGED"));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.results[0].price").value(269.00)));
        publish(menuItem(itemId, 1, restaurantId, categoryId, "Paneer Tikka", "249.00", "VEG", true, "ACTIVE", "UPDATED"));
        // A newer event after it in the same partition (same key): once its name shows, the old one was handled too
        publish(menuItem(itemId, 3, restaurantId, categoryId, "Paneer Tikka Masala", "269.00", "VEG", true, "ACTIVE",
                "UPDATED"));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.results[0].name").value("Paneer Tikka Masala"))
                .andExpect(jsonPath("$.results[0].price").value(269.00)));

        // Removed for good: gone from search
        publish(menuItem(itemId, 4, restaurantId, categoryId, "Paneer Tikka", "269.00", "VEG", true, "BLOCKED", "REMOVED"));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.totalHits").value(0)));
    }

    @Test
    void categoriesAndSearchAsYouType() throws Exception {
        String word = "Momo" + UUID.randomUUID().toString().substring(0, 4).replaceAll("[0-9]", "x");
        long active = randomId();
        long inactive = randomId();
        long restaurantId = randomId();
        publish(category(active, 0, word + " Corner", "ACTIVE", "CREATED"));
        publish(category(inactive, 0, word + " Archive", "INACTIVE", "STATUS_CHANGED"));
        publish(restaurant(restaurantId, 1, "Himalayan Kitchen", "ACTIVE", List.of("Tibetan"), LAT, LON));
        long dish = randomId();
        publish(menuItem(dish, 0, restaurantId, active, "Chicken Steamed " + word, "149.00", "NON_VEG", true,
                "ACTIVE", "CREATED"));

        awaitAsserted(() -> search("/api/v1/search/categories").param("q", word), result -> result
                .andExpect(jsonPath("$[*].id").value(hasItem(active)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(inactive)))));

        // Typing: word beginnings of every word ("chi ste" -> "Chicken Steamed ..."), in this city only
        awaitAsserted(() -> search("/api/v1/search/suggest").param("q", "chi ste").param("city", city), result -> result
                .andExpect(jsonPath("$.foods[*].id").value(contains(dish)))
                .andExpect(jsonPath("$.foods[0].detail").value("Himalayan Kitchen")));
        awaitAsserted(() -> search("/api/v1/search/suggest").param("q", "himal").param("city", city), result -> result
                .andExpect(jsonPath("$.restaurants[*].id").value(contains(restaurantId))));
        mockMvc.perform(search("/api/v1/search/suggest").param("q", " ")).andExpect(status().isBadRequest());

        // The category is renamed: its dishes show the new name
        publish(category(active, 1, word + " House", "ACTIVE", "UPDATED"));
        awaitAsserted(() -> search("/api/v1/search/foods").param("city", city), result -> result
                .andExpect(jsonPath("$.results[0].categoryName").value(word + " House")));
    }

    @Test
    void onlyPlatformAdminsSeeTheIndexOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/search/indexes").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clusterStatus").value(org.hamcrest.Matchers.oneOf("green", "yellow")))
                .andExpect(jsonPath("$.indexes[*].index").value(contains("restaurant", "food", "category")));
        mockMvc.perform(get("/api/v1/admin/search/indexes").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ---- what restaurant-service and catalog-service publish ----

    private RestaurantChanged restaurant(long id, long version, String name, String status, List<String> cuisines,
                                         double lat, double lon) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("STATUS_CHANGED").setRestaurantId(id).setVersion(version).setOwnerUserId(3L)
                .setName(name).setCity(city).setStatus(status).setDescription(name + " serves fresh food")
                .setCuisines(cuisines)
                .setAddress(RestaurantAddress.newBuilder().setLine1("45 Brigade Road").setCity(city)
                        .setState("Karnataka").setPostalCode("560025").setCountry("India")
                        .setLatitude(lat).setLongitude(lon).build())
                .build();
    }

    private static CategoryChanged category(long id, long version, String name, String status, String changeType) {
        return CategoryChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType(changeType).setCategoryId(id).setVersion(version).setName(name)
                .setDisplayOrder(1).setStatus(status).build();
    }

    private static MenuItemChanged menuItem(long id, long version, long restaurantId, long categoryId, String name,
                                            String price, String foodType, boolean available, String status,
                                            String changeType) {
        return MenuItemChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType(changeType).setMenuItemId(id).setVersion(version)
                .setRestaurantId(restaurantId).setCategoryId(categoryId).setName(name)
                .setDescription("Slow cooked, served hot").setPrice(new BigDecimal(price)).setFoodType(foodType)
                .setAvailable(available).setStatus(status).build();
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

    private static MockHttpServletRequestBuilder search(String path) {
        return get(path);
    }

    /** Indexing is asynchronous (Kafka, then Elasticsearch's refresh): repeat the request until it matches. */
    private void awaitAsserted(java.util.function.Supplier<MockHttpServletRequestBuilder> request,
                               ResultCheck check) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> check.verify(mockMvc.perform(request.get()).andExpect(status().isOk())));
    }

    @FunctionalInterface
    private interface ResultCheck {
        void verify(ResultActions result) throws Exception;
    }

    private static long randomId() {
        // Above the int range, so JSON numbers come back as Long, like the expected values
        return ThreadLocalRandom.current().nextLong(3_000_000_000L, 9_000_000_000L);
    }
}
