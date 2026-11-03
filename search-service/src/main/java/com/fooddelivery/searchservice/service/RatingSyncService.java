package com.fooddelivery.searchservice.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fooddelivery.searchservice.client.RatingFeedClient;
import com.fooddelivery.searchservice.config.AppProperties;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.RatingFeed;
import com.fooddelivery.searchservice.dto.response.RatingSyncResult;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps a copy of the ratings (average and count) of restaurants and dishes in their indexes, so search can sort and
 * filter by rating. review-service owns the ratings in its own MySQL; this service only reads its internal feed and
 * never touches that database.
 * <p>
 * Every run pulls both feeds completely (page by page, keyset paging) and only then writes, so a review-service that
 * fails half way changes nothing. Then, per index:
 * <ul>
 *   <li><b>Update</b>: a scripted update-by-query over the ids of the feed, in chunks, that writes only
 *       {@code ratingAverage} and {@code ratingCount}. It can only change documents that exist: ids that are not
 *       indexed (yet) are ignored, and no half-empty document is ever created. A document that already carries the
 *       reported rating is left alone (a no-op), so a run that changes nothing rewrites nothing.</li>
 *   <li><b>Reset</b>: the ids of all documents that carry a rating are read from the index itself, and the ones that
 *       are not in the feed any more (all their reviews were deleted) go back to "no ratings": count 0, average
 *       removed. Because the index is the memory, this also works after a restart, and the service keeps no state.</li>
 * </ul>
 * If review-service cannot be reached, the run is logged and the old values stay: nothing is zeroed on a failure.
 * The index is a copy, so a rating written at the wrong moment is repaired by the next run (see SearchIndexer).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingSyncService {

    /** Ids asked from review-service per request (its maximum is 5000). */
    static final int PAGE_SIZE = 1000;
    /** Ids per update request to Elasticsearch. */
    private static final int CHUNK = 500;
    private static final String RATING_COUNT = "ratingCount";

    private static final String APPLY_SCRIPT = """
            def rating = params.ratings.get(ctx._id);
            if (rating == null
                    || (ctx._source.ratingAverage == rating.average && ctx._source.ratingCount == rating.count)) {
                ctx.op = 'noop';
                return;
            }
            ctx._source.ratingAverage = rating.average;
            ctx._source.ratingCount = rating.count;
            """;
    private static final String RESET_SCRIPT = """
            ctx._source.ratingCount = 0;
            ctx._source.remove('ratingAverage');
            """;

    private final RatingFeedClient feed;
    private final ElasticsearchOperations operations;
    private final AppProperties properties;
    // One run at a time: the scheduler and an admin who asks for a run must not interleave their writes
    private final ReentrantLock running = new ReentrantLock();

    /** The scheduled run. Never throws: the next run tries again, and the stored ratings stay as they are. */
    @Scheduled(initialDelayString = "${app.rating-sync.initial-delay:30s}",
            fixedDelayString = "${app.rating-sync.interval:60s}")
    public void syncOnSchedule() {
        if (!properties.ratingSync().enabled()) {
            return;
        }
        try {
            RatingSyncResult result = sync();
            log.debug("Ratings synced. restaurantsUpdated={} foodsUpdated={} restaurantsReset={} foodsReset={}",
                    result.restaurantsUpdated(), result.foodsUpdated(), result.restaurantsReset(), result.foodsReset());
        } catch (SourceUnavailableException e) {
            log.warn("Rating sync skipped, the stored ratings stay as they are: {}", e.getMessage());
        } catch (DataAccessException e) {
            log.warn("Rating sync failed, the stored ratings stay as they are: {}", e.toString());
        }
    }

    /**
     * One full run.
     *
     * @throws SourceUnavailableException review-service did not answer (nothing was changed)
     */
    public RatingSyncResult sync() {
        running.lock();
        try {
            Map<Long, RatingFeed.Item> restaurants = pull(SearchedIndex.RESTAURANT);
            Map<Long, RatingFeed.Item> foods = pull(SearchedIndex.FOOD);
            Outcome restaurantOutcome = apply(RestaurantDocument.class, RestaurantDocument.INDEX, restaurants);
            Outcome foodOutcome = apply(FoodDocument.class, FoodDocument.INDEX, foods);
            if (restaurantOutcome.reset() > 0 || foodOutcome.reset() > 0) {
                log.info("Ratings removed from search, their reviews are gone. restaurants={} foods={}",
                        restaurantOutcome.reset(), foodOutcome.reset());
            }
            return new RatingSyncResult(restaurantOutcome.updated(), foodOutcome.updated(),
                    restaurantOutcome.reset(), foodOutcome.reset());
        } finally {
            running.unlock();
        }
    }

    private record Outcome(long updated, long reset) {
    }

    /** The whole feed of one kind, by id. */
    private Map<Long, RatingFeed.Item> pull(SearchedIndex index) {
        Map<Long, RatingFeed.Item> ratings = new LinkedHashMap<>();
        long afterId = 0;
        while (true) {
            RatingFeed page = feed.ratings(index, afterId, PAGE_SIZE);
            if (page.items() != null) {
                page.items().stream().filter(item -> item.count() > 0).forEach(item -> ratings.put(item.id(), item));
            }
            Long next = page.nextAfterId();
            if (next == null) {
                return ratings;
            }
            if (next <= afterId) {
                // Never loop forever on a feed that does not move on
                throw new SourceUnavailableException("review-service",
                        new IllegalStateException("the rating feed does not advance past id " + afterId));
            }
            afterId = next;
        }
    }

    private <T> Outcome apply(Class<T> type, String index, Map<Long, RatingFeed.Item> ratings) {
        // Makes the writes of events and of the previous run searchable first
        operations.indexOps(type).refresh();
        List<Long> gone = ratedIds(type);
        gone.removeAll(ratings.keySet());

        long updated = 0;
        for (List<RatingFeed.Item> chunk : chunks(new ArrayList<>(ratings.values()))) {
            Map<String, Object> byId = new HashMap<>();
            chunk.forEach(item -> byId.put(String.valueOf(item.id()),
                    Map.of("average", item.average(), "count", item.count())));
            ByQueryResponse response = updateByQuery(index, ids(chunk.stream().map(RatingFeed.Item::id).toList()),
                    APPLY_SCRIPT, Map.of("ratings", byId));
            updated += response.getUpdated() + response.getNoops();
        }
        long reset = 0;
        for (List<Long> chunk : chunks(gone)) {
            reset += updateByQuery(index, ids(chunk), RESET_SCRIPT, Map.of()).getUpdated();
        }
        return new Outcome(updated, reset);
    }

    /** The ids of every document of an index that carries a rating. */
    private <T> List<Long> ratedIds(Class<T> type) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.range(r -> r.number(n -> n.field(RATING_COUNT).gt(0.0))))
                        .should(s -> s.exists(e -> e.field("ratingAverage")))
                        .minimumShouldMatch("1")))
                .withSourceFilter(new FetchSourceFilter(true, new String[]{RATING_COUNT}, null))
                .withPageable(PageRequest.of(0, 1000))
                .build();
        List<Long> ids = new ArrayList<>();
        try (SearchHitsIterator<T> hits = operations.searchForStream(query, type)) {
            hits.forEachRemaining(hit -> ids.add(Long.valueOf(hit.getId())));
        }
        return ids;
    }

    private static Query ids(List<Long> ids) {
        List<String> values = ids.stream().map(String::valueOf).toList();
        return Query.of(q -> q.ids(i -> i.values(values)));
    }

    private ByQueryResponse updateByQuery(String index, Query filter, String script, Map<String, Object> params) {
        UpdateQuery update = UpdateQuery.builder(NativeQuery.builder().withQuery(filter).build())
                .withLang("painless")
                .withScript(script)
                .withParams(params)
                // A document rewritten by an event at the same moment wins; the next run repeats the rating if needed
                .withAbortOnVersionConflict(false)
                .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .build();
        return operations.updateByQuery(update, IndexCoordinates.of(index));
    }

    private static <T> List<List<T>> chunks(List<T> all) {
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += CHUNK) {
            chunks.add(all.subList(from, Math.min(all.size(), from + CHUNK)));
        }
        return chunks;
    }
}
