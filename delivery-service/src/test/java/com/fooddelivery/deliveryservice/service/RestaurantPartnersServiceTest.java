package com.fooddelivery.deliveryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.client.RestaurantServiceClient;
import com.fooddelivery.deliveryservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.response.RestaurantPartnersResponse;
import com.fooddelivery.deliveryservice.exception.InvalidRequestException;
import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository.PartnerTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantPartnersServiceTest {

    private static final long OWNER = 3L;
    private static final long RESTAURANT = 10L;

    @Mock
    private RestaurantServiceClient restaurantServiceClient;
    @Mock
    private PartnerStatsRepository partnerStatsRepository;

    private RestaurantPartnersService service;

    @BeforeEach
    void setUp() {
        // Wednesday 2026-10-07 12:30 in Asia/Kolkata
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T07:00:00Z"), ZoneOffset.UTC);
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null, null, null, null, null);
        service = new RestaurantPartnersService(restaurantServiceClient, partnerStatsRepository, properties, clock);
    }

    @Test
    void defaultsToTodayInTheBusinessZoneAndRoundsTheAverageToOneDecimal() {
        givenOwnedRestaurant();
        when(partnerStatsRepository.topPartnersOfRestaurant(RESTAURANT, Instant.parse("2026-10-06T18:30:00Z"),
                Instant.parse("2026-10-07T18:30:00Z"), 5)).thenReturn(List.of(
                partner(9L, "Ravi Kumar", 4, "23.456"), partner(8L, null, 2, null)));

        RestaurantPartnersResponse response = service.getPartners(OWNER, RESTAURANT, null, null, 5);

        assertThat(response.restaurantId()).isEqualTo(RESTAURANT);
        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(response.partners()).hasSize(2);
        assertThat(response.partners().getFirst())
                .isEqualTo(new RestaurantPartnersResponse.Partner(9L, "Ravi Kumar", 4, 23.5));
        assertThat(response.partners().get(1))
                .isEqualTo(new RestaurantPartnersResponse.Partner(8L, null, 2, null));
    }

    @Test
    void bothEndDatesAreIncluded() {
        givenOwnedRestaurant();
        when(partnerStatsRepository.topPartnersOfRestaurant(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

        service.getPartners(OWNER, RESTAURANT, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2);

        verify(partnerStatsRepository).topPartnersOfRestaurant(RESTAURANT, Instant.parse("2026-09-30T18:30:00Z"),
                Instant.parse("2026-10-03T18:30:00Z"), 2);
    }

    @Test
    void theRangeIsAtMost366DaysAndNotReversed() {
        givenOwnedRestaurant();
        when(partnerStatsRepository.topPartnersOfRestaurant(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

        service.getPartners(OWNER, RESTAURANT, LocalDate.of(2025, 10, 7), LocalDate.of(2026, 10, 7), 5);

        assertThatThrownBy(() -> service.getPartners(OWNER, RESTAURANT, LocalDate.of(2025, 10, 6),
                LocalDate.of(2026, 10, 7), 5)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getPartners(OWNER, RESTAURANT, LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 6), 5)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void anotherOwnersOrUnknownRestaurantIsNotFoundAndNothingIsQueried() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT)).thenReturn(Optional.of(summary(999L)));
        assertThatThrownBy(() -> service.getPartners(OWNER, RESTAURANT, null, null, 5))
                .isInstanceOf(ResourceNotFoundException.class);

        when(restaurantServiceClient.findRestaurant(11L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPartners(OWNER, 11L, null, null, 5))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(partnerStatsRepository);
    }

    @Test
    void aFailingRestaurantServiceIsNotTreatedAsOwnership() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.getPartners(OWNER, RESTAURANT, null, null, 5))
                .isInstanceOf(RemoteServiceException.class);
        verifyNoInteractions(partnerStatsRepository);
    }

    private void givenOwnedRestaurant() {
        when(restaurantServiceClient.findRestaurant(RESTAURANT)).thenReturn(Optional.of(summary(OWNER)));
    }

    private static RestaurantSummary summary(long ownerUserId) {
        return new RestaurantSummary(RESTAURANT, ownerUserId, "Spice Hub", 1L, null);
    }

    private static PartnerTotals partner(long id, String name, long delivered, String averageMinutes) {
        return new PartnerTotals() {
            public Long getPartnerUserId() {
                return id;
            }

            public String getPartnerName() {
                return name;
            }

            public long getDelivered() {
                return delivered;
            }

            public BigDecimal getAverageMinutes() {
                return averageMinutes == null ? null : new BigDecimal(averageMinutes);
            }
        };
    }
}
