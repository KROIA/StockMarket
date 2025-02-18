package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;

public class GhostOrderBook implements ServerSaveable {
/*
    private final int[] volumePriceArray =
    {
            0,
            2,
            4,
            6,
            9,
            13,
            20,
            31,
            48,
            73,
            112,
            172,
            264,
            406,
            623,
            957,
            1470,
            2257,
            3467,
            5324,
            8176,
            12557,
            19283,
            29614,
            45479,
            69843,
            107259,
            164720,
            252963,
            388481,
            596599,
            916209,
            1407042,
            2160824,
            3318422,
            5096171,
            7826297,
            12019009,
            18457844,
            28346100,
            43531701,
            66852550,
            102666867,
            157667667,
            242133552,
            371849589,
            571057236,
            876984608,
            1346803710,
            2068314787
    };
    private final float[] volumeDistributionArray = new float[volumePriceArray.length];
*/
    private final float[] volumeDistributionArray = new float[10000];
    public GhostOrderBook() {
       // Arrays.fill(volumeDistributionArray, 0);
    }

    public void cleanup() {
    }

    public void updateVolume(double deltaT, int currentPrice) {
        /*for(int i=0; i<volumeDistributionArray.length; i++)
        {
            float targetAmount = getTargetAmount(volumePriceArray[i], currentPrice);
            if(Math.abs(volumeDistributionArray[i])<Math.abs(targetAmount))
                volumeDistributionArray[i] += targetAmount*(float)deltaT;
        }*/

        for(int i=currentPrice-100; i<currentPrice+100; i++)
        {
            if(i < 0 || i >= volumeDistributionArray.length)
                continue;
            float targetAmount = getTargetAmount(i, currentPrice);
            if(Math.abs(volumeDistributionArray[i])<Math.abs(targetAmount))
                volumeDistributionArray[i] += targetAmount*(float)deltaT;
            if(i<currentPrice && volumeDistributionArray[i]<0)
                volumeDistributionArray[i] = 0;
            else if(i>currentPrice && volumeDistributionArray[i]>0)
                volumeDistributionArray[i] = 0;
        }
    }

    @Override
    public boolean load(CompoundTag tag) {
        return false;
    }
    @Override
    public boolean save(CompoundTag tag) {
        return false;
    }

    /**
     * Get the amount of items to buy or sell based on the price difference
     * @param price on which the amount should be based
     * @param currentPrice the current price of the item
     * @return the amount of items to buy or sell. Negative values indicate selling
     */
    public int getAmount(int price, int currentPrice)
    {
        if(price < 0 || price >= volumeDistributionArray.length)
            return price > currentPrice ? -1 : 1;
        int amount = (int)volumeDistributionArray[price];
        if(amount == 0)
            return price > currentPrice ? -1 : 1;
        return amount;


/*
        // linear interpolate
        int i = 0;
        while(i < volumePriceArray.length && volumePriceArray[i] < price)
            i++;
        if(i == 0)
            return (int)volumeDistributionArray[0];
        if(i == volumePriceArray.length)
            return (int)volumeDistributionArray[volumePriceArray.length-1];
        int price1 = volumePriceArray[i-1];
        int price2 = volumePriceArray[i];
        int amount1 = (int)volumeDistributionArray[i-1];
        int amount2 = (int)volumeDistributionArray[i];
        return (int)(amount1 + (amount2-amount1) * (price-price1) / (price2-price1));

*/
        /*float volumeScale = 1000f;
        float currentPriceFloat = (float)currentPrice;
        float relativePrice = currentPriceFloat - (float)price;

        float amount = 0;
        if(relativePrice > 0)
            amount += 0.01f;
        else if(relativePrice <= 0)
            amount += -0.01f;

        //if(relativePrice < 10 && relativePrice > -10)
        //    amount += relativePrice * (float)Math.exp(-Math.abs(relativePrice));


        return (int)(amount*volumeScale);*/
    }
    public void removeAmount(int price, int amount)
    {
        if(price < 0 || price >= volumeDistributionArray.length)
            return;
        if(volumeDistributionArray[price] > 0)
        {
            volumeDistributionArray[price] -= amount;
            if(volumeDistributionArray[price] < 0)
                volumeDistributionArray[price] = 0;
        }
        else if(volumeDistributionArray[price] < 0)
        {
            volumeDistributionArray[price] += amount;
            if(volumeDistributionArray[price] > 0)
                volumeDistributionArray[price] = 0;
        }
        /*
        int i = 0;
        while(i < volumePriceArray.length && volumePriceArray[i] < price)
            i++;
        if(i == 0)
            volumeDistributionArray[0] -= amount;
        else if(i == volumePriceArray.length)
            volumeDistributionArray[volumePriceArray.length-1] -= amount;
        else
        {
            int price1 = volumePriceArray[i-1];
            int price2 = volumePriceArray[i];
            int amount1 = (int)volumeDistributionArray[i-1];
            int amount2 = (int)volumeDistributionArray[i];
            volumeDistributionArray[i-1] = amount1 + (amount2-amount1) * (price-price1) / (price2-price1);
        }
        */

    }

    private float getTargetAmount(int price, int currentPrice)
    {
        // Calculate close price volume distribution
        float currentPriceFloat = (float)currentPrice;
        float relativePrice = (currentPriceFloat - (float)price)/(currentPriceFloat+1);
        float width = 1f;

        float amount = 0;
        if(relativePrice < 20 && relativePrice > -20)
            amount += (float)Math.E * width * relativePrice * (float)Math.exp(-Math.abs(relativePrice*width));




        float volumeScale = 10f;


        if(relativePrice > 0)
            amount += 0.1f;
        else if(relativePrice <= 0)
            amount += -0.1f;

        //if(relativePrice < 20 && relativePrice > -20)
        //    amount += relativePrice * (float)Math.exp(-Math.abs(relativePrice*0.05f));


        return (amount*volumeScale);
    }
}
