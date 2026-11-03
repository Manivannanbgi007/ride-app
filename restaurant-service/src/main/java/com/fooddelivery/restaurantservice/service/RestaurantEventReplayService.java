package com.fooddelivery.restaurantservice.service;

import com.fooddelivery.restaurantservice.dto.response.RepublishResponse;
import com.fooddelivery.restaurantservice.entity.AuditAction;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.kafka.producer.RestaurantEventPublisher;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.web.CallerInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes every restaurant's current state again (changeType REPUBLISHED). MySQL is the source of truth:
 * a consumer that started late, or a search index that was rebuilt, catches up from these events.
 * Consumers keep copies by version, so for them a republished restaurant changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantEventReplayService {

    private static final int PAGE_SIZE = 100;

    private final RestaurantRepository restaurantRepository;
    private final RestaurantEventPublisher restaurantEventPublisher;
    private final AdminAuditService adminAuditService;
    private final EntityManager entityManager;

    /** One transaction: the events go to the outbox together with the audit record, or not at all. */
    @Transactional
    public RepublishResponse republishAll(CallerInfo admin) {
        int republished = 0;
        Page<Restaurant> page;
        int pageNumber = 0;
        do {
            page = restaurantRepository.findAll(PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
            page.forEach(restaurantEventPublisher::republished);
            republished += page.getNumberOfElements();
            // Keep memory flat for many restaurants: the outbox rows are written, the restaurants no longer needed
            entityManager.flush();
            entityManager.clear();
        } while (page.hasNext());

        adminAuditService.recordAction(admin, AuditAction.RESTAURANT_EVENTS_REPUBLISHED,
                AdminAuditService.ENTITY_TYPE_RESTAURANT, "ALL", null, republished + " restaurants", null);
        log.info("Restaurant events republished. count={} adminUserId={}", republished, admin.userId());
        return new RepublishResponse(republished);
    }
}
