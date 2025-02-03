package net.kroia.stockmarket.market.server;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.UUID;

public class ServerTradeItem implements ServerSaveable {
    private String itemID;
    private final PriceHistory priceHistory;
    private final ArrayList<ServerPlayer> subscribers = new ArrayList<>();
    private final MarketManager marketManager;

    private long lastMillis = 0;
    protected long updateTimerIntervallMS = 100;

    private boolean enabled = true;


    public ServerTradeItem(String itemID, int startPrice)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, startPrice);
        this.marketManager = new MarketManager(this, startPrice, priceHistory);

        TickEvent.SERVER_POST.register(this::onServerTick);
    }

    private ServerTradeItem()
    {
        this.priceHistory = new PriceHistory("", 0);
        this.marketManager = new MarketManager(this, 0, priceHistory);

        TickEvent.SERVER_POST.register(this::onServerTick);
    }
    public void cleanup()
    {
        TickEvent.SERVER_POST.unregister(this::onServerTick);
        enabled = false;
        removeTradingBot();
        clear();
    }
    public static ServerTradeItem loadFromTag(CompoundTag tag)
    {
        ServerTradeItem item = new ServerTradeItem();
        if(item.load(tag))
            return item;
        return null;
    }

    public void clear()
    {
        marketManager.clear();
    }

    public void setTradingBot(ServerTradingBot bot)
    {
        marketManager.setTradingBot(bot);
    }
    public void removeTradingBot()
    {
        marketManager.removeTradingBot();
    }
    public boolean hasTradingBot()
    {
        return marketManager.hasTradingBot();
    }
    public ServerTradingBot getTradingBot()
    {
        return marketManager.getTradingBot();
    }

    public void setUpdateInterval(long intervalMillis)
    {
        updateTimerIntervallMS = intervalMillis;
    }
    public long getUpdateInterval()
    {
        return updateTimerIntervallMS;
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
    public void getOrders(UUID playerUUID, ArrayList<Order> orders)
    {
        marketManager.getOrders(playerUUID, orders);
    }

    public void setMarketOpen(boolean open)
    {
        marketManager.setMarketOpen(open);
    }
    public boolean isMarketOpen()
    {
        return marketManager.isMarketOpen();
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

    public void cancelAllOrders(UUID playerUUID)
    {
        marketManager.cancelAllOrders(playerUUID);
        Bank itemBank = ServerBankManager.getUser(playerUUID).getBank(itemID);
        if(itemBank != null)
            itemBank.unlockAll();
        notifySubscribers();
    }
    public boolean changeOrderPrice(long orderID, int newPrice)
    {
        if(marketManager.changeOrderPrice(orderID, newPrice)) {
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

    public void onServerTick(MinecraftServer server) {
        if(subscribers.isEmpty() || !enabled)
            return;

        long currentTime = System.currentTimeMillis();
        if(currentTime - lastMillis > updateTimerIntervallMS) {
            lastMillis = currentTime;
            notifySubscribers();
        }
    }
}
