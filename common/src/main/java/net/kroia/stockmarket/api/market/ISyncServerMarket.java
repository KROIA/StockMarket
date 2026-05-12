package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.UUID;
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

    /**
     * Cancels a pending order identified by its executor, timestamp, type, start price, and target volume.
     * Removes the order from the orderbook and triggers cancellation cleanup (e.g. unlocking reserved funds/items).
     *
     * @param executor     the UUID of the player who placed the order
     * @param time         the timestamp when the order was placed
     * @param type         the order type (LIMIT, MARKET, etc.)
     * @param startPrice   the price at which the order was placed
     * @param targetVolume the target volume of the order
     * @return true if a matching order was found and cancelled, false otherwise
     */
    boolean cancelOrder(UUID executor, long time, Order.Type type, long startPrice, long targetVolume);

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
