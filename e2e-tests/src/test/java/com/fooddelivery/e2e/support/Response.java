package com.fooddelivery.e2e.support;

import java.math.BigDecimal;
import java.net.http.HttpHeaders;

import tools.jackson.databind.JsonNode;

/**
 * An answer of the platform. JSON values are read with JSON pointers: {@code response.text("/price/totalAmount")}.
 *
 * @param body the JSON body; a missing node when the answer had none
 */
public record Response(int status, HttpHeaders headers, JsonNode body, String rawBody) {

    /** Fails with the body in the message (the ProblemDetail says what went wrong) unless the status is expected. */
    public Response expect(int expectedStatus) {
        if (status != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but got " + status + ": " + rawBody);
        }
        return this;
    }

    public JsonNode json(String pointer) {
        return body.at(pointer);
    }

    public String text(String pointer) {
        JsonNode node = json(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    public long number(String pointer) {
        return json(pointer).asLong();
    }

    public BigDecimal amount(String pointer) {
        return json(pointer).decimalValue();
    }

    /** The id of what was created. */
    public long id() {
        return number("/id");
    }

    public String header(String name) {
        return headers.firstValue(name).orElse(null);
    }
}
