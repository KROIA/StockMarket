package net.kroia.stockmarket.util;

import net.kroia.modutilities.event.DataEvent;
import net.kroia.modutilities.event.Signal;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.util.Tuple;

public class StockMarketEvents {
    public final Signal STOCKMARKET_DATA_SAVED_TO_FILE = new Signal();
    public final Signal STOCKMARKET_DATA_LOADED_FROM_FILE = new Signal();


    public final DataEvent<Tuple<TradingPair, Order>> ORDER_PLACED = new DataEvent<>();
    public final DataEvent<Tuple<TradingPair, Order>> ORDER_FINISHED = new DataEvent<>();



    public void removeListeners() {
        STOCKMARKET_DATA_SAVED_TO_FILE.removeListeners();
        STOCKMARKET_DATA_LOADED_FROM_FILE.removeListeners();

        ORDER_FINISHED.removeListeners();
    }

}
