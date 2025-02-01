package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;

/*
    * The MatchingEngine class is responsible for matching buy and sell orders.
 */
public class MatchingEngine implements ServerSaveable {
    private int price;
    private int tradeVolume;

    private boolean marketOpen = true;

    // Create a sorted queue for buy and sell orders, sorted by price.
    private final PriorityQueue<LimitOrder> limitBuyOrders = new PriorityQueue<>((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice()));
    private final PriorityQueue<LimitOrder> limitSellOrders = new PriorityQueue<>(Comparator.comparingDouble(LimitOrder::getPrice));

    private PriceHistory priceHistory;
   // private ServerTradingBot tradingBot;
    public MatchingEngine(int initialPrice, PriceHistory priceHistory)
    {
        this.priceHistory = priceHistory;
        this.price = initialPrice;
        tradeVolume = 0;
       // this.tradingBot = tradingBot;
    }
    /*public MatchingEngine(int initialPrice) {
        this.price = initialPrice;
        tradeVolume = 0;
        tradingBot = null;
    }*/

  /*  public void setTradingBot(ServerTradingBot tradingBot)
    {
        this.tradingBot = tradingBot;
    }*/




    public void addOrder(Order order)
    {
        if(order == null)
            return;
        if(!marketOpen && !order.isBot())
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
            //limitOrder.setAveragePrice(limitOrder.getPrice());
            if(!processLimitOrder(limitOrder))
            {
                if (limitOrder.isBuy())
                {
                    // Add the limit order to the buyOrders queue
                    limitBuyOrders.add(limitOrder);
                }
                else
                {
                    // Add the limit order to the sellOrders queue
                    limitSellOrders.add(limitOrder);
                }
                limitOrder.notifyPlayer();
            }
        } else if (order instanceof MarketOrder marketOrder) {
            processMarketOrder(marketOrder);
            //marketOrder.notifyPlayer();
        } else {
            throw new IllegalArgumentException("Invalid order type");
        }
    }

    public ArrayList<Order> getOrders()
    {
        ArrayList<Order> orders = new ArrayList<>();
        orders.addAll(limitBuyOrders);
        orders.addAll(limitSellOrders);
        return orders;
    }

    public void getOrders(UUID playerUUID, ArrayList<Order> orders)
    {
        for(LimitOrder order : limitBuyOrders)
        {
            if(order.getPlayerUUID().equals(playerUUID))
                orders.add(order);
        }
        for(LimitOrder order : limitSellOrders)
        {
            if(order.getPlayerUUID().equals(playerUUID))
                orders.add(order);
        }
    }
    public PriorityQueue<LimitOrder> getBuyOrders()
    {
        return limitBuyOrders;
    }
    public PriorityQueue<LimitOrder> getSellOrders()
    {
        return limitSellOrders;
    }

    /**
     * Processes a market order by filling it with the best available limit orders.
     * @param marketOrder The market order to process.
     * @return true if the market order has been fully processed, false otherwise.
     */
    private boolean processMarketOrder(MarketOrder marketOrder)
    {
        PriorityQueue<LimitOrder> limitOrders = marketOrder.isBuy() ? limitSellOrders : limitBuyOrders;
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        int fillVolume = Math.abs(marketOrder.getAmount());
        for(LimitOrder limitOrder : limitOrders)
        {
            int filledVolume = TransactionEngine.fill(marketOrder, limitOrder, limitOrder.getPrice());

            if(filledVolume != 0) {
                setPrice(limitOrder.getPrice());
                if(limitOrder.isFilled())
                    toRemove.add(limitOrder);
            }

            fillVolume -= filledVolume;

            if(fillVolume <= 0)
            {
                if(fillVolume<0)
                {
                    limitOrders.removeAll(toRemove);
                    //throw new IllegalStateException("Market order overfilled");
                    StockMarketMod.LOGGER.error("Market order overfilled: "+marketOrder);
                }
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
        int fillVolume = Math.abs(limitOrder.getAmount()-limitOrder.getFilledAmount());
        PriorityQueue<LimitOrder> limitOrders = limitOrder.isBuy() ? limitSellOrders : limitBuyOrders;
        ArrayList<LimitOrder> toRemove = new ArrayList<>();

        for(LimitOrder otherOrder : limitOrders)
        {
            LimitOrder fillWith = null;
            if(otherOrder.getPrice() == limitOrder.getPrice())
            {
                fillWith = otherOrder;
            }
            else
            {
                if(limitOrder.isBuy() && limitOrder.getPrice() > otherOrder.getPrice())
                {
                    fillWith = otherOrder;
                }
                else if(limitOrder.isSell() && limitOrder.getPrice() < otherOrder.getPrice())
                {
                    fillWith = otherOrder;
                }
            }
            if(fillWith == null)
                continue;

            int filledVolume = TransactionEngine.fill(limitOrder, fillWith, fillWith.getPrice());
            if(filledVolume != 0)
            {
                setPrice(fillWith.getPrice());
                if(fillWith.isFilled())
                    toRemove.add(fillWith);
            }
            fillVolume -= filledVolume;

            if(fillVolume <= 0)
            {
                if(fillVolume<0)
                {
                    limitOrders.removeAll(toRemove);
                    //throw new IllegalStateException("Limit order overfilled");
                    StockMarketMod.LOGGER.error("Limit order overfilled: "+limitOrder);
                }
                break;
            }
        }
        limitOrders.removeAll(toRemove);
        return fillVolume == 0;
    }

    public ArrayList<LimitOrder> getLimitBuyOrders() {
        return new ArrayList<>(limitBuyOrders);
    }
    public ArrayList<LimitOrder> getLimitSellOrders() {
        return new ArrayList<>(limitSellOrders);
    }

    private void setPrice(int price)
    {
        this.price = price;
        priceHistory.setCurrentPrice(price);
    }

    public int getPrice() {
        return price;
    }
    public int getTradeVolume() {
        return tradeVolume;
    }
    public boolean isMarketOpen() {
        return marketOpen;
    }
    public void setMarketOpen(boolean marketOpen) {
        this.marketOpen = marketOpen;
    }
    public int resetTradeVolume() {
        int volume = tradeVolume;
        tradeVolume = 0;
        return volume;
    }

    public boolean cancelOrder(long orderID)
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
    }
    public void cancelAllOrders()
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
    }

    public boolean changeOrderPrice(long orderID, int newPrice)
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
        int toFillAmount = targetOrder.getAmount()-targetOrder.getFilledAmount();
        Bank moneyBank = ServerBankManager.getUser(targetOrder.getPlayerUUID()).getMoneyBank();
        int toFreeAmount = toFillAmount * targetOrder.getPrice();
        ServerPlayer player = PlayerUtilities.getOnlinePlayer(targetOrder.getPlayerUUID());
        if(moneyBank.getBalance()-toFreeAmount >= 0 && player != null)
        {
            cancelOrder(orderID);
            LimitOrder newOrder = LimitOrder.create(player, targetOrder.getItemID(), targetOrder.getAmount(), newPrice, targetOrder.getFilledAmount());
            if(newOrder != null) {
                addOrder(newOrder);
                return true;
            }
            else {
                LimitOrder oldOrder = LimitOrder.create(player, targetOrder.getItemID(), targetOrder.getAmount(), targetOrder.getPrice(), targetOrder.getFilledAmount());
                if(oldOrder != null)
                    addOrder(oldOrder);
                return false;
            }
        }
        return false;
    }

    public boolean removeOrder_internal(LimitOrder toRemove)
    {
        return limitBuyOrders.remove(toRemove) || limitSellOrders.remove(toRemove);
    }
    public boolean removeOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitBuyOrders.removeAll(orders) || limitSellOrders.removeAll(orders);
    }
    public boolean removeSellOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitSellOrders.removeAll(orders);
    }
    public boolean removeBuyOrder_internal(ArrayList<LimitOrder> orders)
    {
        return limitBuyOrders.removeAll(orders);
    }


    public String toString()
    {
        return "MatchingEngine{ Price: " + price + " TradeVolume: " + tradeVolume +
                "Sell Orders: "+ limitSellOrders.size()+" Buy Orders: "+ limitBuyOrders.size()+" }";
    }

    /**
     * Returns a volume heatmap of the order book in the given price range.
     * The volume is divided into the given number of tiles.
     * @param tiles The number of tiles to divide the price range into.
     * @param minPrice The minimum price of the heatmap.
     * @param maxPrice The maximum price of the heatmap.
     * @return An array of integers representing the volume in each tile.
     */
    public OrderbookVolume getOrderBookVolume(int tiles, int minPrice, int maxPrice)
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
        return volume;
    }


    @Override
    public boolean save(CompoundTag tag) {
        boolean success = true;
        ListTag buyOrdersList = new ListTag();
        ListTag sellOrdersList = new ListTag();
        for(LimitOrder order : limitBuyOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            success &= order.save(orderTag);
            buyOrdersList.add(orderTag);
        }

        for(LimitOrder order : limitSellOrders)
        {
            CompoundTag orderTag = new CompoundTag();
            success &= order.save(orderTag);
            sellOrdersList.add(orderTag);
        }

        tag.putInt("price", price);
        tag.putInt("trade_volume", tradeVolume);
        tag.put("buy_orders", buyOrdersList);
        tag.put("sell_orders", sellOrdersList);
        tag.putBoolean("market_open", marketOpen);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("price") ||
                !tag.contains("trade_volume") ||
                !tag.contains("buy_orders") ||
                !tag.contains("sell_orders"))
            return false;
        boolean success = true;
        price = tag.getInt("price");
        tradeVolume = tag.getInt("trade_volume");
        ListTag buyOrdersList = tag.getList("buy_orders", 10);
        ListTag sellOrdersList = tag.getList("sell_orders", 10);
        if(tag.contains("market_open"))
            marketOpen = tag.getBoolean("market_open");
        else
            marketOpen = true;
        for(int i = 0; i < buyOrdersList.size(); i++)
        {
            CompoundTag orderTag = buyOrdersList.getCompound(i);
            LimitOrder order = LimitOrder.loadFromTag(orderTag);
            if(order == null)
                success = false;
            else
                limitBuyOrders.add(order);
        }

        for(int i = 0; i < sellOrdersList.size(); i++)
        {
            CompoundTag orderTag = sellOrdersList.getCompound(i);
            LimitOrder order = LimitOrder.loadFromTag(orderTag);
            if(order == null)
                success = false;
            else
                limitSellOrders.add(order);
        }
        return success;
    }
}
