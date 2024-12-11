package net.kroia.stockmarket.market.server.order;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.networking.packet.ResponseOrderPacket;
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
    protected String playerUUID;
    //private final String itemID;
    protected int amount;
    protected int filledAmount = 0;

    protected int averagePrice = 0;
    protected long transferedMoney = 0;

    protected String invalidReason = "";

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

    protected Order(String playerUUID, String itemID, int amount) {
        this.itemID = itemID;
        this.orderID = uniqueOrderID();
        this.playerUUID = playerUUID;
        //this.itemID = itemID;
        this.amount = amount;
    }
    protected Order(String playerUUID, String itemID, int amount, boolean isBot) {
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
            StockMarketMod.LOGGER.warn("BankUser not found for player " + player.getName().getString());
            StockMarketMod.printToClientConsole(player, "User "+player.getName().getString()+" not found does not own a bank");
            return false;
        }

        Bank moneyBank = bankUser.getMoneyBank();
        Bank itemBank = bankUser.getBank(itemID);

        return tryReserveBankFund(moneyBank,itemBank, player.getName().getString(), itemID, amount, price, player);
    }
    protected static boolean tryReserveBankFund(Bank moneyBank, Bank itemBank, String playerName, String itemID, int amount, int price, ServerPlayer dbgPlayer)
    {
        if(moneyBank == null)
        {
            StockMarketMod.LOGGER.warn("Bank not found for player " + playerName);
            if(dbgPlayer != null)
                StockMarketMod.printToClientConsole(dbgPlayer, "User "+playerName+" does not own a money bank");
            return false;
        }
        if(itemBank == null)
        {
            StockMarketMod.LOGGER.warn("User + " + playerName + " has no bank for item " + itemID);
            if(dbgPlayer != null)
                StockMarketMod.printToClientConsole(dbgPlayer, "User "+playerName+" does not own a bank for item "+itemID);
            return false;
        }
        if(amount > 0) {
            if (!moneyBank.lockAmount((long) price * amount)){
                StockMarketMod.LOGGER.warn("Insufficient funds for player " + playerName);
                if(dbgPlayer != null)
                    StockMarketMod.printToClientConsole(dbgPlayer, "Insufficient funds to buy "+amount+" "+itemID+" for $"+price+" each");
                return false;
            }
        }
        else {
            if (!itemBank.lockAmount(-amount)){
                StockMarketMod.LOGGER.warn("Insufficient items ("+itemID+") for player " + playerName);
                if(dbgPlayer != null)
                    StockMarketMod.printToClientConsole(dbgPlayer, "Insufficient items to sell "+-amount+" "+itemID);
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
        playerUUID = buf.readUtf();
        amount = buf.readInt();
        filledAmount = buf.readInt();
        transferedMoney = buf.readLong();
        averagePrice = buf.readInt();
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
        transferedMoney = other.transferedMoney;
        averagePrice = other.averagePrice;
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
                itemID.equals(other.itemID) &&
                playerUUID.compareTo(other.playerUUID)==0 &&
                amount == other.amount &&
                filledAmount == other.filledAmount &&
                transferedMoney == other.transferedMoney &&
                averagePrice == other.averagePrice &&
                status == other.status;
    }

    public String getPlayerUUID() {
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
        setStatus(Status.PROCESSED);
    }
    public void markAsInvalid(String reason) {
        invalidReason = reason;
        if(!isBot)
            StockMarketMod.LOGGER.info("Order invalid: " + toString());
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
        BankUser user = ServerBankManager.getUser(UUID.fromString(playerUUID));
        if(user == null)
        {
            StockMarketMod.LOGGER.error("BankUser not found for player " + playerUUID);
            return;
        }
        Bank moneyBank = user.getMoneyBank();
        Bank itemBank = user.getBank(itemID);
        if(moneyBank == null)
        {
            StockMarketMod.LOGGER.error("MoneyBank not found for player " + playerUUID);
            return;
        }
        if(itemBank == null)
        {
            StockMarketMod.LOGGER.error("ItemBank not found for player " + playerUUID);
            return;
        }


        if(this instanceof LimitOrder limitOrder)
        {
            if(limitOrder.isBuy())
                moneyBank.unlockAmount((long) limitOrder.getPrice() * Math.abs(limitOrder.getAmount()-limitOrder.getFilledAmount()));
            else
                itemBank.unlockAmount(Math.abs(limitOrder.getAmount()+limitOrder.getFilledAmount()));

        }
        else if(this instanceof  MarketOrder marketOrder)
        {
            if(marketOrder.isBuy())
                moneyBank.unlockAmount(marketOrder.getLockedMoney());
            else
                itemBank.unlockAmount(Math.abs(marketOrder.getAmount()+marketOrder.getFilledAmount()));
        }
    }

    public void setStatus(Status status) {
        if(status == this.status)
            return;
        this.status = status;
        if(StockMarketMod.isServer() && !isBot)
        {
            ResponseOrderPacket.sendResponse(this);
        }
    }

    public void notifyPlayer() {
        if(isBot)
            return;
        if(StockMarketMod.isServer())
        {
            ResponseOrderPacket.sendResponse(this);
        }
        else
        {
            StockMarketMod.LOGGER.error("notifyPlayer() called on client");
        }
    }

    public Status getStatus() {
        return status;
    }

    public int getAveragePrice() {
        return averagePrice;
    }
    public void setAveragePrice(int averagePrice) {
        this.averagePrice = averagePrice;
    }
    public void changeAveragePrice(int filledAmount, int fillPrice) {
        if(filledAmount == 0)
            return;
        int fillVolume = Math.abs(filledAmount);
        averagePrice = (fillVolume * averagePrice + Math.abs(filledAmount) * fillPrice) / fillVolume;
    }

    /**
     * Fills the order with the given amount.
     * @param amount The amount to fill the order with.
     * @return The remaining amount that could not be filled.
     */
    /*public int fill(int amount)
    {
        int fillAmount = this.amount - filledAmount;
        if(Math.abs(fillAmount) > Math.abs(amount))
            fillAmount = amount;
        filledAmount += fillAmount;
        if(filledAmount != 0 && status == Status.PENDING)
        {
            setStatus(Status.PARTIAL);
        }

        if(isFilled())
        {
            markAsProcessed();
        }
        return amount - fillAmount;
    }*/
    /*public int fill(Order other)
    {
        int fillAmount = amount - filledAmount;
        if(Math.abs(fillAmount) > Math.abs(other.amount))
            fillAmount = other.amount;
        filledAmount += fillAmount;
        if(filledAmount != 0 && status == Status.PENDING)
        {
            setStatus(Status.PARTIAL);
        }

        if(isFilled())
        {
            markAsProcessed();
        }
        return other.amount - fillAmount;
    }*/
    /*public static int fill(Order o1, Order o2, int currentPrice)
    {
        int fillAmount1 = o1.amount - o1.filledAmount;
        int fillAmount2 = o2.amount - o2.filledAmount;
        if(fillAmount1 > 0 && fillAmount2 > 0 || fillAmount1 < 0 && fillAmount2 < 0)
        {
            // same sign -> both buy or both sell
            return 0;
        }
        int fillVolume = Math.min(Math.abs(fillAmount1), Math.abs(fillAmount2));
        int fillAmount = fillVolume;
        if(fillAmount1 < 0)
            fillAmount = -fillVolume;

        long money = fillVolume * currentPrice;

        UUID playerUUID1 = UUID.fromString(o1.playerUUID);
        UUID playerUUID2 = UUID.fromString(o2.playerUUID);
        MoneyBank bank1 = ServerBankManager.getBank(playerUUID1);
        MoneyBank bank2 = ServerBankManager.getBank(playerUUID2);
        if(bank1 == null || bank2 == null)
        {
            StockMarketMod.LOGGER.error("Bank not found for player: " + o1.playerUUID + " or " + o2.playerUUID+
                    " Order1: " + o1 + " Order2: " + o2+
                    " Can't fill order");
            return 0;
        }

        UUID senderUUID = fillAmount > 0 ? playerUUID1 : playerUUID2;
        UUID receiverUUID = fillAmount > 0 ? playerUUID2 : playerUUID1;
        MoneyBank senderBank = fillAmount > 0 ? bank1 : bank2;
        MoneyBank receiverBank = fillAmount > 0 ? bank2 : bank1;
        Order senderOrder = fillAmount > 0 ? o1 : o2;
        Order receiverOrder = fillAmount > 0 ? o2 : o1;

        if(!senderBank.transferFromLockedPrefered(money, receiverBank))
        {
            long missingAmount = (money - senderBank.getBalance()-senderBank.getLockedBalance());
            StockMarketMod.LOGGER.error("Insufficient funds from player: " + senderUUID.toString()+
                    " Order1: " + senderOrder + " Order2: " + receiverOrder+
                    " Can't fill order");
            senderOrder.markAsInvalid("Insufficient funds");
            StockMarketMod.printToClientConsole(senderUUID, "Insufficient funds to consume order:\n  "+receiverOrder.toString()+
                    "\n  price: $"+currentPrice+
                    "\n  amount: "+fillVolume+
                    "\n  total cost: $"+money +
                    "\n  missing: $"+missingAmount);

            return 0;
        }
        else {
            senderOrder.filledAmount += fillVolume;
            senderOrder.addTransferedMoney(-money);
            receiverOrder.filledAmount -= fillVolume;
            receiverOrder.addTransferedMoney(money);

            if(senderOrder.isFilled())
                senderOrder.markAsProcessed();
            else if(senderOrder.status == Status.PENDING)
                senderOrder.setStatus(Status.PARTIAL);

            if(receiverOrder.isFilled())
                receiverOrder.markAsProcessed();
            else if(receiverOrder.status == Status.PENDING)
                receiverOrder.setStatus(Status.PARTIAL);
        }
        return fillVolume;
    }*/

    public boolean isFilled() {
        return Math.abs(filledAmount) >= Math.abs(amount);
    }

    public void addFilledAmount(int amount) {
        filledAmount += amount;
    }
    public int getFilledAmount() {
        return filledAmount;
    }

    // pure virtual function
    public abstract String toString();

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(orderID);
        buf.writeUtf(itemID);
        buf.writeUtf(playerUUID);
        buf.writeInt(amount);
        buf.writeInt(filledAmount);
        buf.writeLong(transferedMoney);
        buf.writeInt(averagePrice);
        buf.writeUtf(status.toString());
        buf.writeUtf(invalidReason);
        buf.writeBoolean(isBot);
    }

}
