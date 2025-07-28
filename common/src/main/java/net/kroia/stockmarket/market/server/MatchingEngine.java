package net.kroia.stockmarket.market.server;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

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

    //private int price;
    //private int tradeVolume;

    //private boolean marketOpen = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.MARKET_OPEN_AT_CREATION.get();

    // Create a sorted queue for buy and sell orders, sorted by price.
    //private final PriorityQueue<LimitOrder> limitBuyOrders = new PriorityQueue<>((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
    //private final PriorityQueue<LimitOrder> limitSellOrders = new PriorityQueue<>(Comparator.comparingDouble(LimitOrder::getPrice));

    //private final GhostOrderBook ghostOrderBook;
    //private long realVolumeImbalance = 0;
    //private PriceHistory priceHistory;
    public MatchingEngine(ServerMarket market)
    {
        this.serverMarket = market;
        //this.priceHistory = priceHistory;
        //this.ghostOrderBook = new GhostOrderBook(initialPrice);
        //tradeVolume = 0;
        //setPrice(initialPrice);
    }


    public void onServerTick(MinecraftServer server)
    {
        //ghostOrderBook.updateVolume(getPrice());
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
        OrderBook orderBook = serverMarket.getOrderBook();
        if (order instanceof LimitOrder limitOrder)
        {
            if(!processLimitOrder(limitOrder))
            {
                orderBook.placeLimitOrder(limitOrder);
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
        OrderBook orderBook = serverMarket.getOrderBook();
        GhostOrderBook ghostOrderBook = orderBook.getGhostOrderBook();
        HistoricalMarketData priceHistory = serverMarket.getHistoricalMarketData();
        PriorityQueue<LimitOrder> limitOrders = marketOrder.isBuy() ? orderBook.getSellOrders() : orderBook.getBuyOrders();
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        long fillVolume = Math.abs(marketOrder.getAmount());
        int newPrice = getPrice();
        int deltaPrice = marketOrder.isBuy() ? 1 : -1;
        for(LimitOrder limitOrder : limitOrders)
        {
            long filledVolume = 0;

            long loopTimeout = 10000;

            while(limitOrder.getPrice() != newPrice) {
                loopTimeout--;
                if(loopTimeout<=0)
                {
                    BACKEND_INSTANCES.LOGGER.error("Market order processing loop timeout: "+marketOrder);
                    marketOrder.markAsInvalid("Market order processing loop timeout");
                    break;
                }
                long ghostVolume = ghostOrderBook.getAmount(newPrice);
                long transferedVolume = TransactionEngine.ghostFill(serverMarket.getTradingPair() ,marketOrder, ghostVolume, newPrice);
                filledVolume += transferedVolume;
                if(transferedVolume > 0) {
                    if(marketOrder.isBuy()) {
                        ghostOrderBook.removeAmount(newPrice, transferedVolume);
                        if(!marketOrder.isBot())
                        {
                            serverMarket.addItemImbalance(-transferedVolume);
                        }
                    }
                    else if(marketOrder.isSell()) {
                        ghostOrderBook.removeAmount(newPrice, -transferedVolume);
                        if(!marketOrder.isBot())
                        {
                            serverMarket.addItemImbalance(transferedVolume);
                        }
                    }
                    setPrice(newPrice);
                    priceHistory.addVolume(Math.abs(transferedVolume));
                }
                if(marketOrder.isFilled())
                {
                    return true;
                }
                newPrice += deltaPrice;
                if(newPrice < 0) {
                    newPrice = 0;
                    break;
                }
            }

            long transferedVolume = TransactionEngine.fill(serverMarket.getTradingPair() ,marketOrder, limitOrder, limitOrder.getPrice());
            filledVolume += transferedVolume;

            if(limitOrder.isFilled() || limitOrder.getStatus() == Order.Status.CANCELLED || limitOrder.getStatus() == Order.Status.INVALID)
                toRemove.add(limitOrder);

            if(marketOrder.getStatus() == Order.Status.INVALID || marketOrder.getStatus() == Order.Status.CANCELLED)
                break;

            if(filledVolume != 0) {
                newPrice = limitOrder.getPrice();
                priceHistory.addVolume(Math.abs(transferedVolume));
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
                    BACKEND_INSTANCES.LOGGER.error("Market order overfilled: "+marketOrder);
                }
                break;
            }
        }
        long loopTimeout = 10000;
        while(fillVolume > 0)
        {
            loopTimeout--;
            if(loopTimeout<=0)
            {
                BACKEND_INSTANCES.LOGGER.error("Market order processing loop timeout: "+marketOrder);
                marketOrder.markAsInvalid("Market order processing loop timeout");
                break;
            }
            // Fill the remaining volume with ghost orders
            long ghostVolume = ghostOrderBook.getAmount(newPrice);
            long transferedVolume = TransactionEngine.ghostFill(serverMarket.getTradingPair() ,marketOrder, ghostVolume, newPrice);
            fillVolume -= transferedVolume;

            if(transferedVolume > 0) {
                if(marketOrder.isBuy()) {
                    ghostOrderBook.removeAmount(newPrice, transferedVolume);
                    if(!marketOrder.isBot())
                    {
                        serverMarket.addItemImbalance(-transferedVolume);
                    }
                }
                else if(marketOrder.isSell()) {
                    ghostOrderBook.removeAmount(newPrice, -transferedVolume);
                    if(!marketOrder.isBot())
                    {
                        serverMarket.addItemImbalance(transferedVolume);
                    }
                }
                priceHistory.addVolume(Math.abs(transferedVolume));
                setPrice(newPrice);
            }
            if(marketOrder.isFilled() || marketOrder.getStatus() == Order.Status.INVALID || marketOrder.getStatus() == Order.Status.CANCELLED)
                break;
            newPrice += deltaPrice;
            if(newPrice < 0) {
                newPrice = 0;
                break;
            }
        }
        limitOrders.removeAll(toRemove);

        if(fillVolume != 0)
        {
            marketOrder.markAsInvalid(StockMarketTextMessages.getOrderInvalidReasonOrdersToFillTransactionMessage(marketOrder.isBuy()));
            return false;
        }
        return true;
    }


    private boolean processLimitOrder(LimitOrder limitOrder)
    {
        // Process the limit order
        long fillVolume = Math.abs(limitOrder.getAmount()-limitOrder.getFilledAmount());
        OrderBook orderBook = serverMarket.getOrderBook();
        HistoricalMarketData priceHistory = serverMarket.getHistoricalMarketData();
        GhostOrderBook ghostOrderBook = orderBook.getGhostOrderBook();
        PriorityQueue<LimitOrder> limitOrders = limitOrder.isBuy() ? orderBook.getSellOrders() : orderBook.getBuyOrders();
        ArrayList<LimitOrder> toRemove = new ArrayList<>();
        int newPrice = getPrice();
        int deltaPrice = limitOrder.isBuy() ? 1 : -1;
        for(LimitOrder otherOrder : limitOrders)
        {
            LimitOrder fillWith = null;


            long loopTimeout = 10000;
            while(fillVolume > 0 &&
                    limitOrder.isBuy() && limitOrder.getPrice() > getPrice() ||
                    limitOrder.isSell() && limitOrder.getPrice() < getPrice()) {

                if(getPrice() == otherOrder.getPrice())
                {
                    fillWith = otherOrder;
                    break;
                }

                loopTimeout--;
                if(loopTimeout<=0)
                {
                    BACKEND_INSTANCES.LOGGER.error("Limit order processing loop timeout: "+limitOrder);
                    limitOrder.markAsInvalid("Limit order processing loop timeout");
                    break;
                }
                long ghostVolume = ghostOrderBook.getAmount(newPrice);
                long transferedVolume = TransactionEngine.ghostFill(serverMarket.getTradingPair() ,limitOrder, ghostVolume, newPrice);
                //filledVolume += transferedVolume;
                fillVolume -= transferedVolume;
                if(transferedVolume > 0) {
                    if(limitOrder.isBuy()) {
                        ghostOrderBook.removeAmount(newPrice, transferedVolume);
                        if(!limitOrder.isBot())
                        {
                            serverMarket.addItemImbalance(-transferedVolume);
                        }
                    }
                    else if(limitOrder.isSell()) {
                        ghostOrderBook.removeAmount(newPrice, -transferedVolume);
                        if(!limitOrder.isBot())
                        {
                            serverMarket.addItemImbalance(transferedVolume);
                        }
                    }
                    priceHistory.addVolume(Math.abs(transferedVolume));
                    setPrice(newPrice);
                }
                if(limitOrder.isFilled())
                {
                    break;
                }
                newPrice += deltaPrice;
                if(newPrice < 0) {
                    newPrice = 0;
                    break;
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

            long transferedVolume = TransactionEngine.fill(serverMarket.getTradingPair() ,limitOrder, fillWith, fillWith.getPrice());
            if(fillWith.isFilled() || fillWith.getStatus() == Order.Status.CANCELLED || fillWith.getStatus() == Order.Status.INVALID)
                toRemove.add(fillWith);
            if(transferedVolume != 0)
            {
                setPrice(fillWith.getPrice());
                priceHistory.addVolume(Math.abs(transferedVolume));
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
                    BACKEND_INSTANCES.LOGGER.error("Limit order overfilled: "+limitOrder);
                }
                break;
            }
        }



        long loopTimeout = 10000;
        while(  fillVolume > 0 &&
                (limitOrder.isBuy() && limitOrder.getPrice() > getPrice() ||
                limitOrder.isSell() && limitOrder.getPrice() < getPrice())) {
            loopTimeout--;
            if(loopTimeout<=0)
            {
                BACKEND_INSTANCES.LOGGER.error("Limit order processing loop timeout: "+limitOrder);
                limitOrder.markAsInvalid("Limit order processing loop timeout");
                break;
            }
            long ghostVolume = ghostOrderBook.getAmount(newPrice);
            long transferedVolume = TransactionEngine.ghostFill(serverMarket.getTradingPair(), limitOrder, ghostVolume, newPrice);
            fillVolume -= transferedVolume;
            if(transferedVolume > 0) {
                if(limitOrder.isBuy()) {
                    ghostOrderBook.removeAmount(newPrice, transferedVolume);
                    if(!limitOrder.isBot())
                    {
                        serverMarket.addItemImbalance(-transferedVolume);
                    }
                }
                else if(limitOrder.isSell()) {
                    ghostOrderBook.removeAmount(newPrice, -transferedVolume);
                    if(!limitOrder.isBot())
                    {
                        serverMarket.addItemImbalance(transferedVolume);
                    }
                }
                priceHistory.addVolume(Math.abs(transferedVolume));
                setPrice(newPrice);
            }
            if(limitOrder.isFilled())
            {
                break;
            }
            newPrice += deltaPrice;
            if(newPrice < 0) {
                newPrice = 0;
                break;
            }
        }



        limitOrders.removeAll(toRemove);
        return fillVolume == 0;
    }

    private void setPrice(int price)
    {
        serverMarket.getHistoricalMarketData().setCurrentPrice(price);
        serverMarket.getOrderBook().getGhostOrderBook().setCurrentPrice(price);
    }

    public int getPrice() {
        return serverMarket.getHistoricalMarketData().getCurrentPrice();
    }
   /* public int getTradeVolume() {
        return tradeVolume;
    }
    public int resetTradeVolume() {
        int volume = tradeVolume;
        tradeVolume = 0;
        return volume;
    }*/

    /*public boolean cancelOrder(long orderID)
    {
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getOrderID() == orderID)
            {
                order.markAsCancelled();
                limitBuyOrders.remove(order);
                return true;
            }
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getOrderID() == orderID)
            {
                order.markAsCancelled();
                limitSellOrders.remove(order);
                return true;
            }
        }
        return false;
    }
    public void cancelAllOrders(UUID playerOwner)
    {
        ArrayList<LimitOrder> toRemove = new ArrayList<>();
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getPlayerUUID().equals(playerOwner))
            {
                order.markAsCancelled();
                toRemove.add(order);
            }
        }
        limitBuyOrders.removeAll(toRemove);
        toRemove.clear();
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getPlayerUUID().equals(playerOwner))
            {
                order.markAsCancelled();
                toRemove.add(order);
            }
        }
        limitSellOrders.removeAll(toRemove);
    }*/
    /*public void cancelAllOrders()
    {
        for(LimitOrder order : limitBuyOrders)
        {
            order.markAsCancelled();
        }
        limitBuyOrders.clear();
        for(LimitOrder order : limitSellOrders)
        {
            order.markAsCancelled();
        }
        limitSellOrders.clear();
    }*/

    /*public boolean changeOrderPrice(long orderID, int newPrice)
    {
        if(newPrice < 0)
            newPrice = 0;
        LimitOrder targetOrder = null;
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getOrderID() == orderID)
            {
                targetOrder = order;
                break;
            }
        }
        if(targetOrder == null) {
            for (LimitOrder order : limitSellOrders) {
                if (order.getOrderID() == orderID) {
                    targetOrder = order;
                    break;
                }
            }
        }
        if(targetOrder == null)
            return false;

        TradingPair tradingPair = serverMarket.getTradingPair();



        long toFillAmount = targetOrder.getAmount()-targetOrder.getFilledAmount();
        ServerPlayer player = PlayerUtilities.getOnlinePlayer(targetOrder.getPlayerUUID());
        boolean canBeMoved = false;
        if(targetOrder.isBuy())
        {
            long toFreeAmount = toFillAmount * targetOrder.getPrice();
            IBank moneyBank = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(targetOrder.getPlayerUUID()).getBank(tradingPair.getCurrency());
            if(moneyBank != null)
                canBeMoved = moneyBank.getTotalBalance()-toFreeAmount >= 0;
        }
        else
        {
            IBank itemBank = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getUser(targetOrder.getPlayerUUID()).getBank(tradingPair.getItem());
            if(itemBank != null)
                canBeMoved = itemBank.getTotalBalance()-toFillAmount >= 0;
        }

        if(canBeMoved && player != null)
        {
            cancelOrder(orderID);

            LimitOrder newOrder = OrderFactory.createLimitOrder(player, tradingPair, targetOrder.getAmount(), newPrice, targetOrder.getFilledAmount());
            if(newOrder != null) {
                addOrder(newOrder);
                return true;
            }
            else {
                LimitOrder oldOrder = OrderFactory.createLimitOrder(player, tradingPair, targetOrder.getAmount(), targetOrder.getPrice(), targetOrder.getFilledAmount());
                if(oldOrder != null)
                    addOrder(oldOrder);
                return false;
            }
        }
        return false;
    }*/

    /*public boolean removeOrder_internal(LimitOrder toRemove)
    {
        return limitBuyOrders.remove(toRemove) || limitSellOrders.remove(toRemove);
    }
    public boolean removeOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitBuyOrders.removeAll(orders) || limitSellOrders.removeAll(orders);
    }*/
    /*public boolean removeSellOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitSellOrders.removeAll(orders);
    }
    public boolean removeBuyOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitBuyOrders.removeAll(orders);
    }*/


    public String toString()
    {
        return "MatchingEngine";
        //return "MatchingEngine{ Price: " + price + " TradeVolume: " + tradeVolume +
        //        "Sell Orders: "+ limitSellOrders.size()+" Buy Orders: "+ limitBuyOrders.size()+" }";
    }

    /**
     * Returns a volume heatmap of the order book in the given price range.
     * The volume is divided into the given number of tiles.
     * @param tiles The number of tiles to divide the price range into.
     * @param minPrice The minimum price of the heatmap.
     * @param maxPrice The maximum price of the heatmap.
     * @return An array of integers representing the volume in each tile.
     */
   /* public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
    {
        OrderbookVolume orderbookVolume = new OrderbookVolume(tiles, minPrice, maxPrice);
        int priceRange = maxPrice - minPrice;
        float priceStep = (float)priceRange / (float)tiles;
        int[] volume = new int[tiles];
        for(LimitOrder order : limitBuyOrders)
        {
            int index = (int)((float)(order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }
        for(LimitOrder order : limitSellOrders)
        {
            int index = (int)((float)(order.getPrice() - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += order.getAmount()-order.getFilledAmount();
        }

        for(int i=minPrice; i<maxPrice; i++)
        {
            int index = (int)((float)(i - minPrice) / priceStep);
            if(index >= 0 && index < tiles)
                volume[index] += ghostOrderBook.getAmount(i);
        }

        orderbookVolume.setVolume(volume);
        return orderbookVolume;
    }

    public int getVolume(int price)
    {
        int volume = 0;
        if(price < this.price)
        {
            for(LimitOrder order : limitBuyOrders)
            {
                if(order.getPrice() == price)
                    volume += order.getAmount()-order.getFilledAmount();
            }
        }
        else
        {
            for(LimitOrder order : limitSellOrders)
            {
                if(order.getPrice() == price)
                    volume += order.getAmount()-order.getFilledAmount();
            }
        }
        volume += ghostOrderBook.getAmount(price);
        return volume;
    }
    public int getVolume(int minPrice, int maxPrice)
    {
        int volume = 0;
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getPrice() >= minPrice && order.getPrice() <= maxPrice)
                volume += order.getAmount()-order.getFilledAmount();
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getPrice() >= minPrice && order.getPrice() <= maxPrice)
                volume += order.getAmount()-order.getFilledAmount();
        }
        for(int i=minPrice; i<=maxPrice; i++)
        {
            volume += ghostOrderBook.getAmount(i);
        }
        return volume;
    }
*/

    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        //tag.putInt("trade_volume", tradeVolume);

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        /*if(
                !tag.contains("trade_volume"))
            return false;*/
        boolean success = true;
        //price = tag.getInt("price");
        //ghostOrderBook.setCurrentMarketPrice(price);
        //tradeVolume = tag.getInt("trade_volume");
        return success;
    }
}
