package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.restaurantservice.cache.RestaurantCache;
import com.fooddelivery.restaurantservice.dto.response.OperatingHoursResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** "Open now" is never taken from the cache: it is worked out for the moment of the request. */
@ExtendWith(MockitoExtension.class)
class PublicRestaurantServiceTest {

    // Wednesday 2026-10-07, 13:00 in Bengaluru
    private static final Instant WEDNESDAY_1PM_IST = Instant.parse("2026-10-07T07:30:00Z");

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantMapper restaurantMapper;
    @Mock
    private RestaurantCache restaurantCache;

    @Test
    void aCachedPageGetsOpenNowForThisMoment() {
        PublicRestaurantService service = serviceAt(WEDNESDAY_1PM_IST);
        when(restaurantCache.details(eq(5L), any())).thenReturn(page(false,
                new OperatingHoursResponse(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), LocalTime.of(23, 0))));

        assertThat(service.getRestaurant(5L).openNow()).isTrue();
        // Served from the cache: MySQL was not read
        verifyNoInteractions(restaurantRepository);
    }

    @Test
    void closedHoursMeanClosedEvenIfTheCachedCopySaidOpen() {
        PublicRestaurantService service = serviceAt(WEDNESDAY_1PM_IST);
        when(restaurantCache.details(eq(5L), any())).thenReturn(page(true,
                new OperatingHoursResponse(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0), LocalTime.of(23, 0))));

        assertThat(service.getRestaurant(5L).openNow()).isFalse();
    }

    private PublicRestaurantService serviceAt(Instant now) {
        return new PublicRestaurantService(restaurantRepository, restaurantMapper,
                new OpenNowCalculator(Clock.fixed(now, ZoneOffset.UTC)), restaurantCache);
    }

    private static PublicRestaurantDetailsResponse page(boolean openNow, OperatingHoursResponse hours) {
        return new PublicRestaurantDetailsResponse(5L, "Spice Hub", "Biryani", List.of("Biryani"), "+919876543210",
                null, "Asia/Kolkata", List.of(hours), openNow);
    }
}
