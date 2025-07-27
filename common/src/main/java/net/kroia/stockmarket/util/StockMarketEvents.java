package net.kroia.stockmarket.util;

import net.kroia.modutilities.event.Signal;
public class StockMarketEvents {
    public final Signal STOCKMARKET_DATA_SAVED_TO_FILE = new Signal();
    public final Signal STOCKMARKET_DATA_LOADED_FROM_FILE = new Signal();



    public void removeListeners() {
        STOCKMARKET_DATA_SAVED_TO_FILE.removeListeners();
        STOCKMARKET_DATA_LOADED_FROM_FILE.removeListeners();
    }

}
