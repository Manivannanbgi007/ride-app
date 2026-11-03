package com.fooddelivery.reviewservice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.fooddelivery.reviewservice.client.MenuItemInfo;
import com.fooddelivery.reviewservice.client.MenuItemLookup;
import com.fooddelivery.reviewservice.client.OrderLookup;
import com.fooddelivery.reviewservice.client.OrderReviewInfo;
import com.fooddelivery.reviewservice.exception.DependencyUnavailableException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real MySQL (Testcontainers). order-service and catalog-service are replaced by mocks,
 * and the clock is a mock too, so a test chooses when each review was written.
 * <p>
 * Tests share one database, so each test uses its own restaurant (and menu item) ids: filters by restaurant keep
 * their data apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewServiceIntegrationTest {

    private static final long ADMIN = 1L;
    /** Shared counters make every id unique across the tests of this class. */
    private static final AtomicLong IDS = new AtomicLong(1_000);

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OrderLookup orderLookup;
    @MockitoBean
    private MenuItemLookup menuItemLookup;
    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        at("2026-10-05T10:00:00Z");
        // Catalog knows nothing unless a test says so
        when(menuItemLookup.findMenuItems(any())).thenReturn(Map.of());
    }

    // ------------------------------------------------------------------ writing a review

    @Test
    void aCustomerReviewsADeliveredOrderAndReadsItBack() throws Exception {
        long restaurant = nextId();
        long customer = nextId();
        long orderId = stubOrder(customer, restaurant, "DELIVERED", 101L, 102L);

        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 4, "comment": "  Hot and fresh  ",
                         "tags": ["FAST_DELIVERY", "DELICIOUS"],
                         "dishRatings": [{"menuItemId": 102, "rating": 2}, {"menuItemId": 101, "rating": 5}]}
                        """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.customerId").value(customer))
                .andExpect(jsonPath("$.customerName").value("Customer " + customer))
                .andExpect(jsonPath("$.restaurantId").value(restaurant))
                .andExpect(jsonPath("$.restaurantName").value("Restaurant " + restaurant))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Hot and fresh"))
                .andExpect(jsonPath("$.tags", contains("DELICIOUS", "FAST_DELIVERY")))
                .andExpect(jsonPath("$.dishRatings[0].menuItemId").value(101))
                .andExpect(jsonPath("$.dishRatings[0].dishName").value("Dish 101"))
                .andExpect(jsonPath("$.dishRatings[0].rating").value(5))
                .andExpect(jsonPath("$.dishRatings[1].menuItemId").value(102))
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T10:00:00Z"));

        // The storefront asks for it to decide whether to show the form
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Hot and fresh"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.dishRatings", hasSize(2)));

        // Nobody else sees it as theirs, and an order without a review has none to show
        mockMvc.perform(customerGet(customer + 1, "/api/v1/reviews/orders/" + orderId))
                .andExpect(status().isNotFound());
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + nextId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBlankCommentAndNoTagsAreFine() throws Exception {
        long customer = nextId();
        long orderId = stubOrder(customer, nextId(), "DELIVERED", 101L);

        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "comment": "   "}
                        """.formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(nullValue()))
                .andExpect(jsonPath("$.tags", hasSize(0)))
                .andExpect(jsonPath("$.dishRatings", hasSize(0)));
    }

    @Test
    void anOrderCanBeReviewedOnlyOnce() throws Exception {
        long customer = nextId();
        long orderId = stubOrder(customer, nextId(), "DELIVERED", 101L);
        String body = """
                {"orderId": %d, "rating": 5}
                """.formatted(orderId);

        mockMvc.perform(customerPost(customer, body)).andExpect(status().isCreated());
        mockMvc.perform(customerPost(customer, body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This order has already been reviewed"));
    }

    @Test
    void theOrderMustBeDeliveredAndTheCallersOwn() throws Exception {
        long customer = nextId();
        long preparing = stubOrder(customer, nextId(), "PREPARING", 101L);
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5}
                        """.formatted(preparing)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Only delivered orders can be reviewed"));

        long foreign = stubOrder(customer, nextId(), "DELIVERED", 101L);
        mockMvc.perform(customerPost(customer + 1, """
                        {"orderId": %d, "rating": 5}
                        """.formatted(foreign)))
                .andExpect(status().isNotFound());

        long unknown = nextId();
        when(orderLookup.findOrder(unknown)).thenReturn(Optional.empty());
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5}
                        """.formatted(unknown)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dishesAndTagsAreChecked() throws Exception {
        long customer = nextId();
        long orderId = stubOrder(customer, nextId(), "DELIVERED", 101L);

        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "dishRatings": [{"menuItemId": 999, "rating": 3}]}
                        """.formatted(orderId)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5,
                         "dishRatings": [{"menuItemId": 101, "rating": 3}, {"menuItemId": 101, "rating": 4}]}
                        """.formatted(orderId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5,
                         "tags": ["DELICIOUS", "EXCELLENT", "GOOD_SERVICE", "FAST_DELIVERY", "GOOD_VALUE"]}
                        """.formatted(orderId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "tags": ["DELICIOUS", "DELICIOUS"]}
                        """.formatted(orderId)))
                .andExpect(status().isBadRequest());

        // None of the refused requests used up the order: it can still be reviewed
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "tags": ["DELICIOUS", "EXCELLENT", "GOOD_SERVICE", "FAST_DELIVERY"]}
                        """.formatted(orderId)))
                .andExpect(status().isCreated());
    }

    @Test
    void nothingIsSavedWhenOrderServiceIsDown() throws Exception {
        long customer = nextId();
        long orderId = nextId();
        when(orderLookup.findOrder(orderId))
                .thenThrow(new DependencyUnavailableException("order-service", new RuntimeException("connection refused")));

        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5}
                        """.formatted(orderId)))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + orderId)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ the admin list

    @Test
    void theListFiltersSortsAndPages() throws Exception {
        long restaurant = nextId();
        long other = nextId();
        // Times in the business time zone (Asia/Kolkata, UTC+5:30): r3 is already October 7 there
        long r1 = review(restaurant, 5, "Loved it", "2026-10-05T10:00:00Z", "\"DELICIOUS\", \"GOOD_VALUE\"", "", 101L);
        long r2 = review(restaurant, 4, null, "2026-10-06T10:00:00Z", "\"FAST_DELIVERY\"", "", 101L);
        long r3 = review(restaurant, 5, "ok", "2026-10-06T20:00:00Z", "\"DELICIOUS\"", "", 101L);
        long r4 = review(restaurant, 5, null, "2026-10-08T10:00:00Z", "", "", 101L);
        review(other, 1, "Cold", "2026-10-06T10:00:00Z", "", "", 101L);

        // Newest first by default, only this restaurant
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains((int) r4, (int) r3, (int) r2, (int) r1)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.size").value(10));
        // Tags are loaded for the whole page
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&sort=createdAt,asc"))
                .andExpect(jsonPath("$.content[0].tags", contains("DELICIOUS", "GOOD_VALUE")))
                .andExpect(jsonPath("$.content[0].dishRatings", hasSize(0)));

        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&rating=4"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r2)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&tag=DELICIOUS"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3, (int) r1)))
                .andExpect(jsonPath("$.totalElements").value(2));
        // Inclusive days in the business time zone
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&from=2026-10-07&to=2026-10-07"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&from=2026-10-06&to=2026-10-07"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3, (int) r2)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&from=2026-10-08"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r4)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant
                        + "&rating=5&tag=DELICIOUS&from=2026-10-05&to=2026-10-05"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r1)));

        // Sorting: by rating, ties newest first (the id is the last sort key)
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&sort=rating,asc"))
                .andExpect(jsonPath("$.content[0].id").value(r2));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&sort=rating,desc"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r4, (int) r3, (int) r1, (int) r2)));

        // Paging
        mockMvc.perform(adminGet("/api/v1/admin/reviews?restaurantId=" + restaurant + "&size=3&page=1"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void badFiltersAreRefused() throws Exception {
        mockMvc.perform(adminGet("/api/v1/admin/reviews?sort=comment,desc")).andExpect(status().isBadRequest());
        mockMvc.perform(adminGet("/api/v1/admin/reviews?from=2026-10-09&to=2026-10-01")).andExpect(status().isBadRequest());
        mockMvc.perform(adminGet("/api/v1/admin/reviews?tag=NOT_A_TAG")).andExpect(status().isBadRequest());
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?from=2026-10-09&to=2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ summary

    @Test
    void theSummaryCountsAndAveragesInTheDatabase() throws Exception {
        long restaurant = nextId();
        review(restaurant, 5, null, "2026-10-05T10:00:00Z", "", "", 101L);
        review(restaurant, 5, null, "2026-10-05T11:00:00Z", "", "", 101L);
        review(restaurant, 4, null, "2026-10-05T12:00:00Z", "", "", 101L);
        review(restaurant, 2, null, "2026-10-09T12:00:00Z", "", "", 101L);

        // (5 + 5 + 4 + 2) / 4 = 4.0
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?restaurantId=" + restaurant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(1))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['4']").value(1))
                .andExpect(jsonPath("$.distribution['5']").value(2));
        // (5 + 5 + 4) / 3 = 4.67, shown with one decimal
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?restaurantId=" + restaurant + "&to=2026-10-08"))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.7));
    }

    @Test
    void theListAndTheSummaryCanBeNarrowedToOneDish() throws Exception {
        long restaurant = nextId();
        long biryani = nextId();
        long kulfi = nextId();
        // Overall rating of the review and the dish's own rating differ on purpose
        long r1 = review(restaurant, 5, null, "2026-10-05T10:00:00Z", "\"DELICIOUS\"",
                dish(biryani, 2) + "," + dish(kulfi, 5), biryani, kulfi);
        long r2 = review(restaurant, 4, "fine", "2026-10-06T10:00:00Z", "", dish(biryani, 4), biryani, kulfi);
        long r3 = review(restaurant, 5, null, "2026-10-09T10:00:00Z", "\"DELICIOUS\"", dish(biryani, 3), biryani);
        review(restaurant, 1, null, "2026-10-06T11:00:00Z", "", "", biryani); // mentions no rating of the dish
        long otherRestaurant = nextId();
        review(otherRestaurant, 3, null, "2026-10-06T12:00:00Z", "", dish(biryani, 1), biryani);

        // The list: only reviews with a rating of the dish, with all their dish ratings (for the other dish too)
        mockMvc.perform(adminGet("/api/v1/admin/reviews?menuItemId=" + biryani + "&restaurantId=" + restaurant))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3, (int) r2, (int) r1)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[2].dishRatings", hasSize(2)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?menuItemId=" + biryani + "&restaurantId=" + restaurant
                        + "&sort=createdAt,asc&size=2&page=1"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3)))
                .andExpect(jsonPath("$.totalElements").value(3));
        // Combined with the other filters; rating is still the overall rating of the review
        mockMvc.perform(adminGet("/api/v1/admin/reviews?menuItemId=" + biryani + "&restaurantId=" + restaurant
                        + "&rating=5&tag=DELICIOUS&from=2026-10-05&to=2026-10-05"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r1)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?menuItemId=" + biryani + "&restaurantId=" + restaurant + "&rating=4"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r2)));
        mockMvc.perform(adminGet("/api/v1/admin/reviews?menuItemId=" + nextId()))
                .andExpect(jsonPath("$.totalElements").value(0));

        // The summary is over the dish's ratings 2, 4 and 3, not the reviews' 5, 4 and 5
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?menuItemId=" + biryani + "&restaurantId=" + restaurant))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.averageRating").value(3.0))
                .andExpect(jsonPath("$.distribution['2']").value(1))
                .andExpect(jsonPath("$.distribution['3']").value(1))
                .andExpect(jsonPath("$.distribution['4']").value(1))
                .andExpect(jsonPath("$.distribution['5']").value(0));
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?menuItemId=" + biryani + "&restaurantId=" + restaurant
                        + "&from=2026-10-06&to=2026-10-08"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.averageRating").value(4.0));
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?menuItemId=" + biryani))
                .andExpect(jsonPath("$.count").value(4));
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?menuItemId=" + nextId()))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.averageRating").value(nullValue()));
    }

    @Test
    void anEmptySummaryHasNoAverage() throws Exception {
        mockMvc.perform(adminGet("/api/v1/admin/reviews/summary?restaurantId=" + nextId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.distribution['5']").value(0));
    }

    // ------------------------------------------------------------------ featured dishes

    /**
     * Dishes A, B, C, E in one restaurant, rated like this (overall rating and comment of the review in brackets):
     * <pre>
     * r1 (5, "Loved")  : A5 B4    E5      t1
     * r2 (4, none)     : A4 B4 C5 E4      t2
     * r3 (5, "ok")     :    B5    E5      t3
     * r4 (5, none)     :          E4      t4
     * A 4.5 (2)   B 4.33 (3)   C 5.0 (1)   E 4.5 (4)
     * </pre>
     */
    @Test
    void featuredDishesAreRankedPagedAndEnrichedFromTheCatalog() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long b = nextId();
        long c = nextId();
        long e = nextId();
        long r1 = review(restaurant, 5, "Loved", "2026-10-05T10:00:00Z", "",
                dish(a, 5) + "," + dish(b, 4) + "," + dish(e, 5), a, b, c, e);
        long r2 = review(restaurant, 4, null, "2026-10-06T10:00:00Z", "",
                dish(a, 4) + "," + dish(b, 4) + "," + dish(c, 5) + "," + dish(e, 4), a, b, c, e);
        long r3 = review(restaurant, 5, "ok", "2026-10-07T10:00:00Z", "", dish(b, 5) + "," + dish(e, 5), a, b, c, e);
        review(restaurant, 5, null, "2026-10-08T10:00:00Z", "", dish(e, 4), a, b, c, e);
        // A review of another restaurant never counts
        review(nextId(), 5, "Other", "2026-10-08T10:00:00Z", "", dish(a, 1), a);

        when(menuItemLookup.findMenuItems(any())).thenReturn(
                Map.of(c, new MenuItemInfo(c, "Chicken Biryani", "https://img.example/c.jpg", "Biryani")));

        // Best average first; the same average: more ratings first (E 4.5 with 4 ratings before A 4.5 with 2)
        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + restaurant + "&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                // C: the name, picture and category now come from the catalog; its only review has no comment
                .andExpect(jsonPath("$.content[0].menuItemId").value(c))
                .andExpect(jsonPath("$.content[0].dishName").value("Chicken Biryani"))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://img.example/c.jpg"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Biryani"))
                .andExpect(jsonPath("$.content[0].restaurantId").value(restaurant))
                .andExpect(jsonPath("$.content[0].restaurantName").value("Restaurant " + restaurant))
                .andExpect(jsonPath("$.content[0].averageRating").value(5.0))
                .andExpect(jsonPath("$.content[0].ratingCount").value(1))
                .andExpect(jsonPath("$.content[0].topReview.reviewId").value(r2))
                .andExpect(jsonPath("$.content[0].topReview.comment").value(nullValue()))
                .andExpect(jsonPath("$.content[0].topReview.rating").value(4))
                // E: unknown to the catalog, so the saved name and no picture; of its reviews rated 5 with a comment
                // (r1 and r3) the more recent one is the top review
                .andExpect(jsonPath("$.content[1].menuItemId").value(e))
                .andExpect(jsonPath("$.content[1].dishName").value("Dish " + e))
                .andExpect(jsonPath("$.content[1].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[1].categoryName").value(nullValue()))
                .andExpect(jsonPath("$.content[1].averageRating").value(4.5))
                .andExpect(jsonPath("$.content[1].ratingCount").value(4))
                .andExpect(jsonPath("$.content[1].topReview.reviewId").value(r3))
                .andExpect(jsonPath("$.content[1].topReview.customerName").exists())
                .andExpect(jsonPath("$.content[1].topReview.comment").value("ok"))
                .andExpect(jsonPath("$.content[1].topReview.rating").value(5))
                .andExpect(jsonPath("$.content[1].topReview.createdAt").value("2026-10-07T10:00:00Z"));

        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + restaurant + "&size=2&page=1"))
                .andExpect(jsonPath("$.content[*].menuItemId", contains((int) a, (int) b)))
                .andExpect(jsonPath("$.content[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.content[0].ratingCount").value(2))
                .andExpect(jsonPath("$.content[0].topReview.reviewId").value(r1))
                .andExpect(jsonPath("$.content[1].averageRating").value(4.3))
                .andExpect(jsonPath("$.content[1].ratingCount").value(3))
                .andExpect(jsonPath("$.content[1].topReview.reviewId").value(r3))
                .andExpect(jsonPath("$.last").value(true));

        // The period narrows the ratings: only r1 and r2 (before October 7)
        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + restaurant
                        + "&to=2026-10-06&size=12"))
                .andExpect(jsonPath("$.content[*].menuItemId", contains((int) c, (int) a, (int) e, (int) b)))
                .andExpect(jsonPath("$.content[2].ratingCount").value(2));
    }

    @Test
    void theDefaultPageHasThreeDishes() throws Exception {
        long restaurant = nextId();
        long first = nextId();
        long[] items = {first, first + 1, first + 2, first + 3};
        review(restaurant, 5, null, "2026-10-05T10:00:00Z", "",
                dish(items[0], 5) + "," + dish(items[1], 4) + "," + dish(items[2], 3) + "," + dish(items[3], 2), items);

        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + restaurant))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.content[*].menuItemId", contains((int) items[0], (int) items[1], (int) items[2])))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void featuredDishesStillAnswerWhenTheCatalogIsDown() throws Exception {
        long restaurant = nextId();
        long item = nextId();
        review(restaurant, 5, "Great", "2026-10-05T10:00:00Z", "", dish(item, 5), item);
        // What the real client returns when catalog-service cannot be reached
        when(menuItemLookup.findMenuItems(any())).thenReturn(Map.of());

        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + restaurant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].dishName").value("Dish " + item))
                .andExpect(jsonPath("$.content[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[0].categoryName").value(nullValue()))
                .andExpect(jsonPath("$.content[0].averageRating").value(5.0))
                .andExpect(jsonPath("$.content[0].topReview.comment").value("Great"));
    }

    @Test
    void noRatedDishesNoRows() throws Exception {
        mockMvc.perform(adminGet("/api/v1/admin/reviews/featured-dishes?restaurantId=" + nextId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------ dishes a customer liked

    @Test
    void likedDishesOfACustomerAreRankedByTheirOwnRatingsInThePeriod() throws Exception {
        long restaurant = nextId();
        long customer = nextId();
        long a = nextId();
        long b = nextId();
        long d = nextId();
        long e = nextId();
        // This customer's reviews: an old one (August), one in early October and one yesterday
        reviewBy(customer, restaurant, "2026-08-01T10:00:00Z", dish(a, 5) + "," + dish(b, 3), a, b);
        reviewBy(customer, restaurant, "2026-10-01T10:00:00Z", dish(a, 4) + "," + dish(b, 4) + "," + dish(d, 5), a, b, d);
        reviewBy(customer, restaurant, "2026-10-08T10:00:00Z", dish(a, 5) + "," + dish(e, 4), a, e);
        // Someone else's rating of the same dish never counts
        reviewBy(nextId(), restaurant, "2026-10-08T10:00:00Z", dish(a, 1), a);
        at("2026-10-09T10:00:00Z");
        String path = "/api/v1/admin/reviews/customers/" + customer + "/liked-dishes";

        // All time: D 5.0, A 4.7 (3 ratings), E 4.0, B 3.5
        mockMvc.perform(adminGet(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer))
                .andExpect(jsonPath("$.period").value("ALL"))
                .andExpect(jsonPath("$.ratingCount").value(7))
                .andExpect(jsonPath("$.dishes[*].menuItemId", contains((int) d, (int) a, (int) e, (int) b)))
                .andExpect(jsonPath("$.dishes[1].dishName").value("Dish " + a))
                .andExpect(jsonPath("$.dishes[1].averageRating").value(4.7))
                .andExpect(jsonPath("$.dishes[1].ratingCount").value(3))
                .andExpect(jsonPath("$.dishes[1].lastRatedAt").value("2026-10-08T10:00:00Z"));

        // Last 30 days: B and E both 4.0 with one rating, the more recent E first; the August review is out
        mockMvc.perform(adminGet(path + "?period=MONTHLY"))
                .andExpect(jsonPath("$.ratingCount").value(5))
                .andExpect(jsonPath("$.dishes[*].menuItemId", contains((int) d, (int) a, (int) e, (int) b)))
                .andExpect(jsonPath("$.dishes[1].averageRating").value(4.5));

        // Last 7 days: only yesterday's review
        mockMvc.perform(adminGet(path + "?period=WEEKLY"))
                .andExpect(jsonPath("$.ratingCount").value(2))
                .andExpect(jsonPath("$.dishes[*].menuItemId", contains((int) a, (int) e)));

        // The limit cuts the list, not the count
        mockMvc.perform(adminGet(path + "?limit=2"))
                .andExpect(jsonPath("$.dishes", hasSize(2)))
                .andExpect(jsonPath("$.ratingCount").value(7));

        mockMvc.perform(adminGet(path + "?limit=0")).andExpect(status().isBadRequest());
        mockMvc.perform(adminGet(path + "?period=DAILY")).andExpect(status().isBadRequest());
        mockMvc.perform(customerGet(customer, path)).andExpect(status().isForbidden());

        // Unknown customer: nothing
        mockMvc.perform(adminGet("/api/v1/admin/reviews/customers/" + nextId() + "/liked-dishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingCount").value(0))
                .andExpect(jsonPath("$.dishes", hasSize(0)));
    }

    // ------------------------------------------------------------------ helpers

    /** An existing customer reviews another delivered order at the given time (overall rating 5). */
    private void reviewBy(long customer, long restaurantId, String instant, String dishesJson, long... menuItemIds)
            throws Exception {
        at(instant);
        long orderId = stubOrder(customer, restaurantId, "DELIVERED", menuItemIds);
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "tags": [], "dishRatings": [%s]}
                        """.formatted(orderId, dishesJson)))
                .andExpect(status().isCreated());
    }

    private static long nextId() {
        return IDS.incrementAndGet();
    }

    private static String dish(long menuItemId, int rating) {
        return "{\"menuItemId\": %d, \"rating\": %d}".formatted(menuItemId, rating);
    }

    /** The mocked clock says it is this moment (the review gets it as its creation time). */
    private void at(String instant) {
        when(clock.instant()).thenReturn(Instant.parse(instant));
    }

    /** Order-service knows this order; its dishes are called "Dish <id>". Returns the order id. */
    private long stubOrder(long customerId, long restaurantId, String status, long... menuItemIds) {
        long orderId = nextId();
        OrderReviewInfo.Line[] lines = Arrays.stream(menuItemIds)
                .mapToObj(id -> new OrderReviewInfo.Line(id, "Dish " + id))
                .toArray(OrderReviewInfo.Line[]::new);
        when(orderLookup.findOrder(orderId)).thenReturn(Optional.of(new OrderReviewInfo(orderId, customerId,
                "Customer " + customerId, restaurantId, "Restaurant " + restaurantId, status, Arrays.asList(lines))));
        return orderId;
    }

    /**
     * A new customer reviews a delivered order of the restaurant at the given time.
     *
     * @param comment      null for no comment
     * @param tagsJson     the inside of the JSON "tags" array, for example {@code "DELICIOUS", "GOOD_VALUE"}
     * @param dishesJson   the inside of the JSON "dishRatings" array
     * @param menuItemIds  the dishes on the order
     * @return the id of the review
     */
    private long review(long restaurantId, int rating, String comment, String instant, String tagsJson,
                        String dishesJson, long... menuItemIds) throws Exception {
        at(instant);
        long customer = nextId();
        long orderId = stubOrder(customer, restaurantId, "DELIVERED", menuItemIds);
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        String body = """
                {"orderId": %d, "rating": %d, "comment": %s, "tags": [%s], "dishRatings": [%s]}
                """.formatted(orderId, rating, commentJson, tagsJson, dishesJson);
        String response = mockMvc.perform(customerPost(customer, body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private MockHttpServletRequestBuilder customerPost(long customerId, String body) {
        return post("/api/v1/reviews")
                .with(TestTokens.as(customerId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder customerGet(long customerId, String path) {
        return get(path).with(TestTokens.as(customerId, "CUSTOMER"));
    }

    private MockHttpServletRequestBuilder adminGet(String path) {
        return get(path).with(TestTokens.as(ADMIN, "PLATFORM_ADMIN"));
    }

}
