package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.function.Function;

public interface ISyncServerMarket {


    void test_resetVirtualVolumeDistribution();
    void test_setCurrentMarketPrice(long currentMarketPrice);
    void test_clearOrderbook();
    void test_setDefaultVolumeProviderFunction(Function<Double, Float> defaultVolumeProviderFunction);
    void test_resetVirtualOrderBookVolume();


    ItemID getItemID();
    long getDefaultPrice();
    long getCurrentMarketPrice();
    long getCurrentTime();
    long getRawVolume(long price);
    long getRawVolume(long startPrice, long endPrice);
    float getRealVolume(double price);
    float getRealVolume(double startPrice, double endPrice);

    boolean putOrder(Order order);
    boolean putOrder(InterMarketOrder order);
    List<Order> getLimitOrders();
    boolean isMarketOpen();
    boolean setMarketOpen(boolean marketOpen);

    MarketPriceStruct getCurrentMarketPriceStruct();
    MarketPriceStruct getCurrentMarketPriceStructAndReset();

    MarketSettings getSettings();
    void setSettings(MarketSettings settings);

    Orderbook getOrderbook();

    // Returns the accumulated traded volume for the current candle period (real-scaled)
    float getCurrentCandleTradedVolume();

    void update();
}
