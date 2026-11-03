package com.fooddelivery.searchservice.service;

import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.fooddelivery.searchservice.document.CategoryDocument;
import com.fooddelivery.searchservice.dto.response.CategoryHit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Customers looking for a food category. Only ACTIVE categories are found; without words, all in menu order. */
@Service
@RequiredArgsConstructor
public class CategorySearchService {

    private static final List<String> TEXT_FIELDS = List.of("name^2", "description");
    private static final int MAX_RESULTS = 50;

    private final ElasticsearchOperations operations;

    public List<CategoryHit> search(String text) {
        boolean hasText = StringUtils.hasText(text);
        NativeQueryBuilder query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(SearchQueries.exact("status", "ACTIVE"));
                    if (hasText) {
                        b.must(SearchQueries.text(text.trim(), TEXT_FIELDS));
                    }
                    return b;
                }))
                .withMaxResults(MAX_RESULTS);
        if (!hasText) {
            query.withSort(SortOptions.of(s -> s.field(f -> f.field("displayOrder").order(SortOrder.Asc))));
        }
        return operations.search(query.build(), CategoryDocument.class).getSearchHits().stream()
                .map(hit -> {
                    CategoryDocument category = hit.getContent();
                    return new CategoryHit(category.getId(), category.getName(), category.getDescription(),
                            category.getImageUrl(), category.getDisplayOrder());
                })
                .toList();
    }
}
