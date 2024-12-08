package net.kroia.stockmarket.networking.packet;


import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public class UpdatePricePacket {
    //private String itemID;
    //private int price;
    PriceHistory priceHistory;
    OrderbookVolume orderBookVolume;

    public UpdatePricePacket() {

    }
    /*public UpdatePricePacket(String itemID, int price) {
        this.itemID = itemID;
        this.price = price;
    }*/
    public UpdatePricePacket(PriceHistory priceHistory, OrderbookVolume orderBookVolume) {
        this.priceHistory = priceHistory;
        this.orderBookVolume = orderBookVolume;
    }

    public UpdatePricePacket(FriendlyByteBuf buf) {
        priceHistory = new PriceHistory(buf);
        orderBookVolume = new OrderbookVolume(buf);
        //this.itemID = buf.readUtf();
        //this.price = buf.readInt();
        /*int size = buf.readInt();
        this.prices = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String itemName = buf.readUtf();
            // Get Item from item name
            Integer price = buf.readInt();
            prices.put(itemName, price);
        }*/
    }

    public static void sendPacket(String itemID, ServerPlayer player)
    {
        if(!ServerMarket.hasItem(itemID))
        {
            StockMarketMod.LOGGER.warn("Item not found: " + itemID);
            return;
        }
        OrderbookVolume orderBookVolume = ServerMarket.getOrderBookVolume(itemID, 20, 0, 100);
        ModMessages.sendToPlayer(new UpdatePricePacket(ServerMarket.getPriceHistory(itemID), orderBookVolume), player);
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }
    public OrderbookVolume getOrderBookVolume() {
        return orderBookVolume;
    }

    public void toBytes(FriendlyByteBuf buf) {
        priceHistory.toBytes(buf);
        orderBookVolume.toBytes(buf);

        //buf.writeUtf(itemID);
        //buf.writeInt(price);
        /*buf.writeInt(prices.size());
        prices.forEach((itemName, price) -> {
            buf.writeUtf(itemName);
            buf.writeInt(price);
        });*/
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server");
            // HERE WE ARE ON THE CLIENT!
            // Update client-side data
            // Get the data from the packet
            //MarketData.setPrice(this.itemID, this.price);
            //ClientMarket.setPriceHistory(priceHistory);
            //TradeScreen.setOrderBookVolume(orderBookVolume);
            //TradeScreen.updatePlotsData();
            ClientMarket.handlePacket(this);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data

        });
        context.setPacketHandled(true);
    }
}