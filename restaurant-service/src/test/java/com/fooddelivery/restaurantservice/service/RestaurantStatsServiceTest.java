package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.restaurantservice.config.AppProperties;
import com.fooddelivery.restaurantservice.dto.response.RegistrationStatsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantStatsResponse;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.exception.InvalidRequestException;
import com.fooddelivery.restaurantservice.repository.RegistrationDayTotal;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.repository.RestaurantStatusTotal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantStatsServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Test
    void restaurantsPerStatusNowAndSubmittedInThePeriod() {
        RestaurantStatsService service = new RestaurantStatsService(restaurantRepository,
                new AppProperties("Asia/Kolkata", null, "Asia/Kolkata"),
                Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        when(restaurantRepository.countPerStatus()).thenReturn(List.of(
                total(RestaurantStatus.ACTIVE, 720), total(RestaurantStatus.INACTIVE, 90),
                total(RestaurantStatus.SUSPENDED, 12), total(RestaurantStatus.PENDING, 25), total(RestaurantStatus.REJECTED, 3)));
        // 1-7 October in Bengaluru
        when(restaurantRepository.countSubmittedPerStatus(
                Instant.parse("2026-09-30T18:30:00Z"), Instant.parse("2026-10-07T18:30:00Z")))
                .thenReturn(List.of(total(RestaurantStatus.PENDING, 20), total(RestaurantStatus.ACTIVE, 4)));

        RestaurantStatsResponse stats = service.getStats(LocalDate.parse("2026-10-01"), null);

        assertThat(stats.total()).isEqualTo(850);
        assertThat(stats.active()).isEqualTo(720);
        assertThat(stats.inactive()).isEqualTo(90);
        assertThat(stats.suspended()).isEqualTo(12);
        assertThat(stats.pendingApproval()).isEqualTo(25);
        assertThat(stats.newRestaurants()).isEqualTo(24);
        assertThat(stats.toDate()).isEqualTo(LocalDate.parse("2026-10-07"));
    }

    @Test
    void registrationsPerBusinessDayListOnlyDaysWithRestaurants() {
        RestaurantStatsService service = new RestaurantStatsService(restaurantRepository,
                new AppProperties("Asia/Kolkata", null, "Asia/Kolkata"),
                Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        // 5-7 October: from local midnight (18:30 UTC the evening before) to before local midnight
        when(restaurantRepository.countSubmittedPerDay(Instant.parse("2026-10-04T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), "+05:30"))
                .thenReturn(List.of(day("2026-10-07", 3), day("2026-10-05", 2)));

        RegistrationStatsResponse response = service.getRegistrations(LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-07"));

        assertThat(response.days()).containsExactly(
                new RegistrationStatsResponse.Day(LocalDate.parse("2026-10-05"), 2),
                new RegistrationStatsResponse.Day(LocalDate.parse("2026-10-07"), 3));
    }

    @Test
    void registrationsAcrossADaylightSavingChangeAreQueriedOncePerOffset() {
        RestaurantStatsService service = new RestaurantStatsService(restaurantRepository,
                new AppProperties("Asia/Kolkata", null, "Europe/London"), Clock.systemUTC());
        when(restaurantRepository.countSubmittedPerDay(any(), any(), anyString())).thenReturn(List.of());

        // London clocks go back on 2026-10-25 at 01:00 UTC
        assertThat(service.getRegistrations(LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-26")).days()).isEmpty();

        Instant change = Instant.parse("2026-10-25T01:00:00Z");
        verify(restaurantRepository).countSubmittedPerDay(Instant.parse("2026-10-23T23:00:00Z"), change, "+01:00");
        verify(restaurantRepository).countSubmittedPerDay(change, Instant.parse("2026-10-27T00:00:00Z"), "+00:00");
    }

    @Test
    void registrationsAllowFourHundredDaysButNotMore() {
        RestaurantStatsService service = new RestaurantStatsService(restaurantRepository,
                new AppProperties("Asia/Kolkata", null, "Asia/Kolkata"), Clock.systemUTC());
        when(restaurantRepository.countSubmittedPerDay(any(), any(), anyString())).thenReturn(List.of());
        LocalDate from = LocalDate.parse("2026-01-01");

        assertThat(service.getRegistrations(from, from.plusDays(399)).days()).isEmpty();
        assertThatThrownBy(() -> service.getRegistrations(from, from.plusDays(400)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getRegistrations(from, from.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static RegistrationDayTotal day(String day, long total) {
        return new RegistrationDayTotal() {
            @Override
            public String getDay() {
                return day;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static RestaurantStatusTotal total(RestaurantStatus status, long total) {
        return new RestaurantStatusTotal() {
            @Override
            public RestaurantStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
