package com.fooddelivery.catalogservice.service;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.IdVersion;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every category's and menu item's id and version, in id order, one page at a time (keyset paging: stable while
 * items are added). search-service compares its index with this: MySQL is the truth.
 */
@Service
@RequiredArgsConstructor
public class SearchFeedService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;

    @Transactional(readOnly = true)
    public SourceVersionsResponse getCategoryVersions(long afterId, int size) {
        return page(categoryRepository.findVersionsAfter(afterId, PageRequest.of(0, size)), size);
    }

    @Transactional(readOnly = true)
    public SourceVersionsResponse getMenuItemVersions(long afterId, int size) {
        return page(menuItemRepository.findVersionsAfter(afterId, PageRequest.of(0, size)), size);
    }

    private static SourceVersionsResponse page(List<IdVersion> rows, int size) {
        List<SourceVersionsResponse.Entry> entries = rows.stream()
                .map(row -> new SourceVersionsResponse.Entry(row.getId(), row.getVersion()))
                .toList();
        return new SourceVersionsResponse(entries, entries.size() == size ? entries.getLast().id() : null);
    }
}
