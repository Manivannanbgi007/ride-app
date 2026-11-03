package com.fooddelivery.aimcpserver.tool;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.dto.Card;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.security.Role;
import com.fooddelivery.aimcpserver.support.AddressText;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Customers: finding restaurants and dishes and reading a menu (search-service and catalog-service, public data). */
@Component
public class CustomerSearchTools extends BaseTools {

    private static final int MAX_QUERY = 100;
    private static final double DEFAULT_RADIUS_KM = 5.0;

    /** Dish types of the catalog. */
    public enum FoodType {
        VEG, NON_VEG, EGG
    }

    /** How dishes are ordered in the results. */
    public enum FoodSort {
        RELEVANCE, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW
    }

    public CustomerSearchTools(GatewayClient gateway, CallerResolver callers, Clock clock, JsonMapper mapper) {
        super(gateway, callers, clock, mapper);
    }

    @Tool(name = "search_restaurants", description = "Search restaurants by words (name, cuisine), city or cuisine. For "
            + "restaurants NEAR the customer ('near me', 'near my delivery location') call it with nearMe=true: their saved "
            + "address is used (if they have several the result is a NEED_INPUT question with the address labels: ask "
            + "the customer which one in plain words, then call again with addressId). Never ask for ids and never guess a "
            + "location. Results show the distance when an address is used.")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult searchRestaurants(
            @ToolParam(description = "Words to look for, for example biryani", required = false) String query,
            @ToolParam(description = "City name", required = false) String city,
            @ToolParam(description = "Cuisine, for example North Indian", required = false) String cuisine,
            @ToolParam(description = "Id of one of the customer's saved addresses (my_addresses) to search near",
                    required = false) Long addressId,
            @ToolParam(description = "true to search near the customer: their saved address is used (asked about when "
                    + "there are several)", required = false)
            Boolean nearMe,
            @ToolParam(description = "Search radius in km, 1 to 25 (default 5); only together with addressId",
                    required = false) Double radiusKm,
            @ToolParam(description = "How many restaurants, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        Map<String, Object> params = new LinkedHashMap<>();
        putText(params, "q", ToolArgs.optionalText("query", query, MAX_QUERY));
        putText(params, "city", ToolArgs.optionalText("city", city, MAX_QUERY));
        putText(params, "cuisine", ToolArgs.optionalText("cuisine", cuisine, 50));
        boolean wantsNear = addressId != null || Boolean.TRUE.equals(nearMe);
        if (radiusKm != null && !wantsNear) {
            throw ToolException.invalid("radiusKm needs nearMe=true or an addressId.");
        }
        if (radiusKm != null && (radiusKm < 1 || radiusKm > 25)) {
            throw ToolException.invalid("radiusKm must be between 1 and 25.");
        }
        params.put("size", ToolArgs.limit(limit, 10, Card.MAX_ITEMS));
        String near = null;
        if (wantsNear) {
            JsonNode address = addressId != null
                    ? findAddress(caller, ToolArgs.positiveId("addressId", addressId)) : onlyOrAskAddress(caller);
            JsonNode lat = address.path("latitude");
            JsonNode lon = address.path("longitude");
            if (!lat.isNumber() || !lon.isNumber()) {
                throw ToolException.invalid("That saved address has no map location; I can search by city or name instead.");
            }
            // The location comes only from the customer's own saved address, never from the model or the user's text
            params.put("lat", lat.decimalValue().toPlainString());
            params.put("lon", lon.decimalValue().toPlainString());
            double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm.doubleValue();
            params.put("radiusKm", radius);
            near = "within " + radius + " km of the saved address " + AddressText.describe(address);
        }
        JsonNode found = gateway.get(caller, "/api/v1/search/restaurants", params);

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode restaurant : found.path("results")) {
            long id = ToolJson.number(restaurant, "id");
            ObjectNode row = object();
            row.put("id", id);
            row.put("name", ToolJson.text(restaurant, "name"));
            row.put("city", ToolJson.text(restaurant, "city"));
            List<String> cuisines = new ArrayList<>();
            restaurant.path("cuisines").forEach(c -> cuisines.add(c.asString("")));
            row.put("cuisines", String.join(", ", cuisines));
            JsonNode distance = restaurant.path("distanceKm");
            if (distance.isNumber()) {
                row.put("distanceKm", Math.round(distance.asDouble() * 10) / 10.0);
            }
            rows.add(row);
            items.add(Cards.item(id, ToolJson.text(restaurant, "name"), ToolJson.text(restaurant, "addressLine"),
                    cuisines,
                    List.of(Cards.meta("City", ToolJson.text(restaurant, "city")),
                            Cards.meta("Distance", distance.isNumber()
                                    ? (Math.round(distance.asDouble() * 10) / 10.0) + " km" : "")),
                    null, RoutePaths.restaurantPage(id)));
        }
        ObjectNode summary = object();
        summary.put("totalFound", found.path("totalHits").asLong(rows.size()));
        if (near != null) {
            summary.put("searchedNear", near);
        }
        ToolJson.putCapped(summary, "restaurants", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("RESTAURANT", "Restaurants", items));
    }

