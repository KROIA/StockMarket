package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class Bank implements ServerSaveable {

    public enum BankType
    {
        MONEY,
        BOT_MONEY,
        ITEM,
        BOT_ITEM,
        ABSTRACT
    }

    private final BankUser owner;
    protected long balance;
    protected long lockedBalance;

    String itemID;

    public Bank(BankUser owner, String itemID, long balance) {
        this.owner = owner;
        this.itemID = itemID;
        setBalanceInternal(balance);
        this.lockedBalance = 0;
    }
    public Bank(BankUser owner, CompoundTag tag) {
        this.owner = owner;
        load(tag);
    }

    public static Bank construct(BankUser owner, CompoundTag tag) {
        BankType type = BankType.valueOf(tag.getString("BankType"));
        switch(type)
        {
            case MONEY:
                return new MoneyBank(owner, tag);
            case BOT_MONEY:
                return new BotMoneyBank(owner, tag);
            case ITEM:
                return new ItemBank(owner, tag);
            case BOT_ITEM:
                //return new BotItemBank(owner, tag);
            default:
                return null;
        }
    }


    public long getBalance() {
        return balance;
    }
    public long getLockedBalance() {
        return lockedBalance;
    }
    public String getItemID() {
        return itemID;
    }

    public void setBalance(long balance) {
        setBalanceInternal(balance);
        notifyUser("Balance set to " + this.balance);
    }

    public UUID getOwnerUUID() {
        return owner.getOwnerUUID();
    }
    public ServerPlayer getOwner()
    {
        return owner.getOwner();
    }

    public void deposit(long amount) {
        dbg_checkValueIsNegative(amount);
        addBalanceInternal(amount);
        notifyUser("Deposited " + amount);
    }


    public boolean hasSufficientFunds(long amount) {
        return balance >= amount;
    }

    public boolean withdraw(long amount) {
        if (balance < amount) {
            return false;
        }
        dbg_checkValueIsNegative(amount);
        addBalanceInternal(-amount);
        notifyUser("Withdrew " + amount);
        return true;
    }
    public boolean transfer(long amount, Bank other) {
        if (balance < amount) {
            return false;
        }
        dbg_checkValueIsNegative(amount);
        addBalanceInternal(-amount);
        other.deposit(amount);
        notifyUser("Transferred " + amount + " to user "+ other.getOwnerUUID());
        return true;
    }
    public boolean transferFromLocked(long amount, Bank other) {
        if (lockedBalance < amount) {
            return false;
        }
        dbg_checkValueIsNegative(amount);
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser("Transferred " + amount + " from locked balance to user "+ other.getOwnerUUID());
        return true;
    }
    public boolean transferFromLockedPrefered(long amount, Bank other) {
        dbg_checkValueIsNegative(amount);
        long origAmount = amount;
        if (lockedBalance < amount) {
            if (balance+lockedBalance < amount) {
                return false;
            }
            long origLockedBalance = lockedBalance;
            amount -= lockedBalance;
            lockedBalance = 0;
            addBalanceInternal(-amount);
            other.deposit(origAmount);
            notifyUser("Transferred " + origAmount + "(locked: $"+origLockedBalance+" + free: $"+amount+") to user "+ other.getOwnerUUID());
            return true;
        }
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser("Transferred " + amount + " from locked balance to user "+ other.getOwnerUUID());
        return true;
    }

    public boolean lockAmount(long amount) {
        dbg_checkValueIsNegative(amount);
        if (balance < amount) {
            return false;
        }
        addBalanceInternal(-amount);
        lockedBalance += amount;
        dbg_checkValueIsNegative(lockedBalance);
        notifyUser("Locked " + amount);
        return true;
    }
    public boolean unlockAmount(long amount) {
        dbg_checkValueIsNegative(amount);
        if (lockedBalance < amount) {
            return false;
        }
        addBalanceInternal(amount);
        lockedBalance -= amount;
        notifyUser("Unlocked " + amount);
        return true;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("itemID", itemID);
        tag.putLong("balance", balance);
        tag.putLong("lockedBalance", lockedBalance);
    }

    @Override
    public void load(CompoundTag tag) {
        itemID = tag.getString("itemID");
        setBalanceInternal(tag.getLong("balance"));
        lockedBalance = tag.getLong("lockedBalance");
    }

    private void addBalanceInternal(long balance) {
        setBalanceInternal(this.balance + balance);
    }
    private void setBalanceInternal(long balance) {
        if(balance < 0)
            dbg_invalid_balance(balance);
        this.balance = balance;
    }

    private void dbg_invalid_balance(long balance) {
        StockMarketMod.LOGGER.warn("Invalid balance: " + balance);
    }
    private void dbg_checkValueIsNegative(long value) {
        if(value < 0)
            StockMarketMod.LOGGER.warn("Value is negative: " + value);
    }

    protected void notifyUser(String msg) {
        StockMarketMod.printToClientConsole(getOwner(), msg);
    }

    public String toString()
    {
        String owner = getOwnerUUID().toString();
        ServerPlayer player = getOwner();
        if(player != null)
            owner = player.getName().getString();
        return "Owner: "+owner+" "+toStringNoOwner();
    }
    public String toStringNoOwner()
    {
        StringBuilder content = new StringBuilder("ItemID: "+ itemID +" Balance: "+(balance+lockedBalance));
        if(lockedBalance > 0)
            content.append(" (Free available: ").append(balance).append(", Locked: ").append(lockedBalance).append(")");
        return content.toString();
    }

}
