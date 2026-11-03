package com.fooddelivery.deliveryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.fooddelivery.deliveryservice.dto.request.CompleteDeliveryRequest;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.exception.BusinessRuleException;
import com.fooddelivery.deliveryservice.exception.ConflictException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryEventRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartnerDeliveryServiceTest {

    private static final long PARTNER = 9L;
    private static final CallerInfo CALLER = new CallerInfo(PARTNER, null, "127.0.0.1", "test");

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryEventRepository deliveryEventRepository;
    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock
    private DeliveryWorkflow deliveryWorkflow;

    private PartnerDeliveryService partnerDeliveryService;

    @BeforeEach
    void setUp() {
        partnerDeliveryService = new PartnerDeliveryService(deliveryRepository, deliveryEventRepository,
                deliveryPartnerRepository, deliveryWorkflow, new DeliveryMapper(), Clock.systemUTC());
    }

    @Test
    void cashOnDeliveryNeedsTheExactAmount() {
        Delivery delivery = givenDelivery(DeliveryStatus.PICKED_UP, new BigDecimal("353.95"));

        var noCash = new CompleteDeliveryRequest(null);
        assertThatThrownBy(() -> partnerDeliveryService.deliver(CALLER, 40L, noCash))
                .isInstanceOf(BusinessRuleException.class);
        var wrongAmount = new CompleteDeliveryRequest(new BigDecimal("350.00"));
        assertThatThrownBy(() -> partnerDeliveryService.deliver(CALLER, 40L, wrongAmount))
                .isInstanceOf(BusinessRuleException.class);
        verify(deliveryWorkflow, never()).deliver(any(), any(), any());

        DeliveryPartner partner = givenPartner();
        when(deliveryRepository.saveAndFlush(delivery)).thenReturn(delivery);
        partnerDeliveryService.deliver(CALLER, 40L, new CompleteDeliveryRequest(new BigDecimal("353.950")));
        verify(deliveryWorkflow).deliver(delivery, partner, new BigDecimal("353.950"));
    }

    @Test
    void anOnlinePaidOrderHasNoCashToCollect() {
        givenDelivery(DeliveryStatus.PICKED_UP, new BigDecimal("0.00"));

        var completeDeliveryRequest = new CompleteDeliveryRequest(new BigDecimal("10.00"));
        assertThatThrownBy(() -> partnerDeliveryService.deliver(CALLER, 40L, completeDeliveryRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("paid online");
    }

    @Test
    void anOfferCanOnlyBeDeclinedBeforeItIsAccepted() {
        givenDelivery(DeliveryStatus.ACCEPTED, new BigDecimal("0.00"));

        assertThatThrownBy(() -> partnerDeliveryService.decline(CALLER, 40L, null))
                .isInstanceOf(ConflictException.class);
        verify(deliveryWorkflow, never()).unassign(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anotherPartnersDeliveryIsNotFound() {
        when(deliveryRepository.findByIdAndPartnerUserId(41L, PARTNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerDeliveryService.accept(CALLER, 41L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Delivery givenDelivery(DeliveryStatus status, BigDecimal cashToCollect) {
        Delivery delivery = new Delivery();
        delivery.setId(40L);
        delivery.setOrderId(500L);
        delivery.setStatus(status);
        delivery.setPartnerUserId(PARTNER);
        delivery.setCashToCollect(cashToCollect);
        delivery.setCurrency("INR");
        delivery.setReadyAt(Instant.now());
        when(deliveryRepository.findByIdAndPartnerUserId(40L, PARTNER)).thenReturn(Optional.of(delivery));
        return delivery;
    }

    private DeliveryPartner givenPartner() {
        DeliveryPartner partner = DeliveryPartner.register(PARTNER, "Ravi Kumar", "+919800000000");
        when(deliveryPartnerRepository.findById(PARTNER)).thenReturn(Optional.of(partner));
        return partner;
    }

    @Test
    void theMapperShowsTheCurrentStep() {
        Delivery delivery = new Delivery();
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setReadyAt(Instant.now().minusSeconds(600));
        assertThat(new DeliveryMapper().toSummary(delivery, Instant.now()).minutesSinceReady()).isEqualTo(10);
    }
}
