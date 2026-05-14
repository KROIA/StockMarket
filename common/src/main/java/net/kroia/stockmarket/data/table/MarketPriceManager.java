package net.kroia.stockmarket.data.table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MarketPriceManager implements ITableManager<MarketPriceStruct> {

    private final DatabaseManager databaseManager;

    public static final String INSERT = "INSERT INTO MarketPrice (marketid, open, low, high, time, traded_volume) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT marketid, open, low, high, time, traded_volume FROM MarketPrice";
    public static final String DELETE = "DELETE FROM MarketPrice";
    public static final String COUNT =  "SELECT COUNT(*) FROM MarketPrice";

    public MarketPriceManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public CompletableFuture<Void> save(MarketPriceStruct data) {
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


    public CompletableFuture<Void> save(List<MarketPriceStruct> data) {
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

    public void queueRecord(PreparedStatement stmt, MarketPriceStruct data){
        try {
            stmt.setShort(1, data.id());
            stmt.setLong(2, data.open());
            stmt.setLong(3, data.low());
            stmt.setLong(4, data.high());
            stmt.setLong(5, data.time());
            stmt.setFloat(6, data.tradedVolume());
            stmt.addBatch();
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.error("Failed to queue stockmarket price record", e);
        }

    }

    public CompletableFuture<List<MarketPriceStruct>> getHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> marketFilter, int limit){
        return query(dateFilter, marketFilter, SELECT, limit);
    }

    public CompletableFuture<Void> removeHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> marketFilter) {
        return CompletableFuture.runAsync(() -> {
            try {
                String statement = DELETE;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    if (marketFilter.isPresent()) {
                        statement += " AND " + marketFilter.get().getClause("marketid");
                    }
                } else if (marketFilter.isPresent()) {
                    statement += " WHERE " + marketFilter.get().getClause("marketid");
                }

                try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(statement)) {
                    int idx = 1;
                    if (dateFilter.isPresent()) {
                        idx = dateFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (marketFilter.isPresent()) {
                        idx = marketFilter.get().bindParameters(preparedStatement, idx);
                    }
                    preparedStatement.executeUpdate();
                    databaseManager.commitTransaction();
                }
            } catch (SQLException e) {
                StockMarketMod.LOGGER.warn("Failed to remove MarketPrice history: {}", e.getMessage());
            }
        }, databaseManager.getDatabaseThread());
    }

    public CompletableFuture<List<MarketPriceStruct>> query(Optional<DateFilter> dateFilter, Optional<EqualityFilter> marketFilter, String stmt, int limit){
        return CompletableFuture.supplyAsync(() -> {
            try {
                String statement = stmt;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    if (marketFilter.isPresent()) {
                        statement += " AND " + marketFilter.get().getClause("marketid");
                    }
                } else if (marketFilter.isPresent()) {
                    statement += " WHERE " + marketFilter.get().getClause("marketid");
                }
                if(limit > 0){
                    statement = statement + " LIMIT ?";
                }
                List<MarketPriceStruct> result = new ArrayList<>();
                try (PreparedStatement preparedStatement = databaseManager.getConnection().prepareStatement(statement)) {
                    int idx = 1;
                    if (dateFilter.isPresent()) {
                        idx = dateFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (marketFilter.isPresent()) {
                        idx = marketFilter.get().bindParameters(preparedStatement, idx);
                    }
                    if (limit > 0) {
                        preparedStatement.setInt(idx, limit);
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        databaseManager.commitTransaction();
                        while (resultSet.next()) {
                            MarketPriceStruct row = mapRow(resultSet);
                            if (row != null)
                                result.add(row);
                        }
                    }
                }
                return result;

            } catch (SQLException e) {
                StockMarketMod.LOGGER.warn("Failed to query MarketPrice: {}", e.getMessage());
                return List.of();
            }

        }, databaseManager.getDatabaseThread());
    }

    public MarketPriceStruct mapRow(ResultSet rs){
        try {
            short id = rs.getShort(1);
            long open = rs.getLong(2);
            long low = rs.getLong(3);
            long high = rs.getLong(4);
            long time = rs.getLong(5);
            float volume = 0f;
            try { volume = rs.getFloat(6); } catch (SQLException ignored) {}
            return new MarketPriceStruct(id, open, low, high, time, volume);
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }

    public CompletableFuture<Integer> getRecordCount(Optional<DateFilter> dateFilter, Optional<EqualityFilter> marketFilter){
        return CompletableFuture.supplyAsync(() -> {
            try {
                String statement = COUNT;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    if (marketFilter.isPresent()) {
                        statement += " AND " + marketFilter.get().getClause("marketid");
                    }
                } else if (marketFilter.isPresent()) {
                    statement += " WHERE " + marketFilter.get().getClause("marketid");
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
                StockMarketMod.LOGGER.warn("Failed to count MarketPrice records: {}", e.getMessage());
                return 0;
            }

        }, databaseManager.getDatabaseThread());
    }
}
