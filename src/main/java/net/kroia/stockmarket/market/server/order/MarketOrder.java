package net.kroia.stockmarket.market.server.order;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MarketOrder extends Order {


    public MarketOrder(String playerUUID, String itemID, int amount) {
        super(playerUUID, itemID, amount);

        StockMarketMod.LOGGER.info("MarketOrder created: " + toString());
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
            MarketOrder otherMarketOrder = (MarketOrder) other;
            return super.isEqual(other);
        }
        return false;
    }


    @Override
    public String toString() {
        ServerPlayer player = StockMarketMod.getPlayerByUUID(playerUUID);
        String playerName = player == null ? "UUID:"+playerUUID : player.getName().getString();

        return "MarketOrder{ Owner: " + playerName + " Amount: " + amount + " Filled: " + filledAmount + " AveragePrice: " + averagePrice + " Status:" + status+
                (status==Status.INVALID?" Invalid reason: "+invalidReason:"")+" }";
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
