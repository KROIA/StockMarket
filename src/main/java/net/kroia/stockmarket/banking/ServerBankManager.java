package net.kroia.stockmarket.banking;

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
    public static final String BOT_USER_NAME = "StockMarketBot";
   //private static Map<UUID, MoneyBank> bankMap = new HashMap<>();
   //private static Map<UUID, BotMoneyBank> botBankMap = new HashMap<>();


   /* public static MoneyBank getBank(UUID userUUID) {
        MoneyBank bank = bankMap.get(userUUID);
        if(bank == null)
            bank = getBotBank(userUUID);
        return bank;
    }
    public static BotMoneyBank getBotBank(UUID botUUID) {
        return botBankMap.get(botUUID);
    }

    public static MoneyBank createBankIfNotExist(ServerPlayer player, long balance) {
        MoneyBank bank = getBank(player.getUUID());
        if(bank == null)
        {
            return createBank(player, balance);
        }
        return bank;
    }
    public static MoneyBank createBank(ServerPlayer player, long balance) {
        UUID userUUID = player.getUUID();
        MoneyBank bank = getBank(userUUID);
        if(bank != null)
        {
            StockMarketMod.LOGGER.warn("Bank already exists for user " + userUUID);
            return bank;
        }
        bank = new MoneyBank(userUUID, balance);
        bankMap.put(userUUID, bank);
        String msg = "Bank account created with balance $"+balance;
        player.displayClientMessage(Component.literal(msg), false);
        return bank;
    }

    public static BotMoneyBank createBotBankIfNotExist(UUID botUUID, long balance) {
        BotMoneyBank bank = getBotBank(botUUID);
        if(bank == null)
        {
            return createBotBank(botUUID, balance);
        }
        return bank;
    }

    public static BotMoneyBank createBotBank(UUID botUUID, long balance) {
        BotMoneyBank bank = getBotBank(botUUID);
        if(bank != null)
        {
            StockMarketMod.LOGGER.warn("Bank already exists for bot " + botUUID);
            return bank;
        }
        bank = new BotMoneyBank(botUUID, balance);
        botBankMap.put(botUUID, bank);
        return bank;
    }*/

    public static BankUser createBotUser()
    {
        if(botUser != null)
            return botUser;
        UUID botUUID = UUID.nameUUIDFromBytes(BOT_USER_NAME.getBytes());
        ServerPlayerList.addPlayer(botUUID, BOT_USER_NAME);
        botUser = new BankUser(botUUID);
        botUser.createMoneyBank(1000_000);
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
        userMap.put(userUUID, user);
        return user;
    }

    public static BankUser getUser(UUID userUUID)
    {
        return userMap.get(userUUID);
    }

    public static Bank getMoneyBank(UUID userUUID)
    {
        BankUser user = userMap.get(userUUID);
        if(user == null)
            return null;
        return user.getMoneyBank();
    }


    public static void saveToTag(CompoundTag tag)
    {
        ServerBankManager tmp = new ServerBankManager();
        tmp.save(tag);
    }
    @Override
    public void save(CompoundTag tag) {
        if(botUser != null)
            tag.putUUID("botUUID", botUser.getOwnerUUID());
        ListTag bankElements = new ListTag();
        for (Map.Entry<UUID, BankUser> entry : userMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            bankElements.add(bankTag);
        }
        tag.put("users", bankElements);
    }

    public static void loadFromTag(CompoundTag tag)
    {
        ServerBankManager tmp = new ServerBankManager();
        tmp.load(tag);
    }
    @Override
    public void load(CompoundTag tag) {
        UUID botUUID = null;
        if(tag.contains("botUUID"))
        {
            botUUID = tag.getUUID("botUUID");
        }

        ListTag bankElements = tag.getList("users", 10);
        userMap.clear();
        for (int i = 0; i < bankElements.size(); i++) {
            CompoundTag bankTag = bankElements.getCompound(i);
            BankUser user = new BankUser(bankTag);
            userMap.put(user.getOwnerUUID(), user);
            if(user.getOwnerUUID().compareTo(botUUID) == 0)
            {
                if(botUser != null)
                    ServerPlayerList.removePlayer(botUser.getOwnerUUID());
                botUser = user;
                ServerPlayerList.addPlayer(botUUID, BOT_USER_NAME);

            }
        }
    }

    /*public static void handlePacket(ServerPlayer sender, RequestBankDataPacket packet)
    {
        SyncBankDataPacket.sendPacket(sender);
    }*/
}
