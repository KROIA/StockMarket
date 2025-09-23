package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankAccount;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerMarket implements IServerMarket, ServerSaveable {

    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }


    private final TradingPair tradingPair;
    private final OrderBook orderBook;
    private final MatchingEngine matchingEngine;
    private final HistoricalMarketData historicalMarketData;
    private ServerVolatilityBot volatilityBot;
    private boolean marketOpen = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.MARKET_OPEN_AT_CREATION.get();

    /**
     * The amount of items in this market that are received from the game world and vanished in the market.
     * If this value is positive, it means that more volume has been sold by players than bought
     */
    private long itemImbalance;
    private float defaultRealPrice = 0;
    private long shiftPriceCandleIntervalMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get();
    protected TimerMillis shiftPriceTimer = new TimerMillis(true);

    private int priceScaleFactor = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.DEFAULT_PRICE_SCALE.get();
    private int currencyItemFractionScaleFactor = 1;
    private int itemFractionScaleFactor = 1;
    private int smallesTradableVolume = 1;

    public ServerMarket(TradingPair pair, int virtualOrderBookArraySize, int historySize)
    {
        this(pair, 0, virtualOrderBookArraySize, historySize, 1);
    }
    public ServerMarket(TradingPair pair, float initialPrice, int virtualOrderBookArraySize, int historySize, int priceScaleFactor)
    {
        this.tradingPair = pair;
        this.defaultRealPrice = initialPrice;
        if(tradingPair == null)
        {
            throw new IllegalArgumentException("Trading pair cannot be null");
        }


        if(priceScaleFactor != 1 && priceScaleFactor != 10 && priceScaleFactor != 100)
        {
            // Auto set the price scale factor based on the initial price
            if (initialPrice < BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.PRICE_SCALE_100_DEFAULT_PRICE_THRESHOLD.get())
                this.priceScaleFactor = 100; // Use cents for prices below 20
            else if (initialPrice < BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.PRICE_SCALE_10_DEFAULT_PRICE_THRESHOLD.get())
                this.priceScaleFactor = 10; // Use deci for prices below 200
            else
                this.priceScaleFactor = 1; // Use whole units for prices above 200
        }
        else {
            this.priceScaleFactor = priceScaleFactor;
        }
        // Check if the price scale is larger than the currency scale, if so, reset the price scale to the currency scale.
        currencyItemFractionScaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getItemFractionScaleFactor(tradingPair.getCurrency());
        itemFractionScaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getItemFractionScaleFactor(tradingPair.getItem());
        /*if(this.priceScaleFactor * itemFractionScaleFactor > currencyItemFractionScaleFactor)
        {
            this.priceScaleFactor = ggt(currencyItemFractionScaleFactor, itemFractionScaleFactor);
            //this.priceScaleFactor = currencyItemFractionScaleFactor / itemFractionScaleFactor;
            if(this.priceScaleFactor <= 0)
            {
                this.priceScaleFactor = 1; // Fallback to 1 if the scale factor is zero or negative
            }
        }*/
        if(this.priceScaleFactor > itemFractionScaleFactor * currencyItemFractionScaleFactor)
        {
            switch(itemFractionScaleFactor)
            {
                case 1 -> {
                    this.priceScaleFactor = currencyItemFractionScaleFactor;
                }
                case 10 -> {
                    //if(currencyItemFractionScaleFactor % 10 == 0)
                    //    this.priceScaleFactor = currencyItemFractionScaleFactor / 10;
                    //else
                        this.priceScaleFactor = currencyItemFractionScaleFactor;
                }
                case 100 -> {
                    /*if(currencyItemFractionScaleFactor % 100 == 0)
                        this.priceScaleFactor = currencyItemFractionScaleFactor / 100;
                    else */if(currencyItemFractionScaleFactor % 10 == 0)
                        this.priceScaleFactor = currencyItemFractionScaleFactor / 10;
                    else
                        this.priceScaleFactor = currencyItemFractionScaleFactor;
                }
            }
        }
        smallesTradableVolume = Math.min(currencyItemFractionScaleFactor / this.priceScaleFactor, itemFractionScaleFactor);

        /*if(this.priceScaleFactor > currencyItemFractionScaleFactor)
            this.priceScaleFactor = currencyItemFractionScaleFactor;
        else {
            // Check if the currency scale is not a multiple of the price scale, if so, set the price scale to the nearest divisor of the currency scale.
            if(currencyItemFractionScaleFactor % this.priceScaleFactor != 0)
            {
                this.priceScaleFactor = findNearestDivisor(currencyItemFractionScaleFactor, this.priceScaleFactor);
            }
        }

        itemFractionScaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getItemFractionScaleFactor(tradingPair.getItem());
            // Check if the currency scale is not a multiple of the price scale, if so, set the price scale to the nearest divisor of the currency scale.
        if(itemFractionScaleFactor % this.priceScaleFactor != 0) {
            this.priceScaleFactor = findNearestDivisor(itemFractionScaleFactor, this.priceScaleFactor);
        }*/


        int rawInitialPrice = mapToRawPrice(defaultRealPrice);
        this.orderBook = new OrderBook(virtualOrderBookArraySize, rawInitialPrice);
        this.historicalMarketData = new HistoricalMarketData(rawInitialPrice, historySize);
        this.historicalMarketData.getHistory().setScaleFactors(this.priceScaleFactor, currencyItemFractionScaleFactor);
        this.orderBook.setScaleFactors(this.priceScaleFactor, itemFractionScaleFactor);
        this.matchingEngine = new MatchingEngine(this, historicalMarketData, orderBook, tradingPair);
        this.matchingEngine.setScaleFactors(this.priceScaleFactor, currencyItemFractionScaleFactor, itemFractionScaleFactor);

        this.volatilityBot = null;

        shiftPriceTimer.start(shiftPriceCandleIntervalMS);
        this.resetVirtualOrderBookVolumeDistribution();
        //notifySubscriberTimer.start(notifySubscriberIntervalMS);
    }
    public ServerMarket(int virtualOrderBookArraySize, int historySize)
    {
        this.tradingPair = new TradingPair();
        this.orderBook = new OrderBook(virtualOrderBookArraySize);
        this.historicalMarketData = new HistoricalMarketData(historySize);
        this.historicalMarketData.getHistory().setScaleFactors(priceScaleFactor, currencyItemFractionScaleFactor);
        this.orderBook.setScaleFactors(priceScaleFactor, itemFractionScaleFactor);
        this.matchingEngine = new MatchingEngine(this ,historicalMarketData, orderBook, tradingPair);
        this.matchingEngine.setScaleFactors(priceScaleFactor, currencyItemFractionScaleFactor, itemFractionScaleFactor);

        this.volatilityBot = null;

    }


    public void update(MinecraftServer server)
    {
        if(volatilityBot != null)
        {
            volatilityBot.update(server);
        }
        matchingEngine.processIncommingOrders(orderBook.getAndClearIncommingOrders());
        orderBook.updateVirtualOrderBookVolume(getCurrentRawPrice());

        if(shiftPriceTimer.check()) {
            shiftPriceHistory();
        }
    }

    @Override
    public @Nullable BotSettingsData getBotSettingsData()
    {
        if(volatilityBot == null)
            return null;
        return new BotSettingsData(tradingPair, (ServerVolatilityBot.Settings)volatilityBot.getSettings());
    }

    @Override
    public @Nullable VirtualOrderBookSettingsData getVirtualOrderBookSettingsData()
    {
        if(orderBook.getVirtualOrderBook() == null)
            return null;
        return new VirtualOrderBookSettingsData(tradingPair, orderBook.getVirtualOrderBook().getSettings());
    }

    @Override
    public TradingPairData getTradingPairData()
    {
        return new TradingPairData(tradingPair);
    }

    @Override
    public OrderBookVolumeData getOrderBookVolumeData(int historyViewCount, float minPrice, float maxPrice, int tileCount)
    {
        if(tileCount == 0)
        {
            tileCount = BACKEND_INSTANCES.SERVER_SETTINGS.UI.MAX_ORDERBOOK_TILES.get();
        }
        if(minPrice == 0 && maxPrice == 0)
        {
            minPrice = historicalMarketData.getHistory().getLowestRealPrice(historyViewCount);
            maxPrice = historicalMarketData.getHistory().getHighestRealPrice(historyViewCount);
            float range = (maxPrice - minPrice)/2;

            int minTileCount = 10;



            if(tileCount < minTileCount)
            {
                tileCount = minTileCount;
            }

            if(range < 1)
            {
                range = 1;
            }

            float pricePerMinTile = mapToRealPrice(1);
            /*if(range < pricePerMinTile * tileCount)
            {
                range = pricePerMinTile * tileCount;
            }*/
            minPrice -= range;
            maxPrice += range;

            minPrice = Math.max(0, Math.round(minPrice * priceScaleFactor) / (float)priceScaleFactor);
            maxPrice = Math.round(maxPrice * priceScaleFactor) / (float)priceScaleFactor;

            int minRawPrice = mapToRawPrice(minPrice);
            int maxRawPrice = mapToRawPrice(maxPrice);
            if(tileCount > maxRawPrice- minRawPrice)
            {
                tileCount = maxRawPrice - minRawPrice;
            }
        }
        return orderBook.getOrderBookVolumeData(minPrice, maxPrice, tileCount);
    }

    @Override
    public OrderBookVolumeData getOrderBookVolumeData()
    {
        return getOrderBookVolumeData(-1,0, 0, 0);
    }

    @Override
    public OrderReadData getOrderReadData(long orderID)
    {
        LimitOrder order = orderBook.getOrder(orderID);
        if(order == null)
            return null;
        return new OrderReadData(order, priceScaleFactor);
    }
    @Override
    public OrderReadListData getOrderReadListData(List<Long> orderIDs)
    {
        List<LimitOrder> orders = orderBook.getOrders(orderIDs);
        return new OrderReadListData(new ArrayList<>(orders), priceScaleFactor);
    }
    @Override
    public OrderReadListData getOrderReadListData(UUID playerUUID)
    {
        List<LimitOrder> orders = orderBook.getOrders(playerUUID);
        return new OrderReadListData(new ArrayList<>(orders), priceScaleFactor);
    }
    @Override
    public PriceHistoryData getPriceHistoryData(int maxHistoryPointCount)
    {
        return new PriceHistoryData(historicalMarketData.getHistory(), maxHistoryPointCount);
    }
    @Override
    public TradingViewData getTradingViewData(int bankAccountNumber, int maxHistoryPointCount, float minVisiblePrice, float maxVisiblePrice, int orderBookTileCount, boolean requestBotTargetPrice)
    {
        IBankAccount bankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(bankAccountNumber);
        //if(bankAccount == null)
        //return null;

        //IBank itemBank = bankUser.getBank(tradingPair.getItem());
        //IBank moneyBank = bankUser.getBank(tradingPair.getCurrency());

        //if(itemBank == null)
        //    itemBank = bankUser.createItemBank(tradingPair.getItem(), 0, true);
//
        //if(moneyBank == null)
        //    moneyBank = bankUser.createItemBank(tradingPair.getCurrency(), 0, true);
//
        //if(itemBank == null || moneyBank == null)
        //    return null;

        List<Order> orders = new ArrayList<>(orderBook.getOrders(bankAccountNumber));
        return new TradingViewData(new TradingPairData(tradingPair), new PriceHistoryData(historicalMarketData.getHistory(), maxHistoryPointCount),
                bankAccount, getOrderBookVolumeData(maxHistoryPointCount, minVisiblePrice, maxVisiblePrice, orderBookTileCount),
                new OrderReadListData(orders, priceScaleFactor), marketOpen, (float)smallesTradableVolume/(float)itemFractionScaleFactor);
    }
    @Override
    public TradingViewData getTradingViewData(int bankAccountNumber)
    {
        return getTradingViewData(bankAccountNumber, -1,0, 0, 0, false);
    }
    @Override
    public ServerMarketSettingsData getMarketSettingsData()
    {
        ServerVolatilityBot.Settings botSettings = null;
        VirtualOrderBook.Settings virtualOrderBookSettings = null;
        if(volatilityBot != null)
        {
            botSettings = (ServerVolatilityBot.Settings)volatilityBot.getSettings();
        }
        if(orderBook.getVirtualOrderBook() != null)
        {
            virtualOrderBookSettings = orderBook.getVirtualOrderBook().getSettings();
        }
        return new ServerMarketSettingsData(tradingPair, botSettings, virtualOrderBookSettings,
                                            marketOpen, itemImbalance,
                                            shiftPriceCandleIntervalMS, priceScaleFactor);
    }
    @Override
    public boolean setMarketSettingsData(@Nullable ServerMarketSettingsData settingsData)
    {
        if(settingsData == null)
            return false;

        boolean success = true;

        if(settingsData.botSettingsData != null)
        {
            if(volatilityBot != null)
            {
                volatilityBot.setSettings(settingsData.botSettingsData.settings);
            }
            else if(settingsData.doCreateBotIfNotExists)
                createVolatilityBot(settingsData.botSettingsData.settings);

        }
        else if(volatilityBot != null && settingsData.doDestroyBotIfExists)
        {
            success = destroyVolatilityBot();
        }

        /*if(settingsData.virtualOrderBookSettingsData != null) {
            if (orderBook.getVirtualOrderBook() != null) {
                orderBook.getVirtualOrderBook().setSettings(settingsData.virtualOrderBookSettingsData.settings);
            }else if(settingsData.doCreateVirtualOrderBookIfNotExists) {
                createVirtualOrderBook(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get(),
                        settingsData.virtualOrderBookSettingsData.settings);
            }
    }*/
        //else if(orderBook.getVirtualOrderBook() != null && settingsData.doDestroyVirtualOrderBookIfExists)
        //{
        //    success = destroyVirtualOrderBook();
        //}

        marketOpen = settingsData.marketOpen;
        if(settingsData.overwriteItemImbalance)
        {
            setItemImbalance(settingsData.itemImbalance);
        }
        //itemImbalance = settingsData.itemImbalance;
        setShiftPriceCandleIntervalMS(settingsData.shiftPriceCandleIntervalMS);
        //setNotifySubscriberIntervalMS(settingsData.notifySubscriberIntervalMS);


        return success;
    }
    @Override
    public boolean setBotSettingsData(@Nullable BotSettingsData botSettingsData)
    {
        return setBotSettings(botSettingsData != null ? botSettingsData.settings : null);
    }
    @Override
    public boolean setBotSettings(ServerVolatilityBot.Settings settings)
    {
        if(volatilityBot == null)
            return false;

        volatilityBot.setSettings(settings);
        return true;
    }

    @Override
    public boolean setVirtualOrderBookSettingsData(@Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData)
    {
        if(virtualOrderBookSettingsData == null || orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.getVirtualOrderBook().setSettings(virtualOrderBookSettingsData.settings);
        return true;
    }
    @Override
    public boolean setVirtualOrderBookSettings(VirtualOrderBook.Settings settings)
    {
        if(orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.getVirtualOrderBook().setSettings(settings);
        return true;
    }

    @Override
    public void resetVirtualOrderBookVolumeDistribution()
    {
        if(orderBook.getVirtualOrderBook() != null)
        {
            orderBook.getVirtualOrderBook().resetVolumeDistribution();
        }
    }

    @Override
    public void resetHistoricalMarketData()
    {
        historicalMarketData.clear(getCurrentRawPrice());
    }


    @Override
    public int getPriceScaleFactor() {
        return priceScaleFactor;
    }

    @Override
    public float getBotTargetPrice() {
        if(volatilityBot == null)
            return 0;
        return volatilityBot.getTargetPriceF();
    }


    @Override
    public void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS) {
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        shiftPriceTimer.start(shiftPriceCandleIntervalMS);
    }
    @Override
    public long getShiftPriceCandleIntervalMS() {
        return shiftPriceCandleIntervalMS;
    }




    @Override
    public void createVolatilityBot(ServerVolatilityBot.Settings settings)
    {
        if(volatilityBot == null)
            volatilityBot = new ServerVolatilityBot(this);
        volatilityBot.setSettings(settings);
    }

    @Override
    public boolean destroyVolatilityBot()
    {
        if(volatilityBot == null)
            return false;

        volatilityBot.setEnabled(false);
        volatilityBot.clearOrders();
        volatilityBot = null;
        return true;
    }

    @Override
    public boolean hasVolatilityBot()
    {
        return volatilityBot != null;
    }

    /*@Override
    public void createVirtualOrderBook(int realVolumeBookSize, VirtualOrderBook.Settings settings)
    {
        if(orderBook.getVirtualOrderBook() == null)
        {
            orderBook.createVirtualOrderBook(realVolumeBookSize, getCurrentRawPrice() ,settings);
        }else {
            orderBook.getVirtualOrderBook().setSettings(settings);
        }
    }*/

    /*@Override
    public boolean destroyVirtualOrderBook()
    {
        if(orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.destroyVirtualOrderBook();
        return true;
    }*/
    @Override
    public boolean hasVirtualOrderBook()
    {
        return orderBook.getVirtualOrderBook() != null;
    }

    @Override
    public TradingPair getTradingPair() {
        return tradingPair;
    }
    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }
    /*public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }*/
    //@Override
    //public HistoricalMarketData getHistoricalMarketData() {
    //    return historicalMarketData;
    //}

    @Override
    public float getDefaultRealPrice()
    {
        return defaultRealPrice;
    }
    @Override
    public int getCurrentRawPrice() {
        return historicalMarketData.getCurrentPrice();
    }

    @Override
    public float getCurrentRealPrice()
    {
        return mapToRealPrice(getCurrentRawPrice());
    }

    @Override
    public int mapToRawPrice(float realPrice)
    {
        return (int)ServerMarketManager.realToRawPrice(realPrice, priceScaleFactor);
    }

    @Override
    public float mapToRealPrice(int rawPrice)
    {
        return ServerMarketManager.rawToRealPrice(rawPrice, priceScaleFactor);
    }
    @Override
    public boolean isMarketOpen() {
        return marketOpen;
    }
    @Override
    public void openMarket()
    {
        marketOpen = true;
    }
    @Override
    public void closeMarket()
    {
        marketOpen = false;
    }
    @Override
    public void setMarketOpen(boolean marketOpen) {
        this.marketOpen = marketOpen;
    }



    private LimitOrder createLimitOrder(UUID playerUUID, int bankAccountNumber, float amount, float price)
    {
        int rawPrice = mapToRawPrice(price);
        return OrderFactory.createLimitOrder(playerUUID, bankAccountNumber, tradingPair, amount, rawPrice, priceScaleFactor, currencyItemFractionScaleFactor, itemFractionScaleFactor);
    }
    private MarketOrder createMarketOrder(UUID playerUUID, int bankAccountNumber, float amount)
    {
        int currentPrice = getCurrentRawPrice();
        return OrderFactory.createMarketOrder(playerUUID, bankAccountNumber, tradingPair, amount, currentPrice, priceScaleFactor, currencyItemFractionScaleFactor, itemFractionScaleFactor);
    }

    @Override
    public LimitOrder createBotLimitOrder(float amount, float price)
    {
        int rawPrice = mapToRawPrice(price);
        long rawAmount = (long)(amount*itemFractionScaleFactor);
        return OrderFactory.createBotLimitOrder(rawAmount, rawPrice);
    }
    @Override
    public MarketOrder createBotMarketOrder(float amount)
    {
        long rawAmount = (long)(amount*itemFractionScaleFactor);
        return OrderFactory.createBotMarketOrder(rawAmount);
    }
    @Override
    public boolean placeOrder(Order order)
    {
        if(order == null)
            return false;
        addOrder(order);
        return true;
    }
    @Override
    public boolean createAndPlaceLimitOrder(UUID playerUUID, int bankAccountNumber, float amount, float price)
    {
        if(!marketOpen)
            return false;
        LimitOrder order = createLimitOrder(playerUUID, bankAccountNumber, amount, price);
        return placeOrder(order);
    }

    @Override
    public boolean createAndPlaceMarketOrder(UUID playerUUID, int bankAccountNumber, float amount)
    {
        if(!marketOpen)
            return false;

        MarketOrder order = createMarketOrder(playerUUID, bankAccountNumber, amount);
        return placeOrder(order);
    }

    @Override
    public boolean createAndPlaceBotLimitOrder(float amount, float price)
    {
        int rawPrice = mapToRawPrice(price);
        long rawAmount = (long)(amount*itemFractionScaleFactor);
        LimitOrder order = OrderFactory.createBotLimitOrder(rawAmount, rawPrice);
        return placeOrder(order);
    }
    @Override
    public boolean createAndPlaceBotMarketOrder(float amount)
    {
        long rawAmount = (long)(amount*itemFractionScaleFactor);
        MarketOrder order = OrderFactory.createBotMarketOrder(rawAmount);
        return placeOrder(order);
    }

    @Override
    public boolean cancelOrder(long orderID)
    {
        Order order = orderBook.getOrder(orderID);
        if(order == null)
            return false;
        return cancelOrder(order);
    }
    @Override
    public boolean cancelOrder(Order order)
    {
        if(order == null)
            return false;
        order.markAsCancelled();
        unlockLockedAmount(order);
        if(order.getFilledAmount() != 0)
        {

        }
        if(order instanceof LimitOrder limitOrder)
        {
            orderBook.removeOrder(limitOrder);
        }else if(order instanceof MarketOrder marketOrder)
        {

        }
        onOrderFinished(order);
        return true;
    }

    public void onOrderPlaced(Order order)
    {
        BACKEND_INSTANCES.SERVER_EVENTS.ORDER_PLACED.notifyListeners(new Tuple<>(tradingPair, order));
    }
    public void onOrderFinished(Order order)
    {
        BACKEND_INSTANCES.SERVER_MARKET_MANAGER.logNewOrderToHistory(this.tradingPair, order);
        BACKEND_INSTANCES.SERVER_PLAYER_MANAGER.logNewOrderToHistory(this.tradingPair, order);
        BACKEND_INSTANCES.SERVER_EVENTS.ORDER_FINISHED.notifyListeners(new Tuple<>(tradingPair, order));
    }


    @Override
    public void cancelAllBotOrders()
    {
        var orders = orderBook.removeAllBotOrders();
        for(Order order : orders)
        {
            order.markAsCancelled();
        }
    }
    @Override
    public long getItemImbalance() {
        return itemImbalance;
    }
    @Override
    public void addItemImbalance(long amount) {
        itemImbalance += amount;
    }
    @Override
    public void setItemImbalance(long itemImbalance) {
        this.itemImbalance = itemImbalance;
    }

    protected void addOrder(@NotNull Order order)
    {
        orderBook.addIncommingOrder(order);
        onOrderPlaced(order);
    }

    protected boolean changeOrderPrice(long orderID, float newPrice)
    {
        int newRawPrice = mapToRawPrice(newPrice);
        if(newRawPrice < 0)
            newRawPrice = 0;
        Order order = orderBook.getOrder(orderID);
        if(order == null)
            return false;
        if(order instanceof LimitOrder limitOrder) {
            long toFillAmount = limitOrder.getPendingAmount();

           // ServerPlayer player = PlayerUtilities.getOnlinePlayer(limitOrder.getPlayerUUID());
            boolean canBeMoved = false;
            if (limitOrder.isBuy()) {
                long toFreeAmount = ServerMarketManager.scaleToBankSystemMoneyAmount(toFillAmount * limitOrder.getPrice(),
                        priceScaleFactor, currencyItemFractionScaleFactor) / itemFractionScaleFactor;
                long toLockAmount = ServerMarketManager.scaleToBankSystemMoneyAmount(toFillAmount * newRawPrice, priceScaleFactor, currencyItemFractionScaleFactor) / itemFractionScaleFactor;
                IBankAccount account = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(limitOrder.getBankAccountNumber());
                if(account == null)
                {
                    cancelOrder(limitOrder);
                    return false;
                }
                IBank moneyBank = account.getBank(tradingPair.getCurrency());
                if (moneyBank == null)
                    return false;

                canBeMoved = moneyBank.getTotalBalance() - toFreeAmount >= 0 && moneyBank.getBalance() >= toLockAmount;
                if(canBeMoved)
                {
                    if(moneyBank.unlockAmount(Math.min(toFreeAmount, moneyBank.getLockedBalance())) != IBank.Status.SUCCESS)
                    {
                        // Failed to unlock the amount, cannot change the order price.
                        return false;
                    }
                    if(moneyBank.lockAmount(toLockAmount) != IBank.Status.SUCCESS)
                    {
                        // Failed to lock the amount, cannot change the order price.
                        return false;
                    }
                    if(orderBook.removeOrder(limitOrder)) {
                        limitOrder.setPrice(newRawPrice);
                        limitOrder.setLockedMoney(toLockAmount);
                        orderBook.addIncommingOrder(limitOrder);
                        return true;
                    }
                }
            }
            else {
                if(orderBook.removeOrder(limitOrder)) {
                    limitOrder.setPrice(newRawPrice);
                    orderBook.addIncommingOrder(limitOrder);
                    return true;
                }
            }
        }
        return false;
    }
    protected void shiftPriceHistory()
    {
        historicalMarketData.createNewCandle();
    }


    protected boolean unlockLockedAmount(Order order) {
        if(order.isBot())
            return false;

        IBankAccount account = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(order.getBankAccountNumber());
        if(account == null)
            return false;
        if(order.isBuy()) {
            long toUnlockMoney = order.getLockedMoney() + order.getTransferedMoney();
            if(toUnlockMoney > 0) {
                IBank moneyBank = account.getBank(tradingPair.getCurrency());
                if(moneyBank == null)
                    return false;
                moneyBank.unlockAmount(toUnlockMoney);
                return true;
            }
        }else {
            long toUnlockItem = -(order.getAmount() - order.getFilledAmount());
            if(toUnlockItem > 0) {
                IBank itemBank = account.getBank(tradingPair.getItem());
                if(itemBank == null)
                    return false;
                itemBank.unlockAmount(toUnlockItem);
                return true;
            }
        }
        return false;
    }




    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag tradingPairTag = new CompoundTag();
        CompoundTag orderBookTag = new CompoundTag();
        CompoundTag matchingEngineTag = new CompoundTag();
        CompoundTag historicalMarketDataTag = new CompoundTag();
        CompoundTag volatilityBotTag = new CompoundTag();

        tradingPair.save(tradingPairTag);
        orderBook.save(orderBookTag);
        matchingEngine.save(matchingEngineTag);
        historicalMarketData.save(historicalMarketDataTag);
        if(volatilityBot != null) {
            volatilityBot.save(volatilityBotTag);
        }
        tag.put("tradingPair", tradingPairTag);
        tag.put("orderBookInterface", orderBookTag);
        tag.put("matchingEngine", matchingEngineTag);
        tag.put("historicalMarketData", historicalMarketDataTag);
        if(volatilityBot != null) {
            tag.put("volatilityBot", volatilityBotTag);
        }


        tag.putBoolean("marketOpen", marketOpen);
        tag.putLong("itemImbalance", itemImbalance);
        tag.putFloat("defaultRealPrice", defaultRealPrice);
        tag.putLong("shiftPriceCandleIntervalMS", shiftPriceCandleIntervalMS);
        tag.putInt("priceScaleFactor", priceScaleFactor);
        tag.putInt("currencyItemFractionScaleFactor", currencyItemFractionScaleFactor);
        tag.putInt("itemFractionScaleFactor", itemFractionScaleFactor);
        //tag.putLong("notifySubscriberIntervalMS", notifySubscriberIntervalMS);

        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;

        boolean success = true;
        if(tag.contains("tradingPair")) {
            success &= tradingPair.load(tag.getCompound("tradingPair"));
            success &= tradingPair.isValid();
        }
        if(tag.contains("orderBookInterface")) {
            success &= orderBook.load(tag.getCompound("orderBookInterface"));
        }
        if(tag.contains("matchingEngine")) {
            success &= matchingEngine.load(tag.getCompound("matchingEngine"));
        }
        if(tag.contains("historicalMarketData")) {
            success &= historicalMarketData.load(tag.getCompound("historicalMarketData"));
        }
        if(tag.contains("volatilityBot")) {
            if(volatilityBot == null)
                volatilityBot = new ServerVolatilityBot(this);
            success &= volatilityBot.load(tag.getCompound("volatilityBot"));
        }

        if(tag.contains("marketOpen"))
            marketOpen = tag.getBoolean("marketOpen");
        if(tag.contains("itemImbalance"))
            itemImbalance = tag.getLong("itemImbalance");
        if(tag.contains("defaultRealPrice"))
            defaultRealPrice = tag.getFloat("defaultRealPrice");
        if(tag.contains("shiftPriceCandleIntervalMS"))
            shiftPriceCandleIntervalMS = tag.getLong("shiftPriceCandleIntervalMS");
        //if(tag.contains("notifySubscriberIntervalMS"))
        //    notifySubscriberIntervalMS = tag.getLong("notifySubscriberIntervalMS");

        if(shiftPriceCandleIntervalMS < 0)
            shiftPriceCandleIntervalMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get();
        //if(notifySubscriberIntervalMS < 0)
        //    notifySubscriberIntervalMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.NOTIFY_SUBSCRIBER_INTERVAL_MS.get();

        setShiftPriceCandleIntervalMS(shiftPriceCandleIntervalMS);
        //setNotifySubscriberIntervalMS(notifySubscriberIntervalMS);

        var virtual = orderBook.getVirtualOrderBook();
        if(virtual != null)
            virtual.setCurrentPrice(getCurrentRawPrice());


        if(tag.contains("priceScaleFactor"))
            priceScaleFactor = tag.getInt("priceScaleFactor");
        else
            priceScaleFactor = 1;
        if(tag.contains("currencyItemFractionScaleFactor"))
            currencyItemFractionScaleFactor = tag.getInt("currencyItemFractionScaleFactor");
        else
            currencyItemFractionScaleFactor = 1;

        if(tag.contains("itemFractionScaleFactor"))
            itemFractionScaleFactor = tag.getInt("itemFractionScaleFactor");
        else
            itemFractionScaleFactor = 1;


        this.historicalMarketData.getHistory().setScaleFactors(priceScaleFactor, currencyItemFractionScaleFactor);
        this.orderBook.setScaleFactors(priceScaleFactor, itemFractionScaleFactor);
        this.matchingEngine.setScaleFactors(priceScaleFactor, currencyItemFractionScaleFactor, itemFractionScaleFactor);
        this.smallesTradableVolume = Math.min(currencyItemFractionScaleFactor / this.priceScaleFactor, itemFractionScaleFactor);
        return success;
    }

    public static int findNearestDivisor(int input, int divisorInput) {
        if (divisorInput == 0) {
            throw new IllegalArgumentException("divisorInput must not be zero.");
        }

        // If divisorInput already divides input, return it
        if (input % divisorInput == 0) {
            return divisorInput;
        }

        int down = divisorInput;
        int up = divisorInput;

        // Search downward
        while (down > 1 && input % down != 0) {
            down--;
        }

        // Search upward
        while (up <= input && input % up != 0) {
            up++;
        }

        // Decide which is closer
        if (down <= 1) {
            return up; // Only upward match found
        }
        if (up > input) {
            return down; // Only downward match found
        }

        if (Math.abs(divisorInput - down) <= Math.abs(up - divisorInput)) {
            return down;
        } else {
            return up;
        }
    }

    public static int ggt(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);

        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }

        return a;
    }

    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[ServerMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[ServerMarket: "+ tradingPair.getShortDescription() + "] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[ServerMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[ServerMarket: "+ tradingPair.getShortDescription() + "] " + msg);
    }
}
