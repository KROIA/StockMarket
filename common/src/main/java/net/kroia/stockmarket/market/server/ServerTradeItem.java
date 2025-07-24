package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.banksystem.util.ItemID;
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
    private ItemID itemID;
    private ItemID currencyItemID;
    private final PriceHistory priceHistory;
    private final ArrayList<ServerPlayer> subscribers = new ArrayList<>();
    private final MarketManager marketManager;

    private long lastMillis = System.currentTimeMillis();
    protected long updateTimerIntervallMS = 100;

    private boolean enabled = true;


    public ServerTradeItem(ItemID itemID, ItemID currencyItemID, int startPrice)
    {
        this.itemID = itemID;
        this.currencyItemID = currencyItemID;
        this.priceHistory = new PriceHistory(itemID, currencyItemID, startPrice);
        this.marketManager = new MarketManager(this, startPrice, priceHistory);
    }

    private ServerTradeItem()
    {
        this.priceHistory = new PriceHistory(null, null,0);
        this.marketManager = new MarketManager(this, 0, priceHistory);
    }
    public void cleanup()
    {
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
/*
    public void setUpdateInterval(long intervalMillis)
    {
        updateTimerIntervallMS = intervalMillis;
    }
    public long getUpdateInterval()
    {
        return updateTimerIntervallMS;
    }
*/
    public ItemID getItemID()
    {
        return itemID;
    }

    public PriceHistory getPriceHistory()
    {
        return priceHistory;
    }
    public void resetPriceChart()
    {
        priceHistory.clear(marketManager.getCurrentPrice());
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
        //long startMillis = System.currentTimeMillis();
        CompoundTag itemTag = new CompoundTag();
        success &= itemID.save(itemTag);
        tag.put("itemID", itemTag);

        CompoundTag currencyItemTag = new CompoundTag();
        success &= currencyItemID.save(currencyItemTag);
        tag.put("currencyItemID", currencyItemTag);

        CompoundTag matchingEngineTag = new CompoundTag();
        success &= marketManager.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);
        //long matchingEngineMillis = System.currentTimeMillis();
        CompoundTag priceHistoryTag = new CompoundTag();
        success &= priceHistory.save(priceHistoryTag);
        tag.put("priceHistory", priceHistoryTag);

        //long endMillis = System.currentTimeMillis();
        //StockMarketMod.LOGGER.info("[SERVER] Saving ServerMarket item: "+itemID + " " +(endMillis-startMillis)+"ms matching engine part: "+(matchingEngineMillis-startMillis)+"ms price history part: "+(endMillis-matchingEngineMillis)+"ms");
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("itemID") ||
                !tag.contains("currencyItemID") ||
                !tag.contains("matchingEngine") ||
                !tag.contains("priceHistory"))
            return false;
        boolean success = true;

        String oldItemID = tag.getString("itemID");
        if(oldItemID.compareTo("")==0)
        {
            if(itemID == null)
            {
                itemID = new ItemID(tag.getCompound("itemID"));
            }
            else
                success = itemID.load(tag.getCompound("itemID"));
        }
        else {
            itemID = new ItemID(oldItemID);
        }

        currencyItemID = new ItemID(tag.getCompound("currencyItemID"));
        marketManager.load(tag.getCompound("matchingEngine"));

        priceHistory.load(tag.getCompound("priceHistory"));
        marketManager.setItemID(itemID);
        priceHistory.setItemID(itemID);
        priceHistory.setCurrencyItemID(currencyItemID);

        return success;
    }

    public void onServerTick(MinecraftServer server) {
        if(!enabled)
            return;

        this.marketManager.onServerTick(server);

        long currentTime = System.currentTimeMillis();
        if(currentTime - lastMillis > updateTimerIntervallMS) {
            lastMillis = currentTime;
            if(!subscribers.isEmpty())
                notifySubscribers();
        }
    }
}
