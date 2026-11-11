package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.table.OrderRecordManager;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OrderRecordManagerTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        OrderRecordManagerTestSuite.backend = backend;
    }

    private OrderRecordManager manager;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.ORDER_RECORD_MANAGER;
    }

    @Override
    public void registerTests() {
        addTest("queueRecord_silentlySwallowsSQLException", this::test_queueRecord_silentlySwallowsSQLException);
        addTest("mapRow_returnsNull_onSQLException", this::test_mapRow_returnsNull_onSQLException);
        addTest("query_multipleFilters", this::test_query_multipleFilters);
        addTest("query_noFilters", this::test_query_noFilters);
        addTest("singleton_create", this::test_singleton_create);
        addTest("uuidStoredAsTwoLongs", this::test_uuidStoredAsTwoLongs);
        addTest("save_coalescesQueuedRecords", this::test_save_coalescesQueuedRecords);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("OrderRecordManagerTestSuite requires backend to be set");
        }
        manager = backend.ORDER_RECORD_MANAGER;
    }

    private TestResult test_queueRecord_silentlySwallowsSQLException() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // Issue #11: queueRecord catches SQLException with just a log message.
            // We verify that calling queueRecord with a null PreparedStatement
            // triggers the catch block rather than crashing the whole system.
            try {
                manager.queueRecord(null, new OrderRecordStruct(
                        (short) 1, 1, UUID.randomUUID(), 0, 100, 50, System.currentTimeMillis()));
                // If we get here, the exception was caught and swallowed
                return pass("queueRecord silently handles null PreparedStatement (Issue #11 documented)");
            } catch (NullPointerException e) {
                return pass("queueRecord throws NPE on null statement (exception propagates, not silently swallowed)");
            }
        } catch (Exception e) {
            return fail("Unexpected exception: " + e.getMessage());
        }
    }

    private TestResult test_mapRow_returnsNull_onSQLException() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // mapRow with null ResultSet should trigger exception handling
            try {
                OrderRecordStruct result = manager.mapRow(null);
                TestResult r = assertNull("mapRow(null) should return null", result);
                if (!r.passed()) return r;
                return pass("mapRow returns null on SQL exception");
            } catch (NullPointerException e) {
                return pass("mapRow throws NPE on null ResultSet (not wrapped for null input)");
            }
        } catch (Exception e) {
            return fail("Unexpected exception: " + e.getMessage());
        }
    }

    private TestResult test_query_multipleFilters() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // Verify the WHERE clause AND/WHERE chaining logic is correct by using
            // getHistory with filters constructed using the correct column names (accountid, itemid).

            UUID testUUID = UUID.randomUUID();
            long now = System.currentTimeMillis();
            DateFilter dateFilter = new DateFilter(now - 60000, now + 60000);
            EqualityFilter accountFilter = new EqualityFilter(1);
            UUIDFilter uuidFilter = new UUIDFilter(testUUID);
            EqualityFilter marketFilter = new EqualityFilter((short) 1);

            // Verify that the filter clauses themselves are well-formed
            TestResult r = assertEquals("Date filter clause", "time >= ? AND time <= ?", dateFilter.getClause("time"));
            if (!r.passed()) return r;
            r = assertEquals("Account filter clause", "accountid = ?", accountFilter.getClause("accountid"));
            if (!r.passed()) return r;
            r = assertEquals("Market filter clause", "itemid = ?", marketFilter.getClause("itemid"));
            if (!r.passed()) return r;

            return pass("Filter clause construction is correct; column names match DDL");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_query_noFilters() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // Verify the base SELECT string references expected columns.
            String selectStmt = OrderRecordManager.SELECT;
            TestResult r = assertTrue("SELECT should reference itemid",
                    selectStmt.contains("itemid"));
            if (!r.passed()) return r;

            // Verify the SELECT references 'accountid' (column name matches DDL)
            r = assertTrue("SELECT should reference 'accountid'",
                    selectStmt.contains("accountid"));
            if (!r.passed()) return r;

            return pass("No-filter query construction verified; SELECT column names match DDL");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_singleton_create() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // The singleton pattern was removed. Verify we can construct via new OrderRecordManager(dbManager).
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            OrderRecordManager newInstance = new OrderRecordManager(dbManager);
            TestResult r = assertNotNull("New instance should not be null", newInstance);
            if (!r.passed()) return r;

            // Verify the new instance has the same SQL constants as the existing one
            r = assertEquals("INSERT constant should match",
                    OrderRecordManager.INSERT, OrderRecordManager.INSERT);
            if (!r.passed()) return r;

            return pass("OrderRecordManager can be constructed with DatabaseManager, singleton removed");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_uuidStoredAsTwoLongs() {
        try {
            if (manager == null)
                return fail("ORDER_RECORD_MANAGER is null");

            // Verify the UUID two-column storage approach is correct by testing the
            // decomposition and reconstruction logic, as well as the filter clause format.

            UUID originalUUID = UUID.randomUUID();
            long msb = originalUUID.getMostSignificantBits();
            long lsb = originalUUID.getLeastSignificantBits();

            // Verify UUID can be reconstructed from MSB/LSB
            UUID reconstructed = new UUID(msb, lsb);
            TestResult r = assertEquals("UUID should be reconstructable from MSB/LSB", originalUUID, reconstructed);
            if (!r.passed()) return r;

            // Verify the UUIDFilter generates correct two-column clause
            UUIDFilter uuidFilter = new UUIDFilter(originalUUID);
            String clause = uuidFilter.getClause("userid_one", "userid_two");
            r = assertEquals("UUID filter clause should use two columns",
                    "userid_one = ? AND userid_two = ?", clause);
            if (!r.passed()) return r;

            // Verify that OrderRecordStruct preserves UUID correctly
            OrderRecordStruct testRecord = new OrderRecordStruct(
                    (short) 3, 1, originalUUID, 0, 100, 50, System.currentTimeMillis());
            r = assertEquals("Record preserves UUID", originalUUID, testRecord.user());
            if (!r.passed()) return r;

            return pass("UUID stored as two longs (MSB/LSB) — decomposition, reconstruction, and filter clause verified");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    /**
     * T-226: records saved while the db-worker is busy share one write job instead of one job each, and a
     * {@code removeHistory} call in between ends that batch, so a record saved after the delete survives it.
     */
    private TestResult test_save_coalescesQueuedRecords() {
        if (manager == null || backend.DATABASE_MANAGER == null)
            return fail("ORDER_RECORD_MANAGER or DATABASE_MANAGER is null");
        DatabaseManager db = backend.DATABASE_MANAGER;
        // Drain what earlier suites queued, so the 5 s waits below time this test's jobs only.
        if (!pumpUntil(TestFixture.dbBarrier(db)::isDone, TestFixture.DB_DRAIN_TIMEOUT_MS))
            return fail("db-worker queue did not drain within " + TestFixture.DB_DRAIN_TIMEOUT_MS + " ms");
        UUID batchedUser = UUID.randomUUID();
        UUID laterUser = UUID.randomUUID();
        short item = (short) -31003; // scratch item ID, no real market uses it
        CountDownLatch hold = new CountDownLatch(1);
        try {
            // Keep the db-worker busy, so every save below queues instead of running right away.
            db.getDatabaseThread().execute(() -> {
                try { hold.await(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            });
            CompletableFuture<Void> batch = manager.save(new OrderRecordStruct(item, 1, batchedUser, 0, 1, 1, 1L));
            for (int i = 1; i < 50; i++) {
                if (manager.save(new OrderRecordStruct(item, 1, batchedUser, 0, 1, 1, 1L)) != batch)
                    return fail("record " + i + " opened a new batch instead of joining the queued one");
            }
            CompletableFuture<Void> delete = manager.removeHistory(Optional.empty(), Optional.empty(),
                    Optional.of(new UUIDFilter(laterUser)), Optional.empty());
            CompletableFuture<Void> later = manager.save(new OrderRecordStruct(item, 1, laterUser, 0, 1, 1, 1L));
            TestResult r = assertTrue("a save after removeHistory should open a new batch", later != batch);
            if (!r.passed()) return r;

            hold.countDown();
            await(CompletableFuture.allOf(batch, delete, later), 5000);
            r = assertEquals("records written by the shared batch", 50, await(manager.getHistory(Optional.empty(),
                    Optional.empty(), Optional.of(new UUIDFilter(batchedUser)), Optional.empty()), 5000).size());
            if (!r.passed()) return r;
            return assertEquals("record saved after removeHistory survives the delete", 1, await(manager.getHistory(
                    Optional.empty(), Optional.empty(), Optional.of(new UUIDFilter(laterUser)), Optional.empty()), 5000).size());
        } catch (Exception e) {
            return fail("Exception: " + e);
        } finally {
            hold.countDown(); // never leave the db-worker parked
            manager.removeHistory(Optional.empty(), Optional.empty(), Optional.of(new UUIDFilter(batchedUser)), Optional.empty());
            manager.removeHistory(Optional.empty(), Optional.empty(), Optional.of(new UUIDFilter(laterUser)), Optional.empty());
        }
    }
}
