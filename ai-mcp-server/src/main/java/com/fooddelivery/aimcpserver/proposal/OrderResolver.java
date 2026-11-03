package com.fooddelivery.aimcpserver.proposal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fooddelivery.aimcpserver.client.GatewayClient;
import com.fooddelivery.aimcpserver.security.CallerContext;
import com.fooddelivery.aimcpserver.support.AddressText;
import com.fooddelivery.aimcpserver.support.NameMatcher;
import com.fooddelivery.aimcpserver.support.NeedInputException;
import com.fooddelivery.aimcpserver.support.ToolException;
import com.fooddelivery.aimcpserver.support.ToolText;
import tools.jackson.databind.JsonNode;

/**
 * Turns what the customer said (names, "my address") into ids, in code: the model only passes the user's words.
 * Anything unclear becomes a question for the customer (AMBIGUOUS / NEED_INPUT), never a guess and never a request for ids.
 */
final class OrderResolver {

    private static final Pattern ID = Pattern.compile("\\d{1,18}");
    private static final int MAX_OPTIONS = 8;
    private static final int MAX_QUANTITY = 20;
    private static final int MAX_LINES = 20;

    private final GatewayClient gateway;

    OrderResolver(GatewayClient gateway) {
        this.gateway = gateway;
    }

    /** A numeric text is the id; a name is looked up in the public restaurant list (words, case and accents ignored). */
    long restaurantId(CallerContext caller, String text) {
        if (text == null || text.isBlank()) {
            throw ToolException.invalid("restaurant is required: the name the customer said.");
        }
        String wanted = text.strip();
        if (ID.matcher(wanted).matches()) {
            return Long.parseLong(wanted);
        }
        List<JsonNode> matches = NameMatcher.match(wanted, restaurants(caller, Map.of("search", wanted, "size", 50)),
                node -> node.path("name").asString(""));
        if (matches.isEmpty()) {
            // The server-side search is a plain "contains": read the whole list and match word by word here
            matches = NameMatcher.match(wanted, restaurants(caller, Map.of("size", 100)),
                    node -> node.path("name").asString(""));
        }
        if (matches.isEmpty()) {
            throw ToolException.invalid("No restaurant matches \"" + ToolText.clean(wanted, 60) + "\".");
        }
        if (matches.size() > 1) {
            throw new NeedInputException("AMBIGUOUS: several restaurants match. Ask the customer which one they mean: "
                    + options(matches, "name") + ".");
        }
        return matches.get(0).path("id").asLong(0);
    }

    private List<JsonNode> restaurants(CallerContext caller, Map<String, Object> query) {
        List<JsonNode> list = new ArrayList<>();
        gateway.get(caller, "/api/v1/restaurants", query).path("content").forEach(list::add);
        return list;
    }

    /** The dishes of the order as menu item id to quantity, checked against that restaurant's menu. */
    Map<Long, Integer> dishes(List<ProposalService.Line> lines, Map<Long, JsonNode> menu, String restaurantName) {
        if (lines == null || lines.isEmpty()) {
            throw ToolException.invalid("items must contain at least one dish.");
        }
        if (lines.size() > MAX_LINES) {
            throw ToolException.invalid("An order may have at most " + MAX_LINES + " different items.");
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (ProposalService.Line line : lines) {
            if (line == null || line.quantity() == null || line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw ToolException.invalid("quantity must be between 1 and " + MAX_QUANTITY + " for every item.");
            }
            JsonNode dish = dishOf(line, menu, restaurantName);
            String name = ToolText.clean(dish.path("name").asString(""), 80);
            if (!dish.path("available").asBoolean(false)) {
                throw ToolException.invalid(name + " is not available right now.");
            }
            wanted.merge(dish.path("id").asLong(0), line.quantity(), Integer::sum);
        }
        if (wanted.size() > MAX_LINES) {
            throw ToolException.invalid("An order may have at most " + MAX_LINES + " different items.");
        }
        if (wanted.values().stream().anyMatch(quantity -> quantity > MAX_QUANTITY)) {
            throw ToolException.invalid("At most " + MAX_QUANTITY + " of one item.");
        }
        return wanted;
    }

    private JsonNode dishOf(ProposalService.Line line, Map<Long, JsonNode> menu, String restaurantName) {
        String text = line.dish() == null ? "" : line.dish().strip();
        Long id = line.menuItemId() != null ? line.menuItemId() : ID.matcher(text).matches() ? Long.valueOf(text) : null;
        if (id != null && menu.containsKey(id)) {
            return menu.get(id);
        }
        if (id != null && line.dish() == null) {
            throw ToolException.invalid("Menu item " + id + " is not on the menu of " + restaurantName + ".");
        }
        if (text.isEmpty()) {
            throw ToolException.invalid("Every item needs the dish name the customer said.");
        }
        List<JsonNode> matches = NameMatcher.match(text, new ArrayList<>(menu.values()),
                node -> node.path("name").asString(""));
        if (matches.isEmpty()) {
            throw ToolException.invalid("No dish matching \"" + ToolText.clean(text, 60) + "\" on the menu of "
                    + restaurantName + ".");
        }
        List<JsonNode> available = matches.stream().filter(node -> node.path("available").asBoolean(false)).toList();
        if (available.size() > 1) {
            throw new NeedInputException("AMBIGUOUS: several dishes match \"" + ToolText.clean(text, 60)
                    + "\". Ask the customer which one they mean: " + options(available, "name") + ".");
        }
        return available.isEmpty() ? matches.get(0) : available.get(0);
    }

    /**
     * The delivery address: the one the customer named, or their only saved address. When it cannot be decided, a
     * question is added to needs and null is returned.
     */
    JsonNode address(CallerContext caller, String text, List<String> needs) {
        List<JsonNode> addresses = new ArrayList<>();
        gateway.get(caller, "/api/v1/users/me/addresses", Map.of()).forEach(addresses::add);
        if (addresses.isEmpty()) {
            needs.add("the customer has no saved address: tell them to add one under /account/addresses first");
            return null;
        }
        List<JsonNode> chosen = addresses;
        if (text != null && !text.isBlank()) {
            String wanted = text.strip();
            chosen = ID.matcher(wanted).matches()
                    ? addresses.stream().filter(a -> a.path("id").asLong(0) == Long.parseLong(wanted)).toList()
                    : NameMatcher.match(wanted, addresses, AddressText::searchable);
        }
        if (chosen.size() == 1) {
            return chosen.get(0);
        }
        List<String> labels = new ArrayList<>();
        addresses.forEach(a -> labels.add(AddressText.describe(a)));
        needs.add("ask which saved address to deliver to (by its label and street, not by id): " + labels);
        return null;
    }

    private static String options(List<JsonNode> nodes, String nameField) {
        List<String> shown = new ArrayList<>();
        for (JsonNode node : nodes.subList(0, Math.min(MAX_OPTIONS, nodes.size()))) {
            shown.add(ToolText.clean(node.path(nameField).asString(""), 60) + " (id " + node.path("id").asLong(0) + ")");
        }
        return shown.toString();
    }
}
