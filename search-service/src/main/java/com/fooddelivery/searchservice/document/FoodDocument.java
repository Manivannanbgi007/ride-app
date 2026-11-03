package com.fooddelivery.searchservice.document;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

/**
 * A menu item in the "food" index, built from MenuItemChanged events. It also carries a copy of its restaurant
 * (name, city, status) and category name, so one query can search, filter and show everything; those copies
 * are refreshed when the restaurant or category changes (see SearchIndexer).
 */
@Document(indexName = FoodDocument.INDEX, createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/settings.json")
@Mapping(mappingPath = "elasticsearch/food-mapping.json")
@Getter
@Setter
@NoArgsConstructor
public class FoodDocument {

    public static final String INDEX = "food";

    @Id
    private Long id;

    private String name;

    private String description;

    /** Stored as a double (scaled_float in Elasticsearch, two decimals). */
    private Double price;

    private String foodType;

    private String imageUrl;

    /** In stock today. */
    private boolean available;

    /** ACTIVE, DISABLED (by the owner) or BLOCKED (by an admin). */
    private String status;

    private Long categoryId;

    private String categoryName;

    private Long restaurantId;

    /** Null until the restaurant itself is indexed (events of two topics arrive in any order). */
    private String restaurantName;

    private String restaurantCity;

    private String restaurantStatus;

    /** The menu item's version in catalog-service: older events are ignored. */
    private Long sourceVersion;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;

    /** Average of the customers' ratings of this dish (1.0 to 5.0); null while it has none. Owned by review-service. */
    private Double ratingAverage;

    /** How many ratings the average is made of; 0 (or absent in documents indexed before ratings existed) = none. */
    private Integer ratingCount = 0;

    /**
     * Takes over the rating stored in the index. The events know nothing about ratings, so a document rebuilt from
     * an event must not lose them (they are refreshed only by the rating sync).
     */
    public void keepRatingOf(FoodDocument stored) {
        ratingAverage = stored == null ? null : stored.getRatingAverage();
        ratingCount = stored == null ? null : stored.getRatingCount();
        if (ratingCount == null) {
            ratingCount = 0;
        }
    }

    /** Copies what customers see of the restaurant; a missing restaurant leaves the item hidden. */
    public void applyRestaurant(RestaurantDocument restaurant) {
        restaurantName = restaurant == null ? null : restaurant.getName();
        restaurantCity = restaurant == null ? null : restaurant.getCity();
        restaurantStatus = restaurant == null ? null : restaurant.getStatus();
    }

    public void applyCategory(CategoryDocument category) {
        categoryName = category == null ? null : category.getName();
    }
}
