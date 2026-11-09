package net.kroia.stockmarket.stockmarket.market;

import net.kroia.modutilities.NbtCompat;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.util.ProfitAlerts;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.market.core.MatchingEngine;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class ServerMarket implements ServerSaveable, IServerMarket {
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
        Orderbook.setBackend(backend);
        MatchingEngine.setBackend(backend);
    }


    private final ItemID itemID;
    private final Orderbook orderbook;
    private final MatchingEngine matchingEngine;
    private long currentMarketPrice;
    private long netPlayerItemFlow = 0;
    /**
     * #89: exponentially faded copy of {@link #netPlayerItemFlow} (raw units). Gets the same
     * per-fill deltas, but decays towards 0 every tick with the half-life
     * {@code MARKET.NET_FLOW_HALF_LIFE_MINUTES}, so a one-off burst of buys/sells fades out while
     * sustained demand keeps it up. This is the value the price plugins read. Persisted.
     */
    private double fadedNetPlayerItemFlow = 0;
    /** Duration of one server tick in seconds; the faded flow decays per {@link #update()} call. */
    public static final double TICK_SECONDS = 0.05;

    private MarketSettings settings;

    // Callback fired when this market enters maintenance, used by ServerMarketManager to cancel inter-market orders
    private @Nullable Consumer<ItemID> marketMaintenanceCallback;

    // ---- Trading hours (T-158) ----
    // Runtime "inside trading hours" flag. When false: putOrder rejects ALL orders (player, bot,
    // inter-market) and update() skips matching, so the price stays static. Pending/resting orders
    // are never cancelled by a scheduled close and cancelOrder stays allowed.
    // Recomputed at the end of every ServerMarketManager.update() via updateTradingHours().
    private boolean tradingOpen = true;
    // Manual open/close override set by command. null = follow the schedule. Persisted.
    // Cleared automatically on the next scheduled transition.
    private @Nullable Boolean tradingOverride = null;
    // Schedule result of the last evaluation; used to detect scheduled transitions. Persisted.
    private boolean lastScheduledOpen = true;

    /**
     * Input: Real price
     * Output Real volume at the price
     */
    private @Nullable Function<Double, Float> defaultVolumeProviderFunction;

    private long candleStartTime = System.currentTimeMillis();
    private long candleOpenPrice;
    private long candleHighPrice;
    private long candleLowPrice;
    // T-175: accumulated in RAW units as long so small fills still count after a huge one
    // (a float sum stops changing once it exceeds ~2^24 times the fill). Widened to float only
    // at the API/storage boundary (MarketPriceStruct, getCurrentCandleTradedVolume()).
    private long candleTradedVolume = 0;

    /**
     * Temporary order buffers for collecting order async and only process the order on update
     * These order are not saved to NBT
     */
    private final PriorityQueue<Order> buyMarketOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellMarketOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));
    private final PriorityQueue<Order> buyLimitOrders_inputBuffer = new PriorityQueue<>((o1, o2) -> Long.compare(o2.getStartPrice(), o1.getStartPrice()));
    private final PriorityQueue<Order> sellLimitOrders_inputBuffer = new PriorityQueue<>(Comparator.comparingLong(Order::getStartPrice));


    public ServerMarket(ItemID itemID, @Nullable Function<Double, Float> volumeProvider, long defaultPrice, float naturalAbundance)
    {
        this.settings = new MarketSettings(false, defaultPrice, naturalAbundance);
        this.defaultVolumeProviderFunction =  volumeProvider;
        this.itemID = itemID;
        this.orderbook = new Orderbook(itemID,
                                        this::onOrderConsumed,
                                        this::onOrderCanceled,
                                        this::defaultVolumeProvider);
        this.matchingEngine = new MatchingEngine(this.itemID, this.orderbook,
                buyMarketOrders_inputBuffer,
                sellMarketOrders_inputBuffer,
                buyLimitOrders_inputBuffer,
                sellLimitOrders_inputBuffer,
                this::onOrderConsumed,
                this::onOrderCanceled,
                this::onPriceChanged);

        this.currentMarketPrice = defaultPrice;
        this.orderbook.setCurrentMarketPrice(defaultPrice);
        this.orderbook.resetVirtualVolumeDistribution();
        candleOpenPrice = currentMarketPrice;
        candleHighPrice = currentMarketPrice;
        candleLowPrice = currentMarketPrice;
    }

    public ServerMarket(ItemID itemID)
    {
        this(itemID, null, 1000, 10f);
    }

    @Override
    public void test_resetVirtualVolumeDistribution()
    {
        orderbook.resetVirtualVolumeDistribution();
    }
    @Override
    public void test_setCurrentMarketPrice(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        this.orderbook.setCurrentMarketPrice(currentMarketPrice);
    }

    /**
     * Updates only the reported market price without redistributing virtual orderbook depth.
     * Used by the cross-market bilateral walk which already consumed depth via fillVirtual —
     * calling orderbook.setCurrentMarketPrice would shift the DynamicIndexedArray window
     * and regenerate default volumes, draining depth that wasn't actually traded.
     */
    public void setCurrentMarketPriceNoRedistribute(long newPrice)
    {
        this.currentMarketPrice = newPrice;
    }
    @Override
    public void test_clearOrderbook()
    {
        orderbook.clear();
        orderbook.resetVirtualVolumeDistribution();
    }

    /**
     * Test-only helper: empties all four incoming order buffers
     * ({@code buyMarketOrders_inputBuffer}, {@code sellMarketOrders_inputBuffer},
     * {@code buyLimitOrders_inputBuffer}, {@code sellLimitOrders_inputBuffer}).
     *
     * <p>These buffers stage orders submitted via {@link #putOrder(Order)} until the
     * next {@link #update()} tick drains them into the matching engine. If a test
     * leaves entries behind, a following test that calls {@code update()} will
     * process those stale orders and observe unexpected price movement — this
     * helper prevents that cross-test contamination.
     *
     * <p>This method does <b>not</b> touch the orderbook, market price, candle state,
     * bank balances, or the market-open flag. Callers must reset any dependent state
     * they rely on separately.
     */
    @Override
    public void test_clearIncomingOrderBuffers()
    {
        buyMarketOrders_inputBuffer.clear();
        sellMarketOrders_inputBuffer.clear();
        buyLimitOrders_inputBuffer.clear();
        sellLimitOrders_inputBuffer.clear();
    }
    @Override
    public void test_setDefaultVolumeProviderFunction(Function<Double, Float> defaultVolumeProviderFunction)
    {
        this.defaultVolumeProviderFunction = defaultVolumeProviderFunction;
    }
    @Override
    public void test_resetVirtualOrderBookVolume()
    {
        orderbook.resetVirtualVolumeDistribution();
    }

    /**
     * Sets a callback that fires when this market enters maintenance ({@code setMaintenance(true)}).
     * Used by ServerMarketManager to cancel inter-market orders referencing this market.
     *
     * @param callback receives the ItemID of the market entering maintenance, or {@code null} to clear
     */
    public void setMarketMaintenanceCallback(@Nullable Consumer<ItemID> callback) {
        this.marketMaintenanceCallback = callback;
    }



    @Override
    public ItemID getItemID()
    {
        return itemID;
    }
    /**
     * {@inheritDoc}
     * <p>{@link MarketSettings#maxOrderVolume} is already stored in real items, so it is returned
     * as-is (no raw-to-real conversion, that would divide it by the item fraction scale factor).
     */
    @Override
    public double getMaxRealOrderVolume()
    {
        return settings.maxOrderVolume;
    }
    @Override
    public ItemID getItemIDAsync() {
        return itemID;
    }

    @Override
    public long getDefaultPrice()
    {
        return settings.defaultPrice;
    }
    @Override
    public CompletableFuture<Long> getDefaultPriceAsync()
    {
        return CompletableFuture.completedFuture(settings.defaultPrice);
    }




    @Override
    public long getCurrentMarketPrice()
    {
        return currentMarketPrice;
    }
    @Override
    public CompletableFuture<Long> getCurrentMarketPriceAsync() {
        return CompletableFuture.completedFuture(currentMarketPrice);
    }






    @Override
    public long getCurrentTime()
    {
        return System.currentTimeMillis();
    }
    @Override
    public CompletableFuture<Long> getCurrentTimeAsync() {
        return CompletableFuture.completedFuture(getCurrentTime());
    }






    @Override
    public long getRawVolume(long price)
    {
        return orderbook.getRawVolumeRounded(price);
    }
    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long price) {
        return CompletableFuture.completedFuture(getRawVolume(price));
    }



    @Override
    public long getRawVolume(long startPrice, long endPrice)
    {
        return orderbook.getRawVolume(startPrice, endPrice);
    }

    @Override
    public CompletableFuture<Long> getRawVolumeAsync(long startPrice, long endPrice)
    {
        return CompletableFuture.completedFuture(getRawVolume(startPrice, endPrice));
    }





    @Override
    public float getRealVolume(double price)
    {
        return orderbook.getRealVolumeRounded(price);
    }
    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double price)
    {
        return CompletableFuture.completedFuture(getRealVolume(price));
    }






    @Override
    public float getRealVolume(double startPrice, double endPrice)
    {
        return orderbook.getRealVolume(startPrice, endPrice);
    }
    @Override
    public CompletableFuture<Float> getRealVolumeAsync(double startPrice, double endPrice)
    {
        return CompletableFuture.completedFuture(getRealVolume(startPrice, endPrice));
    }




    // Buffers the incoming order, execution will take place in the update()
    @Override
    public boolean putOrder(Order order)
    {
        if(order.isFilled())
            return false;
        if(settings.maintenance && order.isPlayerOrder())
            return false;
        // Outside trading hours (or closed by override) no new orders of any kind are accepted.
        if(!tradingOpen)
            return false;
        if(!order.getItemID().equals(itemID))
            return false; // Wrong stockmarket for this order
        // T-191: accepted player orders get their persisted ID (inter-market legs use the group ID as ledger ref)
        if(order.isPlayerOrder() && order.getType() != Order.Type.INTER_MARKET)
            order.assignOrderIdIfMissing();
        if(order.isBuyOrder())
        {
            if(order.isMarketOrder() || order.getType() == Order.Type.INTER_MARKET)
                buyMarketOrders_inputBuffer.add(order);
            else
                buyLimitOrders_inputBuffer.add(order);
        }
        else
        {
            if(order.isMarketOrder() || order.getType() == Order.Type.INTER_MARKET)
                sellMarketOrders_inputBuffer.add(order);
            else
                sellLimitOrders_inputBuffer.add(order);
        }
        return true;
    }
    @Override
    public CompletableFuture<Boolean> putOrderAsync(Order order) {
        return CompletableFuture.completedFuture(putOrder(order));
    }


    @Override
    public boolean cancelOrder(UUID executor, long time, Order.Type type, long startPrice, long targetVolume) {
        // Search limit orders in the orderbook for an order matching all five criteria
        List<Order> orders = getLimitOrders();
        for (Order order : orders) {
            if (order.getExecutorPlayerUUID() != null
                    && order.getExecutorPlayerUUID().equals(executor)
                    && order.getTime() == time
                    && order.getType() == type
                    && order.getStartPrice() == startPrice
                    && order.getTargetVolume() == targetVolume) {
                orderbook.removeOrder(order);
                // unlockRemainingFunds is now called inside onOrderCanceled (Fix 3),
                // so we no longer invoke it here — that would double-unlock.
                onOrderCanceled(order);
                return true;
            }
        }
        return false;
    }

    public PriorityQueue<Order> getIncomingBuyMarketOrders()
    {
        return buyMarketOrders_inputBuffer;
    }
    public PriorityQueue<Order> getIncomingSellMarketOrders()
    {
        return sellMarketOrders_inputBuffer;
    }
    public PriorityQueue<Order> getIncomingBuyLimitOrders()
    {
        return buyLimitOrders_inputBuffer;
    }
    public PriorityQueue<Order> getIncomingSellLimitOrders()
    {
        return sellLimitOrders_inputBuffer;
    }
    public List<Order> getIncomingOrders()
    {
        List<Order> list = new ArrayList<>();
        list.addAll(buyMarketOrders_inputBuffer);
        list.addAll(sellMarketOrders_inputBuffer);
        list.addAll(buyLimitOrders_inputBuffer);
        list.addAll(sellLimitOrders_inputBuffer);
        return list;
    }



    @Override
    public List<Order> getLimitOrders()
    {
        return orderbook.getLimitOrders();
    }
    @Override
    public CompletableFuture<List<Order>> getLimitOrdersAsync() {
        return CompletableFuture.completedFuture(getLimitOrders());
    }





    @Override
    public boolean isUnderMaintenance()
    {
        return settings.maintenance;
    }
    @Override
    public CompletableFuture<Boolean> isUnderMaintenanceAsync() {
        return CompletableFuture.completedFuture(settings.maintenance);
    }





    @Override
    public boolean setMaintenance(boolean maintenance)
    {
        this.settings.maintenance = maintenance;
        if (maintenance) {
            cancelAllPlayerOrders();
            if (marketMaintenanceCallback != null) {
                marketMaintenanceCallback.accept(itemID);
            }
        }
        return true;
    }

    private void cancelAllPlayerOrders() {
        List<Order> limitOrders = new ArrayList<>(getLimitOrders());
        int cancelledCount = 0;
        for (Order order : limitOrders) {
            if (order.isBotOrder()) continue;
            orderbook.removeOrder(order);
            // unlockRemainingFunds is now called inside onOrderCanceled (Fix 3),
            // so we no longer invoke it here — that would double-unlock.
            onOrderCanceled(order);
            cancelledCount++;
        }
        if (cancelledCount > 0) {
            info("Maintenance enabled: cancelled " + cancelledCount + " player order(s)");
        }
    }
    @Override
    public CompletableFuture<Boolean> setMaintenanceAsync(boolean maintenance) {
        return CompletableFuture.completedFuture(setMaintenance(maintenance));
    }

    @Override
    public boolean isTradingOpen()
    {
        return tradingOpen;
    }

    @Override
    public boolean setTradingHours(int openMinute, int closeMinute)
    {
        applyTradingWindow(openMinute, closeMinute);
        return true;
    }
    @Override
    public CompletableFuture<Boolean> setTradingHoursAsync(int openMinute, int closeMinute)
    {
        return CompletableFuture.completedFuture(setTradingHours(openMinute, closeMinute));
    }

    @Override
    public boolean setTradingOverride(boolean open)
    {
        // Only stored here; applied by the next updateTradingHours() call at the end of the tick
        // so the state never flips while order buffers are being processed.
        tradingOverride = open;
        return true;
    }
    @Override
    public CompletableFuture<Boolean> setTradingOverrideAsync(boolean open)
    {
        return CompletableFuture.completedFuture(setTradingOverride(open));
    }

    /**
     * @return the active manual override ({@code true} = forced open, {@code false} = forced closed),
     *         or {@code null} when the market follows its schedule
     */
    public @Nullable Boolean getTradingOverride()
    {
        return tradingOverride;
    }

    @Override
    public void updateTradingHours(int minuteOfDay)
    {
        boolean scheduled = settings.isWithinTradingHours(minuteOfDay);
        // A scheduled transition (also caused by a window edit or a clock-source switch that
        // flips the result) ends any manual override.
        if (scheduled != lastScheduledOpen) {
            lastScheduledOpen = scheduled;
            tradingOverride = null;
        }
        tradingOpen = tradingOverride != null ? tradingOverride : scheduled;
    }

    /**
     * Clamps the window to [0, 1440] and normalizes every "whole day" form (open == close,
     * open 24:00) to 00:00-24:00. The open state itself is re-evaluated on the next tick.
     */
    private void applyTradingWindow(int openMinute, int closeMinute)
    {
        int open = Math.max(0, Math.min(1440, openMinute));
        int close = Math.max(0, Math.min(1440, closeMinute));
        if (open == 1440) open = 0;
        if (open == close || (open == 0 && close == 1440)) {
            open = 0;
            close = 1440;
        }
        settings.tradingOpenMinute = open;
        settings.tradingCloseMinute = close;
    }






    @Override
    public MarketPriceStruct getCurrentMarketPriceStruct()
    {
        return new MarketPriceStruct(itemID.getShort(), candleOpenPrice, candleLowPrice, candleHighPrice, candleStartTime, candleTradedVolume);
    }
    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync() {
        return CompletableFuture.completedFuture(getCurrentMarketPriceStruct());
    }





    @Override
    public MarketPriceStruct getCurrentMarketPriceStructAndReset()
    {
        MarketPriceStruct  currentMarketPriceStruct = new MarketPriceStruct(itemID.getShort(), candleOpenPrice, candleLowPrice, candleHighPrice, candleStartTime, candleTradedVolume);
        candleStartTime =  System.currentTimeMillis();
        candleOpenPrice = this.currentMarketPrice;
        candleHighPrice = this.currentMarketPrice;
        candleLowPrice = this.currentMarketPrice;
        candleTradedVolume = 0;
        return currentMarketPriceStruct;
    }
    @Override
    public CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync() {
        return CompletableFuture.completedFuture(getCurrentMarketPriceStructAndReset());
    }


    @Override
    public MarketSettings getSettings()
    {
        settings.netPlayerItemFlow = netPlayerItemFlow;
        settings.fadedNetPlayerItemFlow = Math.round(fadedNetPlayerItemFlow);
        return settings;
    }

    @Override
    public long getNetPlayerItemFlow() { return netPlayerItemFlow; }
    @Override
    public void resetNetPlayerItemFlow() { netPlayerItemFlow = 0; fadedNetPlayerItemFlow = 0; }
    @Override
    public void setNetPlayerItemFlow(long netPlayerItemFlow) { this.netPlayerItemFlow = netPlayerItemFlow; }
    @Override
    public double getFadedNetPlayerItemFlow() { return fadedNetPlayerItemFlow; }
    @Override
    public void setFadedNetPlayerItemFlow(double fadedNetPlayerItemFlow) { this.fadedNetPlayerItemFlow = fadedNetPlayerItemFlow; }

    /**
     * Exponential decay of a value over {@code dtSeconds}: {@code value * 2^(-dt / halfLife)}.
     * Pure function, so the decay math can be tested in isolation.
     *
     * @param value           the value to fade
     * @param halfLifeSeconds time after which the value has halved; &lt;= 0 (or NaN) = no fading
     * @param dtSeconds       elapsed time in seconds
     * @return the faded value
     */
    public static double fade(double value, double halfLifeSeconds, double dtSeconds)
    {
        if (!(halfLifeSeconds > 0) || value == 0) return value;
        return value * Math.pow(2.0, -dtSeconds / halfLifeSeconds);
    }

    /** @return the configured net flow half-life in seconds, 0 = no fading (also before the settings exist). */
    public static double netFlowHalfLifeSeconds()
    {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null) return 0;
        Float minutes = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.NET_FLOW_HALF_LIFE_MINUTES.get();
        return minutes == null ? 0 : minutes * 60.0;
    }
    @Override
    public CompletableFuture<Boolean> resetNetPlayerItemFlowAsync()
    {
        resetNetPlayerItemFlow();
        return CompletableFuture.completedFuture(true);
    }

    /**
     * @return whether the virtual orderbook is enabled for this market
     * (mirrors {@link MarketSettings#virtualOrderbookEnabled}).
     */
    public boolean isVirtualOrderbookEnabled()
    {
        return settings.virtualOrderbookEnabled;
    }

    /**
     * Refills the virtual orderbook from the plugin-provided default volume distribution
     * function AND clears the sticky-clear flag on the settings, so subsequent shift-fills
     * repopulate normally again. Distinct from {@link #clearVirtualOrderbook()}, which
     * zeroes everything and makes the shift-fill sticky at 0.
     * <p>
     * No-op when the VO subsystem is disabled — there is nothing to refill, and Clear/Reset
     * from the client are already gated on the same flag (see MarketSettingsWidget).
     */
    @Override
    public void resetVirtualOrderbook()
    {
        if (!settings.virtualOrderbookEnabled) return;
        settings.virtualOrderbookCleared = false;
        orderbook.setVirtualCleared(false);
        orderbook.resetVirtualVolumeDistribution();
    }
    @Override
    public CompletableFuture<Boolean> resetVirtualOrderbookAsync()
    {
        resetVirtualOrderbook();
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Zeros every price level in the virtual orderbook AND sets the sticky-clear flag so
     * any newly-visible slot introduced by a later array shift also lands at 0. Persisted
     * via {@link MarketSettings#virtualOrderbookCleared} so the state survives restarts.
     */
    @Override
    public void clearVirtualOrderbook()
    {
        // No-op when the VO subsystem is disabled — the array is already zero and the
        // sticky-clear flag is a semantically-separate admin decision that should only
        // apply while the subsystem is active.
        if (!settings.virtualOrderbookEnabled) return;
        settings.virtualOrderbookCleared = true;
        orderbook.clearVirtualVolume();
    }
    @Override
    public CompletableFuture<Boolean> clearVirtualOrderbookAsync()
    {
        clearVirtualOrderbook();
        return CompletableFuture.completedFuture(true);
    }
    @Override
    public CompletableFuture<MarketSettings> getSettingsAsync()
    {
        return CompletableFuture.completedFuture(getSettings());
    }





    @Override
    public void setSettings(MarketSettings settings)
    {
        // Detect the enabled→disabled transition BEFORE we overwrite the field so we
        // can zero the array on the way in. When the subsystem is re-enabled later,
        // plugins re-populate on their next tick (they gate on isVirtualOrderbookEnabled).
        boolean wasEnabled = this.settings.virtualOrderbookEnabled;
        boolean willBeEnabled = settings.virtualOrderbookEnabled;

        // Entering maintenance via the settings panel must have the same side effects as
        // setMaintenance(true) (cancel + refund player orders, cancel inter-market orders);
        // otherwise only the flag would flip and open player orders would stay in the book.
        if (settings.maintenance && !this.settings.maintenance)
            setMaintenance(true);
        else
            this.settings.maintenance = settings.maintenance;
        this.settings.defaultPrice = settings.defaultPrice;
        this.settings.naturalAbundance = settings.naturalAbundance;
        this.settings.virtualOrderbookEnabled = settings.virtualOrderbookEnabled;
        // Sticky-clear is persisted; propagate the incoming value and keep the
        // VirtualOrderbook runtime flag in sync so shift-fill immediately observes it.
        this.settings.virtualOrderbookCleared = settings.virtualOrderbookCleared;
        orderbook.setVirtualCleared(settings.virtualOrderbookCleared);
        // Keep the runtime "disabled" flag on the VirtualOrderbook in sync with the
        // enabled setting (inverted). Source-gate approach: this single flag makes all
        // VO reads return 0 and drops all writes.
        orderbook.setVirtualDisabled(!willBeEnabled);
        this.settings.ignorePluginAutosubscribe = settings.ignorePluginAutosubscribe;
        this.settings.maxOrderVolume = Math.max(0, settings.maxOrderVolume); // 0 = unlimited
        applyTradingWindow(settings.tradingOpenMinute, settings.tradingCloseMinute);
        // netPlayerItemFlow is intentionally not copied from client settings

        // Transition-zero on enable→disable: task 4 requires that the VO stays empty
        // while disabled so persisted state is consistent. Zero the array (WITHOUT
        // touching the sticky-clear flag — that's a separate admin decision) by
        // triggering the default-value refill, which now yields 0 for every slot
        // because the disabled flag routes getDefaultVolume through the zero path.
        if (wasEnabled && !willBeEnabled) {
            orderbook.resetVirtualVolumeDistribution();
        }
    }
    @Override
    public CompletableFuture<Boolean> setSettingsAsync(MarketSettings settings)
    {
        setSettings(settings);
        return CompletableFuture.completedFuture(true);
    }


    @Override
    public float getCurrentCandleTradedVolume()
    {
        return candleTradedVolume;
    }

    @Override
    public Orderbook getOrderbook()
    {
        return orderbook;
    }









    @Override
    public void update()
    {
        // #89: tick-time decay of the faded net flow (runs while closed too: no fills, still fades).
        fadedNetPlayerItemFlow = fade(fadedNetPlayerItemFlow, netFlowHalfLifeSeconds(), TICK_SECONDS);
        // T-198: consumed virtual depth outside the array window recovers on tick time (also while closed).
        orderbook.fadeVirtualDepthMemory(TICK_SECONDS);
        // Closed (outside trading hours / override): no matching, price stays static. Buffered
        // orders stay buffered; putOrder already rejects new ones while closed.
        if (!tradingOpen)
            return;
        orderbook.setCurrentMarketPrice(currentMarketPrice);

        matchingEngine.update(currentMarketPrice);
        candleTradedVolume += matchingEngine.getLastTradedVolume();
        trackPlayerNetFlow(matchingEngine.getLastPlayerFilledVolume());

        // Update the current candle
        candleLowPrice = Math.min(candleLowPrice, currentMarketPrice);
        candleHighPrice = Math.max(candleHighPrice, currentMarketPrice);
    }

    /**
     * Gets called when the provided order has been consumed (fully filled)
     * @param order the consumed order
     */
    private void onOrderConsumed(Order order)
    {
        unlockRemainingFunds(order);
        logLedgerClose(order);
        saveOrderRecord(order);
        trackRealizedProfit(order);
    }

    /**
     * Gets called when the provided order has been canceled.
     * It may be partially filled.
     * <p>
     * Fix 3: previously this path skipped {@link #unlockRemainingFunds(Order)}, which
     * silently leaked the buyer's reserved money or seller's reserved items back into
     * BankSystem's locked pool when a market order could not be fully filled (empty
     * opposing side + no virtual liquidity). The matching engine already routes
     * unfilled market orders through {@code orderCanceled} in {@code commitSell} /
     * {@code commitBuy}, so the fix is to actually release the reservation here — the
     * same path a user-initiated cancel takes for a resting limit order.
     * <p>
     * {@code unlockRemainingFunds} is safe to call for market orders: for sells it
     * unlocks {@code -remainingVolume} items (0 when fully filled → no-op); for buys
     * it unlocks {@code targetLocked - alreadySpent} money (0 when fully filled → no-op).
     * So a partial fill followed by cancel-remainder releases exactly the unfilled
     * remainder's reservation.
     * @param order the canceled order
     */
    private void onOrderCanceled(Order order)
    {
        unlockRemainingFunds(order);
        logLedgerClose(order);
        saveOrderRecord(order);
        trackRealizedProfit(order);
        notifyMarketOrderNotFullyFilled(order);
    }

    /**
     * #89: feeds a finished player order's net money (all fills of the order, sell proceeds
     * positive, buy costs negative) into the per-player profit alert. Inter-market legs count too,
     * so a pair trade nets out to its real money result.
     *
     * @param order the consumed or canceled order (fill state is final)
     */
    private void trackRealizedProfit(Order order)
    {
        if (order.isBotOrder() || order.getFilledVolume() == 0 || order.getExecutorPlayerUUID() == null)
            return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null)
            return;
        StockMarketModSettings.Market s = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET;
        Float threshold = s.PROFIT_ALERT_THRESHOLD.get();
        Long window = s.PROFIT_ALERT_WINDOW_MINUTES.get();
        if (threshold == null || threshold <= 0 || window == null)
            return;
        ProfitAlerts.onOrderFinished(order.getExecutorPlayerUUID(),
                MarketManager.convertToRealAmountStatic(order.getTransferredMoney()), threshold, window);
    }

    /**
     * T-173: tells the owner of a player MARKET order that it was only partly filled. The
     * matching engine cancels (and refunds) whatever a market order could not fill in its
     * one pass: virtual-book walk limit, price floor 0, buyer out of money, empty book.
     * {@code CreateOrderRequest} already answered "created" at placement (matching runs later
     * in the tick), so without this the player only saw a plain success.
     * <p>Sent as a translatable chat message (resolved client-side). Only reaches players
     * online on this (master) server; players on a slave server get no message.</p>
     *
     * @param order the canceled order (fill state is final at this point)
     */
    private void notifyMarketOrderNotFullyFilled(Order order)
    {
        if (!order.isMarketOrder() || order.isBotOrder() || order.isFilled())
            return;
        ServerPlayer player = ServerPlayerUtilities.getOnlinePlayer(order.getExecutorPlayerUUID());
        if (player == null)
            return;
        double sf = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
        player.sendSystemMessage(Component.translatable("message.stockmarket.order_status.market_order_partially_filled",
                String.format(Locale.ROOT, "%.2f", Math.abs(order.getFilledVolume()) / sf),
                String.format(Locale.ROOT, "%.2f", Math.abs(order.getTargetVolume()) / sf),
                itemID.getStack().getHoverName()));
    }

    /**
     * T-191: writes the CLOSE ledger row (fills not yet covered by earlier rows) of a finished player
     * order. Inter-market legs are skipped: the market manager logs them under the group ref.
     * Also gives orders from older saves their ID, so the OrderHistory row carries it.
     *
     * @param order the consumed or canceled order (fill state is final)
     */
    private void logLedgerClose(Order order)
    {
        if (order.isBotOrder() || order.getType() == Order.Type.INTER_MARKET) return;
        order.assignOrderIdIfMissing();
        ItemID currency = ledgerCurrency();
        if (currency != null)
            OrderLedger.logDelta(order, OrderLedger.orderRef(order.getOrderID()), OrderLedger.CLOSE, currency);
    }

    /**
     * T-191: writes a {@link OrderLedger#PERIODIC} or {@link OrderLedger#SHUTDOWN} row for every resting
     * player order of this market whose fill state changed since its last row.
     *
     * @param rowKind {@link OrderLedger#PERIODIC} or {@link OrderLedger#SHUTDOWN}
     * @return the number of rows written
     */
    public int logOpenOrdersToLedger(String rowKind)
    {
        ItemID currency = ledgerCurrency();
        if (currency == null) return 0;
        int rows = 0;
        for (Order order : orderbook.getLimitOrders())
        {
            if (order.isBotOrder() || order.getType() == Order.Type.INTER_MARKET) continue;
            order.assignOrderIdIfMissing();
            if (OrderLedger.logDelta(order, OrderLedger.orderRef(order.getOrderID()), rowKind, currency))
                rows++;
        }
        return rows;
    }

    /** @return the trading currency for ledger rows, or null if the market manager is not available */
    private static @Nullable ItemID ledgerCurrency()
    {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null || BACKEND_INSTANCES.MARKET_MANAGER.getSync() == null)
            return null;
        return BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID();
    }

    /**
     * Saves a completed or canceled player order to the OrderHistory SQL table.
     * Bot orders (no player executor) are skipped.
     * @param order the order to save as a historical record
     */
    private void saveOrderRecord(Order order)
    {
        if (order.isBotOrder()) return; // skip bot orders (no player executor)
        if (order.getFilledVolume() == 0) return; // skip orders with no fills (e.g. cancelled before any execution)
        if (order.getType() == Order.Type.INTER_MARKET) return; // saved by InterMarketExecutor with groupID
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.ORDER_RECORD_MANAGER == null) return;

        OrderRecordStruct record = order.getHistoricalRecord();
        BACKEND_INSTANCES.ORDER_RECORD_MANAGER.save(record);
    }


    /**
     * Tracks the net player item flow, per fill (not when an order completes), so a large
     * order's impact reaches the price plugins while it is still filling.
     * filledVolume is negative for sell fills, so subtracting it adds to the counter.
     * Bot and inter-market orders are excluded by the matching engine.
     */
    private void trackPlayerNetFlow(long filledVolume) {
        netPlayerItemFlow -= filledVolume;
        fadedNetPlayerItemFlow -= filledVolume;
    }

    /**
     * Credits a real player fill to this market's net player item flow directly, bypassing
     * the per-update accumulator that {@link #update()} drains.
     *
     * <p><b>When to use this and not the accumulator.</b> The normal path is
     * {@code MatchingEngine.trackPlayerFill} → {@code getLastPlayerFilledVolume()} →
     * {@link #trackPlayerNetFlow(long)}, which only works for fills that happen
     * <i>inside</i> {@code matchingEngine.update(..)}: that method zeroes
     * {@code playerFilledVolumeSinceLastReset} on entry, so anything accumulated outside
     * an update() call is discarded before it is ever read. The bilateral inter-market
     * walk ({@code MatchingEngine.executeCrossMarketLimitOrder}) fills real orders on two
     * markets without calling {@link #update()} on either one, so it must credit here.</p>
     *
     * <p><b>Sign.</b> {@code signedVolume} uses the same convention as an order's filled
     * volume: <b>positive = the player received items</b> (a buy, i.e. demand), <b>negative
     * = the player gave items away</b> (a sell, i.e. supply). The stored counter is the
     * negation of that, so a player buy lowers {@code netPlayerItemFlow} and a player sell
     * raises it — matching {@code VolatilityPlugin.computeEquilibriumPrice}, which reads
     * the counter as net <i>supply</i> pressure.</p>
     *
     * <p>Call once per fill, and only for player orders — bot fills and virtual-depth fills
     * are not player supply/demand and must not be credited.</p>
     *
     * @param signedVolume raw fill volume, positive when the player received items
     */
    public void addPlayerItemFlow(long signedVolume) {
        trackPlayerNetFlow(signedVolume);
    }

    /**
     * Fires the "order fully consumed" bookkeeping for an order that was filled outside
     * this market's {@link MatchingEngine} loop (currently only the bilateral inter-market
     * depth walk, which consumes resting orders through static helpers that have no access
     * to the engine's callbacks). Releases any leftover reservation and writes the order
     * history record — exactly what the in-loop {@code consumedOrderCallback} does.
     *
     * @param order the resting order that was fully filled
     */
    public void notifyOrderConsumed(Order order) {
        onOrderConsumed(order);
    }

    /**
     * Fires the "order canceled" bookkeeping for an order whose remainder was dropped
     * outside this market's {@link MatchingEngine} loop. Critically this runs
     * {@link #unlockRemainingFunds(Order)}: without it the canceled remainder's locked
     * money (buy) or locked items (sell) stay reserved in the player's bank forever.
     *
     * @param order the resting order whose unfilled remainder was canceled
     */
    public void notifyOrderCanceled(Order order) {
        onOrderCanceled(order);
    }

    /**
     * Unlocks the bank funds reserved for the unfilled portion of a cancelled order.
     * Buy orders locked money (remainingVolume * startPrice) in the money bank.
     * Sell orders locked items (abs(remainingVolume)) in the item bank.
     * If the bank account or bank is unavailable (e.g. player left), logs a warning and skips.
     */
    private void unlockRemainingFunds(Order order) {
        if (order.isBotOrder()) return; // bot orders have no bank reservations
        // #93: INTER_MARKET legs hold no reservation of their own; the inter-market executor
        // owns the parent order's locks and releases them itself. Unlocking here would free
        // the reservations of the player's other orders (or the parent's items twice).
        if (order.getType() == Order.Type.INTER_MARKET) return;
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.BANK_SYSTEM_API == null) return;

        IServerBankAccount bankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API
                .getServerBankManager().getSync().getBankAccount(order.getBankAccountNr());
        if (bankAccount == null) {
            warn("Cannot unlock funds for cancelled order: bank account " + order.getBankAccountNr() + " not found");
            return;
        }

        if (order.isBuyOrder()) {
            ItemID moneyID = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID();
            IServerBank moneyBank = bankAccount.getBank(moneyID);
            if (moneyBank == null) {
                warn("Cannot unlock money for cancelled buy order: money bank not found for account " + order.getBankAccountNr());
                return;
            }
            // Prefer the stashed CreateOrderRequest reservation. For MARKET buy orders
            // the "targetVolume * startPrice / SF" formula yields 0 (startPrice is 0),
            // which caused the pre-fix partial-fill money leak. The stash is populated
            // in CreateOrderRequest after the successful lockAmount call; falls back to
            // the price formula for legacy orders (e.g. deserialized from NBT) that
            // pre-date this field or bot-placed orders where the stash isn't set.
            long stashed = order.getOriginalLockedMoney();
            long totalLocked = stashed > 0
                    ? stashed
                    : (long)Math.ceil((double)order.getTargetVolume() * order.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
            // transferredMoney is negative for buy orders (money spent); absolute value = already withdrawn from locked
            long alreadySpent = -order.getTransferredMoney();
            long toUnlock = totalLocked - alreadySpent;
            if (toUnlock <= 0) return;
            BankStatus status = moneyBank.unlockAmount(toUnlock);
            if (status != BankStatus.SUCCESS) {
                warn("Failed to unlock " + toUnlock + " money for cancelled buy order on account "
                        + order.getBankAccountNr() + ": " + status);
            }
        } else {
            // Sell order: unlock reserved items = abs(remainingVolume)
            IServerBank itemBank = bankAccount.getBank(itemID);
            if (itemBank == null) {
                warn("Cannot unlock items for cancelled sell order: item bank not found for account " + order.getBankAccountNr());
                return;
            }
            long toUnlock = -order.getRemainingVolume();
            if (toUnlock <= 0) return;
            BankStatus status = itemBank.unlockAmount(toUnlock);
            if (status != BankStatus.SUCCESS) {
                warn("Failed to unlock " + toUnlock + " items for cancelled sell order on account "
                        + order.getBankAccountNr() + ": " + status);
            }
        }
    }

    private void onPriceChanged(long newPrice)
    {
        currentMarketPrice = newPrice;
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }


    /**
     * Input: Long: raw price level (backend price)
     * @return Float: raw volume (backend volume)
     */
    private float defaultVolumeProvider(long price)
    {
        IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync();
        double realPrice = bankManager.convertToRealAmount(price);
        if(defaultVolumeProviderFunction != null) {
            Float realVolume = defaultVolumeProviderFunction.apply(realPrice);
            if (realVolume == null) return 0;
            return bankManager.getItemFractionScaleFactor() * realVolume;
        }
        float volume = (float)Math.min(10, Math.max(-10, currentMarketPrice - realPrice));
        return volume;
    }

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        CompoundTag orderbookTag = new CompoundTag();
        success &= orderbook.save(orderbookTag);
        tag.put("orderbook", orderbookTag);
        tag.putLong("currentMarketPrice", currentMarketPrice);
        tag.putLong("defaultPrice", settings.defaultPrice);
        tag.putFloat("naturalAbundance", settings.naturalAbundance);
        tag.putLong("netPlayerItemFlow", netPlayerItemFlow);
        tag.putDouble("fadedNetPlayerItemFlow", fadedNetPlayerItemFlow);
        // Persist the new preset-derived flags so restarts keep the admin's intent.
        // Reads on the load side are guarded with contains() so old NBT still loads
        // with the field defaults (true / false / false respectively).
        tag.putBoolean("virtualOrderbookEnabled", settings.virtualOrderbookEnabled);
        tag.putBoolean("virtualOrderbookCleared", settings.virtualOrderbookCleared);
        tag.putBoolean("ignorePluginAutosubscribe", settings.ignorePluginAutosubscribe);
        // Persist the admin/integration maintenance flag so it survives restarts (missing on old NBT -> not under maintenance).
        tag.putBoolean("maintenance", settings.maintenance);
        // Trading hours. Override stored as byte: -1 = none, 0 = forced closed, 1 = forced open.
        tag.putInt("tradingOpenMinute", settings.tradingOpenMinute);
        tag.putInt("tradingCloseMinute", settings.tradingCloseMinute);
        tag.putLong("maxOrderVolume", settings.maxOrderVolume);
        tag.putByte("tradingOverride", tradingOverride == null ? (byte) -1 : (byte) (tradingOverride ? 1 : 0));
        tag.putBoolean("lastScheduledOpen", lastScheduledOpen);

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;

        if(tag.contains("orderbook"))
        {
            CompoundTag virtualOrderbookTag = NbtCompat.getCompound(tag, "orderbook");
            success &= orderbook.load(virtualOrderbookTag);
        }
        else
        {
            success = false;
            error("Can't load Orderbook from NBT tag");
        }

        if(tag.contains("currentMarketPrice"))
        {
            currentMarketPrice =  NbtCompat.getLong(tag, "currentMarketPrice");
        }
        if(tag.contains("defaultPrice"))
        {
            settings.defaultPrice = NbtCompat.getLong(tag, "defaultPrice");
        }
        if(tag.contains("naturalAbundance"))
        {
            settings.naturalAbundance = NbtCompat.getFloat(tag, "naturalAbundance");
        }
        if(tag.contains("netPlayerItemFlow"))
        {
            netPlayerItemFlow = NbtCompat.getLong(tag, "netPlayerItemFlow");
        }
        // #89: saves without the faded value start it at the real flow (what the plugins read until now).
        fadedNetPlayerItemFlow = tag.contains("fadedNetPlayerItemFlow")
                ? NbtCompat.getDouble(tag, "fadedNetPlayerItemFlow") : netPlayerItemFlow;
        if(tag.contains("virtualOrderbookEnabled"))
        {
            settings.virtualOrderbookEnabled = NbtCompat.getBoolean(tag, "virtualOrderbookEnabled");
        }
        if(tag.contains("virtualOrderbookCleared"))
        {
            settings.virtualOrderbookCleared = NbtCompat.getBoolean(tag, "virtualOrderbookCleared");
        }
        if(tag.contains("ignorePluginAutosubscribe"))
        {
            settings.ignorePluginAutosubscribe = NbtCompat.getBoolean(tag, "ignorePluginAutosubscribe");
        }
        // Max order volume: old saves without the key derive it from the (just loaded) abundance,
        // so live servers get the protection on upgrade. A stored 0 stays 0 (unlimited).
        settings.maxOrderVolume = tag.contains("maxOrderVolume")
                ? NbtCompat.getLong(tag, "maxOrderVolume")
                : MarketSettings.defaultMaxOrderVolume(settings.naturalAbundance);
        // Restore the maintenance flag only — do NOT call setMaintenance(): player orders were already
        // cancelled when maintenance was enabled, so the side effects must not re-run on load.
        settings.maintenance = tag.contains("maintenance") && NbtCompat.getBoolean(tag, "maintenance");
        // Trading hours: missing keys (old saves) -> always open, no override.
        applyTradingWindow(
                tag.contains("tradingOpenMinute") ? NbtCompat.getInt(tag, "tradingOpenMinute") : 0,
                tag.contains("tradingCloseMinute") ? NbtCompat.getInt(tag, "tradingCloseMinute") : 1440);
        byte override = tag.contains("tradingOverride") ? NbtCompat.getByte(tag, "tradingOverride") : -1;
        tradingOverride = override < 0 ? null : override == 1;
        lastScheduledOpen = !tag.contains("lastScheduledOpen") || NbtCompat.getBoolean(tag, "lastScheduledOpen");
        tradingOpen = tradingOverride != null ? tradingOverride : lastScheduledOpen;
        // Sync the runtime flags on the VirtualOrderbook with the loaded persisted state.
        // These flags live in memory only on VirtualOrderbook — the settings are the
        // source of truth across restarts.
        orderbook.setVirtualCleared(settings.virtualOrderbookCleared);
        orderbook.setVirtualDisabled(!settings.virtualOrderbookEnabled);

        // Initialize candle state from loaded price to prevent false spike on first candle
        candleOpenPrice = currentMarketPrice;
        candleHighPrice = currentMarketPrice;
        candleLowPrice = currentMarketPrice;
        candleTradedVolume = 0;
        candleStartTime = System.currentTimeMillis();

        return success;
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ServerMarket:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ServerMarket:"+itemID+"]: "+message);
    }


}
