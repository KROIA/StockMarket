package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.server.GhostOrderBook;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

/**
 * The ServerTradingBot simulates buy and sell orders to provide liquidity to the market.
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {

    public static class Settings implements ServerSaveable
    {
        public boolean enabled = true;
        public long updateTimerIntervallMS = StockMarketModSettings.MarketBot.UPDATE_TIMER_INTERVAL_MS;
        public int defaultPrice;
        public float orderBookVolumeScale = 100f;
        public float nearMarketVolumeStrength = 2f;
        public float volumeAccumulationRate = 0.01f;
        public float volumeFastAccumulationRate = 0.5f;
        public float volumeDecumulationRate = 0.005f;

        @Override
        public boolean save(CompoundTag tag) {
            tag.putBoolean("enabled", enabled);
            tag.putInt("defaultPrice", defaultPrice);
            tag.putFloat("orderBookVolumeScale", orderBookVolumeScale);
            tag.putFloat("nearMarketVolumeStrength", nearMarketVolumeStrength);
            tag.putFloat("volumeAccumulationRate", volumeAccumulationRate);
            tag.putFloat("volumeFastAccumulationRate", volumeFastAccumulationRate);
            tag.putFloat("volumeDecumulationRate", volumeDecumulationRate);
            tag.putLong("updateTimerIntervallMS", updateTimerIntervallMS);
            return false;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            enabled = tag.getBoolean("enabled");
            defaultPrice = tag.getInt("defaultPrice");
            orderBookVolumeScale = tag.getFloat("orderBookVolumeScale");
            nearMarketVolumeStrength = tag.getFloat("nearMarketVolumeStrength");
            volumeAccumulationRate = tag.getFloat("volumeAccumulationRate");
            volumeFastAccumulationRate = tag.getFloat("volumeFastAccumulationRate");
            volumeDecumulationRate = tag.getFloat("volumeDecumulationRate");
            updateTimerIntervallMS = tag.getLong("updateTimerIntervallMS");
            return true;
        }

        public void copyFrom(Settings settings)
        {
            this.enabled = settings.enabled;
            this.defaultPrice = settings.defaultPrice;
            this.orderBookVolumeScale = settings.orderBookVolumeScale;
            this.nearMarketVolumeStrength = settings.nearMarketVolumeStrength;
            this.volumeAccumulationRate = settings.volumeAccumulationRate;
            this.volumeFastAccumulationRate = settings.volumeFastAccumulationRate;
            this.volumeDecumulationRate = settings.volumeDecumulationRate;
            this.updateTimerIntervallMS = settings.updateTimerIntervallMS;
        }
    }
    protected Settings settings;
    private MatchingEngine matchingEngine;
    MarketManager parent;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();
    private long[] tmp_load_buyOrderIDs = null;
    private long[] tmp_load_sellOrderIDs = null;


    private long lastMillis = 0;

    public ServerTradingBot() {
        settings = new Settings();
    }
    protected ServerTradingBot(Settings settings) {
        this.settings = settings;
    }

    public void setSettings(Settings settings)
    {
        this.settings = settings;
        if(settings == null)
            return;
        if(matchingEngine != null)
        {
            GhostOrderBook orderBook = matchingEngine.getGhostOrderBook();
            if(orderBook != null)
            {
                orderBook.setVolumeScale(settings.orderBookVolumeScale);
                orderBook.setNearMarketVolumeStrength(settings.nearMarketVolumeStrength);
                orderBook.setVolumeAccumulationRate(settings.volumeAccumulationRate);
                orderBook.setVolumeFastAccumulationRate(settings.volumeFastAccumulationRate);
                orderBook.setVolumeDecumulationRate(settings.volumeDecumulationRate);
            }
        }
    }
    public Settings getSettings()
    {
        if(matchingEngine != null && settings != null) {
            GhostOrderBook orderBook = matchingEngine.getGhostOrderBook();
            if (orderBook != null) {
                settings.orderBookVolumeScale = orderBook.getVolumeScale();
                settings.nearMarketVolumeStrength = orderBook.getNearMarketVolumeStrength();
                settings.volumeAccumulationRate = orderBook.getVolumeAccumulationRate();
                settings.volumeFastAccumulationRate = orderBook.getVolumeFastAccumulationRate();
                settings.volumeDecumulationRate = orderBook.getVolumeDecumulationRate();
            }
        }
        return this.settings;
    }


    public void setParent(MarketManager parent)
    {
        this.parent = parent;
    }
    public MarketManager getParent()
    {
        return this.parent;
    }

    protected void update()
    {
        clearOrders();
        createOrders();
    }

    protected MatchingEngine getMatchingEngine()
    {
        return matchingEngine;
    }
    public void setMatchingEngine(MatchingEngine matchingEngine)
    {
        this.matchingEngine = matchingEngine;
        if(tmp_load_buyOrderIDs != null)
        {
            PriorityQueue<LimitOrder> orders = matchingEngine.getBuyOrders();
            HashMap<Long, LimitOrder> orderMap = new HashMap<>();
            for(LimitOrder order : orders)
            {
                orderMap.put(order.getOrderID(), order);
            }
            for(long orderID : tmp_load_buyOrderIDs)
            {
                LimitOrder order = orderMap.get(orderID);
                if(order == null)
                    continue;
                buyOrders.add(order);
            }
            tmp_load_buyOrderIDs = null;
        }

        if(tmp_load_sellOrderIDs != null)
        {
            PriorityQueue<LimitOrder> orders = matchingEngine.getSellOrders();
            HashMap<Long, LimitOrder> orderMap = new HashMap<>();
            for(LimitOrder order : orders)
            {
                orderMap.put(order.getOrderID(), order);
            }
            for(long orderID : tmp_load_sellOrderIDs)
            {
                LimitOrder order = orderMap.get(orderID);
                if(order == null)
                    continue;
                sellOrders.add(order);
            }
            tmp_load_sellOrderIDs = null;
        }
    }
/*
    public void setMaxOrderCount(int maxOrderCount)
    {
        this.settings.maxOrderCount = maxOrderCount;
    }
    public int getMaxOrderCount()
    {
        return this.settings.maxOrderCount;
    }

    public void setVolumeScale(double volumeScale) {
        this.settings.volumeScale = volumeScale;
    }
    public double getVolumeScale() {
        return this.settings.volumeScale;
    }
    public void setVolumeSpread(double volumeSpread) {
        this.settings.volumeSpread = volumeSpread;
    }
    public double getVolumeSpread() {
        return this.settings.volumeSpread;
    }
    public void setVolumeRandomness(double volumeRandomness) {
        this.settings.volumeRandomness = volumeRandomness;
    }
    public double getVolumeRandomness() {
        return this.settings.volumeRandomness;
    }*/

    public void setUpdateInterval(long intervalMillis)
    {
        this.settings.updateTimerIntervallMS = intervalMillis;
    }
    public long getUpdateInterval()
    {
        return this.settings.updateTimerIntervallMS;
    }
    public void setEnabled(boolean enabled)
    {
        this.settings.enabled = enabled;
    }
    public boolean isEnabled()
    {
        return this.settings.enabled;
    }
    public ItemID getItemID()
    {
        return parent.getItemID();
    }




    public void clearOrders()
    {
        matchingEngine.removeBuyOrder_internal(buyOrders);
        matchingEngine.removeSellOrder_internal(sellOrders);

        for(LimitOrder order : buyOrders)
        {
            order.markAsCancelled();
        }
        for(LimitOrder order : sellOrders)
        {
            order.markAsCancelled();
        }
        buyOrders.clear();
        sellOrders.clear();
    }

    protected void createOrders()
    {

    }

    protected int getMarketVolume(int price)
    {
        if(matchingEngine == null)
            return 0;
        return matchingEngine.getVolume(price);
    }
    protected int getMarketVolume(int minPrice, int maxPrice)
    {
        if(matchingEngine == null)
            return 0;
        return matchingEngine.getVolume(minPrice, maxPrice);
    }

    protected int getCurrentPrice()
    {
        if(matchingEngine == null)
            return 0;
        return matchingEngine.getPrice();
    }

    protected boolean buyLimit(int volume, int price)
    {
        if(volume <= 0 || price < 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        LimitOrder buyOrder = LimitOrder.createBotOrder(itemID, volume, price);
        if(buyOrder != null)
        {
            matchingEngine.addOrder(buyOrder);
            buyOrders.add(buyOrder);
            return true;
        }
        return false;
    }
    protected boolean sellLimit(int volume, int price)
    {
        if(volume <= 0 || price < 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        LimitOrder sellOrder = LimitOrder.createBotOrder(itemID, -volume, price);
        if(sellOrder != null)
        {
            matchingEngine.addOrder(sellOrder);
            sellOrders.add(sellOrder);
            return true;
        }
        return false;
    }
    protected boolean limitTrade(int volume, int price)
    {
        if(volume == 0 || price < 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        LimitOrder order = LimitOrder.createBotOrder(itemID, volume, price);
        matchingEngine.addOrder(order);
        if(volume > 0)
            buyOrders.add(order);
        else
            sellOrders.add(order);
        return true;
    }
    protected boolean buyMarket(int volume)
    {
        if(volume <= 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        MarketOrder buyOrder = MarketOrder.createBotOrder(itemID, volume);
        matchingEngine.addOrder(buyOrder);
        return true;
    }
    protected boolean sellMarket(int volume)
    {
        if(volume == 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        MarketOrder sellOrder = MarketOrder.createBotOrder(itemID, volume);
        matchingEngine.addOrder(sellOrder);
        return true;
    }
    protected boolean marketTrade(int volume)
    {
        if(volume == 0 || matchingEngine == null)
            return false;
        ItemID itemID = parent.getItemID();
        MarketOrder order = MarketOrder.createBotOrder(itemID, volume);
        if(order != null)
        {
            matchingEngine.addOrder(order);
            return true;
        }
        return false;
    }

    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag settingsTag = new CompoundTag();
        settings.save(settingsTag);
        tag.putString("class", this.getClass().getSimpleName());
        tag.put("settings", settingsTag);

        // Save order ids
        long[] buyOrderIDs = new long[buyOrders.size()];
        for(int i=0; i<buyOrders.size(); i++)
        {
            buyOrderIDs[i] = buyOrders.get(i).getOrderID();
        }
        tag.putLongArray("buyOrderIDs", buyOrderIDs);

        long[] sellOrderIDs = new long[sellOrders.size()];
        for(int i=0; i<sellOrders.size(); i++)
        {
            sellOrderIDs[i] = sellOrders.get(i).getOrderID();
        }
        tag.putLongArray("sellOrderIDs", sellOrderIDs);

        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {

        if(tag == null)
            return false;
        if(!tag.contains("settings"))
            return false;
        CompoundTag settingsTag = tag.getCompound("settings");
        settings.load(settingsTag);

        if(tag.contains("buyOrderIDs"))
            tmp_load_buyOrderIDs = tag.getLongArray("buyOrderIDs");
        if(tag.contains("sellOrderIDs"))
            tmp_load_sellOrderIDs = tag.getLongArray("sellOrderIDs");



        setSettings(settings);
        return true;
    }

    public void onServerTick(MinecraftServer server) {
        if(!this.settings.enabled || matchingEngine == null || parent == null)
            return;
        long currentTime = System.currentTimeMillis();

        if(currentTime - lastMillis > this.settings.updateTimerIntervallMS) {
            lastMillis = currentTime;
            update();
        }
    }
}
