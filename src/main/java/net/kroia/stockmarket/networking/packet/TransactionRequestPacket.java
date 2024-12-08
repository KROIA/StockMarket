package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TransactionRequestPacket {

    private String itemID;
    private int amount;
    private int price;
    public enum OrderType {
        limit,
        market,
    }
    private OrderType orderType;

    public TransactionRequestPacket() {
        itemID = "";
        amount = 0;
    }
    public TransactionRequestPacket(String itemID, int amount) {
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = OrderType.market;
        this.price = 0;
    }
    public TransactionRequestPacket(String itemID, int amount, OrderType orderType, int price) {
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = orderType;
        this.price = price;
    }

    public TransactionRequestPacket(FriendlyByteBuf buf) {
        this.itemID = buf.readUtf();
        this.amount = buf.readInt();
        this.orderType = OrderType.valueOf(buf.readUtf());
        this.price = buf.readInt();
    }

    public static void generateRequest(String itemID, int amount, int price) {

        StockMarketMod.LOGGER.info("[CLIENT] Sending TransactionRequestPacket for item: "+itemID + " amount: "+amount);
        ModMessages.sendToServer(new TransactionRequestPacket(itemID, amount, OrderType.limit, price));
    }
    public static void generateRequest(String itemID, int amount) {
        StockMarketMod.LOGGER.info("[CLIENT] Sending TransactionRequestPacket for item: "+itemID + " amount: "+amount);
        ModMessages.sendToServer(new TransactionRequestPacket(itemID, amount));
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeUtf(orderType.name());
        buf.writeInt(price);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server");
            // HERE WE ARE ON THE CLIENT!
            // Update client-side data
            // Get the data from the packet
            //MarketData.setPrice(this.itemID, this.price);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            StockMarketMod.LOGGER.info("[SERVER] Receiving TransactionRequestPacket for item "+this.itemID+" from the player "+context.getSender().getName().getString());
            ServerPlayer player = context.getSender();
            if(this.amount < 0)
            {
                // Selling
                StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is selling "+this.amount+" of "+this.itemID);
            }
            else if(this.amount > 0)
            {
                // Buying
                StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is buying "+this.amount+" of "+this.itemID);
            }

            switch(this.orderType)
            {
                case limit:
                    LimitOrder limitOrder = new LimitOrder(player, amount, price);
                    ServerMarket.addOrder(itemID, limitOrder);
                    //StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is selling "+this.amount+" of "+this.itemID+" with a limit order");
                    break;
                case market:
                    MarketOrder marketOrder = new MarketOrder(player, amount);
                    ServerMarket.addOrder(itemID, marketOrder);
                    //StockMarketMod.LOGGER.info("[SERVER] Player "+context.getSender().getName().getString()+" is selling "+this.amount+" of "+this.itemID+" with a market order");
                    break;
            }

            // Send the packet to the client
            //UpdatePricePacket.sendPacket(itemID, player);

        });
        context.setPacketHandled(true);
    }
}
