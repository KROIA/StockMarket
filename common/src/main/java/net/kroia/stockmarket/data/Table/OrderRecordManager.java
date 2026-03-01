package net.kroia.stockmarket.data.Table;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.Table.Data.MarketPriceStruct;
import net.kroia.stockmarket.data.Table.Data.OrderRecordStruct;
import net.kroia.stockmarket.data.filter.DataFilter;
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

    public static final String INSERT = "INSERT INTO OrderHistory (itemid, userid, type, amount, price, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String SELECT = "SELECT * FROM OrderHistory";
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
            stmt.setInt(1, data.itemID());
            stmt.setBytes(2, uuidToBytes(data.userID()));
            stmt.setInt(3, data.type());
            stmt.setInt(4, data.amount());
            stmt.setInt(5, data.price());
            stmt.setLong(6, data.time());
            stmt.addBatch();
        }
        catch(SQLException e){

        }
    }

    private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buf = ByteBuffer.wrap(new byte[16]);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    public CompletableFuture<List<OrderRecordStruct>> getHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter){
        return query(dateFilter, userFilter, marketFilter, SELECT);

    }

    public CompletableFuture<List<OrderRecordStruct>> deleteHistory(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter){
        return query(dateFilter, userFilter, marketFilter, DELETE);
    }

    public CompletableFuture<List<OrderRecordStruct>> query(Optional<DateFilter> dateFilter, Optional<EqualityFilter> userFilter, Optional<EqualityFilter> marketFilter, String stmt){
        return CompletableFuture.supplyAsync(() -> {
            try {
                String statement = stmt;
                if(dateFilter.isPresent()){
                    statement = statement + " WHERE " + dateFilter.get().getClause("time");
                }
                if(userFilter.isPresent()){
                    statement = statement + " AND " + userFilter.get().getClause("userid");
                }
                if(marketFilter.isPresent()){
                    statement = statement + " AND " + marketFilter.get().getClause("itemid");
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

    private UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        return new UUID(buf.getLong(), buf.getLong());
    }

    public OrderRecordStruct mapRow(ResultSet rs){
        try {
            return new OrderRecordStruct(rs.getShort(1), bytesToUuid(rs.getBytes(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getLong(6));
        }
        catch(SQLException e){
            StockMarketMod.LOGGER.warn("Failed to read MarketPrice record, is the data corrupt?");
            return null;
        }
    }
}
