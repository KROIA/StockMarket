package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/*
    * The MatchingEngine class is responsible for matching buy and sell orders.
 */
public class MatchingEngine implements ServerSaveable {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final ServerMarket serverMarket;

    private final HistoricalMarketData historicalMarketData;    // Owned by the ServerMarket
    private final OrderBook orderBook;  // Owned by the ServerMarket
    private final TradingPair tradingPair;  // Owned by the ServerMarket
    private int priceScaleFactor = 1;
    private int currencyItemFractionScaleFactor = 1;
    public MatchingEngine(ServerMarket market,
                          HistoricalMarketData historicalMarketData,
                          OrderBook orderBook,
                          TradingPair tradingPair)
    {
        this.serverMarket = market;
        this.historicalMarketData = historicalMarketData;
        this.orderBook = orderBook;
        this.tradingPair = tradingPair;
    }

    public void setScaleFactors(int priceScaleFactor, int currencyItemFractionScaleFactor) {
        this.priceScaleFactor = priceScaleFactor;
        this.currencyItemFractionScaleFactor = currencyItemFractionScaleFactor;
    }
    public void processIncommingOrders(List<Order> orders)
    {
        if(orders == null || orders.isEmpty())
            return;
        for(Order order : orders)
        {
            if(order == null)
                continue;
            addOrder(order);
        }
    }
    private void addOrder(Order order)
    {
        if(order == null)
            return;
        if(!serverMarket.isMarketOpen() && !order.isBot())
        {
            order.markAsInvalid(StockMarketTextMessages.getOrderInvalidReasonMarketClosedMessage());
            return;
        }
        if(order.getAmount() == 0)
        {
            order.markAsProcessed();
            return;
        }
        handleNewOrder(order);
    }

    private void handleNewOrder(Order order)
    {
        if (order instanceof LimitOrder limitOrder)
        {
            if(!processLimitOrder(limitOrder))
            {
                if(limitOrder.getStatus() == Order.Status.PENDING || limitOrder.getStatus() == Order.Status.PARTIAL)
                    orderBook.placeLimitOrder(limitOrder);
                else
                {
                    serverMarket.cancelOrder(limitOrder);
                }
            }
        } else if (order instanceof MarketOrder marketOrder) {
            processMarketOrder(marketOrder);
        } else {
            throw new IllegalArgumentException("Invalid order type");
        }
    }


