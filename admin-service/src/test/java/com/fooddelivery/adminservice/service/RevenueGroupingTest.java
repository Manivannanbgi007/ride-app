package com.fooddelivery.adminservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.adminservice.client.RemoteRecords.OrderStats;
import com.fooddelivery.adminservice.dto.request.RevenueGroupBy;
import com.fooddelivery.adminservice.dto.response.RevenueReportResponse;
import org.junit.jupiter.api.Test;

class RevenueGroupingTest {

    // Wednesday 2026-09-30 to Wednesday 2026-10-14: 100.00 revenue and 2 orders every day
    private final List<OrderStats.Day> days = days(LocalDate.parse("2026-09-30"), 15);

    @Test
    void weeksRunMondayToSundayAndTheFirstAndLastArePartial() {
        List<RevenueReportResponse.Period> weeks = RevenueGrouping.group(days, RevenueGroupBy.WEEK);

        assertThat(weeks).extracting(RevenueReportResponse.Period::label).containsExactly("2026-W40", "2026-W41", "2026-W42");
        assertThat(weeks.getFirst().from()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(weeks.getFirst().to()).isEqualTo(LocalDate.parse("2026-10-04"));
        assertThat(weeks.getFirst().revenue()).isEqualByComparingTo("500.00");
        assertThat(weeks.get(1).revenue()).isEqualByComparingTo("700.00");
        assertThat(weeks.get(2).to()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(weeks.get(2).orders()).isEqualTo(6);
    }

    @Test
    void monthsAreCalendarMonths() {
        List<RevenueReportResponse.Period> months = RevenueGrouping.group(days, RevenueGroupBy.MONTH);

        assertThat(months).extracting(RevenueReportResponse.Period::label).containsExactly("2026-09", "2026-10");
        assertThat(months.getFirst().revenue()).isEqualByComparingTo("100.00");
        assertThat(months.get(1).revenue()).isEqualByComparingTo("1400.00");
        assertThat(months.get(1).completed()).isEqualTo(14);
    }

    @Test
    void daysStayDays() {
        assertThat(RevenueGrouping.group(days, RevenueGroupBy.DAY)).hasSize(15)
                .first().extracting(RevenueReportResponse.Period::label).isEqualTo("2026-09-30");
    }

    @Test
    void weekLabelsUseTheIsoWeekYear() {
        // 2026-12-28 is in week 53 of 2026; 2027-01-04 starts week 1 of 2027
        assertThat(RevenueGrouping.label(LocalDate.parse("2026-12-28"), RevenueGroupBy.WEEK)).isEqualTo("2026-W53");
        assertThat(RevenueGrouping.label(LocalDate.parse("2027-01-04"), RevenueGroupBy.WEEK)).isEqualTo("2027-W01");
    }

    private static List<OrderStats.Day> days(LocalDate first, int count) {
        List<OrderStats.Day> days = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            days.add(new OrderStats.Day(first.plusDays(i), 2, 1, 0, new BigDecimal("100.00")));
        }
        return days;
    }
}
