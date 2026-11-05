package net.kroia.stockmarket.util;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public class MultiServerUtils {

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
