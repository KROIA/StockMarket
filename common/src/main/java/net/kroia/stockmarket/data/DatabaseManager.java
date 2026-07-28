package net.kroia.stockmarket.data;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class DatabaseManager {
    private Connection connection;

    /**
     * Single-thread executor that owns every SQL write on the SQLite connection.
     * <p>
     * MUST remain backed by an <i>unbounded</i> queue (the default
     * {@link java.util.concurrent.LinkedBlockingQueue} inside
     * {@link Executors#newSingleThreadExecutor(java.util.concurrent.ThreadFactory)}):
     * {@link #pauseForBackup(long)} relies on the FIFO queue holding every write
     * submitted during the pause window so they replay in order after
     * {@link #resumeFromBackup()}. Switching to a bounded queue or a rejecting
     * {@link java.util.concurrent.RejectedExecutionHandler} would silently
     * drop writes submitted while the worker is parked on the pause latch.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor( r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });


    public static final Path DATABASE_PATH = DataManager.SQL_DATABASE;

    /**
     * Default safety timeout for {@link #pauseForBackup(long)} — 120 seconds.
     * If a pause has been active longer than this, the parked db-worker job
     * auto-resumes with a WARN log so that a forgotten
     * {@code /stockmarket backup resume} does not softlock every future
     * price-history / order-record write.
     * <p>
     * Public so command handlers and tests can reference the exact timeout
     * without duplicating the magic number.
     */
    public static final long PAUSE_TIMEOUT_MS = 120_000L;

    /**
     * True while a backup pause job is queued or actively parked on the db-worker.
     * Flipped false in the worker's {@code finally} block (either after
     * {@link #resumeFromBackup()} or after the safety-timeout auto-resume).
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * Latch the parked db-worker job blocks on while paused for backup.
     * Recreated on every {@link #pauseForBackup(long)} call and cleared in the
     * worker's {@code finally} block. Volatile because the command handler
     * threads and the worker thread both touch it.
     */
    private volatile CountDownLatch resumeLatch = null;

    /**
     * Wall-clock time (ms since epoch) when the current pause was requested,
     * or 0 while not paused. Read by {@link #getPauseElapsedMs()} for the
     * {@code /stockmarket backup status} feedback message.
     */
    private volatile long pauseStartedAtMs = 0L;


    /**
     * Should be called only when the database does not currently exist in a world save.
     * Databases need to be saved on a per-world basis so that we don't have to worry
     * about cross-world data.
     *
     * @return true if all tables were created successfully, false if any table creation failed.
     */
    public boolean createDatabase(MinecraftServer server) {
        try {
            executeSqlFile("/sql/MarketPrice.sql");
            executeSqlFile("/sql/OrderHistory.sql");
            connection.commit();
            migrateSchema();
            return true;
        }
        catch(SQLException | IOException e){
            StockMarketMod.LOGGER.error("Failed to create database table {}", e.getMessage());
            return false;
        }
    }

    // Adds columns introduced after the initial schema. Silently ignores if they already exist.
    private void migrateSchema() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE MarketPrice ADD COLUMN traded_volume REAL NOT NULL DEFAULT 0");
            connection.commit();
        } catch (SQLException ignored) {
            // Column already exists — expected for new databases
            try { connection.rollback(); } catch (SQLException e) { /* ignore */ }
        }

        // Inter-market group UUID columns for linking cross-market trade records
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE OrderHistory ADD COLUMN intermarket_group_one INTEGER NOT NULL DEFAULT 0");
            connection.commit();
        } catch (SQLException ignored) {
            try { connection.rollback(); } catch (SQLException e) { /* ignore */ }
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE OrderHistory ADD COLUMN intermarket_group_two INTEGER NOT NULL DEFAULT 0");
            connection.commit();
        } catch (SQLException ignored) {
            try { connection.rollback(); } catch (SQLException e) { /* ignore */ }
        }
    }



    public void executeSqlFile(String resourcePath) throws IOException, SQLException {
        try (InputStream is = DatabaseManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("SQL file not found: " + resourcePath);

            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            try (Statement stmt = connection.createStatement()) {
                for (String statement : sql.split(";")) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        }
    }

    public void connectToDatabase(MinecraftServer server){

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path dbPath = worldPath.resolve(DATABASE_PATH);
        String url = "jdbc:sqlite:" + Path.of(String.valueOf(dbPath.toAbsolutePath()), "stockdata.db");
        Class<?> driverClass = null;
        Exception exception = null;
        try{
            driverClass = ClassLoader.getSystemClassLoader().loadClass("org.sqlite.JDBC");
        }catch(ClassNotFoundException e)
        {
            exception = e;
        }
        if(exception != null)
        {
            try{
                driverClass = DatabaseManager.class.getClassLoader().loadClass("org.sqlite.JDBC");
            }catch(ClassNotFoundException e)
            {
                StockMarketMod.LOGGER.error("Failed to register JDBC driver", e);
                return;
            }
        }
        try {
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("Failed to register JDBC driver", e);
            return;
        }

        try {

            StockMarketMod.LOGGER.info("Database path: {}", dbPath.toAbsolutePath());
            StockMarketMod.LOGGER.info("Database URL: {}", url);
            if(!Files.exists(dbPath.toAbsolutePath())){
                Files.createDirectories(dbPath.toAbsolutePath());
            }
            connection = DriverManager.getConnection(url);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            StockMarketMod.LOGGER.error("Failed to connect to database {}", e.getMessage());
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(createDatabase(server)) {
            StockMarketMod.LOGGER.info("Successfully connected to database {}", url);
        } else {
            StockMarketMod.LOGGER.error("Database connected but table creation failed for: {}", url);
        }
    }


    /**
     * Closes the database connection and shuts down the executor service.
     * Should be called when the server stops or this instance is no longer needed.
     */
    public void close(){
        try{
            if(connection != null && !connection.isClosed()) {
                connection.commit();
                connection.close();
                StockMarketMod.LOGGER.info("Successfully closed database connection");
            }
        }
        catch (SQLException e) {
            StockMarketMod.LOGGER.error("Failed to close database connection {}",  e.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    public ExecutorService getDatabaseThread(){
        return executor;
    }

    public Connection getConnection(){
        return connection;
    }

    /**
     * Submits a "commit + park" job to the single-thread {@code db-worker} executor so an
     * external filesystem backup (e.g. {@code tar} of the world directory) can capture a
     * consistent snapshot of the SQLite files without racing an in-flight commit.
     * <p>
     * Because the worker is single-threaded, any writes queued before this call complete
     * first — the worker drains them. Only once the pause job becomes the currently-running
     * task does it emit the distinctive
     * {@code [StockMarket] db-worker paused for backup} log line — that log line is the ack
     * a backup script waits for (e.g. via {@code tail -F ... | grep -q ...}); the command
     * handler itself must not block on the worker.
     * <p>
     * After parking, the job blocks on a {@link CountDownLatch} for at most {@code timeoutMs}
     * milliseconds. If {@link #resumeFromBackup()} is called before then, the worker resumes
     * cleanly. If the timeout expires, the worker WARN-logs an auto-resume message and
     * continues on its own — this safety timeout prevents softlock when the operator forgets
     * the paired {@code resume} call.
     * <p>
     * Not stackable: calling {@code pauseForBackup} while already paused returns {@code false}
     * and does not queue a second latch.
     *
     * @param timeoutMs safety timeout in milliseconds; the parked worker auto-resumes after
     *                  this many ms if no {@link #resumeFromBackup()} call arrives. Typically
     *                  {@link #PAUSE_TIMEOUT_MS}.
     * @return {@code true} if the pause job was submitted; {@code false} if already paused
     */
    public boolean pauseForBackup(long timeoutMs) {
        if (!paused.compareAndSet(false, true)) {
            return false;
        }
        pauseStartedAtMs = System.currentTimeMillis();
        final CountDownLatch latch = new CountDownLatch(1);
        resumeLatch = latch;
        final long safetyTimeoutMs = timeoutMs;
        executor.submit(() -> {
            try {
                // Defensive commit — the write path uses setAutoCommit(false), so an open
                // transaction from the last queued write may still be pending.
                try {
                    if (connection != null && !connection.isClosed() && !connection.getAutoCommit()) {
                        connection.commit();
                    }
                } catch (SQLException e) {
                    StockMarketMod.LOGGER.error("[StockMarket] db-worker pause commit failed: {}", e.getMessage());
                }
                // Distinctive ack line — backup scripts grep for this exact string.
                StockMarketMod.LOGGER.info("[StockMarket] db-worker paused for backup");
                boolean released;
                try {
                    released = latch.await(safetyTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    released = false;
                }
                if (!released) {
                    StockMarketMod.LOGGER.warn(
                            "[StockMarket] db-worker auto-resumed after {}ms safety timeout — operator forgot 'resume'?",
                            safetyTimeoutMs);
                }
                StockMarketMod.LOGGER.info("[StockMarket] db-worker resumed");
            } finally {
                paused.set(false);
                resumeLatch = null;
                pauseStartedAtMs = 0L;
            }
        });
        return true;
    }

    /**
     * Releases the currently parked db-worker backup job, if any.
     * <p>
     * The paired {@code [StockMarket] db-worker resumed} log line is emitted from the
     * worker itself (not from this method) so the console transcript reflects the actual
     * state transition on the worker thread.
     *
     * @return {@code true} if a pause was active and has been signalled to resume;
     *         {@code false} if the worker was not paused
     */
    public boolean resumeFromBackup() {
        final CountDownLatch latch = resumeLatch;
        if (!paused.get() || latch == null) {
            return false;
        }
        latch.countDown();
        return true;
    }

    /**
     * @return {@code true} while a {@link #pauseForBackup(long)} job is queued or actively
     *         parked on the db-worker; {@code false} otherwise
     */
    public boolean isPausedForBackup() {
        return paused.get();
    }

    /**
     * @return elapsed milliseconds since the current pause was requested, or 0 if not paused
     */
    public long getPauseElapsedMs() {
        if (!paused.get()) return 0L;
        long started = pauseStartedAtMs;
        if (started == 0L) return 0L;
        return System.currentTimeMillis() - started;
    }

    public boolean commitTransaction() {
        try{
            connection.commit();
            return true;
        }
        catch (SQLException e){
            try {
                connection.rollback();
            } catch (SQLException re) {
                StockMarketMod.LOGGER.error("Failed to rollback transaction {}", re.getMessage());
            }
            StockMarketMod.LOGGER.error("Failed to commit transaction, rolled back {}", e.getMessage());
            return false;
        }
    }



}
