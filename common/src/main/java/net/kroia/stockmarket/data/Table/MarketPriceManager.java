package net.kroia.stockmarket.data.Table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.Table.Data.MarketPriceStruct;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MarketPriceManager implements ITableManager<MarketPriceStruct> {

    public static final String INSERT = "INSERT INTO MarketPrice (marketid, price, low, high, time) VALUES (?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT * FROM MarketPrice WHERE marketid = ? AND time > ? AND time < ? LIMIT ?";



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
        return new MarketPriceManager();
    }

    public void queueRecord(PreparedStatement stmt, MarketPriceStruct data){
        try {
            stmt.setShort(1, data.id());
            stmt.setInt(2, data.price());
            stmt.setInt(3, data.low());
            stmt.setInt(4, data.high());
            stmt.setInt(5, data.time());
            stmt.addBatch();
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.error("Failed to queue market price record", e);
        }

    }

    public CompletableFuture<List<MarketPriceStruct>> getHistory(int startTime, int endTime, int limit, short marketid){
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<MarketPriceStruct> result = new ArrayList<>();
                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(SELECT);
                preparedStatement.setShort(1, marketid);
                preparedStatement.setInt(2, startTime);
                preparedStatement.setInt(3, endTime);
                preparedStatement.setInt(4, limit);
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
            return new MarketPriceStruct(rs.getShort(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }
}
