package com.fooddelivery.deliveryservice.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import com.fooddelivery.deliveryservice.entity.Address;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryPartner;
import com.fooddelivery.deliveryservice.entity.OrderDetails;
import com.fooddelivery.deliveryservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.OrderDetailsRepository;
import com.fooddelivery.deliveryservice.repository.PickupPointRepository;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderDeliveryAddress;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.events.restaurant.RestaurantAddress;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order and restaurant events, turned into deliveries:
 * <ul>
 *   <li>OrderCreated: keep the customer and drop-off address (the order may never be paid, so no delivery yet);</li>
 *   <li>OrderStatusChanged READY_FOR_PICKUP: the food is ready, the delivery starts (PENDING);</li>
 *   <li>OrderCancelled: a delivery that is not finished is cancelled and its partner freed;</li>
 *   <li>RestaurantChanged: keep the pickup address.</li>
 * </ul>
 * Events of different topics can arrive in any order: whatever arrives first, the delivery ends up complete
 * (missing details are filled in later, see {@link DeliveryDetailsService}). Repeated events are skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventService {

    static final String READY_FOR_PICKUP = "READY_FOR_PICKUP";

    private final OrderDetailsRepository orderDetailsRepository;
    private final PickupPointRepository pickupPointRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryWorkflow deliveryWorkflow;
    private final DeliveryDetailsService deliveryDetailsService;
    private final ProcessedEventGuard processedEventGuard;
    private final Clock clock;

    @Transactional
    public void onOrderCreated(OrderCreated event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderCreated")) {
            return;
        }
        if (event.getDeliveryAddress() == null) {
            // Published by an order-service older than event-contracts 1.3.0: the job asks order-service instead
            log.warn("OrderCreated without a delivery address. orderId={}", event.getOrderId());
            return;
        }
        if (orderDetailsRepository.existsById(event.getOrderId())) {
            return;
        }
        OrderDetails details = orderDetailsRepository.save(OrderDetails.of(event.getOrderId(), event.getCustomerId(),
                event.getCustomerName(), event.getCustomerPhone(), event.getRestaurantId(), event.getRestaurantName(),
                dropoffAddress(event.getDeliveryAddress()), event.getPaymentMethod(), event.getTotalAmount(),
                event.getCurrency(), clock.instant()));
        // The food was reported ready before this event arrived: complete that delivery now
        deliveryRepository.findByOrderId(event.getOrderId())
                .filter(delivery -> !delivery.hasOrderDetails())
                .ifPresent(delivery -> deliveryDetailsService.applyOrderDetails(delivery, details));
    }

    @Transactional
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!READY_FOR_PICKUP.equals(event.getStatus())
                || !processedEventGuard.firstDelivery(event.getEventId(), "OrderStatusChanged")) {
            return;
        }
        // Also present when the order was cancelled first (see onOrderCancelled)
        if (deliveryRepository.findByOrderId(event.getOrderId()).isPresent()) {
            return;
        }
        Delivery delivery = newDelivery(event.getOrderId(), event.getCustomerId(), event.getRestaurantId(),
                event.getRestaurantName(), event.getOccurredAt());
        deliveryWorkflow.create(delivery, "Food ready at " + event.getRestaurantName());
    }

    @Transactional
    public void onOrderCancelled(OrderCancelled event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "OrderCancelled")) {
            return;
        }
        String reason = "Order " + event.getFinalStatus().toLowerCase()
                + (event.getReason() == null ? "" : ": " + event.getReason());
        Delivery delivery = deliveryRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (delivery == null) {
            if (READY_FOR_PICKUP.equals(event.getPreviousStatus())) {
                // The "food ready" event is still on its way (another topic): record the delivery as cancelled
                // now, so that event finds it and no partner is ever sent.
                Delivery cancelled = deliveryWorkflow.create(newDelivery(event.getOrderId(), event.getCustomerId(),
                        event.getRestaurantId(), null, event.getOccurredAt()), "Food ready");
                deliveryWorkflow.cancel(cancelled, null, reason);
            }
            return;
        }
        if (delivery.getStatus().isFinished()) {
            return;
        }
        DeliveryPartner partner = delivery.getStatus().hasPartner()
                ? deliveryPartnerRepository.findById(delivery.getPartnerUserId()).orElse(null)
                : null;
        deliveryWorkflow.cancel(delivery, partner, reason);
    }

    /** A new delivery with every detail this service already has a copy of. */
    private Delivery newDelivery(Long orderId, Long customerId, Long restaurantId, String restaurantName,
                                 Instant readyAt) {
        Delivery delivery = new Delivery();
        delivery.setOrderId(orderId);
        delivery.setCustomerId(customerId);
        delivery.setRestaurantId(restaurantId);
        delivery.setRestaurantName(restaurantName);
        delivery.setReadyAt(readyAt);
        orderDetailsRepository.findById(orderId)
                .ifPresent(details -> deliveryDetailsService.copyOrderDetails(delivery, details));
        pickupPointRepository.findById(restaurantId).ifPresent(pickup -> {
            delivery.setPickupAddress(pickup.getAddress());
            if (delivery.getRestaurantName() == null) {
                delivery.setRestaurantName(pickup.getName());
            }
        });
        return delivery;
    }

    /** Pickup addresses: the newest restaurant version wins (older or repeated events change nothing). */
    @Transactional
    public void onRestaurantChanged(RestaurantChanged event) {
        RestaurantAddress address = event.getAddress();
        if (address == null) {
            return;
        }
        pickupPointRepository.upsertIfNewer(event.getRestaurantId(), event.getName(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getState(), address.getPostalCode(),
                address.getCountry(), decimal(address.getLatitude()), decimal(address.getLongitude()),
                event.getVersion());
    }

    private static Address dropoffAddress(OrderDeliveryAddress address) {
        return new Address(address.getLine1(), address.getLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry(), decimal(address.getLatitude()),
                decimal(address.getLongitude()));
    }

    private static BigDecimal decimal(Double coordinate) {
        return coordinate == null ? null : BigDecimal.valueOf(coordinate);
    }
}
