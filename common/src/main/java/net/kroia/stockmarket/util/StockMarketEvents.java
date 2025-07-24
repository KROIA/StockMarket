package net.kroia.stockmarket.util;

import net.kroia.modutilities.event.Signal;
public class StockMarketEvents {
    public final Signal STOCKMARKET_DATA_SAVED_TO_FILE = new Signal();
    public final Signal STOCKMARKET_DATA_LOADED_FROM_FILE = new Signal();



    public void clearListeners() {
        STOCKMARKET_DATA_SAVED_TO_FILE.clearListeners();
        STOCKMARKET_DATA_LOADED_FROM_FILE.clearListeners();
    }

}
