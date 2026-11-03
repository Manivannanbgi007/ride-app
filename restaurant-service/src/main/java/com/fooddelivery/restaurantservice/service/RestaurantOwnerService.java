package com.fooddelivery.restaurantservice.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.fooddelivery.restaurantservice.config.AppProperties;
import com.fooddelivery.restaurantservice.dto.request.OperatingHoursUpdateRequest;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.entity.DocumentType;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.exception.BusinessRuleException;
import com.fooddelivery.restaurantservice.exception.ConflictException;
import com.fooddelivery.restaurantservice.exception.InvalidRequestException;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.kafka.producer.RestaurantEventPublisher;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.validation.OperatingHoursValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** A restaurant owner (RESTAURANT_ADMIN) onboarding and managing their own restaurants. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantOwnerService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final OperatingHoursValidator operatingHoursValidator;
    private final AppProperties appProperties;
    private final RestaurantEventPublisher restaurantEventPublisher;
    private final Clock clock;

    /** Submits a new restaurant. It starts as PENDING until a platform admin reviews it. */
    @Transactional
    public RestaurantDetailsResponse submitRestaurant(Long ownerUserId, RestaurantRequest request) {
        String timeZone = validateRequest(request);
        Instant now = clock.instant();

        Restaurant restaurant = new Restaurant();
        restaurant.setOwnerUserId(ownerUserId);
        restaurantMapper.applyRequest(restaurant, request, timeZone, now);
        restaurant.setStatus(RestaurantStatus.PENDING);
        restaurant.setStatusChangedAt(now);

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        log.info("Restaurant submitted for approval. restaurantId={} ownerUserId={}", savedRestaurant.getId(), ownerUserId);
        restaurantEventPublisher.restaurantSubmitted(savedRestaurant);

        return restaurantMapper.toDetails(savedRestaurant);
    }

    @Transactional(readOnly = true)
    public List<RestaurantSummaryResponse> getMyRestaurants(Long ownerUserId) {
        return restaurantRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(restaurantMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantDetailsResponse getMyRestaurant(Long ownerUserId, Long restaurantId) {
        return restaurantMapper.toDetails(findOwnedRestaurant(ownerUserId, restaurantId));
    }

    @Transactional
    public RestaurantDetailsResponse updateRestaurant(Long ownerUserId, Long restaurantId, RestaurantRequest request) {
        if (request.version() == null) {
            throw new InvalidRequestException("version is required when updating a restaurant");
        }

        Restaurant restaurant = findOwnedRestaurant(ownerUserId, restaurantId);
        ensureEditable(restaurant);
        ensureSameVersion(restaurant, request.version());
        String timeZone = validateRequest(request);

        restaurantMapper.applyRequest(restaurant, request, timeZone, clock.instant());
        restaurantRepository.flush();
        log.info("Restaurant updated by owner. restaurantId={} ownerUserId={}", restaurantId, ownerUserId);
        restaurantEventPublisher.profileUpdated(restaurant);

        return restaurantMapper.toDetails(restaurant);
    }

    @Transactional
    public RestaurantDetailsResponse updateOperatingHours(Long ownerUserId, Long restaurantId,
                                                          OperatingHoursUpdateRequest request) {
        Restaurant restaurant = findOwnedRestaurant(ownerUserId, restaurantId);
        ensureEditable(restaurant);
        ensureSameVersion(restaurant, request.version());
        operatingHoursValidator.validate(request.operatingHours());

        restaurantMapper.replaceOperatingHours(restaurant, request.operatingHours());
        restaurantRepository.flush();
        log.info("Operating hours updated. restaurantId={} ownerUserId={}", restaurantId, ownerUserId);
        restaurantEventPublisher.profileUpdated(restaurant);

        return restaurantMapper.toDetails(restaurant);
    }

    /** After fixing the problems an admin pointed out, the owner sends a REJECTED restaurant for review again. */
    @Transactional
    public RestaurantDetailsResponse resubmitRestaurant(Long ownerUserId, Long restaurantId) {
        Restaurant restaurant = findOwnedRestaurant(ownerUserId, restaurantId);
        RestaurantStatus previousStatus = restaurant.getStatus();

        if (!previousStatus.canChangeTo(RestaurantStatus.PENDING)) {
            throw new ConflictException("Only REJECTED restaurants can be resubmitted (current status: "
                    + previousStatus + ")");
        }

        restaurant.setStatus(RestaurantStatus.PENDING);
        restaurant.setStatusReason(null);
        restaurant.setStatusChangedAt(clock.instant());
        restaurantRepository.flush();
        log.info("Restaurant resubmitted for approval. restaurantId={} ownerUserId={}", restaurantId, ownerUserId);
        restaurantEventPublisher.statusChanged(restaurant, previousStatus);

        return restaurantMapper.toDetails(restaurant);
    }

    /** Business validation of a submission or update. Returns the time zone to store. */
    private String validateRequest(RestaurantRequest request) {
        boolean hasFoodLicense = request.documents().stream()
                .anyMatch(document -> document.type() == DocumentType.FOOD_LICENSE);
        if (!hasFoodLicense) {
            throw new BusinessRuleException("A FOOD_LICENSE document is required");
        }

        operatingHoursValidator.validate(request.operatingHours());
        return resolveTimeZone(request.timeZone());
    }

    private String resolveTimeZone(String timeZone) {
        if (!StringUtils.hasText(timeZone)) {
            return appProperties.defaultTimeZone();
        }
        try {
            return ZoneId.of(timeZone.trim()).getId();
        } catch (DateTimeException e) {
            throw new InvalidRequestException("Unknown time zone: " + timeZone + " (use an IANA name such as Asia/Kolkata)");
        }
    }

    private void ensureEditable(Restaurant restaurant) {
        if (restaurant.getStatus() == RestaurantStatus.SUSPENDED) {
            throw new BusinessRuleException("A suspended restaurant cannot be edited. Contact platform support.");
        }
    }

    /** Optimistic locking across requests: the owner must be editing the latest version. */
    private void ensureSameVersion(Restaurant restaurant, Long expectedVersion) {
        if (!restaurant.getVersion().equals(expectedVersion)) {
            throw new ConflictException("The restaurant was changed by another request (current version "
                    + restaurant.getVersion() + ", yours " + expectedVersion + "). Reload it and try again.");
        }
    }

    private Restaurant findOwnedRestaurant(Long ownerUserId, Long restaurantId) {
        return restaurantRepository.findByIdAndOwnerUserId(restaurantId, ownerUserId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }
}
