package net.kroia.stockmarket.data.table;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ITableManager<T extends Record> {

    public CompletableFuture<Void> save(T data);
    public CompletableFuture<Void> save(List<T> data);
    public void queueRecord(PreparedStatement stmt, T data);
}
