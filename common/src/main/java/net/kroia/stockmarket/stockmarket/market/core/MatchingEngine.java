package net.kroia.stockmarket.stockmarket.market.core;


import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

public class MatchingEngine
{
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Shared types and static depth-consumption methods
    //  Used by both the regular MatchingEngine loop and CrossMarketMatchingEngine.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of consuming depth at a single price level (virtual + real orders).
     * @param volumeConsumed total volume consumed (positive)
     * @param dollarAmount   total dollars exchanged (positive)
     */
    public record DepthConsumeResult(long volumeConsumed, long dollarAmount) {}

    /**
     * Consumes buy-side depth at a specific price level.
     * Fills virtual depth first, then real buy limit orders (FIFO).
     * Handles counterparty bank operations for real orders.
     *
     * @param orderbook  the orderbook to consume from
     * @param itemID     the item being traded (for counterparty bank resolution)
     * @param price      the price level to consume at
     * @param maxVolume  maximum volume to consume (positive)
     * @return consumed volume and dollar amount received from selling into buy-side
     */
    public static DepthConsumeResult consumeBuySideDepthAtPrice(
            Orderbook orderbook, ItemID itemID, long price, long maxVolume)
    {
        long remaining = maxVolume;   // positive, counts down as we sell into buy-side
        long totalConsumed = 0;       // total volume consumed (positive)
        long totalDollars  = 0;       // total dollars received (positive)
        final long SF = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;

        // ── 1. Consume virtual buy-side depth at this price ─────────────────
        long virtualVol = orderbook.getRawVirtualVolumeRounded(price);
        if (virtualVol > 0)
        {
            long toFill = Math.min(remaining, virtualVol);
            // fillVirtual expects negative volume to consume buy-side depth
            long filled = orderbook.fillVirtual(price, -toFill);
            // filled is negative (consumed from buy-side); take absolute value
            long absFilled = Math.abs(filled);
            long cost = Math.round((double) price * absFilled / SF);
            totalConsumed += absFilled;
            totalDollars  += cost;
            remaining     -= absFilled;
        }

        if (remaining <= 0)
            return new DepthConsumeResult(totalConsumed, totalDollars);

        // ── 2. Consume real buy limit orders at this price ──────────────────
        // Snapshot to avoid ConcurrentModificationException
        List<Order> buyOrderSnapshot = new ArrayList<>(orderbook.getBuyLimitOrders());
        List<Order> toRemove = new ArrayList<>();

        for (Order buyOrder : buyOrderSnapshot)
        {
            if (remaining <= 0) break;
            if (buyOrder.getStartPrice() != price) continue;

            long fillPotential = Math.min(buyOrder.getRemainingVolume(), remaining);
            if (fillPotential <= 0) continue;

            if (buyOrder.isPlayerOrder())
            {
                // Resolve counterparty bank accounts
                BankPair other = resolveCounterpartyBanksStatic(buyOrder, itemID);
                if (other == null)
                {
                    toRemove.add(buyOrder);
                    continue;
                }

                // Cap fill to what the buyer can actually afford
                // maxAffordable = buyerFunds * SF / price
                long buyerFunds = other.moneyBank.getTotalBalance();
                long maxAffordable = buyerFunds * SF / price;
                if (maxAffordable < fillPotential)
                {
                    fillPotential = maxAffordable;
                    toRemove.add(buyOrder);  // buyer is broke, cancel remainder
                }

                if (fillPotential <= 0) continue;

                // cost = rawVolume * rawPrice / scaleFactor
                long cost = Math.round((double) fillPotential * price / SF);
                // Update counterparty order: receives items (+fillPotential), pays money (-cost)
                buyOrder.edit(fillPotential, -cost);
                other.itemBank.deposit(fillPotential);
                other.moneyBank.withdrawLockedPrefered(cost);

                totalConsumed += fillPotential;
                totalDollars  += cost;
                remaining     -= fillPotential;

                if (buyOrder.isFilled())
                    toRemove.add(buyOrder);
            }
            else
            {
                // Bot order — no bank operations needed
                long cost = Math.round((double) fillPotential * price / SF);
                buyOrder.edit(fillPotential, -cost);

                totalConsumed += fillPotential;
                totalDollars  += cost;
                remaining     -= fillPotential;

                if (buyOrder.isFilled())
                    toRemove.add(buyOrder);
            }
        }

        // Deferred removal of consumed/canceled orders
        for (Order o : toRemove)
            orderbook.removeOrder(o);

        return new DepthConsumeResult(totalConsumed, totalDollars);
    }