    @Tool(name = "search_foods", description = "Search dishes across all restaurants (only dishes that can be ordered now). "
            + "Filters: words, food type VEG/NON_VEG/EGG, price range, one restaurant, city.")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult searchFoods(
            @ToolParam(description = "Words to look for, for example chicken biryani", required = false) String query,
            @ToolParam(description = "VEG, NON_VEG or EGG", required = false) String foodType,
            @ToolParam(description = "Lowest price", required = false) Double minPrice,
            @ToolParam(description = "Highest price", required = false) Double maxPrice,
            @ToolParam(description = "Only dishes of this restaurant id", required = false) Long restaurantId,
            @ToolParam(description = "City name", required = false) String city,
            @ToolParam(description = "RELEVANCE (default), PRICE_LOW_TO_HIGH or PRICE_HIGH_TO_LOW", required = false)
            String sort,
            @ToolParam(description = "How many dishes, 1 to 25 (default 10)", required = false) Integer limit,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        Map<String, Object> params = new LinkedHashMap<>();
        putText(params, "q", ToolArgs.optionalText("query", query, MAX_QUERY));
        putText(params, "city", ToolArgs.optionalText("city", city, MAX_QUERY));
        FoodType type = ToolArgs.optionalEnum("foodType", foodType, FoodType.class);
        if (type != null) {
            params.put("foodType", type.name());
        }
        FoodSort order = ToolArgs.optionalEnum("sort", sort, FoodSort.class);
        if (order != null) {
            params.put("sort", order.name());
        }
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0) {
            throw ToolException.invalid("Prices must not be negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw ToolException.invalid("minPrice must not be greater than maxPrice.");
        }
        if (minPrice != null) {
            params.put("minPrice", minPrice);
        }
        if (maxPrice != null) {
            params.put("maxPrice", maxPrice);
        }
        if (restaurantId != null) {
            params.put("restaurantId", ToolArgs.positiveId("restaurantId", restaurantId));
        }
        params.put("size", ToolArgs.limit(limit, 10, Card.MAX_ITEMS));
        JsonNode found = gateway.get(caller, "/api/v1/search/foods", params);

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode food : found.path("results")) {
            long id = ToolJson.number(food, "id");
            long restaurant = ToolJson.number(food, "restaurantId");
            ObjectNode row = object();
            row.put("menuItemId", id);
            row.put("name", ToolJson.text(food, "name"));
            row.put("price", ToolJson.amountText(food, "price"));
            row.put("type", ToolJson.text(food, "foodType"));
            row.put("restaurantId", restaurant);
            row.put("restaurant", ToolJson.text(food, "restaurantName"));
            row.put("city", ToolJson.text(food, "restaurantCity"));
            rows.add(row);
            items.add(Cards.item(id, ToolJson.text(food, "name"), ToolJson.text(food, "restaurantName"),
                    List.of(ToolJson.text(food, "foodType")),
                    List.of(Cards.meta("Price", ToolJson.amountText(food, "price")),
                            Cards.meta("City", ToolJson.text(food, "restaurantCity")),
                            Cards.meta("Category", ToolJson.text(food, "categoryName"))),
                    ToolJson.url(food, "imageUrl"), restaurant > 0 ? RoutePaths.restaurantPage(restaurant) : null));
        }
        ObjectNode summary = object();
        summary.put("totalFound", found.path("totalHits").asLong(rows.size()));
        ToolJson.putCapped(summary, "dishes", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null : Cards.entities("FOOD", "Dishes", items));
    }

    @Tool(name = "restaurant_menu", description = "The menu of one restaurant, grouped by category, with price, food type "
            + "and whether each dish is available. Needed before preparing an order (item ids come from here).")
    @ToolPolicy(roles = Role.CUSTOMER)
    public ToolResult restaurantMenu(
            @ToolParam(description = "The restaurant id (from search_restaurants or search_foods)") Long restaurantId,
            ToolContext toolContext) {
        CallerContext caller = callers.require(toolContext, Role.CUSTOMER);
        long id = ToolArgs.positiveId("restaurantId", restaurantId);
        JsonNode menu = gateway.get(caller, "/api/v1/restaurants/" + id + "/menu", Map.of());

        List<ObjectNode> rows = new ArrayList<>();
        List<Card.Item> items = new ArrayList<>();
        for (JsonNode section : menu.path("sections")) {
            String category = ToolJson.text(section, "categoryName");
            for (JsonNode item : section.path("items")) {
                long itemId = ToolJson.number(item, "id");
                boolean available = item.path("available").asBoolean(false);
                ObjectNode row = object();
                row.put("menuItemId", itemId);
                row.put("name", ToolJson.text(item, "name"));
                row.put("category", category);
                row.put("price", ToolJson.amountText(item, "price"));
                row.put("type", ToolJson.text(item, "foodType"));
                row.put("available", available);
                rows.add(row);
                items.add(Cards.item(itemId, ToolJson.text(item, "name"), category,
                        List.of(available ? "Available" : "Unavailable", ToolJson.text(item, "foodType")),
                        List.of(Cards.meta("Price", ToolJson.amountText(item, "price"))),
                        ToolJson.url(item, "imageUrl"), RoutePaths.restaurantPage(id)));
            }
        }
        ObjectNode summary = object();
        summary.put("restaurantId", id);
        summary.put("restaurant", ToolJson.text(menu, "restaurantName"));
        summary.put("itemCount", rows.size());
        ToolJson.putCapped(summary, "items", rows, LIST_BUDGET);
        return result(summary, items.isEmpty() ? null
                : Cards.entities("FOOD", "Menu of " + ToolJson.text(menu, "restaurantName"), items));
    }

    private JsonNode onlyOrAskAddress(CallerContext caller) {
        JsonNode addresses = gateway.get(caller, "/api/v1/users/me/addresses", Map.of());
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        if (addresses.isEmpty()) {
            throw new NeedInputException("NEED_INPUT: the customer has no saved address. Tell them so and offer to search "
                    + "by city or name, or to add an address under /account/addresses.");
        }
        List<String> options = new ArrayList<>();
        addresses.forEach(address -> options.add(AddressText.describe(address)
                + (address.path("latitude").isNumber() ? "" : " [no map location]")));
        throw new NeedInputException("NEED_INPUT: the customer has several saved addresses. Ask which one to use "
                + "(by its label and street, not by id): " + options);
    }

    private JsonNode findAddress(CallerContext caller, long addressId) {
        for (JsonNode address : gateway.get(caller, "/api/v1/users/me/addresses", Map.of())) {
            if (address.path("id").asLong(0) == addressId) {
                return address;
            }
        }
        throw ToolException.invalid("addressId " + addressId + " is not one of the customer's saved addresses. "
                + "Call my_addresses and use one of those ids.");
    }

    private static void putText(Map<String, Object> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }
}
