package com.fooddelivery.paymentservice.strategy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real payment provider (Razorpay, Stripe, a bank...). Deterministic test data:
 * <ul>
 *   <li>card 4000000000000002: declined; card 4000000000009995: insufficient funds</li>
 *   <li>UPI fail@upi: declined; UPI refundfail@upi: payment works, refunds fail</li>
 *   <li>wallet empty-wallet: insufficient balance</li>
 *   <li>everything else: success</li>
 * </ul>
 * A real integration would replace only this class; the strategies and services stay the same.
 */
@Slf4j
@Component
public class SimulatedPaymentGateway {

    private static final Map<String, String> DECLINES = Map.of(
            "4000000000000002", "Card declined by the issuing bank",
            "4000000000009995", "Insufficient funds",
            "fail@upi", "UPI request declined by the customer",
            "empty-wallet", "Insufficient wallet balance");

    private static final String NO_REFUND_MARKER = "-norefund-";

    /** @param instrumentKey card number, UPI id or wallet id (used only to pick the simulated outcome) */
    public ProviderResult charge(PaymentMethod method, String instrumentKey, BigDecimal amount) {
        String decline = DECLINES.get(instrumentKey);
        if (decline != null) {
            log.info("Simulated provider declined a {} payment of {}: {}", method, amount, decline);
            return ProviderResult.declined(decline);
        }
        String marker = "refundfail@upi".equals(instrumentKey) ? NO_REFUND_MARKER : "-";
        return ProviderResult.succeeded(method.name().toLowerCase() + marker + UUID.randomUUID());
    }

    public ProviderResult refund(String providerReference, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return ProviderResult.declined("Refund amount must be positive");
        }
        if (providerReference.contains(NO_REFUND_MARKER)) {
            return ProviderResult.declined("Provider could not refund to this account");
        }
        return ProviderResult.succeeded("refund-" + UUID.randomUUID());
    }
}
