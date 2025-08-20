package net.kroia.stockmarket.util;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class StockMarketDataHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER_NAME = "Finance/StockMarket";

    private static final String PLAYER_DATA_FILE_NAME = "Player_data.dat";
    private static final String MARKET_DATA_FILE_NAME = "Market_data";
    private static final boolean COMPRESSED = false;
    private static File saveFolder;

    private static boolean isLoaded = false;

    private static long tickCounter = 0;
    public static long saveTickInterval = 6000; // 5 minutes

    public static void tickUpdate()
    {
        tickCounter++;
        if(tickCounter >= saveTickInterval)
        {
            tickCounter = 0;
            saveAll();
        }
    }


    public static void setSaveFolder(File folder){
        setSaveFolder(folder, FOLDER_NAME);

    }
    public static void setSaveFolder(File folder, String folderName) {
        File rootFolder = new File(folder, folderName);
        // check if folder exists
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
        saveFolder = rootFolder;
    }
    public static File getSaveFolder() {
        return saveFolder;
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static boolean saveAll()
    {
        StockMarketMod.LOGGER.info("Saving StockMarket Mod data...");
        boolean success = true;
        success &= save_player();
        if(ServerMarket.isInitialized())
            success &= save_market();

        if(success)
            StockMarketMod.LOGGER.info("StockMarket Mod data saved successfully.");
        else
            StockMarketMod.LOGGER.error("Failed to save StockMarket Mod data.");
        return success;
    }

    public static boolean loadAll()
    {
        isLoaded = false;
        StockMarketMod.LOGGER.info("Loading StockMarket Mod data...");
        boolean success = true;
        success &= load_player();
        success &= load_market();

        if(success) {
            StockMarketMod.LOGGER.info("StockMarket Mod data loaded successfully.");
            isLoaded = true;
        }
        else
            StockMarketMod.LOGGER.error("Failed to load StockMarket Mod data.");
        return success;
    }

    public static boolean save_player()
    {
        CompoundTag data = new CompoundTag();
        ServerPlayerList.saveToTag(data);
        return saveDataCompound(PLAYER_DATA_FILE_NAME, data);
    }
    public static boolean load_player()
    {
        CompoundTag data = readDataCompound(PLAYER_DATA_FILE_NAME);
        if(data != null)
            return ServerPlayerList.loadFromTag(data);
        return false;
    }

    public static boolean save_market()
    {
        boolean success = true;
        CompoundTag data = new CompoundTag();
        ServerMarket market = new ServerMarket();
        CompoundTag marketData = new CompoundTag();
        success = market.save(marketData);
        if(!success){
            return false;
        }
        //Save market data shards
        setSaveFolder(saveFolder, "shards");
        clearDirectory(saveFolder.toString());
        ListTag shards = (ListTag) marketData.get("tradeItems");
        for(int i = 0; i < shards.size(); ++i){
            CompoundTag tag = new CompoundTag();
            tag.put("shard", shards.getList(i));
            if(i == 0){
                tag.putLong("shiftPriceHistoryInterval", marketData.getLong("shiftPriceHistoryInterval"));
            }
            saveDataCompound(MARKET_DATA_FILE_NAME + "_shard_" + i + ".dat", tag);
        }
        return success;
    }
    public static void clearDirectory(String directoryPath){
        Path dir = Paths.get(directoryPath);
        try {
            if (Files.exists(dir)) {
                Stream<Path> paths = Files.walk(dir)
                        .filter(path -> !path.equals(dir));
                for (Path f : paths.toList()){
                    Files.delete(f);
                }

            }
        }
        catch(IOException e){//This likely only happens if the directories don't exist, which SHOULD NOT happen.
            return;
        }
    }
    public static boolean load_market() {
        setSaveFolder(saveFolder, "shards");
        try (Stream<Path> paths = Files.walk(saveFolder.toPath())) {
            Stream<Path> files = paths.filter(Files::isRegularFile);
            for(Path f : files.toList()) {
                StockMarketMod.LOGGER.warn(f.toString());
                CompoundTag data = readDataCompound(f.toString());
                if (data == null)
                    continue;
                // Load server_sender market
                ServerMarket market = new ServerMarket();
                if (!data.contains("shard"))
                    return false;
                market.load(data);
            }
        }
        catch(IOException e){
            StockMarketMod.LOGGER.error("Failed to load Market Data.");
            return false;
        }
        return true;

    }

    public static Map<String, ServerTradingBotFactory.DefaultBotSettings> loadDefaultBotSettings()
    {
        List<Path> jsonFiles = getJsonFiles(Path.of(getSaveFolder().getPath()+"/DefaultBotSettings"));
        Map<String, ServerTradingBotFactory.DefaultBotSettings> settings = new HashMap<>();
        Type mapType = new TypeToken<Map<String, ServerTradingBotFactory.DefaultBotSettings>>() {}.getType();
        for(Path file : jsonFiles)
        {
            try {
                Map<String, ServerTradingBotFactory.DefaultBotSettings> tmpMap = loadFromJson("/DefaultBotSettings/"+file.getFileName().toString(), mapType);
                if(tmpMap != null)
                    settings.putAll(tmpMap);
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
            }
        }
        return settings;
    }
    public static boolean saveDefaultBotSettings(Map<String, ServerTradingBotFactory.DefaultBotSettings> settingsMap, String fileName)
    {
        return saveAsJson(settingsMap, "DefaultBotSettings/"+fileName);
    }

    public static List<Path> getJsonFiles(Path path) {
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



    private static CompoundTag readDataCompound(String fileName)
    {
        CompoundTag dataOut = new CompoundTag();
        File file = new File(fileName);
        if (file.exists()) {
            try {
                CompoundTag data;

                // Define a reasonable quota and depth for NBT reading
                long quota = 2097152L; // 2 MB size limit
                int maxDepth = 512; // Maximum allowed depth for NBT structures
                NbtAccounter accounter = new NbtAccounter(quota, maxDepth);
                if(COMPRESSED)
                    data = NbtIo.readCompressed(new FileInputStream(file), accounter);
                else {
                    DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
                    data = NbtIo.read(dataInputStream, accounter);
                }

                dataOut = data;
                return dataOut;
            } catch (IOException e) {
                BankSystemMod.LOGGER.error("Failed to read data from file: " + fileName);
                e.printStackTrace();
            } catch(Exception e)
            {
                BankSystemMod.LOGGER.error("Failed to read data from file: " + fileName);
                e.printStackTrace();
            }
        }
        return null;
    }
    public static boolean saveDataCompound(String fileName, CompoundTag data) {
        File file = new File(saveFolder, fileName);
        try {
            if (COMPRESSED)
                NbtIo.writeCompressed(data, file.toPath());
            else
                NbtIo.write(data, file.toPath());
        } catch (IOException e) {
            BankSystemMod.LOGGER.error("Failed to save data to file: " + fileName);
            e.printStackTrace();
            return false;
        } catch(Exception e)
        {
            BankSystemMod.LOGGER.error("Failed to save data to file: " + fileName);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean saveAsJson(Object o, String fileName)
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
    public static <T> T loadFromJson(String fileName, Type typeOfT) throws JsonSyntaxException {
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
