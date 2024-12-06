package net.kroia.stockmarket.util;

import net.minecraft.network.FriendlyByteBuf;

public class PriceHistory {

    private final int maxHistorySize = 10;
    private int[] lowPrice = new int[maxHistorySize];
    private int[] highPrice = new int[maxHistorySize];
    private int[] closePrice = new int[maxHistorySize];
    private int oldestClosePrice = 0;
    private Timestamp[] timeStamps = new Timestamp[maxHistorySize];

    private final String itemID;

    public PriceHistory(String itemID) {
        this.itemID = itemID;
        clear();
    }
    public PriceHistory(String itemID, int initialPrice) {
        this.itemID = itemID;
        oldestClosePrice = initialPrice;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = initialPrice;
            highPrice[i] = initialPrice;
            closePrice[i] = initialPrice;
        }
    }

    public void clear()
    {
        oldestClosePrice = 0;
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = 0;
            highPrice[i] = 0;
            closePrice[i] = 0;
        }
    }

    public int size()
    {
        return maxHistorySize;
    }

    public String getItemID()
    {
        return itemID;
    }
    public void addPrice(int low, int high, int close, Timestamp timestamp)
    {
        // Shift the prices to the left
        oldestClosePrice = closePrice[0];
        for (int i = 0; i < maxHistorySize-1; i++) {
            lowPrice[i] = lowPrice[i+1];
            highPrice[i] = highPrice[i+1];
            closePrice[i] = closePrice[i+1];
            timeStamps[i] = timeStamps[i+1];
        }
        lowPrice[maxHistorySize-1] = low;
        highPrice[maxHistorySize-1] = high;
        closePrice[maxHistorySize-1] = close;
        timeStamps[maxHistorySize-1] = timestamp;
    }



    public void setCurrentPrice(int close)
    {
        closePrice[maxHistorySize-1] = close;
        lowPrice[maxHistorySize-1] = Math.min(lowPrice[maxHistorySize-1], close);
        highPrice[maxHistorySize-1] = Math.max(highPrice[maxHistorySize-1], close);
    }
    public int getCurrentPrice()
    {
        return closePrice[maxHistorySize-1];
    }

    public int getLowPrice(int index)
    {
        return lowPrice[index];
    }

    public int getHighPrice(int index)
    {
        return highPrice[index];
    }

    public int getClosePrice(int index)
    {
        return closePrice[index];
    }

    public int getOpenPrice(int index)
    {
        if(index == 0)
        {
            return oldestClosePrice;
        }
        return closePrice[index-1];
    }

    public Timestamp getTimeStamp(int index)
    {
        return timeStamps[index];
    }


    // Interface to send the timestamp over the network
    public PriceHistory(FriendlyByteBuf buf) {
        itemID = buf.readUtf();
        oldestClosePrice = buf.readInt();
        for (int i = 0; i < maxHistorySize; i++) {
            lowPrice[i] = buf.readInt();
            highPrice[i] = buf.readInt();
            closePrice[i] = buf.readInt();
            timeStamps[i] = new Timestamp(buf);
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
        buf.writeInt(oldestClosePrice);
        for (int i = 0; i < maxHistorySize; i++) {
            buf.writeInt(lowPrice[i]);
            buf.writeInt(highPrice[i]);
            buf.writeInt(closePrice[i]);
            timeStamps[i].toBytes(buf);
        }
    }


}
