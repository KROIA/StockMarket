package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.request.NewsHistoryRequest;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Master-only tests for the server-side page answering of {@link NewsHistoryRequest}
 * (T-073): the handler must answer straight from
 * {@code DataManager.getNewsHistory().getPage(...)} with the documented newest-first
 * pagination contract, reject anonymous senders with the empty default response, and
 * clamp oversized page requests.
 * <p>
 * Only the handler half is coverable in-suite — the actual client→server(→master)
 * transport and the S2C publish broadcast need a connected client/slave and are
 * verified in-game (see task T-073 acceptance criteria).
 * <p>
 * The suite snapshots the world's real news history in {@link #setup()} and restores
 * it in {@link #teardown()}, so running it in a dev world never corrupts real news.
 */
public class NewsHistoryRequestTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        NewsHistoryRequestTestSuite.backend = backend;
    }

    /** Snapshot of the world's real history content, restored in teardown. */
    private CompoundTag historySnapshot;
    private int previousMaxEntries;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_HISTORY_REQUEST;
    }

    @Override
    public void registerTests() {
        addTest("first_page_is_newest_first", this::test_firstPage_isNewestFirst);
        addTest("cursor_walk_pages_whole_history", this::test_cursorWalk_pagesWholeHistory);
        addTest("max_results_clamped_to_page_size_cap", this::test_maxResults_clampedToPageSizeCap);
        addTest("empty_history_yields_empty_page", this::test_emptyHistory_yieldsEmptyPage);
        addTest("null_sender_gets_default_response", this::test_nullSender_getsDefaultResponse);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("NewsHistoryRequestTestSuite requires backend to be set");
        }
        if (backend.DATA_MANAGER == null) {
            throw new RuntimeException("NewsHistoryRequestTestSuite requires the master DataManager");
        }
        // Preserve the dev world's real news so the suite is side-effect free.
        NewsHistory history = backend.DATA_MANAGER.getNewsHistory();
        historySnapshot = new CompoundTag();
        history.save(historySnapshot);
        previousMaxEntries = history.getMaxEntries();
        history.clear();
    }

    @Override
    public void teardown() {
        if (backend == null || backend.DATA_MANAGER == null || historySnapshot == null) return;
        NewsHistory history = backend.DATA_MANAGER.getNewsHistory();
        history.load(historySnapshot);
        history.setMaxEntries(previousMaxEntries);
        historySnapshot = null;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Builds a minimal record with the given uid (mirrors NewsHistoryTestSuite). */
    private static NewsRecord record(long uid) {
        return new NewsRecord(uid, "event_" + uid, 1_720_000_000_000L + uid, uid,
                Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                List.of(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f)),
                "shock", 0.25f, "ramp", 60);
    }

    /** Fills the master history with records uid 1..count (in publish order). */
    private void populateHistory(int count) {
        NewsHistory history = backend.DATA_MANAGER.getNewsHistory();
        history.clear();
        history.setMaxEntries(Math.max(count, 1));
        for (long uid = 1; uid <= count; uid++) {
            history.append(record(uid));
        }
    }

    /** Runs the handler synchronously (the news page lookup completes immediately). */
    private NewsHistoryRequest.OutputData executeRequest(long beforeUid, int maxResults, UUID playerSender) {
        try {
            NewsHistoryRequest request = new NewsHistoryRequest();
            return request.handleOnMasterServer(
                    new NewsHistoryRequest.InputData(beforeUid, maxResults), "", playerSender).get();
        } catch (Exception e) {
            throw new RuntimeException("NewsHistoryRequest handler failed", e);
        }
    }

    /** Extracts the uids of a response page in result order. */
    private static List<Long> uidsOf(NewsHistoryRequest.OutputData output) {
        List<Long> uids = new ArrayList<>(output.records().size());
        for (NewsRecord record : output.records()) {
            uids.add(record.getNewsUid());
        }
        return uids;
    }

    // ========================================================================
    // Tests
    // ========================================================================

    /** beforeUid <= 0 answers the first page from the newest record, newest-first. */
    private TestResult test_firstPage_isNewestFirst() {
        populateHistory(5);
        NewsHistoryRequest.OutputData output = executeRequest(0, 3, UUID.randomUUID());
        return assertEquals("first page is the 3 newest records, newest-first",
                List.of(5L, 4L, 3L), uidsOf(output));
    }

    /** Walking via the last uid of each response covers the whole history without gaps/dups. */
    private TestResult test_cursorWalk_pagesWholeHistory() {
        populateHistory(7);
        UUID player = UUID.randomUUID();
        List<Long> collected = new ArrayList<>();
        long cursor = 0; // first page: from newest
        int safety = 0;
        while (safety++ < 20) {
            NewsHistoryRequest.OutputData page = executeRequest(cursor, 3, player);
            if (page.records().isEmpty()) break; // empty response = end reached
            collected.addAll(uidsOf(page));
            cursor = page.records().get(page.records().size() - 1).getNewsUid();
        }
        return assertEquals("cursor walk visits every record exactly once, newest-first",
                List.of(7L, 6L, 5L, 4L, 3L, 2L, 1L), collected);
    }

    /** Oversized maxResults is clamped server-side to NewsHistory.MAX_PAGE_SIZE. */
    private TestResult test_maxResults_clampedToPageSizeCap() {
        populateHistory(NewsHistory.MAX_PAGE_SIZE + 10);
        NewsHistoryRequest.OutputData output = executeRequest(0, 100_000, UUID.randomUUID());
        TestResult r = assertEquals("response size clamps to MAX_PAGE_SIZE",
                NewsHistory.MAX_PAGE_SIZE, output.records().size());
        if (!r.passed()) return r;
        return assertEquals("clamped response still starts at the newest record",
                (long) (NewsHistory.MAX_PAGE_SIZE + 10), output.records().get(0).getNewsUid());
    }

    /** An empty history answers an empty (non-null) page. */
    private TestResult test_emptyHistory_yieldsEmptyPage() {
        backend.DATA_MANAGER.getNewsHistory().clear();
        NewsHistoryRequest.OutputData output = executeRequest(0, 10, UUID.randomUUID());
        TestResult r = assertNotNull("response must never be null", output);
        if (!r.passed()) return r;
        return assertTrue("empty history yields an empty page", output.records().isEmpty());
    }

    /** Requests without a player sender get the empty default response, not data. */
    private TestResult test_nullSender_getsDefaultResponse() {
        populateHistory(3);
        NewsHistoryRequest.OutputData output = executeRequest(0, 10, null);
        return assertTrue("anonymous sender receives the empty default response",
                output.records().isEmpty());
    }
}
