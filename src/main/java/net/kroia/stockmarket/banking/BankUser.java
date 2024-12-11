package net.kroia.stockmarket.banking;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.ItemBank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BankUser implements ServerSaveable {
    private UUID userUUID;
    private final HashMap<String, Bank> bankMap = new HashMap<>();

    public BankUser(UUID userUUID) {
        this.userUUID = userUUID;
    }
    public BankUser(CompoundTag tag)
    {
        load(tag);
    }

    public Bank createMoneyBank(long startBalance)
    {
        Bank bank = getBank(MoneyBank.ITEM_ID);
        if(bank != null)
            return bank;
        bank = new MoneyBank(this, startBalance);
        bankMap.put(MoneyBank.ITEM_ID, bank);
        return bank;
    }
    public Bank createItemBank(String itemID, long startBalance)
    {
        Bank bank = getBank(itemID);
        if(bank != null)
            return bank;
        bank = new ItemBank(this, itemID,  startBalance);
        bankMap.put(itemID, bank);
        return bank;
    }

    public Bank getBank(String itemID)
    {
        return bankMap.get(itemID);
    }
    public boolean removeBank(String itemID)
    {
        return bankMap.remove(itemID) != null;
    }
    public Bank getMoneyBank()
    {
        return bankMap.get(MoneyBank.ITEM_ID);
    }

    public HashMap<String, Bank> getBankMap()
    {
        return bankMap;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putUUID("userUUID", userUUID);

        ListTag bankElements = new ListTag();
        for (Map.Entry<String, Bank> entry : bankMap.entrySet()) {
            CompoundTag bankTag = new CompoundTag();
            entry.getValue().save(bankTag);
            bankElements.add(bankTag);
        }
        tag.put("bankMap", bankElements);

    }

    @Override
    public void load(CompoundTag tag) {
        userUUID = tag.getUUID("userUUID");

        ListTag bankElements = tag.getList("bankMap", 10);
        bankMap.clear();
        for (int i = 0; i < bankElements.size(); i++) {
            CompoundTag bankTag = bankElements.getCompound(i);
            Bank bank = Bank.construct(this, bankTag);
            if(bank != null)
                bankMap.put(bank.getItemID(), bank);
        }
    }

    public UUID getOwnerUUID() {
        return userUUID;
    }
    public ServerPlayer getOwner()
    {
        return ServerPlayerList.getPlayer(userUUID);
    }

    public String getOwnerName()
    {
        return ServerPlayerList.getPlayerName(userUUID);
    }

    public String toString()
    {
        String owner = getOwnerUUID().toString();
        ServerPlayer player = getOwner();
        if(player != null)
            owner = player.getName().getString();
        else
            owner = ServerPlayerList.getPlayerName(getOwnerUUID());
        StringBuilder content = new StringBuilder("BankUser: " + owner + "\n");
        for(Bank bank : bankMap.values())
        {
            content.append("  ").append(bank.toStringNoOwner()).append("\n");
        }
        return content.toString();
    }
}
