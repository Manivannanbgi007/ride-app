package com.fooddelivery.orderservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import com.fooddelivery.orderservice.dto.response.OrderResponse;
import com.fooddelivery.orderservice.dto.response.PriceBreakdown;
import com.fooddelivery.orderservice.entity.ActorType;
import com.fooddelivery.orderservice.entity.CartLine;
import com.fooddelivery.orderservice.entity.DeliveryAddress;
import com.fooddelivery.orderservice.entity.Order;
import com.fooddelivery.orderservice.entity.OrderLine;
import com.fooddelivery.orderservice.entity.OrderStatus;
import com.fooddelivery.orderservice.entity.PaymentStatus;
import com.fooddelivery.orderservice.exception.BusinessRuleException;
import com.fooddelivery.orderservice.exception.ForbiddenException;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import com.fooddelivery.orderservice.kafka.producer.OrderEventPublisher;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.repository.CartRepository;
import com.fooddelivery.orderservice.repository.OrderRepository;
import com.fooddelivery.orderservice.repository.OrderTimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checkout: turns the customer's cart into an order, exactly once per Idempotency-Key.
 * <ol>
 *   <li>Idempotency: a key that was already used returns the order created the first time.</li>
 *   <li>Remote checks (no database transaction open): restaurant active and open, every item still
 *       orderable, customer active, address belongs to the customer. Then the cart's coupon, if any, is
 *       reserved in promotion-service (422 with the reason if it can no longer be used).</li>
 *   <li>One short transaction: save the order with copies of names, prices, discount and address,
 *       write the first timeline entry, empty the cart. If that fails, the coupon use is given back.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final OrderTimelineRepository orderTimelineRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final UserServiceClient userServiceClient;
    private final RestaurantServiceClient restaurantServiceClient;
    private final CatalogServiceClient catalogServiceClient;
    private final PromotionServiceClient promotionServiceClient;
    private final OrderPricingCalculator orderPricingCalculator;
    private final OrderStatusTransitions orderStatusTransitions;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    /** @param replayed true when the order already existed for this Idempotency-Key */
    public record PlacementResult(OrderResponse order, boolean replayed) {
    }

    @SuppressWarnings("checkstyle:IllegalCatch") // releases the coupon on any failure, then rethrows
    public PlacementResult placeOrder(Long customerId, String idempotencyKey, PlaceOrderRequest request) {
        String fingerprint = IdempotencyFingerprint.of(request);

        Optional<PlacementResult> replay = findExistingOrder(customerId, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            log.info("Idempotent replay of order placement. orderId={} customerId={}", replay.get().order().id(), customerId);
            return replay.get();
        }

        // Everything from other services is collected before any database transaction starts.
        CartService.CartSnapshot cart = cartService.readCart(customerId);
        if (cart.lines().isEmpty()) {
            throw new BusinessRuleException("Your cart is empty");
        }
        RestaurantSummary restaurant = requireOpenRestaurant(cart.restaurantId());
        List<OrderLine> orderLines = toOrderLines(cart.lines(), restaurant.id());
        UserSummary customer = userServiceClient.findUser(customerId)
                .filter(UserSummary::isActive)
                .orElseThrow(() -> new ForbiddenException("Your account is not active"));
        AddressSummary address = userServiceClient.findAddress(customerId, request.addressId())
                .orElseThrow(() -> new BusinessRuleException("Address " + request.addressId() + " is not in your address book"));
        // Last, once everything else is known to be fine: take one use of the coupon for this checkout.
        String reservationKey = couponReservationKey(customerId, idempotencyKey);
        CouponReservation coupon = cart.couponCode() == null ? null
                : promotionServiceClient.reserve(reservationKey, cart.couponCode(), customerId, restaurant.id(),
                itemsTotal(orderLines));

        try {
            OrderResponse order = transactionTemplate.execute(transaction -> {
                Order savedOrder = orderRepository.saveAndFlush(buildOrder(customerId, customer, restaurant,
                        orderLines, coupon, address, request, idempotencyKey, fingerprint));
                orderStatusTransitions.recordInitialStatus(savedOrder, ActorType.CUSTOMER, customerId,
                        savedOrder.getStatus() == OrderStatus.PLACED
                                ? "Cash on delivery order placed"
                                : "Waiting for online payment");
                // Same transaction: the order and its OrderCreated event are saved together (transactional outbox).
                orderEventPublisher.orderCreated(savedOrder);
                cartRepository.findByCustomerId(customerId).ifPresent(CartService::empty);
                return orderMapper.toResponse(savedOrder,
                        orderTimelineRepository.findByOrderIdOrderByCreatedAtAscIdAsc(savedOrder.getId()));
            });
            log.info("Order placed. orderId={} customerId={} restaurantId={} status={} total={} coupon={}",
                    order.id(), customerId, restaurant.id(), order.status(), order.price().totalAmount(), order.couponCode());
            return new PlacementResult(order, false);
        } catch (DataIntegrityViolationException e) {
            // Two identical requests at the same moment: the unique key (customer_id, idempotency_key)
            // let only one insert through. The other request gets the order that won, which holds the coupon use
            // (both reserved it with the same key).
            Optional<PlacementResult> winner = findExistingOrder(customerId, idempotencyKey, fingerprint);
            if (winner.isEmpty()) {
                releaseCoupon(coupon, reservationKey);
                throw e;
            }
            return winner.get();
        } catch (RuntimeException e) {
            releaseCoupon(coupon, reservationKey);
            throw e;
        }
    }

    /** One reservation per checkout: a retried checkout (same Idempotency-Key) gets the same coupon use back. */
    static String couponReservationKey(Long customerId, String idempotencyKey) {
        return "checkout:" + customerId + ":" + idempotencyKey;
    }

    /**
     * No order was saved: the coupon use is given back now. Best effort: if promotion-service cannot be reached,
     * its clean-up job gives back reservations that never got an order.
     */
    private void releaseCoupon(CouponReservation coupon, String reservationKey) {
        if (coupon == null) {
            return;
        }
        try {
            promotionServiceClient.release(reservationKey);
            log.info("Coupon reservation released after a failed checkout. reservationId={}", coupon.reservationId());
        } catch (RemoteServiceException e) {
            log.warn("Coupon reservation not released; promotion-service will expire it. reservationId={} cause={}",
                    coupon.reservationId(), e.getMessage());
        }
    }

    private static BigDecimal itemsTotal(List<OrderLine> orderLines) {
        return orderLines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<PlacementResult> findExistingOrder(Long customerId, String idempotencyKey, String fingerprint) {
        return transactionTemplate.execute(transaction ->
                orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                        .map(existing -> {
                            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                                throw new BusinessRuleException(
                                        "This Idempotency-Key was already used for a different order request");
                            }
                            return new PlacementResult(orderMapper.toResponse(existing,
                                    orderTimelineRepository.findByOrderIdOrderByCreatedAtAscIdAsc(existing.getId())), true);
                        }));
    }

    private RestaurantSummary requireOpenRestaurant(Long restaurantId) {
        RestaurantSummary restaurant = restaurantServiceClient.findRestaurant(restaurantId)
                .filter(RestaurantSummary::isActive)
                .orElseThrow(() -> new BusinessRuleException("This restaurant is not taking orders"));
        if (!restaurant.openNow()) {
            throw new BusinessRuleException("'" + restaurant.name() + "' is closed right now");
        }
        return restaurant;
    }

    /** Copies current names and prices from catalog-service. Any item that cannot be ordered stops checkout. */
    private List<OrderLine> toOrderLines(List<CartLine> cartLines, Long restaurantId) {
        Map<Long, MenuItemSummary> menuItems = catalogServiceClient.findMenuItems(
                cartLines.stream().map(CartLine::menuItemId).toList());

        List<OrderLine> orderLines = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (CartLine cartLine : cartLines) {
            MenuItemSummary item = menuItems.get(cartLine.menuItemId());
            if (item == null || !item.restaurantId().equals(restaurantId)) {
                problems.add("item " + cartLine.menuItemId() + " is no longer on the menu");
            } else if (!item.isOrderable()) {
                problems.add("'" + item.name() + "' is not available right now");
            } else {
                BigDecimal lineTotal = item.price().multiply(BigDecimal.valueOf(cartLine.quantity()));
                orderLines.add(new OrderLine(item.id(), item.name(), item.foodType(), item.price(),
                        cartLine.quantity(), lineTotal));
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessRuleException("Some items in your cart cannot be ordered: " + String.join("; ", problems)
                    + ". Update your cart and try again.");
        }
        return orderLines;
    }

    private Order buildOrder(Long customerId, UserSummary customer, RestaurantSummary restaurant,
                             List<OrderLine> orderLines, CouponReservation coupon, AddressSummary address,
                             PlaceOrderRequest request, String idempotencyKey, String fingerprint) {
        PriceBreakdown price = orderPricingCalculator.calculate(itemsTotal(orderLines),
                coupon == null ? BigDecimal.ZERO : coupon.discountAmount());

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setCustomerName(customer.fullName());
        order.setCustomerPhone(customer.phone());
        order.setRestaurantId(restaurant.id());
        order.setRestaurantName(restaurant.name());
        order.getLines().addAll(orderLines);
        order.setItemsTotal(price.itemsTotal());
        order.setDiscountAmount(price.discountAmount());
        if (coupon != null) {
            order.setCouponCode(coupon.code());
            order.setCouponReservationId(coupon.reservationId());
        }
        order.setDeliveryFee(price.deliveryFee());
        order.setTaxAmount(price.taxAmount());
        order.setTotalAmount(price.totalAmount());
        order.setCurrency(price.currency());
        order.setDeliveryAddress(new DeliveryAddress(address.line1(), address.line2(), address.city(), address.state(),
                address.postalCode(), address.country(), address.latitude(), address.longitude()));
        order.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentStatus(PaymentStatus.PENDING);
        // Cash on delivery goes straight to the restaurant; online payments wait for payment-service.
        order.setStatus(request.paymentMethod().isOnline() ? OrderStatus.PAYMENT_PENDING : OrderStatus.PLACED);
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(fingerprint);
        return order;
    }
}