    /**
     * Consumes sell-side depth at a specific price level.
     * Fills virtual depth first, then real sell limit orders (FIFO).
     * Handles counterparty bank operations for real orders.
     *
     * @param orderbook      the orderbook to consume from
     * @param itemID         the item being traded (for counterparty bank resolution)
     * @param price          the price level to consume at
     * @param maxVolume      maximum volume to consume (positive)
     * @param availableFunds maximum dollars available to spend (Long.MAX_VALUE for bots)
     * @return consumed volume and dollar amount spent buying from sell-side
     */
    public static DepthConsumeResult consumeSellSideDepthAtPrice(
            Orderbook orderbook, ItemID itemID, long price, long maxVolume, long availableFunds)
    {
        long remaining = maxVolume;   // positive, counts down as we buy from sell-side
        long totalConsumed = 0;       // total volume bought (positive)
        long totalDollars  = 0;       // total dollars spent (positive)
        final long SF = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;

        // ── 1. Consume virtual sell-side depth at this price ────────────────
        long virtualVol = orderbook.getRawVirtualVolumeRounded(price);
        if (virtualVol < 0)
        {
            // Cap by available funds: maxByFunds = availableFunds * SF / price
            // Guard against overflow for bot orders where availableFunds can be Long.MAX_VALUE
            long maxByFunds;
            if (price <= 0 || availableFunds >= Long.MAX_VALUE / SF)
                maxByFunds = remaining;
            else
                maxByFunds = availableFunds * SF / price;

            long toFill = Math.min(remaining, Math.min(Math.abs(virtualVol), maxByFunds));
            if (toFill > 0)
            {
                // fillVirtual expects positive volume to consume sell-side depth
                long filled = orderbook.fillVirtual(price, toFill);
                // filled is positive (bought from sell-side)
                long absFilled = Math.abs(filled);
                long cost = Math.round((double) price * absFilled / SF);
                totalConsumed  += absFilled;
                totalDollars   += cost;
                remaining      -= absFilled;
                availableFunds -= cost;
            }
        }

        if (remaining <= 0)
            return new DepthConsumeResult(totalConsumed, totalDollars);

        // ── 2. Consume real sell limit orders at this price ─────────────────
        // Snapshot to avoid ConcurrentModificationException
        List<Order> sellOrderSnapshot = new ArrayList<>(orderbook.getSellLimitOrders());
        List<Order> toRemove = new ArrayList<>();

        for (Order sellOrder : sellOrderSnapshot)
        {
            if (remaining <= 0 || availableFunds <= 0) break;
            if (sellOrder.getStartPrice() != price) continue;

            // sellOrder.getRemainingVolume() is negative for sells
            // fillPotential is negative (sell-side convention)
            long fillPotential = -Math.min(-sellOrder.getRemainingVolume(), remaining);
            if (fillPotential >= 0) continue;

            if (sellOrder.isPlayerOrder())
            {
                // Resolve counterparty (seller) bank accounts
                BankPair other = resolveCounterpartyBanksStatic(sellOrder, itemID);
                if (other == null)
                {
                    toRemove.add(sellOrder);
                    continue;
                }

                // Cap fill to what the seller actually has in stock
                long sellerStock = other.itemBank.getTotalBalance();
                if (sellerStock < -fillPotential)
                {
                    fillPotential = -sellerStock;  // partial — seller is out of stock
                    toRemove.add(sellOrder);        // cancel remainder of sell order
                }

                // Cap fill to what the buyer can still afford
                long costFull = Math.round((double) fillPotential * price / SF);
                // costFull is negative (fillPotential is negative)
                if (availableFunds < -costFull)
                {
                    // fillPotential = -funds * SF / price (negative volume for sells)
                    fillPotential = -availableFunds * SF / price;
                }

                if (fillPotential >= 0) continue;

                // cost = rawVolume * rawPrice / scaleFactor (negative because fillPotential < 0)
                long cost = Math.round((double) fillPotential * price / SF);
                // Update counterparty order: loses items (fillPotential, negative), receives money (-cost, positive)
                sellOrder.edit(fillPotential, -cost);
                other.itemBank.withdrawLockedPrefered(-fillPotential);
                other.moneyBank.deposit(-cost);

                // Track consumed volume (positive) and dollars spent (positive)
                long absConsumed = -fillPotential;
                long absCost     = -cost;
                totalConsumed  += absConsumed;
                totalDollars   += absCost;
                remaining      -= absConsumed;
                availableFunds -= absCost;

                if (sellOrder.isFilled())
                    toRemove.add(sellOrder);
            }
            else
            {
                // Bot order — no bank operations needed
                // Still cap to buyer's remaining funds
                long costFull = Math.round((double) fillPotential * price / SF);
                if (availableFunds < -costFull)
                    fillPotential = -availableFunds * SF / price;

                if (fillPotential >= 0) continue;

                long cost = Math.round((double) fillPotential * price / SF);
                sellOrder.edit(fillPotential, -cost);

                long absConsumed = -fillPotential;
                long absCost     = -cost;
                totalConsumed  += absConsumed;
                totalDollars   += absCost;
                remaining      -= absConsumed;
                availableFunds -= absCost;

                if (sellOrder.isFilled())
                    toRemove.add(sellOrder);
            }
        }

        // Deferred removal of consumed/canceled orders
        for (Order o : toRemove)
            orderbook.removeOrder(o);

        return new DepthConsumeResult(totalConsumed, totalDollars);
    }

