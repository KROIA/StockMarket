package net.kroia.stockmarket.util;


import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.banking.bankmanager.BankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.client_server.arrs.GenericRequest;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class StockMarketGenericRequest<IN, OUT> extends GenericRequest<IN, OUT> {
    protected static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected boolean playerIsAdmin(ServerPlayer player)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(player.getUUID());
    }
    protected boolean playerIsAdmin(UUID playerUUID)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(playerUUID);
    }

    /**
     * Only call this function on the master server!
     */
    protected IServerMarketManager getServerMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync();
    }

    /**
     * Only call this function on the master server!
     */
    protected final IServerPluginManager getPluginManager() { return BACKEND_INSTANCES.PLUGIN_MANAGER.getSync(); }
    protected IBankManager getServerBankManager() {return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager(); }

    /**
     * Only call this function on the master server!
     */
    protected int getItemFractionScaleFactor()
    {
        IServerBankManager manager = getServerBankManager().getSync();
        if(manager == null)
            return BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
        return manager.getItemFractionScaleFactor();
    }

    /**
     * Only call this function on the master server!
     */
    protected final long getCurrentMarketPrice(ItemID id)
    {
        IServerMarketManager serverMarketManager = BACKEND_INSTANCES.MARKET_MANAGER.getSync();
        IServerMarket m =  serverMarketManager.getMarket(id);
        if(m == null)
            return 0L;
        return m.getCurrentMarketPrice();
    }

    protected long realToBackendValue(double realValue)
    {
        return BankManager.convertToRawAmountStatic(realValue, getItemFractionScaleFactor());
    }
    protected double backendToRealValue(long realValue)
    {
        return BankManager.convertToRealAmountStatic(realValue, getItemFractionScaleFactor());
    }


    /**
     * Resolves a player's display name from their UUID.
     * Returns the UUID string as fallback if the player is not online.
     *
     * @param playerUUID the UUID to resolve
     * @return the player's name, or the UUID string if not found
     */
    protected String getPlayerName(@Nullable UUID playerUUID) {
        if (playerUUID == null) return "Unknown";
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return playerUUID.toString();
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player == null) return playerUUID.toString();
        return player.getName().getString();
    }

    /**
     * Broadcasts a gold-colored system message to all online players with op level 2,
     * except the player who triggered the action. Used to notify other admins when
     * plugin settings are changed.
     *
     * @param excludePlayer UUID of the player to exclude from the broadcast (the one who made the change), or null to send to all admins
     * @param message       the message text (will be prefixed with [StockMarket] and colored gold)
     */
    protected void broadcastToAdmins(@Nullable UUID excludePlayer, String message) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return;

        Component chatMessage = Component.literal("[StockMarket] " + message)
                .withStyle(ChatFormatting.GOLD);

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            // Skip the player who made the change
            if (excludePlayer != null && player.getUUID().equals(excludePlayer)) {
                continue;
            }
            // Only send to players with op level 2 (stockmarket admin permission)
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(chatMessage);
            }
        }
    }

    protected abstract OUT getDefaultResponse();

    public CompletableFuture<OUT> handleOnServer(IN input, ServerPlayer sender) {
        if(needsRoutingToMaster()) {
            if (getServerMarketManager() != null)
                return handleOnMasterServer(input, "", sender.getUUID());
            else {
                error("Not connected to master server");
                return CompletableFuture.completedFuture(getDefaultResponse());
            }
        }
        return handleOnMasterServer(input, "", sender.getUUID());
    }
    @Override
    public boolean needsRoutingToMaster()
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().isSlave();
    }


    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("["+getRequestTypeID()+"] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("["+getRequestTypeID()+"] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("["+getRequestTypeID()+"] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("["+getRequestTypeID()+"] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("["+getRequestTypeID()+"] " + msg);
    }
}
