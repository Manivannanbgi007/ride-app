package com.fooddelivery.deliveryservice.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.deliveryservice.client.OrderServiceClient;
import com.fooddelivery.deliveryservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.deliveryservice.client.RemoteRecords.RemoteAddress;
import com.fooddelivery.deliveryservice.client.RestaurantServiceClient;
import com.fooddelivery.deliveryservice.client.RestaurantServiceClient.RestaurantSummary;
import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.entity.Delivery;
import com.fooddelivery.deliveryservice.entity.DeliveryStatus;
import com.fooddelivery.deliveryservice.entity.OrderDetails;
import com.fooddelivery.deliveryservice.entity.PickupPoint;
import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.OrderDetailsRepository;
import com.fooddelivery.deliveryservice.repository.PickupPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Completes deliveries whose order or restaurant details have not arrived as events: first from this service's
 * own copies, otherwise from order-service and restaurant-service. Remote calls run outside any transaction;
 * if a service is down, the delivery simply waits for the next run (it is never lost).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDetailsService {

    private final DeliveryRepository deliveryRepository;
    private final OrderDetailsRepository orderDetailsRepository;
    private final PickupPointRepository pickupPointRepository;
    private final OrderServiceClient orderServiceClient;
    private final RestaurantServiceClient restaurantServiceClient;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Copies what the order says about its delivery (customer, drop-off, cash to collect). */
    public void copyOrderDetails(Delivery delivery, OrderDetails details) {
        delivery.setCustomerId(details.getCustomerId());
        delivery.setCustomerName(details.getCustomerName());
        delivery.setCustomerPhone(details.getCustomerPhone());
        if (delivery.getRestaurantName() == null) {
            delivery.setRestaurantName(details.getRestaurantName());
        }
        delivery.setDropoffAddress(details.getDropoffAddress());
        delivery.setPaymentMethod(details.getPaymentMethod());
        delivery.setCashToCollect(details.cashToCollect());
        delivery.setCurrency(details.getCurrency());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyOrderDetails(Delivery delivery, OrderDetails details) {
        completeFromOrder(delivery, details);
    }

    /** Callers are already in a transaction (no self-call through the proxy). */
    private void completeFromOrder(Delivery delivery, OrderDetails details) {
        copyOrderDetails(delivery, details);
        log.info("Delivery details completed from the order. deliveryId={} orderId={}",
                delivery.getId(), delivery.getOrderId());
    }

    /** Fills in waiting deliveries that are still missing details. Called by the assignment job. */
    public void completeWaitingDeliveries() {
        List<Long> ids = deliveryRepository.findIdsMissingDetails(DeliveryStatus.PENDING,
                PageRequest.of(0, appProperties.assignment().batchSize()));
        for (Long id : ids) {
            try {
                complete(id);
            } catch (RemoteServiceException e) {
                log.warn("Delivery details not available yet, trying again later. deliveryId={} reason={}",
                        id, e.getMessage());
            }
        }
    }

    private void complete(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        Optional<OrderDetails> orderDetails = delivery.hasOrderDetails()
                ? Optional.empty()
                : orderDetailsRepository.findById(delivery.getOrderId()).or(() -> fetchOrder(delivery.getOrderId()));
        Optional<PickupPoint> pickupPoint = delivery.getPickupAddress() != null
                ? Optional.empty()
                : pickupPointRepository.findById(delivery.getRestaurantId())
                        .or(() -> fetchRestaurant(delivery.getRestaurantId()));

        transactionTemplate.executeWithoutResult(status -> {
            Delivery current = deliveryRepository.findById(deliveryId).orElseThrow();
            orderDetails.filter(details -> !current.hasOrderDetails())
                    .ifPresent(details -> completeFromOrder(current, details));
            pickupPoint.filter(pickup -> current.getPickupAddress() == null).ifPresent(pickup -> {
                current.setPickupAddress(pickup.getAddress());
                if (current.getRestaurantName() == null) {
                    current.setRestaurantName(pickup.getName());
                }
                log.info("Delivery pickup address completed. deliveryId={} restaurantId={}",
                        current.getId(), current.getRestaurantId());
            });
        });
    }

    /** Asks order-service and keeps the answer, like an OrderCreated event. */
    private Optional<OrderDetails> fetchOrder(Long orderId) {
        Optional<OrderSummary> order = orderServiceClient.findOrder(orderId);
        if (order.isEmpty() || order.get().deliveryAddress() == null) {
            log.warn("order-service does not know the order of a delivery. orderId={}", orderId);
            return Optional.empty();
        }
        OrderSummary summary = order.get();
        OrderDetails details = OrderDetails.of(summary.id(), summary.customerId(), summary.customerName(),
                summary.customerPhone(), summary.restaurantId(), summary.restaurantName(),
                summary.deliveryAddress().toAddress(), summary.paymentMethod(), summary.totalAmount(),
                summary.currency(), clock.instant());
        return Optional.of(transactionTemplate.execute(status -> orderDetailsRepository.findById(orderId)
                .orElseGet(() -> orderDetailsRepository.save(details))));
    }

    /** Asks restaurant-service and keeps the answer, like a RestaurantChanged event. */
    private Optional<PickupPoint> fetchRestaurant(Long restaurantId) {
        Optional<RestaurantSummary> restaurant = restaurantServiceClient.findRestaurant(restaurantId);
        if (restaurant.isEmpty() || restaurant.get().address() == null) {
            log.warn("restaurant-service does not know the restaurant of a delivery. restaurantId={}", restaurantId);
            return Optional.empty();
        }
        RestaurantSummary summary = restaurant.get();
        RemoteAddress address = summary.address();
        pickupPointRepository.upsertIfNewer(summary.id(), summary.name(), address.line1(), address.line2(),
                address.city(), address.state(), address.postalCode(), address.country(), address.latitude(),
                address.longitude(), summary.version());
        return pickupPointRepository.findById(restaurantId);
    }
}
