package com.fooddelivery.searchservice.document;

import java.time.Instant;
import java.util.List;

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
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

/**
 * A restaurant in the "restaurant" index: a copy of restaurant-service's data, built from RestaurantChanged
 * events. Field types and analyzers are in resources/elasticsearch (the mapping file wins over annotations).
 */
@Document(indexName = RestaurantDocument.INDEX, createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/settings.json")
@Mapping(mappingPath = "elasticsearch/restaurant-mapping.json")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantDocument {

    public static final String INDEX = "restaurant";

    /** Only these restaurants are shown to customers. */
    public static final String VISIBLE_STATUS = "ACTIVE";

    @Id
    private Long id;

    private String name;

    private String description;

    private List<String> cuisines;

    private String city;

    private String addressLine;

    /** Null when the restaurant has no coordinates ("near me" cannot place it). */
    private GeoPoint location;

    private String status;

    /** The restaurant's version in restaurant-service: older events are ignored. */
    private Long sourceVersion;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;

    /** Average of the customers' ratings (1.0 to 5.0); null while the restaurant has no rating. Owned by review-service. */
    private Double ratingAverage;

    /** How many ratings the average is made of; 0 (or absent in documents indexed before ratings existed) = none. */
    private Integer ratingCount = 0;

    /**
     * Takes over the rating stored in the index. The events know nothing about ratings, so a document rebuilt from
     * an event must not lose them (they are refreshed only by the rating sync).
     */
    public void keepRatingOf(RestaurantDocument stored) {
        ratingAverage = stored == null ? null : stored.getRatingAverage();
        ratingCount = stored == null ? null : stored.getRatingCount();
        if (ratingCount == null) {
            ratingCount = 0;
        }
    }

    public boolean isVisible() {
        return VISIBLE_STATUS.equals(status);
    }
}
