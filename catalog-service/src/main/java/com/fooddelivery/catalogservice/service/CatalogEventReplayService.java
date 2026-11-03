package com.fooddelivery.catalogservice.service;

import com.fooddelivery.catalogservice.dto.response.RepublishResponse;
import com.fooddelivery.catalogservice.entity.AuditAction;
import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.CategoryChange;
import com.fooddelivery.catalogservice.kafka.producer.CatalogEventPublisher.MenuItemChange;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import com.fooddelivery.catalogservice.web.CallerInfo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the current state of every category or menu item again (changeType REPUBLISHED). MySQL is the
 * source of truth: a search index that was rebuilt (or a consumer that started late) catches up from these
 * events. Consumers keep copies by version, so for them a republished item changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogEventReplayService {

    private static final int PAGE_SIZE = 200;

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final CatalogEventPublisher catalogEventPublisher;
    private final AdminAuditService adminAuditService;
    private final EntityManager entityManager;

    /** One transaction: the events go to the outbox together with the audit record, or not at all. */
    @Transactional
    public RepublishResponse republishCategories(CallerInfo admin) {
        int republished = 0;
        for (Category category : categoryRepository.findAllByOrderByDisplayOrderAsc()) {
            catalogEventPublisher.categoryChanged(category, CategoryChange.REPUBLISHED);
            republished++;
        }
        adminAuditService.recordAction(admin, AuditAction.CATEGORY_EVENTS_REPUBLISHED, AdminAuditService.ENTITY_TYPE_CATEGORY,
                "ALL", null, republished + " categories", null);
        log.info("Category events republished. count={} adminUserId={}", republished, admin.userId());
        return new RepublishResponse(republished);
    }

    @Transactional
    public RepublishResponse republishMenuItems(CallerInfo admin) {
        int republished = 0;
        Page<MenuItem> page;
        int pageNumber = 0;
        do {
            page = menuItemRepository.findAll(PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
            page.forEach(item -> catalogEventPublisher.menuItemChanged(item, MenuItemChange.REPUBLISHED));
            republished += page.getNumberOfElements();
            // Keep memory flat for big menus: the outbox rows are written, the items no longer needed
            entityManager.flush();
            entityManager.clear();
        } while (page.hasNext());

        adminAuditService.recordAction(admin, AuditAction.MENU_ITEM_EVENTS_REPUBLISHED, AdminAuditService.ENTITY_TYPE_MENU_ITEM,
                "ALL", null, republished + " menu items", null);
        log.info("Menu item events republished. count={} adminUserId={}", republished, admin.userId());
        return new RepublishResponse(republished);
    }
}
