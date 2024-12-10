package net.kroia.stockmarket.bank;

import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

public class MoneyBank implements ServerSaveable {

    private long balance;
    private String userUUID;

    public MoneyBank(String userUUID, long balance) {
        this.userUUID = userUUID;
        this.balance = balance;
    }
    public MoneyBank(FriendlyByteBuf buf) {
        balance = buf.readLong();
        userUUID = buf.readUtf();
    }
    public MoneyBank(CompoundTag tag) {
        load(tag);
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public String getUserUUID() {
        return userUUID;
    }

    public void setUserUUID(String userUUID) {
        this.userUUID = userUUID;
    }

    public void deposit(long amount) {
        balance += amount;
    }

    public boolean withdraw(long amount) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        return true;
    }
    public boolean transfer(long amount, MoneyBank other) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        other.deposit(amount);
        return true;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("balance", balance);
        tag.putString("userUUID", userUUID);
    }

    @Override
    public void load(CompoundTag tag) {
        balance = tag.getLong("balance");
        userUUID = tag.getString("userUUID");
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeLong(balance);
        buf.writeUtf(userUUID);
    }


}
