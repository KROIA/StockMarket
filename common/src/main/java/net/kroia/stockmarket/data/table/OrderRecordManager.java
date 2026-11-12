package net.kroia.stockmarket.data.table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OrderRecordManager implements ITableManager<OrderRecordStruct>{

    private final DatabaseManager databaseManager;

    public static final String INSERT = "INSERT INTO OrderHistory (itemid, accountid, userid_one, userid_two, type, amount, price, time, intermarket_group_one, intermarket_group_two, order_id, finish_time, limit_price, target_volume, final_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT itemid, accountid, userid_one, userid_two, type, amount, price, time, intermarket_group_one, intermarket_group_two, order_id, finish_time, limit_price, target_volume, final_status FROM OrderHistory";
    public static final String DELETE = "DELETE FROM OrderHistory";
    public static final String COUNT  = "SELECT COUNT(*) FROM OrderHistory";

    /**
     * Records of {@link #save(OrderRecordStruct)} that share one db-worker job, written in one transaction.
     *
     * @see #openBatch
     */
    private static final class Batch {
        final List<OrderRecordStruct> records = new ArrayList<>();
        final CompletableFuture<Void> done = new CompletableFuture<>();
    }

    /**
     * T-226: the batch new records join, null when the next {@link #save(OrderRecordStruct)} has to queue a new
     * one. Every finished player order used to be its own db-worker job with its own commit (about 7 to 10 ms
     * each), so heavy trading queued thousands of them, and a server stop hit {@link DatabaseManager#close()}'s
     * drain cap and dropped the rest. Now one job writes everything that arrived until it started.
     * <p>
     * A batch stays open from its first record until its job starts, or until this manager queues another job
     * ({@link #sealBatch()}), so this manager's jobs keep their FIFO order: a record saved after a
     * {@code removeHistory} call is still written after that delete. Guarded by {@code this}.
     */
    private Batch openBatch;

    public OrderRecordManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Queues one order record. It joins the open batch, or opens a new one and queues its write job.
     *
     * @param data the record to insert
     * @return completes when the record's batch is committed; fails if the write failed
     * @throws java.util.concurrent.RejectedExecutionException if a new batch is needed after the database closed
     */
    public CompletableFuture<Void> save(OrderRecordStruct data) {
        synchronized (this) {
            if (openBatch == null) {
                Batch batch = new Batch();
                databaseManager.getDatabaseThread().execute(() -> writeBatch(batch)); // throws if closed: stays null
                openBatch = batch;
            }
            // Under the lock, so the job cannot take the records before this one is in.
            openBatch.records.add(data);
            return openBatch.done;
        }
    }

    /** Ends the open batch, so records saved from now on are written by a job queued behind the next one. */
    private synchronized void sealBatch() {
        openBatch = null;
    }

    /**
     * The db-worker job of one batch: closes it, then inserts all its records and commits once.
     *
     * @param batch the batch to write
     */
    private void writeBatch(Batch batch) {
        List<OrderRecordStruct> records;
        synchronized (this) {
            if (openBatch == batch)
                openBatch = null; // from here on, save() queues a new batch behind this job
            records = batch.records;
        }
        try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(INSERT)) {
            records.forEach(d -> queueRecord(preparedStatement, d));
            preparedStatement.executeBatch();
            databaseManager.commitTransaction();
            batch.done.complete(null);
        } catch (SQLException | RuntimeException e) {
            // Production callers ignore the future, so log here: a failed batch loses all of its records.
            StockMarketMod.LOGGER.error("Failed to save {} order records", records.size(), e);
            batch.done.completeExceptionally(e);
        }
    }


    public CompletableFuture<Void> save(List<OrderRecordStruct> data) {
        sealBatch();
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(INSERT)) {
                data.forEach(d -> queueRecord(preparedStatement, d));
                preparedStatement.executeBatch();
                databaseManager.commitTransaction();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, databaseManager.getDatabaseThread());
    }

    @Override
    public void queueRecord(PreparedStatement stmt, OrderRecordStruct data) {
        try {
            stmt.setShort(1, data.itemID());
            stmt.setInt(2, data.bankaccountID());
            stmt.setLong(3, data.user().getMostSignificantBits());
            stmt.setLong(4, data.user().getLeastSignificantBits());
            stmt.setInt(5, data.type());
            stmt.setLong(6, data.amount());
            stmt.setLong(7, data.price());
            stmt.setLong(8, data.time());
            // Inter-market group ID stored as two longs (MSB/LSB); null is encoded as (0, 0)
            if (data.interMarketGroupID() != null) {
                stmt.setLong(9, data.interMarketGroupID().getMostSignificantBits());
                stmt.setLong(10, data.interMarketGroupID().getLeastSignificantBits());
            } else {
                stmt.setLong(9, 0);
                stmt.setLong(10, 0);
            }
            // T-191: persisted order ID, NULL for inter-market legs and unknown IDs
            if (data.orderID() != null)
                stmt.setLong(11, data.orderID());
            else
                stmt.setNull(11, java.sql.Types.INTEGER);
            // SM-317: finish fields, NULL when the record has none
            setNullableLong(stmt, 12, data.finishTime());
            setNullableLong(stmt, 13, data.limitPrice());
            setNullableLong(stmt, 14, data.targetVolume());
            setNullableLong(stmt, 15, data.finalStatus() == null ? null : data.finalStatus().longValue());
            stmt.addBatch();
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.error("Failed to queue order record", e);
        }
    }


    public CompletableFuture<List<OrderRecordStruct>> getHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> accountFilter, Optional<UUIDFilter> userFilter, Optional<EqualityFilter> marketFilter){
        return query(dateFilter, accountFilter, userFilter, marketFilter, SELECT);

    }

    public CompletableFuture<Void> removeHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> accountFilter, Optional<UUIDFilter> userFilter, Optional<EqualityFilter> marketFilter) {
        sealBatch(); // records saved after this delete must not be written before it
        return CompletableFuture.runAsync(() -> {
            try {
                boolean started = false;
                String statement = DELETE;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    started = true;
                }
                if (accountFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + accountFilter.get().getClause("accountid");
                    started = true;
                }
                if(userFilter.isPresent()){
                    statement += (started ? " AND " : " WHERE ") + userFilter.get().getClause("userid_one", "userid_two");
                    started = true;
                }
                if (marketFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + marketFilter.get().getClause("itemid");
                }

                try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(statement)) {
                    int idx = 1;
                    if (dateFilter.isPresent()) {
                        idx = dateFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (accountFilter.isPresent()) {
                        idx = accountFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (userFilter.isPresent()) {
                        idx = userFilter.get().bindUUIDParameters(preparedStatement, idx);
                    }
                    if (marketFilter.isPresent()) {
                        idx = marketFilter.get().bindParameters(preparedStatement, idx);
                    }
                    preparedStatement.executeUpdate();
                    databaseManager.commitTransaction();
                }
            } catch (SQLException e) {
                StockMarketMod.LOGGER.warn("Failed to remove OrderHistory: {}", e.getMessage());
            }
        }, databaseManager.getDatabaseThread());
    }

    public CompletableFuture<List<OrderRecordStruct>> query(Optional<DateFilter> dateFilter, Optional<EqualityFilter> accountFilter, Optional<UUIDFilter> userFilter, Optional<EqualityFilter> marketFilter, String stmt){
        sealBatch(); // the result holds the records saved before this call, not later ones
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean started = false;
                String statement = stmt;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    started = true;
                }
                if (accountFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + accountFilter.get().getClause("accountid");
                    started = true;
                }
                if(userFilter.isPresent()){
                    statement += (started ? " AND " : " WHERE ") + userFilter.get().getClause("userid_one", "userid_two");
                    started = true;
                }
                if (marketFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + marketFilter.get().getClause("itemid");
                }
                List<OrderRecordStruct> result = new ArrayList<>();
                try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(statement)) {
                    int idx = 1;
                    if (dateFilter.isPresent()) {
                        idx = dateFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (accountFilter.isPresent()) {
                        idx = accountFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (userFilter.isPresent()) {
                        idx = userFilter.get().bindUUIDParameters(preparedStatement, idx);
                    }
                    if (marketFilter.isPresent()) {
                        idx = marketFilter.get().bindParameters(preparedStatement, idx);
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        // Read-only SELECT — no commit needed. On SQLite with
                        // autoCommit=false a read-only statement doesn't open a
                        // transaction, so calling commit() here would log a
                        // spurious "cannot commit - no transaction is active"
                        // error.
                        while (resultSet.next()) {
                            OrderRecordStruct row = mapRow(resultSet);
                            if (row != null)
                                result.add(row);
                        }
                    }
                }
                return result;

            } catch (SQLException e) {
                StockMarketMod.LOGGER.warn("Failed to query OrderHistory: {}", e.getMessage());
                return List.of();
            }

        }, databaseManager.getDatabaseThread());
    }


    public CompletableFuture<Integer> getRecordCount(Optional<DateFilter> dateFilter, Optional<EqualityFilter> marketFilter){
        sealBatch(); // counts the records saved before this call, not later ones
        return CompletableFuture.supplyAsync(() -> {
            try {
                String statement = COUNT;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    if (marketFilter.isPresent()) {
                        statement += " AND " + marketFilter.get().getClause("itemid");
                    }
                } else if (marketFilter.isPresent()) {
                    statement += " WHERE " + marketFilter.get().getClause("itemid");
                }
                try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(statement)) {
                    int idx = 1;
                    if (dateFilter.isPresent()) {
                        idx = dateFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (marketFilter.isPresent()) {
                        idx = marketFilter.get().bindParameters(preparedStatement, idx);
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        // Read-only SELECT — no commit needed (see rationale in query()).
                        if (resultSet.next()) {
                            return resultSet.getInt(1);
                        }
                    }
                }
                return 0;
            } catch (SQLException e) {
                StockMarketMod.LOGGER.warn("Failed to count OrderHistory records: {}", e.getMessage());
                return 0;
            }
        }, databaseManager.getDatabaseThread());
    }

    /** SM-317: binds {@code value}, or SQL NULL if it is null. */
    private static void setNullableLong(PreparedStatement stmt, int index, Long value) throws SQLException {
        if (value != null)
            stmt.setLong(index, value);
        else
            stmt.setNull(index, java.sql.Types.INTEGER);
    }

    /** SM-317: reads an INTEGER column, null if it holds SQL NULL. */
    private static Long getNullableLong(ResultSet rs, int index) throws SQLException {
        long value = rs.getLong(index);
        return rs.wasNull() ? null : value;
    }

    public OrderRecordStruct mapRow(ResultSet rs){
        try {
            // Reconstruct inter-market group UUID from two longs; (0, 0) means null (no inter-market link)
            long groupMsb = rs.getLong(9);
            long groupLsb = rs.getLong(10);
            UUID groupID = (groupMsb == 0 && groupLsb == 0) ? null : new UUID(groupMsb, groupLsb);
            long orderID = rs.getLong(11);
            Long orderIDOrNull = rs.wasNull() ? null : orderID;
            Long finalStatus = getNullableLong(rs, 15); // SM-317 columns are NULL in older rows
            return new OrderRecordStruct(rs.getShort(1), rs.getInt(2), new UUID(rs.getLong(3), rs.getLong(4)), rs.getInt(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), groupID, orderIDOrNull,
                    getNullableLong(rs, 12), getNullableLong(rs, 13), getNullableLong(rs, 14),
                    finalStatus == null ? null : finalStatus.intValue());
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }
}
