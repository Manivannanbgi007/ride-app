package com.fooddelivery.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fooddelivery.paymentservice.client.OrderServiceClient;
import com.fooddelivery.paymentservice.client.OrderServiceClient.OrderSummary;
import com.fooddelivery.paymentservice.client.RazorpayClient;
import com.fooddelivery.paymentservice.config.AppProperties;
import com.fooddelivery.paymentservice.dto.request.PayRequest;
import com.fooddelivery.paymentservice.dto.request.RefundRequest;
import com.fooddelivery.paymentservice.entity.PayableOrder;
import com.fooddelivery.paymentservice.entity.Payment;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentProvider;
import com.fooddelivery.paymentservice.entity.PaymentStatus;
import com.fooddelivery.paymentservice.entity.Refund;
import com.fooddelivery.paymentservice.entity.Role;
import com.fooddelivery.paymentservice.exception.BusinessRuleException;
import com.fooddelivery.paymentservice.exception.ConflictException;
import com.fooddelivery.paymentservice.exception.ResourceNotFoundException;
import com.fooddelivery.paymentservice.kafka.producer.PaymentEventPublisher;
import com.fooddelivery.paymentservice.mapper.PaymentMapper;
import com.fooddelivery.paymentservice.repository.PayableOrderRepository;
import com.fooddelivery.paymentservice.repository.PaymentRepository;
import com.fooddelivery.paymentservice.repository.RefundRepository;
import com.fooddelivery.paymentservice.strategy.PaymentProviderGuard;
import com.fooddelivery.paymentservice.strategy.PaymentStrategyFactory;
import com.fooddelivery.paymentservice.strategy.SimulatedPaymentGateway;
import com.fooddelivery.paymentservice.strategy.UpiPaymentStrategy;
import com.fooddelivery.paymentservice.web.CallerInfo;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Business rules that are checked before any money moves. */
@ExtendWith(MockitoExtension.class)
class PaymentAndRefundRulesTest {

    private static final Long CUSTOMER = 7L;
    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");
    /** The default setting: online payments use the simulated gateway. */
    private static final AppProperties SIMULATED_GATEWAY = new AppProperties("Asia/Kolkata", null, null,
            new AppProperties.Payment(PaymentProvider.SIMULATED, null));

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PayableOrderRepository payableOrderRepository;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private AdminAuditService adminAuditService;

