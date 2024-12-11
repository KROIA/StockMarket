package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.UpdateBankDataPacket;

public class ClientBankManager {
    private static UpdateBankDataPacket bankDataPacket;

    public static void handlePacket(UpdateBankDataPacket packet)
    {
        bankDataPacket = packet;
    }

    public static long getBalance()
    {
        if(bankDataPacket == null)
        {
            msgBankDataNotReceived();
            return 0;
        }
        return bankDataPacket.getBalance();
    }
    public static long getBalance(String itemID)
    {
        if(bankDataPacket == null)
        {
            msgBankDataNotReceived();
            return 0;
        }
        return bankDataPacket.getBalance(itemID);
    }

    public static long getLockedBalance()
    {
        if(bankDataPacket == null)
        {
            msgBankDataNotReceived();
            return 0;
        }
        return bankDataPacket.getLockedBalance();
    }
    public static long getLockedBalance(String itemID)
    {
        if(bankDataPacket == null)
        {
            msgBankDataNotReceived();
            return 0;
        }
        return bankDataPacket.getLockedBalance(itemID);
    }
    public static boolean hasItemBank(String itemID)
    {
        if(bankDataPacket == null)
        {
            msgBankDataNotReceived();
            return false;
        }
        return bankDataPacket.hasItemBank(itemID);
    }

    private static void msgBankDataNotReceived()
    {
        StockMarketMod.LOGGER.warn("Bank data packet not received yet");
    }
}
