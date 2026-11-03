package com.fooddelivery.searchservice.service;

import java.util.List;

import com.fooddelivery.searchservice.dto.response.IndexOverview;
import com.fooddelivery.searchservice.dto.response.IndexOverview.IndexCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

/** For admins: Elasticsearch's health and how many documents each index holds. */
@Service
@RequiredArgsConstructor
public class IndexOverviewService {

    private final ElasticsearchOperations operations;

    public IndexOverview getOverview() {
        List<IndexCount> counts = SearchIndexes.DOCUMENT_TYPES.stream()
                .map(type -> new IndexCount(operations.getIndexCoordinatesFor(type).getIndexName(),
                        operations.count(Query.findAll(), type)))
                .toList();
        return new IndexOverview(operations.cluster().health().getStatus().toLowerCase(), counts);
    }
}
