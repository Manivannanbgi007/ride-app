package com.fooddelivery.aimcpclient.config;

import java.util.List;
import java.util.Map;

import com.fooddelivery.aimcpclient.identity.Role;
import org.springframework.stereotype.Component;

/** Starter questions shown in the empty chat, per role. Plain data, so changing them needs no logic change. */
@Component
public class SuggestionCatalog {

    private static final Map<Role, List<String>> BY_ROLE = Map.of(
            Role.PLATFORM_ADMIN, List.of(
                    "How many orders were placed today?",
                    "Were any orders cancelled today and why?",
                    "Which restaurants got the most orders today?",
                    "Are there any operational issues I should look at?"),
            Role.RESTAURANT_ADMIN, List.of(
                    "How many orders did my restaurant deliver today?",
                    "What are my most popular dishes?",
                    "Which orders are pending right now?",
                    "Who delivered the most of my orders today?"),
            Role.CUSTOMER, List.of(
                    "What offers are available today?",
                    "Show restaurants near my saved address",
                    "What is the status of my latest order?",
                    "I want to order a biryani"),
            Role.DELIVERY_PARTNER, List.of(
                    "Which delivery is assigned to me?",
                    "Where do I deliver my current order?",
                    "How much cash do I collect for this delivery?",
                    "How many deliveries did I complete today?"));

    public List<String> forRole(Role role) {
        return BY_ROLE.getOrDefault(role, List.of());
    }
}
