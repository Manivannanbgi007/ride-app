package com.fooddelivery.paymentservice.strategy;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** UPI payments, identified by a virtual payment address such as name@bank. */
@Component
@RequiredArgsConstructor
public class UpiPaymentStrategy implements PaymentStrategy {

    private static final Pattern UPI_ID = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,64}$");

    private final SimulatedPaymentGateway gateway;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.UPI;
    }

    @Override
    public String validateAndDescribe(PayRequest request) {
        String upiId = request.upiId() == null ? "" : request.upiId().trim();
        if (!UPI_ID.matcher(upiId).matches()) {
            throw new BusinessRuleException("A valid UPI id (for example name@bank) is required for a UPI payment");
        }
        int at = upiId.indexOf('@');
        return "UPI " + upiId.substring(0, Math.min(2, at)) + "***" + upiId.substring(at);
    }

    @Override
    public ProviderResult charge(PayRequest request, BigDecimal amount) {
        return gateway.charge(method(), request.upiId().trim(), amount);
    }

    @Override
    public ProviderResult refund(String providerReference, BigDecimal amount) {
        return gateway.refund(providerReference, amount);
    }
}
