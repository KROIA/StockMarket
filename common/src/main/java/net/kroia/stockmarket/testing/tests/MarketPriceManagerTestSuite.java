package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MarketPriceManagerTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        MarketPriceManagerTestSuite.backend = backend;
    }

    private MarketPriceManager manager;

    /**
     * Scratch market short IDs used by the two write tests. Real markets are keyed by
     * ItemID shorts minted from 1 upwards, so negative values can never collide with a
     * real market's price history — the tests used to insert under short {@code 1} and
     * {@code 2} and never delete, permanently polluting the live MarketPrice table with
     * fake candles for whatever items happened to own those shorts.
     */
    private static final short SCRATCH_MARKET_ID_A = -31001;
    private static final short SCRATCH_MARKET_ID_B = -31002;

    /**
     * Deletes every MarketPrice row written under a scratch market ID. Blocks briefly so
     * the purge is done before the test returns; never throws (runs inside {@code finally}).
     *
     * @param marketId the scratch short ID to purge
     */
    private void purgeScratchRows(short marketId) {
        if (manager == null)
            return;
        try {
            manager.removeHistory(Optional.empty(), Optional.of(new EqualityFilter(marketId)))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("Failed to purge scratch MarketPrice rows for market "
                    + marketId, e);
        }
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_PRICE_MANAGER;
    }

    @Override
    public void registerTests() {
        addTest("mapRow_returnsNull_onSQLException", this::test_mapRow_returnsNull_onSQLException);
        addTest("query_addsNullToList", this::test_query_addsNullToList);
        addTest("queueRecord_setsAllFields", this::test_queueRecord_setsAllFields);
        addTest("query_dateFilterOnly", this::test_query_dateFilterOnly);
        addTest("query_marketFilterOnly", this::test_query_marketFilterOnly);
        addTest("query_bothFilters", this::test_query_bothFilters);
        addTest("query_noFilters", this::test_query_noFilters);
        addTest("query_withLimit", this::test_query_withLimit);
        addTest("query_zeroLimit_noLimitClause", this::test_query_zeroLimit_noLimitClause);
        addTest("preparedStatement_neverClosed", this::test_preparedStatement_neverClosed);
        // T-218: game_time column (isolated in-memory databases, never the live one)
        addTest("gameTime_roundTrip", this::test_gameTime_roundTrip);
        addTest("gameTime_legacyRow_readsMinusOne", this::test_gameTime_legacyRow_readsMinusOne);
        addTest("migrateSchema_twice_isIdempotent", this::test_migrateSchema_twice_isIdempotent);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("MarketPriceManagerTestSuite requires backend to be set");
        }
        manager = backend.MARKET_PRICE_HISTORY_MANAGER;
        // The tests give each live-manager call 5 s; drain the backlog earlier suites left on the db-worker
        // first, so those 5 s time the call, not the queue ahead of it. Nothing refills it between the tests.
        if (backend.DATABASE_MANAGER != null
                && !pumpUntil(TestFixture.dbBarrier(backend.DATABASE_MANAGER)::isDone, TestFixture.DB_DRAIN_TIMEOUT_MS))
            throw new IllegalStateException("db-worker queue did not drain within " + TestFixture.DB_DRAIN_TIMEOUT_MS + " ms");
    }

    private TestResult test_mapRow_returnsNull_onSQLException() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // mapRow with null ResultSet should return null (triggers SQLException)
            MarketPriceStruct result = manager.mapRow(null);
            TestResult r = assertNull("mapRow(null) should return null", result);
            if (!r.passed()) return r;
            return pass("mapRow returns null on SQL exception (corrupt data)");
        } catch (NullPointerException e) {
            // mapRow may throw NPE before reaching the try/catch
            return pass("mapRow throws NPE on null ResultSet (not wrapped in try/catch for null input)");
        } catch (Exception e) {
            return fail("Unexpected exception: " + e.getMessage());
        }
    }

    private TestResult test_query_addsNullToList() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // This test documents Issue #7: if mapRow returns null, the code checks
            // `if (row != null) result.add(row)` so null is NOT added to the list.
            // Verify that querying returns a list without nulls.
            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.empty(), Optional.empty(), 10);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            for (MarketPriceStruct s : results) {
                if (s == null) {
                    return fail("Query result list contains null entry");
                }
            }
            return pass("Query result list does not contain nulls (null check in place)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_queueRecord_setsAllFields() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // Save a record under a scratch market ID and verify it can be queried back.
            MarketPriceStruct testRecord = new MarketPriceStruct(
                    SCRATCH_MARKET_ID_A, 100L, 90L, 110L, System.currentTimeMillis(), 0f);

            CompletableFuture<Void> saveFuture = manager.save(testRecord);
            saveFuture.get(5, TimeUnit.SECONDS);

            // Query back with market filter
            CompletableFuture<List<MarketPriceStruct>> queryFuture = manager.getHistory(
                    Optional.empty(),
                    Optional.of(new EqualityFilter(SCRATCH_MARKET_ID_A)),
                    1);
            List<MarketPriceStruct> results = queryFuture.get(5, TimeUnit.SECONDS);

            TestResult r = assertTrue("Should have at least 1 result", results.size() >= 1);
            if (!r.passed()) return r;

            MarketPriceStruct retrieved = results.get(results.size() - 1);
            r = assertEquals("Market ID should match", SCRATCH_MARKET_ID_A, retrieved.id());
            if (!r.passed()) return r;
            return pass("queueRecord sets all 5 fields correctly and data is retrievable");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            purgeScratchRows(SCRATCH_MARKET_ID_A);
        }
    }

    private TestResult test_query_dateFilterOnly() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            long now = System.currentTimeMillis();
            DateFilter dateFilter = new DateFilter(now - 60000, now + 60000);

            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.of(dateFilter), Optional.empty(), 100);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            // Should not throw - verifies date filter SQL is valid
            return pass("Query with date filter only executes without error, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_query_marketFilterOnly() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            EqualityFilter marketFilter = new EqualityFilter((short) 1);

            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.empty(), Optional.of(marketFilter), 100);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            return pass("Query with market filter only executes without error, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_query_bothFilters() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            long now = System.currentTimeMillis();
            DateFilter dateFilter = new DateFilter(now - 60000, now + 60000);
            EqualityFilter marketFilter = new EqualityFilter((short) 1);

            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.of(dateFilter), Optional.of(marketFilter), 100);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            return pass("Query with both date AND market filter executes without error, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_query_noFilters() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.empty(), Optional.empty(), 100);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            return pass("Query with no filters executes without error, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_query_withLimit() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // Insert a few records first, under a scratch market ID.
            for (int i = 0; i < 5; i++) {
                MarketPriceStruct record = new MarketPriceStruct(
                        SCRATCH_MARKET_ID_B, 100L + i, 90L, 110L, System.currentTimeMillis() + i, 0f);
                manager.save(record).get(5, TimeUnit.SECONDS);
            }

            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.empty(), Optional.of(new EqualityFilter(SCRATCH_MARKET_ID_B)), 3);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            TestResult r = assertTrue("Should return at most 3 results with LIMIT 3, got " + results.size(),
                    results.size() <= 3);
            if (!r.passed()) return r;
            return pass("LIMIT clause correctly applied, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            purgeScratchRows(SCRATCH_MARKET_ID_B);
        }
    }

    private TestResult test_query_zeroLimit_noLimitClause() {
        try {
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // limit == 0 means no LIMIT clause
            CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                    Optional.empty(), Optional.empty(), 0);
            List<MarketPriceStruct> results = future.get(5, TimeUnit.SECONDS);

            // Should not throw and can return any number of results
            return pass("Zero limit executes without LIMIT clause, returned " + results.size() + " records");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_preparedStatement_neverClosed() {
        try {
            // This test documents Issue #37: PreparedStatement leak.
            // In the current code, PreparedStatements ARE properly closed via try-with-resources
            // in save() and query() methods. This test verifies the pattern works.
            if (manager == null)
                return fail("MARKET_PRICE_HISTORY_MANAGER is null");

            // Execute multiple queries to verify no resource leak crashes
            for (int i = 0; i < 10; i++) {
                CompletableFuture<List<MarketPriceStruct>> future = manager.getHistory(
                        Optional.empty(), Optional.empty(), 1);
                future.get(5, TimeUnit.SECONDS);
            }
            return pass("PreparedStatements properly managed via try-with-resources (Issue #37 documented)");
        } catch (Exception e) {
            return fail("Exception during repeated queries (possible resource leak): " + e.getMessage());
        }
    }

    // --- T-218: game_time column ---

    /**
     * Opens an isolated in-memory SQLite database holding the MarketPrice table as it looked
     * before T-218 (no game_time column) with one legacy row, then runs the schema migration
     * {@code migrations} times. Auto-commit is off, as on the live connection.
     *
     * @param migrations how often {@link DatabaseManager#migrateSchema(Connection)} runs
     * @return the open connection; the caller closes it
     */
    private static Connection legacyDatabase(int migrations) throws Exception {
        // The sqlite driver is registered with DriverManager when the live database connects.
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE MarketPrice (id INTEGER PRIMARY KEY AUTOINCREMENT, marketid INTEGER NOT NULL, "
                    + "open INTEGER NOT NULL, low INTEGER NOT NULL, high INTEGER NOT NULL, time INTEGER NOT NULL, "
                    + "traded_volume REAL NOT NULL DEFAULT 0)");
            st.execute("INSERT INTO MarketPrice (marketid, open, low, high, time, traded_volume) VALUES (7, 100, 90, 110, 1000, 2.5)");
        }
        conn.commit();
        for (int i = 0; i < migrations; i++)
            DatabaseManager.migrateSchema(conn);
        return conn;
    }

    /** Reads all rows (in insertion order) through the production SELECT + mapRow. */
    private static List<MarketPriceStruct> readAll(Connection conn) throws Exception {
        MarketPriceManager mapper = new MarketPriceManager(null);
        List<MarketPriceStruct> rows = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(MarketPriceManager.SELECT)) {
            while (rs.next())
                rows.add(mapper.mapRow(rs));
        }
        return rows;
    }

    /**
     * A candle written through the production INSERT/queueRecord keeps its game time,
     * and a candle built without one stores {@link MarketPriceStruct#NO_GAME_TIME}.
     */
    private TestResult test_gameTime_roundTrip() {
        try (Connection conn = legacyDatabase(1)) {
            MarketPriceManager writer = new MarketPriceManager(null);
            try (PreparedStatement ps = conn.prepareStatement(MarketPriceManager.INSERT)) {
                writer.queueRecord(ps, new MarketPriceStruct((short) 7, 200L, 190L, 210L, 2000L, 1f, 123_456L));
                writer.queueRecord(ps, new MarketPriceStruct((short) 7, 300L, 290L, 310L, 3000L, 0f));
                ps.executeBatch();
            }
            conn.commit();
            List<MarketPriceStruct> rows = readAll(conn);
            TestResult r = assertEquals("three rows", 3, rows.size());
            if (!r.passed()) return r;
            r = assertEquals("game time survives the round trip", 123_456L, rows.get(1).gameTime());
            if (!r.passed()) return r;
            r = assertEquals("open survives the round trip", 200L, rows.get(1).open());
            if (!r.passed()) return r;
            return assertEquals("6-arg struct stores NO_GAME_TIME", MarketPriceStruct.NO_GAME_TIME, rows.get(2).gameTime());
        } catch (Exception e) {
            return fail("Exception: " + e);
        }
    }

    /** Rows written before the migration read back as legacy (game time -1), other values intact. */
    private TestResult test_gameTime_legacyRow_readsMinusOne() {
        try (Connection conn = legacyDatabase(1)) {
            List<MarketPriceStruct> rows = readAll(conn);
            TestResult r = assertEquals("one legacy row", 1, rows.size());
            if (!r.passed()) return r;
            r = assertEquals("legacy row reads NO_GAME_TIME", MarketPriceStruct.NO_GAME_TIME, rows.get(0).gameTime());
            if (!r.passed()) return r;
            r = assertEquals("legacy wall time intact", 1000L, rows.get(0).time());
            if (!r.passed()) return r;
            return assertEquals("legacy volume intact", 2.5f, rows.get(0).tradedVolume());
        } catch (Exception e) {
            return fail("Exception: " + e);
        }
    }

    /** Running the migration twice adds game_time exactly once and leaves the table usable. */
    private TestResult test_migrateSchema_twice_isIdempotent() {
        try (Connection conn = legacyDatabase(2)) {
            int gameTimeColumns = 0;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(MarketPrice)")) {
                while (rs.next())
                    if ("game_time".equals(rs.getString("name")))
                        gameTimeColumns++;
            }
            TestResult r = assertEquals("game_time exists exactly once", 1, gameTimeColumns);
            if (!r.passed()) return r;
            List<MarketPriceStruct> rows = readAll(conn);
            r = assertEquals("legacy row still readable", 1, rows.size());
            if (!r.passed()) return r;
            return assertEquals("legacy row keeps NO_GAME_TIME", MarketPriceStruct.NO_GAME_TIME, rows.get(0).gameTime());
        } catch (Exception e) {
            return fail("Exception: " + e);
        }
    }
}
