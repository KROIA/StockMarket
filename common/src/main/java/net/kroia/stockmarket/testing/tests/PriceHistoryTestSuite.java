package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.PriceHistoryData.Candle;

import java.util.ArrayList;
import java.util.List;

public class PriceHistoryTestSuite extends TestSuite {

    private static final ItemID DUMMY_ITEM = new ItemID((short) 1);
    private static final int SCALE_FACTOR = 100;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PRICE_HISTORY;
    }

    @Override
    public void registerTests() {
        // Candle Operations
        addTest("candle_merge_single", this::test_candle_merge_singleCandle);
        addTest("candle_merge_multiple", this::test_candle_merge_multipleCandles);
        addTest("candle_merge_empty_range", this::test_candle_merge_emptyRange);
        addTest("candle_merge_out_of_bounds", this::test_candle_merge_outOfBounds);
        // Price Tracking
        addTest("set_market_price_updates_candle", this::test_setCurrentMarketPrice_updatesCandle);
        addTest("set_market_price_empty_candles", this::test_setCurrentMarketPrice_emptyCandles);
        addTest("start_new_candle_adds_to_list", this::test_startNewCandle_addsToList);
        // Candle Resampling
        addTest("load_from_different_delta_time", this::test_loadFrom_differentDeltaTime);
        addTest("load_from_empty_source", this::test_loadFrom_emptySource);
        addTest("load_from_time_gap_filling", this::test_loadFrom_timeGapFilling);
        addTest("load_from_no_time_gap_filling", this::test_loadFrom_noTimeGapFilling);
        // Range Queries
        addTest("get_min_price_normal", this::test_getMinPrice_normalRange);
        addTest("get_max_price_normal", this::test_getMaxPrice_normalRange);
        addTest("get_min_price_inverted", this::test_getMinPrice_invertedRange);
        addTest("get_min_price_empty", this::test_getMinPrice_emptyCandles);
        addTest("get_min_price_extremeLongValues", this::test_getMinPrice_extremeLongValues);
        addTest("get_max_price_extremeLongValues", this::test_getMaxPrice_extremeLongValues);
        // Utilities
        addTest("floor_time_rounds_down", this::test_floorTime_roundsDown);
        addTest("floor_time_exact", this::test_floorTime_exact);
        addTest("from_sql_data_empty_list", this::test_fromSqlData_emptyList);
    }

    // --- Candle Operations ---

    /**
     * Merging 1 candle returns itself.
     */
    private TestResult test_candle_merge_singleCandle() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(1000L, 100, 110, 90));
        Candle merged = Candle.merge(candles, 1000L, 0, 1);
        TestResult r = assertEquals("Open should match", 100L, merged.open);
        if (!r.passed()) return r;
        r = assertEquals("High should match", 110L, merged.high);
        if (!r.passed()) return r;
        r = assertEquals("Low should match", 90L, merged.low);
        if (!r.passed()) return r;
        return pass("Single candle merge returns itself");
    }

    /**
     * Merged candle has first open, max high, min low.
     */
    private TestResult test_candle_merge_multipleCandles() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(1000L, 100, 110, 90));
        candles.add(new Candle(2000L, 105, 120, 85));
        candles.add(new Candle(3000L, 95, 115, 80));
        Candle merged = Candle.merge(candles, 1000L, 0, 3);
        TestResult r = assertEquals("Open should be first candle's open", 100L, merged.open);
        if (!r.passed()) return r;
        r = assertEquals("High should be max across all candles", 120L, merged.high);
        if (!r.passed()) return r;
        r = assertEquals("Low should be min across all candles", 80L, merged.low);
        if (!r.passed()) return r;
        return pass("Multiple candle merge produces correct OHLC");
    }

    /**
     * count=0 behavior -- loop doesn't execute, high/low remain at initial extremes.
     */
    private TestResult test_candle_merge_emptyRange() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(1000L, 100, 110, 90));
        // count=0: the loop body never executes
        // open is set from candles.get(begin) if size > begin
        Candle merged = Candle.merge(candles, 0L, 0, 0);
        // The open is set from get(begin) if size > begin, so open=100
        TestResult r = assertEquals("Open should be from candles.get(0)", 100L, merged.open);
        if (!r.passed()) return r;
        // high stays at -Long.MAX_VALUE and low at Long.MAX_VALUE since loop never runs
        r = assertEquals("High should be -Long.MAX_VALUE (loop never ran)", -Long.MAX_VALUE, merged.high);
        if (!r.passed()) return r;
        r = assertEquals("Low should be Long.MAX_VALUE (loop never ran)", Long.MAX_VALUE, merged.low);
        if (!r.passed()) return r;
        return pass("Empty range merge produces sentinel values for high/low");
    }

    /**
     * begin + count > size -- should throw IndexOutOfBoundsException.
     */
    private TestResult test_candle_merge_outOfBounds() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(1000L, 100, 110, 90));
        try {
            Candle.merge(candles, 0L, 0, 5); // only 1 candle but count=5
            return fail("Should have thrown IndexOutOfBoundsException for out-of-bounds merge");
        } catch (IndexOutOfBoundsException e) {
            return pass("Out-of-bounds merge correctly throws IndexOutOfBoundsException");
        }
    }

    // --- Price Tracking ---

    /**
     * Setting market price updates high/low of current candle.
     */
    private TestResult test_setCurrentMarketPrice_updatesCandle() {
        // Use list constructor with empty candles to avoid auto-created candle at price 0.
        // Then set initial price and start a new candle so it opens at a known price.
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        data.setCurrentMarketPrice(100);  // sets internal price (no candle to update yet)
        data.startNewCandle(0L);          // creates candle with open=100, high=100, low=100
        data.setCurrentMarketPrice(150);  // new high
        data.setCurrentMarketPrice(50);   // new low

        Candle current = data.getCurrentCandle();
        TestResult r = assertNotNull("Current candle should not be null", current);
        if (!r.passed()) return r;
        r = assertEquals("High should be 150", 150L, current.high);
        if (!r.passed()) return r;
        r = assertEquals("Low should be 50", 50L, current.low);
        if (!r.passed()) return r;
        return pass("setCurrentMarketPrice correctly updates candle high/low");
    }

    /**
     * No crash when candles list is empty.
     */
    private TestResult test_setCurrentMarketPrice_emptyCandles() {
        // Create with an empty candles list via the list constructor
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        try {
            data.setCurrentMarketPrice(100);
            return pass("setCurrentMarketPrice with empty candles does not crash");
        } catch (Exception e) {
            return fail("setCurrentMarketPrice crashed with empty candles: " + e.getMessage());
        }
    }

    /**
     * New candle added with current market price.
     */
    private TestResult test_startNewCandle_addsToList() {
        PriceHistoryData data = new PriceHistoryData(0L, DUMMY_ITEM, SCALE_FACTOR);
        int sizeBefore = data.getCandles().size();
        data.setCurrentMarketPrice(200);
        data.startNewCandle(1000L);
        int sizeAfter = data.getCandles().size();
        TestResult r = assertEquals("Should have one more candle", sizeBefore + 1, sizeAfter);
        if (!r.passed()) return r;
        Candle newest = data.getCandles().getLast();
        r = assertEquals("New candle open should be current market price", 200L, newest.open);
        if (!r.passed()) return r;
        return pass("startNewCandle adds candle with current market price");
    }

    // --- Candle Resampling ---

    /**
     * Resampling 1-min candles to 5-min candles merges correctly.
     */
    private TestResult test_loadFrom_differentDeltaTime() {
        // Create source with 10 candles, 1 minute apart
        List<Candle> sourceCandles = new ArrayList<>();
        long baseTime = 60000L; // start at 1 minute
        for (int i = 0; i < 10; i++) {
            long t = baseTime + i * 60000L;
            sourceCandles.add(new Candle(t, 100 + i, 110 + i, 90 + i));
        }
        PriceHistoryData source = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, sourceCandles, 105);

        // Resample to 5-minute candles
        long currentServerTime = baseTime + 10 * 60000L;
        PriceHistoryData resampled = source.createFromDifferentCandleDeltaTime(
                currentServerTime, 5 * 60000L, 105, false);

        TestResult r = assertNotNull("Resampled data should not be null", resampled);
        if (!r.passed()) return r;
        r = assertTrue("Resampled should have fewer candles than source",
                resampled.getCandles().size() <= source.getCandles().size());
        if (!r.passed()) return r;
        r = assertTrue("Resampled should have at least 1 candle",
                !resampled.getCandles().isEmpty());
        if (!r.passed()) return r;
        return pass("Resampling from 1-min to 5-min produces merged candles");
    }

    /**
     * Empty source results in empty output.
     */
    private TestResult test_loadFrom_emptySource() {
        PriceHistoryData source = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        PriceHistoryData target = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        target.loadFrom(source, 60000L, 120000L, 100, false);
        return assertTrue("Target should have no candles from empty source",
                target.getCandles().isEmpty());
    }

    /**
     * Gaps filled when createEmptyCandleForTimeGaps is true.
     */
    private TestResult test_loadFrom_timeGapFilling() {
        // Create source with a large gap: candle at t=0 and t=600000 (10 minutes apart)
        List<Candle> sourceCandles = new ArrayList<>();
        sourceCandles.add(new Candle(60000L, 100, 110, 90));
        sourceCandles.add(new Candle(660000L, 105, 115, 95)); // 10 min gap
        PriceHistoryData source = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, sourceCandles, 105);

        PriceHistoryData target = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        // Resample with 1-min delta and gap filling enabled
        target.loadFrom(source, 60000L, 720000L, 105, true);

        // With gap filling, there should be more candles than just 2
        return assertTrue("Gap filling should create additional candles, got: " + target.getCandles().size(),
                target.getCandles().size() > 2);
    }

    /**
     * No gap candles when createEmptyCandleForTimeGaps is false.
     */
    private TestResult test_loadFrom_noTimeGapFilling() {
        List<Candle> sourceCandles = new ArrayList<>();
        sourceCandles.add(new Candle(60000L, 100, 110, 90));
        sourceCandles.add(new Candle(660000L, 105, 115, 95)); // 10 min gap
        PriceHistoryData source = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, sourceCandles, 105);

        PriceHistoryData target = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        target.loadFrom(source, 60000L, 720000L, 105, false);

        // Without gap filling, should have fewer candles
        return assertTrue("Without gap filling, should have few candles, got: " + target.getCandles().size(),
                target.getCandles().size() <= 5);
    }

    // --- Range Queries ---

    /**
     * Returns minimum low across range.
     */
    private TestResult test_getMinPrice_normalRange() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(0L, 100, 110, 90));
        candles.add(new Candle(0L, 100, 120, 80));
        candles.add(new Candle(0L, 100, 115, 85));
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100);

        long min = data.getMinPrice(0, 2);
        return assertEquals("Min price should be 80 (lowest low)", 80L, min);
    }

    /**
     * Returns maximum high across range.
     */
    private TestResult test_getMaxPrice_normalRange() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(0L, 100, 110, 90));
        candles.add(new Candle(0L, 100, 120, 80));
        candles.add(new Candle(0L, 100, 115, 85));
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100);

        long max = data.getMaxPrice(0, 2);
        return assertEquals("Max price should be 120 (highest high)", 120L, max);
    }

    /**
     * startIndex > endIndex; swapped internally.
     */
    private TestResult test_getMinPrice_invertedRange() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(0L, 100, 110, 90));
        candles.add(new Candle(0L, 100, 120, 80));
        candles.add(new Candle(0L, 100, 115, 85));
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100);

        // Inverted: endIndex=0, startIndex=2
        long min = data.getMinPrice(2, 0);
        return assertEquals("Inverted range should still find min=80", 80L, min);
    }

    /**
     * Returns 0 for empty candle list.
     */
    private TestResult test_getMinPrice_emptyCandles() {
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, new ArrayList<>(), 0);
        long min = data.getMinPrice(0, 5);
        return assertEquals("Empty candle list should return 0", 0L, min);
    }

    /**
     * Extreme long values beyond int range are correctly detected by getMinPrice.
     */
    private TestResult test_getMinPrice_extremeLongValues() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(0L, 100, Long.MAX_VALUE - 50, Long.MAX_VALUE - 100));
        candles.add(new Candle(0L, 100, Long.MAX_VALUE - 50, Long.MAX_VALUE - 200));
        candles.add(new Candle(0L, 100, Long.MAX_VALUE - 50, Long.MAX_VALUE - 150));
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100);

        long min = data.getMinPrice(0, 2);
        return assertEquals("Min price should be Long.MAX_VALUE - 200 (Issue #54)",
                Long.MAX_VALUE - 200, min);
    }

    /**
     * Extreme long values beyond int range are correctly detected by getMaxPrice.
     */
    private TestResult test_getMaxPrice_extremeLongValues() {
        List<Candle> candles = new ArrayList<>();
        candles.add(new Candle(0L, 100, Long.MIN_VALUE + 200, Long.MIN_VALUE + 50));
        candles.add(new Candle(0L, 100, Long.MIN_VALUE + 100, Long.MIN_VALUE + 50));
        candles.add(new Candle(0L, 100, Long.MIN_VALUE + 150, Long.MIN_VALUE + 50));
        PriceHistoryData data = new PriceHistoryData(DUMMY_ITEM, SCALE_FACTOR, candles, 100);

        long max = data.getMaxPrice(0, 2);
        return assertEquals("Max price should be Long.MIN_VALUE + 200 (Issue #54)",
                Long.MIN_VALUE + 200, max);
    }

    // --- Utilities ---

    /**
     * floorTime(65000, 60000) == 60000
     */
    private TestResult test_floorTime_roundsDown() {
        long result = PriceHistoryData.floorTime(65000L, 60000L);
        return assertEquals("floorTime(65000, 60000) should be 60000", 60000L, result);
    }

    /**
     * floorTime(60000, 60000) == 60000
     */
    private TestResult test_floorTime_exact() {
        long result = PriceHistoryData.floorTime(60000L, 60000L);
        return assertEquals("floorTime(60000, 60000) should be 60000", 60000L, result);
    }

    /**
     * Returns null for empty list.
     */
    private TestResult test_fromSqlData_emptyList() {
        PriceHistoryData result = PriceHistoryData.fromSqlData(new ArrayList<>(), 100, SCALE_FACTOR);
        return assertNull("fromSqlData with empty list should return null", result);
    }
}
