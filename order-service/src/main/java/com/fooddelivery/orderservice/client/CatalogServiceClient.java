package com.fooddelivery.orderservice.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemCard;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemSummary;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls catalog-service: current names, prices and availability of menu items. */
@Component
public class CatalogServiceClient {

    private static final ParameterizedTypeReference<List<MenuItemSummary>> MENU_ITEM_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<MenuItemCard>> MENU_ITEM_CARDS = new ParameterizedTypeReference<>() {
    };

    private static final String SERVICE = "catalog-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public CatalogServiceClient(@Qualifier("catalogServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** One call for many items. Unknown ids are simply missing from the result. */
    public Map<Long, MenuItemSummary> findMenuItems(Collection<Long> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return Map.of();
        }
        return remoteCalls.call(SERVICE, () -> {
            try {
                List<MenuItemSummary> items = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/menu-items")
                                .queryParam("ids", menuItemIds.toArray())
                                .build())
                        .retrieve()
                        .body(MENU_ITEM_LIST);
                return items == null ? Map.<Long, MenuItemSummary>of()
                        : items.stream().collect(Collectors.toMap(MenuItemSummary::id, Function.identity()));
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }

    /**
     * The same items as a customer's dish cards show them: current price and stock, with picture and category. One
     * call for many items; unknown ids (dishes no longer on any menu) are missing from the result. A failure is
     * passed on: a card must never show a price or stock that was not confirmed.
     */
    public Map<Long, MenuItemCard> findMenuItemCards(Collection<Long> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return Map.of();
        }
        return remoteCalls.call(SERVICE, () -> {
            try {
                List<MenuItemCard> items = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/menu-items")
                                .queryParam("ids", menuItemIds.toArray())
                                .build())
                        .retrieve()
                        .body(MENU_ITEM_CARDS);
                return items == null ? Map.<Long, MenuItemCard>of()
                        : items.stream().collect(Collectors.toMap(MenuItemCard::id, Function.identity()));
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }
}
