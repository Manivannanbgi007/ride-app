package com.fooddelivery.deliveryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.request.RiderLocationRequest;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.RiderLocation;
import com.fooddelivery.deliveryservice.entity.Role;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.RiderLocationRepository;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiderLocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final CallerInfo PARTNER = new CallerInfo(31L, Role.DELIVERY_PARTNER, "10.0.0.1", "req-1");
    private static final RiderLocationRequest POSITION = new RiderLocationRequest(12.9715987, 77.5945661, 12, 90);

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private RiderLocationRepository riderLocationRepository;

    private RiderLocationService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("Asia/Kolkata", null, null, null, null, null,
                new AppProperties.LiveLocation(Duration.ofSeconds(2), Duration.ofMinutes(1)));
        service = new RiderLocationService(deliveryRepository, riderLocationRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theFirstPositionOfADeliveryOnItsWayIsStoredRoundedToSixDecimals() {
        givenDelivery(DeliveryStatus.ACCEPTED);
        when(riderLocationRepository.findById(7L)).thenReturn(Optional.empty());

        service.share(PARTNER, 7L, POSITION);

        RiderLocation saved = savedLocation();
        assertThat(saved.getDeliveryId()).isEqualTo(7L);
        assertThat(saved.getPartnerUserId()).isEqualTo(31L);
        assertThat(saved.getLatitude()).isEqualByComparingTo("12.971599");
        assertThat(saved.getLongitude()).isEqualByComparingTo("77.594566");
        assertThat(saved.getAccuracyMeters()).isEqualTo(12);
        assertThat(saved.getHeadingDeg()).isEqualTo((short) 90);
        assertThat(saved.getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    void aNewerPositionReplacesTheOldOne() {
        givenDelivery(DeliveryStatus.PICKED_UP);
        RiderLocation stored = stored(31L, NOW.minusSeconds(5));
        when(riderLocationRepository.findById(7L)).thenReturn(Optional.of(stored));

        service.share(PARTNER, 7L, new RiderLocationRequest(13.0, 77.6, null, null));

        assertThat(savedLocation()).isSameAs(stored);
        assertThat(stored.getLatitude()).isEqualByComparingTo("13.000000");
        assertThat(stored.getHeadingDeg()).isNull();
        assertThat(stored.getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    void positionsArrivingFasterThanTheMinimumIntervalAreIgnored() {
        givenDelivery(DeliveryStatus.ACCEPTED);
        when(riderLocationRepository.findById(7L)).thenReturn(Optional.of(stored(31L, NOW.minusMillis(500))));

        service.share(PARTNER, 7L, POSITION);

        verify(riderLocationRepository, never()).save(any());
    }

    @Test
    void aPositionLeftByThePreviousPartnerIsReplacedNotKept() {
        givenDelivery(DeliveryStatus.ACCEPTED);
        when(riderLocationRepository.findById(7L)).thenReturn(Optional.of(stored(99L, NOW.minusMillis(500))));

        service.share(PARTNER, 7L, POSITION);

        assertThat(savedLocation().getPartnerUserId()).isEqualTo(31L);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PENDING", "ASSIGNED", "DELIVERED", "CANCELLED"})
    void onlyADeliveryOnItsWaySharesAPosition(DeliveryStatus status) {
        givenDelivery(status);

        assertThatThrownBy(() -> service.share(PARTNER, 7L, POSITION))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only while you are on the way");
        verify(riderLocationRepository, never()).save(any());
    }

    @Test
    void anotherPartnersDeliveryIsNotFound() {
        when(deliveryRepository.findByIdAndPartnerUserId(7L, 31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.share(PARTNER, 7L, POSITION)).isInstanceOf(ResourceNotFoundException.class);
        verify(riderLocationRepository, never()).save(any());
    }

    @Test
    void positionsOfDeliveriesNoLongerOnTheirWayAreDeleted() {
        when(riderLocationRepository.findNotOnTheWay(RiderLocationService.ON_THE_WAY)).thenReturn(List.of(7L, 8L));

        service.deleteFinished();

        verify(riderLocationRepository).deleteAllByIdInBatch(List.of(7L, 8L));
    }

    @Test
    void nothingToDeleteMeansNoDelete() {
        when(riderLocationRepository.findNotOnTheWay(RiderLocationService.ON_THE_WAY)).thenReturn(List.of());

        service.deleteFinished();

        verify(riderLocationRepository, never()).deleteAllByIdInBatch(anyCollection());
    }

    private void givenDelivery(DeliveryStatus status) {
        Delivery delivery = mock(Delivery.class);
        when(delivery.getStatus()).thenReturn(status);
        when(deliveryRepository.findByIdAndPartnerUserId(7L, 31L)).thenReturn(Optional.of(delivery));
    }

    private static RiderLocation stored(Long partnerUserId, Instant receivedAt) {
        RiderLocation location = new RiderLocation(7L, partnerUserId);
        location.moveTo(new BigDecimal("12.900000"), new BigDecimal("77.500000"), 20, (short) 45, receivedAt);
        return location;
    }

    private RiderLocation savedLocation() {
        ArgumentCaptor<RiderLocation> saved = ArgumentCaptor.forClass(RiderLocation.class);
        verify(riderLocationRepository).save(saved.capture());
        return saved.getValue();
    }
}
