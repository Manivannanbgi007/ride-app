package com.fooddelivery.adminservice.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;
import com.fooddelivery.adminservice.dto.response.RevenueReportResponse;

/** Turns revenue per day into revenue per day, week (ISO, Monday to Sunday) or calendar month. */
public final class RevenueGrouping {

    private RevenueGrouping() {
    }

    /**
     * @param days consecutive days, oldest first (as order-service lists them)
     * @return one entry per day, week or month; the first and last can be partial (they start or end with the report)
     */
    public static List<RevenueReportResponse.Period> group(List<OrderStats.Day> days, RevenueGroupBy groupBy) {
        Map<LocalDate, Totals> groups = new LinkedHashMap<>();
        for (OrderStats.Day day : days) {
            LocalDate start = switch (groupBy) {
                case DAY -> day.date();
                case WEEK -> day.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> day.date().withDayOfMonth(1);
            };
            groups.computeIfAbsent(start, key -> new Totals(day.date())).add(day);
        }
        List<RevenueReportResponse.Period> periods = new ArrayList<>();
        groups.forEach((start, totals) -> periods.add(new RevenueReportResponse.Period(label(start, groupBy),
                totals.first, totals.last, totals.orders, totals.completed, totals.revenue)));
        return periods;
    }

    /** 2026-10-07, 2026-W41 or 2026-10. */
    static String label(LocalDate start, RevenueGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> start.toString();
            case WEEK -> "%d-W%02d".formatted(start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> YearMonth.from(start).toString();
        };
    }

    private static final class Totals {
        private final LocalDate first;
        private LocalDate last;
        private long orders;
        private long completed;
        private BigDecimal revenue = BigDecimal.ZERO.setScale(2);

        private Totals(LocalDate first) {
            this.first = first;
            this.last = first;
        }

        private void add(OrderStats.Day day) {
            last = day.date();
            orders += day.orders();
            completed += day.completed();
            revenue = revenue.add(day.revenue());
        }
    }
}
