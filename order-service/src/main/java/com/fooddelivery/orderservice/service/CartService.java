package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.fooddelivery.orderservice.exception.ResourceNotFoundException;
import com.fooddelivery.orderservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer's cart: one restaurant at a time, prices always taken fresh from catalog-service, and at most one
 * coupon, whose discount promotion-service works out again each time the cart is priced.
 * Remote calls happen outside database transactions (see {@link TransactionTemplate} blocks).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    static final String COUPON_CHECK_UNAVAILABLE =
            "Coupons cannot be checked right now. Try again shortly, or remove the coupon to order without it";
    private static final String ADD_ITEMS_FIRST = "Add items to your cart before applying a coupon";

    private final CartRepository cartRepository;
    private final CatalogServiceClient catalogServiceClient;
    private final PromotionServiceClient promotionServiceClient;
    private final OrderPricingCalculator orderPricingCalculator;
    private final AppProperties appProperties;
    private final TransactionTemplate transactionTemplate;

    public CartResponse getCart(Long customerId) {
        CartSnapshot cart = readCart(customerId);
        return toResponse(customerId, cart, findMenuItems(cart));
    }

    /**
     * Reads the cart inside a short transaction (its lines are loaded lazily) and returns a plain copy,
     * so the remote calls afterwards hold no database connection.
     */
    CartSnapshot readCart(Long customerId) {
        return transactionTemplate.execute(transaction -> cartRepository.findByCustomerId(customerId)
                .map(cart -> new CartSnapshot(cart.getRestaurantId(), cart.getCouponCode(), List.copyOf(cart.getLines())))
                .orElse(new CartSnapshot(null, null, List.of())));
    }

    record CartSnapshot(Long restaurantId, String couponCode, List<CartLine> lines) {
    }

    /** Adds the item or changes its quantity. */
    public CartResponse setItemQuantity(Long customerId, Long menuItemId, int quantity) {
        int maxQuantity = appProperties.cart().maxQuantityPerItem();
        if (quantity > maxQuantity) {
            throw new BusinessRuleException("At most " + maxQuantity + " of one item per order");
        }

        MenuItemSummary menuItem = catalogServiceClient.findMenuItems(List.of(menuItemId)).get(menuItemId);
        if (menuItem == null) {
            throw new ResourceNotFoundException("Menu item not found: " + menuItemId);
        }
        if (!menuItem.isOrderable()) {
            throw new BusinessRuleException("'" + menuItem.name() + "' is not available right now");
        }

        transactionTemplate.executeWithoutResult(transaction -> {
            Cart cart = cartRepository.findByCustomerId(customerId).orElseGet(() -> newCart(customerId));

            if (!cart.getLines().isEmpty() && !menuItem.restaurantId().equals(cart.getRestaurantId())) {
                throw new ConflictException("Your cart has items from another restaurant. Clear the cart first.");
            }
            Optional<CartLine> existing = cart.findLine(menuItemId);
            if (existing.isEmpty() && cart.getLines().size() >= appProperties.cart().maxLines()) {
                throw new BusinessRuleException(
                        "A cart can hold at most " + appProperties.cart().maxLines() + " different items");
            }

            existing.ifPresent(cart.getLines()::remove);
            cart.getLines().add(new CartLine(menuItemId, quantity,
                    existing.map(CartLine::addedAt).orElseGet(Instant::now)));
            cart.setRestaurantId(menuItem.restaurantId());
            cartRepository.save(cart);
        });

        return getCart(customerId);
    }

    private static ResourceNotFoundException notInCart(Long menuItemId) {
        return new ResourceNotFoundException("Item " + menuItemId + " is not in the cart");
    }

    public CartResponse removeItem(Long customerId, Long menuItemId) {
        transactionTemplate.executeWithoutResult(transaction -> {
            Cart cart = cartRepository.findByCustomerId(customerId)
                    .orElseThrow(() -> notInCart(menuItemId));
            CartLine line = cart.findLine(menuItemId)
                    .orElseThrow(() -> notInCart(menuItemId));

            cart.getLines().remove(line);
            if (cart.getLines().isEmpty()) {
                empty(cart);
            }
        });
        return getCart(customerId);
    }

    public void clearCart(Long customerId) {
        transactionTemplate.executeWithoutResult(transaction ->
                cartRepository.findByCustomerId(customerId).ifPresent(CartService::empty));
    }

    /** Empties the cart. A coupon belongs to the cart's items, so it goes too. */
    static void empty(Cart cart) {
        cart.getLines().clear();
        cart.setRestaurantId(null);
        cart.setCouponCode(null);
    }

    /**
     * Applies a coupon, or replaces the one applied. Only a coupon that works for the cart as it is now is accepted;
     * otherwise 422 with promotion-service's reason ("Add items worth 120.00 more ...").
     */
    public CartResponse applyCoupon(Long customerId, String code) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        CartSnapshot cart = readCart(customerId);
        if (cart.lines().isEmpty()) {
            throw new BusinessRuleException(ADD_ITEMS_FIRST);
        }
        Map<Long, MenuItemSummary> menuItems = findMenuItems(cart);
        CouponCheck check = promotionServiceClient.check(normalizedCode, customerId, cart.restaurantId(),
                orderableItemsTotal(cart.lines(), menuItems));
        if (!check.applicable()) {
            throw new BusinessRuleException(check.reason());
        }

        transactionTemplate.executeWithoutResult(transaction -> {
            Cart customerCart = cartRepository.findByCustomerId(customerId)
                    .filter(found -> !found.getLines().isEmpty())
                    .orElseThrow(() -> new BusinessRuleException(ADD_ITEMS_FIRST));
            customerCart.setCouponCode(check.code());
        });
        log.info("Coupon applied to cart. customerId={} code={}", customerId, check.code());
        return getCart(customerId);
    }

    public CartResponse removeCoupon(Long customerId) {
        transactionTemplate.executeWithoutResult(transaction ->
                cartRepository.findByCustomerId(customerId).ifPresent(cart -> cart.setCouponCode(null)));
        return getCart(customerId);
    }

    /**
     * Builds the response from cart lines plus current catalog data and the coupon's current discount, and lists
     * anything that blocks checkout.
     */
    CartResponse toResponse(Long customerId, CartSnapshot cart, Map<Long, MenuItemSummary> menuItems) {
        List<CartResponse.Item> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (CartLine line : cart.lines()) {
            MenuItemSummary menuItem = menuItems.get(line.menuItemId());
            if (menuItem == null) {
                problems.add("Item " + line.menuItemId() + " is no longer on the menu");
                items.add(new CartResponse.Item(line.menuItemId(), null, null, null, line.quantity(), null, false));
                continue;
            }

            BigDecimal lineTotal = menuItem.price().multiply(BigDecimal.valueOf(line.quantity()));
            if (!menuItem.isOrderable()) {
                problems.add("'" + menuItem.name() + "' is not available right now");
            }
            items.add(new CartResponse.Item(menuItem.id(), menuItem.name(), menuItem.foodType(), menuItem.price(),
                    line.quantity(), lineTotal, menuItem.isOrderable()));
        }
        BigDecimal itemsTotal = orderableItemsTotal(cart.lines(), menuItems);

        CartResponse.Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (cart.couponCode() != null && !items.isEmpty()) {
            coupon = priceCoupon(customerId, cart, itemsTotal);
            if (coupon.applied()) {
                discount = coupon.discountAmount();
            } else {
                problems.add("Coupon " + coupon.code() + ": " + coupon.reason());
            }
        }

        boolean orderable = !items.isEmpty() && problems.isEmpty();
        return new CartResponse(cart.restaurantId(), items, coupon,
                orderPricingCalculator.calculate(itemsTotal, discount), orderable, problems);
    }

    /** The coupon's discount for the cart as it is now. If promotion-service is down, the cart is still shown. */
    private CartResponse.Coupon priceCoupon(Long customerId, CartSnapshot cart, BigDecimal itemsTotal) {
        try {
            CouponCheck check = promotionServiceClient.check(cart.couponCode(), customerId, cart.restaurantId(), itemsTotal);
            return new CartResponse.Coupon(check.code(), check.description(), check.applicable(),
                    check.applicable() ? check.discountAmount() : BigDecimal.ZERO.setScale(2), check.reason());
        } catch (RemoteServiceException e) {
            log.warn("Coupon not checked; cart priced without it. customerId={} code={} cause={}",
                    customerId, cart.couponCode(), e.getMessage());
            return new CartResponse.Coupon(cart.couponCode(), null, false, BigDecimal.ZERO.setScale(2),
                    COUPON_CHECK_UNAVAILABLE);
        }
    }

    private Map<Long, MenuItemSummary> findMenuItems(CartSnapshot cart) {
        return catalogServiceClient.findMenuItems(cart.lines().stream().map(CartLine::menuItemId).toList());
    }

    /** What the items that can be ordered cost now (a coupon's discount and minimum order are based on this). */
    private static BigDecimal orderableItemsTotal(List<CartLine> lines, Map<Long, MenuItemSummary> menuItems) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : lines) {
            MenuItemSummary menuItem = menuItems.get(line.menuItemId());
            if (menuItem != null && menuItem.isOrderable()) {
                total = total.add(menuItem.price().multiply(BigDecimal.valueOf(line.quantity())));
            }
        }
        return total;
    }

    private static Cart newCart(Long customerId) {
        Cart cart = new Cart();
        cart.setCustomerId(customerId);
        return cart;
    }
}
