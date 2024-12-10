package net.kroia.stockmarket.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public class ServerBank implements ServerSaveable {
    private static Map<String, MoneyBank> bankMap = new HashMap<>();


    public static MoneyBank getBank(String userUUID) {
        return bankMap.get(userUUID);
    }

    public static void createBankIfNotExist(ServerPlayer player, long balance) {
        if(!bankMap.containsKey(player.getStringUUID()))
        {
            createBank(player, balance);
        }
    }
    public static void createBank(ServerPlayer player, long balance) {
        String userUUID = player.getStringUUID();
        if(bankMap.containsKey(userUUID))
        {
            StockMarketMod.LOGGER.warn("Bank already exists for user " + userUUID);
            return;
        }
        bankMap.put(userUUID, new MoneyBank(userUUID, balance));
        String msg = "Bank account created with balance $"+balance;
        player.displayClientMessage(Component.literal(msg), false);
    }


    @Override
    public void save(CompoundTag tag) {
        ListTag bankElements = new ListTag();
        for (Map.Entry<String, MoneyBank> entry : bankMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            bankElements.add(bankTag);
        }
        tag.put("banks", bankElements);
    }

    @Override
    public void load(CompoundTag tag) {
        ListTag bankElements = tag.getList("banks", 10);
        bankMap.clear();
        for (int i = 0; i < bankElements.size(); i++) {
            CompoundTag bankTag = bankElements.getCompound(i);
            MoneyBank bank = new MoneyBank(bankTag);
            bankMap.put(bank.getUserUUID(), bank);
        }

    }
}
