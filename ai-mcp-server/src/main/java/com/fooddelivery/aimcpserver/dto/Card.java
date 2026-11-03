package com.fooddelivery.aimcpserver.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the chat window draws next to the answer. Cards are built from tool data (never from the model's text),
 * so the model cannot invent what a card shows. The "kind" field is the contract with the UI.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Card.Stats.class, name = "stats"),
        @JsonSubTypes.Type(value = Card.Table.class, name = "table"),
        @JsonSubTypes.Type(value = Card.Entities.class, name = "entities")
})
public sealed interface Card {

    /** The heading of the card. */
    String title();

    /** Most cards one answer may carry, and most items/rows one card may carry. */
    int MAX_CARDS = 5;
    int MAX_ITEMS = 25;

    record Stats(String title, List<Stat> stats) implements Card {
    }

    record Stat(String label, String value, String hint) {
    }

    record Table(String title, List<Column> columns, List<Map<String, Object>> rows) implements Card {
    }

    /** align is "left" or "right". */
    record Column(String key, String label, String align) {
    }

    /** entityType is RESTAURANT, FOOD, OFFER, ORDER or DELIVERY. */
    record Entities(String entityType, String title, List<Item> items) implements Card {
    }

    record Item(Object id, String title, String subtitle, List<String> badges, List<Meta> meta, String imageUrl,
                Link link) {
    }

    record Meta(String label, String value) {
    }
}
