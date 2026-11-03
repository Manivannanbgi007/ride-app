package com.fooddelivery.deliveryservice.service;

import com.fooddelivery.deliveryservice.dto.response.LiveTrackingResponse;
import com.fooddelivery.deliveryservice.dto.response.TrackingResponse;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.RiderLocation;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.mapper.DeliveryMapper;
import com.fooddelivery.deliveryservice.repository.DeliveryEventRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.RiderLocationRepository;
import com.fooddelivery.deliveryservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customers following the delivery of their own order. */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final RiderLocationRepository riderLocationRepository;
    private final DeliveryMapper deliveryMapper;

    /** Another customer's order looks exactly like an order without a delivery: "not found". */
    @Transactional(readOnly = true)
    public TrackingResponse track(CallerInfo caller, Long orderId) {
        Delivery delivery = findOwnDelivery(caller, orderId);
        return deliveryMapper.toTracking(delivery, partnerOf(delivery),
                deliveryEventRepository.findByDeliveryIdOrderByIdAsc(delivery.getId()));
    }

    /**
     * The live map: the partner's real GPS position and the addresses. 404 until a partner has accepted the delivery
     * (and for a cancelled one): the page then shows that nobody is on the way yet. The position is shown only while
     * the partner of the delivery is on the way; it is null until their phone has sent one.
     */
    @Transactional(readOnly = true)
    public LiveTrackingResponse live(CallerInfo caller, Long orderId) {
        Delivery delivery = findOwnDelivery(caller, orderId);
        boolean onTheWay = RiderLocationService.ON_THE_WAY.contains(delivery.getStatus());
        if (!onTheWay && delivery.getStatus() != DeliveryStatus.DELIVERED) {
            throw new ResourceNotFoundException("No delivery partner is on the way for order " + orderId + " yet");
        }
        RiderLocation location = onTheWay
                ? riderLocationRepository.findById(delivery.getId())
                        .filter(found -> found.getPartnerUserId().equals(delivery.getPartnerUserId()))
                        .orElse(null)
                : null;
        return deliveryMapper.toLiveTracking(delivery, partnerOf(delivery), location);
    }

    private Delivery findOwnDelivery(CallerInfo caller, Long orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .filter(found -> caller.userId().equals(found.getCustomerId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No delivery for order " + orderId + " yet: it starts when the restaurant has the food ready"));
    }

    private DeliveryPartner partnerOf(Delivery delivery) {
        return delivery.getPartnerUserId() == null
                ? null
                : deliveryPartnerRepository.findById(delivery.getPartnerUserId()).orElse(null);
    }
}
