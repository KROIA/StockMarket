package net.kroia.stockmarket.util;

import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;

public class DataHandler {
    private static final String FILE_NAME = "StockMarket_data.dat";
    private static File saveFolder;
   // private CompoundTag data = new CompoundTag();


    public static void setSaveFolder(File folder) {
        saveFolder = folder;
    }
    public static File getSaveFolder() {
        return saveFolder;
    }

    public static void saveToFile(File saveFolder)
    {
        setSaveFolder(saveFolder);
        saveToFile();
    }
    public static void saveToFile()
    {
        File file = new File(saveFolder, FILE_NAME);
        try {
            CompoundTag data = new CompoundTag();
            // Save server market
            ServerMarket market = new ServerMarket();
            CompoundTag marketData = new CompoundTag();
            market.save(marketData);
            data.put("market", marketData);


            NbtIo.writeCompressed(data, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadFromFile(File saveFolder)
    {
        setSaveFolder(saveFolder);
        loadFromFile();
    }
    public static void loadFromFile()
    {
        File file = new File(saveFolder, FILE_NAME);
        if (file.exists()) {
            try {
                CompoundTag data = new CompoundTag();
                data = NbtIo.readCompressed(file);

                // Load server market
                ServerMarket market = new ServerMarket();
                CompoundTag marketData = data.getCompound("market");
                market.load(marketData);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
