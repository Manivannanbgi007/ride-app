package com.fooddelivery.restaurantservice.service;

import java.util.List;

import com.fooddelivery.restaurantservice.dto.response.InternalRestaurantResponse;
import com.fooddelivery.restaurantservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.IdVersion;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Restaurant data for other microservices, whatever the restaurant's status. */
@Service
@RequiredArgsConstructor
public class InternalRestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OpenNowCalculator openNowCalculator;

    /**
     * Every restaurant's id and version, in id order, one page at a time (keyset paging: stable while restaurants
     * are added). search-service compares its index with this: MySQL is the truth.
     */
    @Transactional(readOnly = true)
    public SourceVersionsResponse getVersions(long afterId, int size) {
        List<IdVersion> page = restaurantRepository.findVersionsAfter(afterId, PageRequest.of(0, size));
        List<SourceVersionsResponse.Entry> entries = page.stream()
                .map(row -> new SourceVersionsResponse.Entry(row.getId(), row.getVersion()))
                .toList();
        return new SourceVersionsResponse(entries, entries.size() == size ? entries.getLast().id() : null);
    }

    @Transactional(readOnly = true)
    public InternalRestaurantResponse getRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .map(this::toInternalResponse)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }

    /** Every restaurant of an owner, whatever its status, newest first (empty when they have none). */
    @Transactional(readOnly = true)
    public List<InternalRestaurantResponse> getRestaurantsOfOwner(Long ownerUserId) {
        return restaurantRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(this::toInternalResponse)
                .toList();
    }

    private InternalRestaurantResponse toInternalResponse(Restaurant restaurant) {
        return new InternalRestaurantResponse(
                restaurant.getId(),
                restaurant.getOwnerUserId(),
                restaurant.getName(),
                restaurant.getStatus(),
                openNowCalculator.isOpenNow(restaurant.getOperatingHours(), restaurant.getTimeZone()),
                restaurant.getVersion(),
                RestaurantMapper.toAddressResponse(restaurant.getAddress()));
    }
}
