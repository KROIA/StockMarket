package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.ServerNewsPublisher;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the master-side news history storage (T-072):
 * {@link NewsHistory} append + prune-at-cap behavior, the paginated
 * {@code getPage(beforeUid, maxResults)} contract T-073 codes against
 * (newest-first, strict {@code uid < beforeUid}, "from newest" conventions,
 * maxResults clamping), lazy pruning after a cap shrink, NBT round-trips
 * (incl. a record whose market was deleted — item name string fallback) and
 * the {@link ServerNewsPublisher} history-append half.
 */
public class NewsHistoryTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_HISTORY;
    }

    @Override
    public void registerTests() {
        // Append + cap
        addTest("append_keeps_chronological_order", this::test_append_keepsChronologicalOrder);
        addTest("append_prunes_oldest_beyond_cap", this::test_append_prunesOldestBeyondCap);
        addTest("cap_shrink_prunes_lazily_on_next_append", this::test_capShrink_prunesLazilyOnNextAppend);
        addTest("cap_is_clamped_to_at_least_one", this::test_cap_isClampedToAtLeastOne);
        addTest("append_null_is_ignored", this::test_append_nullIsIgnored);

        // Pagination
        addTest("page_from_newest_via_zero_cursor", this::test_page_fromNewest_viaZeroCursor);
        addTest("page_from_newest_via_max_value_cursor", this::test_page_fromNewest_viaMaxValueCursor);
        addTest("page_before_uid_is_strictly_exclusive", this::test_page_beforeUid_isStrictlyExclusive);
        addTest("page_empty_history_returns_empty", this::test_page_emptyHistory_returnsEmpty);
        addTest("page_exhausted_cursor_returns_empty", this::test_page_exhaustedCursor_returnsEmpty);
        addTest("page_max_results_clamped_low", this::test_page_maxResults_clampedLow);
        addTest("page_max_results_clamped_high", this::test_page_maxResults_clampedHigh);
        addTest("page_walk_covers_whole_history", this::test_page_walk_coversWholeHistory);

        // NBT persistence
        addTest("nbt_round_trip_preserves_records_and_order", this::test_nbt_roundTrip_preservesRecordsAndOrder);
        addTest("nbt_round_trip_deleted_market_name_fallback", this::test_nbt_roundTrip_deletedMarketNameFallback);
        addTest("nbt_round_trip_empty_history", this::test_nbt_roundTrip_emptyHistory);
        addTest("nbt_load_replaces_previous_content", this::test_nbt_load_replacesPreviousContent);

        // Production publisher (history half)
        addTest("publisher_appends_and_applies_cap", this::test_publisher_appendsAndAppliesCap);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Builds a minimal record with the given uid (uid doubles as timestamp for variety). */
    private static NewsRecord record(long uid) {
        return new NewsRecord(uid, "event_" + uid, 1_720_000_000_000L + uid, uid,
                Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                List.of(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f)),
                "shock", 0.25f, "ramp", 60);
    }

    /** Fills a history with records uid 1..count (in order). */
    private static NewsHistory historyWith(int count) {
        NewsHistory history = new NewsHistory();
        history.setMaxEntries(Math.max(count, 1));
        for (long uid = 1; uid <= count; uid++) {
            history.append(record(uid));
        }
        return history;
    }

    /** Extracts the uids of a page in result order. */
    private static List<Long> uidsOf(List<NewsRecord> page) {
        List<Long> uids = new ArrayList<>(page.size());
        for (NewsRecord record : page) {
            uids.add(record.getNewsUid());
        }
        return uids;
    }

    // ========================================================================
    // Append + cap
    // ========================================================================

    /** Appended records come back newest-first with ascending uids intact. */
    private TestResult test_append_keepsChronologicalOrder() {
        NewsHistory history = historyWith(5);
        TestResult r = assertEquals("size after 5 appends", 5, history.size());
        if (!r.passed()) return r;
        r = assertEquals("newest uid", 5L, history.getNewestUid());
        if (!r.passed()) return r;
        return assertEquals("newest-first page order",
                List.of(5L, 4L, 3L, 2L, 1L), uidsOf(history.getPage(0, 10)));
    }

    /** Appending beyond the cap drops the oldest records. */
    private TestResult test_append_prunesOldestBeyondCap() {
        NewsHistory history = new NewsHistory();
        history.setMaxEntries(5);
        for (long uid = 1; uid <= 10; uid++) {
            history.append(record(uid));
        }
        TestResult r = assertEquals("size stays at cap", 5, history.size());
        if (!r.passed()) return r;
        return assertEquals("only the 5 newest remain",
                List.of(10L, 9L, 8L, 7L, 6L), uidsOf(history.getPage(0, 10)));
    }

    /** A cap shrink must NOT prune immediately — only the next append prunes. */
    private TestResult test_capShrink_prunesLazilyOnNextAppend() {
        NewsHistory history = historyWith(10);
        history.setMaxEntries(3);
        TestResult r = assertEquals("cap shrink alone must not prune", 10, history.size());
        if (!r.passed()) return r;

        history.append(record(11));
        r = assertEquals("next append prunes down to the cap", 3, history.size());
        if (!r.passed()) return r;
        return assertEquals("the 3 newest survive the lazy prune",
                List.of(11L, 10L, 9L), uidsOf(history.getPage(0, 10)));
    }

    /** Cap values below 1 clamp to 1 (the newest record is always kept). */
    private TestResult test_cap_isClampedToAtLeastOne() {
        NewsHistory history = new NewsHistory();
        history.setMaxEntries(0);
        TestResult r = assertEquals("cap clamps to 1", 1, history.getMaxEntries());
        if (!r.passed()) return r;
        history.append(record(1));
        history.append(record(2));
        r = assertEquals("exactly one record kept", 1, history.size());
        if (!r.passed()) return r;
        return assertEquals("the kept record is the newest", 2L, history.getNewestUid());
    }

    /** Null appends are ignored instead of corrupting the buffer. */
    private TestResult test_append_nullIsIgnored() {
        NewsHistory history = historyWith(2);
        history.append(null);
        return assertEquals("null append leaves the history unchanged", 2, history.size());
    }

    // ========================================================================
    // Pagination
    // ========================================================================

    /** beforeUid <= 0 means "from newest". */
    private TestResult test_page_fromNewest_viaZeroCursor() {
        NewsHistory history = historyWith(5);
        TestResult r = assertEquals("cursor 0 starts at the newest",
                List.of(5L, 4L, 3L), uidsOf(history.getPage(0, 3)));
        if (!r.passed()) return r;
        return assertEquals("negative cursor behaves like 0",
                List.of(5L, 4L, 3L), uidsOf(history.getPage(-42, 3)));
    }

    /** beforeUid == Long.MAX_VALUE also means "from newest". */
    private TestResult test_page_fromNewest_viaMaxValueCursor() {
        NewsHistory history = historyWith(5);
        return assertEquals("Long.MAX_VALUE starts at the newest",
                List.of(5L, 4L, 3L), uidsOf(history.getPage(Long.MAX_VALUE, 3)));
    }

    /** Only uids strictly below the cursor are returned (the cursor record is excluded). */
    private TestResult test_page_beforeUid_isStrictlyExclusive() {
        NewsHistory history = historyWith(5);
        TestResult r = assertEquals("cursor 4 yields 3,2,1",
                List.of(3L, 2L, 1L), uidsOf(history.getPage(4, 10)));
        if (!r.passed()) return r;
        return assertEquals("cursor 1 yields nothing (uid 1 itself is excluded)",
                List.of(), uidsOf(history.getPage(1, 10)));
    }

    /** An empty history yields an empty page (never null). */
    private TestResult test_page_emptyHistory_returnsEmpty() {
        NewsHistory history = new NewsHistory();
        List<NewsRecord> page = history.getPage(0, 10);
        TestResult r = assertNotNull("page must never be null", page);
        if (!r.passed()) return r;
        return assertTrue("page of empty history is empty", page.isEmpty());
    }

    /** A cursor older than everything stored yields an empty page (end reached). */
    private TestResult test_page_exhaustedCursor_returnsEmpty() {
        NewsHistory history = new NewsHistory();
        history.setMaxEntries(3);
        for (long uid = 1; uid <= 6; uid++) {
            history.append(record(uid)); // uids 1..3 pruned away
        }
        return assertTrue("cursor below the oldest stored uid yields an empty page",
                history.getPage(4, 10).isEmpty());
    }

    /** maxResults < 1 clamps to 1. */
    private TestResult test_page_maxResults_clampedLow() {
        NewsHistory history = historyWith(5);
        TestResult r = assertEquals("maxResults 0 clamps to 1 result",
                List.of(5L), uidsOf(history.getPage(0, 0)));
        if (!r.passed()) return r;
        return assertEquals("negative maxResults clamps to 1 result",
                List.of(5L), uidsOf(history.getPage(0, -7)));
    }

    /** maxResults above MAX_PAGE_SIZE clamps to MAX_PAGE_SIZE. */
    private TestResult test_page_maxResults_clampedHigh() {
        NewsHistory history = historyWith(NewsHistory.MAX_PAGE_SIZE + 50);
        List<NewsRecord> page = history.getPage(0, 100_000);
        TestResult r = assertEquals("page size clamps to MAX_PAGE_SIZE",
                NewsHistory.MAX_PAGE_SIZE, page.size());
        if (!r.passed()) return r;
        return assertEquals("clamped page still starts at the newest",
                (long) (NewsHistory.MAX_PAGE_SIZE + 50), page.get(0).getNewsUid());
    }

    /** Walking pages via the last uid of each page covers everything without gaps/dups. */
    private TestResult test_page_walk_coversWholeHistory() {
        NewsHistory history = historyWith(10);
        List<Long> collected = new ArrayList<>();
        long cursor = 0; // first page: from newest
        while (true) {
            List<NewsRecord> page = history.getPage(cursor, 3);
            if (page.isEmpty()) break;
            collected.addAll(uidsOf(page));
            cursor = page.get(page.size() - 1).getNewsUid(); // next page: before the oldest of this page
        }
        return assertEquals("walk visits every record exactly once, newest-first",
                List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L), collected);
    }

    // ========================================================================
    // NBT persistence
    // ========================================================================

    /** Save + load reproduces all records in order (value equality per record). */
    private TestResult test_nbt_roundTrip_preservesRecordsAndOrder() {
        NewsHistory original = historyWith(5);
        CompoundTag tag = new CompoundTag();
        TestResult r = assertTrue("save must succeed", original.save(tag));
        if (!r.passed()) return r;

        NewsHistory loaded = new NewsHistory();
        r = assertTrue("load must succeed", loaded.load(tag));
        if (!r.passed()) return r;
        r = assertEquals("record count survives", original.size(), loaded.size());
        if (!r.passed()) return r;
        return assertEquals("records and their order survive (value equality)",
                original.getPage(0, 10), loaded.getPage(0, 10));
    }

    /** A record referencing a deleted market (name string fallback) round-trips intact. */
    private TestResult test_nbt_roundTrip_deletedMarketNameFallback() {
        Map<String, String> headline = new LinkedHashMap<>();
        headline.put("de_de", "Markt geschlossen!");
        headline.put("en_us", "Market shut down!");
        NewsRecord deletedMarketRecord = new NewsRecord(7L, "mine_collapse",
                1_720_000_000_777L, 42L, headline, Map.of("en_us", "Body"),
                List.of(new NewsRecord.AffectedMarket(
                        new ItemID((short) 999), "removedmod:vanished_ore", -0.5f)),
                "crash", -0.4f, "none", 300);

        NewsHistory original = new NewsHistory();
        original.append(record(3));
        original.append(deletedMarketRecord);

        CompoundTag tag = new CompoundTag();
        original.save(tag);
        NewsHistory loaded = new NewsHistory();
        TestResult r = assertTrue("load must succeed", loaded.load(tag));
        if (!r.passed()) return r;

        NewsRecord restored = loaded.getPage(0, 1).get(0);
        r = assertEquals("record with deleted-market reference survives",
                deletedMarketRecord, restored);
        if (!r.passed()) return r;
        r = assertEquals("item name fallback string survives",
                "removedmod:vanished_ore", restored.getAffectedMarkets().get(0).itemName());
        if (!r.passed()) return r;
        return assertEquals("translation-map order survives through the history file",
                "de_de", restored.getHeadline().keySet().iterator().next());
    }

    /** An empty history round-trips to an empty history. */
    private TestResult test_nbt_roundTrip_emptyHistory() {
        NewsHistory original = new NewsHistory();
        CompoundTag tag = new CompoundTag();
        original.save(tag);
        NewsHistory loaded = historyWith(3); // pre-filled to prove load replaces content
        TestResult r = assertTrue("load must succeed", loaded.load(tag));
        if (!r.passed()) return r;
        return assertTrue("loaded history is empty", loaded.isEmpty());
    }

    /** load() replaces the buffer instead of merging into it. */
    private TestResult test_nbt_load_replacesPreviousContent() {
        NewsHistory source = historyWith(2);
        CompoundTag tag = new CompoundTag();
        source.save(tag);

        NewsHistory target = historyWith(5);
        TestResult r = assertTrue("load must succeed", target.load(tag));
        if (!r.passed()) return r;
        r = assertEquals("old content is replaced", 2, target.size());
        if (!r.passed()) return r;
        return assertEquals("loaded records match the source",
                List.of(2L, 1L), uidsOf(target.getPage(0, 10)));
    }

    // ========================================================================
    // Production publisher (history half)
    // ========================================================================

    /**
     * The ServerNewsPublisher appends published records to the history and applies the
     * (lazily supplied) cap at publish time — a shrunken cap prunes on the next publish.
     */
    private TestResult test_publisher_appendsAndAppliesCap() {
        NewsHistory history = new NewsHistory();
        int[] cap = {3};
        ServerNewsPublisher publisher = new ServerNewsPublisher(history, () -> cap[0]);

        for (long uid = 1; uid <= 5; uid++) {
            publisher.publish(record(uid));
        }
        TestResult r = assertEquals("publisher enforces the supplied cap", 3, history.size());
        if (!r.passed()) return r;
        r = assertEquals("newest records survive",
                List.of(5L, 4L, 3L), uidsOf(history.getPage(0, 10)));
        if (!r.passed()) return r;

        // Cap change (e.g. news-config reload) applies on the next publish.
        cap[0] = 2;
        publisher.publish(record(6));
        r = assertEquals("cap change applies at the next publish", 2, history.size());
        if (!r.passed()) return r;
        r = assertEquals("pruned to the 2 newest",
                List.of(6L, 5L), uidsOf(history.getPage(0, 10)));
        if (!r.passed()) return r;

        publisher.publish(null);
        return assertEquals("null publish is ignored", 2, history.size());
    }
}
