package com.fooddelivery.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.orderservice.client.CatalogServiceClient;
import com.fooddelivery.orderservice.client.PromotionServiceClient;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponCheck;
import com.fooddelivery.orderservice.client.RemoteRecords.MenuItemSummary;
import com.fooddelivery.orderservice.config.AppProperties;
import com.fooddelivery.orderservice.dto.response.CartResponse;
import com.fooddelivery.orderservice.entity.Cart;
import com.fooddelivery.orderservice.entity.CartLine;
import com.fooddelivery.orderservice.exception.BusinessRuleException;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import com.fooddelivery.orderservice.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long CUSTOMER = 7L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CatalogServiceClient catalogServiceClient;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = TestProperties.appProperties();
        cartService = new CartService(cartRepository, catalogServiceClient, promotionServiceClient,
                new OrderPricingCalculator(appProperties),
                appProperties, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void cartHoldsItemsOfOneRestaurantOnly() {
        Cart cart = cartWith(10L, new CartLine(100L, 1, Instant.now()));
        when(cartRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(catalogServiceClient.findMenuItems(List.of(200L))).thenReturn(Map.of(200L, item(200L, 20L, "99.00", true)));

        assertThatThrownBy(() -> cartService.setItemQuantity(CUSTOMER, 200L, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another restaurant");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void soldOutItemCannotBeAdded() {
        when(catalogServiceClient.findMenuItems(List.of(100L))).thenReturn(Map.of(100L, item(100L, 10L, "99.00", false)));

        assertThatThrownBy(() -> cartService.setItemQuantity(CUSTOMER, 100L, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void quantityIsLimitedPerItem() {
        assertThatThrownBy(() -> cartService.setItemQuantity(CUSTOMER, 100L, 21))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At most 20");
    }

    @Test
    void cartShowsLivePricesAndBlocksCheckoutWhenAnItemBecameUnavailable() {
        List<CartLine> lines = List.of(new CartLine(100L, 2, Instant.now()), new CartLine(101L, 1, Instant.now()));
        Map<Long, MenuItemSummary> catalog = Map.of(
                100L, item(100L, 10L, "150.00", true),
                101L, item(101L, 10L, "80.00", false));

        CartResponse cart = cartService.toResponse(CUSTOMER, new CartService.CartSnapshot(10L, null, lines), catalog);

        assertThat(cart.price().itemsTotal()).isEqualByComparingTo("300.00"); // only the orderable item counts
        assertThat(cart.orderable()).isFalse();
        assertThat(cart.problems()).singleElement().asString().contains("not available");
        assertThat(cart.items()).extracting(CartResponse.Item::orderable).containsExactly(true, false);
    }

    @Test
    void anAppliedCouponLowersThePrice() {
        whenCouponChecked(new CouponCheck("WELCOME100", true, new BigDecimal("100.00"), null, "100 off"));

        CartResponse cart = cartService.toResponse(CUSTOMER, cartOfTwoBiryanis("WELCOME100"), biryani());

        assertThat(cart.coupon().applied()).isTrue();
        assertThat(cart.price().discountAmount()).isEqualByComparingTo("100.00");
        assertThat(cart.price().totalAmount()).isEqualByComparingTo("525.00"); // 600 - 100, free delivery, 5% tax
        assertThat(cart.orderable()).isTrue();
    }

    @Test
    void aCouponTheCartNoLongerQualifiesForBlocksCheckoutWithTheReason() {
        whenCouponChecked(new CouponCheck("WELCOME100", false, BigDecimal.ZERO,
                "Add items worth 99.00 more to use this coupon (minimum order 699.00)", "100 off"));

        CartResponse cart = cartService.toResponse(CUSTOMER, cartOfTwoBiryanis("WELCOME100"), biryani());

        assertThat(cart.coupon().applied()).isFalse();
        assertThat(cart.price().discountAmount()).isEqualByComparingTo("0.00");
        assertThat(cart.orderable()).isFalse();
        assertThat(cart.problems()).containsExactly(
                "Coupon WELCOME100: Add items worth 99.00 more to use this coupon (minimum order 699.00)");
    }

    @Test
    void theCartIsStillShownWhenCouponsCannotBeChecked() {
        when(promotionServiceClient.check("WELCOME100", CUSTOMER, 10L, new BigDecimal("600.00")))
                .thenThrow(new RemoteServiceException("promotion-service", new RuntimeException("Connection refused")));

        CartResponse cart = cartService.toResponse(CUSTOMER, cartOfTwoBiryanis("WELCOME100"), biryani());

        assertThat(cart.price().totalAmount()).isEqualByComparingTo("630.00");
        assertThat(cart.coupon().reason()).isEqualTo(CartService.COUPON_CHECK_UNAVAILABLE);
        assertThat(cart.orderable()).isFalse();
    }

    @Test
    void aCouponIsOnlyAppliedIfItWorksForTheCart() {
        Cart cart = cartWith(10L, new CartLine(100L, 2, Instant.now()));
        when(cartRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(catalogServiceClient.findMenuItems(List.of(100L))).thenReturn(biryani());
        whenCouponChecked(new CouponCheck("FREEBIE", false, BigDecimal.ZERO, "There is no coupon FREEBIE", null));

        assertThatThrownBy(() -> cartService.applyCoupon(CUSTOMER, " freebie "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("There is no coupon FREEBIE");
        assertThat(cart.getCouponCode()).isNull();
    }

    @Test
    void anAppliedCouponIsKeptInCapitalsAndGoesWhenTheCartEmpties() {
        Cart cart = cartWith(10L, new CartLine(100L, 2, Instant.now()));
        when(cartRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(catalogServiceClient.findMenuItems(List.of(100L))).thenReturn(biryani());
        when(promotionServiceClient.check("WELCOME100", CUSTOMER, 10L, new BigDecimal("600.00")))
                .thenReturn(new CouponCheck("WELCOME100", true, new BigDecimal("100.00"), null, "100 off"));

        assertThat(cartService.applyCoupon(CUSTOMER, "welcome100").coupon().applied()).isTrue();
        assertThat(cart.getCouponCode()).isEqualTo("WELCOME100");

        when(catalogServiceClient.findMenuItems(List.of())).thenReturn(Map.of());
        cartService.removeItem(CUSTOMER, 100L);
        assertThat(cart.getCouponCode()).isNull();
    }

    @Test
    void aCouponNeedsSomethingInTheCart() {
        assertThatThrownBy(() -> cartService.applyCoupon(CUSTOMER, "WELCOME100"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Add items to your cart");
        verifyNoInteractions(promotionServiceClient);
    }

    private void whenCouponChecked(CouponCheck answer) {
        when(promotionServiceClient.check(answer.code(), CUSTOMER, 10L, new BigDecimal("600.00"))).thenReturn(answer);
    }

    private static CartService.CartSnapshot cartOfTwoBiryanis(String couponCode) {
        return new CartService.CartSnapshot(10L, couponCode, List.of(new CartLine(100L, 2, Instant.now())));
    }

    private static Map<Long, MenuItemSummary> biryani() {
        return Map.of(100L, item(100L, 10L, "300.00", true));
    }

    private static Cart cartWith(Long restaurantId, CartLine... lines) {
        Cart cart = new Cart();
        cart.setCustomerId(CUSTOMER);
        cart.setRestaurantId(restaurantId);
        cart.getLines().addAll(List.of(lines));
        return cart;
    }

    private static MenuItemSummary item(Long id, Long restaurantId, String price, boolean available) {
        return new MenuItemSummary(id, restaurantId, "Item " + id, new BigDecimal(price), "VEG", "ACTIVE", available);
    }
}
