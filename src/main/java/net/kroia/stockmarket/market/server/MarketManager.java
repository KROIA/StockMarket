package net.kroia.stockmarket.market.server;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.ServerSaveable;
import net.kroia.stockmarket.util.Timestamp;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;

public class MarketManager implements ServerSaveable {
    private String itemID;

    private MatchingEngine matchingEngine;

    private PriceHistory priceHistory;
    private int lowPrice;
    private int highPrice;

    public MarketManager(String itemID, int initialPrice, PriceHistory history)
    {
        this.itemID = itemID;
        matchingEngine = new MatchingEngine(initialPrice);
        priceHistory = history;

        lowPrice = initialPrice;
        highPrice = initialPrice;
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

        int price = matchingEngine.getPrice();
        lowPrice = Math.min(lowPrice, price);
        highPrice = Math.max(highPrice, price);
        priceHistory.setCurrentPrice(price);
    }
    public boolean cancelOrder(long orderID)
    {
        return matchingEngine.cancelOrder(orderID);
    }
    public ArrayList<Order> getOrders()
    {
        return matchingEngine.getOrders();
    }

    public void shiftPriceHistory()
    {
        int currentPrice = matchingEngine.getPrice();
        lowPrice = currentPrice;
        highPrice = currentPrice;
        priceHistory.addPrice(lowPrice, highPrice, currentPrice, new Timestamp());
    }

    public PriceHistory getPriceHistory() {
        return priceHistory;
    }

    public int getLowPrice() {
        return lowPrice;
    }

    public int getHighPrice() {
        return highPrice;
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
        tag.putInt("lowPrice", lowPrice);
        tag.putInt("highPrice", highPrice);

        CompoundTag matchingEngineTag = new CompoundTag();
        matchingEngine.save(matchingEngineTag);
        tag.put("matchingEngine", matchingEngineTag);



    }

    @Override
    public void load(CompoundTag tag) {
       // itemID = tag.getString("itemID");
        lowPrice = tag.getInt("lowPrice");
        highPrice = tag.getInt("highPrice");

        CompoundTag matchingEngineTag = tag.getCompound("matchingEngine");
        matchingEngine.load(matchingEngineTag);
    }
}
