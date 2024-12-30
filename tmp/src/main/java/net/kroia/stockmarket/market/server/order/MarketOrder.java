package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MarketOrder extends Order {
    private long lockedMoney = 0;

    public static MarketOrder create(ServerPlayer player, String itemID, int amount)
    {
        int currentPrice = ServerMarket.getPrice(itemID);
        if(Order.tryReserveBankFund(player, itemID, amount, currentPrice)) {
            MarketOrder order = new MarketOrder(player.getUUID(), itemID, amount);
            if(amount > 0)
                order.lockedMoney = Math.abs(amount) * currentPrice;
            return order;
        }
        return null;
    }
    public static MarketOrder createBotOrder(UUID playerUUID, Bank botMoneyBank, Bank botItemBank, String itemID, int amount)
    {
        int currentPrice = ServerMarket.getPrice(itemID);
        if(Order.tryReserveBankFund(botMoneyBank, botItemBank, playerUUID, itemID, amount, currentPrice, null)){
            MarketOrder order = new MarketOrder(playerUUID, itemID, amount, true);
            if(amount > 0)
                order.lockedMoney = Math.abs(amount) * currentPrice;
            return order;
        }
        return null;
    }
    protected MarketOrder(UUID playerUUID, String itemID, int amount) {
        super(playerUUID, itemID, amount);

        //StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
    }
    protected MarketOrder(UUID playerUUID, String itemID, int amount, boolean isBot) {
        super(playerUUID, itemID, amount, isBot);

        //StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
    }

    public MarketOrder(FriendlyByteBuf buf)
    {
        super(buf);
    }


    public long getLockedMoney() {
        return lockedMoney;
    }


    @Override
    boolean isEqual(Order other)
    {
        if(other instanceof MarketOrder)
        {
            MarketOrder otherMarketOrder = (MarketOrder) other;
            return super.isEqual(other);
        }
        return false;
    }


    @Override
    public String toString() {
        String playerName = ServerPlayerList.getPlayerName(playerUUID);
        if(playerName.isEmpty())
            playerName = playerUUID.toString();
        return "MarketOrder{\n  Owner: " + playerName +
                "\n  OrderID: " + orderID +
                "\n  Amount: " + amount +
                "\n  Filled: " + filledAmount +
                "\n  AveragePrice: " + getAveragePrice() +
                "\n  TransferedMoney: $" + transferedMoney +
                "\n  Status:" + status+
                (status==Status.INVALID?" Invalid reason: \n    "+invalidReason:"")+"\n}";
    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        Type type = Type.MARKET;
        buf.writeUtf(type.toString());
        super.toBytes(buf);
    }

    @Override
    public void copyFrom(Order other) {
        super.copyFrom(other);
        if(other instanceof MarketOrder)
        {
            MarketOrder otherMarketOrder = (MarketOrder) other;
        }
    }
}