    /**
     * Processes a market order by filling it with the best available limit orders.
     * @param marketOrder The market order to process.
     * @return true if the market order has been fully processed, false otherwise.
     */
    private boolean processMarketOrder(MarketOrder marketOrder)
    {
        VirtualOrderBook virtualOrderBook = orderBook.getVirtualOrderBook();
        PriorityQueue<LimitOrder> limitOrders = marketOrder.isBuy() ? orderBook.getSellOrders() : orderBook.getBuyOrders();
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        long fillVolume = Math.abs(marketOrder.getAmount());
        int newPrice = getPrice();
        int deltaPrice = marketOrder.isBuy() ? 1 : -1;
        for(LimitOrder limitOrder : limitOrders) {
            long filledVolume = 0;

            long loopTimeout = 10000;
            if(virtualOrderBook != null){
                while (limitOrder.getPrice() != newPrice) {
                    loopTimeout--;
                    if (loopTimeout <= 0) {
                        error("Market order processing loop timeout: " + marketOrder);
                        marketOrder.markAsInvalid("Market order processing loop timeout");
                        break;
                    }
                    long virtualVolume = virtualOrderBook.getAmount(newPrice);
                    long transferedVolume = TransactionEngine.virtualFill(tradingPair, marketOrder, virtualVolume, newPrice, priceScaleFactor, currencyItemFractionScaleFactor);
                    filledVolume += transferedVolume;
                    if (transferedVolume > 0) {
                        if (marketOrder.isBuy()) {
                            virtualOrderBook.removeAmount(newPrice, transferedVolume);
                            if (!marketOrder.isBot()) {
                                serverMarket.addItemImbalance(-transferedVolume);
                            }
                        } else if (marketOrder.isSell()) {
                            virtualOrderBook.removeAmount(newPrice, -transferedVolume);
                            if (!marketOrder.isBot()) {
                                serverMarket.addItemImbalance(transferedVolume);
                            }
                        }
                        setPrice(newPrice);
                        historicalMarketData.addVolume(Math.abs(transferedVolume));
                    }
                    if (marketOrder.isFilled()) {
                        limitOrders.removeAll(toRemove);
                        return true;
                    }
                    newPrice += deltaPrice;
                    if (newPrice < 0) {
                        newPrice = 0;
                        break;
                    }
                }
            }


            long transferedVolume = TransactionEngine.fill(tradingPair ,marketOrder, limitOrder, limitOrder.getPrice(), priceScaleFactor, currencyItemFractionScaleFactor);
            filledVolume += transferedVolume;

            if(limitOrder.isFilled() || limitOrder.getStatus() == Order.Status.CANCELLED || limitOrder.getStatus() == Order.Status.INVALID)
                toRemove.add(limitOrder);

            if(marketOrder.getStatus() == Order.Status.INVALID || marketOrder.getStatus() == Order.Status.CANCELLED)
                break;

            if(filledVolume != 0) {
                newPrice = limitOrder.getPrice();
                setPrice(newPrice);
                historicalMarketData.addVolume(Math.abs(transferedVolume));
                if(marketOrder.isBot() && !limitOrder.isBot())
                {
                    serverMarket.addItemImbalance(limitOrder.isBuy()?-transferedVolume:transferedVolume);
                }else if(!marketOrder.isBot() && limitOrder.isBot())
                {
                    serverMarket.addItemImbalance(marketOrder.isBuy()?-transferedVolume:transferedVolume);
                }
            }

            fillVolume -= filledVolume;

            if(fillVolume <= 0)
            {
                if(fillVolume<0)
                {
                    limitOrders.removeAll(toRemove);
                    error("Market order overfilled: "+marketOrder);
                }
                break;
            }
        }

        if(virtualOrderBook != null) {
            long loopTimeout = 10000;
            while (fillVolume > 0) {
                loopTimeout--;
                if (loopTimeout <= 0) {
                    error("Market order processing loop timeout: " + marketOrder);
                    serverMarket.cancelOrder(marketOrder);
                    marketOrder.markAsInvalid("Market order processing loop timeout");
                    break;
                }
                // Fill the remaining volume with virtual orders
                long virtualVolume = virtualOrderBook.getAmount(newPrice);
                long transferedVolume = TransactionEngine.virtualFill(tradingPair, marketOrder, virtualVolume, newPrice, priceScaleFactor, currencyItemFractionScaleFactor);
                fillVolume -= transferedVolume;

                if (transferedVolume > 0) {
                    if (marketOrder.isBuy()) {
                        virtualOrderBook.removeAmount(newPrice, transferedVolume);
                        if (!marketOrder.isBot()) {
                            serverMarket.addItemImbalance(-transferedVolume);
                        }
                    } else if (marketOrder.isSell()) {
                        virtualOrderBook.removeAmount(newPrice, -transferedVolume);
                        if (!marketOrder.isBot()) {
                            serverMarket.addItemImbalance(transferedVolume);
                        }
                    }
                    historicalMarketData.addVolume(Math.abs(transferedVolume));
                    setPrice(newPrice);
                }
                if (marketOrder.isFilled() || marketOrder.getStatus() == Order.Status.INVALID || marketOrder.getStatus() == Order.Status.CANCELLED)
                    break;
                newPrice += deltaPrice;
                if (newPrice < 0) {
                    newPrice = 0;
                    break;
                }
            }
        }

        limitOrders.removeAll(toRemove);

        if(fillVolume != 0)
        {
            serverMarket.cancelOrder(marketOrder);
            marketOrder.markAsInvalid(StockMarketTextMessages.getOrderInvalidReasonOrdersToFillTransactionMessage(marketOrder.isBuy()));
            return false;
        }
        return true;
    }


