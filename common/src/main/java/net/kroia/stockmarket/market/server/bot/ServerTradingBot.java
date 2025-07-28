package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.GhostOrderBook;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;

/**
 * The ServerTradingBot simulates buy and sell orders to generate a movement in the market if no players are trading
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public static class Settings implements ServerSaveable
    {
        public boolean enabled = true;
        public long updateTimerIntervallMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.UPDATE_TIMER_INTERVAL_MS.get();
        public int defaultPrice;
        public float orderBookVolumeScale = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.ORDER_BOOK_VOLUME_SCALE.get();
        public float nearMarketVolumeScale = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.NEAR_MARKET_VOLUME_SCALE.get();
        public float volumeAccumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_ACCUMULATION_RATE.get();
        public float volumeFastAccumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_FAST_ACCUMULATION_RATE.get();
        public float volumeDecumulationRate = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.VOLUME_DECUMULATION_RATE.get();

        @Override
        public boolean save(CompoundTag tag) {
            tag.putBoolean("enabled", enabled);
            tag.putInt("defaultPrice", defaultPrice);
            tag.putFloat("orderBookVolumeScale", orderBookVolumeScale);
            tag.putFloat("nearMarketVolumeScale", nearMarketVolumeScale);
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
            if(!tag.contains("enabled") ||
                    !tag.contains("defaultPrice") ||
                    !tag.contains("orderBookVolumeScale") ||
                    !tag.contains("nearMarketVolumeScale") ||
                    !tag.contains("volumeAccumulationRate") ||
                    !tag.contains("volumeFastAccumulationRate") ||
                    !tag.contains("volumeDecumulationRate") ||
                    !tag.contains("updateTimerIntervallMS"))
                return false;

            enabled = tag.getBoolean("enabled");
            defaultPrice = tag.getInt("defaultPrice");
            orderBookVolumeScale = tag.getFloat("orderBookVolumeScale");
            nearMarketVolumeScale = tag.getFloat("nearMarketVolumeScale");
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
            this.nearMarketVolumeScale = settings.nearMarketVolumeScale;
            this.volumeAccumulationRate = settings.volumeAccumulationRate;
            this.volumeFastAccumulationRate = settings.volumeFastAccumulationRate;
            this.volumeDecumulationRate = settings.volumeDecumulationRate;
            this.updateTimerIntervallMS = settings.updateTimerIntervallMS;
        }




    }

    private final ServerMarket serverMarket;

    protected Settings settings;
    //private MatchingEngine matchingEngine;
    //TradeManager parent;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();
    private long[] tmp_load_buyOrderIDs = null;
    private long[] tmp_load_sellOrderIDs = null;


    private long lastMillis = 0;

    public ServerTradingBot(ServerMarket serverMarket) {
        this.serverMarket = serverMarket;
        settings = new Settings();
    }
    protected ServerTradingBot(ServerMarket market, Settings settings) {
        this.serverMarket = market;
        this.settings = settings;
    }

    public void setSettings(Settings settings)
    {
        this.settings = settings;
        if(settings == null)
            return;
        GhostOrderBook orderBook = serverMarket.getOrderBook().getGhostOrderBook();
        if(orderBook != null)
        {
            orderBook.setVolumeScale(settings.orderBookVolumeScale);
            orderBook.setNearMarketVolumeScale(settings.nearMarketVolumeScale);
            orderBook.setVolumeAccumulationRate(settings.volumeAccumulationRate);
            orderBook.setVolumeFastAccumulationRate(settings.volumeFastAccumulationRate);
            orderBook.setVolumeDecumulationRate(settings.volumeDecumulationRate);
        }
    }
    public Settings getSettings()
    {
        if(serverMarket.getMatchingEngine() != null && settings != null) {
            GhostOrderBook orderBook = serverMarket.getOrderBook().getGhostOrderBook();
            if (orderBook != null) {
                settings.orderBookVolumeScale = orderBook.getVolumeScale();
                settings.nearMarketVolumeScale = orderBook.getNearMarketVolumeScale();
                settings.volumeAccumulationRate = orderBook.getVolumeAccumulationRate();
                settings.volumeFastAccumulationRate = orderBook.getVolumeFastAccumulationRate();
                settings.volumeDecumulationRate = orderBook.getVolumeDecumulationRate();
            }
        }
        return this.settings;
    }


    /*public void setParent(TradeManager parent)
    {
        this.parent = parent;
    }*/
    /*public TradeManager getParent()
    {
        return this.parent;
    }*/

    protected void update()
    {
        clearOrders();
        createOrders();
    }

   /* public void setMatchingEngine(MatchingEngine matchingEngine)
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
    /*public ItemID getItemID()
    {
        return parent.getItemID();
    }
*/



    public void clearOrders()
    {
        serverMarket.cancelAllBotOrders();
    }

    protected void createOrders()
    {

    }

    protected long getOrderBookVolume(int price)
    {
        return serverMarket.getOrderBook().getVolume(price, serverMarket.getCurrentPrice());
    }
    protected long getOrderBookVolume(int minPrice, int maxPrice)
    {
        return serverMarket.getOrderBook().getVolumeInRange(minPrice, maxPrice);
    }

    protected int getCurrentPrice()
    {
        return serverMarket.getCurrentPrice();
    }
    protected long getItemImbalance()
    {
        return serverMarket.getItemImbalance();
    }

    protected ItemID getCurrencyItemID()
    {
        return serverMarket.getTradingPair().getCurrency();
    }

    protected boolean buyLimit(long volume, int price)
    {
        if(volume <= 0 || price < 0)
            return false;

        return serverMarket.createBotLimitOrder(volume, price);
    }
    protected boolean sellLimit(long volume, int price)
    {
        if(volume <= 0 || price < 0)
            return false;
        return serverMarket.createBotLimitOrder(-volume, price);
    }
    protected boolean limitTrade(long volume, int price)
    {
        if(volume == 0 || price < 0)
            return false;
        return serverMarket.createBotLimitOrder(volume, price);
    }
    protected boolean buyMarket(long volume)
    {
        if(volume <= 0)
            return false;
        return serverMarket.createBotMarketOrder(volume);
    }
    protected boolean sellMarket(long volume)
    {
        if(volume <= 0)
            return false;
        return serverMarket.createBotMarketOrder(-volume);
    }
    protected boolean marketTrade(long volume)
    {
        if(volume == 0)
            return false;
        return serverMarket.createBotMarketOrder(volume);
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

        boolean success = true;
        if(tag == null)
            return false;
        if(!tag.contains("settings"))
            return false;
        CompoundTag settingsTag = tag.getCompound("settings");
        success &= settings.load(settingsTag);

        if(tag.contains("buyOrderIDs"))
            tmp_load_buyOrderIDs = tag.getLongArray("buyOrderIDs");
        if(tag.contains("sellOrderIDs"))
            tmp_load_sellOrderIDs = tag.getLongArray("sellOrderIDs");



        setSettings(settings);
        return success;
    }

    public void update(MinecraftServer server) {
        if(!this.settings.enabled)
            return;
        long currentTime = System.currentTimeMillis();

        if(currentTime - lastMillis > this.settings.updateTimerIntervallMS) {
            lastMillis = currentTime;
            update();
        }
    }
}
