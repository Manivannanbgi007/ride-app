package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.MenuItemRevenueResponse;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.repository.OrderStatsRepository;
import com.fooddelivery.orderservice.repository.OrderStatsRepository.DishDayTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revenue of one dish over time for the admin Food Detail chart. Like the other statistics it counts DELIVERED
 * orders only ("completed revenue") and uses the order's created time in the business time zone. The database
 * sums the order lines per business day; this class folds the days into months or weeks and fills the gaps
 * so the chart has no holes. The dish may be unknown here (the catalog is another service): that is all zeros.
 */
@Service
@RequiredArgsConstructor
public class MenuItemRevenueService {

    private final OrderStatsRepository orderStatsRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MenuItemRevenueResponse getRevenue(long menuItemId, RevenuePeriod period) {
        ZoneId zone = ZoneId.of(appProperties.businessTimeZone());
        LocalDate first = period.firstBucketStart(LocalDate.now(clock.withZone(zone)));
        Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
        LocalDate end = first;
        for (int i = 0; i < period.buckets(); i++) {
            buckets.put(end, new Bucket());
            end = period.next(end);
        }

        // The database takes one UTC offset per query, so a window across a daylight-saving change is queried in parts.
        Instant before = end.atStartOfDay(zone).toInstant();
        Instant partStart = first.atStartOfDay(zone).toInstant();
        while (partStart.isBefore(before)) {
            ZoneOffsetTransition change = zone.getRules().nextTransition(partStart);
            Instant partEnd = change == null || !change.getInstant().isBefore(before) ? before : change.getInstant();
            String offset = OrderStatsService.sqlOffset(zone.getRules().getOffset(partStart));
            for (DishDayTotals row : orderStatsRepository.sumDishPerDay(menuItemId, OrderStatus.DELIVERED.name(),
                    partStart, partEnd, offset)) {
                Bucket bucket = buckets.get(period.bucketStart(LocalDate.parse(row.getDay())));
                if (bucket != null) {
                    // An order has one created time, so it is counted in one day and the days' counts can be added
                    bucket.revenue = bucket.revenue.add(row.getRevenue());
                    bucket.quantity += row.getQuantity();
                    bucket.orders += row.getOrderCount();
                }
            }
            partStart = partEnd;
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalQuantity = 0;
        List<MenuItemRevenueResponse.Point> points = new ArrayList<>();
        for (Map.Entry<LocalDate, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            totalRevenue = totalRevenue.add(bucket.revenue);
            totalQuantity += bucket.quantity;
            points.add(new MenuItemRevenueResponse.Point(period.label(entry.getKey()), entry.getKey(),
                    money(bucket.revenue), bucket.quantity, bucket.orders));
        }
        return new MenuItemRevenueResponse(menuItemId, period.name(), money(totalRevenue), totalQuantity, points);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Bucket {
        private BigDecimal revenue = BigDecimal.ZERO;
        private long quantity;
        private long orders;
    }
}
