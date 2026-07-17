package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.ClientNewsCache;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsToastCatchUp;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for the client-side news cache (T-073): newest-first ordering, cap pruning,
 * uid-based de-duplication, null handling, clear semantics and the change-listener
 * hook the newspaper screen (T-074) uses for live updates.
 * <p>
 * Also covers the join-time catch-up additions (T-077): the {@link ClientNewsCache#seed}
 * history merge and the pure {@link NewsToastCatchUp#selectCatchUpToasts} decision
 * logic (window boundary, toast cap, opt-out, empty history).
 */
public class ClientNewsCacheTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_CLIENT_CACHE;
    }

    @Override
    public void registerTests() {
        addTest("add_orders_newest_first", this::test_add_ordersNewestFirst);
        addTest("add_prunes_oldest_beyond_cap", this::test_add_prunesOldestBeyondCap);
        addTest("add_duplicate_uid_is_ignored", this::test_add_duplicateUid_isIgnored);
        addTest("add_null_is_ignored", this::test_add_nullIsIgnored);
        addTest("cap_is_clamped_to_at_least_one", this::test_cap_isClampedToAtLeastOne);
        addTest("clear_empties_the_cache", this::test_clear_emptiesTheCache);
        addTest("newest_uid_tracking", this::test_newestUid_tracking);
        addTest("change_listener_fires_on_add_and_clear", this::test_changeListener_firesOnAddAndClear);
        addTest("records_snapshot_is_unmodifiable", this::test_recordsSnapshot_isUnmodifiable);

        // T-077: history seeding (join-time catch-up)
        addTest("seed_empty_cache_preserves_newest_first", this::test_seed_emptyCache_preservesNewestFirst);
        addTest("seed_merges_with_live_records_by_uid", this::test_seed_mergesWithLiveRecordsByUid);
        addTest("seed_dedupes_prunes_and_notifies_once", this::test_seed_dedupesPrunesAndNotifiesOnce);
        addTest("seed_duplicates_or_empty_is_noop", this::test_seed_duplicatesOrEmpty_isNoop);

        // T-077: catch-up toast selection (pure decision logic)
        addTest("catchup_window_boundary", this::test_catchup_windowBoundary);
        addTest("catchup_cap_keeps_newest_oldest_first", this::test_catchup_capKeepsNewestOldestFirst);
        addTest("catchup_opt_out_yields_nothing", this::test_catchup_optOut_yieldsNothing);
        addTest("catchup_empty_history_yields_nothing", this::test_catchup_emptyHistory_yieldsNothing);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Builds a minimal record with the given uid (mirrors NewsHistoryTestSuite). */
    private static NewsRecord record(long uid) {
        return recordAt(uid, 1_720_000_000_000L + uid);
    }

    /** Builds a minimal record with an explicit publish timestamp (catch-up tests). */
    private static NewsRecord recordAt(long uid, long timestampEpochMs) {
        return new NewsRecord(uid, "event_" + uid, timestampEpochMs, uid,
                Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                List.of(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f)),
                "shock", 0.25f, "ramp", 60);
    }

    /** Extracts the uids of a record list in its iteration order. */
    private static List<Long> uidsOf(List<NewsRecord> records) {
        List<Long> uids = new ArrayList<>();
        for (NewsRecord record : records) {
            uids.add(record.getNewsUid());
        }
        return uids;
    }

    /** Extracts the uids of the cached records in snapshot order (newest first). */
    private static List<Long> uidsOf(ClientNewsCache cache) {
        List<Long> uids = new ArrayList<>();
        for (NewsRecord cached : cache.getRecords()) {
            uids.add(cached.getNewsUid());
        }
        return uids;
    }

    // ========================================================================
    // Tests
    // ========================================================================

    /** Records added in publish order come back newest-first (index 0 = latest). */
    private TestResult test_add_ordersNewestFirst() {
        ClientNewsCache cache = new ClientNewsCache();
        for (long uid = 1; uid <= 5; uid++) {
            cache.add(record(uid));
        }
        TestResult r = assertEquals("size after 5 adds", 5, cache.size());
        if (!r.passed()) return r;
        return assertEquals("newest-first snapshot order",
                List.of(5L, 4L, 3L, 2L, 1L), uidsOf(cache));
    }

    /** Adding beyond the cap drops the oldest records. */
    private TestResult test_add_prunesOldestBeyondCap() {
        ClientNewsCache cache = new ClientNewsCache(3);
        for (long uid = 1; uid <= 7; uid++) {
            cache.add(record(uid));
        }
        TestResult r = assertEquals("size stays at cap", 3, cache.size());
        if (!r.passed()) return r;
        return assertEquals("only the 3 newest remain, newest-first",
                List.of(7L, 6L, 5L), uidsOf(cache));
    }

    /** A record whose uid is already cached is rejected (packet/history-merge dedupe). */
    private TestResult test_add_duplicateUid_isIgnored() {
        ClientNewsCache cache = new ClientNewsCache();
        TestResult r = assertTrue("first add accepted", cache.add(record(1)));
        if (!r.passed()) return r;
        r = assertFalse("duplicate uid rejected", cache.add(record(1)));
        if (!r.passed()) return r;
        r = assertEquals("duplicate did not grow the cache", 1, cache.size());
        if (!r.passed()) return r;
        return assertTrue("containsUid reports the cached record", cache.containsUid(1));
    }

    /** Null adds are ignored instead of corrupting the cache. */
    private TestResult test_add_nullIsIgnored() {
        ClientNewsCache cache = new ClientNewsCache();
        cache.add(record(1));
        TestResult r = assertFalse("null add rejected", cache.add(null));
        if (!r.passed()) return r;
        return assertEquals("null add leaves the cache unchanged", 1, cache.size());
    }

    /** Cap values below 1 clamp to 1 (the newest record is always kept). */
    private TestResult test_cap_isClampedToAtLeastOne() {
        ClientNewsCache cache = new ClientNewsCache(0);
        TestResult r = assertEquals("cap clamps to 1", 1, cache.getMaxEntries());
        if (!r.passed()) return r;
        cache.add(record(1));
        cache.add(record(2));
        r = assertEquals("exactly one record kept", 1, cache.size());
        if (!r.passed()) return r;
        return assertEquals("the kept record is the newest", 2L, cache.getNewestUid());
    }

    /** clear() empties the cache (disconnect discards the whole instance anyway). */
    private TestResult test_clear_emptiesTheCache() {
        ClientNewsCache cache = new ClientNewsCache();
        cache.add(record(1));
        cache.add(record(2));
        cache.clear();
        TestResult r = assertTrue("cache is empty after clear", cache.isEmpty());
        if (!r.passed()) return r;
        r = assertEquals("newest uid resets to 0", 0L, cache.getNewestUid());
        if (!r.passed()) return r;
        return assertTrue("previously cached uid can be re-added after clear",
                cache.add(record(1)));
    }

    /** getNewestUid follows the most recently published record. */
    private TestResult test_newestUid_tracking() {
        ClientNewsCache cache = new ClientNewsCache();
        TestResult r = assertEquals("empty cache reports 0", 0L, cache.getNewestUid());
        if (!r.passed()) return r;
        cache.add(record(4));
        cache.add(record(9));
        return assertEquals("newest uid follows the latest add", 9L, cache.getNewestUid());
    }

    /** The change listener fires on accepted adds and on clear, but not on rejects. */
    private TestResult test_changeListener_firesOnAddAndClear() {
        ClientNewsCache cache = new ClientNewsCache();
        int[] fired = {0};
        cache.setChangeListener(() -> fired[0]++);

        cache.add(record(1));
        TestResult r = assertEquals("listener fires on accepted add", 1, fired[0]);
        if (!r.passed()) return r;

        cache.add(record(1)); // duplicate — rejected
        cache.add(null);      // null — rejected
        r = assertEquals("listener does not fire on rejected adds", 1, fired[0]);
        if (!r.passed()) return r;

        cache.clear();
        r = assertEquals("listener fires on clear", 2, fired[0]);
        if (!r.passed()) return r;

        cache.clear(); // already empty — no change
        r = assertEquals("listener does not fire on clearing an empty cache", 2, fired[0]);
        if (!r.passed()) return r;

        cache.setChangeListener(null);
        cache.add(record(2));
        return assertEquals("unset listener no longer fires", 2, fired[0]);
    }

    /** getRecords() is a read-only snapshot — mutation attempts must throw. */
    private TestResult test_recordsSnapshot_isUnmodifiable() {
        ClientNewsCache cache = new ClientNewsCache();
        cache.add(record(1));
        List<NewsRecord> snapshot = cache.getRecords();
        try {
            snapshot.add(record(2));
            return fail("snapshot must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            return assertEquals("cache unchanged by the mutation attempt", 1, cache.size());
        }
    }

    // ========================================================================
    // T-077: seed() — join-time history seeding
    // ========================================================================

    /** Seeding an empty cache with a newest-first page keeps the newest-first order. */
    private TestResult test_seed_emptyCache_preservesNewestFirst() {
        ClientNewsCache cache = new ClientNewsCache();
        TestResult r = assertTrue("seed reports a change",
                cache.seed(List.of(record(5), record(4), record(3))));
        if (!r.passed()) return r;
        return assertEquals("seeded snapshot is newest-first",
                List.of(5L, 4L, 3L), uidsOf(cache));
    }

    /** History records older than live-cached ones merge into the right uid position. */
    private TestResult test_seed_mergesWithLiveRecordsByUid() {
        ClientNewsCache cache = new ClientNewsCache();
        // A live publish raced ahead of the history response.
        cache.add(record(100));
        TestResult r = assertTrue("seed reports a change",
                cache.seed(List.of(record(101), record(99), record(98))));
        if (!r.passed()) return r;
        return assertEquals("merged snapshot is newest-first by uid",
                List.of(101L, 100L, 99L, 98L), uidsOf(cache));
    }

    /** seed() dedupes by uid, prunes to the cap and fires the listener exactly once. */
    private TestResult test_seed_dedupesPrunesAndNotifiesOnce() {
        ClientNewsCache cache = new ClientNewsCache(3);
        cache.add(record(10));
        int[] fired = {0};
        cache.setChangeListener(() -> fired[0]++);

        TestResult r = assertTrue("seed reports a change",
                cache.seed(List.of(record(10), record(9), record(8), record(7))));
        if (!r.passed()) return r;
        r = assertEquals("cache pruned to its cap", 3, cache.size());
        if (!r.passed()) return r;
        r = assertEquals("the newest records survive the prune, newest-first",
                List.of(10L, 9L, 8L), uidsOf(cache));
        if (!r.passed()) return r;
        return assertEquals("listener fires exactly once per seed", 1, fired[0]);
    }

    /** Seeding nothing new (null, empty, duplicates only) is a silent no-op. */
    private TestResult test_seed_duplicatesOrEmpty_isNoop() {
        ClientNewsCache cache = new ClientNewsCache();
        cache.add(record(1));
        int[] fired = {0};
        cache.setChangeListener(() -> fired[0]++);

        TestResult r = assertFalse("null seed is a no-op", cache.seed(null));
        if (!r.passed()) return r;
        r = assertFalse("empty seed is a no-op", cache.seed(List.of()));
        if (!r.passed()) return r;
        r = assertFalse("duplicates-only seed is a no-op", cache.seed(List.of(record(1))));
        if (!r.passed()) return r;
        r = assertEquals("cache unchanged by no-op seeds", 1, cache.size());
        if (!r.passed()) return r;
        return assertEquals("listener never fired for no-op seeds", 0, fired[0]);
    }

    // ========================================================================
    // T-077: selectCatchUpToasts() — pure catch-up decision logic
    // ========================================================================

    /**
     * Window boundary: strictly-younger-than semantics. A record aged exactly the
     * window is excluded, one millisecond younger is included, and a future-stamped
     * record (client/master clock skew) counts as brand new.
     */
    private TestResult test_catchup_windowBoundary() {
        long now = 1_720_000_000_000L;
        long window = NewsToastCatchUp.CATCH_UP_WINDOW_MS;
        List<NewsRecord> newestFirst = List.of(
                recordAt(4, now + 5_000),          // future-stamped (skew) → in
                recordAt(3, now),                  // age 0 → in
                recordAt(2, now - window + 1),     // age window-1 → in (youngest possible edge)
                recordAt(1, now - window));        // age == window → out (not strictly younger)

        List<NewsRecord> selected = NewsToastCatchUp.selectCatchUpToasts(
                newestFirst, now, window, 10, true);
        return assertEquals("in-window records selected, oldest-first",
                List.of(2L, 3L, 4L), uidsOf(selected));
    }

    /** The cap keeps the NEWEST eligible records and returns them oldest-first. */
    private TestResult test_catchup_capKeepsNewestOldestFirst() {
        long now = 1_720_000_000_000L;
        List<NewsRecord> newestFirst = List.of(
                recordAt(5, now - 1_000), recordAt(4, now - 2_000), recordAt(3, now - 3_000),
                recordAt(2, now - 4_000), recordAt(1, now - 5_000));

        List<NewsRecord> selected = NewsToastCatchUp.selectCatchUpToasts(
                newestFirst, now, NewsToastCatchUp.CATCH_UP_WINDOW_MS,
                NewsToastCatchUp.MAX_CATCH_UP_TOASTS, true);
        TestResult r = assertEquals("cap limits the toast count",
                NewsToastCatchUp.MAX_CATCH_UP_TOASTS, selected.size());
        if (!r.passed()) return r;
        return assertEquals("the newest records win, returned oldest-first",
                List.of(3L, 4L, 5L), uidsOf(selected));
    }

    /** Opt-out dominates everything: even brand-new records yield an empty result. */
    private TestResult test_catchup_optOut_yieldsNothing() {
        long now = 1_720_000_000_000L;
        List<NewsRecord> newestFirst = List.of(recordAt(1, now));
        return assertTrue("opted-out player gets no catch-up toasts",
                NewsToastCatchUp.selectCatchUpToasts(newestFirst, now,
                        NewsToastCatchUp.CATCH_UP_WINDOW_MS,
                        NewsToastCatchUp.MAX_CATCH_UP_TOASTS, false).isEmpty());
    }

    /** Empty/null history and degenerate window/cap parameters yield an empty result. */
    private TestResult test_catchup_emptyHistory_yieldsNothing() {
        long now = 1_720_000_000_000L;
        long window = NewsToastCatchUp.CATCH_UP_WINDOW_MS;
        TestResult r = assertTrue("empty history yields nothing",
                NewsToastCatchUp.selectCatchUpToasts(List.of(), now, window, 3, true).isEmpty());
        if (!r.passed()) return r;
        r = assertTrue("null history yields nothing",
                NewsToastCatchUp.selectCatchUpToasts(null, now, window, 3, true).isEmpty());
        if (!r.passed()) return r;
        r = assertTrue("zero cap yields nothing",
                NewsToastCatchUp.selectCatchUpToasts(List.of(recordAt(1, now)), now, window, 0, true).isEmpty());
        if (!r.passed()) return r;
        return assertTrue("non-positive window yields nothing",
                NewsToastCatchUp.selectCatchUpToasts(List.of(recordAt(1, now)), now, 0, 3, true).isEmpty());
    }
}
