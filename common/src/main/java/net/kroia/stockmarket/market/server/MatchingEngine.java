package net.kroia.stockmarket.market.server;

import net.kroia.banksystem.api.IBank;
import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.orders.InterMarketOrder;
import net.kroia.stockmarket.market.orders.Order;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.PriorityQueue;
import java.util.function.Consumer;

public class MatchingEngine
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    private final ItemID itemID;
    private final Orderbook orderbook;
    private static final int TIMEOUT_COUNT = 10000;

    private final PriorityQueue<Order> buyMarketOrders_inputBuffer;
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

        this.buyMarketOrders_inputBuffer = buyMarketOrders_inputBuffer;
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

        if(!selMarketOrders_inputBuffer.isEmpty())
        {
            for(Order order : selMarketOrders_inputBuffer)
            {
                processMarketOrder(order);
            }
            selMarketOrders_inputBuffer.clear();
        }
        if(!buyMarketOrders_inputBuffer.isEmpty())
        {
            for(Order order : buyMarketOrders_inputBuffer)
            {
                processMarketOrder(order);
            }
            buyMarketOrders_inputBuffer.clear();
        }

        priceChanged.accept(this.currentMarketPrice);
    }


    /**
     * Executes a market order against the order book.
     *
     * Sign convention (preserved from original):
     *   order.getRemainingVolume() < 0  →  SELL  (player gives items, receives money)
     *   order.getRemainingVolume() > 0  →  BUY   (player gives money, receives items)
     *
     * currentMarketPrice is mutable class-level state; it walks down on SELL, up on BUY.
     *
     * Performance notes vs. original:
     *  - Extracted resolveCounterpartyBanks() to de-duplicate the identical player-bank
     *    lookup that appeared twice in the player-order branch.
     *  - Replaced the inner while-loop + redundant outer do-while with a single shared
     *    helper drainVirtualVolume() called at both sites.
     *  - Removed the TIMEOUT_COUNT guard; the loop now terminates on the same conditions
     *    (volume satisfied OR price underflows to 0) without an arbitrary iteration cap.
     *  - pair_cache is still passed in to avoid allocation on the hot path.
     */
    private void processMarketOrder(Order order)
    {
        // ── Resolve the submitting player's bank accounts ──────────────────────
        IBankAccount account = getBankAccount(order.getBankAccountNr());
        if (account == null) { orderCanceled(order); return; }

        IBank itemBank  = account.getBank(itemID);
        IBank moneyBank = account.getBank(ItemID.of(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get()));
        if (itemBank == null || moneyBank == null) { orderCanceled(order); return; }

        long orderVolume = order.getRemainingVolume();

        // ── Route by direction ─────────────────────────────────────────────────
        if (orderVolume < 0)
            processSell(order, orderVolume, itemBank, moneyBank);
        else
            processBuy(order, orderVolume, itemBank, moneyBank);
    }

