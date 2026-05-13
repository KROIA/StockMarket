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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class DatabaseManager {
    private Connection connection;

    private final ExecutorService executor = Executors.newSingleThreadExecutor( r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });


    public static final Path DATABASE_PATH = DataManager.SQL_DATABASE;


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
