package net.kroia.stockmarket.market.server.order;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class MarketOrder extends Order {



    MarketOrder(UUID playerUUID, int bankAccountNumber, long amount, long lockedMoney) {
        super(playerUUID, bankAccountNumber, amount, lockedMoney);
    }
    MarketOrder(long amount) {
        super(amount);
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
    public void encode(FriendlyByteBuf buf)
    {
        Type type = Type.MARKET;
        buf.writeUtf(type.toString());
        super.encode(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        super.decode(buf);
    }

    @Override
    public void copyFrom(Order other) {
        super.copyFrom(other);
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = (JsonObject) super.toJson();
        json.addProperty("type", Type.MARKET.toString());
        return json;
    }


    @Override
    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[MarketOrder] " + msg);
    }
    @Override
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[MarketOrder] " + msg);
    }
    @Override
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[MarketOrder] " + msg, e);
    }
    @Override
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[MarketOrder] " + msg);
    }
    @Override
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[MarketOrder] " + msg);
    }
}
