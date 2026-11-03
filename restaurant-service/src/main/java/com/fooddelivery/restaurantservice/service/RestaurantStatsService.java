package com.fooddelivery.restaurantservice.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fooddelivery.restaurantservice.config.AppProperties;
import com.fooddelivery.restaurantservice.dto.response.RegistrationStatsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantStatsResponse;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.repository.RegistrationDayTotal;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.repository.RestaurantStatusTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Restaurant numbers for the admin dashboard and reports, counted in the database. */
@Service
@RequiredArgsConstructor
public class RestaurantStatsService {

    /** The calendar may show more than a year (a year plus the days around it). */
    static final int CALENDAR_MAX_DAYS = 400;

    private final RestaurantRepository restaurantRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RestaurantStatsResponse getStats(LocalDate fromDate, LocalDate toDate) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, ZoneId.of(appProperties.businessTimeZone()), clock);

        Map<RestaurantStatus, Long> now = perStatus(restaurantRepository.countPerStatus());
        Map<RestaurantStatus, Long> submitted = perStatus(
                restaurantRepository.countSubmittedPerStatus(period.from(), period.before()));

        return new RestaurantStatsResponse(period.fromDate(), period.toDate(), now, sum(now),
                now.getOrDefault(RestaurantStatus.ACTIVE, 0L),
                now.getOrDefault(RestaurantStatus.INACTIVE, 0L),
                now.getOrDefault(RestaurantStatus.SUSPENDED, 0L),
                now.getOrDefault(RestaurantStatus.PENDING, 0L),
                sum(submitted), submitted);
    }

    /**
     * Restaurants submitted per business day, only days with any (same "submitted" definition as getStats: createdAt,
     * whatever status they are in now). The database works out the date with one UTC offset per query, so a period
     * across a daylight-saving change is queried in parts, one per offset.
     */
    @Transactional(readOnly = true)
    public RegistrationStatsResponse getRegistrations(LocalDate fromDate, LocalDate toDate) {
        ZoneId zone = ZoneId.of(appProperties.businessTimeZone());
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, zone, clock, CALENDAR_MAX_DAYS);
        Map<LocalDate, Long> perDay = new TreeMap<>();
        Instant start = period.from();
        while (start.isBefore(period.before())) {
            ZoneOffsetTransition change = zone.getRules().nextTransition(start);
            Instant end = change == null || !change.getInstant().isBefore(period.before())
                    ? period.before() : change.getInstant();
            String offset = sqlOffset(zone.getRules().getOffset(start));
            for (RegistrationDayTotal row : restaurantRepository.countSubmittedPerDay(start, end, offset)) {
                perDay.merge(LocalDate.parse(row.getDay()), row.getTotal(), Long::sum);
            }
            start = end;
        }
        List<RegistrationStatsResponse.Day> days = perDay.entrySet().stream()
                .filter(day -> day.getValue() > 0)
                .map(day -> new RegistrationStatsResponse.Day(day.getKey(), day.getValue()))
                .toList();
        return new RegistrationStatsResponse(days);
    }

    /** MySQL's CONVERT_TZ wants "+05:30" (and "+00:00" for UTC, where Java says "Z"). */
    static String sqlOffset(ZoneOffset offset) {
        return offset.getTotalSeconds() == 0 ? "+00:00" : offset.getId();
    }

    private static Map<RestaurantStatus, Long> perStatus(List<RestaurantStatusTotal> totals) {
        Map<RestaurantStatus, Long> perStatus = new EnumMap<>(RestaurantStatus.class);
        totals.forEach(total -> perStatus.put(total.getStatus(), total.getTotal()));
        return perStatus;
    }

    private static long sum(Map<RestaurantStatus, Long> perStatus) {
        return perStatus.values().stream().mapToLong(Long::longValue).sum();
    }
}