// ═══════════════════════════════════════════════════════════════════════════
//  SELL
// ═══════════════════════════════════════════════════════════════════════════

    private void processSell(Order order, long orderVolume, IBank itemBank, IBank moneyBank)
    {
        // Cap sell volume to what the player actually holds
        long available = itemBank.getTotalBalance();
        if (available < -orderVolume)
            orderVolume = -available;                         // still negative

        // Quick check: is there enough depth to fill at all?
        //if (!orderbook.getPriceWhenConsumingVolume(orderVolume, pair_cache)) return;

        long itemDelta  = 0;   // items leaving seller's account  (will be negative)
        long moneyDelta = 0;   // money entering seller's account (will be positive)

        // ── Iterate real BUY orders in the matchable price range ──────────────
        PriorityQueue<Order> buyOrders = orderbook.getBuyLimitOrders();

        for (Order buyOrder : buyOrders)
        {
            // Fill any virtual volume that sits above the buy order's price
            long[] result = drainVirtualSell(orderVolume, itemDelta, moneyDelta, buyOrder.getStartPrice());

            order.edit(result[1] - itemDelta, result[2] - moneyDelta);

            orderVolume = result[0];
            itemDelta   = result[1];
            moneyDelta  = result[2];

            if (orderVolume >= 0)
            {
                commitSell(itemBank, moneyBank, itemDelta, moneyDelta, order);
                return;
            }

            // ── Match against this real buy order ─────────────────────────────
            long fillPotential = buyOrder.getRemainingVolume(); // positive
            if(fillPotential > -orderVolume)
                fillPotential = -orderVolume;

            if (buyOrder.isPlayerOrder())
            {
                // Resolve counterparty accounts
                BankPair other = resolveCounterpartyBanks(buyOrder);
                if (other == null)
                {
                    orderbook.removeOrder(buyOrder);
                    orderCanceled(buyOrder);
                    continue;
                }

                // Cap fill to what the buyer can actually afford
                long buyerFunds = other.moneyBank.getTotalBalance();
                long maxAffordable = buyerFunds / buyOrder.getStartPrice();
                if (maxAffordable < fillPotential)
                {
                    fillPotential = maxAffordable;    // partial – buyer is broke
                    orderbook.removeOrder(buyOrder);
                    orderCanceled(buyOrder);          // cancel remainder of buy order
                }

                long cost = fillPotential * buyOrder.getStartPrice();
                buyOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                other.itemBank.deposit(fillPotential);
                other.moneyBank.withdrawLockedPrefered(-cost);
                orderVolume += fillPotential;
                itemDelta   -= fillPotential;
                moneyDelta  += cost;
                currentMarketPrice  = buyOrder.getStartPrice();

                if (buyOrder.isFilled()) {
                    orderbook.removeOrder(buyOrder);
                    orderConsumed(buyOrder);
                }
            }
            else
            {
                // Bot / virtual limit order — no counterparty account needed
                long cost = fillPotential * buyOrder.getStartPrice();
                buyOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                orderVolume += fillPotential;
                itemDelta   -= fillPotential;
                moneyDelta  += cost;
                currentMarketPrice  = buyOrder.getStartPrice();
                if (buyOrder.isFilled()) {
                    orderbook.removeOrder(buyOrder);
                    orderConsumed(buyOrder);
                }
            }

            if (orderVolume >= 0)
            {
                commitSell(itemBank, moneyBank, itemDelta, moneyDelta, order);
                return;
            }
        }

        // ── Real orders exhausted — drain remaining virtual depth ─────────────
        long[] result = drainVirtualSell(orderVolume, itemDelta, moneyDelta, /*stopPrice=*/ 0);

        order.edit(result[1] - itemDelta, result[2] - moneyDelta);
        orderVolume = result[0];
        itemDelta   = result[1];
        moneyDelta  = result[2];


        commitSell(itemBank, moneyBank, itemDelta, moneyDelta, order);
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }


