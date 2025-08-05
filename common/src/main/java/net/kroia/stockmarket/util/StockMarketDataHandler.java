package net.kroia.stockmarket.util;


import com.google.gson.JsonElement;
import net.kroia.modutilities.DataPersistence;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class StockMarketDataHandler extends DataPersistence {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    private final String PLAYER_DATA_FILE_NAME = "Player_data.dat";
    private final String MARKET_DATA_FILE_NAME = "Market_data.dat";
    private final String MARKET_SETTINGS_FILE_NAME = "settings.json";
    private final String DEFAULT_MARKET_SETTINGS_DIRECTORY = "DefaultMarketSetupData";
    private final String DEFAULT_MARKET_PRICE_FILE_NAME = "base_prices.json";
    private boolean isLoaded = false;
    private long tickCounter = 0;
    private int lastPlayerCount = 0;

    public StockMarketDataHandler() {
        super(JsonFormat.PRETTY, NbtFormat.COMPRESSED, Path.of("Finance/StockMarket"));
        setLogger((msg)->{error(msg);},
                (msg,e)->{error(msg, e);},
                (msg)->{info(msg);},
                (msg)->{warn(msg);});
    }

    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;

    }

    public void tickUpdate()
    {
        tickCounter++;
        if(tickCounter >= BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.SAVE_INTERVAL_MINUTES.get() * 1200) // 1 minute = 1200 ticks
        {
            tickCounter = 0;
            // Check if any player is online
            int playerCount = PlayerUtilities.getOnlinePlayers().size();
            if(playerCount > 0 || lastPlayerCount > 0) {
                lastPlayerCount = playerCount;
                saveAllAsync();
            }
        }
    }


    @Override
    public void setLevelSavePath(Path levelSavePath)
    {
        super.setLevelSavePath(levelSavePath);
        createSaveFolder();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public boolean saveAll()
    {
        info("Saving StockMarket Mod data...");
        boolean success = true;
        success &= save_globalSettings();
        success &= save_defaultPrices();
        success &= save_player();
        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER != null)
            success &= save_market();

        if(success)
            info("StockMarket Mod data saved successfully.");
        else
            error("Failed to save StockMarket Mod data.");
        return success;
    }
    public CompletableFuture<Boolean> saveAllAsync()
    {
        info("Saving StockMarket Mod data...");

        CompletableFuture<Boolean> fut1 = save_playerAsync();
        CompletableFuture<Boolean> fut2;
        CompletableFuture<Boolean> fut3 = save_globalSettingsAsync();
        CompletableFuture<Boolean> fut4 = CompletableFuture.supplyAsync(this::save_defaultPrices);
        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER != null)
            fut2 = save_marketAsync();
        else
            fut2 = CompletableFuture.completedFuture(false);

        // Combine all futures to ensure all data is saved before returning
        return CompletableFuture.allOf(fut1, fut2, fut3, fut4).thenApply(v -> {
            boolean allSuccess = fut1.join() && fut2.join() && fut3.join() && fut4.join();
            if(allSuccess)
                info("StockMarket Mod data saved successfully.");
            else
                error("Failed to save StockMarket Mod data.");
            return allSuccess;
        });
    }

    public boolean loadAll()
    {
        isLoaded = false;
        info("Loading StockMarket Mod data...");
        boolean success = true;
        Path settingsFilePath = getGlobalSettingsFilePath();
        if(!fileExists(settingsFilePath)) {
            warn("Market settings file not found, creating default settings file.");
            success &= save_globalSettings(settingsFilePath);
        }
        else
            success &= load_globalSettings(settingsFilePath);

        Path basePricesFilePath = getBasePricesFilePath();
        if(!fileExists(basePricesFilePath)) {
            warn("Base prices file not found, creating default base prices file.");
            BACKEND_INSTANCES.DEFAULT_PRICES.balancePrices(); // Ensure default prices are balanced
            success &= save_defaultPrices(basePricesFilePath);
        }
        else
            success &= load_defaultPrices(basePricesFilePath);


        success &= load_player();
        success &= load_market();

        if(success) {
            info("StockMarket Mod data loaded successfully.");
            isLoaded = true;
        }
        else
            error("Failed to load StockMarket Mod data.");
        return success;
    }

    public boolean save_player()
    {
        CompoundTag data = new CompoundTag();
        ServerPlayerList.saveToTag(data);
        return saveDataCompound(getPlayerDataFilePath(), data);
    }
    public CompletableFuture<Boolean> save_playerAsync()
    {
        CompoundTag data = new CompoundTag();
        ServerPlayerList.saveToTag(data);
        // Save player data async because it can take much time when there are many players
        return CompletableFuture.supplyAsync(() -> {
            return saveDataCompound(getPlayerDataFilePath(), data);
        });
    }
    public boolean load_player()
    {
        CompoundTag data = readDataCompound(getPlayerDataFilePath());
        if(data != null)
            return ServerPlayerList.loadFromTag(data);
        return false;
    }

    public boolean save_market()
    {
        boolean success = true;
        CompoundTag data = new CompoundTag();
        CompoundTag marketData = new CompoundTag();
        success = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.save(marketData);
        data.put("market", marketData);
        data.putString("version", StockMarketMod.VERSION);

        if(success)
            success = saveDataCompound(getMarketDataFilePath(), data);
        return success;
    }
    public CompletableFuture<Boolean> save_marketAsync()
    {
        CompoundTag data = new CompoundTag();
        CompoundTag marketData = new CompoundTag();

        CompletableFuture<Boolean> future;
        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.save(marketData)) {
            data.put("market", marketData);
            data.putString("version", StockMarketMod.VERSION);

            // Save market data async because it can take much time when there are many trading items
            future = CompletableFuture.supplyAsync(() -> {
                return saveDataCompound(getMarketDataFilePath(), data);
            });
        }
        else
            future = CompletableFuture.completedFuture(false);
        return future;
    }
    public boolean load_market()
    {
        CompoundTag data = readDataCompound(getMarketDataFilePath());
        if(data == null)
            return false;
        // Load server_sender market
        if(!data.contains("market"))
            return false;
        if(!data.contains("version")) {
            boolean backupSuccess = true;
            // copy stockmarket folder to a backup folder
            Path backupPath = getAbsoluteSavePath("../StockMarketBackup_" + System.currentTimeMillis());
            // create path
            if(!createFolder(backupPath)) {
                error("Failed to create backup folder: " + backupPath);
                backupSuccess = false;
            }
            // copy folder
            Path stockMarketFolder = getAbsoluteSavePath();
            try{
                copyFolder(stockMarketFolder, backupPath);
            }catch (IOException e) {
                error("Failed to copy StockMarket folder to backup folder: " + backupPath, e);
                backupSuccess = false;
            }
            String msg = "Market data file is missing version information. This means you updated the mod to a newer version.\n"+
                    "In that case the changes are not compatible with the previous version.\n"+
                    "The market must therefore be reset.\n";
            if(backupSuccess)
                msg += "To prevent losing the save with the old market data, a copy is created at:\n" + backupPath + "\n";
            else {
                msg += "The backup folder could not be created. Please make sure to backup your world manually,\n" +
                        "if you don't want to loose the old stock market data.\n"+
                        "You can find the data here: "+ stockMarketFolder + "\n";
            }
            error(msg);
            if(backupSuccess)
            {
                // Delete all files and paths in the market data folder recursively
                try {
                    deleteFolderContents(getAbsoluteSavePath());
                } catch (IOException e) {
                    error("Failed to delete old market data files.", e);
                }
            }
            return false;
        }
        CompoundTag marketData = data.getCompound("market");
        return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.load(marketData);
    }

    public Path getGlobalSettingsFilePath()
    {
        return getAbsoluteSavePath(MARKET_SETTINGS_FILE_NAME);
    }
    public Path getMarketDataFilePath()
    {
        return getAbsoluteSavePath(MARKET_DATA_FILE_NAME);
    }
    public Path getPlayerDataFilePath()
    {
        return getAbsoluteSavePath(PLAYER_DATA_FILE_NAME);
    }
    public Path getDefaultMarketSetupDataFolderPath()
    {
        return getAbsoluteSavePath(DEFAULT_MARKET_SETTINGS_DIRECTORY);
    }
    public Path getBasePricesFilePath()
    {
        return getAbsoluteSavePath(DEFAULT_MARKET_PRICE_FILE_NAME);
    }
    public boolean save_globalSettings()
    {
        return save_globalSettings(getGlobalSettingsFilePath());
    }
    public boolean save_globalSettings(Path filePath)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.saveSettings(filePath.toString());
    }

    public CompletableFuture<Boolean> save_globalSettingsAsync()
    {
        return save_globalSettingsAsync(getGlobalSettingsFilePath());
    }
    public CompletableFuture<Boolean> save_globalSettingsAsync(Path filePath)
    {
        return CompletableFuture.supplyAsync(() -> StockMarketDataHandler.save_globalSettingsAsyncStatic(filePath));
    }

    // Helper function to save global settings asynchronously
    private static boolean save_globalSettingsAsyncStatic(Path filePath)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.saveSettings(filePath.toString());
    }


    public boolean load_globalSettings(Path filePath)
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.loadSettings(filePath.toString());
    }

    public boolean save_defaultPrices(Path filePath)
    {
        return BACKEND_INSTANCES.DEFAULT_PRICES.saveSettings(filePath.toString());
    }
    public boolean save_defaultPrices()
    {
        return save_defaultPrices(getBasePricesFilePath());
    }
    public boolean load_defaultPrices(Path filePath)
    {
        return BACKEND_INSTANCES.DEFAULT_PRICES.loadSettings(filePath.toString());
    }
    public boolean load_defaultPrices()
    {
        return load_defaultPrices(getBasePricesFilePath());
    }


    public List<String> getDefaultMarketSetupDataFileNames()
    {
        Path path = getDefaultMarketSetupDataFolderPath();
        if(!folderExists(path)) {
            error("Default market setup data folder does not exist: " + path);
            return new ArrayList<>();
        }
        List<Path> jsonFiles = getJsonFiles(getDefaultMarketSetupDataFolderPath());
        ArrayList<String> fileNames = new ArrayList<>();
        for(Path file : jsonFiles)
        {
            String fileName = file.getFileName().toString();
            // remove file extension
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
            fileNames.add(fileName);
        }
        return fileNames;
    }
    /*public HashMap<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> loadDefaultBotSettings()
    {
        if(!folderExists(getDefaultMarketSetupDataFolderPath())) {
            error("Default bot settings folder does not exist: " + getDefaultMarketSetupDataFolderPath());
            return new HashMap<>();
        }
        List<String> jsonFiles = getDefaultBotSettingsFileNames();
        HashMap<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> settings = new HashMap<>();
        for(String file : jsonFiles)
        {
            HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> map = loadDefaultBotSettings(file);
            settings.put(file, map);
        }
        return settings;
    }
    public HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> loadDefaultBotSettings(String fileName)
    {
        if(!fileName.contains(".json"))
            fileName += ".json";
        HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings> settings = new HashMap<>();
        Path filePath = getDefaultMarketSetupDataFolderPath().resolve(fileName);
        if(!fileExists(filePath)) {
            error("Default bot settings file does not exist: " + fileName);
            return settings;
        }
        JsonElement jsonElement = loadJson(filePath);
        if(jsonElement == null || !jsonElement.isJsonArray()) {
            error("Failed to load default bot settings from file: " + fileName);
            return settings;
        }
        JsonArray jsonArray = jsonElement.getAsJsonArray();
        for(JsonElement element : jsonArray) {
            if(element.isJsonObject()) {
                JsonObject jsonObject = element.getAsJsonObject();
                ServerTradingBotFactory.BotBuilderContainer container = new ServerTradingBotFactory.BotBuilderContainer();
                if(container.fromJson(jsonObject) && container.itemData != null && container.defaultSettings != null) {
                    settings.put(container.itemData.getItemID(), container.defaultSettings);
                } else {
                    error("Invalid BotBuilderContainer in file: " + fileName);
                }
            } else {
                error("Invalid JSON element in file: " + fileName);
            }
        }
        return settings;
    }*/

    /*public boolean saveDefaultBotSettings(ArrayList<ServerTradingBotFactory.BotBuilderContainer> settings, String fileName)
    {
        JsonArray jsonArray = new JsonArray();
        for(ServerTradingBotFactory.BotBuilderContainer container : settings) {
            jsonArray.add(container.toJson());
        }
        return saveJson(jsonArray, getDefaultBotSettingsFolderPath().resolve(fileName));
    }*/
    
    public boolean saveDefaultMarketSetupDataGroup(MarketFactory.DefaultMarketSetupDataGroup category)
    {
        if(category == null || category.groupName == null || category.groupName.isEmpty()) {
            error("Invalid MarketSetupDataGroup:\n" + category);
            return false;
        }
        JsonElement json = category.toJson();
        if(json == null) {
            error("Failed to convert MarketSetupDataGroup to JSON:\n" + category);
            return false;
        }
        Path filePath = getDefaultMarketSetupDataFolderPath().resolve(category.groupName + ".json");
        return saveJson(json, filePath);
    }
    public @Nullable MarketFactory.DefaultMarketSetupDataGroup loadDefaultMarketSetupDataGroup(String groupName)
    {
        if(groupName == null || groupName.isEmpty()) {
            error("Invalid group name: " + groupName);
            return null;
        }
        Path filePath = getDefaultMarketSetupDataFolderPath().resolve(groupName + ".json");
        JsonElement json = loadJson(filePath);
        if(json == null || !json.isJsonObject()) {
            error("Failed to load MarketSetupDataGroup from file: " + filePath);
            return null;
        }
        MarketFactory.DefaultMarketSetupDataGroup category = new MarketFactory.DefaultMarketSetupDataGroup();
        if(!category.fromJson(json)) {
            error("Failed to parse MarketSetupDataGroup from JSON:\n" + json);
            return null;
        }
        return category;
    }

    public boolean basePriceFileExists()
    {
        Path filePath = getBasePricesFilePath();
        return fileExists(filePath);
    }
    public boolean saveBasePricesFile(JsonElement json)
    {
        if(json == null || !json.isJsonObject()) {
            error("Invalid JSON element for base prices: " + json);
            return false;
        }
        return saveJson(json, getBasePricesFilePath());
    }
    public @Nullable JsonElement loadBasePricesFile()
    {
        Path filePath = getBasePricesFilePath();
        if(!fileExists(filePath)) {
            error("Base prices file does not exist: " + filePath);
            return null;
        }
        JsonElement json = loadJson(filePath);
        if(json == null || !json.isJsonObject()) {
            error("Failed to load base prices from file: " + filePath);
            return null;
        }
        return json;
    }

    


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[StockMarketDataHandler] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketDataHandler] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[MarketFactory] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[StockMarketDataHandler] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[StockMarketDataHandler] " + msg);
    }


    public static void copyFolder(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public static void deleteFolderContents(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Provided path is not a directory: " + folder);
        }

        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(folder)) {
                    Files.delete(dir); // delete subdirectory
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
