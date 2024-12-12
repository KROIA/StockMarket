package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.BotMoneyBank;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * The ServerTradingBot simulates buy and sell orders to provide liquidity to the market.
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {

    public static class Settings implements ServerSaveable
    {
        public boolean enabled = true;
        public int maxOrderCount = ModSettings.MarketBot.MAX_ORDERS;
        public double volumeScale = ModSettings.MarketBot.VOLUME_SCALE;
        public double volumeSpread = ModSettings.MarketBot.VOLUME_SPREAD;
        public long updateTimerIntervallMS = ModSettings.MarketBot.UPDATE_TIMER_INTERVAL_MS;

        @Override
        public boolean save(CompoundTag tag) {
            tag.putBoolean("enabled", enabled);
            tag.putInt("maxOrderCount", maxOrderCount);
            tag.putDouble("volumeScale", volumeScale);
            tag.putDouble("volumeSpread", volumeSpread);
            tag.putLong("updateTimerIntervallMS", updateTimerIntervallMS);
            return false;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            if(!tag.contains("enabled") ||
                !tag.contains("maxOrderCount") ||
                !tag.contains("volumeScale") ||
                !tag.contains("volumeSpread") ||
                !tag.contains("updateTimerIntervallMS"))
                 return false;
            enabled = tag.getBoolean("enabled");
            maxOrderCount = tag.getInt("maxOrderCount");
            volumeScale = tag.getDouble("volumeScale");
            volumeSpread = tag.getDouble("volumeSpread");
            updateTimerIntervallMS = tag.getLong("updateTimerIntervallMS");
            return true;
        }
    }


    //protected MarketManager parent;
    protected Settings settings;
    private MatchingEngine matchingEngine;
    MarketManager parent;


    //protected final int maxOrderCount = ModSettings.MarketBot.MAX_ORDERS;

    //protected double volumeScale = ModSettings.MarketBot.VOLUME_SCALE;
    //protected double volumeSpread = ModSettings.MarketBot.VOLUME_SPREAD;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();


    //protected long updateTimerIntervallMS = ModSettings.MarketBot.UPDATE_TIMER_INTERVAL_MS;

    //protected boolean enabled = true;
    private long[] tmp_load_buyOrderIDs = null;
    private long[] tmp_load_sellOrderIDs = null;


    private long lastMillis = 0;

    public ServerTradingBot() {
        settings = new Settings();
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }
    protected ServerTradingBot(Settings settings) {
        this.settings = settings;
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    public void setSettings(Settings settings)
    {
        this.settings = settings;
    }
    public Settings getSettings()
    {
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
    /*public ServerTradingBot(MarketManager parent, MatchingEngine matchingEngine) {
       // this.parent = parent;
        this.matchingEngine = matchingEngine;

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }*/

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

    public UUID getUUID()
    {
        return ServerBankManager.getBotUser().getOwnerUUID();
    }
    public String getItemID()
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
        BankUser user = ServerBankManager.getBotUser();
        Bank moneyBank = user.getMoneyBank();
        String itemID = parent.getItemID();
        Bank itemBank = user.getBank(itemID);
        UUID botUUID = getUUID();

        int priceIncerement = 1;
        int currentPrice = matchingEngine.getPrice();
        for(int i=1; i<=this.settings.maxOrderCount/2; i++)
        {
            int sellPrice = currentPrice + i*priceIncerement;
            int buyPrice = currentPrice - i*priceIncerement;


            int buyVolume = getAvailableVolume(buyPrice);
            if(buyVolume > 0) {

                if(moneyBank.getBalance()>(buyVolume*2)*buyPrice) {
                    LimitOrder buyOrder = LimitOrder.createBotOrder(botUUID, moneyBank, itemBank, itemID, buyVolume, buyPrice);
                    if (buyOrder != null) {
                        matchingEngine.addOrder(buyOrder);
                        buyOrders.add(buyOrder);
                    }
                }
            }

            int sellVolume = getAvailableVolume(sellPrice);
            if(sellVolume < 0) {
                if(itemBank.getBalance() > -sellVolume) {
                    LimitOrder sellOrder = LimitOrder.createBotOrder(botUUID, moneyBank, itemBank, itemID, sellVolume, sellPrice);
                    if (sellOrder != null) {
                        matchingEngine.addOrder(sellOrder);
                        sellOrders.add(sellOrder);
                    }
                }
            }




        }
    }

    protected int getAvailableVolume(int price)
    {
        if(price < 0)
            return 0;
        int currentPrice = matchingEngine.getPrice();
        return getVolumeDistribution(currentPrice - price);
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    protected int getVolumeDistribution(int x)
    {
        double fX = (double)Math.abs(x);
        double exp = Math.exp(-fX*1.f/this.settings.volumeSpread);
        double random = Math.random()+1;

        double volume = (this.settings.volumeScale*random) * (1 - exp) * exp;

        if(x < 0)
            return (int)-volume;
        return (int)volume;

        //return -x*;
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




        return true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if(!this.settings.enabled || matchingEngine == null || parent == null)
            return;
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();

            if(currentTime - lastMillis > this.settings.updateTimerIntervallMS) {
                lastMillis = currentTime;
                update();
            }
        }
    }
}
