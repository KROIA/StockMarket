package net.kroia.stockmarket.market.server.order;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.MoneyBank;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class MarketOrder extends Order {
    private long lockedMoney = 0;

    public static MarketOrder create(ServerPlayer player, String itemID, int amount)
    {
        int currentPrice = ServerMarket.getPrice(itemID);
        if(Order.tryReserveBankFund(player, amount, currentPrice)) {
            MarketOrder order = new MarketOrder(player.getUUID().toString(), itemID, amount);
            if(amount > 0)
                order.lockedMoney = Math.abs(amount) * currentPrice;
            return order;
        }
        return null;
    }
    public static MarketOrder createBotOrder(String uuid, Bank botBank, String itemID, int amount)
    {
        int currentPrice = ServerMarket.getPrice(itemID);
        if(Order.tryReserveBankFund(botBank, uuid, amount, currentPrice)){
            MarketOrder order = new MarketOrder(uuid, itemID, amount, true);
            if(amount > 0)
                order.lockedMoney = Math.abs(amount) * currentPrice;
            return order;
        }
        return null;
    }
    protected MarketOrder(String playerUUID, String itemID, int amount) {
        super(playerUUID, itemID, amount);

        //StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
    }
    protected MarketOrder(String playerUUID, String itemID, int amount, boolean isBot) {
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
        String playerName;
        if(this.isBot) {
            playerName = playerUUID;
        }else {
            ServerPlayer player = StockMarketMod.getPlayerByUUID(playerUUID);
            playerName = player == null ? "UUID:" + playerUUID : player.getName().getString();
        }
        return "MarketOrder{\n  Owner: " + playerName +
                "\n  Amount: " + amount +
                "\n  Filled: " + filledAmount +
                "\n  AveragePrice: " + averagePrice +
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