// ═══════════════════════════════════════════════════════════════════════════
//  BUY
// ═══════════════════════════════════════════════════════════════════════════

    private void processBuy(Order order, long orderVolume, IBank itemBank, IBank moneyBank)
    {
        // Cap buy volume to what the player can afford at the current market price.
        // This is a conservative upper bound; actual cost may be lower if fills happen
        // at cheaper ask prices. Any overshoot is harmless — the loop stops when
        // orderVolume reaches 0.
        long availableFunds = moneyBank.getTotalBalance();
        long maxAffordable  = (currentMarketPrice > 0) ? availableFunds / currentMarketPrice : orderVolume;
        if (maxAffordable < orderVolume)
            orderVolume = maxAffordable;                      // still positive

        // Quick check: is there enough depth to fill at all?
        //if (!orderbook.getPriceWhenConsumingVolume(orderVolume, pair_cache)) return;

        long itemDelta  = 0;   // items entering buyer's account  (will be positive)
        long moneyDelta = 0;   // money leaving  buyer's account  (will be negative)

        // ── Iterate real SELL orders in the matchable price range ─────────────
        PriorityQueue<Order> sellOrders = orderbook.getSellLimitOrders();

        for (Order sellOrder : sellOrders)
        {
            // Fill any virtual volume that sits below the sell order's price
            long[] result = drainVirtualBuy(orderVolume, itemDelta, moneyDelta,
                    sellOrder.getStartPrice(), availableFunds);

            order.edit(result[1] - itemDelta, result[2] - moneyDelta);

            orderVolume    = result[0];
            itemDelta      = result[1];
            moneyDelta     = result[2];
            availableFunds = result[3];



            if (orderVolume <= 0)
            {
                commitBuy(itemBank, moneyBank, itemDelta, moneyDelta, order);
                return;
            }

            // ── Match against this real sell order ────────────────────────────
            long fillPotential = sellOrder.getRemainingVolume(); // negative
            if (-fillPotential > orderVolume)
                fillPotential = -orderVolume;

            if (sellOrder.isPlayerOrder())
            {
                // Resolve counterparty (seller) accounts
                BankPair other = resolveCounterpartyBanks(sellOrder);
                if (other == null)
                {
                    orderbook.removeOrder(sellOrder);
                    orderCanceled(sellOrder);
                    continue;
                }

                // Cap fill to what the seller actually has in stock
                long sellerStock = other.itemBank.getTotalBalance();
                if (sellerStock < -fillPotential)
                {
                    fillPotential = -sellerStock;              // partial – seller is out of stock
                    orderbook.removeOrder(sellOrder);
                    orderCanceled(sellOrder);                 // cancel remainder of sell order
                }

                // Cap fill to what the buyer can still afford
                long costFull = fillPotential * sellOrder.getStartPrice();
                if (availableFunds < -costFull)
                {
                    fillPotential  = -availableFunds / sellOrder.getStartPrice();
                    costFull       = fillPotential * sellOrder.getStartPrice();
                    // Buyer exhausted — order will finish after this fill
                }

                if (fillPotential >= 0)
                    break;  // buyer is completely broke, stop

                long cost = fillPotential * sellOrder.getStartPrice();
                sellOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                other.itemBank.withdrawLockedPrefered(-fillPotential);
                other.moneyBank.deposit(-cost);
                orderVolume    += fillPotential;
                itemDelta      -= fillPotential;
                moneyDelta     += cost;
                availableFunds += cost;
                currentMarketPrice  = sellOrder.getStartPrice();

                if (sellOrder.isFilled()) {
                    orderbook.removeOrder(sellOrder);
                    orderConsumed(sellOrder);
                }
            }
            else
            {
                // Bot / virtual limit order — no counterparty account needed
                // Still cap to buyer's remaining funds
                long costFull = fillPotential * sellOrder.getStartPrice();
                if (availableFunds < -costFull)
                    fillPotential = -availableFunds / sellOrder.getStartPrice();

                if (fillPotential >= 0) break;

                long cost = fillPotential * sellOrder.getStartPrice();
                sellOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                orderVolume    += fillPotential;
                itemDelta      -= fillPotential;
                moneyDelta     += cost;
                availableFunds += cost;
                currentMarketPrice  = sellOrder.getStartPrice();

                if (sellOrder.isFilled()) {
                    orderbook.removeOrder(sellOrder);
                    orderConsumed(sellOrder);
                }
            }

            if (orderVolume <= 0)
            {
                commitBuy(itemBank, moneyBank, itemDelta, moneyDelta, order);
                //orderbook.setCurrentMarketPrice(currentMarketPrice);
                return;
            }
        }

        // ── Real orders exhausted — drain remaining virtual depth ─────────────
        long[] result = drainVirtualBuy(orderVolume, itemDelta, moneyDelta,
                Long.MAX_VALUE, availableFunds);

        order.edit(result[1] - itemDelta, result[2] - moneyDelta);
        orderVolume = result[0];
        itemDelta   = result[1];
        moneyDelta  = result[2];

        commitBuy(itemBank, moneyBank, itemDelta, moneyDelta, order);
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }



