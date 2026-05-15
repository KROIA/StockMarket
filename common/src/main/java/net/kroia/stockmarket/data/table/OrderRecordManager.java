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

    public static final String INSERT = "INSERT INTO OrderHistory (itemid, accountid, userid_one, userid_two, type, amount, price, time, intermarket_group_one, intermarket_group_two) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT itemid, accountid, userid_one, userid_two, type, amount, price, time, intermarket_group_one, intermarket_group_two FROM OrderHistory";
    public static final String DELETE = "DELETE FROM OrderHistory";
    public static final String COUNT  = "SELECT COUNT(*) FROM OrderHistory";

    public OrderRecordManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Void> save(OrderRecordStruct data) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(INSERT)) {
                queueRecord(preparedStatement, data);
                preparedStatement.execute();
                databaseManager.commitTransaction();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, databaseManager.getDatabaseThread());
    }


    public CompletableFuture<Void> save(List<OrderRecordStruct> data) {
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
                        databaseManager.commitTransaction();
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
                        databaseManager.commitTransaction();
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

    public OrderRecordStruct mapRow(ResultSet rs){
        try {
            // Reconstruct inter-market group UUID from two longs; (0, 0) means null (no inter-market link)
            long groupMsb = rs.getLong(9);
            long groupLsb = rs.getLong(10);
            UUID groupID = (groupMsb == 0 && groupLsb == 0) ? null : new UUID(groupMsb, groupLsb);
            return new OrderRecordStruct(rs.getShort(1), rs.getInt(2), new UUID(rs.getLong(3), rs.getLong(4)), rs.getInt(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), groupID);
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }
}
