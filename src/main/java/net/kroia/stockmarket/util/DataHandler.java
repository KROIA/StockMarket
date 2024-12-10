package net.kroia.stockmarket.util;

import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;

public class DataHandler {
    private static final String FOLDER_NAME = "stockmarket";

    private static final String MARKET_DATA_FILE_NAME = "Market_data.dat";
    private static final String BANK_DATA_FILE_NAME = "Bank_data.dat";
    private static final boolean COMPRESSED = false;
    private static File saveFolder;

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

    public static void saveAll()
    {
        save_bank();
        save_market();
    }

    public static void loadAll()
    {
        load_bank();
        load_market();
    }


    public static void save_market()
    {
        CompoundTag data = new CompoundTag();
        ServerMarket market = new ServerMarket();
        CompoundTag marketData = new CompoundTag();
        market.save(marketData);
        data.put("market", marketData);
        saveDataCompound(MARKET_DATA_FILE_NAME, data);
    }
    public static void load_market()
    {
        CompoundTag data = readDataCompound(MARKET_DATA_FILE_NAME);
        if(data != null)
        {
            // Load server market
            ServerMarket market = new ServerMarket();
            CompoundTag marketData = data.getCompound("market");
            market.load(marketData);
        }
    }

    public static void save_bank()
    {
        CompoundTag data = new CompoundTag();
        CompoundTag bankData = new CompoundTag();
        ServerBankManager.saveToTag(bankData);
        data.put("banking", bankData);
        saveDataCompound(BANK_DATA_FILE_NAME, data);
    }

    public static void load_bank()
    {
        CompoundTag data = readDataCompound(BANK_DATA_FILE_NAME);
        if(data != null)
        {
            CompoundTag bankData = data.getCompound("banking");
            ServerBankManager.loadFromTag(bankData);
        }
    }


    private static CompoundTag readDataCompound(String fileName)
    {
        CompoundTag dataOut = new CompoundTag();
        File file = new File(saveFolder, fileName);
        if (file.exists()) {
            try {
                CompoundTag data = new CompoundTag();

                if(COMPRESSED)
                    data = NbtIo.readCompressed(file);
                else
                    data = NbtIo.read(file);

                dataOut = data;
                return dataOut;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    public static void saveDataCompound(String fileName, CompoundTag data) {
        File file = new File(saveFolder, fileName);
        try {
            if (COMPRESSED)
                NbtIo.writeCompressed(data, file);
            else
                NbtIo.write(data, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
