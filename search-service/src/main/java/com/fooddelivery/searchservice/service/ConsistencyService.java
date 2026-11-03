package com.fooddelivery.searchservice.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

import com.fooddelivery.searchservice.client.SourceOfTruthClient;
import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.ConsistencyReport;
import com.fooddelivery.searchservice.dto.response.ConsistencyReport.IndexConsistency;
import com.fooddelivery.searchservice.dto.response.RepairResult;
import com.fooddelivery.searchservice.dto.response.RepairResult.IndexRepair;
import com.fooddelivery.searchservice.dto.response.SourceVersions;
import com.fooddelivery.searchservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Finds out whether Elasticsearch still matches MySQL, and repairs it. MySQL (asked through the owning services) is
 * always right; the index is a copy.
 * <p>
 * The check walks the owning service's list of (id, version) and the index's, both in id order, side by side: an
 * id only in MySQL is missing from the index, an id only in the index is an orphan, and the same id at different
 * versions is stale (index older) or ahead (index newer, e.g. after a database restore). Nothing is held in memory
 * but one page of each side and the ids that differ.
 * <p>
 * The repair removes orphans (each asked about again first, so a row created a moment ago is never taken for
 * one) and documents that are ahead, and asks the owning service to publish the affected data again; the indexer
 * then rebuilds it through the normal flow (versioned, repeat-safe). Nothing is written to MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsistencyService {

    static final int PAGE_SIZE = 1_000;
    /** The most orphans one repair confirms and removes (each is checked with its own call); repair again for more. */
    static final int ORPHAN_CHECK_LIMIT = 2_000;
    private static final int SAMPLE_SIZE = 20;

    private final SourceOfTruthClient source;
    private final IndexVersionReader index;
    private final Clock clock;

    /** What differs, per index. Changes nothing. */
    public ConsistencyReport check() {
        List<Drift> drifts = new ArrayList<>();
        for (SearchedIndex searched : SearchedIndex.values()) {
            drifts.add(compare(searched));
        }
        return report(drifts);
    }

    /** Removes what MySQL no longer has, and has the owning services publish the rest again. */
    public RepairResult repair(CallerInfo admin) {
        List<Drift> drifts = new ArrayList<>();
        List<IndexRepair> repairs = new ArrayList<>();
        for (SearchedIndex searched : SearchedIndex.values()) {
            Drift drift = compare(searched);
            drifts.add(drift);
            List<Long> toDelete = new ArrayList<>(drift.orphans());
            toDelete.addAll(drift.ahead());
            if (!toDelete.isEmpty()) {
                index.delete(searched, toDelete);
            }
            // A document that was only ahead is gone now, so it needs publishing again like a missing one
            boolean needsPublishing = !drift.missing().isEmpty() || !drift.stale().isEmpty() || !drift.ahead().isEmpty();
            int republished = needsPublishing ? source.republish(searched, admin) : 0;
            repairs.add(new IndexRepair(searched.name().toLowerCase(java.util.Locale.ROOT), toDelete.size(), republished));
            log.info("Index repaired. index={} deleted={} republished={} adminUserId={}", searched, toDelete.size(),
                    republished, admin.userId());
        }
        return new RepairResult(report(drifts), repairs);
    }

    private ConsistencyReport report(List<Drift> drifts) {
        List<IndexConsistency> indexes = drifts.stream().map(Drift::toReport).toList();
        return new ConsistencyReport(clock.instant(), indexes.stream().allMatch(IndexConsistency::isConsistent), indexes);
    }

    private Drift compare(SearchedIndex searched) {
        Drift drift = new Drift(searched);
        Feed fromMysql = new Feed(after -> toEntries(source.versions(searched, after, PAGE_SIZE)));
        Feed fromIndex = new Feed(after -> index.readAfter(searched, after, PAGE_SIZE).stream()
                .map(entry -> new IndexVersionReader.Entry(entry.id(), entry.version())).toList());
        List<Long> orphanCandidates = new ArrayList<>();

        IndexVersionReader.Entry truth = fromMysql.peek();
        IndexVersionReader.Entry copy = fromIndex.peek();
        while (truth != null || copy != null) {
            if (copy == null || (truth != null && truth.id() < copy.id())) {
                drift.inMysql++;
                drift.missing().add(truth.id());
                fromMysql.advance();
            } else if (truth == null || copy.id() < truth.id()) {
                drift.inIndex++;
                orphanCandidates.add(copy.id());
                fromIndex.advance();
            } else {
                drift.inMysql++;
                drift.inIndex++;
                if (copy.version() < truth.version()) {
                    drift.stale().add(truth.id());
                } else if (copy.version() > truth.version()) {
                    drift.ahead().add(truth.id());
                }
                fromMysql.advance();
                fromIndex.advance();
            }
            truth = fromMysql.peek();
            copy = fromIndex.peek();
        }
        confirmOrphans(searched, orphanCandidates, drift);
        if (!drift.toReport().isConsistent()) {
            log.warn("Search index differs from MySQL. index={} missing={} stale={} ahead={} orphans={}", searched,
                    drift.missing().size(), drift.stale().size(), drift.ahead().size(), drift.orphans().size());
        }
        return drift;
    }

    /** A document MySQL did not list may be a row created after the list was read: ask about each one again. */
    private void confirmOrphans(SearchedIndex searched, List<Long> candidates, Drift drift) {
        int checked = 0;
        for (long id : candidates) {
            if (checked++ >= ORPHAN_CHECK_LIMIT) {
                drift.orphansUnconfirmed = candidates.size() - ORPHAN_CHECK_LIMIT;
                return;
            }
            if (!source.exists(searched, id)) {
                drift.orphans().add(id);
            }
        }
    }

    private static List<IndexVersionReader.Entry> toEntries(SourceVersions page) {
        return page.entries().stream().map(e -> new IndexVersionReader.Entry(e.id(), e.version())).toList();
    }

    /** One side of the comparison: entries in id order, fetched a page at a time as they are needed. */
    private static final class Feed {
        private final LongFunction<List<IndexVersionReader.Entry>> fetch;
        private List<IndexVersionReader.Entry> page = List.of();
        private int position;
        private long lastId;
        private boolean exhausted;

        Feed(LongFunction<List<IndexVersionReader.Entry>> fetch) {
            this.fetch = fetch;
        }

        IndexVersionReader.Entry peek() {
            if (position >= page.size() && !exhausted) {
                page = fetch.apply(lastId);
                position = 0;
                exhausted = page.isEmpty();
            }
            return position < page.size() ? page.get(position) : null;
        }

        void advance() {
            lastId = page.get(position).id();
            position++;
        }
    }

    /** What differs in one index (the full lists, for the repair; the report only shows samples). */
    private static final class Drift {
        private final SearchedIndex searched;
        private final List<Long> missing = new ArrayList<>();
        private final List<Long> stale = new ArrayList<>();
        private final List<Long> ahead = new ArrayList<>();
        private final List<Long> orphans = new ArrayList<>();
        long inMysql;
        long inIndex;
        long orphansUnconfirmed;

        Drift(SearchedIndex searched) {
            this.searched = searched;
        }

        List<Long> missing() {
            return missing;
        }

        List<Long> stale() {
            return stale;
        }

        List<Long> ahead() {
            return ahead;
        }

        List<Long> orphans() {
            return orphans;
        }

        IndexConsistency toReport() {
            return new IndexConsistency(searched.name().toLowerCase(java.util.Locale.ROOT), inMysql, inIndex, missing.size(),
                    stale.size(), ahead.size(), orphans.size(), orphansUnconfirmed, sample(missing), sample(stale),
                    sample(ahead), sample(orphans));
        }

        private static List<Long> sample(List<Long> ids) {
            return List.copyOf(ids.subList(0, Math.min(SAMPLE_SIZE, ids.size())));
        }
    }
}
