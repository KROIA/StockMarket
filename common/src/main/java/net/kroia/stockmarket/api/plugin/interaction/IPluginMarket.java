package net.kroia.stockmarket.api.plugin.interaction;

import net.kroia.banksystem.util.ItemID;
import org.jetbrains.annotations.NotNull;

public interface IPluginMarket {
    /**
     * Gets the itemID associated with this market.
     * @return The itemID of the market.
     */
    @NotNull ItemID getMarketID();


    /**
     * Gets the maximum volume a player is allowed to place in a single order on this market.
     * @return the limit in real items; 0 = unlimited
     */
    double getMaxRealOrderVolume();


    /**
     * Gets the interval in ticks at which the plugin should send stream packets to clients.
     * @return The tick interval for sending stream packets.
     */
    //int getStreamPacketSendTickInterval();

    /**
     * Sets the interval in ticks at which the plugin should send stream packets to clients.
     * @param interval The tick interval for sending stream packets.
     */
    //void setStreamPacketSendTickInterval(int interval);


    /**
     * Gets the default price of the market as a real price value.
     * @return The default price of the market.
     */
    double getDefaultRealPrice();

    /**
     * Sets the market's default (base/anchor) price as a real price value.
     * <p>
     * The default price is the long-term anchor other plugins build on — e.g. the
     * VolatilityPlugin oscillates its random walk around a flow-adjusted equilibrium
     * derived from it. Unlike {@link #setTargetPrice}, this is <b>not</b> buffered in the
     * per-tick market cache: the change is applied to the market immediately and is
     * persisted with the market's settings.
     * <p>
     * Intended for rare, permanent level shifts only — e.g. the NewsPlugin baking a
     * {@code reversal:none} news impact into the market once its hold phase ends
     * (NewsEventSystem plan §2 item 4). Do NOT use this for continuous price steering;
     * use {@link #setTargetPrice}/{@link #addToTargetPrice} for that.
     *
     * @param defaultRealPrice the new default price in real (scaled) units; must be &gt; 0
     */
    void setDefaultRealPrice(double defaultRealPrice);

    /**
     * Gets the current price of the market as a real price value.
     * Already scaled using the internal scaling factor.
     * @return The current price of the market.
     */
    double getPrice();


    /**
     * Gets the current price of the specified trading pair market as a real price value.
     * Already scaled using the internal scaling factor.
     * @param pair for which market the price should be returned
     * @return the current market price of the trading pair market
     */
    //float getPriceOf(TradingPair pair);


    /**
     * Converts the internal backend price to a real price value.
     * The backend price is an integer value used for calculations and storage.
     * The real price is a double value that is scaled using the internal scaling factor.
     * @param backendPrice The internal backend price to convert.
     * @return The converted real price value.
     */
    double convertBackendPriceToRealPrice(long backendPrice);

    /**
     * Converts a real price value to the internal backend price.
     * The backend price is an integer value used for calculations and storage.
     * The real price is a double value that is scaled using the internal scaling factor.
     * @param realPrice The real price value to convert.
     * @return The converted internal backend price.
     */
    long convertRealPriceToBackendPrice(double realPrice);

    /**
     * Gets the target-price value where the market is moving towards.
     * This value must be secret from non-admin players.
     * It is the target-price value used the last time the market was updated.
     * @return The target price of the market.
     */
    double getPreviousTargetPrice();


    /**
     * Gets the current target price that the market is moving towards.
     * This is the value set by plugins during the current tick.
     * If no plugin has set a target price yet this tick, returns the previous target price.
     * @return the current target price as a real (scaled) value
     */
    double getTargetPrice();


    void setTargetPrice(double targetPrice);


    /**
     * Adds the given delta to the target price.
     * @param delta The delta to add to the target price.
     *              Can be positive or negative.
     */
    void addToTargetPrice(double delta);

    /**
     * T-213: publishes this tick's flow equilibrium price (the price the market settles at without noise).
     * The NetFlowPlugin writes it; the VolatilityPlugin adds its random walk on top of it. Rolled over once
     * per tick like the target price.
     * <p>
     * The default implementation is a no-op so existing external implementations keep compiling.
     *
     * @param equilibriumPrice the equilibrium price in real (scaled) units
     */
    default void setEquilibriumPrice(double equilibriumPrice) {}

    /**
     * T-213: the flow equilibrium price: the value set this tick if any plugin published one, else last tick's,
     * else the default price (no equilibrium publisher, e.g. the NetFlowPlugin is removed or disabled).
     * <p>
     * The default implementation returns {@link #getDefaultRealPrice()}.
     *
     * @return the equilibrium price in real (scaled) units
     */
    default double getEquilibriumPrice() { return getDefaultRealPrice(); }

