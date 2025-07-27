package net.kroia.stockmarket.market.server.order;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.server.ServerStockMarketManager;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MarketOrder extends Order {


    public static MarketOrder create(ServerPlayer player, ItemID itemID, ItemID currencyItemID, int amount)
    {
        int currentPrice = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getPrice(itemID);
        if(Order.tryReserveBankFund(player, itemID, amount, currentPrice)) {

            return new MarketOrder(player.getUUID(), itemID, currencyItemID, amount, currentPrice);
        }
        return null;
    }
    public static MarketOrder createBotOrder(ItemID itemID, ItemID currencyItemID, int amount)
    {
        int currentPrice = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getPrice(itemID);
        return new MarketOrder(null, itemID, currencyItemID, amount, currentPrice,true);
    }
    protected MarketOrder(UUID playerUUID, ItemID itemID, ItemID currencyItemID, int amount, int currentPrice) {
        super(playerUUID, itemID, currencyItemID, amount);
        if(amount > 0)
            this.lockedMoney = (long) Math.abs(amount) * currentPrice;
    }
    protected MarketOrder(UUID playerUUID, ItemID itemID, ItemID currencyItemID, int amount, int currentPrice, boolean isBot) {
        super(playerUUID, itemID, currencyItemID, amount, isBot);
        if(amount > 0)
            this.lockedMoney = (long) Math.abs(amount) * currentPrice;
    }

    public MarketOrder(FriendlyByteBuf buf)
    {
        super(buf);
    }




    @Override
    boolean isEqual(Order other)
    {
        if(other instanceof MarketOrder)
        {
            return super.isEqual(other);
        }
        return false;
    }


    @Override
    public String toString() {
        String playerName;
        if(playerUUID == null)
            playerName = "Bot";
        else
            playerName = ServerPlayerList.getPlayerName(playerUUID);
        if(playerName == null || playerName.isEmpty())
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
    }
}
