package net.kroia.stockmarket.data;

import net.kroia.banksystem.data.DiagnosticsDatabase;
import net.kroia.modutilities.networking.multi_server.IServerIdentity;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.stockmarket.market.OrderLedger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * T-215 (audit tool SM-D1): records which server each player order came from, in the OrderOrigin table of
 * {@code <world>/data/StockMarket/Database/stockmarket_diagnostics.db}.
 * <p>
 * The file is written by BankSystem's {@link DiagnosticsDatabase} with this mod's own schema: its own connection and
 * daemon thread {@value #THREAD_NAME} (never the server thread or the StockMarket db-worker), opened at master start,
 * a corrupt file is moved aside and recreated, a full queue drops writes. Gameplay never reads it, so deleting
 * it never changes game state. The file holds only Meta (created_ms, schema_version {@value #SCHEMA_VERSION}),
 * OrderOrigin and TradeAlert (SM-316, see {@code TradeAlerts.Row}), none of BankSystem's tables; no other Meta keys
 * are written.
 * <p>
 * Master only: orders are created there. The table holds only what is stored nowhere else. Account, item and time
 * of an order are in OrderHistory, the BankSystem TransactionLog and the saved order book under the same ref, so a
 * row is just the ref and its origin:
 * <pre>
 * OrderOrigin(ref TEXT PRIMARY KEY,      -- "sm:order:&lt;id&gt;" or "sm:im:&lt;inter-market group uuid&gt;" (the ledger refs)
 *             origin_server_uid TEXT,    -- ModUtilities server uid of the sending server, null if unknown
 *             origin_name TEXT NOT NULL) -- slave name, '' for an order placed on the master itself
 * </pre>
 * Nothing here blocks or throws to the caller.
 */
public final class OrderOriginLog {
    public static final String FILE_NAME = "stockmarket_diagnostics.db";
    public static final String THREAD_NAME = "stockmarket-diag-db";
    /** Meta schema_version of the file; bump it when a table or column is added to {@link #SCHEMA}. 2 = TradeAlert (SM-316). */
    public static final String SCHEMA_VERSION = "2";
    /**
     * The file's tables after Meta, run in order on every open, so each statement must be idempotent. Files written
     * before a table existed get it at their next open (CREATE TABLE IF NOT EXISTS).
     */
    static final List<String> SCHEMA = List.of(
            "CREATE TABLE IF NOT EXISTS OrderOrigin "
                    + "(ref TEXT PRIMARY KEY, origin_server_uid TEXT, origin_name TEXT NOT NULL)",
            // SM-316: one row per TradeAlerts.raise; columns and kind codes in TradeAlerts.Row
            "CREATE TABLE IF NOT EXISTS TradeAlert (id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, kind TEXT NOT NULL, "
                    + "player_uuid TEXT, account_number INTEGER, item_id INTEGER, amount INTEGER, window_ms INTEGER, "
                    + "threshold REAL, text TEXT NOT NULL, order_ids TEXT)");
    private static final String INSERT = "INSERT OR IGNORE INTO OrderOrigin (ref, origin_server_uid, origin_name) VALUES (?, ?, ?)";
    /**
     * SM-316: inserts one TradeAlert row; parameters in column order ts, kind, player_uuid, account_number, item_id,
     * amount, window_ms, threshold, text, order_ids. Written by {@code TradeAlerts.raise} through {@link #live()}.
     */
    public static final String INSERT_TRADE_ALERT = "INSERT INTO TradeAlert (ts, kind, player_uuid, account_number, item_id, "
            + "amount, window_ms, threshold, text, order_ids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static volatile @Nullable DiagnosticsDatabase live;

    private OrderOriginLog() {}

    /**
     * Opens the log for this server session (master start) and queues an idempotent schema statement, so the writer
     * opens (creates) the file now, on its own thread. Creating the .db, -wal and -shm files can take seconds while a
     * real-time virus scanner checks them; done lazily, the first orders' rows would wait behind that.
     *
     * @param databaseFolder the StockMarket database folder of the world
     */
    public static void open(Path databaseFolder) {
        close();
        DiagnosticsDatabase db = create(databaseFolder.resolve(FILE_NAME));
        db.execute(SCHEMA.get(0)); // CREATE TABLE IF NOT EXISTS: a no-op write that only triggers the open
        live = db;
    }

    /**
     * Creates a writer for an order-origin file: Meta plus {@link #SCHEMA}, thread {@value #THREAD_NAME}. Nothing is
     * opened until the first write. Also used by the tests for a file in a temp folder.
     *
     * @param file the SQLite file to write
     * @return the writer; the caller closes it
     */
    public static DiagnosticsDatabase create(Path file) {
        return new DiagnosticsDatabase(file, THREAD_NAME, SCHEMA_VERSION, SCHEMA);
    }

    /** Drains the queued rows (up to 2 s) and closes the file (server stop). */
    public static void close() {
        DiagnosticsDatabase db = live;
        live = null;
        if (db != null)
            db.close();
    }

    /** @return the open log of this session, or null (slave, client, before start, after stop) */
    public static @Nullable DiagnosticsDatabase live() {
        return live;
    }

    /**
     * Records the origin of a regular player order the master just accepted.
     *
     * @param orderID the order's ID (assigned by {@code putOrder})
     * @param slaveID the slave the request came from, "" or null when it was placed on the master
     */
    public static void order(long orderID, @Nullable String slaveID) {
        record(live, OrderLedger.orderRef(orderID), slaveID, MultiServerManager.getIdentity());
    }

    /**
     * Records the origin of an inter-market order the master just accepted; both legs share the group ref.
     *
     * @param groupID the order's inter-market group ID
     * @param slaveID the slave the request came from, "" or null when it was placed on the master
     */
    public static void interMarket(UUID groupID, @Nullable String slaveID) {
        record(live, OrderLedger.interMarketRef(groupID), slaveID, MultiServerManager.getIdentity());
    }

    /**
     * Queues one OrderOrigin row; a second row with the same ref is ignored.
     *
     * @param db       the target file, made by {@link #create(Path)} (it holds the table), or null to do nothing
     * @param ref      the order's ledger ref
     * @param slaveID  the slave the order came from, "" or null for the master itself
     * @param identity this server's identity, or null if not known yet (the uid is then null)
     */
    public static void record(@Nullable DiagnosticsDatabase db, String ref, @Nullable String slaveID, @Nullable IServerIdentity identity) {
        if (db == null)
            return;
        try {
            String name = slaveID == null ? "" : slaveID;
            UUID uid = identity == null ? null : name.isEmpty() ? identity.serverUid() : identity.slaveUid(name);
            db.execute(INSERT, ref, uid == null ? null : uid.toString(), name);
        } catch (RuntimeException e) { // diagnostics must never break order placement
            StockMarketMod.LOGGER.warn("[OrderOriginLog] could not record {}: {}", ref, e.toString());
        }
    }
}
