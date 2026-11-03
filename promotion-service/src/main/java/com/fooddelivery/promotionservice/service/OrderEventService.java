package com.fooddelivery.promotionservice.service;

import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What became of the order a coupon use was reserved for: created (the use belongs to it), cancelled or rejected
 * (the use is given back), delivered (the use is final). Events without a coupon are ignored; repeated events
 * are skipped ({@link ProcessedEventGuard}). The two topics can arrive in any order (see CouponRedemptionService).
 */
@Service
@RequiredArgsConstructor
public class OrderEventService {

    private static final String DELIVERED = "DELIVERED";

    private final CouponRedemptionService couponRedemptionService;
    private final ProcessedEventGuard processedEventGuard;

    @Transactional
    public void onOrderCreated(OrderCreated event) {
        if (event.getCouponReservationId() == null
                || !processedEventGuard.firstDelivery(event.getEventId(), "OrderCreated")) {
            return;
        }
        couponRedemptionService.applyToOrder(event.getCouponReservationId(), event.getOrderId());
    }

    @Transactional
    public void onOrderCancelled(OrderCancelled event) {
        if (event.getCouponReservationId() == null
                || !processedEventGuard.firstDelivery(event.getEventId(), "OrderCancelled")) {
            return;
        }
        String reason = "Order " + event.getFinalStatus().toLowerCase()
                + (event.getReason() == null ? "" : ": " + event.getReason());
        couponRedemptionService.releaseForCancelledOrder(event.getCouponReservationId(), event.getOrderId(), reason);
    }

    @Transactional
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!DELIVERED.equals(event.getStatus())
                || !processedEventGuard.firstDelivery(event.getEventId(), "OrderStatusChanged")) {
            return;
        }
        couponRedemptionService.redeem(event.getOrderId());
    }
}
