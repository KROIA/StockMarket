package net.kroia.stockmarket.banking.bank;

import com.mojang.datafixers.util.Pair;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBankDataPacket;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientBankManager {
    private static SyncBankDataPacket bankDataPacket;

    public static void clear()
    {
        bankDataPacket = null;
    }
    public static void handlePacket(SyncBankDataPacket packet)
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

    public static HashMap<String, SyncBankDataPacket.BankData> getBankData() {
        return bankDataPacket.getBankData();
    }
    public static ArrayList<Pair<String, SyncBankDataPacket.BankData>> getSortedItemData()
    {
        HashMap<String, SyncBankDataPacket.BankData> bankAccounts = bankDataPacket.getBankData();
        // Sort the bank accounts by itemID
        ArrayList<Pair<String,SyncBankDataPacket.BankData>> sortedBankAccounts = new ArrayList<>();
        for (String itemID : bankAccounts.keySet()) {
            if(itemID.equals(MoneyBank.ITEM_ID))
                continue; // Skip the money bank
            sortedBankAccounts.add(new Pair<>(itemID, bankAccounts.get(itemID)));
        }
        //sortedBankAccounts.sort((a, b) -> a.getFirst().compareTo(b.getFirst()));
        // Sort by balance
        sortedBankAccounts.sort((a, b) -> Long.compare(b.getSecond().getBalance(), a.getSecond().getBalance()));
        return sortedBankAccounts;
    }

    private static void msgBankDataNotReceived()
    {
        StockMarketMod.LOGGER.warn("Bank data packet not received yet");
    }
}
