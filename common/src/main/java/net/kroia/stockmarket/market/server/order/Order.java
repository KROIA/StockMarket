package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.banking.BankUser;
import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncOrderPacket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/*
    * The Order class represents a buy or sell order.
 */
public abstract class Order {
    private static long lastOrderID = 0;

    protected long orderID;
    protected String itemID;
    protected UUID playerUUID;
    //private final String itemID;
    protected int amount;
    protected int filledAmount = 0;
    protected long transferedMoney = 0;

    protected String invalidReason = "";

    protected long lockedMoney = 0;

    protected boolean isBot = false;

    public enum Status {
        PENDING,
        PROCESSED,
        PARTIAL,
        CANCELLED,
        INVALID
    }
    public enum Type {
        MARKET,
        LIMIT
    }
    protected Status status = Status.PENDING;

    protected Order(UUID playerUUID, String itemID, int amount) {
        this.itemID = itemID;
        this.orderID = uniqueOrderID();
        this.playerUUID = playerUUID;
        //this.itemID = itemID;
        this.amount = amount;
    }
    protected Order(UUID playerUUID, String itemID, int amount, boolean isBot) {
        this.itemID = itemID;
        this.orderID = uniqueOrderID();
        this.playerUUID = playerUUID;
        //this.itemID = itemID;
        this.amount = amount;
        this.isBot = isBot;
    }
    protected Order()
    {

    }
    protected static boolean tryReserveBankFund(ServerPlayer player, String itemID, int amount, int price)
    {
        BankUser bankUser = ServerBankManager.getUser(player.getUUID());
        if(bankUser == null)
        {
            PlayerUtilities.printToClientConsole(player, BankSystemTextMessages.getBankNotFoundMessage(player.getName().getString(),itemID));
            return false;
        }

        Bank moneyBank = bankUser.getMoneyBank();
        Bank itemBank = bankUser.getBank(itemID);
        if(itemBank == null)
        {
            itemBank = bankUser.createItemBank(itemID, 0);
        }

        return tryReserveBankFund(moneyBank, itemBank, player.getUUID(), itemID, amount, price, player);
    }
    protected static boolean tryReserveBankFund(Bank moneyBank, Bank itemBank, UUID playerUUID, String itemID, int amount, int price, ServerPlayer dbgPlayer)
    {
        if(moneyBank == null)
        {
            if(dbgPlayer != null)
                PlayerUtilities.printToClientConsole(dbgPlayer, BankSystemTextMessages.getBankNotFoundMessage(ServerPlayerList.getPlayerName(playerUUID), MoneyItem.getName()));
            return false;
        }
        if(itemBank == null)
        {
            if(dbgPlayer != null)
                PlayerUtilities.printToClientConsole(dbgPlayer, BankSystemTextMessages.getBankNotFoundMessage(ServerPlayerList.getPlayerName(playerUUID), MoneyItem.getName()));
            return false;
        }
        if(amount > 0) {
            if (moneyBank.lockAmount((long) price * amount) != Bank.Status.SUCCESS) {
                if(dbgPlayer != null)
                    PlayerUtilities.printToClientConsole(dbgPlayer, StockMarketTextMessages.getInsufficientFundToBuyMessage(itemID, amount, price));
                return false;
            }
        }
        else {
            if (itemBank.lockAmount(-amount) != Bank.Status.SUCCESS){
                if(dbgPlayer != null)
                    PlayerUtilities.printToClientConsole(dbgPlayer, StockMarketTextMessages.getInsufficientItemsToSellMessage(itemID, amount));
                return false;
            }
        }
        return true;
    }

    public static Order construct(FriendlyByteBuf buf)
    {
        Type type = Type.valueOf(buf.readUtf());
        return switch (type) {
            case MARKET -> new MarketOrder(buf);
            case LIMIT -> new LimitOrder(buf);
            default -> throw new IllegalArgumentException("Invalid order type");
        };
    }
    protected Order(FriendlyByteBuf buf)
    {
        orderID = buf.readLong();
        itemID = buf.readUtf();
        playerUUID = buf.readUUID();
        amount = buf.readInt();
        filledAmount = buf.readInt();
        lockedMoney = buf.readLong();
        transferedMoney = buf.readLong();
        status = Status.valueOf(buf.readUtf());
        invalidReason = buf.readUtf();
        isBot = buf.readBoolean();
    }

    public void copyFrom(Order other)
    {
        orderID = other.orderID;
        itemID = other.itemID;
        playerUUID = other.playerUUID;
        amount = other.amount;
        filledAmount = other.filledAmount;
        lockedMoney = other.lockedMoney;
        transferedMoney = other.transferedMoney;
        status = other.status;
        invalidReason = other.invalidReason;
        isBot = other.isBot;
    }

    public void markAsBot() {
        isBot = true;
    }
    public boolean isBot() {
        return isBot;
    }

