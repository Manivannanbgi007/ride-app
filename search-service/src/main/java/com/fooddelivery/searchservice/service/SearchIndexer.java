package com.fooddelivery.searchservice.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import com.fooddelivery.searchservice.document.CategoryDocument;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

/**
 * Keeps the indexes in line with the events (MySQL in the owning services stays the source of truth).
 * <ul>
 *   <li>Every document stores the version of its source entity; an event older than the stored copy is ignored,
 *       so repeated, replayed or late events never make the index go back in time.</li>
 *   <li>Foods carry a copy of their restaurant and category. When a restaurant or category changes, the copies
 *       in its foods are updated with update-by-query; a food indexed at that same moment re-checks its
 *       restaurant and category after writing, so neither side can miss the other.</li>
 *   <li>Restaurants and foods also carry a rating (average and count) that does not come from these events but from
 *       review-service (see RatingSyncService). Rebuilding a document from an event takes over the stored rating, so an
 *       update of a restaurant or dish never wipes it. (If the sync writes a rating in the few milliseconds between
 *       reading the document and writing it back, the sync repeats that rating on its next run.)</li>
 * </ul>
 * Reads by id ({@code operations.get}) are real-time in Elasticsearch: they see writes made a moment ago.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexer {

    private static final String REMOVED = "REMOVED";
    private static final String DELETED = "DELETED";

    private final ElasticsearchOperations operations;
    // Injected so the indexes exist before the first event is applied
    private final SearchIndexes searchIndexes;
    private final Clock clock;

    public void apply(RestaurantChanged event) {
        long id = event.getRestaurantId();
        RestaurantDocument current = operations.get(String.valueOf(id), RestaurantDocument.class);
        if (current != null && isOutdated(current.getSourceVersion(), event.getVersion(), "restaurant", id)) {
            return;
        }
        RestaurantDocument restaurant = new RestaurantDocument();
        restaurant.setId(id);
        restaurant.setName(event.getName());
        restaurant.setDescription(event.getDescription());
        restaurant.setCuisines(event.getCuisines() == null ? List.of() : List.copyOf(event.getCuisines()));
        restaurant.setCity(event.getCity());
        RestaurantAddress address = event.getAddress();
        if (address != null) {
            restaurant.setAddressLine(address.getLine1() + ", " + address.getCity());
            if (address.getLatitude() != null && address.getLongitude() != null) {
                restaurant.setLocation(new GeoPoint(address.getLatitude(), address.getLongitude()));
            }
        }
        restaurant.setStatus(event.getStatus());
        restaurant.setSourceVersion(event.getVersion());
        restaurant.setIndexedAt(clock.instant());
        // Ratings come from review-service through the rating sync, not from events: keep what is stored
        restaurant.keepRatingOf(current);
        operations.save(restaurant);
        log.info("Restaurant indexed. restaurantId={} version={} status={} change={}",
                id, event.getVersion(), event.getStatus(), event.getChangeType());

        if (current == null || !Objects.equals(current.getName(), restaurant.getName())
                || !Objects.equals(current.getCity(), restaurant.getCity())
                || !Objects.equals(current.getStatus(), restaurant.getStatus())) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("name", restaurant.getName());
            copy.put("city", restaurant.getCity());
            copy.put("status", restaurant.getStatus());
            long updated = updateFoods("restaurantId", id, """
                    ctx._source.restaurantName = params.name;
                    ctx._source.restaurantCity = params.city;
                    ctx._source.restaurantStatus = params.status;
                    """, copy);
            log.info("Restaurant copy refreshed in its foods. restaurantId={} foods={}", id, updated);
        }
    }

    public void apply(CategoryChanged event) {
        long id = event.getCategoryId();
        CategoryDocument current = operations.get(String.valueOf(id), CategoryDocument.class);
        if (current != null && isOutdated(current.getSourceVersion(), event.getVersion(), "category", id)) {
            return;
        }
        if (DELETED.equals(event.getChangeType())) {
            // catalog-service only deletes categories no menu item uses: no food refers to it
            operations.delete(String.valueOf(id), CategoryDocument.class);
            log.info("Category removed from the index. categoryId={}", id);
            return;
        }
        CategoryDocument category = new CategoryDocument();
        category.setId(id);
        category.setName(event.getName());
        category.setDescription(event.getDescription());
        category.setImageUrl(event.getImageUrl());
        category.setDisplayOrder(event.getDisplayOrder());
        category.setStatus(event.getStatus());
        category.setSourceVersion(event.getVersion());
        category.setIndexedAt(clock.instant());
        operations.save(category);
        log.info("Category indexed. categoryId={} version={} change={}", id, event.getVersion(), event.getChangeType());

        if (current == null || !Objects.equals(current.getName(), category.getName())) {
            long updated = updateFoods("categoryId", id, "ctx._source.categoryName = params.name;",
                    Map.of("name", category.getName()));
            log.info("Category name refreshed in its foods. categoryId={} foods={}", id, updated);
        }
    }

    public void apply(MenuItemChanged event) {
        long id = event.getMenuItemId();
        FoodDocument current = operations.get(String.valueOf(id), FoodDocument.class);
        if (current != null && isOutdated(current.getSourceVersion(), event.getVersion(), "food", id)) {
            return;
        }
        if (REMOVED.equals(event.getChangeType())) {
            operations.delete(String.valueOf(id), FoodDocument.class);
            log.info("Food removed from the index. menuItemId={}", id);
            return;
        }
        FoodDocument food = new FoodDocument();
        food.setId(id);
        food.setName(event.getName());
        food.setDescription(event.getDescription());
        food.setPrice(event.getPrice().doubleValue());
        food.setFoodType(event.getFoodType());
        food.setImageUrl(event.getImageUrl());
        food.setAvailable(event.getAvailable());
        food.setStatus(event.getStatus());
        food.setCategoryId(event.getCategoryId());
        food.setRestaurantId(event.getRestaurantId());
        food.setSourceVersion(event.getVersion());
        food.setIndexedAt(clock.instant());
        // Ratings come from review-service through the rating sync, not from events: keep what is stored
        food.keepRatingOf(current);
        food.applyRestaurant(operations.get(String.valueOf(event.getRestaurantId()), RestaurantDocument.class));
        food.applyCategory(operations.get(String.valueOf(event.getCategoryId()), CategoryDocument.class));
        operations.save(food);

        // The restaurant or category may have changed while this food was being written: copy them again if so
        RestaurantDocument restaurant = operations.get(String.valueOf(event.getRestaurantId()), RestaurantDocument.class);
        CategoryDocument category = operations.get(String.valueOf(event.getCategoryId()), CategoryDocument.class);
        FoodDocument recheck = new FoodDocument();
        recheck.applyRestaurant(restaurant);
        recheck.applyCategory(category);
        if (!Objects.equals(recheck.getRestaurantName(), food.getRestaurantName())
                || !Objects.equals(recheck.getRestaurantCity(), food.getRestaurantCity())
                || !Objects.equals(recheck.getRestaurantStatus(), food.getRestaurantStatus())
                || !Objects.equals(recheck.getCategoryName(), food.getCategoryName())) {
            food.applyRestaurant(restaurant);
            food.applyCategory(category);
            // The rating may have been written since the first save: take over what is stored now
            food.keepRatingOf(operations.get(String.valueOf(id), FoodDocument.class));
            operations.save(food);
        }
        log.info("Food indexed. menuItemId={} version={} restaurantId={} change={}",
                id, event.getVersion(), event.getRestaurantId(), event.getChangeType());
    }

    private static boolean isOutdated(Long storedVersion, long eventVersion, String type, long id) {
        // Equal versions are applied again: a replay can then refresh documents written by an older indexer
        if (storedVersion != null && storedVersion > eventVersion) {
            log.info("Outdated event ignored. type={} id={} eventVersion={} indexedVersion={}",
                    type, id, eventVersion, storedVersion);
            return true;
        }
        return false;
    }

    /** Updates every food of a restaurant or category in place; makes recent writes searchable first. */
    private long updateFoods(String field, long value, String script, Map<String, Object> params) {
        operations.indexOps(FoodDocument.class).refresh();
        UpdateQuery update = UpdateQuery.builder(NativeQuery.builder()
                        .withQuery(q -> q.term(t -> t.field(field).value(value)))
                        .build())
                .withLang("painless")
                .withScript(script)
                .withParams(params)
                // A food written at the same moment wins; it re-checks its copies itself (see apply(MenuItemChanged))
                .withAbortOnVersionConflict(false)
                .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .build();
        ByQueryResponse response = operations.updateByQuery(update, IndexCoordinates.of(FoodDocument.INDEX));
        return response.getUpdated();
    }
}
