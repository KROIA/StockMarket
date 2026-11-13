package net.kroia.stockmarket.minecraft.command;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.UtilitiesPlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Master-side command handler implementation.
 * Implements BOTH IServerStockMarketCommandHandler (sync, master-only) and
 * IAsyncStockMarketCommandHandler (async, wraps sync calls in CompletableFuture).
 */
public class ServerStockMarketCommandHandler implements IServerStockMarketCommandHandler, IAsyncStockMarketCommandHandler {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    // === IAsyncStockMarketCommandHandler (GUI commands) ===

    @Override
    public CompletableFuture<Boolean> stockmarket_devTestScreen_async(@NotNull UUID executor) {
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
        if (player != null) {
            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.DEVELOPMENT);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    // === IServerStockMarketCommandHandler (master-only commands) ===

    /** SM-264: targets the executor's own UUID; no name lookup, which could hit another user with the same name. */
    @Override
    public boolean stockmarket_setStockmarketAdminMode(@NotNull UUID executor, boolean isAdmin) {
        return setAdminMode(executor, executor, tryGetPlayerName(executor), isAdmin);
    }

    /**
     * SM-264: resolves {@code userName} via {@code getPlayerUUID} (online player first, then the stored users) and
     * refuses with {@code message.stockmarket.command_player_name_ambiguous} if several stored users share the name.
     */
    @Override
    public boolean stockmarket_setStockmarketAdminMode_user(@NotNull UUID executor, String userName, boolean isAdmin) {
        @Nullable UUID playerUUID = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getPlayerUUID(userName);
        if (playerUUID == null) {
            boolean ambiguous = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager().isUserNameAmbiguous(userName);
            sendFailure(executor, Component.translatable(ambiguous
                    ? "message.stockmarket.command_player_name_ambiguous"
                    : "message.stockmarket.command_player_not_found", userName));
            return false;
        }
        return setAdminMode(executor, playerUUID, userName, isAdmin);
    }

    /**
     * Sets the admin flag of an already resolved user, tells executor and target, and mirrors it to LuckPerms.
     *
     * @param executor   the player who ran the command
     * @param playerUUID the target user's UUID
     * @param userName   the target's name, for the messages and LuckPerms
     * @param isAdmin    the new flag
     * @return true if the user exists and the flag was set
     */
    private static boolean setAdminMode(UUID executor, UUID playerUUID, String userName, boolean isAdmin) {
        if (BACKEND_INSTANCES.MARKET_MANAGER.getSync().setStockmarketAdminMode(playerUUID, isAdmin)) {
            Component msg = Component.translatable(isAdmin
                    ? "message.stockmarket.command_admin_mode_enabled"
                    : "message.stockmarket.command_admin_mode_disabled", userName);
            sendMessage(executor, msg);
            if (!executor.equals(playerUUID))
                sendMessage(playerUUID, msg);
            // Mirror into LuckPerms (setting on + LuckPerms only); deop re-checks group grants ("group wins").
            net.kroia.stockmarket.util.AdminFlagSync.pushToLuckPerms(executor, playerUUID, userName, isAdmin);
            return true;
        }
        sendFailure(executor, Component.translatable("message.stockmarket.command_player_not_found", userName));
        return false;
    }

    // === Helper methods ===

    public static String tryGetPlayerName(UUID player) {
        if (UtilitiesPlatform.getServer() != null) {
            ServerPlayer serverPlayer = ServerPlayerUtilities.getOnlinePlayer(player);
            if (serverPlayer != null) {
                return serverPlayer.getName().getString();
            }
        }
        return player.toString();
    }

    /**
     * Sends a chat message with the literal "[StockMarket] " prefix to an online player.
     * T-159: takes a (translatable) Component so the text is resolved on the client
     * in the player's language — never resolve lang keys on the server.
     *
     * @param player  recipient UUID; ignored if the player is offline
     * @param message message body, usually {@code Component.translatable(key, args)}
     */
    public static void sendMessage(UUID player, Component message) {
        ServerPlayer serverPlayer = ServerPlayerUtilities.getOnlinePlayer(player);
        if (serverPlayer != null)
            serverPlayer.sendSystemMessage(Component.literal("[StockMarket] ").append(message));
    }

    /**
     * SM-312: like {@link #sendMessage}, but the whole line is red, the same as {@code CommandSourceStack.sendFailure}.
     * For refusals and failures only.
     *
     * @param player  recipient UUID; ignored if the player is offline
     * @param message message body, usually {@code Component.translatable(key, args)}
     */
    public static void sendFailure(UUID player, Component message) {
        ServerPlayer serverPlayer = ServerPlayerUtilities.getOnlinePlayer(player);
        if (serverPlayer != null)
            serverPlayer.sendSystemMessage(Component.literal("[StockMarket] ").append(message).withStyle(ChatFormatting.RED));
    }

    private static void info(String msg) {
        BACKEND_INSTANCES.LOGGER.info("[ServerStockMarketCommandHandler] " + msg);
    }

    private static void error(String msg) {
        BACKEND_INSTANCES.LOGGER.error("[ServerStockMarketCommandHandler] " + msg);
    }
}
