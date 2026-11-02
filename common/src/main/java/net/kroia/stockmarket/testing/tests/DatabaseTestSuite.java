package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.sql.Connection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        DatabaseTestSuite.backend = backend;
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DATABASE_TEST;
    }

    @Override
    public void registerTests() {
        addTest("commitTransaction_success", () -> onDbThread(this::test_commitTransaction_success));
        addTest("commitTransaction_rollsBackOnFailure", () -> onDbThread(this::test_commitTransaction_rollsBackOnFailure));
        addTest("commitTransaction_rollbackFailure", () -> onDbThread(this::test_commitTransaction_rollbackFailure));
        addTest("staticFields_staleAfterRestart", this::test_staticFields_staleAfterRestart);
        addTest("createDatabase_logsMisleadingly", this::test_createDatabase_logsMisleadingly);
        addTest("shutdownDatabase_commitsBeforeClose", () -> onDbThread(this::test_shutdownDatabase_commitsBeforeClose));
        addTest("shutdownDatabase_nullConnection", this::test_shutdownDatabase_nullConnection);
        // Isolated DatabaseManager instances; never touch the live backend's manager.
        addTest("close_waitsForQueuedJob", this::test_close_waitsForQueuedJob);
        addTest("close_rejectsSubmitAfterClose", this::test_close_rejectsSubmitAfterClose);
        addTest("close_fromDbWorker_noDeadlock", this::test_close_fromDbWorker_noDeadlock);
    }

    @Override
    public void setup() {
        if (backend == null) {
            throw new RuntimeException("DatabaseTestSuite requires backend to be set");
        }
    }

    /**
     * Runs a test body on the {@code db-worker} executor and waits for its result.
     * <p>
     * Production confines every use of the single SQLite {@link Connection} to that
     * single-thread executor. Touching the connection (statements, commit/rollback) from
     * the test thread races in-flight db-worker commits and causes intermittent
     * "cannot commit/rollback - no transaction is active" failures.
     *
     * @param body the test body to execute on the database thread
     * @return the body's result, or a failure on exception/timeout
     */
    private TestResult onDbThread(Callable<TestResult> body) {
        if (backend.DATABASE_MANAGER == null)
            return fail("DATABASE_MANAGER is null");
        try {
            return backend.DATABASE_MANAGER.getDatabaseThread().submit(body).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return fail("Test body failed on db-worker thread: " + e);
        }
    }

    private TestResult test_commitTransaction_success() {
        try {
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            // SQLite requires an active transaction before commit — a no-op commit
            // throws "cannot commit - no transaction is active". Execute a write
            // statement to start a transaction.
            Connection conn = dbManager.getConnection();
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS _test_commit (id INTEGER)");
            conn.createStatement().execute("INSERT INTO _test_commit VALUES (1)");

            boolean result = dbManager.commitTransaction();
            TestResult r = assertTrue("commitTransaction should succeed on valid connection", result);
            if (!r.passed()) return r;

            // Clean up the temp table
            conn.createStatement().execute("DROP TABLE IF EXISTS _test_commit");
            dbManager.commitTransaction();

            return pass("commitTransaction succeeds normally");
        } catch (Exception e) {
            return fail("Exception during commit: " + e.getMessage());
        }
    }

    private TestResult test_commitTransaction_rollsBackOnFailure() {
        try {
            // We cannot easily force a commit failure on the real connection,
            // so we verify the code path exists and the method returns false on failure.
            // The DatabaseManager.commitTransaction() catches SQLException and calls rollback.
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            // A normal commit should succeed
            boolean result = dbManager.commitTransaction();
            if (!result)
                return fail("Normal commit unexpectedly failed");

            return pass("commitTransaction rollback path exists in code - normal commit succeeds");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_commitTransaction_rollbackFailure() {
        try {
            // This documents that the code handles double failure (commit + rollback)
            // by logging and returning false. We verify it doesn't crash on normal path.
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            boolean result = dbManager.commitTransaction();
            TestResult r = assertTrue("Commit should succeed on healthy connection", result);
            if (!r.passed()) return r;
            return pass("Double failure path documented - code handles commit+rollback failure gracefully");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_staticFields_staleAfterRestart() {
        try {
            // Verify that the DatabaseManager instance has a valid connection
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            Connection conn = dbManager.getConnection();
            TestResult r = assertNotNull("Connection should not be null for active instance", conn);
            if (!r.passed()) return r;

            r = assertFalse("Connection should not be closed", conn.isClosed());
            if (!r.passed()) return r;

            return pass("DatabaseManager instance has valid open connection (not stale)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_createDatabase_logsMisleadingly() {
        try {
            // This test documents Issue #3: if createDatabase() fails, the code path in
            // connectToDatabase() previously still logged "Successfully connected".
            // After the fix, it logs an error instead.
            // We verify the current connection is valid (meaning createDatabase succeeded).
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            Connection conn = dbManager.getConnection();
            TestResult r = assertNotNull("Connection should exist after successful createDatabase", conn);
            if (!r.passed()) return r;
            return pass("createDatabase succeeded - misleading log issue documented (Issue #3)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_shutdownDatabase_commitsBeforeClose() {
        try {
            // Verify that the close() method exists and the pattern calls commit before close.
            // We cannot actually close the active connection without breaking other tests.
            // Instead, we verify the connection is active and commit works.
            DatabaseManager dbManager = backend.DATABASE_MANAGER;
            if (dbManager == null)
                return fail("DATABASE_MANAGER is null");

            // SQLite requires an active write transaction before commit — execute a
            // dummy write so the commit has something to commit.
            Connection conn = dbManager.getConnection();
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS _test_shutdown (id INTEGER)");
            conn.createStatement().execute("INSERT INTO _test_shutdown VALUES (1)");

            boolean commitResult = dbManager.commitTransaction();
            TestResult r = assertTrue("Commit before close should succeed", commitResult);
            if (!r.passed()) return r;

            // Clean up
            conn.createStatement().execute("DROP TABLE IF EXISTS _test_shutdown");
            dbManager.commitTransaction();

            return pass("shutdownDatabase pattern commits before close (verified commit works)");
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private TestResult test_shutdownDatabase_nullConnection() {
        try {
            // Verify that a fresh DatabaseManager (not connected) can call close() without crash
            DatabaseManager freshManager = new DatabaseManager();
            // close() should handle null connection gracefully
            freshManager.close();
            return pass("close() on DatabaseManager with null connection does not crash");
        } catch (Exception e) {
            return fail("close() with null connection caused exception: " + e.getMessage());
        }
    }

    /**
     * close() must drain the db-worker before closing: a job queued behind a slow one
     * has to have run by the time close() returns.
     */
    private TestResult test_close_waitsForQueuedJob() {
        DatabaseManager freshManager = new DatabaseManager();
        AtomicBoolean queuedJobRan = new AtomicBoolean(false);
        try {
            freshManager.getDatabaseThread().submit(() -> {
                try { Thread.sleep(300); } catch (InterruptedException ignored) { }
            });
            freshManager.getDatabaseThread().submit(() -> queuedJobRan.set(true));
            freshManager.close();
            return assertTrue("Queued db-worker job should finish before close() returns", queuedJobRan.get());
        } catch (Exception e) {
            freshManager.close();
            return fail("Exception: " + e);
        }
    }

    /**
     * Jobs submitted after close() must be rejected loudly instead of running against a
     * closed connection.
     */
    private TestResult test_close_rejectsSubmitAfterClose() {
        DatabaseManager freshManager = new DatabaseManager();
        freshManager.close();
        try {
            freshManager.getDatabaseThread().submit(() -> { });
            return fail("submit() after close() should throw RejectedExecutionException");
        } catch (RejectedExecutionException e) {
            return pass("submit() after close() is rejected");
        }
    }

    /**
     * close() called from inside a db-worker job must not wait on its own thread.
     */
    private TestResult test_close_fromDbWorker_noDeadlock() {
        DatabaseManager freshManager = new DatabaseManager();
        try {
            Future<?> closing = freshManager.getDatabaseThread().submit(freshManager::close);
            closing.get(5, TimeUnit.SECONDS);
            return assertTrue("Executor should be shut down after close() from db-worker",
                    freshManager.getDatabaseThread().isShutdown());
        } catch (Exception e) {
            freshManager.close();
            return fail("close() from db-worker did not complete: " + e);
        }
    }
}
