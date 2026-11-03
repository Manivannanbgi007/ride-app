package com.fooddelivery.orderservice.kafka.producer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

import com.fooddelivery.events.Topics;
import com.fooddelivery.events.order.OrderCancelled;
import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.order.OrderCreatedItem;
import com.fooddelivery.events.order.OrderDeliveryAddress;
import com.fooddelivery.events.order.OrderPaymentRejected;
import com.fooddelivery.events.order.OrderStatusChanged;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.DeliveryAddress;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Builds the order events and writes them to the transactional outbox, in the caller's transaction;
 * {@link OutboxRelay} publishes them to Kafka. All of them use the order id as message key.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final String AGGREGATE_ORDER = "ORDER";

    /** Scale of the Avro decimal fields (see the .avsc files). */
    private static final int MONEY_SCALE = 2;

    private final OutboxWriter outboxWriter;
    private final Clock clock;

    /** A new order: payment-service keeps the amount for the payment; later, notifications use it. */
    public void orderCreated(Order order) {
        OrderCreated event = OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setOrderId(order.getId())
                .setCustomerId(order.getCustomerId())
                .setRestaurantId(order.getRestaurantId())
                .setStatus(order.getStatus().name())
                .setPaymentMethod(order.getPaymentMethod().name())
                .setItems(order.getLines().stream()
                        .map(line -> OrderCreatedItem.newBuilder()
                                .setMenuItemId(line.menuItemId())
                                .setName(line.name())
                                .setQuantity(line.quantity())
                                .setUnitPrice(money(line.unitPrice()))
                                .build())
                        .toList())
                .setItemsTotal(money(order.getItemsTotal()))
                .setDeliveryFee(money(order.getDeliveryFee()))
                .setTaxAmount(money(order.getTaxAmount()))
                .setTotalAmount(money(order.getTotalAmount()))
                .setCurrency(order.getCurrency())
                // Since event-contracts 1.3.0: what delivery-service needs to bring the food
                .setRestaurantName(order.getRestaurantName())
                .setCustomerName(order.getCustomerName())
                .setCustomerPhone(order.getCustomerPhone())
                .setDeliveryAddress(deliveryAddress(order.getDeliveryAddress()))
                // Since 1.5.0: the coupon, and the promotion-service reservation it was taken with
                .setCouponCode(order.getCouponCode())
                .setDiscountAmount(money(order.getDiscountAmount()))
                .setCouponReservationId(order.getCouponReservationId())
                .build();
        append(order, Topics.ORDER_CREATED, event);
    }

    private static OrderDeliveryAddress deliveryAddress(DeliveryAddress address) {
        return OrderDeliveryAddress.newBuilder()
                .setLine1(address.line1())
                .setLine2(address.line2())
                .setCity(address.city())
                .setState(address.state())
                .setPostalCode(address.postalCode())
                .setCountry(address.country())
                .setLatitude(address.latitude() == null ? null : address.latitude().doubleValue())
                .setLongitude(address.longitude() == null ? null : address.longitude().doubleValue())
                .build();
    }

    /** The order entered a new status (previousStatus is null for its first status). */
    public void orderStatusChanged(Order order, OrderStatus previousStatus, ActorType changedBy, String note) {
        OrderStatusChanged event = OrderStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setOrderId(order.getId())
                .setCustomerId(order.getCustomerId())
                .setRestaurantId(order.getRestaurantId())
                .setRestaurantName(order.getRestaurantName())
                .setStatus(order.getStatus().name())
                .setPreviousStatus(previousStatus == null ? null : previousStatus.name())
                .setChangedBy(changedBy.name())
                .setNote(note)
                .build();
        append(order, Topics.ORDER_STATUS_CHANGED, event);
    }

    /** The order ended without delivery (CANCELLED or REJECTED); a paid order must be refunded. */
    public void orderCancelled(Order order, OrderStatus previousStatus) {
        OrderCancelled event = OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setOrderId(order.getId())
                .setCustomerId(order.getCustomerId())
                .setRestaurantId(order.getRestaurantId())
                .setFinalStatus(order.getStatus().name())
                .setPreviousStatus(previousStatus.name())
                .setCancelledBy(order.getCancelledBy().name())
                .setReason(order.getCancellationReason())
                .setPaymentMethod(order.getPaymentMethod().name())
                .setPaymentStatus(order.getPaymentStatus().name())
                .setTotalAmount(money(order.getTotalAmount()))
                .setCurrency(order.getCurrency())
                // Since 1.5.0: promotion-service gives this coupon use back
                .setCouponReservationId(order.getCouponReservationId())
                .build();
        append(order, Topics.ORDER_CANCELLED, event);
    }

    /** A payment arrived for an order that no longer accepts it: payment-service gives the money back. */
    public void orderPaymentRejected(Order order, Long paymentId, String reason) {
        OrderPaymentRejected event = OrderPaymentRejected.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(clock.instant())
                .setRequestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .setOrderId(order.getId())
                .setPaymentId(paymentId)
                .setOrderStatus(order.getStatus().name())
                .setReason(reason)
                .build();
        append(order, Topics.ORDER_PAYMENT_REJECTED, event);
    }

    private void append(Order order, String topic, SpecificRecord event) {
        outboxWriter.append(AGGREGATE_ORDER, order.getId(), topic, String.valueOf(order.getId()), event);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
