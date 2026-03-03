package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.orders.InterMarketOrder;
import net.kroia.stockmarket.market.orders.Order;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Consumer;

public class MatchingEngine
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final ItemID itemID;
    private final Orderbook orderbook;

    private final PriorityQueue<Order> buyMarkeOrders_inputBuffer;
    private final PriorityQueue<Order> selMarketOrders_inputBuffer;
    private final PriorityQueue<Order> buyLimitOrders_inputBuffer;
    private final PriorityQueue<Order> sellLimitOrders_inputBuffer;
    private final PriorityQueue<InterMarketOrder> interMarket_LimitBuyOrders_inputBuffer;
    private final PriorityQueue<InterMarketOrder> interMarket_MarketBuyOrders_inputBuffer;

    private final @NotNull Consumer<Order> consumedOrderCallback;
    private final @NotNull Consumer<InterMarketOrder> consumedInterMarketOrderCallback;
    private final @NotNull Consumer<Order> cancelOrderCallback;
    private final @NotNull Consumer<InterMarketOrder> cancelInterMarketOrderCallback;


    private final Orderbook.LongPair pair_cache =  new Orderbook.LongPair();
    private long currentMarketPrice;


    public MatchingEngine(ItemID itemID, Orderbook orderbook,
                          PriorityQueue<Order> buyMarketOrders_inputBuffer,
                          PriorityQueue<Order> selMarketOrders_inputBuffer,
                          PriorityQueue<Order> buyLimitOrders_inputBuffer,
                          PriorityQueue<Order> sellLimitOrders_inputBuffer,
                          PriorityQueue<InterMarketOrder> interMarket_LimitBuyOrders_inputBuffer,
                          PriorityQueue<InterMarketOrder> interMarket_MarketBuyOrders_inputBuffer,
                          @NotNull Consumer<Order> consumedOrderCallback,
                          @NotNull Consumer<InterMarketOrder> consumedInterMarketOrderCallback,
                          @NotNull Consumer<Order> cancelOrderCallback,
                          @NotNull Consumer<InterMarketOrder> cancelInterMarketOrderCallback)
    {
        this.itemID = itemID;
        this.orderbook = orderbook;

        this.buyMarkeOrders_inputBuffer = buyMarketOrders_inputBuffer;
        this.selMarketOrders_inputBuffer = selMarketOrders_inputBuffer;
        this.buyLimitOrders_inputBuffer = buyLimitOrders_inputBuffer;
        this.sellLimitOrders_inputBuffer = sellLimitOrders_inputBuffer;
        this.interMarket_LimitBuyOrders_inputBuffer = interMarket_LimitBuyOrders_inputBuffer;
        this.interMarket_MarketBuyOrders_inputBuffer = interMarket_MarketBuyOrders_inputBuffer;

        this.consumedOrderCallback = consumedOrderCallback;
        this.consumedInterMarketOrderCallback = consumedInterMarketOrderCallback;
        this.cancelOrderCallback = cancelOrderCallback;
        this.cancelInterMarketOrderCallback = cancelInterMarketOrderCallback;
    }



    public void update(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;

    }


    private void processMarketOrder(Order order)
    {
        long volume = order.getRemainingVolume();
        UUID mainBankAccount = order.getOwnerBankID();
       // IBankAccount mainBankAccount = BACKEND_INSTANCES.BANK_SYSTEM_API.getSer
        if(volume < 0)
        {
            // Sell order
            if(orderbook.getPriceWhenConsumingVolume(volume, pair_cache))
            {
                long earnedMoney = pair_cache.second;
                List<Order> matchableOrders = orderbook.getBuyOrders(currentMarketPrice, pair_cache.first); // Get Orders that are in matchable range
                for(Order buyOrder : matchableOrders)
                {
                    long buyOrderPrice = buyOrder.getStartPrice();
                    while(buyOrderPrice < currentMarketPrice)
                    {
                        // Fill virtual orderbook

                    }
                }
            }
        }
    }



    @Nullable IBankAccount getBankAccount(int bankAccountID)
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(bankAccountID);
    }
    @Nullable IBank getBank(int bankAccountID)
    {
        IBankAccount account = getBankAccount(bankAccountID);
        if(account != null)
        {
            IBank bank = account.getBank(itemID);
            if(bank == null)
            {
                error("No bank for item: "+itemID+ " in bank acocunt: "+bankAccountID);
            }
            return bank;
        }
        error("No bank account with the accountNR: "+bankAccountID);
        return null;
    }
    @Nullable IBank getMoneyBank(int bankAccountID)
    {
        IBankAccount account = getBankAccount(bankAccountID);
        ItemID moneyID = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get();
        if(account != null && moneyID != null)
        {
            IBank bank = account.getBank(moneyID);
            if(bank == null)
            {
                error("No bank for item: "+moneyID+ " in bank acocunt: "+bankAccountID);
            }
            return bank;
        }
        if(moneyID == null)
        {
            error("No currency set in the settings!");
        }
        if(account == null)
        {
            error("No bank account with the accountNR: "+bankAccountID);
        }
        return null;
    }


    private void orderConsumed(Order order)
    {
        consumedOrderCallback.accept(order);
    }
    private void orderConsumed(InterMarketOrder order)
    {
        consumedInterMarketOrderCallback.accept(order);
    }
    private void orderCanceled(Order order)
    {
        cancelOrderCallback.accept(order);
    }
    private void orderCanceled(InterMarketOrder order)
    {
        cancelInterMarketOrderCallback.accept(order);
    }



    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[MatchingEngine:"+itemID+"]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[MatchingEngine:"+itemID+"]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[MatchingEngine:"+itemID+"]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[MatchingEngine:"+itemID+"]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[MatchingEngine:"+itemID+"]: "+message);
    }
}
