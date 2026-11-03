package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * One page of "which items exist in MySQL, at which version", as an owning service answers (internal API).
 *
 * @param nextAfterId ask for the next page with this as afterId; null on the last page
 */
public record SourceVersions(List<Entry> entries, Long nextAfterId) {

    public record Entry(long id, long version) {
    }
}
