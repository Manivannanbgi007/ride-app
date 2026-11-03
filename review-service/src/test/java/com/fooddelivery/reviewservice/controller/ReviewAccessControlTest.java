package com.fooddelivery.reviewservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fooddelivery.reviewservice.TestTokens;
import com.fooddelivery.reviewservice.config.SecurityConfig;
import com.fooddelivery.reviewservice.dto.request.UpdateReviewRequest;
import com.fooddelivery.reviewservice.service.CustomerLikedDishesService;
import com.fooddelivery.reviewservice.service.FeaturedDishService;
import com.fooddelivery.reviewservice.service.LikedDishPeriod;
import com.fooddelivery.reviewservice.service.RatingService;
import com.fooddelivery.reviewservice.service.ReviewAdminService;
import com.fooddelivery.reviewservice.service.ReviewService;
import com.fooddelivery.reviewservice.web.SecurityProblemHandlers;
import com.fooddelivery.reviewservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation. */
@WebMvcTest({ReviewController.class, AdminReviewController.class, RatingController.class, InternalRatingController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class ReviewAccessControlTest {

    private static final String VALID_EDIT = """
            {"rating": 3, "comment": "Cooled down", "tags": ["GOOD_VALUE"], "version": 0}
            """;

    private static final String VALID_REVIEW = """
            {"orderId": 5, "rating": 4, "tags": ["DELICIOUS"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private ReviewAdminService reviewAdminService;
    @MockitoBean
    private FeaturedDishService featuredDishService;
    @MockitoBean
    private CustomerLikedDishesService customerLikedDishesService;

    @MockitoBean
    private RatingService ratingService;

    @Test
    void onlyCustomersEditAndDeleteTheirReviews() throws Exception {
        for (String role : new String[] {"PLATFORM_ADMIN", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(put("/api/v1/reviews/9").with(TestTokens.as("7", role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_EDIT))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/reviews/9").with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/v1/reviews/9").contentType(MediaType.APPLICATION_JSON).content(VALID_EDIT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/reviews/9")).andExpect(status().isUnauthorized());
        // A customer without an id is not identified
        mockMvc.perform(delete("/api/v1/reviews/9").header("X-User-Role", "CUSTOMER")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void aCustomerEditsAndDeletesTheirReview() throws Exception {
        mockMvc.perform(put("/api/v1/reviews/9").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EDIT))
                .andExpect(status().isOk());
        verify(reviewService).update(eq(9L), any(UpdateReviewRequest.class), argThat(caller -> caller.userId() == 7L));

        mockMvc.perform(delete("/api/v1/reviews/9").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isNoContent());
        verify(reviewService).delete(eq(9L), argThat(caller -> caller.userId() == 7L));
    }

    @Test
    void anEditNeedsValidFields() throws Exception {
        mockMvc.perform(put("/api/v1/reviews/9").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 0, "comment": "%s", "dishRatings": [{"menuItemId": 1, "rating": 6}]}
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists())
                .andExpect(jsonPath("$.errors.comment").exists())
                .andExpect(jsonPath("$.errors['dishRatings[0].rating']").exists());
        mockMvc.perform(put("/api/v1/reviews/9").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
        mockMvc.perform(put("/api/v1/reviews/abc").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_EDIT))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewService);
    }

    @Test
    void ratingsArePublicAndReadNoIdentity() throws Exception {
        for (String path : new String[] {"/api/v1/ratings/restaurants?ids=1,2", "/api/v1/ratings/menu-items?ids=1",
                "/api/v1/ratings/restaurants/5", "/api/v1/ratings/menu-items/5", "/api/v1/ratings/restaurants/5/reviews"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
            // A wrong identity header changes nothing: it is not read
            mockMvc.perform(get(path).with(TestTokens.as("abc", "NOT_A_ROLE")))
                    .andExpect(status().isOk());
        }
        verify(ratingService, times(2)).restaurantRatings(Set.of(1L, 2L));
        verify(ratingService, times(2)).menuItemRatings(Set.of(1L));
        verify(ratingService, times(2)).restaurantRating(5L);
        verify(ratingService, times(2)).menuItemRating(5L);
        verify(ratingService, times(2)).restaurantReviews(5L, 0, 10, "newest");
    }

    @Test
    void ratingRequestsAreValidatedBeforeTheServiceIsCalled() throws Exception {
        String tooManyIds = IntStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        for (String path : new String[] {"/api/v1/ratings/restaurants?ids=", "/api/v1/ratings/restaurants",
                "/api/v1/ratings/menu-items?ids=1,a", "/api/v1/ratings/menu-items?ids=-1",
                "/api/v1/ratings/restaurants?ids=" + tooManyIds,
                "/api/v1/ratings/restaurants/0",
                "/api/v1/ratings/restaurants/5/reviews?size=51", "/api/v1/ratings/restaurants/5/reviews?size=0",
                "/api/v1/ratings/restaurants/5/reviews?page=-1"}) {
            mockMvc.perform(get(path)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(ratingService);
    }

    @Test
    void theInternalFeedNeedsTheServicesTokenAndLimitsTheSize() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ratings/restaurants").with(TestTokens.serviceToken())).andExpect(status().isOk());
        verify(ratingService).restaurantFeed(0L, 1000);
        mockMvc.perform(get("/api/v1/internal/ratings/menu-items?afterId=7&size=5000")
                        .with(TestTokens.serviceToken()))
                .andExpect(status().isOk());
        verify(ratingService).menuItemFeed(7L, 5000);

        for (String query : new String[] {"?size=0", "?size=5001", "?afterId=-1"}) {
            mockMvc.perform(get("/api/v1/internal/ratings/restaurants" + query).with(TestTokens.serviceToken()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyCustomersWriteReviews() throws Exception {
        for (String role : new String[] {"PLATFORM_ADMIN", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(post("/api/v1/reviews").with(TestTokens.as("7", role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_REVIEW))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_REVIEW))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void onlyCustomersReadTheirReviewOfAnOrder() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/orders/5").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reviews/orders/5")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void aCustomerWithoutAnIdIsNotIdentified() throws Exception {
        mockMvc.perform(post("/api/v1/reviews").header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REVIEW))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reviewService);
    }

    @Test
    void onlyPlatformAdminsReadAllReviews() throws Exception {
        for (String path : new String[] {"/api/v1/admin/reviews", "/api/v1/admin/reviews/summary",
                "/api/v1/admin/reviews/featured-dishes"}) {
            mockMvc.perform(get(path).with(TestTokens.as("7", "CUSTOMER")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(reviewAdminService, featuredDishService);
    }

    @Test
    void platformAdminsGetThroughToTheAdminApi() throws Exception {
        for (String path : new String[] {"/api/v1/admin/reviews", "/api/v1/admin/reviews/summary",
                "/api/v1/admin/reviews/featured-dishes"}) {
            mockMvc.perform(get(path).with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void onlyPlatformAdminsReadTheLikedDishesOfACustomer() throws Exception {
        String path = "/api/v1/admin/reviews/customers/7/liked-dishes";
        for (String role : new String[] {"CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            mockMvc.perform(get(path).with(TestTokens.as("7", role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(customerLikedDishesService);

        mockMvc.perform(get(path).with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        verify(customerLikedDishesService).likedDishes(7L, LikedDishPeriod.ALL, 7);

        mockMvc.perform(get(path + "?period=WEEKLY&limit=20").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk());
        verify(customerLikedDishesService).likedDishes(7L, LikedDishPeriod.WEEKLY, 20);

        for (String query : new String[] {"?limit=0", "?limit=21", "?period=YEARLY"}) {
            mockMvc.perform(get(path + query).with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aReviewNeedsValidFields() throws Exception {
        mockMvc.perform(post("/api/v1/reviews").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": 6, "comment": "%s", "dishRatings": [{"menuItemId": 1, "rating": 0}]}
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orderId").exists())
                .andExpect(jsonPath("$.errors.rating").exists())
                .andExpect(jsonPath("$.errors.comment").exists())
                .andExpect(jsonPath("$.errors['dishRatings[0].rating']").exists());
        verifyNoInteractions(reviewService);
    }

    @Test
    void anUnknownTagIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/reviews").with(TestTokens.as("7", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": 5, "rating": 4, "tags": ["TASTY_LIKE_HOME"]}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewService);
    }

    @Test
    void featuredDishPagesAreLimited() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews/featured-dishes?size=13")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(featuredDishService);
    }

    @Test
    void theDishFilterIsForPlatformAdminsAndMustBeANumber() throws Exception {
        for (String path : new String[] {"/api/v1/admin/reviews?menuItemId=5", "/api/v1/admin/reviews/summary?menuItemId=5"}) {
            mockMvc.perform(get(path).with(TestTokens.as("1", "RESTAURANT_ADMIN")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path.replace("5", "abc")).with(TestTokens.as("1", "PLATFORM_ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reviewAdminService);
    }

    @Test
    void ratingFilterMustBeAStarCount() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews?rating=9")
                        .with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reviewAdminService);
    }
}
