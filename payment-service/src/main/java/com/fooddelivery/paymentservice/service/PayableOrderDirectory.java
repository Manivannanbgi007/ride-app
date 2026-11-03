package com.fooddelivery.paymentservice.service;

import java.util.Optional;

import com.fooddelivery.paymentservice.client.OrderServiceClient;
import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.entity.PayableOrder;
import com.fooddelivery.paymentservice.exception.RemoteServiceException;
import com.fooddelivery.paymentservice.repository.PayableOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Where payment-service finds the order a customer wants to pay: its local copy first (fed by
 * OrderCreated / OrderCancelled events), order-service only for an order whose OrderCreated event has
 * not arrived yet (a customer who pays within a second of checkout). The copy is written by events only.
 */
@Service
@RequiredArgsConstructor
public class PayableOrderDirectory {

    private final PayableOrderRepository payableOrderRepository;
    private final OrderServiceClient orderServiceClient;

    /**
     * @return the order, or empty if it does not exist
     * @throws RemoteServiceException if it is not known locally and order-service cannot be reached
     */
    public Optional<OrderSummary> findOrder(Long orderId) {
        Optional<PayableOrder> copy = payableOrderRepository.findById(orderId);
        if (copy.isPresent()) {
            return copy.map(PayableOrderDirectory::toSummary);
        }
        return orderServiceClient.findOrder(orderId);
    }

    private static OrderSummary toSummary(PayableOrder order) {
        return new OrderSummary(order.getOrderId(), order.getCustomerId(), order.getRestaurantId(), order.getStatus(),
                order.getPaymentMethod(), null, order.getTotalAmount(), order.getCurrency());
    }
}
