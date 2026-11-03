package com.fooddelivery.restaurantservice.service;

import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.restaurantservice.cache.RestaurantCache;
import com.fooddelivery.restaurantservice.dto.request.RestaurantSearchCriteria;
import com.fooddelivery.restaurantservice.dto.response.PageResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.entity.OperatingHours;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.repository.RestaurantSpecifications;
import com.fooddelivery.restaurantservice.validation.SortValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Customers browsing restaurants. Only ACTIVE restaurants are ever visible here. */
@Service
@RequiredArgsConstructor
public class PublicRestaurantService {

    private static final List<String> SORTABLE_FIELDS = List.of("name", "createdAt");

    private final RestaurantRepository restaurantRepository;
    private final RestaurantMapper restaurantMapper;
    private final OpenNowCalculator openNowCalculator;
    private final RestaurantCache restaurantCache;

    @Transactional(readOnly = true)
    public PageResponse<PublicRestaurantSummaryResponse> browseRestaurants(RestaurantSearchCriteria criteria,
                                                                          Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);

        List<Specification<Restaurant>> conditions = new ArrayList<>();
        conditions.add(RestaurantSpecifications.hasStatus(RestaurantStatus.ACTIVE));
        if (StringUtils.hasText(criteria.search())) {
            conditions.add(RestaurantSpecifications.nameContains(criteria.search()));
        }
        if (StringUtils.hasText(criteria.city())) {
            conditions.add(RestaurantSpecifications.inCity(criteria.city()));
        }
        if (StringUtils.hasText(criteria.cuisine())) {
            conditions.add(RestaurantSpecifications.servesCuisine(criteria.cuisine()));
        }

        return PageResponse.from(restaurantRepository.findAll(Specification.allOf(conditions), pageable)
                .map(restaurant -> restaurantMapper.toPublicSummary(restaurant, isOpenNow(restaurant))));
    }

    /**
     * The page comes from Redis when cached (see RestaurantCache); "open now" is always worked out for this moment,
     * from the (cached) opening hours.
     */
    @Transactional(readOnly = true)
    public PublicRestaurantDetailsResponse getRestaurant(Long restaurantId) {
        PublicRestaurantDetailsResponse page = restaurantCache.details(restaurantId, () -> {
            Restaurant restaurant = restaurantRepository.findByIdAndStatus(restaurantId, RestaurantStatus.ACTIVE)
                    .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
            return restaurantMapper.toPublicDetails(restaurant, false);
        });
        return page.withOpenNow(openNowCalculator.isOpenNow(page.operatingHours().stream()
                .map(hours -> new OperatingHours(hours.dayOfWeek(), hours.opensAt(), hours.closesAt()))
                .toList(), page.timeZone()));
    }

    private boolean isOpenNow(Restaurant restaurant) {
        return openNowCalculator.isOpenNow(restaurant.getOperatingHours(), restaurant.getTimeZone());
    }
}
