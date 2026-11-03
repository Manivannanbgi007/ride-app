package com.fooddelivery.searchservice.service;

import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.fooddelivery.searchservice.document.CategoryDocument;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.document.SearchedIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

/** Reads what an index holds: the id and source version of each document, in id order, one page at a time. */
@Component
@RequiredArgsConstructor
public class IndexVersionReader {

    /** A document's id and the version of its source row it was built from (-1 when unknown). */
    public record Entry(long id, long version) {
    }

    private static final String[] FIELDS = {"id", "sourceVersion"};

    private final ElasticsearchOperations operations;

    /** Up to size documents with an id above afterId (a range, not an offset: stable while documents are added). */
    public List<Entry> readAfter(SearchedIndex index, long afterId, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.range(r -> r.number(n -> n.field("id").gt((double) afterId))))
                .withSort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)))
                .withSourceFilter(new FetchSourceFilter(true, FIELDS, null))
                .withMaxResults(size)
                .build();
        return operations.search(query, index.documentType()).getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(IndexVersionReader::entryOf)
                .toList();
    }

    public long count(SearchedIndex index) {
        return operations.count(Query.findAll(), index.documentType());
    }

    /** Removes documents by id (the index is a copy: this never touches MySQL). */
    public void delete(SearchedIndex index, List<Long> ids) {
        ids.forEach(id -> operations.delete(String.valueOf(id), index.documentType()));
        operations.indexOps(index.documentType()).refresh();
    }

    private static Entry entryOf(Object document) {
        return switch (document) {
            case RestaurantDocument d -> new Entry(d.getId(), versionOf(d.getSourceVersion()));
            case FoodDocument d -> new Entry(d.getId(), versionOf(d.getSourceVersion()));
            case CategoryDocument d -> new Entry(d.getId(), versionOf(d.getSourceVersion()));
            default -> throw new IllegalStateException("Not an indexed document: " + document.getClass());
        };
    }

    private static long versionOf(Long version) {
        return version == null ? -1 : version;
    }
}
