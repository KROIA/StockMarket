package net.kroia.stockmarket.networking.packet.server_sender.update;


import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncPricePacket {
    //private String itemID;
    //private int price;
    private PriceHistory priceHistory;
    private OrderbookVolume orderBookVolume;
    private int minPrice;
    private int maxPrice;

    private ArrayList<Order> orders;


    public SyncPricePacket() {

    }
    /*public SyncPricePacket(String itemID, int price) {
        this.itemID = itemID;
        this.price = price;
    }*/
    public SyncPricePacket(PriceHistory priceHistory, OrderbookVolume orderBookVolume, int minPrice, int maxPrice, ArrayList<Order> orders) {
        this.priceHistory = priceHistory;
        this.orderBookVolume = orderBookVolume;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.orders = orders;
    }

    public SyncPricePacket(FriendlyByteBuf buf) {
        priceHistory = new PriceHistory(buf);
        orderBookVolume = new OrderbookVolume(buf);
        minPrice = buf.readInt();
        maxPrice = buf.readInt();

        int size = buf.readInt();
        orders = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Order order = Order.construct(buf);
            orders.add(order);
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
    public SyncPricePacket(String itemID)
    {
        commonSetup(itemID);
        this.orders = ServerMarket.getOrders(itemID);
    }
    public SyncPricePacket(String itemID, UUID playerUUID)
    {
        commonSetup(itemID);
        this.orders = ServerMarket.getOrders(itemID, playerUUID);
    }
    private void commonSetup(String itemID)
    {
        if(!ServerMarket.hasItem(itemID))
        {
            StockMarketMod.LOGGER.warn("Item not found: " + itemID);
            return;
        }
        PriceHistory history = ServerMarket.getPriceHistory(itemID);
        if(history == null)
        {
            StockMarketMod.LOGGER.warn("Price history not found: " + itemID);
            return;
        }
        int minPrice = history.getLowestPrice();
        int maxPrice = history.getHighestPrice();
        int range = (maxPrice - minPrice)/2;
        if(range < 10)
        {
            range = 10;
        }



        minPrice -= range;
        maxPrice += range;

        // Fllor to next 10
        minPrice = (minPrice / 10) * 10;
        maxPrice = (maxPrice / 10) * 10;

        minPrice = Math.max(0, minPrice);

        int tiles = ModSettings.UI.MAX_ORDERBOOK_TILES;
        if(maxPrice-minPrice < tiles)
        {
            tiles = maxPrice-minPrice;
        }



        OrderbookVolume orderBookVolume = ServerMarket.getOrderBookVolume(itemID, tiles, minPrice, maxPrice);
        this.priceHistory = history;
        this.orderBookVolume = orderBookVolume;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public static void sendPacket(String itemID, ServerPlayer player)
    {
        SyncPricePacket packet = new SyncPricePacket(itemID, player.getUUID());
        ModMessages.sendToPlayer(packet, player);
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }
    public OrderbookVolume getOrderBookVolume() {
        return orderBookVolume;
    }
    public int getMinPrice() {
        return minPrice;
    }
    public int getMaxPrice() {
        return maxPrice;
    }
    public ArrayList<Order> getOrders() {
        return orders;
    }

    public void toBytes(FriendlyByteBuf buf) {
        priceHistory.toBytes(buf);
        orderBookVolume.toBytes(buf);
        buf.writeInt(minPrice);
        buf.writeInt(maxPrice);

        buf.writeInt(orders.size());
        orders.forEach(order -> {
            order.toBytes(buf);
        });

        //buf.writeUtf(itemID);
        //buf.writeInt(price);
        /*buf.writeInt(prices.size());
        prices.forEach((itemName, price) -> {
            buf.writeUtf(itemName);
            buf.writeInt(price);
        });*/
    }

    public void handle(CustomPayloadEvent.Context context) {
        // Check if on server_sender or client
        if(context.isClientSide()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server_sender");
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