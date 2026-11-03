package com.fooddelivery.promotionservice.controller;

import com.fooddelivery.promotionservice.dto.request.CouponRequest;
import com.fooddelivery.promotionservice.dto.request.CouponSearchCriteria;
import com.fooddelivery.promotionservice.dto.request.CouponUpdateRequest;
import com.fooddelivery.promotionservice.dto.request.StatusChangeRequest;
import com.fooddelivery.promotionservice.dto.response.AuditLogResponse;
import com.fooddelivery.promotionservice.dto.response.CouponDetailsResponse;
import com.fooddelivery.promotionservice.dto.response.CouponResponse;
import com.fooddelivery.promotionservice.dto.response.PageResponse;
import com.fooddelivery.promotionservice.dto.response.RedemptionResponse;
import com.fooddelivery.promotionservice.entity.RedemptionStatus;
import com.fooddelivery.promotionservice.service.CouponAdminService;
import com.fooddelivery.promotionservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: coupons", description = "Create and manage coupons, watch their usage (PLATFORM_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponAdminService couponAdminService;

    @Operation(summary = "Create a coupon (ACTIVE at once; audited)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponRequest request, CallerInfo caller) {
        return couponAdminService.create(request, caller);
    }

    @Operation(summary = "Search coupons", description = "Usage monitoring: timesUsed against usageLimit, availability. "
            + "activeFrom/activeTo: coupons whose validity overlaps that period (used by the admin calendar; at most 100 "
            + "per page).")
    @GetMapping
    public PageResponse<CouponResponse> search(
            @Valid @ParameterObject CouponSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return couponAdminService.search(criteria, pageable);
    }

    @Operation(summary = "One coupon with its usage", description = "Uses per status, discount given, customers")
    @GetMapping("/{couponId}")
    public CouponDetailsResponse getCoupon(@PathVariable Long couponId) {
        return couponAdminService.getCoupon(couponId);
    }

    @Operation(summary = "Change a coupon's terms (audited)",
            description = "The code cannot change; placed orders keep their discount")
    @PutMapping("/{couponId}")
    public CouponResponse update(@PathVariable Long couponId, @Valid @RequestBody CouponUpdateRequest request,
                                 CallerInfo caller) {
        return couponAdminService.update(couponId, request, caller);
    }

    @Operation(summary = "Activate a coupon (audited)")
    @PutMapping("/{couponId}/activate")
    public CouponResponse activate(@PathVariable Long couponId, @Valid @RequestBody StatusChangeRequest request,
                                   CallerInfo caller) {
        return couponAdminService.activate(couponId, request.reason(), caller);
    }

    @Operation(summary = "Deactivate a coupon (audited)", description = "Stops new uses at once")
    @PutMapping("/{couponId}/deactivate")
    public CouponResponse deactivate(@PathVariable Long couponId, @Valid @RequestBody StatusChangeRequest request,
                                     CallerInfo caller) {
        return couponAdminService.deactivate(couponId, request.reason(), caller);
    }

    @Operation(summary = "The uses of a coupon (newest first)", description = "Filter with ?status=REDEEMED")
    @GetMapping("/{couponId}/redemptions")
    public PageResponse<RedemptionResponse> getRedemptions(
            @PathVariable Long couponId,
            @RequestParam(required = false) RedemptionStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "reservedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return couponAdminService.getRedemptions(couponId, status, pageable);
    }

    @Operation(summary = "Audit history of a coupon (newest first)")
    @GetMapping("/{couponId}/audit-logs")
    public PageResponse<AuditLogResponse> getAuditLogs(@PathVariable Long couponId,
                                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return couponAdminService.getAuditLogs(couponId, pageable);
    }
}
