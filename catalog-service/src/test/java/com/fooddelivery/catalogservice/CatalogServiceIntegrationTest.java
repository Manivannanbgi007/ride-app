package com.fooddelivery.catalogservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fooddelivery.catalogservice.client.RestaurantServiceClient;
import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL and Kafka (Testcontainers), including the seeded categories.
 * Only restaurant-service is replaced by a mock; its events are published to Kafka by the tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogServiceIntegrationTest {

    private static final long ADMIN_ID = 1L;
    private static final long BIRYANI = 3L;   // seeded by Flyway V4
    private static final long DESSERTS = 7L;  // seeded by Flyway V4
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantSnapshotRepository restaurantSnapshotRepository;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProducerFactory<Object, Object> producerFactory;

    @Autowired
    private ConsumerFactory<Object, Object> consumerFactory;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @MockitoBean
    private RestaurantServiceClient restaurantServiceClient;

    @Test
    void seededCategoriesAreAvailableToCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Pizza", "Biryani", "South Indian", "Desserts", "Beverages")));
    }

    @Test
    void ownerBuildsAMenuThatCustomersSeeGroupedByCategory() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");

        long kulfiId = createItem(ownerId, restaurantId, "Malai Kulfi", "89.00", DESSERTS, "VEG");
        long biryaniId = createItem(ownerId, restaurantId, "Chicken Dum Biryani", "299.00", BIRYANI, "NON_VEG");
        long vegBiryaniId = createItem(ownerId, restaurantId, "Veg Biryani", "219.00", BIRYANI, "VEG");

        // Sold out stays on the menu (greyed out); disabled disappears
        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/availability", restaurantId, kulfiId), """
                {"available": false}
                """).andExpect(status().isOk());
        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/disable", restaurantId, vegBiryaniId), null)
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Test Kitchen"))
                .andExpect(jsonPath("$.sections.length()").value(2))
                .andExpect(jsonPath("$.sections[0].categoryName").value("Biryani"))
                .andExpect(jsonPath("$.sections[0].items.length()").value(1))
                .andExpect(jsonPath("$.sections[0].items[0].id").value(biryaniId))
                .andExpect(jsonPath("$.sections[1].categoryName").value("Desserts"))
                .andExpect(jsonPath("$.sections[1].items[0].available").value(false))
                .andExpect(jsonPath("$.sections[1].items[0].status").doesNotExist());

        // The owner sees every item, including the disabled one
        asOwner(ownerId, get("/api/v1/restaurants/mine/{r}/menu-items", restaurantId), null)
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void menusAreCachedInRedisAndEveryChangeShowsAtOnce() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        String categoryName = "Cache " + UUID.randomUUID().toString().substring(0, 8);
        String categoryBody = asAdmin(post("/api/v1/admin/categories"), """
                {"name": "%s"}
                """.formatted(categoryName)).andReturn().getResponse().getContentAsString();
        long categoryId = ((Number) JsonPath.read(categoryBody, "$.id")).longValue();
        long itemId = createItem(ownerId, restaurantId, "Masala Dosa", "120.00", categoryId, "VEG");

        // First read builds the menu from MySQL and caches it
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(jsonPath("$.sections[0].items[0].price").value(120.00));
        assertThat(redis.keys("catalog:v1:menu:" + restaurantId + ":*")).hasSize(1);

        // Changed in MySQL behind the service's back: the cached menu is still served (proves the cache is used)
        MenuItem item = menuItemRepository.findById(itemId).orElseThrow();
        item.setName("Changed Behind The Cache");
        menuItemRepository.save(item);
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(jsonPath("$.sections[0].items[0].name").value("Masala Dosa"));

        // A change through the service is visible on the very next read
        long version = menuItemRepository.findById(itemId).orElseThrow().getVersion();
        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/price", restaurantId, itemId), """
                {"price": 135.00, "version": %d}
                """.formatted(version)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(jsonPath("$.sections[0].items[0].price").value(135.00))
                .andExpect(jsonPath("$.sections[0].items[0].name").value("Changed Behind The Cache"));

        // Categories are part of every menu: renaming one refreshes the menus and the category list
        mockMvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].name", hasItems(categoryName)));
        asAdmin(put("/api/v1/admin/categories/{c}", categoryId), """
                {"name": "%s Renamed"}
                """.formatted(categoryName)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(jsonPath("$.sections[0].categoryName").value(categoryName + " Renamed"));
        mockMvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].name", hasItems(categoryName + " Renamed")));
    }

    @Test
    void everyMenuChangeIsPublishedForSearchWithItsVersion() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long itemId = createItem(ownerId, restaurantId, "Hyderabadi Biryani", "299.00", BIRYANI, "NON_VEG");

        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/price", restaurantId, itemId), """
                {"price": 319.00, "version": 0}
                """).andExpect(status().isOk());
        asAdmin(put("/api/v1/admin/menu-items/{i}/deactivate", itemId), """
                {"reason": "Photo shows another dish"}
                """).andExpect(status().isOk());
        asAdmin(delete("/api/v1/admin/menu-items/{i}", itemId).param("reason", "Duplicate listing"), null)
                .andExpect(status().isNoContent());

        // Outbox -> Kafka, in order, each with the version after the change (the removal carries the last state)
        List<MenuItemChanged> events = KafkaTestEvents.awaitEvents(consumerFactory, Topics.MENU_ITEM_CHANGED,
                String.valueOf(itemId), MenuItemChanged.class, 4);
        assertThat(events).extracting(MenuItemChanged::getChangeType)
                .containsExactly("CREATED", "PRICE_CHANGED", "STATUS_CHANGED", "REMOVED");
        assertThat(events).extracting(MenuItemChanged::getVersion).containsExactly(0L, 1L, 2L, 2L);
        assertThat(events.get(1).getPrice()).isEqualByComparingTo("319.00");
        assertThat(events.get(2).getStatus()).isEqualTo("BLOCKED");
        assertThat(events.getFirst().getRestaurantId()).isEqualTo(restaurantId);
        assertThat(events.getFirst().getCategoryId()).isEqualTo(BIRYANI);
        assertThat(events.getFirst().getFoodType()).isEqualTo("NON_VEG");
    }

    @Test
    void anAdminReplaysCategoriesAndMenuItemsUnchanged() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long itemId = createItem(ownerId, restaurantId, "Filter Coffee", "49.00", BIRYANI, "VEG");
        String body = asAdmin(post("/api/v1/admin/categories"), """
                {"name": "Replay %s", "description": "Created by a test"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = ((Number) JsonPath.read(body, "$.id")).longValue();

        asAdmin(post("/api/v1/admin/menu-items/events/republish"), null)
                .andExpect(jsonPath("$.republished").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        asAdmin(post("/api/v1/admin/categories/events/republish"), null)
                .andExpect(jsonPath("$.republished").value(org.hamcrest.Matchers.greaterThanOrEqualTo(9)));

        List<MenuItemChanged> itemEvents = KafkaTestEvents.awaitEvents(consumerFactory, Topics.MENU_ITEM_CHANGED,
                String.valueOf(itemId), MenuItemChanged.class, 2);
        assertThat(itemEvents).extracting(MenuItemChanged::getChangeType).containsExactly("CREATED", "REPUBLISHED");
        assertThat(itemEvents.get(1).getVersion()).isEqualTo(itemEvents.getFirst().getVersion());
        List<CategoryChanged> categoryEvents = KafkaTestEvents.awaitEvents(consumerFactory, Topics.CATEGORY_CHANGED,
                String.valueOf(categoryId), CategoryChanged.class, 2);
        assertThat(categoryEvents).extracting(CategoryChanged::getChangeType).containsExactly("CREATED", "REPUBLISHED");
        assertThat(categoryEvents.getFirst().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void adminBlockOverridesTheOwnerAndIsAudited() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long itemId = createItem(ownerId, restaurantId, "Mystery Shake", "149.00", BIRYANI, "VEG");

        asAdmin(put("/api/v1/admin/menu-items/{i}/deactivate", itemId), """
                {"reason": "Listed in the wrong category with a misleading description"}
                """).andExpect(jsonPath("$.status").value("BLOCKED"));

        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/enable", restaurantId, itemId), null)
                .andExpect(status().isUnprocessableContent());

        asAdmin(put("/api/v1/admin/menu-items/{i}/category", itemId), """
                {"categoryId": 8, "reason": "It is a beverage"}
                """).andExpect(jsonPath("$.categoryName").value("Beverages"));

        asAdmin(get("/api/v1/admin/menu-items")
                .param("restaurantId", String.valueOf(restaurantId)).param("status", "BLOCKED"), null)
                .andExpect(jsonPath("$.totalElements").value(1));

        asAdmin(delete("/api/v1/admin/menu-items/{i}", itemId).param("reason", "Duplicate of another item"), null)
                .andExpect(status().isNoContent());

        asAdmin(get("/api/v1/admin/menu-items/{i}/audit-logs", itemId), null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("MENU_ITEM_REMOVED_BY_ADMIN"))
                .andExpect(jsonPath("$.content[2].action").value("MENU_ITEM_BLOCKED_BY_ADMIN"))
                .andExpect(jsonPath("$.content[2].adminUserId").value(ADMIN_ID));
    }

    @Test
    void categoryLifecycleCreateReorderProtectAndDelete() throws Exception {
        // Create: goes to the end
        String created = asAdmin(post("/api/v1/admin/categories"), """
                {"name": "Rolls %d", "description": "Kathi rolls and wraps"}
                """.formatted(randomId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rollsId = ((Number) JsonPath.read(created, "$.id")).longValue();

        // Reorder: put the new category first, keep the rest as they were
        String all = asAdmin(get("/api/v1/admin/categories").param("size", "100"), null)
                .andReturn().getResponse().getContentAsString();
        List<Long> order = new ArrayList<>(List.of(rollsId));
        for (Number id : JsonPath.<List<Number>>read(all, "$.content[*].id")) {
            if (id.longValue() != rollsId) {
                order.add(id.longValue());
            }
        }
        asAdmin(put("/api/v1/admin/categories/order"), "{\"categoryIds\": " + order + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(rollsId))
                .andExpect(jsonPath("$[0].displayOrder").value(1));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[0].id").value(rollsId));

        // A category in use cannot be deleted
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long itemId = createItem(ownerId, restaurantId, "Paneer Roll", "129.00", rollsId, "VEG");
        asAdmin(delete("/api/v1/admin/categories/{c}", rollsId), null)
                .andExpect(status().isConflict());

        // Deactivated: hidden from customers, and new items cannot use it
        asAdmin(put("/api/v1/admin/categories/{c}/deactivate", rollsId), """
                {"reason": "Merging into North Indian"}
                """).andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not((int) rollsId)));
        asOwner(ownerId, post("/api/v1/restaurants/mine/{r}/menu-items", restaurantId),
                itemJson("Egg Roll", "99.00", rollsId, "EGG"))
                .andExpect(status().isUnprocessableContent());

        // Move the item away, then the category can be deleted
        asAdmin(put("/api/v1/admin/menu-items/{i}/category", itemId), """
                {"categoryId": 6, "reason": "Category merged"}
                """).andExpect(status().isOk());
        asAdmin(delete("/api/v1/admin/categories/{c}", rollsId).param("reason", "Merged"), null)
                .andExpect(status().isNoContent());
    }

    @Test
    void priceChangeOnAnOutdatedCopyIsRejected() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long itemId = createItem(ownerId, restaurantId, "Masala Dosa", "120.00", 5L, "VEG");

        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/price", restaurantId, itemId), """
                {"price": 130.00, "version": 0}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/price", restaurantId, itemId), """
                {"price": 110.00, "version": 0}
                """).andExpect(status().isConflict());
    }

    @Test
    void menusFollowTheRestaurantsStatusInRestaurantService() throws Exception {
        long ownerId = randomId();
        long pendingRestaurant = givenRestaurant(ownerId, "PENDING");
        long suspendedRestaurant = givenRestaurant(ownerId, "SUSPENDED");

        // Owners may prepare a menu before going live, and customers do not see it yet
        createItem(ownerId, pendingRestaurant, "Filter Coffee", "40.00", 8L, "VEG");
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", pendingRestaurant))
                .andExpect(status().isNotFound());

        // A suspended restaurant's menu is frozen
        asOwner(ownerId, post("/api/v1/restaurants/mine/{r}/menu-items", suspendedRestaurant),
                itemJson("Idli", "60.00", 5L, "VEG"))
                .andExpect(status().isUnprocessableContent());

        // Another owner cannot touch the menu
        asOwner(randomId(), get("/api/v1/restaurants/mine/{r}/menu-items", pendingRestaurant), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void internalApiReturnsCurrentItemDataForOrderService() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        long dosaId = createItem(ownerId, restaurantId, "Rava Dosa", "110.00", 5L, "VEG");
        long vadaId = createItem(ownerId, restaurantId, "Medu Vada", "60.00", 5L, "VEG");
        asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/availability", restaurantId, vadaId), """
                {"available": false}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/internal/menu-items")
                        .with(TestTokens.serviceToken())
                        .param("ids", dosaId + "," + vadaId + ",999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + dosaId + ")].price").value(110.0))
                .andExpect(jsonPath("$[?(@.id == " + vadaId + ")].available").value(false));

        mockMvc.perform(get("/api/v1/internal/menu-items").with(TestTokens.serviceToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerWritesFailSafelyWhenRestaurantServiceIsDown() throws Exception {
        long restaurantId = randomId();
        when(restaurantServiceClient.findRestaurant(restaurantId))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("Connection refused")));

        asOwner(randomId(), post("/api/v1/restaurants/mine/{r}/menu-items", restaurantId),
                itemJson("Samosa", "30.00", 6L, "VEG"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Dependency unavailable"));
    }

    @Test
    void restaurantEventsKeepALocalCopySoMenusWorkWhileRestaurantServiceIsDown() throws Exception {
        long ownerId = randomId();
        long restaurantId = randomId();
        when(restaurantServiceClient.findRestaurant(restaurantId))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("Connection refused")));

        // restaurant-service announced the restaurant: catalog-service keeps its own copy
        publish(restaurantChanged(restaurantId, ownerId, "ACTIVE", 3L));
        await().atMost(EVENT_TIMEOUT).until(() -> restaurantSnapshotRepository.findById(restaurantId).isPresent());

        // Owner and customers work without restaurant-service
        createItem(ownerId, restaurantId, "Hyderabadi Biryani", "319.00", BIRYANI, "NON_VEG");
        mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Event Kitchen"));

        // The suspension arrives as an event: customers no longer see the menu
        publish(restaurantChanged(restaurantId, ownerId, "SUSPENDED", 4L));
        await().atMost(EVENT_TIMEOUT).untilAsserted(() -> mockMvc.perform(get("/api/v1/restaurants/{r}/menu", restaurantId))
                .andExpect(status().isNotFound()));

        // Older data (a late event or a slow REST answer) never overwrites newer data
        restaurantSnapshotRepository.upsertIfNewer(restaurantId, ownerId, "Event Kitchen", "ACTIVE", 3L);
        assertThat(restaurantSnapshotRepository.findById(restaurantId)).get().satisfies(copy -> {
            assertThat(copy.getStatus()).isEqualTo("SUSPENDED");
            assertThat(copy.getSourceVersion()).isEqualTo(4L);
        });
        verify(restaurantServiceClient, never()).findRestaurant(restaurantId);
    }

    @Test
    void anEventThatCannotBeReadGoesToTheDeadLetterTopic() throws Exception {
        String key = "poison-" + UUID.randomUUID();
        KafkaTemplate<Object, Object> rawBytesTemplate = new KafkaTemplate<>(producerFactory.copyWithConfigurationOverride(
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        rawBytesTemplate.send(Topics.RESTAURANT_CHANGED, key, "not an Avro message".getBytes(StandardCharsets.UTF_8)).get();

        Properties readRawBytes = new Properties();
        readRawBytes.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (Consumer<Object, Object> deadLetters = consumerFactory.createConsumer(
                "test-" + UUID.randomUUID(), null, null, readRawBytes)) {
            deadLetters.subscribe(List.of(Topics.RESTAURANT_CHANGED + Topics.DEAD_LETTER_SUFFIX));
            ConsumerRecord<Object, Object> deadLetter = awaitRecord(deadLetters, key);

            assertThat(new String((byte[]) deadLetter.value(), StandardCharsets.UTF_8)).isEqualTo("not an Avro message");
            assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
            assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value())
                    .asString(StandardCharsets.UTF_8).isEqualTo(Topics.RESTAURANT_CHANGED);
        }
    }

    @Test
    void adminCreatesEditsAndDuplicatesMenuItemsOfAnyRestaurant() throws Exception {
        long restaurantId = givenRestaurant(randomId(), "ACTIVE");

        long itemId = createItemAsAdmin(restaurantId, "Admin Biryani", "299.00", BIRYANI);
        asAdmin(post("/api/v1/admin/menu-items"), adminItemJson(restaurantId, "Admin Biryani", "299.00", BIRYANI))
                .andExpect(status().isConflict());
        asAdmin(post("/api/v1/admin/menu-items"), adminItemJson(restaurantId, "Other Dish", "99.00", 9999))
                .andExpect(status().isUnprocessableContent());
        asAdmin(post("/api/v1/admin/menu-items"), adminItemJson(randomId(), "Ghost Dish", "99.00", BIRYANI))
                .andExpect(status().isNotFound());

        // Edit with the version read last; the same version again is stale
        asAdmin(put("/api/v1/admin/menu-items/{i}", itemId), itemJson("Admin Biryani Special", "349.00", BIRYANI, "NON_VEG", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Biryani Special"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.version").value(1));
        asAdmin(put("/api/v1/admin/menu-items/{i}", itemId), itemJson("Stale Name", "349.00", BIRYANI, "NON_VEG", 0))
                .andExpect(status().isConflict());

        // A blocked item can be edited and stays blocked
        asAdmin(put("/api/v1/admin/menu-items/{i}/deactivate", itemId), """
                {"reason": "Checking the photo"}
                """).andExpect(status().isOk());
        asAdmin(put("/api/v1/admin/menu-items/{i}", itemId), itemJson("Admin Biryani Special", "359.00", BIRYANI, "NON_VEG", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.price").value(359.00));

        // Copies are hidden and numbered
        asAdmin(post("/api/v1/admin/menu-items/{i}/duplicate", itemId), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Admin Biryani Special (copy)"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.price").value(359.00));
        asAdmin(post("/api/v1/admin/menu-items/{i}/duplicate", itemId), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Admin Biryani Special (copy 2)"));
        asAdmin(post("/api/v1/admin/menu-items/{i}/duplicate", randomId()), null)
                .andExpect(status().isNotFound());

        // Audit trail, newest first
        asAdmin(get("/api/v1/admin/menu-items/{i}/audit-logs", itemId), null)
                .andExpect(jsonPath("$.content[*].action", org.hamcrest.Matchers.contains(
                        "MENU_ITEM_UPDATED_BY_ADMIN", "MENU_ITEM_BLOCKED_BY_ADMIN", "MENU_ITEM_UPDATED_BY_ADMIN",
                        "MENU_ITEM_CREATED_BY_ADMIN")));

        // Search-service follows: CREATED, UPDATED, STATUS_CHANGED, UPDATED
        List<MenuItemChanged> events = KafkaTestEvents.awaitEvents(consumerFactory, Topics.MENU_ITEM_CHANGED,
                String.valueOf(itemId), MenuItemChanged.class, 4);
        assertThat(events).extracting(MenuItemChanged::getChangeType)
                .containsExactly("CREATED", "UPDATED", "STATUS_CHANGED", "UPDATED");
        assertThat(events.get(1).getName()).isEqualTo("Admin Biryani Special");
    }

    @Test
    void ingredientsAndNutritionAreStoredReturnedChangedAndCleared() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");

        String body = asOwner(ownerId, post("/api/v1/restaurants/mine/{r}/menu-items", restaurantId), """
                {"name": "Fit Bowl", "price": 199.00, "categoryId": %d, "foodType": "VEG",
                 "ingredients": "Quinoa, chickpeas", "nutritionInfo": "Calories 450 kcal, Protein 20 g"}
                """.formatted(BIRYANI))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(body, "$.id")).longValue();

        asOwner(ownerId, get("/api/v1/restaurants/mine/{r}/menu-items/{i}", restaurantId, itemId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients").value("Quinoa, chickpeas"))
                .andExpect(jsonPath("$.nutritionInfo").value("Calories 450 kcal, Protein 20 g"));

        asAdmin(put("/api/v1/admin/menu-items/{i}", itemId), """
                {"name": "Fit Bowl", "price": 199.00, "categoryId": %d, "foodType": "VEG", "version": 0,
                 "ingredients": "Quinoa", "nutritionInfo": "Calories 400 kcal"}
                """.formatted(BIRYANI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients").value("Quinoa"))
                .andExpect(jsonPath("$.nutritionInfo").value("Calories 400 kcal"));

        // A request without the fields still works and clears them (full update)
        asAdmin(put("/api/v1/admin/menu-items/{i}", itemId), itemJson("Fit Bowl", "199.00", BIRYANI, "VEG", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients").doesNotExist())
                .andExpect(jsonPath("$.nutritionInfo").doesNotExist());
    }

    @Test
    void categoryStatsCountOrderableItemsPerCategoryInTheDatabase() throws Exception {
        long ownerId = randomId();
        long restaurantId = givenRestaurant(ownerId, "ACTIVE");
        String body = asAdmin(post("/api/v1/admin/categories"), """
                {"name": "Stats %s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long categoryId = ((Number) JsonPath.read(body, "$.id")).longValue();
        String categoryName = JsonPath.read(body, "$.name");

        // 25 items, more than any seeded category gets in these tests: 15 orderable, 5 sold out, 5 blocked
        for (int i = 0; i < 25; i++) {
            long itemId = createItemAsAdmin(restaurantId, "Stats Dish " + i, "50.00", categoryId);
            if (i < 5) {
                asOwner(ownerId, put("/api/v1/restaurants/mine/{r}/menu-items/{i}/availability", restaurantId, itemId), """
                        {"available": false}
                        """).andExpect(status().isOk());
            } else if (i < 10) {
                asAdmin(put("/api/v1/admin/menu-items/{i}/deactivate", itemId), """
                        {"reason": "Stats test"}
                        """).andExpect(status().isOk());
            }
        }

        asAdmin(get("/api/v1/admin/menu-items/category-stats"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$[0].categoryName").value(categoryName))
                .andExpect(jsonPath("$[0].totalItems").value(25))
                .andExpect(jsonPath("$[0].orderableItems").value(15))
                .andExpect(jsonPath("$[0].orderablePercent").value(60));
        asAdmin(get("/api/v1/admin/menu-items/category-stats").param("limit", "1"), null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryId").value(categoryId));
    }

    private long createItemAsAdmin(long restaurantId, String name, String price, long categoryId) throws Exception {
        String body = asAdmin(post("/api/v1/admin/menu-items"), adminItemJson(restaurantId, name, price, categoryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String adminItemJson(long restaurantId, String name, String price, long categoryId) {
        return """
                {"restaurantId": %d, "name": "%s", "price": %s, "categoryId": %d, "foodType": "VEG"}
                """.formatted(restaurantId, name, price, categoryId);
    }

    private static String itemJson(String name, String price, long categoryId, String foodType, long version) {
        return """
                {"name": "%s", "price": %s, "categoryId": %d, "foodType": "%s", "version": %d}
                """.formatted(name, price, categoryId, foodType, version);
    }

    private void publish(RestaurantChanged event) throws Exception {
        kafkaTemplate.send(Topics.RESTAURANT_CHANGED, String.valueOf(event.getRestaurantId()), event).get();
    }

    private static RestaurantChanged restaurantChanged(long restaurantId, long ownerId, String status, long version) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setChangeType(version == 0 ? "SUBMITTED" : "STATUS_CHANGED")
                .setRestaurantId(restaurantId)
                .setVersion(version)
                .setOwnerUserId(ownerId)
                .setName("Event Kitchen")
                .setCity("Bengaluru")
                .setStatus(status)
                .build();
    }

    private static ConsumerRecord<Object, Object> awaitRecord(Consumer<Object, Object> consumer, String key) {
        long deadline = System.nanoTime() + EVENT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<Object, Object> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(consumerRecord.key())) {
                    return consumerRecord;
                }
            }
        }
        throw new AssertionError("No record with key " + key);
    }

    private long givenRestaurant(long ownerId, String status) {
        long restaurantId = randomId();
        when(restaurantServiceClient.findRestaurant(restaurantId))
                .thenReturn(Optional.of(new RestaurantSummary(restaurantId, ownerId, "Test Kitchen", status, 0L)));
        return restaurantId;
    }

    private long createItem(long ownerId, long restaurantId, String name, String price, long categoryId, String foodType)
            throws Exception {
        String body = asOwner(ownerId, post("/api/v1/restaurants/mine/{r}/menu-items", restaurantId),
                itemJson(name, price, categoryId, foodType))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String itemJson(String name, String price, long categoryId, String foodType) {
        return """
                {"name": "%s", "price": %s, "categoryId": %d, "foodType": "%s"}
                """.formatted(name, price, categoryId, foodType);
    }

    private ResultActions asOwner(long ownerId, MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(TestTokens.as(ownerId, "RESTAURANT_ADMIN"));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(TestTokens.as(ADMIN_ID, "PLATFORM_ADMIN"));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000, 1_000_000_000);
    }
}
