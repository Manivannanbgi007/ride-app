package com.fooddelivery.reviewservice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import com.fooddelivery.reviewservice.exception.DependencyUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Editing and deleting one's own review, and what that does to the public numbers. */
class ReviewEditIntegrationTest extends AbstractReviewApiTest {

    @Test
    void editingMyReviewChangesItAndTheAggregates() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long b = nextId();
        long c = nextId();
        long customer = nextId();
        // C1 rates the restaurant 5 and the dishes a (5) and b (3); a second customer rates the restaurant 3
        Posted mine = reviewBy(customer, "Customer " + customer, restaurant, 5, "2026-10-05T10:00:00Z",
                dish(a, 5) + "," + dish(b, 3), a, b, c);
        review(restaurant, 3, "2026-10-05T11:00:00Z", "");

        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a))
                .andExpect(jsonPath("$.average").value(5.0))
                .andExpect(jsonPath("$.count").value(1));

        // The edit: new stars, comment and tags; b gets another rating, a is dropped, c is rated for the first time
        at("2026-10-06T08:00:00Z");
        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 1, "comment": "  Changed my mind  ", "tags": ["GOOD_VALUE"],
                         "dishRatings": [%s, %s], "version": 0}
                        """.formatted(dish(b, 4), dish(c, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.reviewId()))
                .andExpect(jsonPath("$.orderId").value(mine.orderId()))
                .andExpect(jsonPath("$.customerId").value(customer))
                .andExpect(jsonPath("$.customerName").value("Customer " + customer))
                .andExpect(jsonPath("$.restaurantId").value(restaurant))
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.comment").value("Changed my mind"))
                .andExpect(jsonPath("$.tags", contains("GOOD_VALUE")))
                .andExpect(jsonPath("$.dishRatings", hasSize(2)))
                .andExpect(jsonPath("$.dishRatings[0].menuItemId").value(b))
                .andExpect(jsonPath("$.dishRatings[0].dishName").value("Dish " + b))
                .andExpect(jsonPath("$.dishRatings[0].rating").value(4))
                .andExpect(jsonPath("$.dishRatings[1].menuItemId").value(c))
                .andExpect(jsonPath("$.dishRatings[1].rating").value(2))
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-06T08:00:00Z"))
                .andExpect(jsonPath("$.version").value(1));

        // What I read back is the edited review
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.dishRatings", hasSize(2)));

        // The aggregates follow at once: restaurant (1 + 3) / 2, the dropped dish has no rating left
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(2.0))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.distribution['1']").value(1))
                .andExpect(jsonPath("$.distribution['3']").value(1))
                .andExpect(jsonPath("$.distribution['5']").value(0));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a))
                .andExpect(jsonPath("$.average").value(nullValue()))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items?ids=" + a + "," + b + "," + c))
                .andExpect(jsonPath("$[*].id", contains((int) b, (int) c)))
                .andExpect(jsonPath("$[0].average").value(4.0))
                .andExpect(jsonPath("$[1].average").value(2.0));
        // The public list shows the new text and the time of the edit
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant + "/reviews?sort=lowest"))
                .andExpect(jsonPath("$.content[0].comment").value("Changed my mind"))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-05T10:00:00Z"))
                .andExpect(jsonPath("$.content[0].updatedAt").value("2026-10-06T08:00:00Z"));
    }

    @Test
    void anEditWithoutDishesRemovesAllDishRatingsAndTheCommentCanBeCleared() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long customer = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, restaurant, 4, "2026-10-05T10:00:00Z", dish(a, 4), a);

        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 4, "comment": "   "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value(nullValue()))
                .andExpect(jsonPath("$.tags", hasSize(0)))
                .andExpect(jsonPath("$.dishRatings", hasSize(0)));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a)).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void someoneElsesReviewCannotBeEditedOrDeletedAndNothingChanges() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long owner = nextId();
        long other = nextId();
        Posted mine = reviewBy(owner, "Customer " + owner, restaurant, 5, "2026-10-05T10:00:00Z", dish(a, 5), a);
        String body = """
                {"rating": 1, "comment": "vandal", "tags": [], "dishRatings": [%s]}
                """.formatted(dish(a, 1));

        mockMvc.perform(customerPut(other, mine.reviewId(), body)).andExpect(status().isNotFound());
        mockMvc.perform(customerDelete(other, mine.reviewId())).andExpect(status().isNotFound());
        // The same answer as for a review that does not exist
        mockMvc.perform(customerPut(owner, nextId(), body)).andExpect(status().isNotFound());
        mockMvc.perform(customerDelete(owner, nextId())).andExpect(status().isNotFound());

        mockMvc.perform(customerGet(owner, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Review of " + mine.orderId()))
                .andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(5.0))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a)).andExpect(jsonPath("$.average").value(5.0));
    }

    @Test
    void aPlatformAdminCannotUseTheCustomerEndpoints() throws Exception {
        long customer = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, nextId(), 5, "2026-10-05T10:00:00Z", "");

        // Even the owner's id does not help with another role
        mockMvc.perform(put("/api/v1/reviews/" + mine.reviewId())
                        .with(TestTokens.as(customer, "PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/reviews/" + mine.reviewId())
                        .with(TestTokens.as(customer, "PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/reviews/" + mine.reviewId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/reviews/" + mine.reviewId())).andExpect(status().isUnauthorized());
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void anEditFollowsTheRulesOfWritingAReview() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long customer = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, restaurant, 5, "2026-10-05T10:00:00Z", dish(a, 5), a);
        long foreignDish = nextId();

        // A dish that is not on the original order
        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 2, "dishRatings": [%s]}
                        """.formatted(dish(foreignDish, 3))))
                .andExpect(status().isUnprocessableContent());
        // The same dish twice, too many tags, a tag twice
        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 2, "dishRatings": [%s, %s]}
                        """.formatted(dish(a, 3), dish(a, 4))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 2, "tags": ["DELICIOUS", "EXCELLENT", "GOOD_SERVICE", "FAST_DELIVERY", "GOOD_VALUE"]}
                        """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(customerPut(customer, mine.reviewId(), """
                        {"rating": 2, "tags": ["DELICIOUS", "DELICIOUS"]}
                        """))
                .andExpect(status().isBadRequest());
        // Stars between 1 and 5, a comment of at most 1000 characters
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 6}")).andExpect(status().isBadRequest());
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{}")).andExpect(status().isBadRequest());
        String longComment = "x".repeat(1001);
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 3, \"comment\": \"%s\"}".formatted(longComment)))
                .andExpect(status().isBadRequest());

        // Nothing was changed
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.dishRatings[0].rating").value(5))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void theOrderMustStillBeFoundAndTheOrderServiceReachable() throws Exception {
        long customer = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, nextId(), 5, "2026-10-05T10:00:00Z", "");

        when(orderLookup.findOrder(mine.orderId()))
                .thenThrow(new DependencyUnavailableException("order-service", new RuntimeException("connection refused")));
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 1}"))
                .andExpect(status().isServiceUnavailable());

        doReturn(Optional.empty()).when(orderLookup).findOrder(mine.orderId());
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 1}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void anOutdatedVersionIsAConflictAndACurrentOrMissingOneIsFine() throws Exception {
        long customer = nextId();
        long restaurant = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, restaurant, 5, "2026-10-05T10:00:00Z", "");

        at("2026-10-06T08:00:00Z");
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 4, \"version\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // A second tab still holds version 0
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 1, \"version\": 0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId()))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.version").value(1));

        // Without a version the edit is not guarded; with the current one it passes
        at("2026-10-07T08:00:00Z");
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        at("2026-10-08T08:00:00Z");
        mockMvc.perform(customerPut(customer, mine.reviewId(), "{\"rating\": 2, \"version\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(2.0));
    }

    @Test
    void deletingMyReviewFreesTheOrderForANewOne() throws Exception {
        long restaurant = nextId();
        long a = nextId();
        long customer = nextId();
        Posted mine = reviewBy(customer, "Customer " + customer, restaurant, 2, "2026-10-05T10:00:00Z", dish(a, 2), a);
        review(restaurant, 4, "2026-10-05T11:00:00Z", "");
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant)).andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(customerDelete(customer, mine.reviewId())).andExpect(status().isNoContent());

        // Gone: for me, from the aggregates (the dish rating went with it) and from the public list
        mockMvc.perform(customerGet(customer, "/api/v1/reviews/orders/" + mine.orderId())).andExpect(status().isNotFound());
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.average").value(nullValue()));
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant + "/reviews"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/admin/reviews?restaurantId=" + restaurant)
                        .with(TestTokens.as(1, "PLATFORM_ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1));
        // Deleting it again finds nothing
        mockMvc.perform(customerDelete(customer, mine.reviewId())).andExpect(status().isNotFound());

        // The same delivered order can be reviewed again
        at("2026-10-06T10:00:00Z");
        mockMvc.perform(customerPost(customer, """
                        {"orderId": %d, "rating": 5, "dishRatings": [%s]}
                        """.formatted(mine.orderId(), dish(a, 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));
        mockMvc.perform(publicGet("/api/v1/ratings/restaurants/" + restaurant))
                .andExpect(jsonPath("$.average").value(4.5))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(publicGet("/api/v1/ratings/menu-items/" + a)).andExpect(jsonPath("$.average").value(5.0));
    }
}
