package net.kroia.stockmarket.player;

import net.kroia.modutilities.persistence.NBTFileParser;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.market.server.order.OrderHistory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class ServerPlayerData {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    private static final NBTFileParser nbtFileParser = new NBTFileParser(NBTFileParser.NbtFormat.COMPRESSED);
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        nbtFileParser.setLogger(backend.LOGGER::error, backend.LOGGER::error, backend.LOGGER::debug, backend.LOGGER::warn);
    }

    private static class PlayerMiscData implements ServerSaveable
    {
        public static final class KEY
        {
            public static final String NAME = "n";
            public static final String UUID = "u";
        }
        public String name = "";
        public UUID uuid = null;

        public PlayerMiscData(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
        public PlayerMiscData(ServerPlayer player) {
            this.uuid = player.getUUID();
            this.name = player.getName().getString();
        }
        public PlayerMiscData()
        {
            this("", null);
        }

        @Override
        public boolean save(CompoundTag tag) {
            if(tag == null)
                return false;
            tag.putString(KEY.NAME, name);
            if(uuid != null)
                tag.putUUID(KEY.UUID, uuid);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            if(tag.contains(KEY.NAME)) {
                name = tag.getString(KEY.NAME);
            }
            if(tag.contains(KEY.UUID)) {
                uuid = tag.getUUID(KEY.UUID);
            } else {
                uuid = null; // If no UUID is present, set it to null
            }
            return true;
        }
    }

    /**
     * Absolute path to the players data folder. (e.g. "dir/bla/bla/<playerUUID>").
     */
    private final Path saveFolder;
    private final OrderHistory orderHistory;
    private final PlayerMiscData playerMiscData;

    /**
     * Defines which elements get stored/loaded from the OrderDataRecord.
     */
    private static final byte orderDataRecordUseFlags =
            OrderDataRecord.USE_AMOUNT |
            // OrderDataRecord.USE_PLAYER |
            OrderDataRecord.USE_TYPE |
            OrderDataRecord.USE_STATUS |
            OrderDataRecord.USE_TIMESTAMP;



    /**
     * Constructor for ServerPlayerData.
     * @param saveFolder The folder to where this class will create a sub-folder using the UUID.
     *                   If saveFolder=="dir/bla/bla", then the sub-folder will be "dir/bla/bla/<playerUUID>".
     *                   The players data will be stored in that sub-folder.
     * @param playerUUID The UUID of the player. This will be used to create a sub-folder in the saveFolder.
     */
    public ServerPlayerData(Path saveFolder, UUID playerUUID) {
        String folderName = playerUUID.toString().replace("-", "_");
        this.saveFolder = saveFolder.resolve(folderName);
        playerMiscData = new PlayerMiscData("", playerUUID);

        orderHistory = new OrderHistory(this.saveFolder.resolve("OrderHistory"));
        orderHistory.setOrderDataRecordUseFlags(orderDataRecordUseFlags);
    }
    public ServerPlayerData(Path saveFolder, ServerPlayer player) {
        playerMiscData = new PlayerMiscData(player);
        String folderName = playerMiscData.uuid.toString().replace("-", "_");
        this.saveFolder = saveFolder.resolve(folderName);


        orderHistory = new OrderHistory(this.saveFolder.resolve("OrderHistory"));
        orderHistory.setOrderDataRecordUseFlags(orderDataRecordUseFlags);
    }


    // Needs to load the data after using this constructor
    private ServerPlayerData(Path saveFolder)
    {
        this.saveFolder = saveFolder.toAbsolutePath();
        orderHistory = new OrderHistory(this.saveFolder.resolve("OrderHistory"));
        orderHistory.setOrderDataRecordUseFlags(orderDataRecordUseFlags);
        String folderName = saveFolder.getFileName().toString();
        UUID playerUUID;
        try {
            playerUUID = UUID.fromString(folderName.replace("_", "-"));
        } catch (IllegalArgumentException e) {
            error("Failed to parse UUID from folder name: " + folderName, e);
            playerUUID = null;
        }
        this.playerMiscData = new PlayerMiscData("", playerUUID);
    }


    /**
     * Creates a new ServerPlayerData instance from a save folder.
     * @param saveFolder The folder, containing the stringifies UUID in its path name "dir/bla/bla/<playerUUID>"
     * @return A new ServerPlayerData instance if success
     */
    public static @Nullable ServerPlayerData createFromSave(Path saveFolder)
    {
        if (saveFolder == null || !saveFolder.toFile().exists()) {
            throw new IllegalArgumentException("Save folder does not exist: " + saveFolder);
        }
        ServerPlayerData playerData = new ServerPlayerData(saveFolder);
        if(playerData.playerMiscData.uuid == null)
        {
            return null;
        }
        if(playerData.load())
            return playerData;
        else
            return null;
    }



    public UUID getPlayerUUID() {
        return playerMiscData.uuid;
    }
    public Path getSaveFolder() {
        return saveFolder;
    }

    // Do not provide access to the order history directly, because orders do not save the player UUID.
    // When new orders must be loaded, the player UUID for each order record must be set manually.
    /*public OrderHistory getOrderHistory() {
        return orderHistory;
    }*/
    public String getPlayerName() {
        return playerMiscData.name;
    }
    public void updatePlayerName(ServerPlayer player) {
        if(player.getUUID().compareTo(this.playerMiscData.uuid) != 0) {
            error("Player UUID does not match: " + player.getUUID() + " != " + this.playerMiscData.uuid + " You try to update the player \""+
                    this.playerMiscData.name+"\" using the provided player: \""+player.getName().getString()+"\"");
            return;
        }
        this.playerMiscData.name = player.getName().getString();
    }



    public void putOrder(TradingPair pair, Order order){
        orderHistory.putOrder(pair, order);
    }

    public List<OrderDataRecord> getOrderHistory(int offset, int amount){
        return orderHistory.getChronological(offset, amount);
    }


    public boolean save()
    {
        boolean success = true;
        if (!saveFolder.toFile().exists()) {
            saveFolder.toFile().mkdirs();
        }
        debug("Saving player data for player: " + playerMiscData.name);
        if (!saveFolder.toFile().exists()) {
            error("Can't create save folder: " + saveFolder);
            success = false;
        }


        if(success) {
            CompoundTag miscData = new CompoundTag();
            if(!playerMiscData.save(miscData)) {
                error("Failed to save player misc data for player: " + playerMiscData.name);
                success = false;
            }
            else
                success &= nbtFileParser.saveDataCompound(saveFolder.resolve("PlayerMiscData.nbt"), miscData);
        }

        if(success)
            success &= orderHistory.save();
        debug("Saving player data for player: "+playerMiscData.name+" ["+(success ? "OK" : "FAILED")+"]");
        return success;
    }
    public boolean load()
    {
        boolean success = true;
        debug("Loading player data for player: " + playerMiscData.name);
        if (!saveFolder.toFile().exists()) {
            error("Save folder does not exist: " + saveFolder);
            success = false;
        }
        if(success) {
            if(orderHistory.load())
            {
                // Since the player uuid is not stored, we put it back
                orderHistory.setPlayerUUIDForAllOrders(playerMiscData.uuid);
            }
            else
            {
                error("Failed to load order history for player: " + playerMiscData.name);
                success = false;
            }
        }
        debug("Loading player data for player: "+playerMiscData.name+" ["+(success ? "OK" : "FAILED")+"]");
        return success;
    }







    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ServerPlayerData: "+ playerMiscData.name + "] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlayerData: "+ playerMiscData.name + "] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlayerData: "+ playerMiscData.name + "] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ServerPlayerData: "+ playerMiscData.name + "] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ServerPlayerData: "+ playerMiscData.name + "] " + msg);
    }
}
