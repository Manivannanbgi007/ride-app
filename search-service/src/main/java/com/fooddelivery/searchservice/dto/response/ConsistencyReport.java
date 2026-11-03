package com.fooddelivery.searchservice.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch compared with MySQL (the truth), index by index. The lists are samples (the first ones); the numbers
 * are complete.
 *
 * @param consistent true when nothing differs anywhere
 */
public record ConsistencyReport(Instant checkedAt, boolean consistent, List<IndexConsistency> indexes) {

    /**
     * @param inMysql            how many rows the owning service has
     * @param inIndex            how many documents the index has
     * @param missing            in MySQL, not in the index (an event was lost or not applied yet)
     * @param stale              in the index, but at an older version than MySQL
     * @param ahead              in the index at a NEWER version than MySQL (for example after a database restore)
     * @param orphans            in the index, and MySQL no longer has it (checked again, one by one)
     * @param orphansUnconfirmed more orphans than one check confirms: repair again to continue
     */
    public record IndexConsistency(String index, long inMysql, long inIndex, long missing, long stale, long ahead,
                                   long orphans, long orphansUnconfirmed, List<Long> sampleMissing,
                                   List<Long> sampleStale, List<Long> sampleAhead, List<Long> sampleOrphans) {

        public boolean isConsistent() {
            return missing == 0 && stale == 0 && ahead == 0 && orphans == 0 && orphansUnconfirmed == 0;
        }
    }
}
