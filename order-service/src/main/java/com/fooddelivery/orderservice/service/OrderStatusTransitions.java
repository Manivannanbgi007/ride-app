package com.fooddelivery.orderservice.service;

import java.time.Clock;

import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.OrderTimelineEntry;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place where an order's status changes. Every change is checked against
 * {@link OrderStatus} rules, written to the order timeline and published as OrderStatusChanged
 * (transactional outbox), all in the same transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusTransitions {

    private final OrderTimelineRepository orderTimelineRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetrics orderMetrics;
    private final Clock clock;

    /** Records the first status of a new (already saved) order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitialStatus(Order order, ActorType actor, Long actorUserId, String note) {
        orderTimelineRepository.save(new OrderTimelineEntry(order.getId(), order.getStatus(), actor, actorUserId,
                note, clock.instant()));
        orderEventPublisher.orderStatusChanged(order, null, actor, note);
        orderMetrics.placed(order);
        orderMetrics.statusChanged(order.getStatus(), actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void changeStatus(Order order, OrderStatus newStatus, ActorType actor, Long actorUserId, String note) {
        applyStatusChange(order, newStatus, actor, actorUserId, note);
    }

    /** The status change itself; callers are already in a transaction (no self-call through the proxy). */
    private void applyStatusChange(Order order, OrderStatus newStatus, ActorType actor, Long actorUserId, String note) {
        OrderStatus oldStatus = order.getStatus();
        if (!oldStatus.canChangeTo(newStatus)) {
            throw new ConflictException("Order " + order.getId() + " cannot change from " + oldStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        orderTimelineRepository.save(new OrderTimelineEntry(order.getId(), newStatus, actor, actorUserId, note,
                clock.instant()));
        orderEventPublisher.orderStatusChanged(order, oldStatus, actor, note);
        orderMetrics.statusChanged(newStatus, actor);
        log.info("Order status changed. orderId={} oldStatus={} newStatus={} actor={} actorUserId={}",
                order.getId(), oldStatus, newStatus, actor, actorUserId);
    }

    /** A timeline entry without a status change, for example "Payment failed: card declined". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(Order order, ActorType actor, Long actorUserId, String note) {
        orderTimelineRepository.save(new OrderTimelineEntry(order.getId(), order.getStatus(), actor, actorUserId,
                note, clock.instant()));
    }

    /**
     * Cancellation also stores who cancelled and why on the order itself, and publishes
     * OrderCancelled (after commit) so that, for example, payment-service refunds a paid order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Order order, OrderStatus cancelledStatus, ActorType actor, Long actorUserId, String reason) {
        OrderStatus previousStatus = order.getStatus();
        applyStatusChange(order, cancelledStatus, actor, actorUserId, reason);
        order.setCancellationReason(reason);
        order.setCancelledBy(actor);
        orderEventPublisher.orderCancelled(order, previousStatus);
    }
}
