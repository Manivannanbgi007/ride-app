package com.fooddelivery.reviewservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

/**
 * The public ratings and the internal feed, against real rows. The public requests carry no token; the internal ones
 * the services' shared token.
 */
class RatingApiIntegrationTest extends AbstractReviewApiTest {

    // ------------------------------------------------------------------ validation

    @Test
    void theIdListMustHaveOneToHundredPositiveNumbers() throws Exception {
        for (String endpoint : List.of("/api/v1/ratings/restaurants", "/api/v1/ratings/menu-items")) {
            mockMvc.perform(publicGet(endpoint)).andExpect(status().isBadRequest());
            for (String ids : List.of("", "%20", "1,,2", "1,", ",1", "abc", "1,x", "-1", "0", "1,-5", "1.5",
                    "99999999999999999999")) {
                mockMvc.perform(publicGet(endpoint + "?ids=" + ids)).andExpect(status().isBadRequest());
            }
            mockMvc.perform(publicGet(endpoint + "?ids=" + ids(101))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("At most 100 ids at a time"));
            // 100 is the limit; a repeated id counts once
            mockMvc.perform(publicGet(endpoint + "?ids=" + ids(100))).andExpect(status().isOk());
            mockMvc.perform(publicGet(endpoint + "?ids=" + ids(100) + ",1,1")).andExpect(status().isOk());
        }
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/0")).andExpect(status().isBadRequest());
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/-3")).andExpect(status().isBadRequest());
    }

