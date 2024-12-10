package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.ServerSaveable;
import net.kroia.stockmarket.util.Timestamp;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

public class MarketManager implements ServerSaveable {
    private String itemID;

    private MatchingEngine matchingEngine;
    private ServerTradingBot tradingBot;
    private ServerVolatilityBot volatilityBot;

    private PriceHistory priceHistory;
    private ServerTradeItem tradeItem;
   // private int lowPrice;
   // private int highPrice;

    private long lastTimeFast = 0;
    private long lastTimeSlow = 0;

    private static final long timerFastMS = 500;
    private static final long timerSlowMS = 1000;

    public MarketManager(ServerTradeItem tradeItem, int initialPrice, PriceHistory history)
    {
        this.tradeItem = tradeItem;
        this.itemID = tradeItem.getItemID();
        matchingEngine = new MatchingEngine(initialPrice, history);
        tradingBot = new ServerTradingBot(matchingEngine, itemID);
        volatilityBot = new ServerVolatilityBot(matchingEngine, itemID, 10);
        //matchingEngine.setTradingBot(tradingBot);
        priceHistory = history;

       // lowPrice = initialPrice;
       // highPrice = initialPrice;

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    public void setPriceHistory(PriceHistory priceHistory) {
        this.priceHistory = priceHistory;
    }
    public void setItemID(String itemID) {
        this.itemID = itemID;
    }

    public void addOrder(Order order)
    {
        StockMarketMod.LOGGER.info("Adding order: " + order.toString());
        matchingEngine.addOrder(order);
/*
        int price = matchingEngine.getPrice();
        lowPrice = Math.min(lowPrice, price);
        highPrice = Math.max(highPrice, price);
        priceHistory.setCurrentPrice(price);*/
    }
    public boolean cancelOrder(long orderID)
    {
        return matchingEngine.cancelOrder(orderID);
    }
    public ArrayList<Order> getOrders()
    {
        return matchingEngine.getOrders();
    }

    public void getOrders(String playerUUID, ArrayList<Order> orders)
    {
        matchingEngine.getOrders(playerUUID, orders);
    }

    public void updateBot()
    {
        if(tradingBot != null)
        {
            tradingBot.update();
            tradeItem.notifySubscribers();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();

            if(currentTime - lastTimeFast > timerFastMS) {
                lastTimeFast = currentTime;
                onFastTimerFinished();
            }
            if(currentTime - lastTimeSlow > timerSlowMS) {
                lastTimeSlow = currentTime;
                onSlowTimerFinished();
            }
        }
    }

    private void onFastTimerFinished()
    {
        if(volatilityBot != null)
        {
            volatilityBot.update();
            tradeItem.notifySubscribers();
        }
    }
    private void onSlowTimerFinished()
    {
        if(tradingBot != null)
        {
            tradingBot.update();
            tradeItem.notifySubscribers();
        }
    }

    public void shiftPriceHistory()
    {
        int currentPrice = matchingEngine.getPrice();
        priceHistory.addPrice(currentPrice, currentPrice, currentPrice, new Timestamp());
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }

    public int getLowPrice() {
        return priceHistory.getLowPrice();
    }

    public int getHighPrice() {
        return priceHistory.getHighPrice();
    }

    public int getCurrentPrice() {
        return matchingEngine.getPrice();
    }

    public int getVolume() {
        return matchingEngine.getTradeVolume();
    }

    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        return matchingEngine.getOrderBookVolume(tiles, minPrice, maxPrice);
    }

    @Override
    public void save(CompoundTag tag) {
      //  tag.putString("itemID", itemID);
        //tag.putInt("lowPrice", lowPrice);
        //tag.putInt("highPrice", highPrice);

        CompoundTag matchingEngineTag = new CompoundTag();
        matchingEngine.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);


        if(tradingBot != null)
        {
            CompoundTag tradingBotTag = new CompoundTag();
            tradingBot.save(tradingBotTag);
            tag.put("tradingBot", tradingBotTag);
        }


    }

    @Override
    public void load(CompoundTag tag) {
       // itemID = tag.getString("itemID");
        //lowPrice = tag.getInt("lowPrice");
        //highPrice = tag.getInt("highPrice");

        CompoundTag matchingEngineTag = tag.getCompound("matchingEngine");
        matchingEngine.load(matchingEngineTag);

        if(tag.contains("tradingBot") && tradingBot != null)
        {
            CompoundTag tradingBotTag = tag.getCompound("tradingBot");
            tradingBot.load(tradingBotTag);
        }
    }
}