    /**
     * Returns the total matchable volume at a price level (virtual + real limit orders).
     *
     * @param orderbook the orderbook to query
     * @param price     the price level
     * @param isBuySide true for buy-side depth (positive), false for sell-side (positive result either way)
     * @return total matchable volume at this price (always positive or zero)
     */
    public static long getMatchableVolume(Orderbook orderbook, long price, boolean isBuySide)
    {
        long total = 0;
        long virtualVol = orderbook.getRawVirtualVolumeRounded(price);

        if (isBuySide)
        {
            // Buy-side virtual depth is positive
            if (virtualVol > 0)
                total += virtualVol;

            // Count real buy limit orders at this price
            for (Order order : orderbook.getBuyLimitOrders())
            {
                if (order.getStartPrice() == price)
                    total += order.getRemainingVolume();  // positive for buy orders
            }
        }
        else
        {
            // Sell-side virtual depth is negative; return absolute value
            if (virtualVol < 0)
                total += Math.abs(virtualVol);

            // Count real sell limit orders at this price
            for (Order order : orderbook.getSellLimitOrders())
            {
                if (order.getStartPrice() == price)
                    total += Math.abs(order.getRemainingVolume());  // negative for sell orders, take abs
            }
        }

        return total;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cross-market bilateral matching (static entry point)
    //  Walks both orderbooks simultaneously — have-market buy-side DOWN,
    //  want-market sell-side UP. Uses the shared depth-consumption methods above.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a limit inter-market order using bilateral depth walking.
     *
     * <p>Algorithm overview:</p>
     * <ol>
     *   <li>Start at current market prices for both markets</li>
     *   <li>Check cross rate: if wantPrice * SF / havePrice &gt; limit, stop</li>
     *   <li>Sell have-items into buy-side depth (if money buffer needs funding)</li>
     *   <li>Buy want-items from sell-side depth (using money buffer)</li>
     *   <li>Advance prices when depth exhausted at current level</li>
     *   <li>Update both market prices atomically at end</li>
     *   <li>Persist state on order (transactionMoneyBalance, remaining volumes)</li>
     * </ol>
     *
     * <p>Money flows through {@link InterMarketOrder#getTransactionMoneyBalance()} as a
     * dollar buffer — sells deposit here, buys withdraw. No temporary Order objects are
     * created. On completion/cancellation, the remaining buffer is deposited to the
     * player's money bank by ServerMarketManager.</p>
     *
     * <h2>Stop Conditions</h2>
     * <ol>
     *   <li>Cross rate at current price pair exceeds crossMarketLimitPrice</li>
     *   <li>Want-item target volume reached (order filled)</li>
     *   <li>Have-item locked volume exhausted (sell budget spent)</li>
     *   <li>No depth on either side</li>
     * </ol>
     *
     * @param order             the inter-market limit order to execute
     * @param haveMarket        market for the "sell" item (sell into buy-side depth, price walks DOWN)
     * @param wantMarket        market for the "buy" item (buy from sell-side depth, price walks UP)
     * @param playerBankAccount player's bank account (null for bot orders)
     * @param tradingCurrencyID the money item ID used as intermediary
     * @return FILLED if target volume reached, PARTIAL_FILL if some progress made,
     *         SKIPPED if cross rate is unfavorable at current prices
     */
    public static InterMarketExecutor.ExecutionResult executeCrossMarketLimitOrder(
            InterMarketOrder order,
            ServerMarket haveMarket,
            ServerMarket wantMarket,
            @Nullable IServerBankAccount playerBankAccount,
            ItemID tradingCurrencyID)
    {
        final long SF = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;

        // Sample starting prices: sell into buy-side (price walks DOWN), buy from sell-side (price walks UP)
        long haveSamplePrice = haveMarket.getCurrentMarketPrice();
        long wantSamplePrice  = wantMarket.getCurrentMarketPrice();

        // Read order state (persisted across ticks for partial fills)
        long wantTargetVolume      = order.getBuyOrder().getRemainingVolume();            // positive, items still to buy
        long haveLockedVolume     = Math.abs(order.getSellOrder().getRemainingVolume()); // positive, items still available to sell
        long crossMarketLimitPrice = order.getCrossRateLimit();
        long transactionMoneyBalance = order.getTransactionMoneyBalance();

        // Track whether any progress was made this tick
        boolean madeProgress = false;
        long lastHaveFillPrice = haveSamplePrice;
        long lastWantFillPrice = wantSamplePrice;

        // Main loop with timeout protection
        int timeout = 10000;
        while (timeout-- > 0)
        {
            // 1. Rate check
            if (haveSamplePrice <= 0)
                break;
            long crossMarketPrice = wantSamplePrice * SF / haveSamplePrice;
            if (crossMarketPrice > crossMarketLimitPrice)
                break;

            // 2. Stop conditions
            if (wantTargetVolume <= 0)
                break;
            //if (haveLockedVolume <= 0 && transactionMoneyBalance <= 0)
            //    break;

            // 3. Read depth at current prices
            long haveVolume = getMatchableVolume(haveMarket.getOrderbook(), haveSamplePrice, true);
            long wantVolume  = getMatchableVolume(wantMarket.getOrderbook(), wantSamplePrice, false);

            if (wantVolume <= 0)
            {
                // No depth at current prices — advance and retry
                wantSamplePrice += 1;
                continue;
            }
            if(haveVolume <= 0)
            {
                // No depth at current prices — advance and retry
                haveSamplePrice -= 1;
                if (haveSamplePrice <= 0)
                    break;
                continue;
            }

            // 4. Sell have-items if needed to fund want-item purchase
            long canAffordWant = (wantSamplePrice > 0) ? transactionMoneyBalance * SF / wantSamplePrice : 0;

            if (canAffordWant < wantVolume && canAffordWant < wantTargetVolume)
            {
                long wantWeNeed = Math.min(wantVolume, wantTargetVolume);
                long dollarsNeeded = wantWeNeed * wantSamplePrice - transactionMoneyBalance;
                if (dollarsNeeded < 0) dollarsNeeded = 0;

                long haveToSell = (haveSamplePrice > 0) ? dollarsNeeded / haveSamplePrice : 0;
                if (haveSamplePrice > 0 && haveToSell * haveSamplePrice / SF < dollarsNeeded)
                    haveToSell += 1;  // round up

                haveToSell = Math.min(haveToSell, haveVolume);

                if (haveToSell > 0)
                {
                    // Withdraw from player's locked item balance
                    if (!order.isBotOrder() && playerBankAccount != null)
                    {
                        IServerBank haveItemBank = playerBankAccount.getBank(order.getSellItemID());
                        if (haveItemBank == null || haveItemBank.withdrawLockedPrefered(haveToSell) != BankStatus.SUCCESS)
                            break;
                    }

                    DepthConsumeResult consumed = consumeBuySideDepthAtPrice(
                            haveMarket.getOrderbook(), haveMarket.getItemID(), haveSamplePrice, haveToSell);

                    haveLockedVolume -= consumed.volumeConsumed();
                    transactionMoneyBalance += consumed.dollarAmount();
                    haveVolume -= consumed.volumeConsumed();

                    if (consumed.volumeConsumed() > 0)
                    {
                        order.getSellOrder().edit(-consumed.volumeConsumed(), consumed.dollarAmount());
                        madeProgress = true;
                        lastHaveFillPrice = haveSamplePrice;
                    }
                }
            }

            // 5. Buy want-items with available balance
            long wantToBuy = Math.min(wantVolume, wantTargetVolume);
            if (wantSamplePrice > 0)
            {
                long canBuy = transactionMoneyBalance * SF / wantSamplePrice;
                wantToBuy = Math.min(wantToBuy, canBuy);
            }

            if (wantToBuy > 0)
            {
                DepthConsumeResult consumed = consumeSellSideDepthAtPrice(
                        wantMarket.getOrderbook(), wantMarket.getItemID(), wantSamplePrice, wantToBuy, transactionMoneyBalance);

                transactionMoneyBalance -= consumed.dollarAmount();
                wantTargetVolume -= consumed.volumeConsumed();
                wantVolume -= consumed.volumeConsumed();

                if (!order.isBotOrder() && playerBankAccount != null && consumed.volumeConsumed() > 0)
                {
                    IServerBank wantItemBank = playerBankAccount.getBank(order.getBuyItemID());
                    if (wantItemBank != null)
                        wantItemBank.deposit(consumed.volumeConsumed());
                }

                if (consumed.volumeConsumed() > 0)
                {
                    order.getBuyOrder().edit(consumed.volumeConsumed(), -consumed.dollarAmount());
                    madeProgress = true;
                    lastWantFillPrice = wantSamplePrice;
                }
            }

            // 6. Advance prices when depth exhausted
            if (haveVolume <= 0) haveSamplePrice -= 1;
            if (wantVolume <= 0) wantSamplePrice += 1;
            if (haveSamplePrice <= 0)
                break;
        }

        // 7. Update reported market prices only if fills occurred, using the last actual fill prices.
        //    Uses setCurrentMarketPriceNoRedistribute to avoid shifting the VirtualOrderbook's
        //    DynamicIndexedArray window — the bilateral walk already consumed depth directly
        //    via fillVirtual, and redistributing would regenerate default volumes at new price
        //    levels, draining depth that wasn't actually traded.
        if (madeProgress)
        {
            haveMarket.setCurrentMarketPriceNoRedistribute(lastHaveFillPrice);
            wantMarket.setCurrentMarketPriceNoRedistribute(lastWantFillPrice);
        }

        // 8. Persist state on order
        order.setTransactionMoneyBalance(transactionMoneyBalance);

        // 9. Determine result
        if (wantTargetVolume <= 0 || order.isFilled())
            return InterMarketExecutor.ExecutionResult.FILLED;
        else if (madeProgress)
            return InterMarketExecutor.ExecutionResult.PARTIAL_FILL;
        else
            return InterMarketExecutor.ExecutionResult.SKIPPED;
    }

    /** Tiny value-holder to return two banks together. */
    private static final class BankPair
    {
        final IServerBank itemBank;
        final IServerBank moneyBank;
        BankPair(IServerBank i, IServerBank m) { itemBank = i; moneyBank = m; }
    }

    private final ItemID itemID;
    private final Orderbook orderbook;
    private static final int TIMEOUT_COUNT = 10000;

    private final PriorityQueue<Order> buyMarketOrders_inputBuffer;
    private final PriorityQueue<Order> sellMarketOrders_inputBuffer;
    private final PriorityQueue<Order> buyLimitOrders_inputBuffer;
    private final PriorityQueue<Order> sellLimitOrders_inputBuffer;

    private final @NotNull Consumer<Order> consumedOrderCallback;
    private final @NotNull Consumer<Order> cancelOrderCallback;

    private final @NotNull Consumer<Long> priceChanged;


    private final Orderbook.LongPair pair_cache =  new Orderbook.LongPair();
    private long currentMarketPrice;
    private float tradedVolumeSinceLastReset = 0;


    public MatchingEngine(ItemID itemID, Orderbook orderbook,
                          PriorityQueue<Order> buyMarketOrders_inputBuffer,
                          PriorityQueue<Order> sellMarketOrders_inputBuffer,
                          PriorityQueue<Order> buyLimitOrders_inputBuffer,
                          PriorityQueue<Order> sellLimitOrders_inputBuffer,
                          @NotNull Consumer<Order> consumedOrderCallback,
                          @NotNull Consumer<Order> cancelOrderCallback,
                          @NotNull Consumer<Long> priceChanged)
    {
        this.itemID = itemID;
        this.orderbook = orderbook;

        this.buyMarketOrders_inputBuffer = buyMarketOrders_inputBuffer;
        this.sellMarketOrders_inputBuffer = sellMarketOrders_inputBuffer;
        this.buyLimitOrders_inputBuffer = buyLimitOrders_inputBuffer;
        this.sellLimitOrders_inputBuffer = sellLimitOrders_inputBuffer;

        this.consumedOrderCallback = consumedOrderCallback;
        this.cancelOrderCallback = cancelOrderCallback;

        this.priceChanged = priceChanged;
    }



    // Total matched trade volume accumulated during the last update() call (real-scaled)
    public float getLastTradedVolume() { return tradedVolumeSinceLastReset; }

    public void update(long currentMarketPrice)
    {
        this.currentMarketPrice = currentMarketPrice;
        tradedVolumeSinceLastReset = 0;

        if(!buyLimitOrders_inputBuffer.isEmpty())
        {
            for(Order order : buyLimitOrders_inputBuffer)
            {
                processLimitOrder(order);
            }
            buyLimitOrders_inputBuffer.clear();
        }
        if(!sellLimitOrders_inputBuffer.isEmpty())
        {
            for(Order order : sellLimitOrders_inputBuffer)
            {
                processLimitOrder(order);
            }
            sellLimitOrders_inputBuffer.clear();
        }
        if(!sellMarketOrders_inputBuffer.isEmpty())
        {
            for(Order order : sellMarketOrders_inputBuffer)
            {
                // INTER_MARKET orders with startPrice > 0 use it as a price floor
                // (set by InterMarketExecutor to enforce cross-rate limits). startPrice = 0 means no floor.
                long minPrice = (order.getType() == Order.Type.INTER_MARKET && order.getStartPrice() > 0)
                        ? order.getStartPrice() : 0;
                processMarketOrder(order, minPrice, Long.MAX_VALUE);
            }
            sellMarketOrders_inputBuffer.clear();
        }
        if(!buyMarketOrders_inputBuffer.isEmpty())
        {
            for(Order order : buyMarketOrders_inputBuffer)
            {
                // INTER_MARKET orders with startPrice > 0 use it as a price cap
                // (set by InterMarketExecutor to enforce cross-rate limits). startPrice = 0 means no cap.
                long maxPrice = (order.getType() == Order.Type.INTER_MARKET && order.getStartPrice() > 0)
                        ? order.getStartPrice() : Long.MAX_VALUE;
                processMarketOrder(order, 0, maxPrice);
            }
            buyMarketOrders_inputBuffer.clear();
        }

        priceChanged.accept(this.currentMarketPrice);
    }

    private void processLimitOrder(Order order)
    {
        if(order.isBuyOrder())
        {
            if(order.getStartPrice() < currentMarketPrice)
            {
                orderbook.putOrder(order);
                return;
            }

        }
        else
        {
            if(order.getStartPrice() > currentMarketPrice)
            {
                orderbook.putOrder(order);
                return;
            }
        }
        processMarketOrder(order, order.getStartPrice(), order.getStartPrice());
        if(!order.isFilled())
        {
            orderbook.putOrder(order);
        }
    }

    /**
     * Executes a stockmarket order against the order book.
     *
     * Sign convention (preserved from original):
     *   order.getRemainingVolume() < 0  →  SELL  (player gives items, receives money)
     *   order.getRemainingVolume() > 0  →  BUY   (player gives money, receives items)
     *
     * currentMarketPrice is mutable class-level state; it walks down on SELL, up on BUY.
     *
     */
    private void processMarketOrder(Order order, long minAcceptedPrice, long maxAcceptedPrice)
    {
        // ── Resolve the submitting player's bank accounts ──────────────────────
        IServerBankAccount account = getBankAccount(order.getBankAccountNr());
        long orderVolume = order.getRemainingVolume();
        if (account == null)
        {
            if (orderVolume < 0)
                processBotSell(order, orderVolume, minAcceptedPrice);
            else
                processBotBuy(order, orderVolume, maxAcceptedPrice);
            return;
        }

        IServerBank itemBank  = account.getBank(itemID);
        IServerBank moneyBank = account.getBank(BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID());
        if (itemBank == null || moneyBank == null) { orderCanceled(order); return; }



        // ── Route by direction ─────────────────────────────────────────────────
        if (orderVolume < 0)
            processSell(order, orderVolume, itemBank, moneyBank, minAcceptedPrice);
        else
            processBuy(order, orderVolume, itemBank, moneyBank, maxAcceptedPrice);
    }

// ═══════════════════════════════════════════════════════════════════════════
//  SELL
// ═══════════════════════════════════════════════════════════════════════════

    private void processSell(Order order, long orderVolume, @Nullable IServerBank itemBank, @Nullable IServerBank moneyBank, long minimalAcceptedPrice)
    {
        if(itemBank != null) {
            // Cap sell volume to what the player actually holds
            long available = itemBank.getTotalBalance();
            if (available < -orderVolume)
                orderVolume = -available;                         // still negative
        }

        // Quick check: is there enough depth to fill at all?
        //if (!orderbook.getPriceWhenConsumingVolume(orderVolume, pair_cache)) return;

        long itemDelta  = 0;   // items leaving seller's account  (will be negative)
        long moneyDelta = 0;   // money entering seller's account (will be positive)

        // ── Iterate real BUY order in the matchable price range ──────────────
        PriorityQueue<Order> buyOrders = orderbook.getBuyLimitOrders();
        // Copy to a temporary list to avoid ConcurrentModificationException
        // (the loop body calls orderbook.removeOrder which modifies the PriorityQueue)
        List<Order> buyOrderSnapshot = new ArrayList<>(buyOrders);
        List<Order> buyOrdersToRemove = new ArrayList<>();
        boolean sellEarlyReturn = false;

        for (Order buyOrder : buyOrderSnapshot)
        {
            // Fill any virtual volume that sits above the buy order's price
            long[] result = drainVirtualSell(orderVolume, itemDelta, moneyDelta, Math.max(minimalAcceptedPrice, buyOrder.getStartPrice()));

            order.edit(result[1] - itemDelta, result[2] - moneyDelta);

            orderVolume = result[0];
            itemDelta   = result[1];
            moneyDelta  = result[2];

            if (orderVolume >= 0 || (currentMarketPrice <= minimalAcceptedPrice && buyOrder.getStartPrice() != minimalAcceptedPrice))
            {
                sellEarlyReturn = true;
                break;
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
                    buyOrdersToRemove.add(buyOrder);
                    orderCanceled(buyOrder);
                    continue;
                }

                // Cap fill to what the buyer can actually afford
                // maxAffordable = buyerFunds * SF / price (since cost = vol * price / SF)
                long buyerFunds = other.moneyBank.getTotalBalance();
                long maxAffordable = buyerFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / buyOrder.getStartPrice();
                if (maxAffordable < fillPotential)
                {
                    fillPotential = maxAffordable;    // partial – buyer is broke
                    buyOrdersToRemove.add(buyOrder);
                    orderCanceled(buyOrder);          // cancel remainder of buy order
                }

                // cost = rawVolume * rawPrice / scaleFactor (both inputs are raw-scaled)
                long cost = Math.round((double)fillPotential * buyOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                buyOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                other.itemBank.deposit(fillPotential);
                other.moneyBank.withdrawLockedPrefered(cost);
                orderVolume += fillPotential;
                itemDelta   -= fillPotential;
                moneyDelta  += cost;
                currentMarketPrice  = buyOrder.getStartPrice();
                tradedVolumeSinceLastReset += Math.abs(fillPotential);

                if (buyOrder.isFilled()) {
                    buyOrdersToRemove.add(buyOrder);
                    orderConsumed(buyOrder);
                }
            }
            else
            {
                // Bot / virtual limit order — no counterparty account needed
                // cost = rawVolume * rawPrice / scaleFactor
                long cost = Math.round((double)fillPotential * buyOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                buyOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                orderVolume += fillPotential;
                itemDelta   -= fillPotential;
                moneyDelta  += cost;
                currentMarketPrice  = buyOrder.getStartPrice();
                tradedVolumeSinceLastReset += Math.abs(fillPotential);
                if (buyOrder.isFilled()) {
                    buyOrdersToRemove.add(buyOrder);
                    orderConsumed(buyOrder);
                }
            }

            if (orderVolume >= 0)
            {
                sellEarlyReturn = true;
                break;
            }
        }

        // Deferred removal: remove matched/consumed/canceled orders from the actual PriorityQueue
        for (Order toRemove : buyOrdersToRemove)
            orderbook.removeOrder(toRemove);

        if (sellEarlyReturn)
        {
            commitSell(itemBank, moneyBank, itemDelta, moneyDelta, order);
            return;
        }

        // ── Real order exhausted — drain remaining virtual depth ─────────────
        long[] result = drainVirtualSell(orderVolume, itemDelta, moneyDelta, minimalAcceptedPrice);

        order.edit(result[1] - itemDelta, result[2] - moneyDelta);
        orderVolume = result[0];
        itemDelta   = result[1];
        moneyDelta  = result[2];


        commitSell(itemBank, moneyBank, itemDelta, moneyDelta, order);
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }
    private void processBotSell(Order order, long orderVolume, long minimalAcceptedPrice)
    {
        processSell(order, orderVolume, null, null, minimalAcceptedPrice);
    }


// ═══════════════════════════════════════════════════════════════════════════
//  BUY
// ═══════════════════════════════════════════════════════════════════════════

    private void processBuy(Order order, long orderVolume, @Nullable IServerBank itemBank, @Nullable IServerBank moneyBank, long maximalAcceptedPrice)
    {
        long availableFunds = Long.MAX_VALUE;
        if(moneyBank != null) {
            // Cap buy volume to what the player can afford at the current stockmarket price.
            // This is a conservative upper bound; actual cost may be lower if fills happen
            // at cheaper ask prices. Any overshoot is harmless — the loop stops when
            // orderVolume reaches 0.
            availableFunds = moneyBank.getTotalBalance();
            // maxAffordable = funds * SF / price (since cost = vol * price / SF)
            long maxAffordable = (currentMarketPrice > 0) ? availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / currentMarketPrice : orderVolume;
            if (maxAffordable < orderVolume)
                orderVolume = maxAffordable;                      // still positive
        }

        // Quick check: is there enough depth to fill at all?
        //if (!orderbook.getPriceWhenConsumingVolume(orderVolume, pair_cache)) return;

        long itemDelta  = 0;   // items entering buyer's account  (will be positive)
        long moneyDelta = 0;   // money leaving  buyer's account  (will be negative)

        // ── Iterate real SELL order in the matchable price range ─────────────
        PriorityQueue<Order> sellOrders = orderbook.getSellLimitOrders();
        // Copy to a temporary list to avoid ConcurrentModificationException
        // (the loop body calls orderbook.removeOrder which modifies the PriorityQueue)
        List<Order> sellOrderSnapshot = new ArrayList<>(sellOrders);
        List<Order> sellOrdersToRemove = new ArrayList<>();
        boolean buyEarlyReturn = false;

        for (Order sellOrder : sellOrderSnapshot)
        {
            // Fill any virtual volume that sits below the sell order's price
            long[] result = drainVirtualBuy(orderVolume, itemDelta, moneyDelta,
                    Math.min(maximalAcceptedPrice, sellOrder.getStartPrice()), availableFunds);

            order.edit(result[1] - itemDelta, result[2] - moneyDelta);

            orderVolume    = result[0];
            itemDelta      = result[1];
            moneyDelta     = result[2];
            availableFunds = result[3];



            if (orderVolume <= 0 || (currentMarketPrice >= maximalAcceptedPrice && sellOrder.getStartPrice() != maximalAcceptedPrice))
            {
                buyEarlyReturn = true;
                break;
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
                    sellOrdersToRemove.add(sellOrder);
                    orderCanceled(sellOrder);
                    continue;
                }

                // Cap fill to what the seller actually has in stock
                long sellerStock = other.itemBank.getTotalBalance();
                if (sellerStock < -fillPotential)
                {
                    fillPotential = -sellerStock;              // partial – seller is out of stock
                    sellOrdersToRemove.add(sellOrder);
                    orderCanceled(sellOrder);                 // cancel remainder of sell order
                }

                // Cap fill to what the buyer can still afford
                // costFull = rawVolume * rawPrice / scaleFactor (fillPotential is negative for sells)
                long costFull = Math.round((double)fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                if (availableFunds < -costFull)
                {
                    // fillPotential = -funds * SF / price (negative volume for sells)
                    fillPotential  = -availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / sellOrder.getStartPrice();
                    // Buyer exhausted — order will finish after this fill
                }

                if (fillPotential >= 0)
                    break;  // buyer is completely broke, stop

                // cost = rawVolume * rawPrice / scaleFactor
                long cost = Math.round((double)fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                sellOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                other.itemBank.withdrawLockedPrefered(-fillPotential);
                other.moneyBank.deposit(-cost);
                orderVolume    += fillPotential;
                itemDelta      -= fillPotential;
                moneyDelta     += cost;
                availableFunds += cost;
                currentMarketPrice  = sellOrder.getStartPrice();
                tradedVolumeSinceLastReset += Math.abs(fillPotential);

                if (sellOrder.isFilled()) {
                    sellOrdersToRemove.add(sellOrder);
                    orderConsumed(sellOrder);
                }
            }
            else
            {
                // Bot / virtual limit order — no counterparty account needed
                // Still cap to buyer's remaining funds
                // costFull = rawVolume * rawPrice / scaleFactor (fillPotential is negative for sells)
                long costFull = Math.round((double)fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                if (availableFunds < -costFull)
                    fillPotential = -availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / sellOrder.getStartPrice();

                if (fillPotential >= 0) break;

                // cost = rawVolume * rawPrice / scaleFactor
                long cost = Math.round((double)fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                sellOrder.edit(fillPotential, -cost);
                order.edit(-fillPotential, cost);
                orderVolume    += fillPotential;
                itemDelta      -= fillPotential;
                moneyDelta     += cost;
                availableFunds += cost;
                currentMarketPrice  = sellOrder.getStartPrice();
                tradedVolumeSinceLastReset += Math.abs(fillPotential);

                if (sellOrder.isFilled()) {
                    sellOrdersToRemove.add(sellOrder);
                    orderConsumed(sellOrder);
                }
            }

            if (orderVolume <= 0)
            {
                buyEarlyReturn = true;
                break;
            }
        }

        // Deferred removal: remove matched/consumed/canceled orders from the actual PriorityQueue
        for (Order toRemove : sellOrdersToRemove)
            orderbook.removeOrder(toRemove);

        if (buyEarlyReturn)
        {
            commitBuy(itemBank, moneyBank, itemDelta, moneyDelta, order);
            return;
        }

        // ── Real order exhausted — drain remaining virtual depth ─────────────
        long[] result = drainVirtualBuy(orderVolume, itemDelta, moneyDelta,
                maximalAcceptedPrice, availableFunds);

        order.edit(result[1] - itemDelta, result[2] - moneyDelta);
        orderVolume = result[0];
        itemDelta   = result[1];
        moneyDelta  = result[2];

        commitBuy(itemBank, moneyBank, itemDelta, moneyDelta, order);
        orderbook.setCurrentMarketPrice(currentMarketPrice);
    }
    private void processBotBuy(Order order, long orderVolume, long maximalAcceptedPrice)
    {
        processBuy(order, orderVolume, null, null, maximalAcceptedPrice);
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
        while (orderVolume < 0 && nextExecutedPrice >= stopPrice)
        {
            long virtualVol = orderbook.getRawVirtualVolumeRounded(nextExecutedPrice);
            if (virtualVol > 0)
            {
                long filled = orderbook.fillVirtual(nextExecutedPrice, orderVolume);
                long virtualCost = Math.round((double)nextExecutedPrice * filled / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                itemDelta  += filled;
                moneyDelta -= virtualCost;
                orderVolume -= filled;
                tradedVolumeSinceLastReset += Math.abs(filled);
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
        while (orderVolume > 0 && nextExecutedPrice <= stopPrice)
        {
            long virtualVol = orderbook.getRawVirtualVolumeRounded(nextExecutedPrice);
            if (virtualVol < 0)
            {
                // Also cap to what the buyer can afford at this price level
                // maxByFunds = funds * SF / price (since cost = vol * price / SF)
                // Guard against overflow for bot orders where availableFunds can be Long.MAX_VALUE
                long maxByFunds;
                if (nextExecutedPrice <= 0 || availableFunds >= Long.MAX_VALUE / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR) {
                    maxByFunds = orderVolume;
                } else {
                    maxByFunds = availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / nextExecutedPrice;
                }
                long wantToFill = Math.min(orderVolume, maxByFunds);

                if (wantToFill <= 0)
                    break;   // buyer is broke

                long filled = orderbook.fillVirtual(nextExecutedPrice, wantToFill);
                // cost = rawPrice * rawVolume / scaleFactor
                long cost = Math.round((double)nextExecutedPrice * filled / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                itemDelta      += filled;
                moneyDelta     -= cost;
                availableFunds -= cost;
                orderVolume    -= filled;
                tradedVolumeSinceLastReset += Math.abs(filled);
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
    private void commitSell(@Nullable IServerBank itemBank, @Nullable IServerBank moneyBank,
                            long itemDelta, long moneyDelta, Order order)
    {
        if(itemBank != null)
            itemBank.withdrawLockedPrefered(-itemDelta);  // itemDelta is negative → withdraw positive
        if(moneyBank != null)
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
    private void commitBuy(@Nullable IServerBank itemBank, @Nullable IServerBank moneyBank,
                           long itemDelta, long moneyDelta, Order order)
    {
        if(moneyBank != null)
            moneyBank.withdrawLockedPrefered(-moneyDelta);  // moneyDelta is negative → withdraw positive
        if(itemBank != null)
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
        @Nullable IServerBankAccount acct = getBankAccount(counterOrder.getBankAccountNr());
        if (acct == null)
            return null;
        IServerBank ib = acct.getBank(itemID);
        IServerBank mb = acct.getBank(BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID());
        return (ib == null || mb == null) ? null : new BankPair(ib, mb);
    }

    /** Static overload for use by the shared depth-consumption methods. Takes itemID as a parameter. */
    private static BankPair resolveCounterpartyBanksStatic(Order counterOrder, ItemID itemID)
    {
        if (BACKEND_INSTANCES == null) return null;
        IServerBankAccount acct = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync()
                .getBankAccount(counterOrder.getBankAccountNr());
        if (acct == null) return null;
        IServerBank ib = acct.getBank(itemID);
        IServerBank mb = acct.getBank(BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID());
        return (ib == null || mb == null) ? null : new BankPair(ib, mb);
    }

















    @Nullable IServerBankAccount getBankAccount(int bankAccountID)
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(bankAccountID);
    }
    @Nullable IServerBank getItemBank(int bankAccountID)
    {
        IServerBankAccount account = getBankAccount(bankAccountID);
        if(account != null)
        {
            IServerBank bank = account.getBank(itemID);
            if(bank == null)
            {
                error("No bank for item: "+itemID+ " in bank acocunt: "+bankAccountID);
            }
            return bank;
        }
        error("No bank account with the accountNR: "+bankAccountID);
        return null;
    }
    @Nullable IServerBank getMoneyBank(int bankAccountID)
    {
        IServerBankAccount account = getBankAccount(bankAccountID);
        ItemID moneyID = BACKEND_INSTANCES.MARKET_MANAGER.getSync().getTradingCurrencyID();
        if(account != null && moneyID != null)
        {
            IServerBank bank = account.getBank(moneyID);
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
    private void orderCanceled(Order order)
    {
        cancelOrderCallback.accept(order);
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
