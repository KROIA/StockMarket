package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public class ServerTradeItem {
    private final String itemID;
    private final PriceHistory priceHistory;
    private final ArrayList<ServerPlayer> subscribers = new ArrayList<>();
    private final MarketManager marketManager;

    public ServerTradeItem(String itemID, int startPrice)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, startPrice);
        this.marketManager = new MarketManager(itemID, startPrice, priceHistory);
    }

    public String getItemID()
    {
        return itemID;
    }

    public PriceHistory getPriceHistory()
    {
        return priceHistory;
    }

    public void addSubscriber(ServerPlayer player)
    {
        // Check if player already exists
        for(ServerPlayer subscriber : subscribers)
        {
            if(subscriber.getUUID().equals(player.getUUID()))
            {
                return;
            }
        }
        subscribers.add(player);
        notifySubscriber(player);
    }

    public void removeSubscriber(ServerPlayer player)
    {
        subscribers.remove(player);
    }

    public ArrayList<ServerPlayer> getSubscribers()
    {
        return subscribers;
    }
    public ArrayList<Order> getOrders()
    {
        return marketManager.getOrders();
    }

    public void shiftPriceHistory()
    {
        marketManager.shiftPriceHistory();
        notifySubscribers();
    }

    public int getPrice()
    {
        return marketManager.getCurrentPrice();
    }

    public void addOrder(Order order)
    {
        marketManager.addOrder(order);
        notifySubscribers();
    }
    public boolean cancelOrder(long orderID)
    {
        if(marketManager.cancelOrder(orderID)) {
            notifySubscribers();
            return true;
        }
        return false;
    }

    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        return marketManager.getOrderBookVolume(tiles, minPrice, maxPrice);
    }

    private void notifySubscribers()
    {
        for(ServerPlayer player : subscribers)
        {
            UpdatePricePacket.sendPacket(itemID, player);
        }
    }
    private void notifySubscriber(ServerPlayer player)
    {
        UpdatePricePacket.sendPacket(itemID, player);
    }


}
