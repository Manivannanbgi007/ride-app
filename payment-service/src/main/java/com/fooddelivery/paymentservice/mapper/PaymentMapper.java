package com.fooddelivery.paymentservice.mapper;

import java.util.List;

import com.fooddelivery.paymentservice.dto.response.PaymentDetailsResponse;
import com.fooddelivery.paymentservice.dto.response.PaymentResponse;
import com.fooddelivery.paymentservice.dto.response.RefundResponse;
import com.fooddelivery.paymentservice.entity.OutboxEvent;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.Refund;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getInstrument(),
                payment.getProviderReference(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getProcessingStartedAt(),
                payment.getCompletedAt(),
                payment.getProvider(),
                payment.getProviderOrderId());
    }

    public RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getRequestedByType(),
                refund.getRequestedByUserId(),
                refund.getProviderReference(),
                refund.getFailureReason(),
                refund.getRequestedAt(),
                refund.getCompletedAt());
    }

    public PaymentDetailsResponse toDetails(Payment payment, List<Refund> refunds, List<OutboxEvent> events) {
        return new PaymentDetailsResponse(
                toResponse(payment),
                refunds.stream().map(this::toResponse).toList(),
                events.stream()
                        .map(event -> new PaymentDetailsResponse.PublishedEvent(event.getEventId(), event.eventName(),
                                event.getTopic(), event.getPublishedAt() == null ? "PENDING" : "PUBLISHED",
                                event.getCreatedAt(), event.getPublishedAt(), event.getAttempts(), event.getLastError()))
                        .toList());
    }
}
