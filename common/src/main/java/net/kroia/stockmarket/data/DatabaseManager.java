package net.kroia.stockmarket.data;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.sqlite.JDBC;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class DatabaseManager {
    private static Connection connection;
    private static Statement statement;
    private static ResultSet resultSet;
    private static PreparedStatement preparedStatement;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor( r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });


    public static final Path DATABASE_PATH = Path.of("data", "stockmarket", "database");


    /**
     * Should be called only when the database does not currently exist in a world save.
     * Databases need to be saved on a per-world basis so that we don't have to worry
     * about cross-world data.
     */
    public static void createDatabase(MinecraftServer server) {
        try {
            DatabaseManager.executeSqlFile("/sql/MarketPrice.sql");
            DatabaseManager.executeSqlFile("/sql/OrderHistory.sql");
        }
        catch(SQLException | IOException e){
            StockMarketMod.LOGGER.error("Failed to create database table {}", e.getMessage());
        }
    }



    public static void executeSqlFile(String resourcePath) throws IOException, SQLException {
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

    public static void connectToDatabase(MinecraftServer server){

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path dbPath = worldPath.resolve(DATABASE_PATH);
        String url = "jdbc:sqlite:" + Path.of(String.valueOf(dbPath.toAbsolutePath()), "stockdata.db");
        try {
            Class<?> driverClass = ClassLoader.getSystemClassLoader().loadClass("org.sqlite.JDBC");
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
        createDatabase(server);
        StockMarketMod.LOGGER.info("Successfully connected to database {}", url);
    }


    public static void shutdownDatabase(MinecraftServer server){
        try{
            if(connection != null && !connection.isClosed()) {
                connection.commit();
                connection.close();
                StockMarketMod.LOGGER.info("Successfully closed database connection {}", connection.getCatalog());
            }
        }
        catch (SQLException e) {
                StockMarketMod.LOGGER.error("Failed to shutdown database {}",  e.getMessage());
            }

    }


    public static ExecutorService getDatabaseThread(){
        return executor;
    }

    public static Connection getConnection(){
        return connection;
    }

    public static void commitTransaction() throws SQLException {
        try{
            connection.commit();
        }
        catch (SQLException e){
            connection.rollback();
            StockMarketMod.LOGGER.error("Failed to commit transaction {}",  e.getMessage());
        }
    }



}
