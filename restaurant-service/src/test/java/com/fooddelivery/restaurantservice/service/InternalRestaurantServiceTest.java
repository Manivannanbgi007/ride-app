package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fooddelivery.restaurantservice.dto.response.SourceVersionsResponse;
import com.fooddelivery.restaurantservice.repository.IdVersion;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/** The list of restaurant ids and versions that search-service compares its index with. */
class InternalRestaurantServiceTest {

    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final InternalRestaurantService service = new InternalRestaurantService(repository, null);

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
    void aFullPageSaysWhereTheNextOneStarts() {
        when(repository.findVersionsAfter(eq(0L), any())).thenReturn(List.of(row(1, 4), row(2, 1), row(5, 9)));

        SourceVersionsResponse page = service.getVersions(0, 3);

        assertThat(page.entries()).containsExactly(new SourceVersionsResponse.Entry(1, 4),
                new SourceVersionsResponse.Entry(2, 1), new SourceVersionsResponse.Entry(5, 9));
        assertThat(page.nextAfterId()).isEqualTo(5L);
        verify(repository).findVersionsAfter(0L, PageRequest.of(0, 3));
    }

    @Test
    void aShortPageIsTheLastOne() {
        when(repository.findVersionsAfter(eq(5L), any())).thenReturn(List.of(row(6, 2)));

        SourceVersionsResponse page = service.getVersions(5, 1000);

        assertThat(page.entries()).hasSize(1);
        assertThat(page.nextAfterId()).isNull();
    }

    @Test
    void nothingAfterTheLastIdIsAnEmptyLastPage() {
        when(repository.findVersionsAfter(eq(99L), any())).thenReturn(List.of());

        SourceVersionsResponse page = service.getVersions(99, 1000);

        assertThat(page.entries()).isEmpty();
        assertThat(page.nextAfterId()).isNull();
    }
}
