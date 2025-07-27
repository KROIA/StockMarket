package net.kroia.stockmarket.util;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.ServerStockMarketManager;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


public class StockMarketDataHandler {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    private final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final String FOLDER_NAME = "Finance/StockMarket";

    private final String PLAYER_DATA_FILE_NAME = "Player_data.dat";
    private final String MARKET_DATA_FILE_NAME = "Market_data.dat";
    private final boolean COMPRESSED = false;
    private File saveFolder;

    private boolean isLoaded = false;

    private long tickCounter = 0;
    private int lastPlayerCount = 0;

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



    public void setSaveFolder(File folder) {
        File rootFolder = new File(folder, FOLDER_NAME);
        // check if folder exists
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
        saveFolder = rootFolder;
    }
    public File getSaveFolder() {
        return saveFolder;
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
        success &= load_globalSettings();
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
        return saveDataCompound(PLAYER_DATA_FILE_NAME, data);
    }
    public CompletableFuture<Boolean> save_playerAsync()
    {
        CompoundTag data = new CompoundTag();
        ServerPlayerList.saveToTag(data);
        // Save player data async because it can take much time when there are many players
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            return saveDataCompound(PLAYER_DATA_FILE_NAME, data);
        });
        return future;
    }
    public boolean load_player()
    {
        CompoundTag data = readDataCompound(PLAYER_DATA_FILE_NAME);
        if(data != null)
            return ServerPlayerList.loadFromTag(data);
        return false;
    }

    public boolean save_market()
    {
        boolean success = true;
        CompoundTag data = new CompoundTag();
        ServerStockMarketManager market = new ServerStockMarketManager();
        CompoundTag marketData = new CompoundTag();
        success = market.save(marketData);
        data.put("market", marketData);

        if(success)
            success = saveDataCompound(MARKET_DATA_FILE_NAME, data);
        return success;
    }
    public CompletableFuture<Boolean> save_marketAsync()
    {
        CompoundTag data = new CompoundTag();
        ServerStockMarketManager market = new ServerStockMarketManager();
        CompoundTag marketData = new CompoundTag();

        CompletableFuture<Boolean> future;
        if(market.save(marketData)) {
            data.put("market", marketData);

            // Save market data async because it can take much time when there are many trading items
            future = CompletableFuture.supplyAsync(() -> {
                return saveDataCompound(MARKET_DATA_FILE_NAME, data);
            });
        }
        else
            future = CompletableFuture.completedFuture(false);
        return future;
    }
    public boolean load_market()
    {
        CompoundTag data = readDataCompound(MARKET_DATA_FILE_NAME);
        if(data == null)
            return false;
        // Load server_sender market
        ServerStockMarketManager market = new ServerStockMarketManager();
        if(!data.contains("market"))
            return false;
        CompoundTag marketData = data.getCompound("market");
        return market.load(marketData);
    }


    public boolean save_globalSettings()
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.saveSettings();
    }

    public CompletableFuture<Boolean> save_globalSettingsAsync()
    {
        return CompletableFuture.supplyAsync(StockMarketDataHandler::save_globalSettingsAsyncStatic);
    }

    // Helper function to save global settings asynchronously
    private static boolean save_globalSettingsAsyncStatic()
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.saveSettings();
    }


    public boolean load_globalSettings()
    {
        return BACKEND_INSTANCES.SERVER_SETTINGS.loadSettings();
    }

    public List<String> getDefaultBotSettingsFileNames()
    {
        List<Path> jsonFiles = getJsonFiles(Path.of(getSaveFolder().getPath()+"/DefaultBotSettings"));
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
        Type arrayType = new TypeToken<ArrayList<ServerTradingBotFactory.BotBuilderContainer>>() {}.getType();
        try {
            ArrayList<ServerTradingBotFactory.BotBuilderContainer> tmpArray = loadFromJson("/DefaultBotSettings/"+fileName, arrayType);
            if(tmpArray != null) {
                for(ServerTradingBotFactory.BotBuilderContainer container : tmpArray)
                    settings.put(container.itemData.getItemID(), container.defaultSettings);
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
        return settings;
    }
    public boolean saveDefaultBotSettings(Map<ServerTradingBotFactory.ItemData, ServerTradingBotFactory.DefaultBotSettings> settingsMap, String fileName)
    {
        return saveAsJson(settingsMap, "DefaultBotSettings/"+fileName);
    }
    public boolean saveDefaultBotSettings(ArrayList<ServerTradingBotFactory.BotBuilderContainer> settings, String fileName)
    {
        return saveAsJson(settings, "DefaultBotSettings/"+fileName);
    }

    public boolean fileExists(String fileName) {
        File file = new File(getSaveFolder(), fileName);
        return file.exists();
    }

    public List<Path> getJsonFiles(Path path) {
        try {
            return Files.list(path) // List files
                    .filter(Files::isRegularFile)       // Keep only regular files
                    .filter(_path -> _path.toString().endsWith(".json")) // Keep only .json files
                    .collect(Collectors.toList()); // Convert to List
        } catch (IOException e) {
            //e.printStackTrace();
            return List.of(); // Return empty list on error
        }
    }



    private CompoundTag readDataCompound(String fileName)
    {
        CompoundTag dataOut = new CompoundTag();
        File file = new File(saveFolder, fileName);
        if (file.exists()) {
            try {
                CompoundTag data;

                if(COMPRESSED)
                    data = NbtIo.readCompressed(file);
                else
                    data = NbtIo.read(file);

                dataOut = data;
                return dataOut;
            } catch (IOException e) {
                BACKEND_INSTANCES.LOGGER.error("Failed to read data from file: " + fileName, e);
            } catch(Exception e)
            {
                BACKEND_INSTANCES.LOGGER.error("Failed to read data from file: " + fileName, e);
            }
        }
        return null;
    }
    public boolean saveDataCompound(String fileName, CompoundTag data) {
        long startMillis = System.currentTimeMillis();
        boolean success = true;
        File file = new File(saveFolder, fileName);
        try {
            if (COMPRESSED)
                NbtIo.writeCompressed(data, file);
            else
                NbtIo.write(data, file);
        } catch (IOException e) {
            BACKEND_INSTANCES.LOGGER.error("Failed to save data to file: " + fileName, e);
            success = false;
        } catch(Exception e)
        {
            BACKEND_INSTANCES.LOGGER.error("Failed to save data to file: " + fileName, e);
            success = false;
        }
        long endMillis = System.currentTimeMillis();
        BACKEND_INSTANCES.LOGGER.info("Saving data to file: " + fileName + " took " + (endMillis - startMillis) + "ms");
        return success;
    }

    public boolean saveAsJson(Object o, String fileName)
    {
        String json = GSON.toJson(o);
        try {
            Path path = Paths.get(getSaveFolder()+"/"+fileName);
            Files.createDirectories(path.getParent());
            Files.writeString(Paths.get(getSaveFolder()+"/"+fileName), json);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public <T> T loadFromJson(String fileName, Type typeOfT) throws JsonSyntaxException {
        try {
            // Read JSON content
            String json = Files.readString(Paths.get(getSaveFolder()+"/"+fileName));
            return (T) GSON.fromJson(json, TypeToken.get(typeOfT));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
