package com.fooddelivery.aimcpserver.support;

import tools.jackson.databind.JsonNode;

/** How a saved address (user-service AddressResponse) is called in questions to the customer: label, street, city, id. */
public final class AddressText {

    private AddressText() {
    }

    /** For example "HOME: 12 Lake View, Pune (id 5)". Never includes coordinates. */
    public static String describe(JsonNode address) {
        StringBuilder text = new StringBuilder(ToolText.clean(address.path("type").asString(""), 20));
        text.append(": ").append(ToolText.clean(address.path("line1").asString(""), 60));
        String city = ToolText.clean(address.path("city").asString(""), 40);
        if (!city.isEmpty()) {
            text.append(", ").append(city);
        }
        return text.append(" (id ").append(address.path("id").asLong(0)).append(')').toString();
    }

    /**
     * A delivery's pickup or drop-off address (delivery-service AddressResponse) on one line, as a delivery partner
     * reads it on the road: "12 MG Road, Flat 4, Bengaluru 560001". Never includes coordinates.
     */
    public static String line(JsonNode address) {
        StringBuilder text = new StringBuilder();
        for (String field : new String[] {"line1", "line2", "city"}) {
            String part = ToolText.clean(address.path(field).asString(""), 60);
            if (!part.isEmpty()) {
                text.append(text.isEmpty() ? "" : ", ").append(part);
            }
        }
        String postalCode = ToolText.clean(address.path("postalCode").asString(""), 12);
        if (!postalCode.isEmpty()) {
            text.append(text.isEmpty() ? "" : " ").append(postalCode);
        }
        return text.toString();
    }

    /** What a customer would type to pick this address: label, street, area and city. */
    public static String searchable(JsonNode address) {
        return address.path("type").asString("") + " " + address.path("line1").asString("") + " "
                + address.path("line2").asString("") + " " + address.path("city").asString("") + " "
                + address.path("postalCode").asString("");
    }
}
