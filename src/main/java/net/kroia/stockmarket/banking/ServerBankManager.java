package net.kroia.stockmarket.banking;

import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerBankManager implements ServerSaveable {

    private static Map<UUID, BankUser> userMap = new HashMap<>();
    private static BankUser botUser;
    public static BankUser createBotUser()
    {
        if(botUser != null || !ModSettings.MarketBot.ENABLED)
            return botUser;
        UUID botUUID = UUID.nameUUIDFromBytes(ModSettings.MarketBot.USER_NAME.getBytes());
        ServerPlayerList.addPlayer(botUUID, ModSettings.MarketBot.USER_NAME);
        botUser = new BankUser(botUUID);
        botUser.createMoneyBank(ModSettings.MarketBot.STARTING_BALANCE);
        userMap.put(botUser.getOwnerUUID(), botUser);
        return botUser;
    }

    public static BankUser getBotUser()
    {
        return botUser;
    }

    public static BankUser createUser(UUID userUUID, ArrayList<String> itemIDs, boolean createMoneyBank, long startMoney)
    {
        BankUser user = userMap.get(userUUID);
        if(user != null)
            return user;
        user = new BankUser(userUUID);
        for(String itemID : itemIDs)
            user.createItemBank(itemID, 0);
        if(createMoneyBank)
            user.createMoneyBank(startMoney);
        StockMarketMod.printToClientConsole(userUUID, "A bank account has been created for you.\n" +
                "You can access your account using the Bank Terminal block\nor the /bank command.");
        userMap.put(userUUID, user);
        return user;
    }

    public static BankUser getUser(UUID userUUID)
    {
        return userMap.get(userUUID);
    }
    public static void clear()
    {
        userMap.clear();
        botUser = null;
    }

    public static Bank getMoneyBank(UUID userUUID)
    {
        BankUser user = userMap.get(userUUID);
        if(user == null)
            return null;
        return user.getMoneyBank();
    }

    public static long getMoneyCirculation()
    {
        long total = 0;
        for (Map.Entry<UUID, BankUser> entry : userMap.entrySet()) {
            total += entry.getValue().getTotalMoneyBalance();
        }
        return total;
    }


    public static boolean saveToTag(CompoundTag tag)
    {
        ServerBankManager tmp = new ServerBankManager();
        return tmp.save(tag);
    }
    @Override
    public boolean save(CompoundTag tag) {
        if(botUser != null)
            tag.putUUID("botUUID", botUser.getOwnerUUID());
        ListTag bankElements = new ListTag();
        for (Map.Entry<UUID, BankUser> entry : userMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            bankElements.add(bankTag);
        }
        tag.put("users", bankElements);
        return true;
    }

    public static boolean loadFromTag(CompoundTag tag)
    {
        ServerBankManager tmp = new ServerBankManager();
        return tmp.load(tag);
    }
    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;
        UUID botUUID = null;
        if(tag.contains("botUUID") && ModSettings.MarketBot.ENABLED)
        {
            botUUID = tag.getUUID("botUUID");
        }

        ListTag bankElements = tag.getList("users", 10);
        userMap.clear();
        for (int i = 0; i < bankElements.size(); i++) {
            CompoundTag bankTag = bankElements.getCompound(i);
            BankUser user = BankUser.loadFromTag(bankTag);
            if(user == null)
            {
                success = false;
                continue;
            }
            userMap.put(user.getOwnerUUID(), user);
            if(botUUID != null) {
                if (user.getOwnerUUID().compareTo(botUUID) == 0) {
                    if (botUser != null)
                        ServerPlayerList.removePlayer(botUser.getOwnerUUID());
                    botUser = user;
                    ServerPlayerList.addPlayer(botUUID, ModSettings.MarketBot.USER_NAME);
                }
            }
        }
        return success;
    }

    /*public static void handlePacket(ServerPlayer sender, RequestBankDataPacket packet)
    {
        SyncBankDataPacket.sendPacket(sender);
    }*/
}
