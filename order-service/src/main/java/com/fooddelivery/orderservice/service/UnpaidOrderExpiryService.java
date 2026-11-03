package com.fooddelivery.orderservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.fooddelivery.orderservice.config.UnpaidOrderProperties;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saga timeout: an online-payment order that is still unpaid after {@code expireAfter} is cancelled by the
 * system, so it does not wait forever. The cancellation publishes OrderCancelled; if a payment still
 * succeeds afterwards, order-service rejects it and payment-service refunds it (OrderPaymentRejected).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnpaidOrderExpiryService {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderStatusTransitions orderStatusTransitions;
    private final UnpaidOrderProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** @return how many orders were cancelled */
    public int cancelExpiredOrders() {
        Instant createdBefore = clock.instant().minus(properties.expireAfter());
        List<Long> orderIds = orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PAYMENT_PENDING, createdBefore,
                PageRequest.of(0, BATCH_SIZE));

        int cancelled = 0;
        for (Long orderId : orderIds) {
            try {
                // One transaction per order: a problem with one order does not stop the others.
                Boolean done = transactionTemplate.execute(transaction -> orderRepository.findById(orderId)
                        .filter(order -> order.getStatus() == OrderStatus.PAYMENT_PENDING)
                        .map(order -> {
                            orderStatusTransitions.cancel(order, OrderStatus.CANCELLED, ActorType.SYSTEM, null,
                                    "Payment not completed within " + properties.expireAfter().toMinutes() + " minutes");
                            return true;
                        })
                        .orElse(false));
                if (Boolean.TRUE.equals(done)) {
                    cancelled++;
                    log.info("Unpaid order expired and cancelled. orderId={}", orderId);
                }
            } catch (OptimisticLockingFailureException e) {
                // The payment result arrived at the same moment: the next run sees the order's new state.
                log.info("Unpaid order changed while expiring it; checked again next time. orderId={}", orderId);
            }
        }
        return cancelled;
    }
}
