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

    private final @NotNull Consumer<Long> priceChanged;


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
                          @NotNull Consumer<InterMarketOrder> cancelInterMarketOrderCallback,
                          @NotNull Consumer<Long> priceChanged)
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

        this.priceChanged = priceChanged;
    }



    public void update(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;

    }


    private void processMarketOrder(Order order)
    {
        long volume = order.getRemainingVolume();
        UUID executorPlayerUUID = order.getExecutorPlayerUUID();
        IBank itemBank = null;
        IBank moneyBank = null;

        IBankAccount account = getBankAccount(order.getBankAccountNr());
        if(account != null)
        {
            itemBank  = account.getBank(itemID);
            moneyBank = account.getBank(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
        }
        if(itemBank == null || moneyBank == null)
        {
            orderCanceled(order); // Missing bank account
            return;
        }

        if(volume < 0)
        {
            // Sell order

            // check item balance
            long availableItemBalance = itemBank.getTotalBalance();
            if(availableItemBalance < volume)
                volume = availableItemBalance;

            if(orderbook.getPriceWhenConsumingVolume(volume, pair_cache))
            {
                //long earnedMoney = pair_cache.second;
                long itemBankBalanceDelta = 0;
                long moneyBankBalanceDelta = 0;
                List<Order> matchableOrders = orderbook.getBuyOrders(currentMarketPrice, pair_cache.first); // Get Orders that are in matchable range
                for(Order buyOrder : matchableOrders)
                {
                    long buyOrderPrice = buyOrder.getStartPrice();
                    while(buyOrderPrice < currentMarketPrice)
                    {
                        // Fill virtual orderbook
                        if(orderbook.getVirtualPriceRounded(currentMarketPrice) > 0) {
                            long filled = orderbook.fillVirtual(currentMarketPrice, volume);
                            itemBankBalanceDelta += filled;
                            moneyBankBalanceDelta -= currentMarketPrice * filled;
                            volume -= filled;
                            if(volume >= 0)
                            {
                                itemBank.withdrawLockedPrefered(-itemBankBalanceDelta);
                                moneyBank.deposit(moneyBankBalanceDelta);
                                orderConsumed(order);
                                return;
                            }
                        }
                        else
                        {
                            currentMarketPrice -= 1;
                        }
                    }


                    long fillPotential = buyOrder.getRemainingVolume();
                    if(buyOrder.isPlayerOrder())
                    {
                        // Player offer
                        IBankAccount otherAccount = getBankAccount(buyOrder.getBankAccountNr());
                        IBank otherItemBank = null;
                        IBank otherMoneyBank = null;
                        if (otherAccount != null) {
                            otherItemBank = otherAccount.getBank(itemID);
                            otherMoneyBank = otherAccount.getBank(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
                        }
                        if (otherItemBank == null || otherMoneyBank == null) {
                            orderbook.removeOrder(buyOrder);
                            orderCanceled(buyOrder); // Missing bank account
                            continue;
                        }
                        long otherMoneyBalance = otherMoneyBank.getTotalBalance();
                        long otherCost = fillPotential * buyOrderPrice;
                        if(otherMoneyBalance < otherCost)
                        {
                            fillPotential = otherMoneyBalance /  buyOrderPrice;
                            otherCost = fillPotential * buyOrderPrice;
                            buyOrder.edit(fillPotential, -otherCost);
                            volume += fillPotential;
                            assert(fillPotential > 0);
                            assert(otherCost < 0);
                            otherItemBank.deposit(fillPotential);
                            otherMoneyBank.withdrawLockedPrefered(-otherCost);
                            itemBankBalanceDelta -= fillPotential;
                            moneyBankBalanceDelta += otherCost;
                            orderbook.removeOrder(buyOrder);
                            orderCanceled(buyOrder);
                            if(volume >= 0)
                            {
                                itemBank.withdrawLockedPrefered(-itemBankBalanceDelta);
                                moneyBank.deposit(moneyBankBalanceDelta);
                                orderConsumed(order);
                                return;
                            }
                            continue;
                        }
                        else
                        {
                            buyOrder.edit(fillPotential, -otherCost);
                            volume += fillPotential;
                            assert(fillPotential > 0);
                            assert(otherCost < 0);
                            otherItemBank.deposit(fillPotential);
                            otherMoneyBank.withdrawLockedPrefered(-otherCost);
                            itemBankBalanceDelta -= fillPotential;
                            moneyBankBalanceDelta += otherCost;
                            if(buyOrder.isFilled()) {
                                orderbook.removeOrder(buyOrder);
                                orderConsumed(buyOrder);
                            }
                        }
                    }
                    else {
                        // Bot offer
                        long otherCost = fillPotential * buyOrderPrice;
                        buyOrder.edit(fillPotential, -otherCost);
                        volume += fillPotential;
                        assert(fillPotential > 0);
                        assert(otherCost < 0);
                        itemBankBalanceDelta -= fillPotential;
                        moneyBankBalanceDelta += otherCost;
                        if(buyOrder.isFilled()) {
                            orderbook.removeOrder(buyOrder);
                            orderConsumed(buyOrder);
                        }
                    }
                    if(volume >= 0)
                    {
                        itemBank.withdrawLockedPrefered(-itemBankBalanceDelta);
                        moneyBank.deposit(moneyBankBalanceDelta);
                        orderConsumed(order);
                        return;
                    }
                }
            }
        }
    }



    @Nullable IBankAccount getBankAccount(int bankAccountID)
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getBankAccount(bankAccountID);
    }
    @Nullable IBank getItemBank(int bankAccountID)
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