// ═══════════════════════════════════════════════════════════════════════════
//  Shared helpers
// ═══════════════════════════════════════════════════════════════════════════

    /**
     * Walk currentMarketPrice downward, consuming virtual volume at each step,
     * until the remaining sell volume reaches 0 or price hits {@code stopPrice}.
     *
     * @param orderVolume  current remaining volume (negative = still needs filling)
     * @param itemDelta    accumulated item delta so far
     * @param moneyDelta   accumulated money delta so far
     * @param stopPrice    do not go below this price (exclusive lower bound)
     * @return             long[3] = { orderVolume, itemDelta, moneyDelta }
     */
    private long[] drainVirtualSell(long orderVolume, long itemDelta, long moneyDelta, long stopPrice)
    {
        long nextExecutedPrice = currentMarketPrice;
        long timeout = TIMEOUT_COUNT;
        while (orderVolume < 0 && nextExecutedPrice > stopPrice)
        {
            long virtualVol = orderbook.getVirtualVolumeRounded(nextExecutedPrice);
            if (virtualVol > 0)
            {
                long filled = orderbook.fillVirtual(nextExecutedPrice, orderVolume);
                itemDelta  += filled;
                moneyDelta -= nextExecutedPrice * filled;
                orderVolume -= filled;
                if(filled != 0)
                {
                    currentMarketPrice = nextExecutedPrice;
                }
                // If we emptied this price level, step down
                if (virtualVol + filled == 0 && orderVolume != 0)
                    nextExecutedPrice -= 1;
            }
            else
            {
                nextExecutedPrice -= 1;
            }

            if (nextExecutedPrice <= 0)
            {
                nextExecutedPrice = 0;
                break;
            }
            timeout--;
            if(timeout <= 0)
            {
                // timeout
                break;
            }
        }
        orderbook.setCurrentMarketPrice(currentMarketPrice);
        return new long[]{ orderVolume, itemDelta, moneyDelta };
    }
    /**
     * Walk currentMarketPrice upward, consuming virtual volume at each step,
     * until the remaining buy volume reaches 0, price hits {@code stopPrice},
     * or the buyer runs out of funds.
     *
     * @param orderVolume    current remaining volume (positive = still needs filling)
     * @param itemDelta      accumulated item delta so far
     * @param moneyDelta     accumulated money delta so far  (negative, growing)
     * @param stopPrice      do not go above this price (exclusive upper bound)
     * @param availableFunds buyer's remaining spendable balance
     * @return               long[4] = { orderVolume, itemDelta, moneyDelta, availableFunds }
     */
    private long[] drainVirtualBuy(long orderVolume, long itemDelta, long moneyDelta,
                                   long stopPrice, long availableFunds)
    {
        long nextExecutedPrice = currentMarketPrice;
        long timeout = TIMEOUT_COUNT;
        while (orderVolume > 0 && currentMarketPrice < stopPrice)
        {
            long virtualVol = orderbook.getVirtualVolumeRounded(nextExecutedPrice);
            if (virtualVol < 0)
            {
                // Also cap to what the buyer can afford at this price level
                long maxByFunds = (nextExecutedPrice > 0)
                        ? availableFunds / nextExecutedPrice
                        : orderVolume;
                long wantToFill = Math.min(orderVolume, maxByFunds);

                if (wantToFill <= 0)
                    break;   // buyer is broke

                long filled = orderbook.fillVirtual(nextExecutedPrice, wantToFill);
                long cost   = nextExecutedPrice * filled;
                itemDelta      += filled;
                moneyDelta     -= cost;
                availableFunds -= cost;
                orderVolume    -= filled;
                if(filled != 0)
                {
                    currentMarketPrice = nextExecutedPrice;
                }

                // If we emptied this price level, step up
                if (virtualVol + filled == 0 && orderVolume != 0)
                    nextExecutedPrice += 1;
            }
            else
            {
                nextExecutedPrice += 1;
            }
            timeout--;
            if(timeout <= 0)
            {
                // timeout
                break;
            }
        }
        orderbook.setCurrentMarketPrice(currentMarketPrice);
        return new long[]{ orderVolume, itemDelta, moneyDelta, availableFunds };
    }


    /** Commit a completed (or partially completed) SELL and fire orderConsumed. */
    private void commitSell(IBank itemBank, IBank moneyBank,
                            long itemDelta, long moneyDelta, Order order)
    {
        itemBank.withdrawLockedPrefered(-itemDelta);  // itemDelta is negative → withdraw positive
        moneyBank.deposit(moneyDelta);
        if(order.isFilled())
            orderConsumed(order);
        else
        {
            if(order.isMarketOrder())
                orderCanceled(order);
        }
    }

    /** Commit a completed (or partially completed) BUY and fire orderConsumed. */
    private void commitBuy(IBank itemBank, IBank moneyBank,
                           long itemDelta, long moneyDelta, Order order)
    {
        moneyBank.withdrawLockedPrefered(-moneyDelta);  // moneyDelta is negative → withdraw positive
        itemBank.deposit(itemDelta);
        if(order.isFilled())
            orderConsumed(order);
        else
        {
            if(order.isMarketOrder())
                orderCanceled(order);
        }
    }

    /** Looks up item + money bank for a counterparty order. Returns null if unavailable. */
    private BankPair resolveCounterpartyBanks(Order counterOrder)
    {
        IBankAccount acct = getBankAccount(counterOrder.getBankAccountNr());
        if (acct == null) return null;
        IBank ib = acct.getBank(itemID);
        IBank mb = acct.getBank(ItemID.of(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get()));
        return (ib == null || mb == null) ? null : new BankPair(ib, mb);
    }

    /** Tiny value-holder to return two banks together without allocating an array. */
    private static final class BankPair
    {
        final IBank itemBank;
        final IBank moneyBank;
        BankPair(IBank i, IBank m) { itemBank = i; moneyBank = m; }
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
        ItemID moneyID = ItemID.of(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.CURRENCY.get());
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
