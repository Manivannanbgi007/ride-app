package com.fooddelivery.orderservice.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.CatalogLookupClient;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemDetails;
import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.CustomerTopFoodsResponse;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.FoodTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The dishes a customer ordered most, for the admin customer screen. Orders count unless CANCELLED, REJECTED or
 * PAYMENT_PENDING: the customer order summary has no "real order" definition of its own (it lists every status),
 * so this follows its split of finished/active versus cancelled/rejected and also leaves out unpaid orders that
 * never reached a restaurant. The database ranks and sums in one grouped query; image and category are looked up
 * in catalog-service afterwards and are simply null when it cannot answer.
 */
@Service
@RequiredArgsConstructor
public class CustomerTopFoodsService {

    private final OrderStatsRepository orderStatsRepository;
    private final CatalogLookupClient catalogLookupClient;
    private final AppProperties appProperties;
    private final Clock clock;

    // No transaction here on purpose: the query finishes first, so the remote call never holds a connection
    public CustomerTopFoodsResponse getTopFoods(long customerId, TopFoodsPeriod period, int limit) {
        ZoneId zone = ZoneId.of(appProperties.businessTimeZone());
        Instant from = period.start(clock.instant(), zone);
        List<FoodTotals> rows = orderStatsRepository.topFoodsOfCustomer(customerId, from, limit);

        Map<Long, MenuItemDetails> details = catalogLookupClient.findDetails(
                rows.stream().map(FoodTotals::getMenuItemId).toList());
        List<CustomerTopFoodsResponse.Item> items = rows.stream().map(row -> {
            MenuItemDetails detail = details.get(row.getMenuItemId());
            return new CustomerTopFoodsResponse.Item(row.getMenuItemId(), row.getName(), row.getOrderCount(),
                    row.getQuantity(), row.getTotalSpent().setScale(2, RoundingMode.HALF_UP),
                    row.getUnitPrice().setScale(2, RoundingMode.HALF_UP), row.getLastOrderedAt(),
                    detail == null ? null : detail.imageUrl(), detail == null ? null : detail.categoryName());
        }).toList();
        return new CustomerTopFoodsResponse(customerId, period.name(), from.atZone(zone).toLocalDate(), items);
    }
}