    private PaymentService paymentService;
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactions = new TransactionTemplate(mock(PlatformTransactionManager.class));
        PaymentStrategyFactory factory =
                new PaymentStrategyFactory(List.of(new UpiPaymentStrategy(new SimulatedPaymentGateway())));
        PaymentMetrics paymentMetrics = new PaymentMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        PaymentProviderGuard providerGuard = new PaymentProviderGuard(RateLimiterRegistry.ofDefaults(),
                CircuitBreakerRegistry.ofDefaults());
        paymentService = new PaymentService(paymentRepository,
                new PayableOrderDirectory(payableOrderRepository, orderServiceClient), factory, providerGuard,
                paymentMetrics, paymentEventPublisher, new PaymentMapper(), Clock.systemUTC(), transactions,
                SIMULATED_GATEWAY);
        refundService = new RefundService(paymentRepository, refundRepository, orderServiceClient, factory,
                mock(RazorpayClient.class), providerGuard, paymentMetrics, paymentEventPublisher, adminAuditService,
                new PaymentMapper(), Clock.systemUTC(), transactions);
    }

    @Test
    void onlyOrdersWaitingForPaymentCanBePaid() {
        givenOrder("PLACED", "UPI", CUSTOMER);

        var request = upi("meera@okhdfc");
        assertThatThrownBy(() -> paymentService.pay(CUSTOMER, "pay-key-0001", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not waiting for payment");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void paymentMethodMustBeTheOneChosenAtCheckout() {
        givenOrder("PAYMENT_PENDING", "CARD", CUSTOMER);

        var request = upi("meera@okhdfc");
        assertThatThrownBy(() -> paymentService.pay(CUSTOMER, "pay-key-0001", request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("paid by CARD");
    }

    @Test
    void customersCannotPaySomeoneElsesOrder() {
        givenOrder("PAYMENT_PENDING", "UPI", 999L);

        var request = upi("meera@okhdfc");
        assertThatThrownBy(() -> paymentService.pay(CUSTOMER, "pay-key-0001", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anOrderKnownFromItsEventsIsPaidWithoutAskingOrderService() {
        when(payableOrderRepository.findById(500L)).thenReturn(Optional.of(PayableOrder.of(500L, CUSTOMER, 10L, "UPI",
                new BigDecimal("353.95"), "INR", PayableOrder.AWAITING_PAYMENT, Instant.now())));
        AtomicReference<Payment> saved = new AtomicReference<>();
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(900L);
            saved.set(payment);
            return payment;
        });
        when(paymentRepository.findById(900L)).thenAnswer(invocation -> Optional.of(saved.get()));

        PaymentService.PaymentResult result = paymentService.pay(CUSTOMER, "pay-key-0001", upi("meera@okhdfc"));

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().amount()).isEqualByComparingTo("353.95");
        verifyNoInteractions(orderServiceClient);
        verify(paymentEventPublisher).statusChanged(any(Payment.class), eq(null));
    }

    @Test
    void anOrderCancelledMeanwhileCanNoLongerBePaid() {
        when(payableOrderRepository.findById(500L)).thenReturn(Optional.of(PayableOrder.of(500L, CUSTOMER, 10L, "UPI",
                new BigDecimal("353.95"), "INR", "CANCELLED", Instant.now())));

        var request = upi("meera@okhdfc");
        assertThatThrownBy(() -> paymentService.pay(CUSTOMER, "pay-key-0001", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not waiting for payment (status CANCELLED)");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void aSecondPaymentIsRefusedWhileAnotherIsRunningOrDone() {
        givenOrder("PAYMENT_PENDING", "UPI", CUSTOMER);
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(500L), any())).thenReturn(true);

        var request = upi("meera@okhdfc");
        assertThatThrownBy(() -> paymentService.pay(CUSTOMER, "pay-key-0002", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in progress or completed");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAutomaticRefundIsSkippedWhenNothingIsLeftToRefund() {
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "353.95")));

        assertThat(refundService.requestAutomaticRefund(900L, "Order cancelled")).isEmpty();
        verify(refundRepository, never()).save(any());
    }

    @Test
    void anAutomaticRefundIsRequestedAtMostOncePerPayment() {
        when(refundRepository.findByPaymentIdAndIdempotencyKey(900L, "auto-refund-900"))
                .thenReturn(Optional.of(new Refund()));

        assertThat(refundService.requestAutomaticRefund(900L, "Order cancelled")).isEmpty();
        verify(refundRepository, never()).save(any());
    }

    @Test
    void retryWithTheSameKeyReturnsTheFirstPaymentWithoutCallingAnyone() {
        Payment existing = payment(PaymentStatus.SUCCESS, "0.00");
        existing.setRequestFingerprint(PaymentFingerprint.of(upi("meera@okhdfc")));
        when(paymentRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER, "pay-key-0001")).thenReturn(Optional.of(existing));

        PaymentService.PaymentResult result = paymentService.pay(CUSTOMER, "pay-key-0001", upi("meera@okhdfc"));

        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(orderServiceClient);
    }

    @Test
    void refundsAreOnlyForOrdersThatAreNotRunningAnyMore() {
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "0.00")));
        givenOrder("PREPARING", "UPI", CUSTOMER);

        var refundRequest = new RefundRequest(null, "Customer complaint");
        assertThatThrownBy(() -> refundService.requestRefund(900L, refundRequest, "refund-key-01", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cancel the order first");
    }

    @Test
    void refundCannotExceedWhatIsLeftToRefund() {
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "300.00")));
        givenOrder("CANCELLED", "UPI", CUSTOMER);

        var refundRequest = new RefundRequest(new BigDecimal("100.00"), "Second refund");
        assertThatThrownBy(() -> refundService.requestRefund(900L, refundRequest, "refund-key-02", ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 0.01 and 53.95");
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void failedPaymentsCannotBeRefunded() {
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(payment(PaymentStatus.FAILED, "0.00")));
        givenOrder("CANCELLED", "UPI", CUSTOMER);

        var refundRequest = new RefundRequest(null, "x");
        assertThatThrownBy(() -> refundService.requestRefund(900L, refundRequest, "refund-key-03", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only successful payments");
    }

    private void givenOrder(String status, String paymentMethod, Long customerId) {
        when(orderServiceClient.findOrder(500L)).thenReturn(Optional.of(new OrderSummary(500L, customerId, 10L, status,
                paymentMethod, "PENDING", new BigDecimal("353.95"), "INR")));
    }

    private static Payment payment(PaymentStatus status, String refunded) {
        Payment payment = new Payment();
        payment.setId(900L);
        payment.setOrderId(500L);
        payment.setCustomerId(CUSTOMER);
        payment.setAmount(new BigDecimal("353.95"));
        payment.setRefundedAmount(new BigDecimal(refunded));
        payment.setCurrency("INR");
        payment.setMethod(PaymentMethod.UPI);
        payment.setStatus(status);
        payment.setProviderReference("upi-test");
        return payment;
    }

    private static PayRequest upi(String upiId) {
        return new PayRequest(500L, PaymentMethod.UPI, null, upiId, null);
    }
}
