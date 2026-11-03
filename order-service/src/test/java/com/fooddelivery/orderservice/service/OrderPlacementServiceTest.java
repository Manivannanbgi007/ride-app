package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.orderservice.client.CatalogServiceClient;
import com.fooddelivery.orderservice.client.PromotionServiceClient;
import com.fooddelivery.orderservice.client.RemoteRecords.AddressSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponReservation;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.RestaurantSummary;
import com.fooddelivery.orderservice.client.RemoteRecords.UserSummary;
import com.fooddelivery.orderservice.client.RestaurantServiceClient;
import com.fooddelivery.orderservice.client.UserServiceClient;
import com.fooddelivery.orderservice.dto.request.PlaceOrderRequest;
import com.fooddelivery.orderservice.entity.CartLine;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.OrderTimelineEntry;
import com.fooddelivery.orderservice.entity.PaymentMethod;
import com.fooddelivery.orderservice.exception.BusinessRuleException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.CartRepository;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import com.fooddelivery.orderservice.service.OrderPlacementService.PlacementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    private static final Long CUSTOMER = 7L;
    private static final Long RESTAURANT = 10L;
    private static final String KEY = "checkout-0001";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderTimelineRepository orderTimelineRepository;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartService cartService;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private RestaurantServiceClient restaurantServiceClient;
    @Mock
    private CatalogServiceClient catalogServiceClient;
    @Mock
    private PromotionServiceClient promotionServiceClient;

    private OrderPlacementService orderPlacementService;

    @BeforeEach
    void setUp() {
        orderPlacementService = new OrderPlacementService(orderRepository, orderTimelineRepository, cartRepository,
                cartService, userServiceClient, restaurantServiceClient, catalogServiceClient, promotionServiceClient,
                new OrderPricingCalculator(TestProperties.appProperties()),
                new OrderStatusTransitions(orderTimelineRepository, orderEventPublisher,
                new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), Clock.systemUTC()),
                orderEventPublisher, new OrderMapper(), new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void cashOnDeliveryOrderGoesStraightToTheRestaurantWithCopiedPrices() {
        givenCheckoutReady("ACTIVE", true, true);

        PlacementResult result = orderPlacementService.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CASH_ON_DELIVERY));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        Order order = saved.getValue();
        assertThat(result.replayed()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getRestaurantName()).isEqualTo("Spice Hub");
        assertThat(order.getLines()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Chicken Biryani");
            assertThat(line.unitPrice()).isEqualByComparingTo("299.00");
            assertThat(line.lineTotal()).isEqualByComparingTo("598.00");
        });
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("0.00");   // above the free-delivery threshold
        assertThat(order.getTotalAmount()).isEqualByComparingTo("627.90"); // 598.00 + 5% tax
        assertThat(order.getDeliveryAddress().city()).isEqualTo("Bengaluru");
        verify(orderTimelineRepository).save(any(OrderTimelineEntry.class));
        verify(orderEventPublisher).orderCreated(order);
    }

    @Test
    void onlinePaymentOrderWaitsForPayment() {
        givenCheckoutReady("ACTIVE", true, true);

        orderPlacementService.placeOrder(CUSTOMER, KEY, request(PaymentMethod.UPI));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void retryWithTheSameKeyReturnsTheFirstOrderWithoutCallingOtherServices() {
        Order existing = existingOrder(IdempotencyFingerprint.of(request(PaymentMethod.CASH_ON_DELIVERY)));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER, KEY)).thenReturn(Optional.of(existing));

        PlacementResult result = orderPlacementService.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CASH_ON_DELIVERY));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(500L);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(restaurantServiceClient, catalogServiceClient, userServiceClient, promotionServiceClient,
                orderEventPublisher);
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() {
        Order existing = existingOrder(IdempotencyFingerprint.of(request(PaymentMethod.CASH_ON_DELIVERY)));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER, KEY)).thenReturn(Optional.of(existing));

        var request = request(PaymentMethod.CARD);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Idempotency-Key was already used");
    }

    @Test
    void closedRestaurantCannotTakeOrders() {
        givenCart();
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, 3L, "Spice Hub", "ACTIVE", false)));

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed right now");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void itemThatSoldOutAfterAddingToCartStopsCheckout() {
        givenCart();
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, 3L, "Spice Hub", "ACTIVE", true)));
        when(catalogServiceClient.findMenuItems(List.of(100L))).thenReturn(Map.of(100L,
                new MenuItemSummary(100L, RESTAURANT, "Chicken Biryani", new BigDecimal("299.00"), "NON_VEG", "ACTIVE", false)));

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Chicken Biryani");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void emptyCartCannotBeOrdered() {
        when(cartService.readCart(CUSTOMER)).thenReturn(new CartService.CartSnapshot(null, null, List.of()));

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void addressMustBelongToTheCustomer() {
        givenCheckoutReady("ACTIVE", true, false);

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not in your address book");
    }

    @Test
    void theCartsCouponIsReservedForTheCheckoutAndPricedIntoTheOrder() {
        givenCheckoutWithCoupon();
        givenOrdersAreSaved();

        PlacementResult result = orderPlacementService.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CASH_ON_DELIVERY));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        Order order = saved.getValue();
        assertThat(order.getCouponCode()).isEqualTo("WELCOME100");
        assertThat(order.getCouponReservationId()).isEqualTo(900L);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("40.00");   // 498.00 after the discount: under 499.00
        assertThat(order.getTotalAmount()).isEqualByComparingTo("562.90"); // 498.00 + 40.00 + 5% of 498.00
        assertThat(result.order().couponCode()).isEqualTo("WELCOME100");
        assertThat(result.order().price().discountAmount()).isEqualByComparingTo("100.00");
        verify(promotionServiceClient, never()).release(any());
    }

    @Test
    void aCouponThatCanNoLongerBeUsedStopsCheckout() {
        givenRemoteChecksPass("WELCOME100", "ACTIVE", true, true);
        when(promotionServiceClient.reserve(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("Coupon WELCOME100: This coupon has been fully used"));

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Coupon WELCOME100: This coupon has been fully used");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void whenTheOrderCannotBeSavedTheCouponUseIsGivenBack() {
        givenCheckoutWithCoupon();
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new QueryTimeoutException("Lock wait timeout"));

        var request = request(PaymentMethod.CASH_ON_DELIVERY);
        assertThatThrownBy(() -> orderPlacementService.placeOrder(CUSTOMER, KEY, request))
                .isInstanceOf(QueryTimeoutException.class);
        verify(promotionServiceClient).release("checkout:7:" + KEY);
    }

    @Test
    void aSimultaneousIdenticalCheckoutThatWonKeepsTheCouponUse() {
        givenCheckoutWithCoupon();
        Order winner = existingOrder(IdempotencyFingerprint.of(request(PaymentMethod.CASH_ON_DELIVERY)));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(CUSTOMER, KEY))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_orders_idempotency'"));

        PlacementResult result = orderPlacementService.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CASH_ON_DELIVERY));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(500L);
        // Both requests reserved with the same key: the use belongs to the order that won
        verify(promotionServiceClient, never()).release(any());
    }

    private void givenCart() {
        givenCart(null);
    }

    private void givenCart(String couponCode) {
        when(cartService.readCart(CUSTOMER)).thenReturn(
                new CartService.CartSnapshot(RESTAURANT, couponCode, List.of(new CartLine(100L, 2, Instant.now()))));
    }

    private void givenCheckoutReady(String restaurantStatus, boolean openNow, boolean addressExists) {
        givenRemoteChecksPass(null, restaurantStatus, openNow, addressExists);
        if (addressExists) {
            givenOrdersAreSaved();
        }
    }

    /** Two biryanis (598.00) and a coupon that takes 100.00 off them. */
    private void givenCheckoutWithCoupon() {
        givenRemoteChecksPass("WELCOME100", "ACTIVE", true, true);
        when(promotionServiceClient.reserve(eq("checkout:7:" + KEY), eq("WELCOME100"), eq(CUSTOMER), eq(RESTAURANT),
                argThat(itemsTotal -> itemsTotal.compareTo(new BigDecimal("598.00")) == 0)))
                .thenReturn(new CouponReservation(900L, "WELCOME100", new BigDecimal("100.00"), "RESERVED"));
    }

    private void givenOrdersAreSaved() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });
    }

    private void givenRemoteChecksPass(String couponCode, String restaurantStatus, boolean openNow, boolean addressExists) {
        givenCart(couponCode);
        when(restaurantServiceClient.findRestaurant(RESTAURANT))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT, 3L, "Spice Hub", restaurantStatus, openNow)));
        when(catalogServiceClient.findMenuItems(List.of(100L))).thenReturn(Map.of(100L,
                new MenuItemSummary(100L, RESTAURANT, "Chicken Biryani", new BigDecimal("299.00"), "NON_VEG", "ACTIVE", true)));
        when(userServiceClient.findUser(CUSTOMER)).thenReturn(Optional.of(
                new UserSummary(CUSTOMER, "Priya Sharma", "priya@example.com", "+919876543210", "CUSTOMER", "ACTIVE")));
        when(userServiceClient.findAddress(CUSTOMER, 55L)).thenReturn(addressExists
                ? Optional.of(new AddressSummary(55L, CUSTOMER, "12 MG Road", null, "Bengaluru", "Karnataka", "560001",
                "India", null, null))
                : Optional.empty());
    }

    private static Order existingOrder(String fingerprint) {
        Order order = new Order();
        order.setId(500L);
        order.setCustomerId(CUSTOMER);
        order.setStatus(OrderStatus.PLACED);
        order.setRequestFingerprint(fingerprint);
        order.setDeliveryAddress(new com.fooddelivery.orderservice.entity.DeliveryAddress(
                "12 MG Road", null, "Bengaluru", "Karnataka", "560001", "India", null, null));
        return order;
    }

    private static PlaceOrderRequest request(PaymentMethod paymentMethod) {
        return new PlaceOrderRequest(55L, paymentMethod, "Less spicy");
    }
}
