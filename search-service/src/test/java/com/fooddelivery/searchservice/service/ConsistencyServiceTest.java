package com.fooddelivery.searchservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fooddelivery.searchservice.client.SourceOfTruthClient;
import com.fooddelivery.searchservice.document.SearchedIndex;
import com.fooddelivery.searchservice.dto.response.ConsistencyReport;
import com.fooddelivery.searchservice.dto.response.RepairResult;
import com.fooddelivery.searchservice.dto.response.SourceVersions;
import com.fooddelivery.searchservice.exception.SourceUnavailableException;
import com.fooddelivery.searchservice.web.CallerInfo;
import com.fooddelivery.searchservice.web.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How the index is compared with MySQL, and what a repair does: without Elasticsearch, with both sides faked. */
class ConsistencyServiceTest {

    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-1");

    private final SourceOfTruthClient source = mock(SourceOfTruthClient.class);
    private final IndexVersionReader index = mock(IndexVersionReader.class);
    private final ConsistencyService service = new ConsistencyService(source, index,
            Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC));

    /** id -> version, per index, on each side */
    private final Map<SearchedIndex, TreeMap<Long, Long>> mysql = new java.util.EnumMap<>(SearchedIndex.class);
    private final Map<SearchedIndex, TreeMap<Long, Long>> indexed = new java.util.EnumMap<>(SearchedIndex.class);

    @BeforeEach
    void bothSidesAnswerFromTheirMaps() {
        for (SearchedIndex searched : SearchedIndex.values()) {
            mysql.put(searched, new TreeMap<>());
            indexed.put(searched, new TreeMap<>());
        }
        when(source.versions(any(), anyLong(), anyInt())).thenAnswer(call -> {
            SearchedIndex searched = call.getArgument(0);
            long after = call.getArgument(1);
            int size = call.getArgument(2);
            List<SourceVersions.Entry> page = mysql.get(searched).tailMap(after, false).entrySet().stream()
                    .limit(size).map(e -> new SourceVersions.Entry(e.getKey(), e.getValue())).toList();
            return new SourceVersions(page, null);
        });
        when(source.exists(any(), anyLong())).thenAnswer(call -> mysql.get((SearchedIndex) call.getArgument(0))
                .containsKey((Long) call.getArgument(1)));
        when(index.readAfter(any(), anyLong(), anyInt())).thenAnswer(call -> {
            SearchedIndex searched = call.getArgument(0);
            long after = call.getArgument(1);
            int size = call.getArgument(2);
            return indexed.get(searched).tailMap(after, false).entrySet().stream().limit(size)
                    .map(e -> new IndexVersionReader.Entry(e.getKey(), e.getValue())).toList();
        });
    }

    private void both(SearchedIndex searched, long id, long version) {
        mysql.get(searched).put(id, version);
        indexed.get(searched).put(id, version);
    }

    private ConsistencyReport.IndexConsistency report(ConsistencyReport report, SearchedIndex searched) {
        return report.indexes().stream().filter(i -> i.index().equals(searched.name().toLowerCase())).findFirst().orElseThrow();
    }

    @Test
    void identicalSidesAreConsistent() {
        both(SearchedIndex.RESTAURANT, 1, 3);
        both(SearchedIndex.RESTAURANT, 2, 1);
        both(SearchedIndex.FOOD, 10, 7);

        ConsistencyReport report = service.check();

        assertThat(report.consistent()).isTrue();
        assertThat(report.checkedAt()).isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));
        assertThat(report(report, SearchedIndex.RESTAURANT).inMysql()).isEqualTo(2);
        assertThat(report(report, SearchedIndex.RESTAURANT).inIndex()).isEqualTo(2);
    }

    @Test
    void everyKindOfDifferenceIsFoundAndNamed() {
        SearchedIndex r = SearchedIndex.RESTAURANT;
        both(r, 1, 4);
        mysql.get(r).put(2L, 9L);       // in MySQL only: missing
        indexed.get(r).put(3L, 5L);     // in the index only: an orphan
        mysql.get(r).put(4L, 6L);       // the index is older: stale
        indexed.get(r).put(4L, 2L);
        mysql.get(r).put(5L, 1L);       // the index is newer: ahead
        indexed.get(r).put(5L, 8L);

        ConsistencyReport.IndexConsistency found = report(service.check(), r);

        assertThat(found.isConsistent()).isFalse();
        assertThat(found.sampleMissing()).containsExactly(2L);
        assertThat(found.sampleOrphans()).containsExactly(3L);
        assertThat(found.sampleStale()).containsExactly(4L);
        assertThat(found.sampleAhead()).containsExactly(5L);
        assertThat(found.inMysql()).isEqualTo(4);
        assertThat(found.inIndex()).isEqualTo(4);
        // The other indexes are untouched
        assertThat(report(service.check(), SearchedIndex.FOOD).isConsistent()).isTrue();
    }

    @Test
    void aRowCreatedAfterMysqlWasReadIsNotAnOrphan() {
        // The index has it (the event was applied), the page of MySQL was read just before the row existed:
        // it is asked about again, and found
        indexed.get(SearchedIndex.FOOD).put(77L, 1L);
        when(source.exists(SearchedIndex.FOOD, 77L)).thenReturn(true);

        ConsistencyReport.IndexConsistency found = report(service.check(), SearchedIndex.FOOD);

        assertThat(found.orphans()).isZero();
    }

    @Test
    void worksAcrossPageBoundaries() {
        // 2,500 ids on each side (pages of 1,000): one missing, one orphan, one stale, around the boundaries
        for (long id = 1; id <= 2_500; id++) {
            both(SearchedIndex.FOOD, id, 1);
        }
        mysql.get(SearchedIndex.FOOD).remove(1_000L);          // last of page 1 only in the index
        indexed.get(SearchedIndex.FOOD).remove(1_001L);        // first of page 2 only in MySQL
        indexed.get(SearchedIndex.FOOD).put(2_000L, 0L);       // exactly at a boundary: stale
        mysql.get(SearchedIndex.FOOD).put(2_501L, 1L);         // after the last of the index

        ConsistencyReport.IndexConsistency found = report(service.check(), SearchedIndex.FOOD);

        assertThat(found.sampleOrphans()).containsExactly(1_000L);
        assertThat(found.sampleMissing()).containsExactly(1_001L, 2_501L);
        assertThat(found.sampleStale()).containsExactly(2_000L);
        assertThat(found.inMysql()).isEqualTo(2_500);
        assertThat(found.inIndex()).isEqualTo(2_499);
    }

    @Test
    void anEmptyIndexAndAnEmptyDatabaseAreConsistent() {
        assertThat(service.check().consistent()).isTrue();
    }

    @Test
    void anIndexThatLostEverythingIsMissingEverything() {
        for (long id = 1; id <= 1_500; id++) {
            mysql.get(SearchedIndex.CATEGORY).put(id, 1L);
        }

        ConsistencyReport.IndexConsistency found = report(service.check(), SearchedIndex.CATEGORY);

        assertThat(found.missing()).isEqualTo(1_500);
        assertThat(found.sampleMissing()).hasSize(20).startsWith(1L, 2L, 3L);
    }

    @Test
    void repairRemovesOrphansAndAheadDocumentsAndAsksTheOwnerToPublishAgain() {
        SearchedIndex r = SearchedIndex.RESTAURANT;
        both(r, 1, 4);
        indexed.get(r).put(3L, 5L);     // orphan
        indexed.get(r).put(5L, 8L);     // ahead
        mysql.get(r).put(5L, 1L);
        mysql.get(r).put(6L, 2L);       // missing
        when(source.republish(r, ADMIN)).thenReturn(3);

        RepairResult result = service.repair(ADMIN);

        verify(index).delete(eq(r), eq(List.of(3L, 5L)));
        verify(source).republish(r, ADMIN);
        RepairResult.IndexRepair repair = result.indexes().stream().filter(i -> i.index().equals("restaurant")).findFirst()
                .orElseThrow();
        assertThat(repair.deleted()).isEqualTo(2);
        assertThat(repair.republished()).isEqualTo(3);
        assertThat(result.before().consistent()).isFalse();
    }

    @Test
    void repairLeavesAConsistentIndexAlone() {
        both(SearchedIndex.RESTAURANT, 1, 4);

        RepairResult result = service.repair(ADMIN);

        verify(index, never()).delete(any(), any());
        verify(source, never()).republish(any(), any());
        assertThat(result.indexes()).allSatisfy(repair -> {
            assertThat(repair.deleted()).isZero();
            assertThat(repair.republished()).isZero();
        });
    }

    @Test
    void aMissingRowNeedsOnlyItsOwnersRepublishNotTheOthers() {
        mysql.get(SearchedIndex.FOOD).put(10L, 1L);
        when(source.republish(SearchedIndex.FOOD, ADMIN)).thenReturn(1);

        service.repair(ADMIN);

        verify(source).republish(SearchedIndex.FOOD, ADMIN);
        verify(source, never()).republish(eq(SearchedIndex.RESTAURANT), any());
        verify(source, never()).republish(eq(SearchedIndex.CATEGORY), any());
        verify(index, never()).delete(any(), any());
    }

    @Test
    void anOwnerThatCannotBeReachedStopsEverythingAndDeletesNothing() {
        indexed.get(SearchedIndex.RESTAURANT).put(3L, 5L); // would be an orphan
        doThrow(new SourceUnavailableException("restaurant-service", new RuntimeException("connection refused")))
                .when(source).versions(eq(SearchedIndex.RESTAURANT), anyLong(), anyInt());

        assertThatThrownBy(() -> service.repair(ADMIN)).isInstanceOf(SourceUnavailableException.class);

        verify(index, never()).delete(any(), any());
        verify(source, never()).republish(any(), any());
    }

    @Test
    void moreOrphansThanOneRepairConfirmsAreReportedAsUnconfirmedAndNotDeleted() {
        List<Long> orphanIds = new ArrayList<>();
        for (long id = 1; id <= ConsistencyService.ORPHAN_CHECK_LIMIT + 50; id++) {
            indexed.get(SearchedIndex.CATEGORY).put(id, 1L);
            orphanIds.add(id);
        }

        ConsistencyReport.IndexConsistency found = report(service.check(), SearchedIndex.CATEGORY);

        assertThat(found.orphans()).isEqualTo(ConsistencyService.ORPHAN_CHECK_LIMIT);
        assertThat(found.orphansUnconfirmed()).isEqualTo(50);
        assertThat(found.isConsistent()).isFalse();
    }
}
