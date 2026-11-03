package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fooddelivery.catalogservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.catalogservice.repository.CategoryRepository;
import com.fooddelivery.catalogservice.repository.IdVersion;
import com.fooddelivery.catalogservice.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;

/** The lists of ids and versions that search-service compares its indexes with. */
class SearchFeedServiceTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final MenuItemRepository menuItems = mock(MenuItemRepository.class);
    private final SearchFeedService service = new SearchFeedService(categories, menuItems);

    private static IdVersion row(long id, long version) {
        return new IdVersion() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getVersion() {
                return version;
            }
        };
    }

    @Test
    void menuItemsComeInIdOrderWithTheNextPageStart() {
        when(menuItems.findVersionsAfter(eq(10L), any())).thenReturn(List.of(row(11, 2), row(14, 5)));

        SourceVersionsResponse page = service.getMenuItemVersions(10, 2);

        assertThat(page.entries()).containsExactly(new SourceVersionsResponse.Entry(11, 2),
                new SourceVersionsResponse.Entry(14, 5));
        assertThat(page.nextAfterId()).isEqualTo(14L);
        verifyNoInteractions(categories);
    }

    @Test
    void categoriesEndWithAShortPage() {
        when(categories.findVersionsAfter(eq(0L), any())).thenReturn(List.of(row(1, 1), row(2, 3)));

        SourceVersionsResponse page = service.getCategoryVersions(0, 1000);

        assertThat(page.entries()).hasSize(2);
        assertThat(page.nextAfterId()).isNull();
        verifyNoInteractions(menuItems);
    }
}
