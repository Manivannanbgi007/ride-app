package com.fooddelivery.searchservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.fooddelivery.searchservice.client.RatingFeedClient;
import com.fooddelivery.searchservice.config.AppProperties;
import com.fooddelivery.searchservice.config.AppProperties.ConsistencyCheck;
import com.fooddelivery.searchservice.config.AppProperties.RatingSync;
import com.fooddelivery.searchservice.config.AppProperties.RemoteService;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.RatingFeed;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/** The parts of the rating sync that need no Elasticsearch: switching it off, failures, a feed that does not move on. */
class RatingSyncServiceTest {

    private final RatingFeedClient feed = mock(RatingFeedClient.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);

    private RatingSyncService service(boolean enabled) {
        RemoteService remote = new RemoteService("http://localhost:8094", Duration.ofSeconds(2), Duration.ofSeconds(10));
        AppProperties properties = new AppProperties(remote, remote, remote,
                new ConsistencyCheck(true, Duration.ofMinutes(5), Duration.ofHours(6)),
                new RatingSync(enabled, Duration.ofSeconds(30), Duration.ofSeconds(60)));
        return new RatingSyncService(feed, operations, properties);
    }

    @Test
    void whenSwitchedOffTheScheduledRunDoesNothing() {
        service(false).syncOnSchedule();

        verifyNoInteractions(feed, operations);
    }

    @Test
    void theScheduledRunNeverThrowsWhenReviewServiceOrElasticsearchFail() {
        when(feed.ratings(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt()))
                .thenThrow(new SourceUnavailableException("review-service", new IllegalStateException("down")));

        assertThatCode(service(true)::syncOnSchedule).doesNotThrowAnyException();
        // Nothing was written
        verifyNoInteractions(operations);

        when(feed.ratings(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt())).thenReturn(new RatingFeed(List.of(), null));
        when(feed.ratings(eq(SearchedIndex.FOOD), anyLong(), anyInt())).thenReturn(new RatingFeed(List.of(), null));
        when(operations.indexOps(RestaurantDocument.class)).thenThrow(new DataAccessResourceFailureException("es down"));

        assertThatCode(service(true)::syncOnSchedule).doesNotThrowAnyException();
    }

    @Test
    void aFeedThatNeverMovesOnStopsTheRun() {
        when(feed.ratings(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt()))
                .thenReturn(new RatingFeed(List.of(new RatingFeed.Item(5, 4.0, 1)), 5L));

        assertThatThrownBy(() -> service(true).sync()).isInstanceOf(SourceUnavailableException.class)
                .hasMessageContaining("does not advance");

        // The first page moves on (0 -> 5), the second one says 5 again
        verify(feed, times(2)).ratings(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt());
        verifyNoInteractions(operations);
    }
}
