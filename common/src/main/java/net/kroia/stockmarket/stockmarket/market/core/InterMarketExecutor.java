package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Central coordinator for inter-market (item-to-item) trade execution.
 *
 * <p>An inter-market order swaps items from one market ("have" / sell-leg) into
 * items on another market ("want" / buy-leg), using the trading currency as an
 * intermediary. The executor does NOT own the orders — it receives them from
 * ServerMarketManager (wired in IMT-08).</p>
 *
 * <h2>Two-Phase Algorithm</h2>
 * <ol>
 *   <li><b>Simulate (read-only)</b> — Use {@link DepthSimulation} to walk both
 *       orderbooks without mutating any state. This determines the exact sell
 *       volume and buy volume before committing either leg.</li>
 *   <li><b>Execute</b> — Create regular {@link Order} objects and route them
 *       through {@link ServerMarket#putOrder(Order)} + {@link ServerMarket#update()},
 *       so the MatchingEngine handles all depth-walking, bank transfers, and
 *       state updates.</li>
 * </ol>
 *
 * <h2>Limit Orders</h2>
 * <p>Limit inter-market orders include a cross-rate limit: the maximum ratio of
 * sell-items per buy-item the trader will accept. If the current market conditions
 * are unfavorable, the order is skipped (SKIPPED). If a full fill would exceed
 * the rate limit, a binary search finds the maximum partial fill volume that
 * stays within the limit.</p>
 *
 * <h2>Money Flow</h2>
 * <p>Money flows through the player's bank account as an intermediary. The sell
 * leg deposits dollars into the money bank (via MatchingEngine), and the buy leg
 * withdraws dollars from it. Any rounding dust left over is deposited to the
 * player's money bank.</p>
 */
public class InterMarketExecutor
{
    private static final String LOG_TAG = "[InterMarketExecutor]: ";

    // ═══════════════════════════════════════════════════════════════════════════
    //  Execution result enum
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of executing a single inter-market order.
     */
    public enum ExecutionResult
    {
        /** Fully executed — both legs completed, order is done */
        FILLED,
        /** Partially filled — limit order, favorable portion executed, remainder stays pending */
        PARTIAL_FILL,
        /** Skipped — limit order, current rate is unfavorable, order stays pending for retry */
        SKIPPED,
        /** Canceled — cannot execute (market closed, no funds, zero depth, etc.) */
        CANCELED,
        /** Unexpected error during execution */
        ERROR
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Single order execution
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a single inter-market order against two markets.
     *
     * <p>The sell-leg market is where the "have" item is sold, and the buy-leg market
     * is where the "want" item is bought. The trading currency flows through the
     * player's bank account as an intermediary.</p>
     *
     * @param order              the inter-market order to execute
     * @param haveMarket         market for the "sell" item (sellOrder's itemID)
     * @param wantMarket         market for the "buy" item (buyOrder's itemID)
     * @param playerBankAccount  player's bank account (for money intermediary), null for bot orders
     * @param tradingCurrencyID  the money item ID used as intermediary
     * @return the execution result
     */
    public static ExecutionResult executeOrder(
            InterMarketOrder order,
            ServerMarket haveMarket,
            ServerMarket wantMarket,
            @Nullable IServerBankAccount playerBankAccount,
            ItemID tradingCurrencyID)
    {
        try
        {
            // ── Pre-flight checks ─────────────────────────────────────────────
            if ((!haveMarket.isMarketOpen() || !wantMarket.isMarketOpen()) && !order.isBotOrder())
            {
                logWarn("One or both markets are closed, canceling order");
                return ExecutionResult.CANCELED;
            }

            // For player orders, verify the money bank exists
            if (!order.isBotOrder() && playerBankAccount != null)
            {
                IServerBank moneyBank = playerBankAccount.getBank(tradingCurrencyID);
                if (moneyBank == null)
                {
                    logWarn("Player has no money bank for currency " + tradingCurrencyID + ", canceling order");
                    return ExecutionResult.CANCELED;
                }
            }

            // ── Route to market-order or limit-order execution ────────────────
            if (order.isMarketOrder())
            {
                return executeMarketOrder(order, haveMarket, wantMarket, playerBankAccount, tradingCurrencyID);
            }
            else
            {
                return executeLimitOrder(order, haveMarket, wantMarket, playerBankAccount, tradingCurrencyID);
            }
        }
        catch (Exception e)
        {
            logError("Unexpected error executing inter-market order", e);
            return ExecutionResult.ERROR;
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Market order execution
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a market-type inter-market order using the two-phase approach:
     * simulate both legs, then execute both legs.
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Forward simulation: sell items into haveMarket, get dollarYield</li>
     *   <li>Buy simulation: buy items from wantMarket using dollarYield</li>
     *   <li>Back-calculation: determine the exact sell volume needed to produce
     *       the dollars that the buy leg will actually spend</li>
     *   <li>Execute both legs via putOrder + update</li>
     *   <li>Deposit any rounding dust to player's money bank</li>
     * </ol>
     */
    private static ExecutionResult executeMarketOrder(
            InterMarketOrder order,
            ServerMarket haveMarket,
            ServerMarket wantMarket,
            @Nullable IServerBankAccount playerBankAccount,
            ItemID tradingCurrencyID)
    {
        long SF = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;

        // The remaining sell volume (positive: items still to sell, accounts for partial fills)
        long maxSellVolume = Math.abs(order.getSellOrder().getRemainingVolume());
        if (maxSellVolume <= 0)
        {
            logWarn("Inter-market order has zero or negative sell volume, canceling");
            return ExecutionResult.CANCELED;
        }

        Orderbook haveOrderbook = haveMarket.getOrderbook();
        Orderbook wantOrderbook = wantMarket.getOrderbook();
        long havePrice = haveMarket.getCurrentMarketPrice();
        long wantPrice = wantMarket.getCurrentMarketPrice();

        // ═══ PHASE 1: SIMULATE ═══

        // Step A — Forward simulation: sell maxSellVolume items into haveMarket
        DepthSimulation.SimResult simSell = DepthSimulation.simulateSell(
                haveOrderbook, maxSellVolume, havePrice);

        if (simSell.volumeFilled() <= 0 || simSell.dollarAmount() <= 0)
        {
            logWarn("Sell simulation yielded 0 volume or 0 dollars, canceling order");
            return ExecutionResult.CANCELED;
        }

        // Step B — Buy simulation: use the dollar yield to buy from wantMarket
        DepthSimulation.SimResult simBuy = DepthSimulation.simulateBuy(
                wantOrderbook, simSell.dollarAmount(), wantPrice);

        if (simBuy.volumeFilled() <= 0 || simBuy.dollarAmount() <= 0)
        {
            logWarn("Buy simulation yielded 0 items or spent 0 dollars, canceling order");
            return ExecutionResult.CANCELED;
        }

        // Step C — Back-calculate: find the exact sell volume that produces exactly
        // what the buy leg will spend, minimizing leftover dollars
        DepthSimulation.SimResult simSellExact = DepthSimulation.simulateSellForDollars(
                haveOrderbook, simBuy.dollarAmount(), havePrice);

        if (simSellExact.volumeFilled() <= 0)
        {
            logWarn("Back-calculation yielded 0 sell volume, canceling order");
            return ExecutionResult.CANCELED;
        }

        long exactSellVolume = simSellExact.volumeFilled();
        long exactDollarYield = simSellExact.dollarAmount();
        long dollarsSpent = simBuy.dollarAmount();
        long itemsReceived = simBuy.volumeFilled();

        // ═══ PHASE 2: EXECUTE ═══

        return executeBothLegs(
                order, haveMarket, wantMarket, playerBankAccount, tradingCurrencyID,
                exactSellVolume, dollarsSpent, itemsReceived, wantPrice, SF,
                0, 0);
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Limit order execution
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a limit-type inter-market order.
     *
     * <p>The cross-rate limit defines the maximum acceptable rate of sell-items
     * per buy-item. Using integer arithmetic to avoid floating-point imprecision:
     * {@code effectiveRate = sellVolume * SF / buyVolume} compared against
     * {@code crossRateLimit}.</p>
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Quick check: if currentRate (wantPrice/havePrice) exceeds the limit,
     *       skip immediately</li>
     *   <li>Full simulation (same as market order)</li>
     *   <li>Check effective rate; if within limit, execute full volume</li>
     *   <li>If over limit, binary search for the max fillable buy-volume where
     *       the rate stays within the limit</li>
     *   <li>Partial fill: update the order's fill state, keep remainder pending</li>
     * </ol>
     */
    /**
     * Executes a limit-type inter-market order.
     *
     * <p>The cross-rate limit defines the maximum acceptable rate of sell-items
     * per buy-item. When the current market rate is favorable (≤ limit), the
     * order executes directly through the matching engine.</p>
     *
     * <p>To enforce the rate limit even with slippage, the sell volume is capped
     * to only what's needed to buy the target amount of want-items at current
     * prices. This keeps the effective rate close to the current rate (which
     * already passed the check). The order fills partially each tick until the
     * full target is met.</p>
     */
    private static ExecutionResult executeLimitOrder(
            InterMarketOrder order,
            ServerMarket haveMarket,
            ServerMarket wantMarket,
            @Nullable IServerBankAccount playerBankAccount,
            ItemID tradingCurrencyID)
    {
        long SF = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
        long crossRateLimit = order.getCrossRateLimit();

        long maxSellVolume = Math.abs(order.getSellOrder().getRemainingVolume());
        if (maxSellVolume <= 0)
        {
            return ExecutionResult.CANCELED;
        }

        long havePrice = haveMarket.getCurrentMarketPrice();
        long wantPrice = wantMarket.getCurrentMarketPrice();

        if (wantPrice <= 0 || havePrice <= 0)
        {
            return ExecutionResult.SKIPPED;
        }

        // Rate check: currentRate = wantPrice * SF / havePrice.
        // If this exceeds the limit, the rate is unfavorable — skip.
        long currentRate = wantPrice * SF / havePrice;
        if (currentRate > crossRateLimit)
        {
            return ExecutionResult.SKIPPED;
        }

        // Compute a conservative sell volume: only sell enough glass to buy the
        // remaining target sand at current prices. This prevents overshoot —
        // selling all glass at unfavorable depth prices would give too few sand.
        long buyRemaining = Math.abs(order.getBuyOrder().getRemainingVolume());
        // Glass needed = sand * (sandPrice / glassPrice) in raw integer math
        long sellNeeded = buyRemaining * wantPrice / havePrice;
        // Add 1 to avoid rounding down to 0 for small volumes
        if (sellNeeded <= 0) sellNeeded = 1;
        long sellVolume = Math.min(maxSellVolume, sellNeeded);

        long estimatedBuyVolume = sellVolume * havePrice / wantPrice;
        if (estimatedBuyVolume <= 0) estimatedBuyVolume = 1;

        long estimatedDollarBudget = sellVolume * havePrice / SF;
        if (estimatedDollarBudget <= 0) estimatedDollarBudget = 1;

        // Price constraints: sell glass only at prices that keep the cross-rate within limit.
        // minSellPrice ensures each glass sold yields enough dollars for the rate to hold.
        // maxBuyPrice caps the sand price to prevent overpaying.
        long minSellPrice = wantPrice * SF / crossRateLimit;
        long maxBuyPrice = havePrice * crossRateLimit / SF;

        ExecutionResult result = executeBothLegs(
                order, haveMarket, wantMarket, playerBankAccount, tradingCurrencyID,
                sellVolume, estimatedDollarBudget, estimatedBuyVolume, wantPrice, SF,
                minSellPrice, maxBuyPrice);

        if (result == ExecutionResult.FILLED || result == ExecutionResult.PARTIAL_FILL)
        {
            if (order.isFilled())
                return ExecutionResult.FILLED;
            else
                return ExecutionResult.PARTIAL_FILL;
        }
        return result;
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Shared two-leg execution
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates and executes both the sell-leg and buy-leg Order objects through
     * the respective ServerMarkets' matching engines.
     *
     * <p>The InterMarketExecutor runs AFTER ServerMarketManager.update() has
     * processed all regular market orders, so the input buffers are empty.
     * Each putOrder + update pair executes just that single order against the
     * current orderbook state with settled prices.</p>
     *
     * @param order              the parent inter-market order
     * @param haveMarket         sell-leg market
     * @param wantMarket         buy-leg market
     * @param playerBankAccount  player's bank account (null for bots)
     * @param tradingCurrencyID  currency item ID
     * @param sellVolume         exact number of items to sell (positive)
     * @param dollarBudget       dollars to spend on the buy leg
     * @param estimatedBuyVolume estimated items to receive from buy leg
     * @param wantPrice          current buy-leg market price (for buy order startPrice)
     * @param SF                 scale factor (BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR)
     * @param sellPriceFloor     minimum sell price (0 = no floor). INTER_MARKET orders use
     *                           startPrice as a price floor in the MatchingEngine.
     * @param buyPriceCap        maximum buy price (0 = no cap). INTER_MARKET orders use
     *                           startPrice as a price cap in the MatchingEngine.
     * @return ExecutionResult (FILLED or CANCELED)
     */
    private static ExecutionResult executeBothLegs(
            InterMarketOrder order,
            ServerMarket haveMarket,
            ServerMarket wantMarket,
            @Nullable IServerBankAccount playerBankAccount,
            ItemID tradingCurrencyID,
            long sellVolume,
            long dollarBudget,
            long estimatedBuyVolume,
            long wantPrice,
            long SF,
            long sellPriceFloor,
            long buyPriceCap)
    {
        // ── Create the sell Order ─────────────────────────────────────────────
        // INTER_MARKET type: ServerMarket skips history/net-flow tracking.
        // The MatchingEngine uses startPrice as the minimum accepted sell price
        // for INTER_MARKET orders (0 = no floor for market-type orders).
        Order sellOrder;
        if (order.isBotOrder())
        {
            sellOrder = new Order(
                    order.getSellItemID(),
                    Order.Type.INTER_MARKET,
                    -sellVolume,               // negative = sell
                    sellPriceFloor,            // startPrice = price floor for matching engine
                    order.getTime());          // bot order (no player UUID)
        }
        else
        {
            sellOrder = new Order(
                    order.getSellItemID(),
                    Order.Type.INTER_MARKET,
                    -sellVolume,               // negative = sell
                    sellPriceFloor,            // startPrice = price floor for matching engine
                    order.getTime(),
                    order.getOwnerUUID(),
                    order.getBankAccountNr());
        }

        // ── Create the buy Order ──────────────────────────────────────────────
        // INTER_MARKET type: startPrice is used as max accepted buy price (0 = no cap).
        long buyVolume = estimatedBuyVolume;
        if (buyVolume <= 0 && wantPrice > 0)
        {
            buyVolume = dollarBudget * SF / wantPrice;
        }
        if (buyVolume <= 0)
        {
            logWarn("Computed buy volume is 0, canceling inter-market order");
            return ExecutionResult.CANCELED;
        }

        // buyPriceCap: 0 = no cap (market orders), >0 = price cap (limit orders)
        Order buyOrder;
        if (order.isBotOrder())
        {
            buyOrder = new Order(
                    order.getBuyItemID(),
                    Order.Type.INTER_MARKET,
                    buyVolume,                 // positive = buy
                    buyPriceCap,               // startPrice: 0 = no cap, >0 = max buy price
                    order.getTime());          // bot order (no player UUID)
        }
        else
        {
            buyOrder = new Order(
                    order.getBuyItemID(),
                    Order.Type.INTER_MARKET,
                    buyVolume,                 // positive = buy
                    buyPriceCap,               // startPrice: 0 = no cap, >0 = max buy price
                    order.getTime(),
                    order.getOwnerUUID(),
                    order.getBankAccountNr());
        }

        // ── Execute sell leg ──────────────────────────────────────────────────
        // Queue the sell order and immediately process it
        boolean sellQueued = haveMarket.putOrder(sellOrder);
        if (!sellQueued)
        {
            logWarn("Failed to queue sell order in haveMarket, canceling inter-market order");
            return ExecutionResult.CANCELED;
        }
        haveMarket.update();

        // Check actual sell results
        long actualSellFilled = -sellOrder.getFilledVolume();  // filledVolume is negative for sells, negate for positive
        long actualDollarYield = sellOrder.getTransferredMoney();  // positive for sells (money received)

        if (actualSellFilled <= 0 || actualDollarYield <= 0)
        {
            // Sell leg failed to fill anything — the buy leg should not execute.
            // The MatchingEngine already handled the sell order's lifecycle (cancel callback etc.)
            logWarn("Sell leg filled 0 volume or yielded 0 dollars, canceling buy leg");
            return ExecutionResult.CANCELED;
        }

        // ── Execute buy leg ───────────────────────────────────────────────────
        // Adjust buy order's target volume based on actual dollar yield if it differs
        // from what was simulated (e.g., due to orderbook changes between sim and exec).
        // The buy order volume should be what the actual dollar yield can afford.
        if (actualDollarYield != dollarBudget && wantPrice > 0)
        {
            long adjustedBuyVolume = actualDollarYield * SF / wantPrice;
            if (adjustedBuyVolume > 0 && adjustedBuyVolume != buyVolume)
            {
                buyOrder.editTargetVolume(adjustedBuyVolume);
            }
        }

        boolean buyQueued = wantMarket.putOrder(buyOrder);
        if (!buyQueued)
        {
            // Buy leg failed to queue. The sell leg already executed,
            // so the dollars are in the player's money bank. This is acceptable —
            // the player still has the dollars from the sell.
            logWarn("Failed to queue buy order in wantMarket after sell leg executed");
            return ExecutionResult.CANCELED;
        }
        wantMarket.update();

        // ── Post-execution: update InterMarketOrder fill state ────────────────
        long actualBuyFilled = buyOrder.getFilledVolume();      // positive for buys
        long actualDollarSpent = -buyOrder.getTransferredMoney(); // transferredMoney is negative for buys

        // Update the inner orders of the InterMarketOrder to reflect actual fills
        Order imoBuyOrder = order.getBuyOrder();
        Order imoSellOrder = order.getSellOrder();

        // Edit the buy leg: add filled volume and transferred money
        imoBuyOrder.edit(actualBuyFilled, buyOrder.getTransferredMoney());

        // Edit the sell leg: add filled volume (negative) and transferred money (positive)
        imoSellOrder.edit(sellOrder.getFilledVolume(), sellOrder.getTransferredMoney());

        // ── Rounding dust: deposit leftover dollars to player's money bank ────
        // dust = actual dollars received from sell - actual dollars spent on buy
        long dust = actualDollarYield - actualDollarSpent;
        if (dust > 0 && playerBankAccount != null && !order.isBotOrder())
        {
            IServerBank moneyBank = playerBankAccount.getBank(tradingCurrencyID);
            if (moneyBank != null)
            {
                moneyBank.deposit(dust);
            }
        }

        // Determine if the order is fully or partially filled
        if (order.isFilled())
        {
            return ExecutionResult.FILLED;
        }
        else
        {
            // For market orders, whatever we got is the final result (no retry).
            // For limit orders, remaining volume stays pending.
            if (order.isMarketOrder())
                return ExecutionResult.FILLED;  // Market orders don't retry
            else
                return ExecutionResult.PARTIAL_FILL;
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Batch processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes all pending inter-market orders from a queue.
     *
     * <p>Orders are sorted by time (FIFO) before processing. For each order,
     * both markets are looked up, and execution proceeds. Market orders are
     * fill-or-cancel (no retry). Limit orders may be partially filled, skipped
     * (rate unfavorable), or fully filled.</p>
     *
     * @param pendingOrders     queue of pending inter-market orders to process
     * @param markets           map of ItemID to ServerMarket for looking up both legs
     * @param bankAccountLookup function to resolve bank accounts by account number (null for bot-only processing)
     * @param tradingCurrencyID the money item ID
     * @return map of each processed order to its execution result
     */
    public static Map<InterMarketOrder, ExecutionResult> processOrders(
            Queue<InterMarketOrder> pendingOrders,
            Map<ItemID, ServerMarket> markets,
            @Nullable java.util.function.IntFunction<IServerBankAccount> bankAccountLookup,
            ItemID tradingCurrencyID)
    {
        Map<InterMarketOrder, ExecutionResult> results = new LinkedHashMap<>();

        if (pendingOrders == null || pendingOrders.isEmpty())
            return results;

        // Sort by time (FIFO) — earlier orders execute first
        List<InterMarketOrder> sorted = new ArrayList<>(pendingOrders);
        sorted.sort(Comparator.comparingLong(InterMarketOrder::getTime));

        for (InterMarketOrder order : sorted)
        {
            // Look up both markets
            ServerMarket haveMarket = markets.get(order.getSellItemID());
            ServerMarket wantMarket = markets.get(order.getBuyItemID());

            if (haveMarket == null || wantMarket == null)
            {
                logWarn("Market not found for inter-market order: have=" + order.getSellItemID()
                        + " want=" + order.getBuyItemID());
                results.put(order, ExecutionResult.CANCELED);
                continue;
            }

            // Resolve player's bank account (null for bots)
            IServerBankAccount playerBankAccount = null;
            if (!order.isBotOrder() && bankAccountLookup != null)
            {
                playerBankAccount = bankAccountLookup.apply(order.getBankAccountNr());
                if (playerBankAccount == null)
                {
                    logWarn("Bank account not found for inter-market order: accountNr="
                            + order.getBankAccountNr());
                    results.put(order, ExecutionResult.CANCELED);
                    continue;
                }
            }

            ExecutionResult result = executeOrder(
                    order, haveMarket, wantMarket, playerBankAccount, tradingCurrencyID);

            results.put(order, result);
        }

        return results;
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  Logging helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static void logWarn(String message)
    {
        StockMarketMod.LOGGER.warn(LOG_TAG + message);
    }

    private static void logError(String message, Throwable throwable)
    {
        StockMarketMod.LOGGER.error(LOG_TAG + message, throwable);
    }

    // Utility class — no instances
    private InterMarketExecutor() {}
}
