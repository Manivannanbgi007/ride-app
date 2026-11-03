package com.fooddelivery.paymentservice.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.paymentservice.dto.request.CardDetails;
import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class PaymentStrategiesTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
    private final CardPaymentStrategy card = new CardPaymentStrategy(gateway,
            Clock.fixed(Instant.parse("2026-10-07T07:00:00Z"), ZoneOffset.UTC));
    private final UpiPaymentStrategy upi = new UpiPaymentStrategy(gateway);
    private final WalletPaymentStrategy wallet = new WalletPaymentStrategy(gateway);

    @Test
    void validCardIsStoredOnlyAsBrandAndLastFourDigits() {
        assertThat(card.validateAndDescribe(cardPayment("4111 1111 1111 1111", 12, 2030))).isEqualTo("VISA **** 1111");
    }

    @Test
    void mistypedCardNumberFailsTheLuhnCheckWithoutRepeatingTheNumber() {
        var cardPayment = cardPayment("4111111111111112", 12, 2030);
        assertThatThrownBy(() -> card.validateAndDescribe(cardPayment))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The card number is not valid");
    }

    @Test
    void expiredCardIsRefused() {
        var cardPayment = cardPayment("4111111111111111", 9, 2026);
        assertThatThrownBy(() -> card.validateAndDescribe(cardPayment))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The card has expired");
    }

    @Test
    void cardDetailsNeverPrintTheFullNumber() {
        String printed = new CardDetails("4111111111111111", 12, 2030).toString();

        assertThat(printed).doesNotContain("4111111111111111").contains("**** 1111");
    }

    @Test
    void testCardsDecideTheSimulatedOutcome() {
        assertThat(card.charge(cardPayment("4111111111111111", 12, 2030), BigDecimal.TEN).success()).isTrue();
        assertThat(card.charge(cardPayment("4000000000000002", 12, 2030), BigDecimal.TEN).failureReason())
                .isEqualTo("Card declined by the issuing bank");
    }

    @Test
    void upiIdIsValidatedAndMasked() {
        assertThat(upi.validateAndDescribe(upiPayment("meera@okhdfc"))).isEqualTo("UPI me***@okhdfc");
        var upiPayment = upiPayment("not-a-upi-id");
        assertThatThrownBy(() -> upi.validateAndDescribe(upiPayment))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void walletIdIsMasked() {
        PayRequest request = new PayRequest(1L, PaymentMethod.WALLET, null, null, "wallet-98450");

        assertThat(wallet.validateAndDescribe(request)).isEqualTo("WALLET ***8450");
    }

    @Test
    void refundsFailForTheSpecialTestUpiId() {
        ProviderResult payment = upi.charge(upiPayment("refundfail@upi"), BigDecimal.TEN);

        assertThat(payment.success()).isTrue();
        assertThat(upi.refund(payment.providerReference(), BigDecimal.TEN).success()).isFalse();
    }

    @Test
    void factoryPicksTheStrategyByMethodAndRefusesCashOnDelivery() {
        PaymentStrategyFactory factory = new PaymentStrategyFactory(List.of(card, upi, wallet));

        assertThat(factory.getStrategy(PaymentMethod.UPI)).isSameAs(upi);
        assertThatThrownBy(() -> factory.getStrategy(PaymentMethod.CASH_ON_DELIVERY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("delivery partner");
    }

    private static PayRequest cardPayment(String number, int month, int year) {
        return new PayRequest(1L, PaymentMethod.CARD, new CardDetails(number, month, year), null, null);
    }

    private static PayRequest upiPayment(String upiId) {
        return new PayRequest(1L, PaymentMethod.UPI, null, upiId, null);
    }
}
