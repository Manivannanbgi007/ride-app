package com.fooddelivery.restaurantservice.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.restaurantservice.dto.request.AdminRestaurantSearchCriteria;
import com.fooddelivery.restaurantservice.dto.response.AuditLogResponse;
import com.fooddelivery.restaurantservice.dto.response.PageResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.entity.AuditAction;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.exception.ConflictException;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.kafka.producer.RestaurantEventPublisher;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.repository.RestaurantSpecifications;
import com.fooddelivery.restaurantservice.validation.SortValidator;
import com.fooddelivery.restaurantservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Platform admins reviewing and controlling restaurants.
 * Every status change follows {@link RestaurantStatus} rules and is audited in the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRestaurantService {

    private static final List<String> SORTABLE_FIELDS = List.of("createdAt", "name", "status", "statusChangedAt");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final AdminAuditService adminAuditService;
    private final RestaurantEventPublisher restaurantEventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummaryResponse> searchRestaurants(AdminRestaurantSearchCriteria criteria,
                                                                   Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);

        List<Specification<Restaurant>> conditions = new ArrayList<>();
        if (StringUtils.hasText(criteria.search())) {
            conditions.add(RestaurantSpecifications.matchesAdminSearch(criteria.search()));
        }
        if (criteria.status() != null) {
            conditions.add(RestaurantSpecifications.hasStatus(criteria.status()));
        }
        if (StringUtils.hasText(criteria.city())) {
            conditions.add(RestaurantSpecifications.inCity(criteria.city()));
        }

        return PageResponse.from(restaurantRepository.findAll(Specification.allOf(conditions), pageable)
                .map(restaurantMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public RestaurantDetailsResponse getRestaurant(Long restaurantId) {
        return restaurantMapper.toDetails(findRestaurant(restaurantId));
    }

    @Transactional
    public RestaurantDetailsResponse approveRestaurant(Long restaurantId, String reason, CallerInfo admin) {
        return changeStatus(restaurantId, RestaurantStatus.APPROVED, AuditAction.RESTAURANT_APPROVED, reason, admin);
    }

    @Transactional
    public RestaurantDetailsResponse rejectRestaurant(Long restaurantId, String reason, CallerInfo admin) {
        return changeStatus(restaurantId, RestaurantStatus.REJECTED, AuditAction.RESTAURANT_REJECTED, reason, admin);
    }

    @Transactional
    public RestaurantDetailsResponse activateRestaurant(Long restaurantId, String reason, CallerInfo admin) {
        return changeStatus(restaurantId, RestaurantStatus.ACTIVE, AuditAction.RESTAURANT_ACTIVATED, reason, admin);
    }

    @Transactional
    public RestaurantDetailsResponse deactivateRestaurant(Long restaurantId, String reason, CallerInfo admin) {
        return changeStatus(restaurantId, RestaurantStatus.INACTIVE, AuditAction.RESTAURANT_DEACTIVATED, reason, admin);
    }

    @Transactional
    public RestaurantDetailsResponse suspendRestaurant(Long restaurantId, String reason, CallerInfo admin) {
        return changeStatus(restaurantId, RestaurantStatus.SUSPENDED, AuditAction.RESTAURANT_SUSPENDED, reason, admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getRestaurantAuditLogs(Long restaurantId, Pageable pageable) {
        findRestaurant(restaurantId);
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_RESTAURANT, restaurantId, pageable);
    }

    private RestaurantDetailsResponse changeStatus(Long restaurantId, RestaurantStatus newStatus, AuditAction action,
                                                   String reason, CallerInfo admin) {
        Restaurant restaurant = findRestaurant(restaurantId);
        RestaurantStatus oldStatus = restaurant.getStatus();

        if (!oldStatus.canChangeTo(newStatus)) {
            throw new ConflictException("Cannot change restaurant " + restaurantId + " from " + oldStatus + " to " + newStatus);
        }

        Instant now = clock.instant();
        restaurant.setStatus(newStatus);
        restaurant.setStatusReason(reason);
        restaurant.setStatusChangedAt(now);
        if (newStatus == RestaurantStatus.APPROVED) {
            restaurant.setApprovedAt(now);
        }

        adminAuditService.recordAction(admin, action, AdminAuditService.ENTITY_TYPE_RESTAURANT, restaurantId,
                oldStatus.name(), newStatus.name(), reason);
        log.info("Restaurant status changed by admin. restaurantId={} oldStatus={} newStatus={} adminUserId={}",
                restaurantId, oldStatus, newStatus, admin.userId());

        // Write now: the response and the event show the new version, and a concurrent change is reported as 409 here.
        restaurantRepository.flush();
        restaurantEventPublisher.statusChanged(restaurant, oldStatus);
        return restaurantMapper.toDetails(restaurant);
    }

    private Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }
}
