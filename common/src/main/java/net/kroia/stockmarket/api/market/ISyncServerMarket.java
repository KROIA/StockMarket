package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.function.Function;

public interface ISyncServerMarket {


    void test_resetVirtualVolumeDistribution();
    void test_setCurrentMarketPrice(long currentMarketPrice);
    void test_clearOrderbook();
    void test_setDefaultVolumeProviderFunction(Function<Long, Float> defaultVolumeProviderFunction);
    void test_resetVirtualOrderBookVolume();


    ItemID getItemID();
    long getCurrentMarketPrice();
    long getCurrentTime();
    long getVolume(long price);
    float getVolume(long startPrice, long endPrice);

    boolean putOrder(Order order);
    boolean putOrder(InterMarketOrder order);
    List<Order> getLimitOrders();
    boolean isMarketOpen();
    boolean setMarketOpen(boolean marketOpen);

    MarketPriceStruct getCurrentMarketPriceStruct();
    MarketPriceStruct getCurrentMarketPriceStructAndReset();


    void update();
}
