package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface ISyncServerMarket {


    void test_resetVirtualVolumeDistribution();
    void test_setCurrentMarketPrice(long currentMarketPrice);
    void test_clearOrderbook();
    /**
     * Sets the default-volume provider used to fill the {@code VirtualOrderbook} with virtual
     * liquidity when its window re-centers on large price moves or is reset.
     *
     * <p><b>Contract:</b> the function receives a <b>real</b> price (backend price divided by the
     * item fraction scale factor) and must return the <b>real</b> (unscaled) volume at that price.
     * {@code ServerMarket.defaultVolumeProvider(long)} adapts this to the raw
     * {@code VirtualOrderbook} provider contract (backend price in, raw volume out) by performing
     * the price and volume conversions itself — implementations must not convert.
     * The sign of the returned volume only matters in magnitude:
     * {@code VirtualOrderbook.getDefaultVolume} re-normalizes the sign against the current
     * backend market price (positive below, negative above).
     *
     * <p>Despite the {@code test_} prefix this is also part of the production wiring
     * (registered by {@code PluginOrderBook} for the volume distribution plugins).
     *
     * @param defaultVolumeProviderFunction real price to real volume function, or {@code null}
     *                                      to fall back to the built-in default distribution
     */
    void test_setDefaultVolumeProviderFunction(Function<Double, Float> defaultVolumeProviderFunction);
    void test_resetVirtualOrderBookVolume();

    /**
     * Test-only helper: empties all four incoming order buffers
     * (buyMarket, sellMarket, buyLimit, sellLimit input queues) that
     * {@code putOrder} stages before {@code update()} drains them into the matching engine.
     *
     * <p>Used to guarantee isolation between tests: without this, orders staged by a
     * preceding test can leak into a subsequent test's {@code update()} call and shift
     * the market price unexpectedly.
     *
     * <p>This does <b>not</b> reset any dependent state (bank balances, orderbook,
     * candle fields, market price, market-open flag). Callers are responsible for
     * resetting whatever else their test needs.
     */
    void test_clearIncomingOrderBuffers();


    ItemID getItemID();
    long getDefaultPrice();
    long getCurrentMarketPrice();
    long getCurrentTime();
    long getRawVolume(long price);
    long getRawVolume(long startPrice, long endPrice);
    float getRealVolume(double price);
    float getRealVolume(double startPrice, double endPrice);

    boolean putOrder(Order order);

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
    /**
     * @return {@code true} if this market is under maintenance (player orders rejected,
     *         bots still trade)
     */
    boolean isUnderMaintenance();
    /**
     * Enables or disables maintenance mode. Enabling it cancels all player orders and fires the
     * maintenance callback (cancels inter-market orders); ending it only clears the flag.
     * @param maintenance {@code true} to enter maintenance, {@code false} to end it
     * @return {@code true} on success
     */
    boolean setMaintenance(boolean maintenance);
    /**
     * SM-354: like {@link #setMaintenance(boolean)}, but entering maintenance also stores a close
     * reason that the trade screen shows instead of the plain maintenance text. Ending maintenance
     * clears the reason (as every reopen path does).
     * @param maintenance {@code true} to enter maintenance, {@code false} to end it
     * @param reasonKey   translation key of the close reason, or {@code null} for the plain
     *                    maintenance text; ignored when {@code maintenance} is {@code false}
     * @return {@code true} on success
     */
    boolean setMaintenance(boolean maintenance, @org.jetbrains.annotations.Nullable String reasonKey);
    /**
     * SM-354: translation key of the reason this market was put under maintenance.
     * @return the reason key, or {@code null} if none was given or the market is not under maintenance
     */
    @org.jetbrains.annotations.Nullable String getMaintenanceReason();

    /**
     * @return {@code true} if the market is currently inside its trading hours (or forced open by
     *         an override). When {@code false} no new orders are accepted and no matching happens;
     *         resting orders stay in the book and can still be cancelled.
     */
    boolean isTradingOpen();

    /**
     * Combined state for display: MAINTENANCE wins over CLOSED.
     * @return the current {@link MarketSettings.TradingState}
     */
    default MarketSettings.TradingState getTradingState()
    {
        if (isUnderMaintenance()) return MarketSettings.TradingState.MAINTENANCE;
        return isTradingOpen() ? MarketSettings.TradingState.OPEN : MarketSettings.TradingState.CLOSED;
    }

    /**
     * Sets the daily trading window. Values are clamped to [0, 1440]; open == close (or
     * 00:00-24:00) means always open; open &gt; close wraps over midnight. The open state is
     * re-evaluated at the end of the next market-manager tick.
     * @param openMinute  opening minute of the day (0..1440)
     * @param closeMinute closing minute of the day (0..1440)
     * @return {@code true} on success
     */
    boolean setTradingHours(int openMinute, int closeMinute);

    /**
     * Manually forces the market open or closed until the next scheduled transition
     * (schedule flips open/closed), then the override is cleared.
     * Applied at the end of the next market-manager tick. Persisted.
     * @param open {@code true} to force open, {@code false} to force closed
     * @return {@code true} on success
     */
    boolean setTradingOverride(boolean open);

    /**
     * Re-evaluates the trading schedule. Called by the server market manager once per tick
     * (master only) after all order buffers were processed.
     * @param minuteOfDay current minute of the day (0..1439) of the active clock source
     */
    void updateTradingHours(int minuteOfDay);

    MarketPriceStruct getCurrentMarketPriceStruct();
    MarketPriceStruct getCurrentMarketPriceStructAndReset();

    MarketSettings getSettings();
    void setSettings(MarketSettings settings);

    /**
     * @return the real net player item flow (raw units; positive = players net-sold). Updated on
     *         every player fill, never decays.
     */
    long getNetPlayerItemFlow();
    /** Resets both the real and the faded ({@link #getFadedNetPlayerItemFlow()}) net flow to 0. */
    void resetNetPlayerItemFlow();
    /**
     * #89: exponentially faded net player item flow (raw units, same sign as
     * {@link #getNetPlayerItemFlow()}). Gets the same per-fill deltas but decays towards 0 with the
     * half-life {@code MARKET.NET_FLOW_HALF_LIFE_MINUTES}. The price plugins read this value.
     * @return the faded flow in raw units
     */
    double getFadedNetPlayerItemFlow();
    /**
     * Overwrites the faded net flow (raw units). Used to saturate it at a plugin's price band.
     * @param fadedNetPlayerItemFlow the new raw value
     */
    void setFadedNetPlayerItemFlow(double fadedNetPlayerItemFlow);
    /**
     * Overwrites the net player item flow counter (raw units; positive = players net-sold).
     * @param netPlayerItemFlow the new raw counter value
     */
    void setNetPlayerItemFlow(long netPlayerItemFlow);
    /**
     * Refills the virtual orderbook from the plugin-provided default volume distribution
     * function and drops the sticky-clear flag. Use this after a {@link #clearVirtualOrderbook()}
     * to bring the market back to a fully-populated depth profile.
     */
    void resetVirtualOrderbook();

    /**
     * Zeros the virtual orderbook AND sets the sticky-clear flag so any future array
     * shift also fills with 0. Distinct from {@link #resetVirtualOrderbook()} — clearing
     * is persistent through price moves until an explicit reset undoes it.
     */
    void clearVirtualOrderbook();

    Orderbook getOrderbook();

    // Returns the accumulated traded volume for the current candle period, in RAW units (real * ITEM_FRACTION_SCALE_FACTOR)
    float getCurrentCandleTradedVolume();

    void update();
}
