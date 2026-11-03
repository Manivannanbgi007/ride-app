package com.fooddelivery.orderservice.mapper;

import java.util.List;

import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.OrderSummaryResponse;
import com.fooddelivery.orderservice.dto.response.PriceBreakdown;
import com.fooddelivery.orderservice.entity.DeliveryAddress;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderLine;
import com.fooddelivery.orderservice.entity.OrderTimelineEntry;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order, List<OrderTimelineEntry> timeline) {
        DeliveryAddress address = order.getDeliveryAddress();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getRestaurantId(),
                order.getRestaurantName(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getLines().stream().map(OrderMapper::toLine).toList(),
                new PriceBreakdown(order.getItemsTotal(), order.getDiscountAmount(), order.getDeliveryFee(),
                        order.getTaxAmount(), order.getTotalAmount(), order.getCurrency()),
                order.getCouponCode(),
                toAddress(address),
                order.getNotes(),
                order.getCancellationReason(),
                order.getCancelledBy(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion(),
                timeline.stream().map(OrderMapper::toTimelineEntry).toList());
    }

    public OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getRestaurantId(),
                order.getRestaurantName(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getLines().stream().mapToInt(OrderLine::quantity).sum(),
                order.getCreatedAt(),
                order.getLines().stream()
                        .map(line -> new OrderSummaryResponse.Item(line.menuItemId(), line.name(), line.foodType(),
                                line.quantity()))
                        .toList());
    }

    public static OrderResponse.Address toAddress(DeliveryAddress address) {
        return new OrderResponse.Address(address.line1(), address.line2(), address.city(), address.state(),
                address.postalCode(), address.country(), address.latitude(), address.longitude());
    }

    private static OrderResponse.Line toLine(OrderLine line) {
        return new OrderResponse.Line(line.menuItemId(), line.name(), line.foodType(), line.unitPrice(),
                line.quantity(), line.lineTotal());
    }

    private static OrderResponse.TimelineEntry toTimelineEntry(OrderTimelineEntry entry) {
        return new OrderResponse.TimelineEntry(entry.getStatus(), entry.getActorType(), entry.getActorUserId(),
                entry.getNote(), entry.getCreatedAt());
    }
}
