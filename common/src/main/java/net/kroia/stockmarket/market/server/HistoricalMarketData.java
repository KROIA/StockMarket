package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.Timestamp;
import net.minecraft.nbt.CompoundTag;

public class HistoricalMarketData implements ServerSaveable {

    private int currentPrice;
    private final PriceHistory history;

    public HistoricalMarketData(int historySize) {
        this.currentPrice = 0;
        history = new PriceHistory(historySize);
    }
    public HistoricalMarketData(int currentPrice, int historySize) {
        this.currentPrice = currentPrice;
        history = new PriceHistory(currentPrice, historySize);
    }

    public void createNewCandle()
    {
        history.addPrice(currentPrice, currentPrice, currentPrice, new Timestamp());
    }

    public int getCurrentPrice() {
        return currentPrice;
    }
    public void setCurrentRawPrice(int currentPrice) {
        this.currentPrice = currentPrice;
        history.setCurrentRawPrice(currentPrice);
    }
    public void addVolume(long volume)
    {
        history.addVolume(volume);
    }

    public void clear(int defaultPrice)
    {
        this.currentPrice = defaultPrice;
        this.history.clear(defaultPrice);
    }

    public PriceHistory getHistory() {
        return history;
    }

    @Override
    public boolean save(CompoundTag tag) {
        history.save(tag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(history.load(tag))
        {
            currentPrice = history.getCurrentRawPrice();
            return true;
        }
        return false;
    }
}
