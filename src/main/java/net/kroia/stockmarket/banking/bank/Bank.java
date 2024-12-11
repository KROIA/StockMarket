package net.kroia.stockmarket.banking.bank;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.util.ServerPlayerList;
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
        notifyUser("Balance set to " + this.balance + " for "+itemID);
    }

    public UUID getOwnerUUID() {
        return owner.getOwnerUUID();
    }
    public ServerPlayer getOwner()
    {
        return owner.getOwner();
    }

    public String getOwnerName()
    {
        return owner.getOwnerName();
    }


    public void deposit(long amount) {
        dbg_checkValueIsNegative(amount);
        addBalanceInternal(amount);
        notifyUser("Deposited " + amount+ " of "+itemID);
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
        notifyUser("Withdrew " + amount + " of "+itemID);
        return true;
    }
    public boolean transfer(long amount, Bank other) {
        if (balance < amount) {
            return false;
        }
        dbg_checkValueIsNegative(amount);
        addBalanceInternal(-amount);
        other.deposit(amount);
        notifyUser_transfer(amount, itemID, other);
        return true;
    }
    public boolean transferFromLocked(long amount, Bank other) {
        if (lockedBalance < amount) {
            return false;
        }
        dbg_checkValueIsNegative(amount);
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser_transfer(amount, itemID, other);
        return true;
    }
    public boolean transferFromLockedPrefered(long amount, Bank other) {
        dbg_checkValueIsNegative(amount);
        long origAmount = amount;
        if (lockedBalance < amount) {
            if (balance+lockedBalance < amount) {
                return false;
            }
            amount -= lockedBalance;
            lockedBalance = 0;
            addBalanceInternal(-amount);
            other.deposit(origAmount);
            notifyUser_transfer(origAmount, itemID, other);
            return true;
        }
        lockedBalance -= amount;
        other.deposit(amount);
        notifyUser_transfer(amount, itemID, other);
        return true;
    }

    public static boolean exchangeFromLockedPrefered(Bank from1, Bank to1, long amount1, Bank from2, Bank to2, long amount2)
    {
        dbg_checkValueIsNegative(amount1);
        dbg_checkValueIsNegative(amount2);

        // Both transactions must be possible, otherwise no transaction is done
        // Copy original data
        long origLockedBalance1 = from1.lockedBalance;
        long origLockedBalance2 = from2.lockedBalance;
        long origBalance1 = from1.balance;
        long origBalance2 = from2.balance;

        // Try to transfer from locked balance
        if(from1.transferFromLockedPrefered(amount1, to1) && from2.transferFromLockedPrefered(amount2, to2))
        {
            return true;
        }
        // If not possible, revert changes
        from1.lockedBalance = origLockedBalance1;
        from2.lockedBalance = origLockedBalance2;
        from1.balance = origBalance1;
        from2.balance = origBalance2;
        return false;
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

    private static void dbg_invalid_balance(long balance) {
        StockMarketMod.LOGGER.warn("Invalid balance: " + balance);
    }
    private static void dbg_checkValueIsNegative(long value) {
        if(value < 0)
            StockMarketMod.LOGGER.warn("Value is negative: " + value);
    }


    protected void notifyUser_transfer(long amount, String itemID, Bank other) {
        if(amount == 0)
            return;
        notifyUser("Transferred " + amount + " of "+itemID+" to user: "+ other.getOwnerName());
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
        else
            owner = ServerPlayerList.getPlayerName(getOwnerUUID());
        return "Owner: "+owner+" "+toStringNoOwner();
    }
    public String toStringNoOwner()
    {
        StringBuilder content = new StringBuilder("ItemID: \""+ itemID +"\" Balance: "+(balance+lockedBalance));
        if(lockedBalance > 0)
            content.append(" (Free available: ").append(balance).append(", Locked: ").append(lockedBalance).append(")");
        return content.toString();
    }

}
