package net.kroia.stockmarket.util;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.StockMarketMod;
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
        ServerPlayerUtilities.printToClientConsole(executor, "["+ StockMarketMod.getAPI().getModID()+"] This is a slave server and it is not connected to a "+StockMarketMod.getAPI().getModID()+" master server!");
        return false;
    }

    public static boolean canInteractWithStockMarket()
    {
        if(BankSystemMod.getAPI().getServerBankManager().isSlave())
        {
            return checkConnectionToMaster();
        }
        return true;
    }
    public static boolean canInteractWithStockMarket(UUID executor)
    {
        if(BankSystemMod.getAPI().getServerBankManager().isSlave())
        {
            return checkConnectionToMaster(executor);
        }
        return true;
    }
}
