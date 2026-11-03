package com.fooddelivery.aimcpclient.dto.response;

import java.util.List;

/** Starter questions for the caller's role. */
public record SuggestionsResponse(List<String> suggestions) {
}
