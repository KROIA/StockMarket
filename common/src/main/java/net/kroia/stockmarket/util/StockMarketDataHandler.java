package net.kroia.stockmarket.util;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.DataPersistence;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.minecraft.nbt.CompoundTag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class StockMarketDataHandler extends DataPersistence {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    private final String PLAYER_DATA_FILE_NAME = "Player_data.dat";
    private final String MARKET_DATA_FILE_NAME = "Market_data.dat";
    private final String MARKET_SETTINGS_FILE_NAME = "settings.json";
    private final String DEFAULT_BOT_SETTINGS_DIRECTORY = "DefaultBotSettings";
    private boolean isLoaded = false;
    private long tickCounter = 0;
    private int lastPlayerCount = 0;

    public StockMarketDataHandler() {
        super(JsonFormat.PRETTY, NbtFormat.COMPRESSED, Path.of("Finance/StockMarket"));
        setLogger((msg)->{BACKEND_INSTANCES.LOGGER.error(msg);},
                (msg,e)->{BACKEND_INSTANCES.LOGGER.error(msg, e);},
                (msg)->{BACKEND_INSTANCES.LOGGER.info(msg);},
                (msg)->{BACKEND_INSTANCES.LOGGER.warn(msg);});
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
        BACKEND_INSTANCES.LOGGER.info("Saving StockMarket Mod data...");
        boolean success = true;
        success &= save_globalSettings();
        success &= save_player();
        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER != null)
            success &= save_market();

        if(success)
            BACKEND_INSTANCES.LOGGER.info("StockMarket Mod data saved successfully.");
        else
            BACKEND_INSTANCES.LOGGER.error("Failed to save StockMarket Mod data.");
        return success;
    }
    public CompletableFuture<Boolean> saveAllAsync()
    {
        BACKEND_INSTANCES.LOGGER.info("Saving StockMarket Mod data...");

        CompletableFuture<Boolean> fut1 = save_playerAsync();
        CompletableFuture<Boolean> fut2;
        CompletableFuture<Boolean> fut3 = save_globalSettingsAsync();
        if(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER != null)
            fut2 = save_marketAsync();
        else
            fut2 = CompletableFuture.completedFuture(false);
        return fut1.thenCombine(fut2, (a, b) -> a && b).thenCombine(fut3, (a, b) -> a && b).thenApply(success -> {
            if(success)
                BACKEND_INSTANCES.LOGGER.info("StockMarket Mod data saved successfully.");
            else
                BACKEND_INSTANCES.LOGGER.error("Failed to save StockMarket Mod data.");
            return success;
        });
    }

    public boolean loadAll()
    {
        isLoaded = false;
        BACKEND_INSTANCES.LOGGER.info("Loading StockMarket Mod data...");
        boolean success = true;
        Path settingsFilePath = getGlobalSettingsFilePath();
        if(!fileExists(settingsFilePath)) {
            BACKEND_INSTANCES.LOGGER.warn("Market settings file not found, creating default settings file.");
            success &= save_globalSettings(settingsFilePath);
        }
        else
            success &= load_globalSettings(settingsFilePath);
        success &= load_player();
        success &= load_market();

        if(success) {
            BACKEND_INSTANCES.LOGGER.info("StockMarket Mod data loaded successfully.");
            isLoaded = true;
        }
        else
            BACKEND_INSTANCES.LOGGER.error("Failed to load StockMarket Mod data.");
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
    public Path getDefaultBotSettingsFolderPath()
    {
        return getAbsoluteSavePath(DEFAULT_BOT_SETTINGS_DIRECTORY);
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

    public List<String> getDefaultBotSettingsFileNames()
    {
        List<Path> jsonFiles = getJsonFiles(getDefaultBotSettingsFolderPath());
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
    public HashMap<String, HashMap<ItemID, ServerTradingBotFactory.DefaultBotSettings>> loadDefaultBotSettings()
    {
        if(!folderExists(getDefaultBotSettingsFolderPath())) {
            BACKEND_INSTANCES.LOGGER.error("Default bot settings folder does not exist: " + getDefaultBotSettingsFolderPath());
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
        Path filePath = getDefaultBotSettingsFolderPath().resolve(fileName);
        if(!fileExists(filePath)) {
            BACKEND_INSTANCES.LOGGER.error("Default bot settings file does not exist: " + fileName);
            return settings;
        }
        JsonElement jsonElement = loadJson(filePath);
        if(jsonElement == null || !jsonElement.isJsonArray()) {
            BACKEND_INSTANCES.LOGGER.error("Failed to load default bot settings from file: " + fileName);
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
                    BACKEND_INSTANCES.LOGGER.error("Invalid BotBuilderContainer in file: " + fileName);
                }
            } else {
                BACKEND_INSTANCES.LOGGER.error("Invalid JSON element in file: " + fileName);
            }
        }
        return settings;
    }

    public boolean saveDefaultBotSettings(ArrayList<ServerTradingBotFactory.BotBuilderContainer> settings, String fileName)
    {
        JsonArray jsonArray = new JsonArray();
        for(ServerTradingBotFactory.BotBuilderContainer container : settings) {
            jsonArray.add(container.toJson());
        }
        return saveJson(jsonArray, getDefaultBotSettingsFolderPath().resolve(fileName));
    }




}
