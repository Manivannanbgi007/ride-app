package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.client.CatalogServiceClient;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemCard;
import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.CustomerOrderActivityResponse;
import com.fooddelivery.orderservice.dto.response.CustomerOrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.FavouriteDishResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.FavouriteDishRow;
import com.fooddelivery.orderservice.repository.StatusTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's own dashboard: their orders in numbers (all time), what they ordered and spent in a period (day by
 * day), and the dishes they order most, priced and stocked as they are today. Always the calling customer's own
 * orders: the customer id comes from their access token, never from the request. The database adds up; nothing is
 * counted in the browser.
 */
@Service
@RequiredArgsConstructor
public class CustomerInsightsService {

    /** A dashboard shows at most a year of activity at a time. */
    public static final int MAX_ACTIVITY_DAYS = 366;

    private final OrderRepository orderRepository;
    private final OrderStatsRepository orderStatsRepository;
    private final OrderStatsService orderStatsService;
    private final CatalogServiceClient catalogServiceClient;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CustomerOrderSummaryResponse summary(Long customerId) {
        return CustomerSummaries.of(customerId, orderRepository.sumByStatusForCustomer(customerId),
                orderRepository.findLastOrderTime(customerId).orElse(null), currency());
    }

    /**
     * The period's orders by status and what the delivered ones cost, plus every day of the period. Both days default
     * to today; the period is at most {@value #MAX_ACTIVITY_DAYS} days.
     */
    @Transactional(readOnly = true)
    public CustomerOrderActivityResponse activity(Long customerId, LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = ZoneId.of(appProperties.businessTimeZone());
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock, MAX_ACTIVITY_DAYS);

        long total = 0;
        BigDecimal spent = BigDecimal.ZERO;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (StatusTotals totals : orderRepository.sumByStatusForCustomerBetween(customerId, period.from(),
                period.before())) {
            total += totals.getOrderCount();
            byStatus.merge(totals.getStatus().name(), totals.getOrderCount(), Long::sum);
            if (totals.getStatus() == OrderStatus.DELIVERED) {
                spent = spent.add(totals.getTotalAmount());
            }
        }
        return new CustomerOrderActivityResponse(period.fromDate(), period.toDate(), total, byStatus,
                spent.setScale(2), currency(), orderStatsService.perDayForCustomer(customerId, period));
    }

    /**
     * The dishes the customer ordered most, each with today's price and stock from catalog-service (one call for all
     * of them). A dish no longer on any menu is kept, without a price and not available. If catalog-service cannot
     * answer, the call fails: a dish is never offered at a price or stock that was not confirmed.
     */
    // No transaction here on purpose: the query finishes first, so the remote call never holds a connection
    public List<FavouriteDishResponse> favouriteDishes(Long customerId, int limit) {
        List<FavouriteDishRow> rows = orderStatsRepository.favouriteDishesOfCustomer(customerId, limit);
        Map<Long, MenuItemCard> cards = catalogServiceClient.findMenuItemCards(
                rows.stream().map(FavouriteDishRow::getMenuItemId).toList());
        return rows.stream().map(row -> {
            MenuItemCard card = cards.get(row.getMenuItemId());
            return new FavouriteDishResponse(row.getMenuItemId(), card == null ? row.getName() : card.name(),
                    row.getRestaurantId(), row.getRestaurantName(),
                    card == null || card.price() == null ? null : card.price().setScale(2, RoundingMode.HALF_UP),
                    card == null ? null : card.foodType(), card == null ? null : card.imageUrl(),
                    card == null ? null : card.categoryName(), card != null && card.isOrderable(),
                    row.getOrderCount(), row.getQuantity(), row.getLastOrderedAt());
        }).toList();
    }

    private String currency() {
        return appProperties.pricing().currency();
    }
}
