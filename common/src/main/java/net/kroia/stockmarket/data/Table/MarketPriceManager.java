package net.kroia.stockmarket.data.Table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.Table.Data.MarketPriceStruct;
import net.kroia.stockmarket.data.Table.Data.OrderRecordStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MarketPriceManager implements ITableManager<MarketPriceStruct> {
    private static MarketPriceManager instance;

    public static final String INSERT = "INSERT INTO MarketPrice (marketid, price, low, high, time) VALUES (?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT * FROM MarketPrice";
    public static final String DELETE = "DELETE FROM MarketPrice";
    public static final String COUNT =  "SELECT COUNT(*) FROM MarketPrice";



    public CompletableFuture<Void> save(MarketPriceStruct data) {
        return CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(INSERT);
                queueRecord(preparedStatement, data);
                preparedStatement.execute();
                DatabaseManager.commitTransaction();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, DatabaseManager.getDatabaseThread());
    }


    public CompletableFuture<Void> save(List<MarketPriceStruct> data) {
        return CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(INSERT);
                data.forEach(d -> queueRecord(preparedStatement, d));
                preparedStatement.executeBatch();
                DatabaseManager.commitTransaction();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, DatabaseManager.getDatabaseThread());
    }

    public static MarketPriceManager create(){
        if(instance == null){
            instance = new MarketPriceManager();
        }
        return instance;
    }

    public void queueRecord(PreparedStatement stmt, MarketPriceStruct data){
        try {
            stmt.setShort(1, data.id());
            stmt.setInt(2, data.price());
            stmt.setInt(3, data.low());
            stmt.setInt(4, data.high());
            stmt.setLong(5, data.time());
            stmt.addBatch();
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.error("Failed to queue market price record", e);
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

                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(statement);
                preparedStatement.executeUpdate();
                DatabaseManager.commitTransaction();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, DatabaseManager.getDatabaseThread());
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
                    statement = statement + " LIMIT " + limit;
                }
                List<MarketPriceStruct> result = new ArrayList<>();
                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(statement);
                ResultSet resultSet = preparedStatement.executeQuery();
                DatabaseManager.commitTransaction();
                while(resultSet.next()){
                    result.add(mapRow(resultSet));
                }
                return result;

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        }, DatabaseManager.getDatabaseThread());
    }

    public MarketPriceStruct mapRow(ResultSet rs){
        try {
            return new MarketPriceStruct(rs.getShort(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getLong(5));
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
                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(statement);
                ResultSet resultSet = preparedStatement.executeQuery();
                DatabaseManager.commitTransaction();
                if (resultSet.next()){
                    return resultSet.getInt(1);
                };
                return 0;

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        }, DatabaseManager.getDatabaseThread());
    }
}
