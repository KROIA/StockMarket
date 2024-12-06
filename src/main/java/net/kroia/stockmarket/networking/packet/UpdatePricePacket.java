package net.kroia.stockmarket.networking.packet;


import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.Market;
import net.kroia.stockmarket.market.MarketData;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.screen.custom.TradeScreen;
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
    ArrayList<Integer> orderBookVolume;

    public UpdatePricePacket() {

    }
    /*public UpdatePricePacket(String itemID, int price) {
        this.itemID = itemID;
        this.price = price;
    }*/
    public UpdatePricePacket(PriceHistory priceHistory, ArrayList<Integer> orderBookVolume) {
        this.priceHistory = priceHistory;
        this.orderBookVolume = orderBookVolume;
    }

    public UpdatePricePacket(FriendlyByteBuf buf) {
        priceHistory = new PriceHistory(buf);
        orderBookVolume = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            orderBookVolume.add(buf.readInt());
        }
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
        ArrayList<Integer> orderBookVolume = Market.getOrderBookVolume(itemID, 20, 0, 100);
        ModMessages.sendToPlayer(new UpdatePricePacket(Market.getPriceHistory(itemID), orderBookVolume), player);
    }

    public void toBytes(FriendlyByteBuf buf) {
        priceHistory.toBytes(buf);
        buf.writeInt(orderBookVolume.size());
        orderBookVolume.forEach(buf::writeInt);

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
            Market.setPriceHistory(priceHistory);
            TradeScreen.setOrderBookVolume(orderBookVolume);
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