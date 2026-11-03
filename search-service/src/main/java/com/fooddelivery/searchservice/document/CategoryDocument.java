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

/** A food category in the "category" index, built from CategoryChanged events. */
@Document(indexName = CategoryDocument.INDEX, createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/settings.json")
@Mapping(mappingPath = "elasticsearch/category-mapping.json")
@Getter
@Setter
@NoArgsConstructor
public class CategoryDocument {

    public static final String INDEX = "category";

    @Id
    private Long id;

    private String name;

    private String description;

    private String imageUrl;

    private int displayOrder;

    /** ACTIVE or INACTIVE (inactive categories are hidden from customers). */
    private String status;

    /** The category's version in catalog-service: older events are ignored. */
    private Long sourceVersion;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;
}
