package net.kroia.stockmarket.networking.packet.server_sender.update;


import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.UUID;

public class SyncPricePacket extends NetworkPacket {
    private PriceHistory priceHistory;
    private OrderbookVolume orderBookVolume;
    private int minPrice;
    private int maxPrice;

    private ArrayList<Order> orders;


    public SyncPricePacket() {
        super();

    }
    public SyncPricePacket(PriceHistory priceHistory, OrderbookVolume orderBookVolume, int minPrice, int maxPrice, ArrayList<Order> orders) {
        super();
        this.priceHistory = priceHistory;
        this.orderBookVolume = orderBookVolume;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.orders = orders;
    }

    public SyncPricePacket(FriendlyByteBuf buf) {
        super(buf);
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

        int tiles = StockMarketModSettings.UI.MAX_ORDERBOOK_TILES;
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
        StockMarketNetworking.sendToClient(player, packet);
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

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        priceHistory.toBytes(buf);
        orderBookVolume.toBytes(buf);
        buf.writeInt(minPrice);
        buf.writeInt(maxPrice);

        buf.writeInt(orders.size());
        orders.forEach(order -> {
            order.toBytes(buf);
        });
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
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
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}