    private boolean processLimitOrder(LimitOrder limitOrder)
    {
        // Process the limit order
        long fillVolume = Math.abs(limitOrder.getAmount()-limitOrder.getFilledAmount());
        VirtualOrderBook virtualOrderBook = orderBook.getVirtualOrderBook();
        PriorityQueue<LimitOrder> limitOrders = limitOrder.isBuy() ? orderBook.getSellOrders() : orderBook.getBuyOrders();
        ArrayList<LimitOrder> toRemove = new ArrayList<>();
        int newPrice = getPrice();
        int deltaPrice = limitOrder.isBuy() ? 1 : -1;
        for(LimitOrder otherOrder : limitOrders)
        {
            LimitOrder fillWith = null;


            if(virtualOrderBook != null) {
                long loopTimeout = 10000;
                while (fillVolume > 0 &&
                        limitOrder.isBuy() && limitOrder.getPrice() > getPrice() ||
                        limitOrder.isSell() && limitOrder.getPrice() < getPrice()) {

                    if (getPrice() == otherOrder.getPrice()) {
                        fillWith = otherOrder;
                        break;
                    }

                    loopTimeout--;
                    if (loopTimeout <= 0) {
                        error("Limit order processing loop timeout: " + limitOrder);
                        limitOrder.markAsInvalid("Limit order processing loop timeout");
                        break;
                    }
                    long virtualVolume = virtualOrderBook.getAmount(newPrice);
                    long transferedVolume = TransactionEngine.virtualFill(tradingPair, limitOrder, virtualVolume, newPrice, priceScaleFactor, currencyItemFractionScaleFactor);
                    //filledVolume += transferedVolume;
                    fillVolume -= transferedVolume;
                    if (transferedVolume > 0) {
                        if (limitOrder.isBuy()) {
                            virtualOrderBook.removeAmount(newPrice, transferedVolume);
                            if (!limitOrder.isBot()) {
                                serverMarket.addItemImbalance(-transferedVolume);
                            }
                        } else if (limitOrder.isSell()) {
                            virtualOrderBook.removeAmount(newPrice, -transferedVolume);
                            if (!limitOrder.isBot()) {
                                serverMarket.addItemImbalance(transferedVolume);
                            }
                        }
                        historicalMarketData.addVolume(Math.abs(transferedVolume));
                        setPrice(newPrice);
                    }
                    if (limitOrder.isFilled()) {
                        break;
                    }
                    newPrice += deltaPrice;
                    if (newPrice < 0) {
                        newPrice = 0;
                        break;
                    }
                }
            }
            if(limitOrder.getPrice() == otherOrder.getPrice())
            {
                fillWith = otherOrder;
            }else {
                if (limitOrder.isBuy() && limitOrder.getPrice() > otherOrder.getPrice()) {
                    fillWith = otherOrder;
                } else if (limitOrder.isSell() && limitOrder.getPrice() < otherOrder.getPrice()) {
                    fillWith = otherOrder;
                }
            }

            if(fillWith == null)
                continue;

            long transferedVolume = TransactionEngine.fill(tradingPair ,limitOrder, fillWith, fillWith.getPrice(), priceScaleFactor, currencyItemFractionScaleFactor);
            if(fillWith.isFilled() || fillWith.getStatus() == Order.Status.CANCELLED || fillWith.getStatus() == Order.Status.INVALID)
                toRemove.add(fillWith);
            if(transferedVolume != 0)
            {
                setPrice(fillWith.getPrice());
                historicalMarketData.addVolume(Math.abs(transferedVolume));
                if(limitOrder.isBot() && !fillWith.isBot())
                {
                    serverMarket.addItemImbalance(fillWith.isBuy()?-transferedVolume:transferedVolume);
                }else if(!limitOrder.isBot() && fillWith.isBot())
                {
                    serverMarket.addItemImbalance(limitOrder.isBuy()?-transferedVolume:transferedVolume);
                }
            }
            fillVolume -= transferedVolume;

            if(fillVolume <= 0)
            {
                if(fillVolume<0)
                {
                    limitOrders.removeAll(toRemove);
                    error("Limit order overfilled: "+limitOrder);
                }
                break;
            }
        }


        if(virtualOrderBook != null) {
            long loopTimeout = 10000;
            while (fillVolume > 0 &&
                    (limitOrder.isBuy() && limitOrder.getPrice() > getPrice() ||
                            limitOrder.isSell() && limitOrder.getPrice() < getPrice())) {
                loopTimeout--;
                if (loopTimeout <= 0) {
                    error("Limit order processing loop timeout: " + limitOrder);
                    limitOrder.markAsInvalid("Limit order processing loop timeout");
                    break;
                }
                long virtualVolume = virtualOrderBook.getAmount(newPrice);
                long transferedVolume = TransactionEngine.virtualFill(tradingPair, limitOrder, virtualVolume, newPrice, priceScaleFactor, currencyItemFractionScaleFactor);
                fillVolume -= transferedVolume;
                if (transferedVolume > 0) {
                    if (limitOrder.isBuy()) {
                        virtualOrderBook.removeAmount(newPrice, transferedVolume);
                        if (!limitOrder.isBot()) {
                            serverMarket.addItemImbalance(-transferedVolume);
                        }
                    } else if (limitOrder.isSell()) {
                        virtualOrderBook.removeAmount(newPrice, -transferedVolume);
                        if (!limitOrder.isBot()) {
                            serverMarket.addItemImbalance(transferedVolume);
                        }
                    }
                    historicalMarketData.addVolume(Math.abs(transferedVolume));
                    setPrice(newPrice);
                }
                if (limitOrder.isFilled()) {
                    break;
                }
                newPrice += deltaPrice;
                if (newPrice < 0) {
                    newPrice = 0;
                    break;
                }
            }
        }



        limitOrders.removeAll(toRemove);
        return fillVolume == 0;
    }

    private void setPrice(int price)
    {
        historicalMarketData.setCurrentRawPrice(price);

        VirtualOrderBook virtualOrderBook = orderBook.getVirtualOrderBook();
        if(virtualOrderBook != null)
            virtualOrderBook.setCurrentPrice(price);
    }

    public int getPrice() {
        return historicalMarketData.getCurrentPrice();
    }


    public String toString()
    {
        return "MatchingEngine";
    }



    @Override
    public boolean save(CompoundTag tag) {
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        return true;
    }


    private void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[MatchingEngine: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    private void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[MatchingEngine: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    private void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[MatchingEngine: "+ tradingPair.getShortDescription() + "] " + msg, e);
    }
    private void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[MatchingEngine: "+ tradingPair.getShortDescription() + "] " + msg);
    }
    private void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[MatchingEngine: "+ tradingPair.getShortDescription() + "] " + msg);
    }
}
