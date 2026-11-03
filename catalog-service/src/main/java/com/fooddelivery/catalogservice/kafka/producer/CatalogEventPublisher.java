package com.fooddelivery.catalogservice.kafka.producer;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.web.RequestIdFilter;
import com.fooddelivery.events.Topics;
import com.fooddelivery.events.catalog.CategoryChanged;
import com.fooddelivery.events.catalog.MenuItemChanged;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds MenuItemChanged and CategoryChanged events carrying the current state, and writes them to the
 * transactional outbox in the caller's transaction; {@link OutboxRelay} publishes them to Kafka.
 * <p>
 * Call it after the change was flushed, so the event carries the new version: consumers (search-service)
 * keep their own copy and use the version to ignore events older than that copy.
 * <p>
 * Every menu change passes through here, so this is also where the menu cache learns about it (MenuCache;
 * applied when the transaction commits).
 */
@Component
@RequiredArgsConstructor
public class CatalogEventPublisher {

    /** What happened to a menu item (the event's changeType). */
    public enum MenuItemChange {
        CREATED, UPDATED, PRICE_CHANGED, AVAILABILITY_CHANGED, STATUS_CHANGED, CATEGORY_CHANGED, REMOVED, REPUBLISHED
    }

    /** What happened to a category (the event's changeType). */
    public enum CategoryChange {
        CREATED, UPDATED, STATUS_CHANGED, REORDERED, DELETED, REPUBLISHED
    }

    private static final String AGGREGATE_MENU_ITEM = "MENU_ITEM";
    private static final String AGGREGATE_CATEGORY = "CATEGORY";

    /** Scale of the Avro decimal field (see MenuItemChanged.avsc). */
    private static final int MONEY_SCALE = 2;

    private final OutboxWriter outboxWriter;
    private final MenuCache menuCache;
    private final Clock clock;

    public void menuItemChanged(MenuItem item, MenuItemChange change) {
        MenuItemChanged event = MenuItemChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setChangeType(change.name())
                .setMenuItemId(item.getId())
                .setVersion(item.getVersion())
                .setRestaurantId(item.getRestaurantId())
                .setCategoryId(item.getCategoryId())
                .setName(item.getName())
                .setDescription(item.getDescription())
                .setPrice(item.getPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .setFoodType(item.getFoodType().name())
                .setImageUrl(item.getImageUrl())
                .setAvailable(item.isAvailable())
                .setStatus(item.getStatus().name())
                .build();
        outboxWriter.append(AGGREGATE_MENU_ITEM, item.getId(), Topics.MENU_ITEM_CHANGED, String.valueOf(item.getId()),
                event);
        menuCache.menuChanged(item.getRestaurantId());
    }

    public void categoryChanged(Category category, CategoryChange change) {
        CategoryChanged event = CategoryChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setChangeType(change.name())
                .setCategoryId(category.getId())
                .setVersion(category.getVersion())
                .setName(category.getName())
                .setDescription(category.getDescription())
                .setImageUrl(category.getImageUrl())
                .setDisplayOrder(category.getDisplayOrder())
                .setStatus(category.getStatus().name())
                .build();
        outboxWriter.append(AGGREGATE_CATEGORY, category.getId(), Topics.CATEGORY_CHANGED,
                String.valueOf(category.getId()), event);
        menuCache.categoriesChanged();
    }
}
