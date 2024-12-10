package net.kroia.stockmarket.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoneyBank implements ServerSaveable {

    private long balance;
    private long lockedBalance;
    private UUID userUUID;

    public MoneyBank(UUID userUUID, long balance) {
        this.userUUID = userUUID;
        this.balance = balance;
        this.lockedBalance = 0;
    }
    public MoneyBank(FriendlyByteBuf buf) {
        balance = buf.readLong();
        lockedBalance = buf.readLong();
        String userUUID = buf.readUtf();
        this.userUUID = UUID.fromString(userUUID);
    }
    public MoneyBank(CompoundTag tag) {
        load(tag);
    }

    public long getBalance() {
        return balance;
    }
    public long getLockedBalance() {
        return lockedBalance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
        notifyUser("Balance set to $" + balance);
    }

    public UUID getUserUUID() {
        return userUUID;
    }

    public void setUserUUID(UUID userUUID) {
        this.userUUID = userUUID;
    }

    public void deposit(long amount) {
        balance += amount;
        notifyUser("Deposited $" + amount);
    }


    public boolean hasSufficientFunds(long amount) {
        return balance >= amount;
    }

    public boolean withdraw(long amount) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        notifyUser("Withdrew $" + amount);
        return true;
    }
    public boolean transfer(long amount, MoneyBank other) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        other.deposit(amount);
        notifyUser("Transferred $" + amount + " to user "+ other.getUserUUID());
        return true;
    }
    public boolean transferFromLocked(long amount, MoneyBank other) {
        if (lockedBalance < amount) {
            return false;
        }
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser("Transferred $" + amount + " from locked balance to user "+ other.getUserUUID());
        return true;
    }
    public boolean transferFromLockedPrefered(long amount, MoneyBank other) {
        long origAmount = amount;
        if (lockedBalance < amount) {
            if (balance+lockedBalance < amount) {
                return false;
            }
            long origLockedBalance = lockedBalance;
            amount -= lockedBalance;
            lockedBalance = 0;
            balance -= amount;
            other.deposit(origAmount);
            notifyUser("Transferred $" + origAmount + "(locked: $"+origLockedBalance+" + free: $"+amount+") to user "+ other.getUserUUID());
            return true;
        }
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser("Transferred $" + amount + " from locked balance to user "+ other.getUserUUID());
        return true;
    }

    public boolean lockAmount(long amount) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        lockedBalance += amount;
        notifyUser("Locked $" + amount);
        return true;
    }
    public boolean unlockAmount(long amount) {
        if (lockedBalance < amount) {
            return false;
        }
        balance += amount;
        lockedBalance -= amount;
        notifyUser("Unlocked $" + amount);
        return true;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("balance", balance);
        tag.putLong("lockedBalance", lockedBalance);
        String userUUID = this.userUUID.toString();
        tag.putString("userUUID", userUUID);
    }

    @Override
    public void load(CompoundTag tag) {
        balance = tag.getLong("balance");
        lockedBalance = tag.getLong("lockedBalance");
        String userUUID = tag.getString("userUUID");

        this.userUUID = UUID.fromString(userUUID);
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeLong(balance);
        buf.writeLong(lockedBalance);
        String userUUID = this.userUUID.toString();
        buf.writeUtf(userUUID);
    }

    protected void notifyUser(String msg) {
        StockMarketMod.printToClientConsole(userUUID, msg);
    }


}
