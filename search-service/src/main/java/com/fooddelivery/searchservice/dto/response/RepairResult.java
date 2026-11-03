package com.fooddelivery.searchservice.dto.response;

import java.util.List;

/**
 * What a repair did, and what it found. The index catches up through the normal event flow in a few seconds: check
 * again afterwards.
 *
 * @param before what the check found
 */
public record RepairResult(ConsistencyReport before, List<IndexRepair> indexes) {

    /**
     * @param deleted     documents removed from the index (orphans, and ones ahead of MySQL, which are rebuilt)
     * @param republished events the owning service published again (0 when the index needed none)
     */
    public record IndexRepair(String index, long deleted, int republished) {
    }
}
