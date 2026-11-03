package com.fooddelivery.promotionservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.promotionservice.dto.request.CouponRequest;
import com.fooddelivery.promotionservice.dto.request.CouponSearchCriteria;
import com.fooddelivery.promotionservice.dto.request.CouponUpdateRequest;
import com.fooddelivery.promotionservice.dto.response.AuditLogResponse;
import com.fooddelivery.promotionservice.dto.response.CouponDetailsResponse;
import com.fooddelivery.promotionservice.dto.response.CouponResponse;
import com.fooddelivery.promotionservice.dto.response.PageResponse;
import com.fooddelivery.promotionservice.dto.response.RedemptionResponse;
import com.fooddelivery.promotionservice.entity.AuditAction;
import com.fooddelivery.promotionservice.entity.Coupon;
import com.fooddelivery.promotionservice.entity.CouponStatus;
import com.fooddelivery.promotionservice.entity.DiscountType;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import com.fooddelivery.promotionservice.exception.BusinessRuleException;
import com.fooddelivery.promotionservice.exception.ConflictException;
import com.fooddelivery.promotionservice.exception.InvalidRequestException;
import com.fooddelivery.promotionservice.exception.ResourceNotFoundException;
import com.fooddelivery.promotionservice.mapper.CouponMapper;
import com.fooddelivery.promotionservice.repository.CouponRedemptionRepository;
import com.fooddelivery.promotionservice.repository.CouponRepository;
import com.fooddelivery.promotionservice.repository.CouponSpecifications;
import com.fooddelivery.promotionservice.repository.RedemptionTotals;
import com.fooddelivery.promotionservice.validation.SortValidator;
import com.fooddelivery.promotionservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Platform admins managing coupons and watching how they are used. Every change is audited. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private static final List<String> SORTABLE_FIELDS = List.of("code", "createdAt", "validFrom", "validUntil",
            "timesUsed", "status");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CouponRules couponRules;
    private final CouponMapper couponMapper;
    private final AdminAuditService adminAuditService;

    @Transactional
    public CouponResponse create(CouponRequest request, CallerInfo admin) {
        String code = CouponRedemptionService.normalize(request.code());
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("A coupon " + code + " already exists");
        }
        validateTerms(request.discountType(), request.discountValue(), request.maxDiscount(), request.validFrom(),
                request.validUntil());

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setCreatedBy(admin.userId());
        applyTerms(coupon, request.description(), request.discountType(), request.discountValue(), request.maxDiscount(),
                request.minOrderAmount(), request.restaurantId(), request.validFrom(), request.validUntil(),
                request.usageLimit(), request.perCustomerLimit());
        Coupon saved = couponRepository.saveAndFlush(coupon);

        adminAuditService.recordAction(admin, AuditAction.COUPON_CREATED, AdminAuditService.ENTITY_TYPE_COUPON, saved.getId(),
                null, describe(saved), null);
        log.info("Coupon created. code={} couponId={} adminUserId={}", code, saved.getId(), admin.userId());
        return couponMapper.toResponse(saved, couponRules.today());
    }

    @Transactional
    public CouponResponse update(Long couponId, CouponUpdateRequest request, CallerInfo admin) {
        Coupon coupon = findCoupon(couponId);
        if (!coupon.getVersion().equals(request.version())) {
            throw new ConflictException("The coupon was changed by someone else (version " + coupon.getVersion()
                    + "). Reload it and try again.");
        }
        validateTerms(request.discountType(), request.discountValue(), request.maxDiscount(), request.validFrom(),
                request.validUntil());
        if (request.usageLimit() != null && request.usageLimit() < coupon.getTimesUsed()) {
            throw new BusinessRuleException("The usage limit cannot be lower than the current uses ("
                    + coupon.getTimesUsed() + ")");
        }

        String before = describe(coupon);
        applyTerms(coupon, request.description(), request.discountType(), request.discountValue(), request.maxDiscount(),
                request.minOrderAmount(), request.restaurantId(), request.validFrom(), request.validUntil(),
                request.usageLimit(), request.perCustomerLimit());
        couponRepository.flush();

        adminAuditService.recordAction(admin, AuditAction.COUPON_UPDATED, AdminAuditService.ENTITY_TYPE_COUPON, couponId,
                before, describe(coupon), null);
        return couponMapper.toResponse(coupon, couponRules.today());
    }

    @Transactional
    public CouponResponse activate(Long couponId, String reason, CallerInfo admin) {
        return changeStatus(couponId, CouponStatus.ACTIVE, AuditAction.COUPON_ACTIVATED, reason, admin);
    }

    /** Stops new uses at once. Orders already placed keep their discount. */
    @Transactional
    public CouponResponse deactivate(Long couponId, String reason, CallerInfo admin) {
        return changeStatus(couponId, CouponStatus.INACTIVE, AuditAction.COUPON_DEACTIVATED, reason, admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> search(CouponSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        List<Specification<Coupon>> conditions = new ArrayList<>();
        if (StringUtils.hasText(criteria.code())) {
            conditions.add(CouponSpecifications.codeContains(criteria.code().trim()));
        }
        if (criteria.status() != null) {
            conditions.add(CouponSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.discountType() != null) {
            conditions.add(CouponSpecifications.hasDiscountType(criteria.discountType()));
        }
        if (criteria.restaurantId() != null) {
            conditions.add(CouponSpecifications.forRestaurant(criteria.restaurantId()));
        }
        if (criteria.validOn() != null) {
            conditions.add(CouponSpecifications.validOn(criteria.validOn()));
        }
        if (criteria.activeFrom() != null && criteria.activeTo() != null
                && criteria.activeTo().isBefore(criteria.activeFrom())) {
            throw new InvalidRequestException("activeTo must not be before activeFrom");
        }
        if (criteria.activeFrom() != null) {
            conditions.add(CouponSpecifications.validUntilOnOrAfter(criteria.activeFrom()));
        }
        if (criteria.activeTo() != null) {
            conditions.add(CouponSpecifications.validFromOnOrBefore(criteria.activeTo()));
        }
        LocalDate today = couponRules.today();
        return PageResponse.from(couponRepository.findAll(Specification.allOf(conditions), pageable)
                .map(coupon -> couponMapper.toResponse(coupon, today)));
    }

    /** The coupon with its usage: uses per status, discount given, customers. */
    @Transactional(readOnly = true)
    public CouponDetailsResponse getCoupon(Long couponId) {
        Coupon coupon = findCoupon(couponId);
        Map<RedemptionStatus, Long> usesByStatus = new EnumMap<>(RedemptionStatus.class);
        for (RedemptionStatus status : RedemptionStatus.values()) {
            usesByStatus.put(status, 0L);
        }
        BigDecimal discountGiven = BigDecimal.ZERO;
        for (RedemptionTotals totals : redemptionRepository.totalsForCoupon(couponId)) {
            usesByStatus.put(totals.getStatus(), totals.getUses());
            if (totals.getStatus() == RedemptionStatus.APPLIED || totals.getStatus() == RedemptionStatus.REDEEMED) {
                discountGiven = discountGiven.add(totals.getDiscount());
            }
        }
        return new CouponDetailsResponse(couponMapper.toResponse(coupon, couponRules.today()), usesByStatus,
                discountGiven.setScale(2, RoundingMode.HALF_UP), redemptionRepository.countDistinctCustomers(couponId));
    }

    @Transactional(readOnly = true)
    public PageResponse<RedemptionResponse> getRedemptions(Long couponId, RedemptionStatus status, Pageable pageable) {
        findCoupon(couponId);
        var page = status == null
                ? redemptionRepository.findByCouponId(couponId, pageable)
                : redemptionRepository.findByCouponIdAndStatus(couponId, status, pageable);
        return PageResponse.from(page.map(couponMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLogs(Long couponId, Pageable pageable) {
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_COUPON, couponId, pageable);
    }

    private CouponResponse changeStatus(Long couponId, CouponStatus newStatus, AuditAction action, String reason,
                                        CallerInfo admin) {
        Coupon coupon = findCoupon(couponId);
        CouponStatus oldStatus = coupon.getStatus();
        if (oldStatus == newStatus) {
            throw new ConflictException("Coupon " + coupon.getCode() + " is already " + newStatus);
        }
        coupon.setStatus(newStatus);
        couponRepository.flush();
        adminAuditService.recordAction(admin, action, AdminAuditService.ENTITY_TYPE_COUPON, couponId, oldStatus.name(),
                newStatus.name(), reason);
        log.info("Coupon status changed. code={} oldStatus={} newStatus={} adminUserId={}",
                coupon.getCode(), oldStatus, newStatus, admin.userId());
        return couponMapper.toResponse(coupon, couponRules.today());
    }

    private static void validateTerms(DiscountType type, BigDecimal value, BigDecimal maxDiscount, LocalDate validFrom,
                                      LocalDate validUntil) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(HUNDRED) > 0) {
            throw new BusinessRuleException("A percentage discount cannot be more than 100");
        }
        if (type == DiscountType.FIXED && maxDiscount != null) {
            throw new BusinessRuleException("maxDiscount only applies to PERCENTAGE coupons");
        }
        if (validUntil.isBefore(validFrom)) {
            throw new BusinessRuleException("validUntil must not be before validFrom");
        }
    }

    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"}) // the coupon's terms, shared by create and update
    private static void applyTerms(Coupon coupon, String description, DiscountType type, BigDecimal value,
                                   BigDecimal maxDiscount, BigDecimal minOrderAmount, Long restaurantId,
                                   LocalDate validFrom, LocalDate validUntil, Integer usageLimit,
                                   Integer perCustomerLimit) {
        coupon.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(value);
        coupon.setMaxDiscount(maxDiscount);
        coupon.setMinOrderAmount(minOrderAmount == null ? BigDecimal.ZERO.setScale(2) : minOrderAmount);
        coupon.setRestaurantId(restaurantId);
        coupon.setValidFrom(validFrom);
        coupon.setValidUntil(validUntil);
        coupon.setUsageLimit(usageLimit);
        coupon.setPerCustomerLimit(perCustomerLimit);
    }

    private static String describe(Coupon coupon) {
        return CouponRules.summary(coupon) + "; min order " + coupon.getMinOrderAmount()
                + "; " + coupon.getValidFrom() + " to " + coupon.getValidUntil()
                + "; limit " + (coupon.getUsageLimit() == null ? "none" : coupon.getUsageLimit())
                + ", per customer " + (coupon.getPerCustomerLimit() == null ? "none" : coupon.getPerCustomerLimit())
                + (coupon.getRestaurantId() == null ? "" : "; restaurant " + coupon.getRestaurantId());
    }

    private Coupon findCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon " + couponId + " not found"));
    }
}
