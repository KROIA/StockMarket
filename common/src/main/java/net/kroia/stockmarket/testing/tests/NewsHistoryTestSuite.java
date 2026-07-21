package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsHistoryChunkStore;
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

    /**
     * Appending past the cap drops the oldest CHUNK once ≥2 chunks exist (T-110:
     * aligned drop, no partial rewrite). With cap = CHUNK_SIZE + 5 and 2×CHUNK_SIZE
     * appended records, the first chunk drops and the second (100+5 records) survives.
     */
    private TestResult test_append_prunesOldestBeyondCap() {
        NewsHistory history = new NewsHistory();
        int chunkSize = NewsHistoryChunkStore.CHUNK_SIZE;
        history.setMaxEntries(chunkSize + 5);
        for (long uid = 1; uid <= 2L * chunkSize; uid++) {
            history.append(record(uid));
        }
        // 200 records total, cap = 105. Chunk 0 (uid 1..100) full → chunk 1 (uid 101..200)
        // full. Total 200 > 105 && 2 chunks → drop chunk 0. Result: chunk 1 only,
        // 100 records. 100 < 105 → stop.
        TestResult r = assertEquals("size is one chunk after aligned drop", chunkSize, history.size());
        if (!r.passed()) return r;
        List<NewsRecord> page = history.getPage(0, chunkSize);
        r = assertEquals("newest record survives", 2L * chunkSize, page.get(0).getNewsUid());
        if (!r.passed()) return r;
        return assertEquals("oldest surviving record is chunk-1 start",
                chunkSize + 1L, page.get(page.size() - 1).getNewsUid());
    }

    /**
     * A cap shrink applies lazily on the next append (T-110): the shrink itself
     * does nothing, and pruning is chunk-granular — the oldest whole chunk drops
     * only when the total crosses the cap AND ≥2 chunks exist.
     */
    private TestResult test_capShrink_prunesLazilyOnNextAppend() {
        NewsHistory history = new NewsHistory();
        int chunkSize = NewsHistoryChunkStore.CHUNK_SIZE;
        history.setMaxEntries(1000);
        // Two full chunks — total 200 records, 2 chunks.
        for (long uid = 1; uid <= 2L * chunkSize; uid++) {
            history.append(record(uid));
        }
        history.setMaxEntries(50); // Shrink to 50 (far below chunk size).
        TestResult r = assertEquals("cap shrink alone must not prune",
                2 * chunkSize, history.size());
        if (!r.passed()) return r;

        history.append(record(2L * chunkSize + 1L));
        // Now 201 records in 3 chunks (100/100/1). Cap = 50 → drop chunk 0 (101),
        // still 100 > 50 && 2 chunks → drop chunk 1 (1 remaining). Chunk 2 (1
        // record) is the newest — the newest chunk is never dropped.
        r = assertEquals("cap-granular drops leave the newest chunk", 1, history.size());
        if (!r.passed()) return r;
        return assertEquals("the newest record survives",
                2L * chunkSize + 1L, history.getNewestUid());
    }

    /**
     * Cap values below 1 clamp to 1 (T-072 behaviour retained). Pruning is chunk-
     * granular (T-110) — the newest chunk is never dropped, so with a cap this
     * small the effective retention is up to one full chunk.
     */
    private TestResult test_cap_isClampedToAtLeastOne() {
        NewsHistory history = new NewsHistory();
        history.setMaxEntries(0);
        TestResult r = assertEquals("cap clamps to 1", 1, history.getMaxEntries());
        if (!r.passed()) return r;
        history.append(record(1));
        history.append(record(2));
        // Both records share the newest chunk (cap alignment caveat). The newest
        // chunk is never dropped, so both survive despite exceeding cap.
        r = assertEquals("both records fit in the (undroppable) newest chunk",
                2, history.size());
        if (!r.passed()) return r;
        return assertEquals("the newest record is still the newest", 2L, history.getNewestUid());
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

    /**
     * A cursor older than everything stored yields an empty page (end reached).
     * T-110: uses chunk-aligned pruning so uids 1..CHUNK_SIZE are dropped when
     * chunk 0 is retired.
     */
    private TestResult test_page_exhaustedCursor_returnsEmpty() {
        NewsHistory history = new NewsHistory();
        int chunkSize = NewsHistoryChunkStore.CHUNK_SIZE;
        history.setMaxEntries(chunkSize + 5);
        for (long uid = 1; uid <= 2L * chunkSize; uid++) {
            history.append(record(uid));
        }
        // Cap = 105, size = 200 → chunk 0 (uids 1..100) dropped. Chunk 1 keeps
        // uids 101..200. A cursor at 50 (< oldest surviving 101) yields empty.
        return assertTrue("cursor below the oldest surviving uid yields an empty page",
                history.getPage(50, 10).isEmpty());
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
     * The ServerNewsPublisher appends published records to the history and applies
     * the (lazily supplied) cap at publish time — a shrunken cap prunes on the next
     * publish. T-110: pruning is chunk-granular; the counts are chosen so a chunk
     * rotation + drop is triggered and the assertions cover the aligned semantics.
     */
    private TestResult test_publisher_appendsAndAppliesCap() {
        NewsHistory history = new NewsHistory();
        int chunkSize = NewsHistoryChunkStore.CHUNK_SIZE;
        int[] cap = {chunkSize + 5}; // 105 — barely above one chunk
        ServerNewsPublisher publisher = new ServerNewsPublisher(history, () -> cap[0]);

        long total = 2L * chunkSize; // fills chunk 0 + chunk 1, no drop yet (200 < 205 doesn't hold — 200 > 105)
        for (long uid = 1; uid <= total; uid++) {
            publisher.publish(record(uid));
        }
        // 200 records vs cap 105 → drop chunk 0 (100). Now 100 records (uid 101..200)
        // in a single chunk. 100 < 105 → stop.
        TestResult r = assertEquals("publisher enforces the aligned cap", chunkSize, history.size());
        if (!r.passed()) return r;
        r = assertEquals("newest record survives", total, history.getNewestUid());
        if (!r.passed()) return r;

        // Cap change (e.g. news-config reload) applies on the next publish. The
        // next publish (record 201) fills chunk 1 to 100 and rotates to chunk 2
        // with 1 record; cap=10 triggers dropping chunk 1 (100), leaving just the
        // fresh newest chunk with the one just-published record.
        cap[0] = 10;
        publisher.publish(record(total + 1L));
        r = assertEquals("chunk rotation + aligned drop leaves the fresh newest chunk",
                1, history.size());
        if (!r.passed()) return r;
        r = assertEquals("the freshly rotated record survives",
                total + 1L, history.getNewestUid());
        if (!r.passed()) return r;

        publisher.publish(null);
        return assertEquals("null publish is ignored", 1, history.size());
    }
}
