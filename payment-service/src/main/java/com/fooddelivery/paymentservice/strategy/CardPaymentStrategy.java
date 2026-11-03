package com.fooddelivery.paymentservice.strategy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

import com.fooddelivery.paymentservice.dto.request.CardDetails;
import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Card payments. The card number is checked (Luhn) and then only its last 4 digits are kept. */
@Component
@RequiredArgsConstructor
public class CardPaymentStrategy implements PaymentStrategy {

    private final SimulatedPaymentGateway gateway;
    private final Clock clock;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CARD;
    }

    @Override
    public String validateAndDescribe(PayRequest request) {
        CardDetails card = request.card();
        if (card == null) {
            throw new BusinessRuleException("Card details are required for a CARD payment");
        }
        String number = digitsOnly(card.number());
        // Messages never repeat the card number: they could end up in logs.
        if (number.length() < 12 || number.length() > 19 || !passesLuhnCheck(number)) {
            throw new BusinessRuleException("The card number is not valid");
        }
        if (YearMonth.of(card.expiryYear(), card.expiryMonth()).isBefore(YearMonth.now(clock))) {
            throw new BusinessRuleException("The card has expired");
        }
        return brand(number) + " **** " + number.substring(number.length() - 4);
    }

    @Override
    public ProviderResult charge(PayRequest request, BigDecimal amount) {
        return gateway.charge(method(), digitsOnly(request.card().number()), amount);
    }

    @Override
    public ProviderResult refund(String providerReference, BigDecimal amount) {
        return gateway.refund(providerReference, amount);
    }

    /** The Luhn checksum catches most typing mistakes in card numbers. */
    static boolean passesLuhnCheck(String number) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static String brand(String number) {
        if (number.startsWith("4")) {
            return "VISA";
        }
        if (number.startsWith("5")) {
            return "MASTERCARD";
        }
        if (number.startsWith("6")) {
            return "RUPAY";
        }
        return "CARD";
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
