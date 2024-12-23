package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerTradingBotFactory;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.Timestamp;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.UUID;

public class MarketManager implements ServerSaveable {
    private String itemID;

    private MatchingEngine matchingEngine;
    private ServerTradingBot tradingBot;

    private PriceHistory priceHistory;
    //private ServerTradeItem tradeItem;

    public MarketManager(ServerTradeItem tradeItem, int initialPrice, PriceHistory history)
    {
        //this.tradeItem = tradeItem;
        this.itemID = tradeItem.getItemID();
        matchingEngine = new MatchingEngine(initialPrice, history);
        priceHistory = history;
    }

    public void clear()
    {
        if(tradingBot != null)
        {
            ServerTradingBot bot = tradingBot;
            bot.clearOrders();
            removeTradingBot();
        }
    }

    public void setTradingBot(ServerTradingBot bot)
    {
        if(!StockMarketModSettings.MarketBot.ENABLED)
        {
            StockMarketMod.LOGGER.warn("[MarketManager] Trading bots are disabled");
            return;
        }
        if(bot.getParent()!= null)
        {
            bot.getParent().removeTradingBot();
        }
        // Check if bot aleady has a item bank
        Bank itemBank = ServerMarket.getBotUser().getBank(itemID);
        if(itemBank == null)
        {
            itemBank = ServerMarket.getBotUser().createItemBank(itemID, 0);
        }
        else {
            if(bot instanceof ServerVolatilityBot volatilityBot)
            {
                ServerVolatilityBot.Settings settings = (ServerVolatilityBot.Settings) volatilityBot.getSettings();
                if(settings != null)
                {
                    if(settings.targetItemBalance == 0)
                    {
                        settings.targetItemBalance = itemBank.getBalance()/2;
                    }
                }
            }
        }
        bot.setParent(this);
        bot.setMatchingEngine(matchingEngine);
        tradingBot = bot;
        //bot.setEnabled(true);
    }
    public void removeTradingBot()
    {
        if(tradingBot != null) {
            tradingBot.setEnabled(false);
            tradingBot.clearOrders();
            tradingBot.setParent(null);
            tradingBot.setMatchingEngine(null);

        }
        tradingBot = null;
    }
    public boolean hasTradingBot()
    {
        return tradingBot != null;
    }
    public ServerTradingBot getTradingBot()
    {
        return tradingBot;
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

    public void cancelAllOrders(UUID playerUUID)
    {
        matchingEngine.cancelAllOrders(playerUUID);
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

        CompoundTag botTag = new CompoundTag();
        if(tradingBot != null)
        {
            success &= tradingBot.save(botTag);
            tag.put("tradingBot", botTag);
        }


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

        if(tag.contains("tradingBot"))
        {
            CompoundTag tradingBotTag = tag.getCompound("tradingBot");
            ServerTradingBot bot = ServerTradingBotFactory.loadFromTag(tradingBotTag);
            if(bot != null)
                setTradingBot(bot);
        }

        return !itemID.isEmpty() && success;
    }
}
