package net.kroia.stockmarket.stockmarket.market;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.market.core.MatchingEngine;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private MarketSettings settings;

    // Callback fired when this market closes, used by ServerMarketManager to cancel inter-market orders
    private @Nullable Consumer<ItemID> marketClosedCallback;

    /**
     * Input: Real price
     * Output Real volume at the price
     */
    private @Nullable Function<Double, Float> defaultVolumeProviderFunction;

    private long candleStartTime = System.currentTimeMillis();
    private long candleOpenPrice;
    private long candleHighPrice;
    private long candleLowPrice;
    private float candleTradedVolume = 0;

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
        this.settings = new MarketSettings(true, defaultPrice, naturalAbundance);
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
     * Sets a callback that fires when this market closes (setMarketOpen(false)).
     * Used by ServerMarketManager to cancel inter-market orders referencing this market.
     */
    public void setMarketClosedCallback(@Nullable Consumer<ItemID> callback) {
        this.marketClosedCallback = callback;
    }



    @Override
    public ItemID getItemID()
    {
        return itemID;
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
        if(order.isFilled() || !settings.marketOpen)
            return false;
        if(!order.getItemID().equals(itemID))
            return false; // Wrong stockmarket for this order
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
                unlockRemainingFunds(order);
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
    public boolean isMarketOpen()
    {
        return settings.marketOpen;
    }
    @Override
    public CompletableFuture<Boolean> isMarketOpenAsync() {
        return CompletableFuture.completedFuture(settings.marketOpen);
    }





    @Override
    public boolean setMarketOpen(boolean marketOpen)
    {
        this.settings.marketOpen = marketOpen;
        if (!marketOpen) {
            cancelAllPlayerOrders();
            if (marketClosedCallback != null) {
                marketClosedCallback.accept(itemID);
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
            unlockRemainingFunds(order);
            onOrderCanceled(order);
            cancelledCount++;
        }
        if (cancelledCount > 0) {
            info("Market closed: cancelled " + cancelledCount + " player order(s)");
        }
    }
    @Override
    public CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen) {
        return CompletableFuture.completedFuture(setMarketOpen(marketOpen));
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
        return settings;
    }

    @Override
    public long getNetPlayerItemFlow() { return netPlayerItemFlow; }
    @Override
    public void resetNetPlayerItemFlow() { netPlayerItemFlow = 0; }
    @Override
    public CompletableFuture<Boolean> resetNetPlayerItemFlowAsync()
    {
        resetNetPlayerItemFlow();
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
        this.settings.marketOpen = settings.marketOpen;
        this.settings.defaultPrice = settings.defaultPrice;
        this.settings.naturalAbundance = settings.naturalAbundance;
        // netPlayerItemFlow is intentionally not copied from client settings
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
        orderbook.setCurrentMarketPrice(currentMarketPrice);

        if(settings.marketOpen)
        {
            matchingEngine.update(currentMarketPrice);
            candleTradedVolume += matchingEngine.getLastTradedVolume();
        }

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
        trackPlayerNetFlow(order);
        saveOrderRecord(order);
    }

    /**
     * Gets called when the provided order has been canceled
     * It may be partially filled
     * @param order the canceled order
     */
    private void onOrderCanceled(Order order)
    {
        trackPlayerNetFlow(order);
        saveOrderRecord(order);
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
     * Tracks the net player item flow for completed/cancelled orders.
     * filledVolume is negative for sell orders, so subtracting it adds to the counter.
     * Bot orders are excluded.
     */
    private void trackPlayerNetFlow(Order order) {
        if (order.isBotOrder()) return;
        if (order.getType() == Order.Type.INTER_MARKET) return; // tracked at inter-market level
        long filledVolume = order.getFilledVolume();
        if (filledVolume == 0) return;
        // filledVolume is negative for sell orders, so subtracting it adds to the counter
        netPlayerItemFlow -= filledVolume;
    }

    /**
     * Unlocks the bank funds reserved for the unfilled portion of a cancelled order.
     * Buy orders locked money (remainingVolume * startPrice) in the money bank.
     * Sell orders locked items (abs(remainingVolume)) in the item bank.
     * If the bank account or bank is unavailable (e.g. player left), logs a warning and skips.
     */
    private void unlockRemainingFunds(Order order) {
        if (order.isBotOrder()) return; // bot orders have no bank reservations
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
            // Total originally locked = ceil(targetVolume * startPrice / SF), matching CreateOrderRequest lock formula
            long totalLocked = (long)Math.ceil((double)order.getTargetVolume() * order.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
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

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        boolean success = true;

        if(tag.contains("orderbook"))
        {
            CompoundTag virtualOrderbookTag = tag.getCompound("orderbook");
            success &= orderbook.load(virtualOrderbookTag);
        }
        else
        {
            success = false;
            error("Can't load Orderbook from NBT tag");
        }

        if(tag.contains("currentMarketPrice"))
        {
            currentMarketPrice =  tag.getLong("currentMarketPrice");
        }
        if(tag.contains("defaultPrice"))
        {
            settings.defaultPrice = tag.getLong("defaultPrice");
        }
        if(tag.contains("naturalAbundance"))
        {
            settings.naturalAbundance = tag.getFloat("naturalAbundance");
        }
        if(tag.contains("netPlayerItemFlow"))
        {
            netPlayerItemFlow = tag.getLong("netPlayerItemFlow");
        }

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
