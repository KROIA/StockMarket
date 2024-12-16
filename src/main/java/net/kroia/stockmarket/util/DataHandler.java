package net.kroia.stockmarket.util;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DataHandler {
    private static final String FOLDER_NAME = "stockmarket";

    private static final String PLAYER_DATA_FILE_NAME = "Player_data.dat";
    private static final String MARKET_DATA_FILE_NAME = "Market_data.dat";
    private static final String BANK_DATA_FILE_NAME = "Bank_data.dat";
    private static final boolean COMPRESSED = false;
    private static File saveFolder;

    private static ScheduledExecutorService saveScheduler;


    public DataHandler()
    {

    }

    public static void startTimer()
    {
        saveScheduler = Executors.newSingleThreadScheduledExecutor();
        saveScheduler.scheduleAtFixedRate(DataHandler::saveAll, 1, 1, java.util.concurrent.TimeUnit.MINUTES);
    }
    public static void stopTimer()
    {
        saveScheduler.shutdown();
    }


    public static void setSaveFolder(File folder) {
        File rootFolder = new File(folder, FOLDER_NAME);
        // check if folder exists
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
        saveFolder = rootFolder;
    }
    public static File getSaveFolder() {
        return saveFolder;
    }

    public static boolean saveAll()
    {
        StockMarketMod.LOGGER.info("Saving StockMarket Mod data...");
        boolean success = true;
        success &= save_player();
        success &= save_bank();
        success &= save_market();

        if(success)
            StockMarketMod.LOGGER.info("StockMarket Mod data saved successfully.");
        else
            StockMarketMod.LOGGER.error("Failed to save StockMarket Mod data.");
        return success;
    }

    public static boolean loadAll()
    {
        StockMarketMod.LOGGER.info("Loading StockMarket Mod data...");
        boolean success = true;
        success &= load_player();
        success &= load_bank();
        success &= load_market();

        if(success)
            StockMarketMod.LOGGER.info("StockMarket Mod data loaded successfully.");
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
        data.put("market", marketData);
        saveDataCompound(MARKET_DATA_FILE_NAME, data);
        return success;
    }
    public static boolean load_market()
    {
        CompoundTag data = readDataCompound(MARKET_DATA_FILE_NAME);
        if(data == null)
            return false;
            // Load server_sender market
        ServerMarket market = new ServerMarket();
        if(!data.contains("market"))
            return false;
        CompoundTag marketData = data.getCompound("market");
        return market.load(marketData);
    }

    public static boolean save_bank()
    {
        boolean success = true;
        CompoundTag data = new CompoundTag();
        CompoundTag bankData = new CompoundTag();
        success = ServerBankManager.saveToTag(bankData);
        data.put("banking", bankData);
        saveDataCompound(BANK_DATA_FILE_NAME, data);
        return success;
    }

    public static boolean load_bank()
    {
        CompoundTag data = readDataCompound(BANK_DATA_FILE_NAME);
        if(data == null)
            return false;
        if(!data.contains("banking"))
            return false;

        CompoundTag bankData = data.getCompound("banking");
        return ServerBankManager.loadFromTag(bankData);
    }


    private static CompoundTag readDataCompound(String fileName)
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
                StockMarketMod.LOGGER.error("Failed to read data from file: " + fileName);
                e.printStackTrace();
            } catch(Exception e)
            {
                StockMarketMod.LOGGER.error("Failed to read data from file: " + fileName);
                e.printStackTrace();
            }
        }
        return null;
    }
    public static boolean saveDataCompound(String fileName, CompoundTag data) {
        File file = new File(saveFolder, fileName);
        try {
            if (COMPRESSED)
                NbtIo.writeCompressed(data, file);
            else
                NbtIo.write(data, file);
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("Failed to save data to file: " + fileName);
            e.printStackTrace();
            return false;
        } catch(Exception e)
        {
            StockMarketMod.LOGGER.error("Failed to save data to file: " + fileName);
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
