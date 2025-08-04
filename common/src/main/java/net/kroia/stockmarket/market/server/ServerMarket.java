package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankUser;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderFactory;
import net.kroia.stockmarket.networking.packet.request.VirtualOrderBookSettingsData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerMarket implements ServerSaveable {

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
    private long shiftPriceCandleIntervalMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get();
    //private long notifySubscriberIntervalMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.NOTIFY_SUBSCRIBER_INTERVAL_MS.get();


    //private final ArrayList<ServerPlayer> subscribers = new ArrayList<>();
    protected TimerMillis shiftPriceTimer = new TimerMillis(true);
    //protected TimerMillis notifySubscriberTimer = new TimerMillis(true);

    public ServerMarket(TradingPair pair)
    {
        this(pair, 0);
    }
    public ServerMarket(TradingPair pair, int initialPrice)
    {
        this.tradingPair = pair;
        this.orderBook = new OrderBook(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get(), initialPrice);
        this.matchingEngine = new MatchingEngine(this);
        this.historicalMarketData = new HistoricalMarketData(initialPrice, BACKEND_INSTANCES.SERVER_SETTINGS.UI.PRICE_HISTORY_SIZE.get());
        this.volatilityBot = null;

        shiftPriceTimer.start(shiftPriceCandleIntervalMS);
        //notifySubscriberTimer.start(notifySubscriberIntervalMS);
    }
    public ServerMarket()
    {
        this.tradingPair = new TradingPair();
        this.orderBook = new OrderBook(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get());
        this.matchingEngine = new MatchingEngine(this);
        this.historicalMarketData = new HistoricalMarketData(BACKEND_INSTANCES.SERVER_SETTINGS.UI.PRICE_HISTORY_SIZE.get());
        this.volatilityBot = null;

    }


    public @Nullable BotSettingsData getBotSettingsData()
    {
        if(volatilityBot == null)
            return null;
        return new BotSettingsData(tradingPair, (ServerVolatilityBot.Settings)volatilityBot.getSettings());
    }
    public @Nullable VirtualOrderBookSettingsData getVirtualOrderBookSettingsData()
    {
        if(orderBook.getVirtualOrderBook() == null)
            return null;
        return new VirtualOrderBookSettingsData(tradingPair, orderBook.getVirtualOrderBook().getSettings());
    }
    public TradingPairData getTradingPairData()
    {
        return new TradingPairData(tradingPair);
    }
    public OrderBookVolumeData getOrderBookVolumeData(int historyViewCount, int minPrice, int maxPrice, int tileCount)
    {
        if(minPrice == 0 && maxPrice == 0)
        {
            minPrice = historicalMarketData.getHistory().getLowestPrice(historyViewCount);
            maxPrice = historicalMarketData.getHistory().getHighestPrice(historyViewCount);
            int range = (maxPrice - minPrice)/2;
            if(range < 10)
            {
                range = 10;
            }
            minPrice -= range;
            maxPrice += range;

            // Fllor to next 5
            minPrice = (minPrice / 5) * 5;
            maxPrice = (maxPrice / 5) * 5;

            minPrice = Math.max(0, minPrice);
        }
        if(tileCount == 0)
        {
            tileCount = BACKEND_INSTANCES.SERVER_SETTINGS.UI.MAX_ORDERBOOK_TILES.get();
            if(maxPrice-minPrice < tileCount)
            {
                tileCount = maxPrice-minPrice;
            }
        }
        return new OrderBookVolumeData(minPrice, maxPrice, tileCount, orderBook);
    }
    public OrderBookVolumeData getOrderBookVolumeData()
    {
        return getOrderBookVolumeData(-1,0, 0, 0);
    }
    public OrderReadData getOrderReadData(long orderID)
    {
        LimitOrder order = orderBook.getOrder(orderID);
        if(order == null)
            return null;
        return new OrderReadData(order);
    }
    public OrderReadListData getOrderReadListData(List<Long> orderIDs)
    {
        List<LimitOrder> orders = orderBook.getOrders(orderIDs);
        return new OrderReadListData(new ArrayList<>(orders));
    }
    public OrderReadListData getOrderReadListData(UUID playerUUID)
    {
        List<LimitOrder> orders = orderBook.getOrders(playerUUID);
        return new OrderReadListData(new ArrayList<>(orders));
    }
    public PriceHistoryData getPriceHistoryData(int maxHistoryPointCount)
    {
        return new PriceHistoryData(historicalMarketData.getHistory(), maxHistoryPointCount);
    }
    public TradingViewData getTradingViewData(UUID player,int maxHistoryPointCount, int minVisiblePrice, int maxVisiblePrice, int orderBookTileCount, boolean requestBotTargetPrice)
    {
        IBankUser bankUser = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(player);
        if(bankUser == null)
            return null;

        IBank itemBank = bankUser.getBank(tradingPair.getItem());
        IBank moneyBank = bankUser.getBank(tradingPair.getCurrency());

        if(itemBank == null)
            itemBank = bankUser.createItemBank(tradingPair.getItem(), 0, true);

        if(moneyBank == null)
            moneyBank = bankUser.createItemBank(tradingPair.getCurrency(), 0, true);

        if(itemBank == null || moneyBank == null)
            return null;

        List<Order> orders = new ArrayList<>(orderBook.getOrders(player));
        int botTargetPrice = -1;
        if(requestBotTargetPrice && volatilityBot != null)
        {
            botTargetPrice = volatilityBot.getTargetPrice();
        }

        return new TradingViewData(new TradingPairData(tradingPair), new PriceHistoryData(historicalMarketData.getHistory(), maxHistoryPointCount),
                itemBank, moneyBank, getOrderBookVolumeData(maxHistoryPointCount, minVisiblePrice, maxVisiblePrice, orderBookTileCount),
                new OrderReadListData(orders), marketOpen, botTargetPrice);
    }
    public TradingViewData getTradingViewData(UUID player)
    {
        return getTradingViewData(player, -1,0, 0, 0, false);
    }
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
                                            shiftPriceCandleIntervalMS/*, notifySubscriberIntervalMS*/);
    }
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

        if(settingsData.virtualOrderBookSettingsData != null) {
            if (orderBook.getVirtualOrderBook() != null) {
                orderBook.getVirtualOrderBook().setSettings(settingsData.virtualOrderBookSettingsData.settings);
            }else if(settingsData.doCreateVirtualOrderBookIfNotExists) {
                createVirtualOrderBook(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get(),
                        settingsData.virtualOrderBookSettingsData.settings);
            }
        }
        else if(orderBook.getVirtualOrderBook() != null && settingsData.doDestroyVirtualOrderBookIfExists)
        {
            success = destroyVirtualOrderBook();
        }

        marketOpen = settingsData.marketOpen;
        //itemImbalance = settingsData.itemImbalance;
        setShiftPriceCandleIntervalMS(settingsData.shiftPriceCandleIntervalMS);
        //setNotifySubscriberIntervalMS(settingsData.notifySubscriberIntervalMS);


        return success;
    }
    public boolean setBotSettingsData(@Nullable BotSettingsData botSettingsData)
    {
        return setBotSettings(botSettingsData != null ? botSettingsData.settings : null);
    }
    public boolean setBotSettings(ServerVolatilityBot.Settings settings)
    {
        if(volatilityBot == null)
            return false;

        volatilityBot.setSettings(settings);
        return true;
    }

    public boolean setVirtualOrderBookSettingsData(@Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData)
    {
        if(virtualOrderBookSettingsData == null || orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.getVirtualOrderBook().setSettings(virtualOrderBookSettingsData.settings);
        return true;
    }
    public boolean setVirtualOrderBookSettings(VirtualOrderBook.Settings settings)
    {
        if(orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.getVirtualOrderBook().setSettings(settings);
        return true;
    }


    public int getBotTargetPrice() {
        if(volatilityBot == null)
            return 0;
        return volatilityBot.getTargetPrice();
    }
    public void resetHistoricalMarketData()
    {
        historicalMarketData.clear(historicalMarketData.getCurrentPrice());
    }



    public void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS) {
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        shiftPriceTimer.start(shiftPriceCandleIntervalMS);
    }
    public long getShiftPriceCandleIntervalMS() {
        return shiftPriceCandleIntervalMS;
    }
    /*public void setNotifySubscriberIntervalMS(long notifySubscriberIntervalMS) {
        this.notifySubscriberIntervalMS = notifySubscriberIntervalMS;
        notifySubscriberTimer.start(notifySubscriberIntervalMS);
    }
    public long getNotifySubscriberIntervalMS() {
        return notifySubscriberIntervalMS;
    }*/





    public void createVolatilityBot(ServerVolatilityBot.Settings settings)
    {
        if(volatilityBot == null)
            volatilityBot = new ServerVolatilityBot(this);
        volatilityBot.setSettings(settings);
    }
    public boolean destroyVolatilityBot()
    {
        if(volatilityBot == null)
            return false;

        volatilityBot.setEnabled(false);
        volatilityBot.clearOrders();
        volatilityBot = null;
        return true;
    }
    public boolean hasVolatilityBot()
    {
        return volatilityBot != null;
    }
    public void createVirtualOrderBook(int realVolumeBookSize, VirtualOrderBook.Settings settings)
    {
        if(orderBook.getVirtualOrderBook() == null)
        {
            orderBook.createVirtualOrderBook(realVolumeBookSize, historicalMarketData.getCurrentPrice() ,settings);
            //orderBook.getVirtualOrderBook().setCurrentPrice(historicalMarketData.getCurrentPrice());
        }else {
            orderBook.getVirtualOrderBook().setSettings(settings);
        }
    }
    public boolean destroyVirtualOrderBook()
    {
        if(orderBook.getVirtualOrderBook() == null)
            return false;

        orderBook.destroyVirtualOrderBook();
        return true;
    }
    public boolean hasVirtualOrderBook()
    {
        return orderBook.getVirtualOrderBook() != null;
    }

    public TradingPair getTradingPair() {
        return tradingPair;
    }
    public OrderBook getOrderBook() {
        return orderBook;
    }
    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }
    public HistoricalMarketData getHistoricalMarketData() {
        return historicalMarketData;
    }
    public int getCurrentPrice() {
        return historicalMarketData.getCurrentPrice();
    }
    public boolean isMarketOpen() {
        return marketOpen;
    }
    public void openMarket()
    {
        marketOpen = true;
    }
    public void closeMarket()
    {
        marketOpen = false;
    }
    public void setMarketOpen(boolean marketOpen) {
        this.marketOpen = marketOpen;
    }

    public void update(MinecraftServer server)
    {
        if(volatilityBot != null)
        {
            volatilityBot.update(server);
        }
        matchingEngine.processIncommingOrders(orderBook.getAndClearIncommingOrders());
        orderBook.updateVirtualOrderBookVolume(historicalMarketData.getCurrentPrice());


        //if(notifySubscriberTimer.check()) {
        //    if(!subscribers.isEmpty())
        //        notifySubscribers();
        //}

        if(shiftPriceTimer.check()) {
            shiftPriceHistory();
        }
    }


    public boolean createLimitOrder(UUID playerUUID, long amount, int price)
    {
        if(!marketOpen)
            return false;

        LimitOrder order = OrderFactory.createLimitOrder(playerUUID, tradingPair, amount, price);
        if(order == null)
            return false;

        addOrder(order);
        return true;
    }
    public boolean createMarketOrder(UUID playerUUID, long amount)
    {
        if(!marketOpen)
            return false;

        int currentPrice = historicalMarketData.getCurrentPrice();
        MarketOrder order = OrderFactory.createMarketOrder(playerUUID, tradingPair, amount, currentPrice);
        if(order == null)
            return false;

        addOrder(order);
        return true;
    }
    public boolean createBotLimitOrder(long amount, int price)
    {
        //if(!marketOpen)
        //    return false;

        LimitOrder order = OrderFactory.createBotLimitOrder(amount, price);
        addOrder(order);
        return true;
    }
    public boolean createBotMarketOrder(long amount)
    {
        //if(!marketOpen)
        //    return false;

        MarketOrder order = OrderFactory.createBotMarketOrder(amount);
        addOrder(order);
        return true;
    }

    public boolean cancelOrder(long orderID)
    {
        Order order = orderBook.getOrder(orderID);
        if(order == null)
            return false;
        return cancelOrder(order);
    }
    public boolean cancelOrder(Order order)
    {
        if(order == null)
            return false;
        order.markAsCancelled();
        unlockLockedAmount(order);
        if(order instanceof LimitOrder limitOrder)
        {
            orderBook.removeOrder(limitOrder);
        }else if(order instanceof MarketOrder marketOrder)
        {

        }
        return true;
    }

    public void cancelAllBotOrders()
    {
        var orders = orderBook.removeAllBotOrders();
        for(Order order : orders)
        {
            order.markAsCancelled();
        }
    }
    public long getItemImbalance() {
        return itemImbalance;
    }
    public void addItemImbalance(long amount) {
        itemImbalance += amount;
    }
    public void setItemImbalance(long itemImbalance) {
        this.itemImbalance = itemImbalance;
    }

    protected void addOrder(@NotNull Order order)
    {
        orderBook.addIncommingOrder(order);
    }

    protected boolean changeOrderPrice(long orderID, int newPrice)
    {
        if(newPrice < 0)
            newPrice = 0;
        Order order = orderBook.getOrder(orderID);
        if(order == null)
            return false;
        if(order instanceof LimitOrder limitOrder) {
            long toFillAmount = limitOrder.getPendingAmount();

           // ServerPlayer player = PlayerUtilities.getOnlinePlayer(limitOrder.getPlayerUUID());
            boolean canBeMoved = false;
            if (limitOrder.isBuy()) {
                long toFreeAmount = toFillAmount * limitOrder.getPrice();
                long toLockAmount = toFillAmount * newPrice;
                IBank moneyBank = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(limitOrder.getPlayerUUID()).getBank(tradingPair.getCurrency());
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
                        limitOrder.setPrice(newPrice);
                        orderBook.addIncommingOrder(limitOrder);
                        return true;
                    }
                }
            }
            else {
                if(orderBook.removeOrder(limitOrder)) {
                    limitOrder.setPrice(newPrice);
                    orderBook.addIncommingOrder(limitOrder);
                    return true;
                }
            }
        }
        return false;
    }
    protected void orderInvalid(Order order, String reason)
    {
        cancelOrder(order.getOrderID());
        order.markAsInvalid(reason);
    }
    protected void orderFilled(Order order)
    {
        order.markAsProcessed();
        unlockLockedAmount(order);
        if(order instanceof LimitOrder limitOrder)
        {
            orderBook.removeOrder(limitOrder);
        }else if(order instanceof MarketOrder marketOrder)
        {
            
        }
    }

    protected void shiftPriceHistory()
    {
        historicalMarketData.createNewCandle();
    }


    protected boolean unlockLockedAmount(Order order) {
        if(order.isBot())
            return false;

        IBankUser user = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(order.getPlayerUUID());
        if(user == null)
            return false;
        if(order.isBuy()) {
            long toUnlockMoney = order.getLockedMoney() + order.getTransferedMoney();
            if(toUnlockMoney > 0) {
                IBank moneyBank = user.getBank(tradingPair.getCurrency());
                if(moneyBank == null)
                    return false;
                moneyBank.unlockAmount(toUnlockMoney);
                return true;
            }
        }else {
            long toUnlockItem = -(order.getAmount() - order.getFilledAmount());
            if(toUnlockItem > 0) {
                IBank itemBank = user.getBank(tradingPair.getItem());
                if(itemBank == null)
                    return false;
                itemBank.unlockAmount(toUnlockItem);
                return true;
            }
        }
        return false;
    }


    protected void notifySubscribers()
    {
        /*for(ServerPlayer player : subscribers)
        {
            SyncPricePacket.sendPacket(itemID, player);
        }*/
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
        tag.put("orderBook", orderBookTag);
        tag.put("matchingEngine", matchingEngineTag);
        tag.put("historicalMarketData", historicalMarketDataTag);
        if(volatilityBot != null) {
            tag.put("volatilityBot", volatilityBotTag);
        }


        tag.putBoolean("marketOpen", marketOpen);
        tag.putLong("itemImbalance", itemImbalance);
        tag.putLong("shiftPriceCandleIntervalMS", shiftPriceCandleIntervalMS);
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
        if(tag.contains("orderBook")) {
            success &= orderBook.load(tag.getCompound("orderBook"));
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
            virtual.setCurrentPrice(historicalMarketData.getCurrentPrice());
        return success;
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
