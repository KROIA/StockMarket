package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.ServerSaveable;
import net.kroia.stockmarket.util.Timestamp;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.UUID;

public class MarketManager implements ServerSaveable {
    private String itemID;

    private MatchingEngine matchingEngine;
    private final ArrayList<ServerTradingBot> tradingBots = new ArrayList<>();

    private PriceHistory priceHistory;
    //private ServerTradeItem tradeItem;

    public MarketManager(ServerTradeItem tradeItem, int initialPrice, PriceHistory history)
    {
        //this.tradeItem = tradeItem;
        this.itemID = tradeItem.getItemID();
        matchingEngine = new MatchingEngine(initialPrice, history);
        priceHistory = history;
    }

    public void addTradingBot(ServerTradingBot bot)
    {
        if(!ModSettings.MarketBot.ENABLED)
        {
            StockMarketMod.LOGGER.warn("[MarketManager] Trading bots are disabled");
            return;
        }
        if(bot.getParent()!= null)
        {
            bot.getParent().removeTradingBot(bot);
        }
        bot.setParent(this);
        bot.setMatchingEngine(matchingEngine);
        tradingBots.add(bot);
        bot.setEnabled(true);
    }
    public void removeTradingBot(ServerTradingBot bot)
    {
        if(bot != null && tradingBots.contains(bot) && bot.getParent() == this)
        {
            bot.setEnabled(false);
            bot.setParent(null);
            bot.setMatchingEngine(null);
            tradingBots.remove(bot);
        }
    }
    public void removeAllBots()
    {
        for(ServerTradingBot bot : tradingBots)
        {
            bot.setEnabled(false);
            bot.setParent(null);
            bot.setMatchingEngine(null);
        }
        tradingBots.clear();
    }
    public int getBotCount()
    {
        return tradingBots.size();
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

    public void getOrders(UUID playerUUID, ArrayList<Order> orders)
    {
        matchingEngine.getOrders(playerUUID, orders);
    }

    public String getItemID()
    {
        return itemID;
    }

    /*@SubscribeEvent
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
    }*/


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
    public boolean save(CompoundTag tag) {
        boolean success = true;
        tag.putString("itemID", itemID);
        //tag.putInt("lowPrice", lowPrice);
        //tag.putInt("highPrice", highPrice);

        CompoundTag matchingEngineTag = new CompoundTag();
        success &= matchingEngine.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);

        ListTag tradingBotList = new ListTag();
        for(ServerTradingBot bot : tradingBots)
        {
            CompoundTag tradingBotTag = new CompoundTag();
            success &= bot.save(tradingBotTag);
            tradingBotList.add(tradingBotTag);
        }
        tag.put("tradingBots", tradingBotList);

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("itemID") ||
                !tag.contains("matchingEngine"))
            return false;
        boolean success = true;
        itemID = tag.getString("itemID");

        CompoundTag matchingEngineTag = tag.getCompound("matchingEngine");
        success &= matchingEngine.load(matchingEngineTag);

        if(tag.contains("tradingBots"))
        {
            removeAllBots();
            ListTag tradingBotList = tag.getList("tradingBots", 10);
            for (int i = 0; i < tradingBotList.size(); i++) {
                CompoundTag tradingBotTag = tradingBotList.getCompound(i);
                ServerTradingBot bot = ServerTradingBotFactory.loadFromTag(tradingBotTag);
                if(bot != null)
                    addTradingBot(bot);
            }
        }

        return !itemID.isEmpty() && success;
    }
}
