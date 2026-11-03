package com.fooddelivery.aimcpserver.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One dish of an order as the model passes it to prepare_order: the dish as the customer said it. */
public record OrderLine(
        @JsonProperty(required = true) @JsonPropertyDescription("The dish name as the customer said it") String dish,
        @JsonProperty(required = true) @JsonPropertyDescription("How many, 1 to 20") Integer quantity
) {
}