    /**
     * T-213: whether a plugin already wrote the target price this tick ({@link #setTargetPrice} or
     * {@link #addToTargetPrice}). The NetFlowPlugin only sets the target to the bare equilibrium when nobody
     * did, so a misordered plugin list never overwrites the VolatilityPlugin's target.
     * <p>
     * The default implementation returns false.
     *
     * @return true if the target price was written during the current tick
     */
    default boolean isTargetPriceSetThisTick() { return false; }



    /**
     * Creates a Limit order since the price is given.
     * @param amount Amount of items to trade.
     *               For Buy: amount is positive
     *               For Sell: amount is negative
     * @param price Price per item.
     * @return The internal order ID of the created order.
     */
    long placeOrder(double amount, double price);

    /**
     * Creates a Market order since no price is given.
     * The order will be executed at the best available price in the order book.
     * This method will not return the Order id since the order can not be canceled once placed,
     * so that information can not be used int the next update for anything.
     * @param amount Amount of items to trade.
     *               For Buy: amount is positive
     *               For Sell: amount is negative
     */
    void placeOrder(double amount);


    /**
     * Gets the natural abundance of this market's item.
     * Higher values mean the item is more common (e.g. cobblestone),
     * lower values mean it is rarer (e.g. diamond).
     * @return the natural abundance factor
     */
    float getNaturalAbundance();

    /**
     * Gets the net player item flow for this market.
     * Positive means players have net-sold items into the market.
     * Negative means players have net-bought items from the market.
     * Only player orders contribute; bot orders are excluded.
     * @return the net player item flow as a real (scaled) value
     */
    double getNetPlayerItemFlow();

    /**
     * Overwrites the net player item flow counter. Applied immediately (not cache-buffered).
     * Used by the VolatilityPlugin to saturate the counter at the flow where its
     * equilibrium price already sits at the configured floor/ceiling.
     * @param netPlayerItemFlow the new net flow in real (scaled) units, same sign
     *                          convention as {@link #getNetPlayerItemFlow()}
     * <p>
     * The default implementation is a no-op so existing external implementations of this
     * interface keep compiling. Implementations backed by a real market should override it.
     */
    default void setNetPlayerItemFlow(double netPlayerItemFlow) {}

    /**
     * Gets the faded net player item flow (#89): the same per-fill counting as
     * {@link #getNetPlayerItemFlow()}, but it decays exponentially towards 0 with the admin
     * half-life {@code MARKET.NET_FLOW_HALF_LIFE_MINUTES}. A one-off burst of trades fades out,
     * sustained demand or supply keeps it up. Price plugins should use this value.
     * <p>
     * The default implementation returns {@link #getNetPlayerItemFlow()} (no fading) so
     * existing external implementations of this interface keep compiling.
     * @return the faded net flow as a real (scaled) value, same sign convention
     */
    default double getFadedNetPlayerItemFlow() { return getNetPlayerItemFlow(); }

    /**
     * Overwrites the faded net player item flow. Applied immediately (not cache-buffered).
     * Used by the VolatilityPlugin to saturate it at its price band.
     * The default implementation is a no-op.
     * @param fadedNetPlayerItemFlow the new faded flow in real (scaled) units
     */
    default void setFadedNetPlayerItemFlow(double fadedNetPlayerItemFlow) {}



    /**
     * Checks whether this market is currently open for trading, i.e. not under maintenance AND inside
     * its trading hours ({@code !maintenance && tradingOpen}). Markets under maintenance reject player orders
     * while bot orders still trade; markets outside their trading hours reject all orders and
     * are not passed to plugin update/finalize at all.
     * @return true if the market is open (not under maintenance and inside trading hours), false otherwise
     */
    boolean isMarketOpen();

    /**
     * Whether the virtual orderbook is enabled for this market. When false, plugins that
     * write to or read from the virtual orderbook should skip this market entirely.
     */
    boolean isVirtualOrderbookEnabled();

    /**
     * Zeros the virtual orderbook AND makes the shift-fill provider return 0 for every
     * price level (sticky through subsequent price moves). Distinct from
     * {@link #resetVirtualOrderbook()} — clearing survives array shifts until an explicit
     * reset undoes it.
     */
    void clearVirtualOrderbook();

    /**
     * Refills the virtual orderbook from the default volume distribution function AND
     * drops the sticky-clear flag so subsequent shift-fills repopulate normally again.
     */
    void resetVirtualOrderbook();

    /**
     * Gets the accumulated traded volume for the current price candle.
     * Resets when a new candle starts.
     * @return the traded volume for the current candle
     */
    float getCurrentCandleTradedVolume();

}
