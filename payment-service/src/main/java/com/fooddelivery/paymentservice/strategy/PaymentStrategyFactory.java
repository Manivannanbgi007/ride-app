package com.fooddelivery.paymentservice.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

/**
 * Factory pattern: picks the strategy for a payment method.
 * Spring injects every {@link PaymentStrategy} bean; they are indexed by the method they handle,
 * so the services never need an if/else chain over payment methods.
 */
@Component
public class PaymentStrategyFactory {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);

    public PaymentStrategyFactory(List<PaymentStrategy> availableStrategies) {
        availableStrategies.forEach(strategy -> strategies.put(strategy.method(), strategy));
    }

    public PaymentStrategy getStrategy(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new BusinessRuleException(method + " cannot be paid online"
                    + (method == PaymentMethod.CASH_ON_DELIVERY ? ": it is paid to the delivery partner" : ""));
        }
        return strategy;
    }
}
