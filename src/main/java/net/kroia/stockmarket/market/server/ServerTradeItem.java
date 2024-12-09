package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.market.client.ClientTradeItem;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public class ServerTradeItem implements ServerSaveable {
    private String itemID;
    private final PriceHistory priceHistory;
    private final ArrayList<ServerPlayer> subscribers = new ArrayList<>();
    private final MarketManager marketManager;

    public ServerTradeItem(String itemID, int startPrice)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, startPrice);
        this.marketManager = new MarketManager(itemID, startPrice, priceHistory);
    }

    public ServerTradeItem(CompoundTag tag)
    {
        this.priceHistory = new PriceHistory("", 0);
        this.marketManager = new MarketManager("", 0, priceHistory);
        load(tag);
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
    public void getOrders(String playerUUID, ArrayList<Order> orders)
    {
        marketManager.getOrders(playerUUID, orders);
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

    public void updateBot()
    {
        marketManager.updateBot();
        notifySubscribers();
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("itemID", itemID);
        CompoundTag matchingEngineTag = new CompoundTag();
        marketManager.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);

        CompoundTag priceHistoryTag = new CompoundTag();
        priceHistory.save(priceHistoryTag);
        tag.put("priceHistory", priceHistoryTag);
    }

    @Override
    public void load(CompoundTag tag) {
        itemID = tag.getString("itemID");
        marketManager.load(tag.getCompound("matchingEngine"));

        priceHistory.load(tag.getCompound("priceHistory"));



        marketManager.setItemID(itemID);
        priceHistory.setItemID(itemID);



    }
}