    public long getLockedMoney() {
        return lockedMoney;
    }


    public static long uniqueOrderID()
    {
        long oderID = System.currentTimeMillis();
        if(oderID <= lastOrderID)
        {
            oderID = lastOrderID + 1;
            while(oderID <= lastOrderID)
            {
                oderID++;
            }
        }
        lastOrderID = oderID;
        return oderID;
    }

    boolean isEqual(Order other)
    {
        return  orderID == other.orderID &&
                itemID.compareTo(other.itemID)==0 &&
                playerUUID.compareTo(other.playerUUID)==0 &&
                amount == other.amount &&
                filledAmount == other.filledAmount &&
                lockedMoney == other.lockedMoney &&
                transferedMoney == other.transferedMoney &&
                status == other.status;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }
    public long getOrderID() {
        return orderID;
    }
    public String getItemID() {
        return itemID;
    }
    public long getTransferedMoney() {
        return transferedMoney;
    }
    public void addTransferedMoney(long money) {
        transferedMoney += money;

    }

    public int getAmount() {
        return amount;
    }
    public String getInvalidReason() {
        return invalidReason;
    }

    public boolean isBuy() {
        return amount > 0;
    }
    public boolean isSell() {
        return amount < 0;
    }

    public void markAsProcessed() {
        if(!isBot)
            StockMarketMod.LOGGER.info("Order processed: " + toString());
        unlockLockedMoney();
        setStatus(Status.PROCESSED);
    }
    public void markAsInvalid(String reason) {
        invalidReason = reason;
        if(!isBot) {
            StockMarketMod.LOGGER.info("Order invalid: " + toString());

            PlayerUtilities.printToClientConsole(getPlayerUUID(), StockMarketTextMessages.getOrderInvalidMessage(reason));
        }
        unlockLockedMoney();
        setStatus(Status.INVALID);
    }
    public void markAsCancelled() {
        if(!isBot)
            StockMarketMod.LOGGER.info("Order canceled: " + toString());
        unlockLockedMoney();
        setStatus(Status.CANCELLED);
    }
    private void unlockLockedMoney()
    {
        BankUser user = ServerBankManager.getUser(playerUUID);
        if(user == null)
        {
            StockMarketMod.LOGGER.error("BankUser not found for player " + ServerPlayerList.getPlayerName(playerUUID));
            return;
        }
        Bank moneyBank = user.getMoneyBank();
        Bank itemBank = user.getBank(itemID);
        if(moneyBank == null)
        {
            StockMarketMod.LOGGER.error("MoneyBank not found for player " + ServerPlayerList.getPlayerName(playerUUID));
            return;
        }
        if(itemBank == null)
        {
            StockMarketMod.LOGGER.error("ItemBank not found for player " + ServerPlayerList.getPlayerName(playerUUID));
            return;
        }


        if(this instanceof LimitOrder limitOrder)
        {
            if(limitOrder.isBuy())
                moneyBank.unlockAmount(Math.max(0,limitOrder.getLockedMoney() - Math.abs(limitOrder.getTransferedMoney())));
            else
                itemBank.unlockAmount(Math.abs(limitOrder.getAmount()-limitOrder.getFilledAmount()));

        }
        else if(this instanceof  MarketOrder marketOrder)
        {
            if(marketOrder.isBuy()) {
                long amount = marketOrder.getLockedMoney() - Math.abs(marketOrder.getTransferedMoney());
                if(amount > 0)
                    moneyBank.unlockAmount(amount);
            }
            else
                itemBank.unlockAmount(Math.abs(marketOrder.getAmount())-marketOrder.getFilledAmount());
        }
    }

    public void setStatus(Status status) {
        if(status == this.status)
            return;
        this.status = status;
        if(/*StockMarketMod.isServer() && */!isBot)
        {
            SyncOrderPacket.sendResponse(this);
        }
    }

    public void notifyPlayer() {
        if(isBot)
            return;
        SyncOrderPacket.sendResponse(this);
    }

    public Status getStatus() {
        return status;
    }

    public int getAveragePrice() {
        return this.filledAmount == 0 ? 0 : (int)(Math.round(Math.abs((double)this.transferedMoney / this.filledAmount)));
    }

    public boolean isFilled() {
        return Math.abs(filledAmount) >= Math.abs(amount);
    }

    public void addFilledAmount(int amount) {
        filledAmount += amount;

    }
    public int getFilledAmount() {
        return filledAmount;
    }

    public abstract String toString();

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(orderID);
        buf.writeUtf(itemID);
        buf.writeUUID(playerUUID);
        buf.writeInt(amount);
        buf.writeInt(filledAmount);
        buf.writeLong(lockedMoney);
        buf.writeLong(transferedMoney);
        buf.writeUtf(status.toString());
        buf.writeUtf(invalidReason);
        buf.writeBoolean(isBot);
    }

}
