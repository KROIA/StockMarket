package net.kroia.stockmarket.pluginsystem.plugin.core.cache;


import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;

import java.util.ArrayList;
import java.util.List;

public class VirtualOrderBookCache {
    public enum ManipulationOperator
    {
        SET,
        ADD,
    }
    public enum ManipulationType
    {
        RANGE,  // Applies the volume to a given range
        ARRAY   // Uses an array to apply individual price levels
    }

    private static class ManipulationData {

        private final ManipulationType type;
        private final ManipulationOperator operator;

        // ManipulationType == RANGE
        private final double minPrice, maxPrice;
        private float rangeVolume;

        // ManipulationType == ARRAY
        private final long backendStartPrice;
        private final float[] arrayVolume;


        public ManipulationData(double minPrice, double maxPrice, float volume, ManipulationOperator operator)
        {
            type  = ManipulationType.RANGE;
            this.operator = operator;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.rangeVolume = volume;

            backendStartPrice = 0;
            arrayVolume = null;
        }
        public ManipulationData(int backendStartPrice, float[] volume, ManipulationOperator operator)
        {
            type  = ManipulationType.ARRAY;
            this.backendStartPrice = backendStartPrice;
            this.arrayVolume = volume;
            this.operator = operator;

            minPrice = 0;
            maxPrice = 0;
            rangeVolume = 0;
        }


        public void apply(Orderbook orderBook)
        {
            switch(type)
            {
                case RANGE:
                {
                    orderBook.setVirtualOrderbookVolume(minPrice, maxPrice, rangeVolume);
                }
                case ARRAY:
                {
                    orderBook.setVirtualOrderbookVolume(backendStartPrice, arrayVolume);
                }
            }
        }
    }

    private final List<ManipulationData> manipulationData = new ArrayList<ManipulationData>();

    public VirtualOrderBookCache()
    {

    }

    public void addManipulation(double minPrice, double maxPrice, float volume, ManipulationOperator operator)
    {
        ManipulationData data = new ManipulationData(minPrice, maxPrice, volume, operator);
        manipulationData.add(data);
    }
    public void addManipulation(int backendStartPrice, float[] volume, ManipulationOperator operator)
    {
        ManipulationData data = new ManipulationData(backendStartPrice, volume, operator);
        manipulationData.add(data);
    }

    public void apply(IServerMarket serverMarket)
    {
        Orderbook orderBook = serverMarket.getOrderbook();
        for(ManipulationData data : manipulationData)
        {
            data.apply(orderBook);
        }
        manipulationData.clear();
    }
}
