package net.kroia.stockmarket.data.table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OrderRecordManager implements ITableManager<OrderRecordStruct>{
    private static OrderRecordManager instance;

    public static final String INSERT = "INSERT INTO OrderHistory (itemid, marketid, type, amount, price, time) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT itemid, marketid, type, amount, price, time FROM OrderHistory";
    public static final String DELETE = "DELETE FROM OrderHistory";



    public CompletableFuture<Void> save(OrderRecordStruct data) {
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


    public CompletableFuture<Void> save(List<OrderRecordStruct> data) {
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

    @Override
    public void queueRecord(PreparedStatement stmt, OrderRecordStruct data) {
        try {
            stmt.setShort(1, data.itemID());
            stmt.setInt(2, data.bankaccountID());
            stmt.setInt(3, data.type());
            stmt.setLong(4, data.amount());
            stmt.setLong(5, data.price());
            stmt.setLong(6, data.time());
            stmt.addBatch();
        }
        catch(SQLException e){

        }
    }


    public CompletableFuture<List<OrderRecordStruct>> getHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter){
        return query(dateFilter, userFilter, marketFilter, SELECT);

    }

    public CompletableFuture<Void> removeHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter) {
        return CompletableFuture.runAsync(() -> {
            try {
                boolean started = false;
                String statement = DELETE;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    started = true;
                }
                if (userFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + userFilter.get().getClause("marketid");
                    started = true;
                }
                if (marketFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + marketFilter.get().getClause("itemid");
                }

                PreparedStatement preparedStatement = DatabaseManager.getConnection().prepareStatement(statement);
                preparedStatement.executeUpdate();
                DatabaseManager.commitTransaction();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, DatabaseManager.getDatabaseThread());
    }

    public CompletableFuture<List<OrderRecordStruct>> query(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter, String stmt){
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean started = false;
                String statement = stmt;
                if (dateFilter.isPresent()) {
                    statement += " WHERE " + dateFilter.get().getClause("time");
                    started = true;
                }
                if (userFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + userFilter.get().getClause("marketid");
                    started = true;
                }
                if (marketFilter.isPresent()) {
                    statement += (started ? " AND " : " WHERE ") + marketFilter.get().getClause("itemid");
                }
                List<OrderRecordStruct> result = new ArrayList<>();
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


    public OrderRecordStruct mapRow(ResultSet rs){
        try {
            OrderRecordStruct struct =  new OrderRecordStruct(rs.getShort(1), rs.getInt(2), rs.getInt(3), rs.getLong(4), rs.getLong(5), rs.getLong(6));
            return struct;
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }

    public static OrderRecordManager create(){
        if(instance == null){
            instance = new OrderRecordManager();
        }
        return instance;
    }
}
