package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
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
        this.marketManager = new MarketManager(this, startPrice, priceHistory);
    }

    private ServerTradeItem()
    {
        this.priceHistory = new PriceHistory("", 0);
        this.marketManager = new MarketManager(this, 0, priceHistory);
    }
    public static ServerTradeItem loadFromTag(CompoundTag tag)
    {
        ServerTradeItem item = new ServerTradeItem();
        if(item.load(tag))
            return item;
        return null;
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

    public void notifySubscribers()
    {
        for(ServerPlayer player : subscribers)
        {
            SyncPricePacket.sendPacket(itemID, player);
        }
    }
    private void notifySubscriber(ServerPlayer player)
    {
        SyncPricePacket.sendPacket(itemID, player);
    }

    public void updateBot()
    {
        marketManager.updateBot();
        notifySubscribers();
    }

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        tag.putString("itemID", itemID);
        CompoundTag matchingEngineTag = new CompoundTag();
        success &= marketManager.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);

        CompoundTag priceHistoryTag = new CompoundTag();
        success &= priceHistory.save(priceHistoryTag);
        tag.put("priceHistory", priceHistoryTag);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("itemID") ||
                !tag.contains("matchingEngine") ||
                !tag.contains("priceHistory"))
            return false;

        itemID = tag.getString("itemID");
        marketManager.load(tag.getCompound("matchingEngine"));

        priceHistory.load(tag.getCompound("priceHistory"));
        marketManager.setItemID(itemID);
        priceHistory.setItemID(itemID);

        return !itemID.isEmpty();
    }
}
