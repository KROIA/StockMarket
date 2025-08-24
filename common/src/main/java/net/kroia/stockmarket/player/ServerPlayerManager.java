package net.kroia.stockmarket.player;

import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.networking.packet.server_sender.ClientServerManagerMetaDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ServerPlayerManager {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        ServerPlayerData.setBackend(backend);
    }



    private final Path saveFolder;

    private final Map<UUID, ServerPlayerData> playerDataMap = new HashMap<>();
    public ServerPlayerManager(Path saveFolder) {
        this.saveFolder = saveFolder;
    }


    public void onPlayerJoin(ServerPlayer player)
    {
        if(player == null)
            return;
        // This will create a new ServerPlayerData instance if it does not exist
        ServerPlayerData playerData = getPlayerData(player);
        ClientServerManagerMetaDataPacket.sendToClinet(player);
    }
    public void onPlayerLeave(ServerPlayer player)
    {
        if(player == null)
            return;
        ServerPlayerData playerData = getPlayerData(player);
        playerData.save();
    }


    // Player utilities
    public @NotNull String getPlayerName(UUID playerUUID) {
        if(playerUUID == null)
            return "";
        ServerPlayerData playerData = playerDataMap.get(playerUUID);
        if(playerData == null)
            return "";
        return playerData.getPlayerName();
    }
    public UUID getPlayerUUID(String playerName) {
        if(playerName == null || playerName.isEmpty())
            return null;
        for(Map.Entry<UUID, ServerPlayerData> entry : playerDataMap.entrySet()) {
            ServerPlayerData playerData = entry.getValue();
            if(playerData.getPlayerName().equalsIgnoreCase(playerName)) {
                return playerData.getPlayerUUID();
            }
        }
        return null;
    }
    public boolean playerExists(UUID playerUUID) {
        if(playerUUID == null)
            return false;
        return playerDataMap.containsKey(playerUUID);
    }
    public boolean playerExists(String playerName) {
        if(playerName == null || playerName.isEmpty())
            return false;
        for(Map.Entry<UUID, ServerPlayerData> entry : playerDataMap.entrySet()) {
            ServerPlayerData playerData = entry.getValue();
            if(playerData.getPlayerName().equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }
    public ServerPlayer getServerPlayer(UUID playerUUID) {
        if(playerUUID == null)
            return null;

        // Get the Minecraft server_sender instance
        MinecraftServer server = UtilitiesPlatform.getServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayer(playerUUID); // Returns null if the player is not online
    }
    public static ServerPlayer getPlayer(String name)
    {
        if(name == null)
            return null;
        // Get the Minecraft server_sender instance
        MinecraftServer server = UtilitiesPlatform.getServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayerByName(name); // Returns null if the player is not online
    }



    // Market data flow
    public void logNewOrderToHistory(TradingPair pair, Order order) {
        if(pair == null || order == null){
            return;
        }
        UUID playerUUID = order.getPlayerUUID();
        if(playerUUID == null)
            return;
        ServerPlayerData playerData = getPlayerData(playerUUID);
        playerData.putOrder(pair, order);
    }

    public List<OrderDataRecord> retrieveOrderData(UUID player, int offset, int amount){
        ServerPlayerData playerData = getPlayerData(player);
        return playerData.getOrderHistory(offset, amount);
    }








    private ServerPlayerData getPlayerData(UUID playerUUID) {
        return playerDataMap.computeIfAbsent(playerUUID, uuid -> new ServerPlayerData(saveFolder, uuid));
    }
    private ServerPlayerData getPlayerData(ServerPlayer player) {
        if(player == null)
            return null;
        UUID uuid = player.getUUID();
        ServerPlayerData playerData = playerDataMap.get(uuid);
        if(playerData == null) {
            playerData = new ServerPlayerData(saveFolder, player);
            playerDataMap.put(uuid, playerData);
        }
        else {
            // Since the name can change update it when the player joins
            playerData.updatePlayerName(player);
        }
        return playerData;
    }


    public boolean save()
    {
        boolean success = true;
        debug("Saving player data...");
        if (!saveFolder.toFile().exists()) {
            saveFolder.toFile().mkdirs();
            if(!saveFolder.toFile().exists()) {
                error("Can't create save folder: " + saveFolder);
                success = false;
            }
        }
        if(success) {
            for (Map.Entry<UUID, ServerPlayerData> entry : playerDataMap.entrySet()) {
                ServerPlayerData playerData = entry.getValue();
                success &= playerData.save();
            }
        }
        debug("Saving player data ["+(success ? "OK" : "FAILED")+"]");
        return success;
    }

    public boolean load()
    {
        boolean success = true;
        debug("Loading player data...");
        if (!saveFolder.toFile().exists()) {
            error("Save folder does not exist: " + saveFolder);
            success = false;
        }

        if(success) {
            // Get the list of player folders
            List<Path> subFolders;
            try {
                subFolders = java.nio.file.Files.list(saveFolder)
                        .filter(java.nio.file.Files::isDirectory)
                        .toList();
            } catch (java.io.IOException e) {
                error("Failed to list player data folders", e);
                return false;
            }
            for (Path subFolder : subFolders) {
                // Create a new ServerPlayerData instance from the sub-folder
                ServerPlayerData playerData = ServerPlayerData.createFromSave(subFolder);
                if (playerData == null) {
                    error("Failed to create player data from folder: " + subFolder);
                    success = false;
                    continue;
                }
                // Add the player data to the map
                UUID playerUUID = playerData.getPlayerUUID();
                if (playerDataMap.containsKey(playerUUID)) {
                    warn("Player data for UUID " + playerUUID + " already exists. Overwriting.");
                }
                playerDataMap.put(playerUUID, playerData);
            }
        }



        debug("Loading player data ["+(success ? "OK" : "FAILED")+"]");
        return success;
    }










    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ServerPlayerManager] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlayerManager] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerPlayerManager] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ServerPlayerManager] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ServerPlayerManager] " + msg);
    }
}
