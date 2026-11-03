package com.fooddelivery.restaurantservice.controller;

import java.time.LocalDate;

import com.fooddelivery.restaurantservice.dto.request.AdminRestaurantSearchCriteria;
import com.fooddelivery.restaurantservice.dto.request.StatusChangeRequest;
import com.fooddelivery.restaurantservice.dto.response.AdminRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.AuditLogResponse;
import com.fooddelivery.restaurantservice.dto.response.PageResponse;
import com.fooddelivery.restaurantservice.dto.response.RegistrationStatsResponse;
import com.fooddelivery.restaurantservice.dto.response.RepublishResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantStatsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.service.AdminRestaurantDetailsService;
import com.fooddelivery.restaurantservice.service.AdminRestaurantService;
import com.fooddelivery.restaurantservice.service.RestaurantEventReplayService;
import com.fooddelivery.restaurantservice.service.RestaurantStatsService;
import com.fooddelivery.restaurantservice.web.CallerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform admins managing all restaurants. Access restricted to PLATFORM_ADMIN by RoleAccessInterceptor. */
@Tag(name = "Admin - Restaurants", description = "Restaurant onboarding review and status control")
@RestController
@RequestMapping("/api/v1/admin/restaurants")
@RequiredArgsConstructor
public class AdminRestaurantController {

    private final AdminRestaurantService adminRestaurantService;
    private final AdminRestaurantDetailsService adminRestaurantDetailsService;
    private final RestaurantEventReplayService restaurantEventReplayService;
    private final RestaurantStatsService restaurantStatsService;

    @Operation(summary = "List restaurants with search, filters, paging and sorting",
            description = "Pending approvals: ?status=PENDING&sort=createdAt,asc")
    @GetMapping
    public PageResponse<RestaurantSummaryResponse> searchRestaurants(
            @Valid @ParameterObject AdminRestaurantSearchCriteria criteria,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminRestaurantService.searchRestaurants(criteria, pageable);
    }

    @Operation(summary = "Restaurant numbers: per status now, and restaurants submitted in a period (default: today)")
    @GetMapping("/stats")
    public RestaurantStatsResponse getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return restaurantStatsService.getStats(fromDate, toDate);
    }

    @Operation(summary = "Restaurants submitted per day, for the admin calendar (up to 400 days)",
            description = "Only days with registrations. Both dates are required: calendar days in the business time zone.")
    @GetMapping("/stats/registrations")
    public RegistrationStatsResponse getRegistrations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return restaurantStatsService.getRegistrations(from, to);
    }

    @Operation(summary = "Restaurant review details: business info, documents, hours and owner")
    @GetMapping("/{restaurantId}")
    public AdminRestaurantDetailsResponse getRestaurant(@PathVariable Long restaurantId) {
        return adminRestaurantDetailsService.getRestaurantDetails(restaurantId);
    }

    @Operation(summary = "Approve a PENDING restaurant")
    @PutMapping("/{restaurantId}/approve")
    public RestaurantDetailsResponse approveRestaurant(@PathVariable Long restaurantId,
                                                       @Valid @RequestBody StatusChangeRequest request,
                                                       CallerInfo caller) {
        return adminRestaurantService.approveRestaurant(restaurantId, request.reason(), caller);
    }

    @Operation(summary = "Reject a PENDING restaurant (the reason is shown to the owner)")
    @PutMapping("/{restaurantId}/reject")
    public RestaurantDetailsResponse rejectRestaurant(@PathVariable Long restaurantId,
                                                      @Valid @RequestBody StatusChangeRequest request,
                                                      CallerInfo caller) {
        return adminRestaurantService.rejectRestaurant(restaurantId, request.reason(), caller);
    }

    @Operation(summary = "Activate (go live after approval, or reinstate)")
    @PutMapping("/{restaurantId}/activate")
    public RestaurantDetailsResponse activateRestaurant(@PathVariable Long restaurantId,
                                                        @Valid @RequestBody StatusChangeRequest request,
                                                        CallerInfo caller) {
        return adminRestaurantService.activateRestaurant(restaurantId, request.reason(), caller);
    }

    @Operation(summary = "Deactivate an ACTIVE restaurant")
    @PutMapping("/{restaurantId}/deactivate")
    public RestaurantDetailsResponse deactivateRestaurant(@PathVariable Long restaurantId,
                                                          @Valid @RequestBody StatusChangeRequest request,
                                                          CallerInfo caller) {
        return adminRestaurantService.deactivateRestaurant(restaurantId, request.reason(), caller);
    }

    @Operation(summary = "Suspend a restaurant (for example a policy violation)")
    @PutMapping("/{restaurantId}/suspend")
    public RestaurantDetailsResponse suspendRestaurant(@PathVariable Long restaurantId,
                                                       @Valid @RequestBody StatusChangeRequest request,
                                                       CallerInfo caller) {
        return adminRestaurantService.suspendRestaurant(restaurantId, request.reason(), caller);
    }

    @Operation(summary = "Audit history of a restaurant (newest first)")
    @GetMapping("/{restaurantId}/audit-logs")
    public PageResponse<AuditLogResponse> getRestaurantAuditLogs(@PathVariable Long restaurantId,
                                                                 @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminRestaurantService.getRestaurantAuditLogs(restaurantId, pageable);
    }

    @Operation(summary = "Publish every restaurant again as a RestaurantChanged event (audited)",
            description = "For consumers that need to catch up, for example after rebuilding the search index. "
                    + "Changes no data; consumers ignore versions they already have.")
    @PostMapping("/events/republish")
    public RepublishResponse republishEvents(CallerInfo caller) {
        return restaurantEventReplayService.republishAll(caller);
    }
}