    @Test
    void theReviewListHasLimitedPagesAndKnownSorts() throws Exception {
        String path = "/api/v1/ratings/restaurants/" + nextId() + "/reviews";
        for (String query : List.of("?size=0", "?size=51", "?page=-1", "?size=abc", "?sort=best", "?sort=NEWEST",
                "?sort=createdAt,desc")) {
            mockMvc.perform(publicGet(path + query)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(publicGet(path + "?sort=best"))
                .andExpect(jsonPath("$.detail").value("Cannot sort by 'best'. Allowed values: [newest, highest, lowest]"));
        mockMvc.perform(publicGet(path + "?size=50&sort=lowest")).andExpect(status().isOk());
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/0/reviews")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ restaurants and dishes in bulk

    @Test
    void restaurantRatingsAreAveragedInTheDatabaseAndUnratedIdsAreOmitted() throws Exception {
        long thirds = nextId();
        long quarter = nextId();
        long unrated = nextId();
        long single = nextId();
        // 5, 4, 4 = 4.33 -> 4.3
        review(thirds, 5, "2026-10-05T10:00:00Z", "");
        review(thirds, 4, "2026-10-05T11:00:00Z", "");
        review(thirds, 4, "2026-10-05T12:00:00Z", "");
        // 5, 4, 4, 4 = 4.25 -> 4.3 (half up, not half even)
        review(quarter, 5, "2026-10-05T10:00:00Z", "");
        review(quarter, 4, "2026-10-05T11:00:00Z", "");
        review(quarter, 4, "2026-10-05T12:00:00Z", "");
        review(quarter, 4, "2026-10-05T13:00:00Z", "");
        // 1 and 2 = 1.5, one review = 2.0
        review(single, 2, "2026-10-05T10:00:00Z", "");

        mockMvc.perform(publicGet("/api/v1/ratings/restaurants?ids=" + single + "," + unrated + "," + quarter + ","
                        + thirds + "," + thirds))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // In id order, whatever the order asked for
                .andExpect(jsonPath("$[*].id", contains((int) thirds, (int) quarter, (int) single)))
                .andExpect(jsonPath("$[0].average").value(4.3))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].average").value(4.3))
                .andExpect(jsonPath("$[1].count").value(4))
                .andExpect(jsonPath("$[2].average").value(2.0))
                .andExpect(jsonPath("$[2].count").value(1));

        // Nobody has rated this one yet: an empty list, not an error
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants?ids=" + unrated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dishRatingsComeFromTheRatingsOfTheDishesNotTheOverallRating() throws Exception {
        long restaurant = nextId();
        long biryani = nextId();
        long kulfi = nextId();
        long unrated = nextId();
        // The overall stars (5) differ on purpose from the dish stars
        review(restaurant, 5, "2026-10-05T10:00:00Z", dish(biryani, 2) + "," + dish(kulfi, 5), biryani, kulfi);
        review(restaurant, 5, "2026-10-05T11:00:00Z", dish(biryani, 3), biryani, kulfi);
        review(restaurant, 5, "2026-10-05T12:00:00Z", dish(biryani, 3), biryani);
        // Dish on the order but not rated: no rating
        review(restaurant, 5, "2026-10-05T13:00:00Z", "", unrated);
        // The same dish in another restaurant's order still counts for the dish
        review(nextId(), 1, "2026-10-05T14:00:00Z", dish(biryani, 4), biryani);

        mockMvc.perform(publicGet("/api/v1/ratings/menu-items?ids=" + unrated + "," + kulfi + "," + biryani))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) biryani, (int) kulfi)))
                // (2 + 3 + 3 + 4) / 4 = 3.0
                .andExpect(jsonPath("$[0].average").value(3.0))
                .andExpect(jsonPath("$[0].count").value(4))
                .andExpect(jsonPath("$[1].average").value(5.0))
                .andExpect(jsonPath("$[1].count").value(1));
    }

    // ------------------------------------------------------------------ one restaurant or dish

    @Test
    void oneRestaurantShowsAverageCountAndStars() throws Exception {
        long restaurant = nextId();
        review(restaurant, 5, "2026-10-05T10:00:00Z", "");
        review(restaurant, 5, "2026-10-05T11:00:00Z", "");
        review(restaurant, 4, "2026-10-05T12:00:00Z", "");
        review(restaurant, 2, "2026-10-05T13:00:00Z", "");
        review(restaurant, 1, "2026-10-05T14:00:00Z", "");
        // Another restaurant's reviews never count
        review(nextId(), 3, "2026-10-05T15:00:00Z", "");

        // (5 + 5 + 4 + 2 + 1) / 5 = 3.4
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(restaurant))
                .andExpect(jsonPath("$.average").value(3.4))
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.distribution['1']").value(1))
                .andExpect(jsonPath("$.distribution['2']").value(1))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['4']").value(1))
                .andExpect(jsonPath("$.distribution['5']").value(2));
    }

    @Test
    void aRestaurantWithoutReviewsIsNotAnError() throws Exception {
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + nextId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(0))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['4']").value(0))
                .andExpect(jsonPath("$.distribution['5']").value(0));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + nextId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.distribution['5']").value(0));
    }

    @Test
    void oneDishShowsItsOwnRatings() throws Exception {
        long restaurant = nextId();
        long dish = nextId();
        review(restaurant, 1, "2026-10-05T10:00:00Z", dish(dish, 5), dish);
        review(restaurant, 1, "2026-10-05T11:00:00Z", dish(dish, 5), dish);
        review(restaurant, 5, "2026-10-05T12:00:00Z", dish(dish, 2), dish);
        review(restaurant, 5, "2026-10-05T13:00:00Z", "", dish);

        // (5 + 5 + 2) / 3 = 4.0
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + dish))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dish))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(1))
                .andExpect(jsonPath("$.distribution['5']").value(2));
    }

    @Test
    void publicEndpointsDoNotTrustOrNeedIdentityHeaders() throws Exception {
        long restaurant = nextId();
        review(restaurant, 4, "2026-10-05T10:00:00Z", "");

        // No headers at all, and headers that would fail the role checks of the other APIs
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant)).andExpect(status().isOk());
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant)
                        .with(TestTokens.as("abc", "NOT_A_ROLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants?ids=" + restaurant).header("X-User-Role", "DELIVERY_PARTNER"))
                .andExpect(status().isOk());
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant + "/reviews")).andExpect(status().isOk());
        // The reviews API itself still wants a customer
        mockMvc.perform(publicGet("/api/v1/reviews/orders/1")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ the public review list

    @Test
    void theReviewListShowsOnlyAShortNameAndNothingPrivate() throws Exception {
        long restaurant = nextId();
        long dishA = nextId();
        long dishB = nextId();
        long priya = nextId();
        Posted posted = reviewBy(priya, "Priya Sharma", restaurant, 5, "2026-10-05T10:00:00Z",
                dish(dishB, 2) + "," + dish(dishA, 5), dishA, dishB);
        reviewBy(nextId(), "Madonna", restaurant, 4, "2026-10-05T11:00:00Z", "");
        reviewBy(nextId(), "  mary   jane  watson ", restaurant, 3, "2026-10-05T12:00:00Z", "");

        String body = mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].reviewerName").value("mary W."))
                .andExpect(jsonPath("$.content[1].reviewerName").value("Madonna"))
                .andExpect(jsonPath("$.content[2].id").value(posted.reviewId()))
                .andExpect(jsonPath("$.content[2].reviewerName").value("Priya S."))
                .andExpect(jsonPath("$.content[2].rating").value(5))
                .andExpect(jsonPath("$.content[2].comment").value("Review of " + posted.orderId()))
                .andExpect(jsonPath("$.content[2].tags", hasSize(0)))
                .andExpect(jsonPath("$.content[2].dishes", hasSize(2)))
                .andExpect(jsonPath("$.content[2].dishes[0].menuItemId").value(dishA))
                .andExpect(jsonPath("$.content[2].dishes[0].dishName").value("Dish " + dishA))
                .andExpect(jsonPath("$.content[2].dishes[0].rating").value(5))
                .andExpect(jsonPath("$.content[2].dishes[1].rating").value(2))
                .andExpect(jsonPath("$.content[2].createdAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.content[2].updatedAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.content[2].customerId").doesNotExist())
                .andExpect(jsonPath("$.content[2].customerName").doesNotExist())
                .andExpect(jsonPath("$.content[2].orderId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();

        // Not even as a substring anywhere: the full name, the customer's id (as a field) or the order id (as a field)
        assertThat(body).doesNotContain("Sharma", "Watson", "customerId", "orderId", "customerName");
    }

    @Test
    void theReviewListSortsAndPages() throws Exception {
        long restaurant = nextId();
        long r1 = review(restaurant, 3, "2026-10-05T10:00:00Z", "").reviewId();
        long r2 = review(restaurant, 5, "2026-10-05T11:00:00Z", "").reviewId();
        long r3 = review(restaurant, 1, "2026-10-05T12:00:00Z", "").reviewId();
        long r4 = review(restaurant, 5, "2026-10-05T13:00:00Z", "").reviewId();
        // Another restaurant's review never shows
        review(nextId(), 5, "2026-10-05T14:00:00Z", "");
        String path = "/api/v1/ratings/restaurants/" + restaurant + "/reviews";

        // Newest is the default
        mockMvc.perform(publicGet(path))
                .andExpect(jsonPath("$.content[*].id", contains((int) r4, (int) r3, (int) r2, (int) r1)))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.page").value(0));
        // Highest first, ties newest first
        mockMvc.perform(publicGet(path + "?sort=highest"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r4, (int) r2, (int) r1, (int) r3)));
        // Lowest first, ties newest first
        mockMvc.perform(publicGet(path + "?sort=lowest"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3, (int) r1, (int) r4, (int) r2)));

        mockMvc.perform(publicGet(path + "?sort=highest&size=3&page=1"))
                .andExpect(jsonPath("$.content[*].id", contains((int) r3)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + nextId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------------ the public review list of a dish

    @Test
    void aDishListsTheReviewsThatRatedItNewestFirstWithNothingPrivate() throws Exception {
        long restaurant = nextId();
        long biryani = nextId();
        long raita = nextId();
        long older = reviewBy(nextId(), "Priya Sharma", restaurant, 5, "2026-10-05T10:00:00Z",
                dish(biryani, 5) + "," + dish(raita, 3), biryani, raita).reviewId();
        long newer = reviewBy(nextId(), "Arjun Mehta", restaurant, 2, "2026-10-06T10:00:00Z",
                dish(biryani, 2), biryani).reviewId();
        // The dish was in the order but not rated, and another dish's review: neither is listed
        review(restaurant, 4, "2026-10-07T10:00:00Z", "", biryani);
        review(restaurant, 4, "2026-10-08T10:00:00Z", dish(raita, 4), raita);
        String path = "/api/v1/ratings/menu-items/" + biryani + "/reviews";

        String body = mockMvc.perform(publicGet(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains((int) newer, (int) older)))
                .andExpect(jsonPath("$.content[0].reviewerName").value("Arjun M."))
                .andExpect(jsonPath("$.content[0].rating").value(2))
                .andExpect(jsonPath("$.content[0].dishes[0].menuItemId").value(biryani))
                .andExpect(jsonPath("$.content[0].dishes[0].rating").value(2))
                .andExpect(jsonPath("$.content[1].reviewerName").value("Priya S."))
                .andExpect(jsonPath("$.content[1].comment").exists())
                .andExpect(jsonPath("$.content[1].dishes", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Sharma", "Mehta", "customerId", "orderId", "customerName");

        mockMvc.perform(publicGet(path + "?size=1&page=1"))
                .andExpect(jsonPath("$.content[*].id", contains((int) older)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + nextId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void theDishReviewListChecksItsIdAndPageSize() throws Exception {
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/0/reviews")).andExpect(status().isBadRequest());
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + nextId() + "/reviews?size=51"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + nextId() + "/reviews?page=-1"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ the internal feed for search-service

    @Test
    void theRestaurantFeedIsKeysetPagedInIdOrderForOtherServices() throws Exception {
        long first = nextId();
        long second = nextId();
        long withoutReviews = nextId();
        long third = nextId();
        review(third, 4, "2026-10-05T10:00:00Z", "");
        review(first, 5, "2026-10-05T11:00:00Z", "");
        review(first, 4, "2026-10-05T12:00:00Z", "");
        review(second, 2, "2026-10-05T13:00:00Z", "");

        // The first page is full, so it says where the next one starts
        mockMvc.perform(internalGet("/api/v1/internal/ratings/restaurants?afterId=" + (first - 1) + "&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].average").value(4.5))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].average").value(2.0))
                .andExpect(jsonPath("$.items[1].count").value(1))
                .andExpect(jsonPath("$.nextAfterId").value(second));
        // The restaurant without reviews is skipped
        mockMvc.perform(internalGet("/api/v1/internal/ratings/restaurants?afterId=" + second + "&size=1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.items[0].average").value(4.0))
                .andExpect(jsonPath("$.nextAfterId").value(third));
        assertThat(withoutReviews).isBetween(second, third);

        // Past the last id: nothing and no next page. A page that is not full is the last one, too.
        mockMvc.perform(internalGet("/api/v1/internal/ratings/restaurants?afterId=9000000000"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.nextAfterId").value(nullValue()));
        mockMvc.perform(internalGet("/api/v1/internal/ratings/restaurants?afterId=9000000000&size=5000"))
                .andExpect(status().isOk());
        // Defaults: afterId 0, size 1000
        mockMvc.perform(internalGet("/api/v1/internal/ratings/restaurants")).andExpect(status().isOk());
    }

    @Test
    void theMenuItemFeedCountsDishRatings() throws Exception {
        long restaurant = nextId();
        long first = nextId();
        long second = nextId();
        long unrated = nextId();
        review(restaurant, 1, "2026-10-05T10:00:00Z", dish(second, 4) + "," + dish(first, 5), first, second, unrated);
        review(restaurant, 1, "2026-10-05T11:00:00Z", dish(first, 4), first);

        mockMvc.perform(internalGet("/api/v1/internal/ratings/menu-items?afterId=" + (first - 1) + "&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[0].average").value(4.5))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].average").value(4.0))
                .andExpect(jsonPath("$.items[1].count").value(1))
                .andExpect(jsonPath("$.nextAfterId").value(second));
        mockMvc.perform(internalGet("/api/v1/internal/ratings/menu-items?afterId=9000000000"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.nextAfterId").value(nullValue()));
    }

    @Test
    void theFeedSizeAndStartAreChecked() throws Exception {
        for (String endpoint : List.of("/api/v1/internal/ratings/restaurants", "/api/v1/internal/ratings/menu-items")) {
            for (String query : List.of("?size=0", "?size=5001", "?afterId=-1", "?size=abc", "?afterId=abc")) {
                mockMvc.perform(internalGet(endpoint + query)).andExpect(status().isBadRequest());
            }
        }
    }

    private static String ids(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }
}
