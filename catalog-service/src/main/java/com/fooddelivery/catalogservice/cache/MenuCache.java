package com.fooddelivery.catalogservice.cache;

import java.util.List;
import java.util.function.Supplier;

import com.fooddelivery.catalogservice.config.CacheProperties;
import com.fooddelivery.catalogservice.dto.response.MenuResponse;
import com.fooddelivery.catalogservice.dto.response.PublicCategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Caches what customers read most: a restaurant's menu and the list of active categories (MySQL stays the
 * source of truth). A menu depends on its restaurant (name, status), its items and the categories (names, order);
 * a change to any of them makes the cached copy invisible at once (see {@link RedisJsonCache}).
 * <p>
 * Keys: catalog:v1:menu:{restaurantId}:{menuGeneration}.{categoryGeneration} and catalog:v1:categories:{categoryGeneration}.
 * The "v1" changes when the shape of the cached JSON changes, so a new version never reads old entries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuCache {

    static final String CATEGORY_GENERATION = "catalog:generation:categories";
    private static final String KEY_PREFIX = "catalog:v1:";

    private final RedisJsonCache cache;
    private final CacheProperties properties;

    /** The cached menu, or the one {@code loader} builds from MySQL (then cached). */
    public MenuResponse menu(Long restaurantId, Supplier<MenuResponse> loader) {
        return cache.generations(List.of(menuGeneration(restaurantId), CATEGORY_GENERATION))
                .map(generations -> {
                    String key = KEY_PREFIX + "menu:" + restaurantId + ":" + generations.get(0) + "." + generations.get(1);
                    return cache.get(key, MenuResponse.class).orElseGet(() -> {
                        MenuResponse menu = loader.get();
                        cache.put(key, menu, properties.ttl());
                        log.debug("Menu cached. restaurantId={} key={}", restaurantId, key);
                        return menu;
                    });
                })
                .orElseGet(loader);
    }

    public List<PublicCategoryResponse> activeCategories(Supplier<List<PublicCategoryResponse>> loader) {
        return cache.generations(List.of(CATEGORY_GENERATION))
                .map(generations -> {
                    String key = KEY_PREFIX + "categories:" + generations.getFirst();
                    return cache.get(key, ActiveCategories.class).map(ActiveCategories::categories).orElseGet(() -> {
                        List<PublicCategoryResponse> categories = loader.get();
                        cache.put(key, new ActiveCategories(categories), properties.ttl());
                        return categories;
                    });
                })
                .orElseGet(loader);
    }

    /** One restaurant's menu changed (items, or the restaurant itself): applied when the transaction commits. */
    public void menuChanged(Long restaurantId) {
        cache.bumpAfterCommit(menuGeneration(restaurantId));
    }

    /** Category names, order or status changed: every menu shows them, so every menu is refreshed. */
    public void categoriesChanged() {
        cache.bumpAfterCommit(CATEGORY_GENERATION);
    }

    private static String menuGeneration(Long restaurantId) {
        return "catalog:generation:menu:" + restaurantId;
    }

    /** JSON needs a named type for a list: the cached form of the category list. */
    record ActiveCategories(List<PublicCategoryResponse> categories) {
    }
}
