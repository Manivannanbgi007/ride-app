package com.fooddelivery.orderservice.service;

import com.fooddelivery.events.delivery.DeliveryStatusChanged;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import com.fooddelivery.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order side of deliveries (DeliveryStatusChanged from delivery-service), the last steps of the saga:
 * picked up means OUT_FOR_DELIVERY, delivered means DELIVERED (and a cash-on-delivery order is then paid).
 * Assignments only add a timeline entry. Repeated events are skipped ({@link ProcessedEventGuard}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDeliveryService {

    private final OrderRepository orderRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final ProcessedEventGuard processedEventGuard;

    @Transactional
    public void applyDeliveryStatusChanged(DeliveryStatusChanged event) {
        if (!processedEventGuard.firstDelivery(event.getEventId(), "DeliveryStatusChanged")) {
            return;
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Delivery event for an unknown order ignored. orderId={} deliveryId={}",
                    event.getOrderId(), event.getDeliveryId());
            return;
        }
        String partner = event.getPartnerName() == null ? "a delivery partner" : event.getPartnerName();
        switch (event.getStatus()) {
            case "ASSIGNED" -> orderStatusTransitions.recordEvent(order, ActorType.SYSTEM, null,
                    "Delivery assigned to " + partner);
            case "PICKED_UP" -> pickedUp(order, event, partner);
            case "DELIVERED" -> delivered(order, event, partner);
            default -> {
                // PENDING, ACCEPTED, CANCELLED: nothing changes on the order
            }
        }
    }

    private void pickedUp(Order order, DeliveryStatusChanged event, String partner) {
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            logIgnored(order, event);
            return;
        }
        orderStatusTransitions.changeStatus(order, OrderStatus.OUT_FOR_DELIVERY, ActorType.DELIVERY_PARTNER,
                event.getPartnerUserId(), "Picked up by " + partner);
    }

    private void delivered(Order order, DeliveryStatusChanged event, String partner) {
        if (order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            // The pickup was not reported: the food is delivered, so it was picked up.
            orderStatusTransitions.changeStatus(order, OrderStatus.OUT_FOR_DELIVERY, ActorType.DELIVERY_PARTNER,
                    event.getPartnerUserId(), "Picked up by " + partner);
        }
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            logIgnored(order, event);
            return;
        }
        orderStatusTransitions.changeStatus(order, OrderStatus.DELIVERED, ActorType.DELIVERY_PARTNER,
                event.getPartnerUserId(), "Delivered by " + partner);
        if (order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY && order.getPaymentStatus() == PaymentStatus.PENDING) {
            order.setPaymentStatus(PaymentStatus.PAID);
            orderStatusTransitions.recordEvent(order, ActorType.DELIVERY_PARTNER, event.getPartnerUserId(),
                    "Cash collected: " + order.getCurrency() + " " + order.getTotalAmount());
        }
        log.info("Order delivered. orderId={} partnerUserId={}", order.getId(), event.getPartnerUserId());
    }

    /** For example a pickup reported for an order cancelled at the same moment: the order's state wins. */
    private static void logIgnored(Order order, DeliveryStatusChanged event) {
        log.warn("Delivery event does not fit the order's status, ignored. orderId={} orderStatus={} deliveryStatus={}",
                order.getId(), order.getStatus(), event.getStatus());
    }
}
