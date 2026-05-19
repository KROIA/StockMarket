package net.kroia.stockmarket.minecraft.command;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.command.IAsyncStockMarketCommandHandler;
import net.kroia.stockmarket.api.command.IServerStockMarketCommandHandler;
import net.kroia.stockmarket.data.StarterKitData;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.UtilitiesPlatform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    public CompletableFuture<Boolean> stockmarket_manage_async(@NotNull UUID executor) {
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
        if (player == null) return CompletableFuture.completedFuture(false);
        if (BACKEND_INSTANCES.MARKET_MANAGER.getSync().isStockmarketAdmin(executor)) {
            OpenUIPacket.sendToClient(player, OpenUIPacket.GUIType.MANAGEMENT);
            return CompletableFuture.completedFuture(true);
        } else {
            ServerPlayerUtilities.printToClientConsole(player, "This command is only for StockMarket admins!");
            return CompletableFuture.completedFuture(false);
        }
    }

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

    @Override
    public boolean stockmarket_setStockmarketAdminMode(@NotNull UUID executor, boolean isAdmin) {
        return stockmarket_setStockmarketAdminMode_user(executor, tryGetPlayerName(executor), isAdmin);
    }

    @Override
    public boolean stockmarket_setStockmarketAdminMode_user(@NotNull UUID executor, String userName, boolean isAdmin) {
        @Nullable UUID playerUUID = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getPlayerUUID(userName);
        if (playerUUID == null) {
            sendMessage(executor, "No UUID found for Player: " + userName);
            return false;
        }
        if (BACKEND_INSTANCES.MARKET_MANAGER.getSync().setStockmarketAdminMode(playerUUID, isAdmin)) {
            sendMessage(executor, "Stockmarket admin mode set to: " + (isAdmin ? "ON" : "OFF") + " for player: " + userName);
            if (!executor.equals(playerUUID))
                sendMessage(playerUUID, "Stockmarket admin mode set to: " + (isAdmin ? "ON" : "OFF") + " for player: " + userName);
            return true;
        }
        return false;
    }

    @Override
    public boolean stockmarket_starterKit(@NotNull UUID executor) {
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
        if (player == null) return false;

        StarterKitData starterKitData = BACKEND_INSTANCES.DATA_MANAGER.getStarterKitData();
        if (starterKitData.hasClaimed(executor)) {
            sendMessage(executor, "You have already claimed the starter kit!");
            return false;
        }

        List<ItemStack> items = StarterKitData.getStarterKitItems();
        for (ItemStack itemStack : items) {
            if (!player.getInventory().add(itemStack)) {
                // Inventory full — drop the item on the ground
                player.drop(itemStack, false);
            }
        }

        starterKitData.markClaimed(executor);
        BACKEND_INSTANCES.DATA_MANAGER.saveStarterKit();
        sendMessage(executor, "Starter kit claimed! Check your inventory.");
        return true;
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

    public static void sendMessage(UUID player, String message) {
        ServerPlayerUtilities.printToClientConsole(player, "[StockMarket] " + message);
    }

    private static void info(String msg) {
        BACKEND_INSTANCES.LOGGER.info("[ServerStockMarketCommandHandler] " + msg);
    }

    private static void error(String msg) {
        BACKEND_INSTANCES.LOGGER.error("[ServerStockMarketCommandHandler] " + msg);
    }
}
