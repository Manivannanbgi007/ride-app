package com.fooddelivery.promotionservice.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fooddelivery.promotionservice.discount.DiscountStrategyFactory;
import com.fooddelivery.promotionservice.dto.request.CouponCheckRequest;
import com.fooddelivery.promotionservice.dto.request.ReservationRequest;
import com.fooddelivery.promotionservice.dto.response.CouponCheckResponse;
import com.fooddelivery.promotionservice.dto.response.ReservationResponse;
import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponRedemption;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import com.fooddelivery.promotionservice.exception.BusinessRuleException;
import com.fooddelivery.promotionservice.exception.ConflictException;
import com.fooddelivery.promotionservice.repository.CouponRedemptionRepository;
import com.fooddelivery.promotionservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The life of a coupon use: checked for the cart, reserved at checkout, linked to its order, redeemed on delivery,
 * or given back (order cancelled, or no order placed). The usage counter on the coupon always equals its current
 * uses (reserved, on an order, redeemed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRedemptionService {

    static final Set<RedemptionStatus> USES = EnumSet.of(RedemptionStatus.RESERVED, RedemptionStatus.APPLIED,
            RedemptionStatus.REDEEMED);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CouponRules couponRules;
    private final DiscountStrategyFactory discountStrategyFactory;
    private final Clock clock;

    /** Would the coupon work for this cart, and for how much? Changes nothing. */
    @Transactional(readOnly = true)
    public CouponCheckResponse check(CouponCheckRequest request) {
        String code = normalize(request.code());
        Optional<Coupon> found = couponRepository.findByCode(code);
        if (found.isEmpty()) {
            return new CouponCheckResponse(code, false, BigDecimal.ZERO.setScale(2), "There is no coupon " + code, null);
        }
        Coupon coupon = found.get();
        long customerUses = customerUses(coupon, request.customerId());
        return couponRules.problem(coupon, request.restaurantId(), request.itemsTotal(), customerUses)
                .map(problem -> new CouponCheckResponse(code, false, BigDecimal.ZERO.setScale(2), problem,
                        coupon.getDescription()))
                .orElseGet(() -> new CouponCheckResponse(code, true,
                        discountStrategyFactory.discountFor(coupon, request.itemsTotal()), null, coupon.getDescription()));
    }

    /**
     * Takes one use of the coupon for a checkout. The coupon row stays locked until the transaction ends, so
     * reservations of one coupon happen one after the other: its limits hold even under many simultaneous checkouts.
     * A checkout retried with the same reservation key gets its first reservation back.
     *
     * @throws BusinessRuleException (422) when the coupon cannot be used, with the reason
     */
    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        String code = normalize(request.code());
        Coupon coupon = couponRepository.lockByCode(code)
                .orElseThrow(() -> new BusinessRuleException("There is no coupon " + code));

        // Looked up under the lock: a simultaneous retry of the same checkout sees the first reservation
        Optional<CouponRedemption> existing = redemptionRepository.findByReservationKey(request.reservationKey());
        if (existing.isPresent()) {
            CouponRedemption redemption = existing.get();
            if (!redemption.getCouponCode().equals(code)) {
                throw new ConflictException("This checkout already reserved coupon " + redemption.getCouponCode());
            }
            if (redemption.getStatus().isUse()) {
                return toResponse(redemption);
            }
            if (redemption.getStatus() != RedemptionStatus.EXPIRED || redemption.getOrderId() != null) {
                throw new BusinessRuleException("This checkout's coupon reservation has ended; please check out again");
            }
            // The use was given back after a failed attempt, and the customer retries the same checkout: take it again
            return takeUse(coupon, redemption, request);
        }

        CouponRedemption redemption = new CouponRedemption();
        redemption.setCouponId(coupon.getId());
        redemption.setCouponCode(code);
        redemption.setReservationKey(request.reservationKey());
        redemption.setCustomerId(request.customerId());
        redemption.setRestaurantId(request.restaurantId());
        return takeUse(coupon, redemption, request);
    }

    /** Checks the rules, then counts one use of the (locked) coupon for this reservation. */
    private ReservationResponse takeUse(Coupon coupon, CouponRedemption redemption, ReservationRequest request) {
        couponRules.problem(coupon, request.restaurantId(), request.itemsTotal(), customerUses(coupon, request.customerId()))
                .ifPresent(problem -> {
                    throw new BusinessRuleException(problem);
                });

        redemption.setOrderAmount(request.itemsTotal());
        redemption.setDiscountAmount(discountStrategyFactory.discountFor(coupon, request.itemsTotal()));
        redemption.setStatus(RedemptionStatus.RESERVED);
        redemption.setReservedAt(clock.instant());
        redemption.setReleasedAt(null);
        redemption.setReleaseReason(null);
        CouponRedemption saved = redemptionRepository.saveAndFlush(redemption);
        couponRepository.addUse(coupon.getId());
        log.info("Coupon reserved. code={} reservationId={} customerId={} discount={}",
                coupon.getCode(), saved.getId(), request.customerId(), saved.getDiscountAmount());
        return toResponse(saved);
    }

    /** The checkout failed after the reservation (order-service says so): the use is given back. */
    @Transactional
    public void releaseReservation(String reservationKey) {
        redemptionRepository.findByReservationKey(reservationKey)
                .filter(redemption -> redemption.getStatus() == RedemptionStatus.RESERVED)
                .ifPresent(redemption -> giveBack(redemption, RedemptionStatus.EXPIRED, "Checkout did not complete"));
    }

    /** OrderCreated: the reservation belongs to this order now. */
    @Transactional
    public void applyToOrder(Long reservationId, Long orderId) {
        CouponRedemption redemption = redemptionRepository.findById(reservationId).orElse(null);
        if (redemption == null) {
            log.warn("OrderCreated for an unknown coupon reservation. reservationId={} orderId={}", reservationId, orderId);
            return;
        }
        switch (redemption.getStatus()) {
            case RESERVED -> {
                redemption.setStatus(RedemptionStatus.APPLIED);
                redemption.setOrderId(orderId);
                redemption.setAppliedAt(clock.instant());
                log.info("Coupon applied to an order. code={} orderId={}", redemption.getCouponCode(), orderId);
            }
            case EXPIRED -> {
                // The order arrived after the clean-up gave the use back (Kafka was slow): the customer got the
                // discount, so the use counts again, even if that takes the coupon over its limit.
                redemption.setStatus(RedemptionStatus.APPLIED);
                redemption.setOrderId(orderId);
                redemption.setAppliedAt(clock.instant());
                redemption.setReleasedAt(null);
                redemption.setReleaseReason(null);
                couponRepository.addUse(redemption.getCouponId());
                log.warn("Coupon applied to an order after its reservation had expired. code={} orderId={}",
                        redemption.getCouponCode(), orderId);
            }
            // OrderCancelled was read first (another topic): the order is cancelled, the use stays given back
            case RELEASED -> redemption.setOrderId(Objects.requireNonNullElse(redemption.getOrderId(), orderId));
            case APPLIED, REDEEMED -> {
                // repeated event: nothing to do
            }
        }
    }

    /** OrderCancelled: the use is given back. Works whether or not OrderCreated was read before. */
    @Transactional
    public void releaseForCancelledOrder(Long reservationId, Long orderId, String reason) {
        CouponRedemption redemption = redemptionRepository.findById(reservationId).orElse(null);
        if (redemption == null) {
            log.warn("OrderCancelled for an unknown coupon reservation. reservationId={} orderId={}", reservationId, orderId);
            return;
        }
        if (redemption.getOrderId() == null) {
            redemption.setOrderId(orderId);
        }
        switch (redemption.getStatus()) {
            case RESERVED, APPLIED -> giveBack(redemption, RedemptionStatus.RELEASED, reason);
            case EXPIRED -> {
                // Already given back by the clean-up; record why the order ended
                redemption.setStatus(RedemptionStatus.RELEASED);
                redemption.setReleaseReason(reason);
            }
            case REDEEMED -> log.warn("A delivered order was cancelled; its coupon use stays. orderId={}", orderId);
            case RELEASED -> {
                // repeated event
            }
        }
    }

    /** OrderStatusChanged DELIVERED: the use is final. */
    @Transactional
    public void redeem(Long orderId) {
        redemptionRepository.findByOrderId(orderId)
                .filter(redemption -> redemption.getStatus() == RedemptionStatus.APPLIED)
                .ifPresent(redemption -> {
                    redemption.setStatus(RedemptionStatus.REDEEMED);
                    redemption.setRedeemedAt(clock.instant());
                    log.info("Coupon redeemed. code={} orderId={}", redemption.getCouponCode(), orderId);
                });
    }

    /** The clean-up job: a reservation no order was placed with (one at a time, in its own transaction). */
    @Transactional
    public boolean expireIfStillReserved(Long redemptionId, String reason) {
        return redemptionRepository.findById(redemptionId)
                .filter(redemption -> redemption.getStatus() == RedemptionStatus.RESERVED)
                .map(redemption -> {
                    giveBack(redemption, RedemptionStatus.EXPIRED, reason);
                    return true;
                })
                .orElse(false);
    }

    private void giveBack(CouponRedemption redemption, RedemptionStatus status, String reason) {
        redemption.setStatus(status);
        redemption.setReleaseReason(reason);
        redemption.setReleasedAt(clock.instant());
        redemptionRepository.saveAndFlush(redemption);
        couponRepository.removeUse(redemption.getCouponId());
        log.info("Coupon use given back. code={} reservationId={} status={} reason={}",
                redemption.getCouponCode(), redemption.getId(), status, reason);
    }

    private long customerUses(Coupon coupon, Long customerId) {
        return redemptionRepository.countByCouponIdAndCustomerIdAndStatusIn(coupon.getId(), customerId, USES);
    }

    static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static ReservationResponse toResponse(CouponRedemption redemption) {
        return new ReservationResponse(redemption.getId(), redemption.getCouponCode(), redemption.getDiscountAmount(),
                redemption.getStatus());
    }
}
