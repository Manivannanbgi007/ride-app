package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * For admins: is search healthy, and how much is indexed.
 *
 * @param clusterStatus green, yellow or red (Elasticsearch's own health)
 */
public record IndexOverview(String clusterStatus, List<IndexCount> indexes) {

    public record IndexCount(String index, long documents) {
    }
}
