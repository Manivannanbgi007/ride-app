package com.fooddelivery.deliveryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fooddelivery.deliveryservice.client.DeliveryPartnerServiceClient;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.PartnerStatus;
import com.fooddelivery.deliveryservice.entity.VehicleType;
import com.fooddelivery.deliveryservice.kafka.producer.PartnerStatusPublisher;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PartnerRosterServiceTest {

    private static final long PARTNER = 9L;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryWorkflow deliveryWorkflow;
    @Mock
    private DeliveryPartnerServiceClient deliveryPartnerServiceClient;
    @Mock
    private PartnerStatusPublisher partnerStatusPublisher;
    @Mock
    private ProcessedEventGuard processedEventGuard;

    private PartnerRosterService partnerRosterService;

    @BeforeEach
    void setUp() {
        partnerRosterService = new PartnerRosterService(deliveryPartnerRepository, deliveryRepository, deliveryWorkflow,
                deliveryPartnerServiceClient, partnerStatusPublisher, processedEventGuard, new DeliveryMapper(),
                new TransactionTemplate());
        when(processedEventGuard.firstDelivery(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void aPartnerOnAnAcceptedDeliveryStaysOnItWhenSuspendedAndReactivated() {
        DeliveryPartner partner = givenPartner(PartnerStatus.BUSY, 2L);
        partner.setCurrentDeliveryId(40L);
        Delivery accepted = new Delivery();
        accepted.setId(40L);
        accepted.setStatus(DeliveryStatus.ACCEPTED);
        accepted.setPartnerUserId(PARTNER);
        when(deliveryRepository.findById(40L)).thenReturn(Optional.of(accepted));

        partnerRosterService.onPartnerChanged(event(3, "SUSPENDED", "Fraud check", "SCOOTER"));
        assertThat(partner.isSuspended()).isTrue();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.BUSY);
        verify(deliveryWorkflow, never()).unassign(any(), any(), any(), any(), any(), any(), any());

        partnerRosterService.onPartnerChanged(event(4, "ACTIVE", null, "SCOOTER"));
        assertThat(partner.isSuspended()).isFalse();
        assertThat(partner.getSuspensionReason()).isNull();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.BUSY);
        verify(partnerStatusPublisher, never()).statusChanged(any());
    }

    @Test
    void aPartnerFromBeforeTheMoveKeepsTheirDataWhenRegisteredAfresh() {
        // On the dispatch list before delivery-partner-service existed: vehicle, city, suspended by an admin
        DeliveryPartner partner = DeliveryPartner.register(PARTNER, "Ravi Kumar", "+919800000000");
        partner.setVehicleType(VehicleType.SCOOTER);
        partner.setVehicleNumber("KA01AB1234");
        partner.setCity("Mysuru");
        partner.setStatus(PartnerStatus.SUSPENDED);
        partner.setSuspended(true);
        partner.setSuspensionReason("Fraud check");
        when(deliveryPartnerRepository.lockById(PARTNER)).thenReturn(Optional.of(partner));
        when(deliveryPartnerRepository.saveAndFlush(partner)).thenReturn(partner);

        // Registered there afresh (UserRegistered replayed before the import): no vehicle, active
        DeliveryPartnerChanged registered = event(0, "ACTIVE", null, null);
        registered.setCity(null);
        registered.setVehicleNumber(null);
        partnerRosterService.onPartnerChanged(registered);

        assertThat(partner.getProfileVersion()).isZero();
        assertThat(partner.getCity()).isEqualTo("Mysuru");
        assertThat(partner.isSuspended()).isTrue();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.SUSPENDED);

        // The import completes the record there (version 1): from now on the owner's data applies
        partnerRosterService.onPartnerChanged(event(1, "ACTIVE", null, "CAR"));
        assertThat(partner.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(partner.isSuspended()).isFalse();
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.OFFLINE);
    }

    @Test
    void anOlderEventChangesNothing() {
        DeliveryPartner partner = givenPartner(PartnerStatus.AVAILABLE, 5L);

        partnerRosterService.onPartnerChanged(event(5, "SUSPENDED", "Fraud check", "CAR"));

        assertThat(partner.isSuspended()).isFalse();
        assertThat(partner.getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
        verify(deliveryPartnerRepository, never()).saveAndFlush(any());
    }

    @Test
    void aVehicleTypeThisVersionDoesNotKnowIsLeftEmpty() {
        when(deliveryPartnerRepository.lockById(PARTNER)).thenReturn(Optional.empty());
        when(deliveryPartnerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        partnerRosterService.onPartnerChanged(event(0, "ACTIVE", null, "HOVERCRAFT"));

        verify(deliveryPartnerRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getVehicleType() == null && "Bengaluru".equals(saved.getCity()) && !saved.isReadyToWork()));
    }

    private DeliveryPartner givenPartner(PartnerStatus status, long profileVersion) {
        DeliveryPartner partner = DeliveryPartner.register(PARTNER, "Ravi Kumar", "+919800000000");
        partner.applyProfile("Ravi Kumar", "+919800000000", VehicleType.MOTORCYCLE, "KA01AB1234", "Bengaluru",
                profileVersion);
        partner.setStatus(status);
        when(deliveryPartnerRepository.lockById(PARTNER)).thenReturn(Optional.of(partner));
        org.mockito.Mockito.lenient().when(deliveryPartnerRepository.saveAndFlush(partner)).thenReturn(partner);
        return partner;
    }

    private static DeliveryPartnerChanged event(long version, String accountStatus, String reason, String vehicleType) {
        return DeliveryPartnerChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setChangeType("SUSPENDED".equals(accountStatus) ? "SUSPENDED" : "PROFILE_UPDATED")
                .setPartnerUserId(PARTNER).setVersion(version).setFullName("Ravi Kumar").setPhone("+919800000000")
                .setVehicleType(vehicleType).setVehicleNumber("KA01AB1234").setCity("Bengaluru")
                .setAccountStatus(accountStatus).setSuspensionReason(reason)
                .build();
    }
}
