package com.fooddelivery.paymentservice.strategy;

import java.math.BigDecimal;

import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Wallet payments (Paytm, PhonePe, ...), identified by a wallet id. */
@Component
@RequiredArgsConstructor
public class WalletPaymentStrategy implements PaymentStrategy {

    private final SimulatedPaymentGateway gateway;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.WALLET;
    }

    @Override
    public String validateAndDescribe(PayRequest request) {
        String walletId = request.walletId() == null ? "" : request.walletId().trim();
        if (walletId.length() < 4) {
            throw new BusinessRuleException("A wallet id is required for a WALLET payment");
        }
        return "WALLET ***" + walletId.substring(walletId.length() - 4);
    }

    @Override
    public ProviderResult charge(PayRequest request, BigDecimal amount) {
        return gateway.charge(method(), request.walletId().trim(), amount);
    }

    @Override
    public ProviderResult refund(String providerReference, BigDecimal amount) {
        return gateway.refund(providerReference, amount);
    }
}
