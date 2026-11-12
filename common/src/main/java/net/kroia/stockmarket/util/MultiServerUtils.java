package net.kroia.stockmarket.util;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MultiServerUtils {

    /** Test seam (T-237): market items this JVM treats as unresolvable, as if their mod were missing here. */
    private static final Set<ItemID> UNRESOLVABLE_FOR_TESTING = ConcurrentHashMap.newKeySet();

    /**
     * T-237: whether this server, or a client for the server it is connected to, can resolve the market's item.
     * A market for an item that only exists on the master (its mod is missing here) cannot be shown or traded
     * here and is hidden. Same check as BankSystem's slave-side display filter; lookup only, never registers
     * or mints an ItemID.
     *
     * @param marketID the market's item (null counts as unresolvable)
     * @return true if the item resolves to a non-empty item here
     */
    public static boolean isMarketResolvableOnThisServer(@Nullable ItemID marketID)
    {
        return marketID != null && !UNRESOLVABLE_FOR_TESTING.contains(marketID)
                && ItemIDManager.isResolvableOnThisServer(marketID);
    }

    /**
     * Test seam (T-237): makes {@link #isMarketResolvableOnThisServer} report {@code marketID} as unresolvable,
     * or undoes that. Tests must undo it in a finally block.
     *
     * @param marketID     the market item
     * @param unresolvable true to hide the item, false to undo
     */
    public static void setUnresolvableForTesting(ItemID marketID, boolean unresolvable)
    {
        if (unresolvable)
            UNRESOLVABLE_FOR_TESTING.add(marketID);
        else
            UNRESOLVABLE_FOR_TESTING.remove(marketID);
    }

    /**
     * SM-309: whether this side hides markets it cannot resolve: a slave server, or a client connected to a
     * slave (its item registry is the slave's). The master and its own clients hide nothing, so an admin can
     * still remove broken markets there.
     *
     * @param marketID the market's item
     * @return true if the market must be left out of results received here
     */
    public static boolean isMarketHiddenHere(@Nullable ItemID marketID)
    {
        return isSlaveSide() && !isMarketResolvableOnThisServer(marketID);
    }

    /** @return true on a slave server, or on a client whose server reported that it is not the master */
    private static boolean isSlaveSide()
    {
        if (MultiServerManager.isSlave())
            return true;
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        return client != null && client.SETTINGS != null && !client.SETTINGS.isMasterServer();
    }

    public static boolean checkConnectionToMaster()
    {
        return MultiServerManager.isRunning() && MultiServerManager.isSlave();
    }
    public static boolean checkConnectionToMaster(UUID executor)
    {
        if(checkConnectionToMaster())
            return true;
        // T-159: translatable so the client resolves it in the player's language
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(executor);
        if (player != null)
            player.sendSystemMessage(Component.translatable("message.stockmarket.slave_not_connected"));
        return false;
    }

    public static boolean canInteractWithStockMarket()
    {
        if(BankSystemMod.getAPI().isSlave())
        {
            return checkConnectionToMaster();
        }
        return true;
    }
    public static boolean canInteractWithStockMarket(UUID executor)
    {
        if(BankSystemMod.getAPI().isSlave())
        {
            return checkConnectionToMaster(executor);
        }
        return true;
    }
}
