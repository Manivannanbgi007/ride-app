package com.fooddelivery.searchservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.searchservice.TestTokens;
import com.fooddelivery.searchservice.config.SecurityConfig;
import com.fooddelivery.searchservice.dto.request.FoodSearchCriteria;
import com.fooddelivery.searchservice.dto.request.FoodSearchCriteria.FoodSort;
import com.fooddelivery.searchservice.dto.request.RestaurantSearchCriteria;
import com.fooddelivery.searchservice.dto.request.RestaurantSearchCriteria.RestaurantSort;
import com.fooddelivery.searchservice.dto.response.RatingSyncResult;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import com.fooddelivery.searchservice.service.CategorySearchService;
import com.fooddelivery.searchservice.service.ConsistencyService;
import com.fooddelivery.searchservice.service.FoodSearchService;
import com.fooddelivery.searchservice.service.IndexOverviewService;
import com.fooddelivery.searchservice.service.RatingSyncService;
import com.fooddelivery.searchservice.service.RestaurantSearchService;
import com.fooddelivery.searchservice.service.SuggestionService;
import com.fooddelivery.searchservice.web.SecurityProblemHandlers;
import com.fooddelivery.searchservice.web.ServiceTokenAuthorizationManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: who may call what, and validation of search parameters. */
@WebMvcTest({SearchController.class, AdminSearchController.class})
@Import({SecurityConfig.class, SecurityProblemHandlers.class, ServiceTokenAuthorizationManager.class})
// The internal APIs' shared token of the tests (a web-layer test does not read application-test.yml)
@TestPropertySource(properties = "app.security.internal-service-token=" + TestTokens.SERVICE_TOKEN)
class SearchAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantSearchService restaurantSearchService;
    @MockitoBean
    private FoodSearchService foodSearchService;
    @MockitoBean
    private CategorySearchService categorySearchService;
    @MockitoBean
    private SuggestionService suggestionService;
    @MockitoBean
    private IndexOverviewService indexOverviewService;
    @MockitoBean
    private ConsistencyService consistencyService;
    @MockitoBean
    private RatingSyncService ratingSyncService;

    @Test
    void searchNeedsNoIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/search/restaurants").param("q", "biryani")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/search/categories")).andExpect(status().isOk());
    }

    @Test
    void theIndexOverviewIsForPlatformAdminsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/search/indexes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/search/indexes").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(indexOverviewService);
    }

    @Test
    void badSearchParametersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/foods").param("size", "500")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/foods").param("foodType", "VEGAN")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/restaurants").param("lat", "123")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/suggest")).andExpect(status().isBadRequest());
        verifyNoInteractions(foodSearchService, restaurantSearchService, suggestionService);
    }

    @Test
    void minRatingMustBeBetweenOneAndFive() throws Exception {
        for (String path : new String[]{"/api/v1/search/restaurants", "/api/v1/search/foods"}) {
            mockMvc.perform(get(path).param("minRating", "0.5")).andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("minRating", "5.1")).andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("minRating", "four")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(foodSearchService, restaurantSearchService);
    }

    @Test
    void minRatingAndSortByRatingReachTheSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search/restaurants").param("minRating", "4.5").param("sort", "rating"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/search/foods").param("minRating", "4").param("sort", "RATING"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/search/foods").param("minRating", "1").param("sort", "PRICE_LOW_TO_HIGH"))
                .andExpect(status().isOk());

        ArgumentCaptor<RestaurantSearchCriteria> restaurants = ArgumentCaptor.forClass(RestaurantSearchCriteria.class);
        verify(restaurantSearchService).search(restaurants.capture(), eq(0), eq(20));
        assertThat(restaurants.getValue().minRating()).isEqualTo(4.5);
        assertThat(restaurants.getValue().sort()).isEqualTo(RestaurantSort.RATING);
        ArgumentCaptor<FoodSearchCriteria> foods = ArgumentCaptor.forClass(FoodSearchCriteria.class);
        verify(foodSearchService, times(2)).search(foods.capture(), eq(0), eq(20));
        assertThat(foods.getAllValues()).extracting(FoodSearchCriteria::sort)
                .containsExactly(FoodSort.RATING, FoodSort.PRICE_LOW_TO_HIGH);
    }

    @Test
    void unknownSortValuesAreStillRejectedAndOldOnesKeepWorking() throws Exception {
        mockMvc.perform(get("/api/v1/search/foods").param("sort", "STARS")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/foods").param("sort", "price_low_to_high")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/restaurants").param("sort", "STARS")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search/foods").param("sort", "PRICE_HIGH_TO_LOW")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/search/foods").param("sort", "")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/search/restaurants").param("sort", "RELEVANCE")).andExpect(status().isOk());
    }

    @Test
    void theRatingSyncIsForPlatformAdminsOnly() throws Exception {
        when(ratingSyncService.sync()).thenReturn(new RatingSyncResult(3, 4, 1, 2));

        mockMvc.perform(post("/api/v1/admin/search/ratings/sync")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/search/ratings/sync").with(TestTokens.as("7", "CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ratingSyncService);

        mockMvc.perform(post("/api/v1/admin/search/ratings/sync").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantsUpdated").value(3))
                .andExpect(jsonPath("$.foodsUpdated").value(4))
                .andExpect(jsonPath("$.restaurantsReset").value(1))
                .andExpect(jsonPath("$.foodsReset").value(2));
    }

    @Test
    void theRatingSyncAnswers503WhenReviewServiceIsDown() throws Exception {
        when(ratingSyncService.sync()).thenThrow(new SourceUnavailableException("review-service",
                new IllegalStateException("connection refused")));

        mockMvc.perform(post("/api/v1/admin/search/ratings/sync").with(TestTokens.as("1", "PLATFORM_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Source unavailable"));
    }
}
