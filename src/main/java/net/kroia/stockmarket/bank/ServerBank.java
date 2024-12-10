package net.kroia.stockmarket.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerBank implements ServerSaveable {
    private static Map<UUID, MoneyBank> bankMap = new HashMap<>();
    private static Map<UUID, BotMoneyBank> botBankMap = new HashMap<>();


    public static MoneyBank getBank(UUID userUUID) {
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
    }


    @Override
    public void save(CompoundTag tag) {
        ListTag bankElements = new ListTag();
        for (Map.Entry<UUID, MoneyBank> entry : bankMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            bankElements.add(bankTag);
        }
        tag.put("banks", bankElements);

        ListTag botBankElements = new ListTag();
        for (Map.Entry<UUID, BotMoneyBank> entry : botBankMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            botBankElements.add(bankTag);
        }
        tag.put("botBanks", botBankElements);
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

        ListTag botBankElements = tag.getList("botBanks", 10);
        botBankMap.clear();
        for (int i = 0; i < botBankElements.size(); i++) {
            CompoundTag bankTag = botBankElements.getCompound(i);
            BotMoneyBank bank = new BotMoneyBank(bankTag);
            botBankMap.put(bank.getUserUUID(), bank);
        }

    }
}
