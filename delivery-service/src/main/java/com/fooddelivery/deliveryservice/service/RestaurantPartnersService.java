package com.fooddelivery.deliveryservice.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.fooddelivery.deliveryservice.client.RestaurantServiceClient;
import com.fooddelivery.deliveryservice.config.AppProperties;
import com.fooddelivery.deliveryservice.dto.response.RestaurantPartnersResponse;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.repository.PartnerStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A restaurant owner's view of which delivery partners deliver for the restaurant. Ownership is asked of
 * restaurant-service (a header only says who the caller is, not who owns the restaurant); a failing
 * restaurant-service gives 503. Like the owner endpoints of order-service, a restaurant of another owner or an
 * unknown one is a 404.
 */
@Service
@RequiredArgsConstructor
public class RestaurantPartnersService {

    private final RestaurantServiceClient restaurantServiceClient;
    private final PartnerStatsRepository partnerStatsRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    // No transaction here on purpose: the remote call finishes first, so it never holds a database connection
    public RestaurantPartnersResponse getPartners(long ownerUserId, long restaurantId, LocalDate fromDate,
                                                  LocalDate toDate, int limit) {
        requireOwnedRestaurant(restaurantId, ownerUserId);
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, ZoneId.of(appProperties.businessTimeZone()), clock);

        List<RestaurantPartnersResponse.Partner> partners = partnerStatsRepository
                .topPartnersOfRestaurant(restaurantId, period.from(), period.before(), limit).stream()
                .map(row -> new RestaurantPartnersResponse.Partner(row.getPartnerUserId(), row.getPartnerName(),
                        row.getDelivered(), row.getAverageMinutes() == null ? null
                                : row.getAverageMinutes().setScale(1, RoundingMode.HALF_UP).doubleValue()))
                .toList();
        return new RestaurantPartnersResponse(restaurantId, period.fromDate(), period.toDate(), partners);
    }

    private void requireOwnedRestaurant(long restaurantId, long ownerUserId) {
        boolean owned = restaurantServiceClient.findRestaurant(restaurantId)
                .filter(restaurant -> Long.valueOf(ownerUserId).equals(restaurant.ownerUserId()))
                .isPresent();
        if (!owned) {
            throw new ResourceNotFoundException("Restaurant not found: " + restaurantId);
        }
    }
}
