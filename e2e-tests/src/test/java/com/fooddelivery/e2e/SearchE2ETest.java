package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;

/**
 * Search follows the other services through their events (restaurant-service, catalog-service -> Kafka ->
 * search-service -> Elasticsearch): customers find only what they can order right now.
 */
class SearchE2ETest extends E2ETestBase {

    @Test
    void aRestaurantAndItsDishesAreFoundOnlyWhileTheyCanBeOrdered() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        Actor owner = Platform.registerOwner(unique);
        String name = "Nawab Kitchen " + unique;
        long restaurantId = Platform.submitRestaurant(owner, name, city);

        // Waiting for approval: not on offer
        assertThat(search("/api/v1/search/restaurants?city=" + city).number("/totalHits")).isZero();

        Platform.approveAndActivate(restaurantId, "e2e-search-" + unique);
        await().untilAsserted(() -> assertThat(ids(search("/api/v1/search/restaurants?city=" + city + "&q=nawab")))
                .containsExactly(restaurantId));
        // Found with a typo in its cuisine, and by cuisine
        assertThat(ids(search("/api/v1/search/restaurants?city=" + city + "&q=biryni"))).containsExactly(restaurantId);
        assertThat(search("/api/v1/search/restaurants?city=" + city + "&cuisine=mughlai").number("/totalHits"))
                .isEqualTo(1);

        await().until(() -> Api.anonymous().get("/api/v1/restaurants/" + restaurantId + "/menu").status() == 200);
        long chicken = Platform.addDish(owner, restaurantId, "Hyderabadi Chicken Biryani", "299.00", "NON_VEG");
        Platform.addDish(owner, restaurantId, "Paneer Biryani", "249.00", "VEG");
        Platform.addDish(owner, restaurantId, "Veg Pulao", "189.00", "VEG");
        await().untilAsserted(() -> assertThat(search("/api/v1/search/foods?city=" + city).number("/totalHits"))
                .isEqualTo(3));

        // A typo still finds the dish, with its restaurant and category; facets count the food types
        Response hyderabadi = search("/api/v1/search/foods?city=" + city + "&q=hydrabadi");
        assertThat(hyderabadi.number("/results/0/id")).isEqualTo(chicken);
        assertThat(hyderabadi.text("/results/0/restaurantName")).isEqualTo(name);
        assertThat(hyderabadi.text("/results/0/categoryName")).isEqualTo("Biryani");
        Response all = search("/api/v1/search/foods?city=" + city);
        assertThat(facetCount(all, "VEG")).isEqualTo(2);
        assertThat(facetCount(all, "NON_VEG")).isEqualTo(1);
        assertThat(names(search("/api/v1/search/foods?city=" + city + "&foodType=VEG&maxPrice=250&sort=PRICE_LOW_TO_HIGH")))
                .containsExactly("Veg Pulao", "Paneer Biryani");

        // Suspended by an admin: the restaurant and its dishes disappear from search
        Api.as(Platform.admin()).put("/api/v1/admin/restaurants/" + restaurantId + "/suspend",
                Map.of("reason", "Hygiene inspection")).expect(200);
        await().untilAsserted(() -> {
            assertThat(search("/api/v1/search/foods?city=" + city).number("/totalHits")).isZero();
            assertThat(search("/api/v1/search/restaurants?city=" + city).number("/totalHits")).isZero();
        });
    }

    private static Response search(String path) {
        return Api.anonymous().get(path).expect(200);
    }

    private static List<Long> ids(Response results) {
        return results.json("/results").valueStream().map(hit -> hit.path("id").asLong()).toList();
    }

    private static List<String> names(Response results) {
        return results.json("/results").valueStream().map(hit -> hit.path("name").asString()).toList();
    }

    private static long facetCount(Response results, String foodType) {
        return results.json("/facets/foodTypes").valueStream()
                .filter(facet -> foodType.equals(facet.path("value").asString()))
                .mapToLong(facet -> facet.path("count").asLong())
                .sum();
    }
}
