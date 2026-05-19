package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only depth simulation that mirrors the MatchingEngine's depth-walking logic
 * without modifying any state (no calls to fillVirtual, order.edit, setCurrentMarketPrice, etc.).
 *
 * <p>Used to predict execution outcomes (volume filled, dollar cost/yield, resulting price)
 * for hypothetical market orders against the current orderbook state.</p>
 *
 * <p>All prices and volumes are in "raw" (scaled) format using
 * {@link BankSystemModSettings#ITEM_FRACTION_SCALE_FACTOR} as the scale factor.</p>
 */
public class DepthSimulation
{
    /** Safety timeout to prevent infinite loops when walking through sparse depth regions. */
    private static final int TIMEOUT_COUNT = 10000;

    // Utility class — no instances needed
    private DepthSimulation() {}

    /**
     * Result of a depth simulation walk.
     *
     * @param volumeFilled       number of items filled (always positive: items sold or items bought)
     * @param dollarAmount       total dollar amount involved (positive: money received for sells,
     *                           money spent for buys)
     * @param priceAfterExecution the price level where the walk ended (the new market price
     *                           that would result from this execution)
     */
    public record SimResult(long volumeFilled, long dollarAmount, long priceAfterExecution) {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  simulateSell — walk buy-side depth downward (selling items into bids)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Simulates selling {@code volume} items into the buy-side depth of the orderbook,
     * mirroring the MatchingEngine's processSell + drainVirtualSell logic in a read-only fashion.
     *
     * <p>The walk starts at {@code currentPrice} and moves downward through:
     * <ol>
     *   <li>Virtual buy-side volume (positive virtual volume at each price level)</li>
     *   <li>Real buy limit orders (sorted by price descending, highest first)</li>
     * </ol>
     * These are interleaved: virtual depth is drained down to each real order's price before
     * matching against that real order, mirroring the MatchingEngine's interleaving pattern.</p>
     *
     * @param orderbook    the orderbook to read depth from (NOT modified)
     * @param volume       the amount of items to sell (positive value; will be treated as a sell)
     * @param currentPrice the current market price to start walking from
     * @return SimResult with volumeFilled (items sold), dollarAmount (money received),
     *         and priceAfterExecution (final price level)
     */
    public static SimResult simulateSell(Orderbook orderbook, long volume, long currentPrice)
    {
        if (volume <= 0 || currentPrice <= 0)
            return new SimResult(0, 0, currentPrice);

        // Track the sell as negative remaining volume, mirroring MatchingEngine convention
        // (orderVolume < 0 means sell)
        long remainingSellVolume = -volume;  // negative
        long dollarYield = 0;                // money received (positive)
        long volumeFilled = 0;               // items sold (positive)
        long walkPrice = currentPrice;
        long lastFilledPrice = currentPrice; // tracks the last price where a fill happened

        // Snapshot real buy limit orders and sort by price descending (highest first).
        // PriorityQueue iterator does NOT guarantee sorted order, so explicit sort is needed.
        List<Order> buyOrders = new ArrayList<>(orderbook.getBuyLimitOrders());
        buyOrders.sort((a, b) -> Long.compare(b.getStartPrice(), a.getStartPrice()));

        int buyOrderIndex = 0;

        // Walk through real orders interleaved with virtual depth,
        // mirroring MatchingEngine.processSell's for-loop over buyOrderSnapshot
        while (remainingSellVolume < 0)
        {
            // Determine the stop price for virtual drain: the next real buy order's price, or 0
            long stopPrice;
            if (buyOrderIndex < buyOrders.size())
                stopPrice = buyOrders.get(buyOrderIndex).getStartPrice();
            else
                stopPrice = 0;

            // --- Drain virtual buy-side depth from walkPrice down to stopPrice ---
            // Mirrors MatchingEngine.drainVirtualSell(orderVolume, itemDelta, moneyDelta, stopPrice)
            long timeout = TIMEOUT_COUNT;
            while (remainingSellVolume < 0 && walkPrice >= stopPrice)
            {
                long virtualVol = orderbook.getRawVirtualVolumeRounded(walkPrice);
                if (virtualVol > 0)
                {
                    // Simulate fillVirtual(walkPrice, remainingSellVolume):
                    // fillVirtual with positive currentVolume and negative volume returns
                    // the consumed amount (negative), clamped so volume doesn't go below 0.
                    long canConsume = Math.min(virtualVol, -remainingSellVolume);
                    // filled in MatchingEngine terms is negative (reduction of buy-side volume)
                    // but we track the absolute fill amount here
                    long cost = Math.round((double) walkPrice * canConsume / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                    dollarYield += cost;
                    volumeFilled += canConsume;
                    remainingSellVolume += canConsume;  // moves toward 0
                    lastFilledPrice = walkPrice;

                    // If we fully consumed this price level, step down
                    if (canConsume >= virtualVol && remainingSellVolume < 0)
                        walkPrice--;
                    // else we partially filled, stay at this price (loop will exit since remainingSellVolume >= 0)
                }
                else
                {
                    walkPrice--;
                }

                if (walkPrice <= 0)
                {
                    walkPrice = 0;
                    break;
                }

                timeout--;
                if (timeout <= 0)
                    break;
            }

            // --- Match against the next real buy order ---
            if (buyOrderIndex < buyOrders.size() && remainingSellVolume < 0)
            {
                Order buyOrder = buyOrders.get(buyOrderIndex);

                // fillPotential = how much this buy order can absorb (positive)
                long fillPotential = buyOrder.getRemainingVolume();  // positive for buy orders
                if (fillPotential > -remainingSellVolume)
                    fillPotential = -remainingSellVolume;

                // cost = rawVolume * rawPrice / scaleFactor
                long cost = Math.round((double) fillPotential * buyOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                dollarYield += cost;
                volumeFilled += fillPotential;
                remainingSellVolume += fillPotential;
                walkPrice = buyOrder.getStartPrice();
                lastFilledPrice = buyOrder.getStartPrice();

                buyOrderIndex++;
            }
            else if (walkPrice <= 0)
            {
                break;
            }
            else if (buyOrderIndex >= buyOrders.size())
            {
                // No more real orders; we already drained virtual above.
                // If we still have remaining volume and walkPrice reached stopPrice=0,
                // we're done.
                break;
            }
        }

        long finalPrice = lastFilledPrice > 0 ? lastFilledPrice : 1;
        return new SimResult(volumeFilled, dollarYield, finalPrice);
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  simulateBuy — walk sell-side depth upward (buying items from asks)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Simulates buying items from the sell-side depth of the orderbook using a dollar budget,
     * mirroring the MatchingEngine's processBuy + drainVirtualBuy logic in a read-only fashion.
     *
     * <p>The walk starts at {@code currentPrice} and moves upward through:
     * <ol>
     *   <li>Virtual sell-side volume (negative virtual volume at each price level)</li>
     *   <li>Real sell limit orders (sorted by price ascending, lowest first)</li>
     * </ol>
     * These are interleaved: virtual depth is drained up to each real order's price before
     * matching against that real order, mirroring the MatchingEngine's interleaving pattern.</p>
     *
     * @param orderbook    the orderbook to read depth from (NOT modified)
     * @param dollarBudget the money available to spend (positive value)
     * @param currentPrice the current market price to start walking from
     * @return SimResult with volumeFilled (items bought), dollarAmount (money spent),
     *         and priceAfterExecution (final price level)
     */
    public static SimResult simulateBuy(Orderbook orderbook, long dollarBudget, long currentPrice)
    {
        if (dollarBudget <= 0 || currentPrice <= 0)
            return new SimResult(0, 0, currentPrice);

        // Track the buy as positive remaining volume. We start with a large estimate
        // and let the budget constrain us (mirroring MatchingEngine where orderVolume is
        // capped to what the player can afford).
        // maxAffordable = funds * SF / price
        long remainingBuyVolume;
        if (currentPrice > 0)
            remainingBuyVolume = dollarBudget * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / currentPrice + 1;
        else
            remainingBuyVolume = Long.MAX_VALUE;

        if (remainingBuyVolume <= 0)
            return new SimResult(0, 0, currentPrice);

        long dollarsSpent = 0;               // money spent (positive)
        long itemsReceived = 0;              // items bought (positive)
        long walkPrice = currentPrice;
        long availableFunds = dollarBudget;  // remaining budget
        long lastFilledPrice = currentPrice; // tracks the last price where a fill happened

        // Snapshot real sell limit orders and sort by price ascending (lowest first).
        // PriorityQueue iterator does NOT guarantee sorted order, so explicit sort is needed.
        List<Order> sellOrders = new ArrayList<>(orderbook.getSellLimitOrders());
        sellOrders.sort(Comparator.comparingLong(Order::getStartPrice));

        int sellOrderIndex = 0;

        // Walk through real orders interleaved with virtual depth,
        // mirroring MatchingEngine.processBuy's for-loop over sellOrderSnapshot
        while (remainingBuyVolume > 0 && availableFunds > 0)
        {
            // Determine the stop price for virtual drain: the next real sell order's price, or max
            long stopPrice;
            if (sellOrderIndex < sellOrders.size())
                stopPrice = sellOrders.get(sellOrderIndex).getStartPrice();
            else
                stopPrice = Long.MAX_VALUE;

            // --- Drain virtual sell-side depth from walkPrice up to stopPrice ---
            // Mirrors MatchingEngine.drainVirtualBuy(orderVolume, itemDelta, moneyDelta, stopPrice, availableFunds)
            long timeout = TIMEOUT_COUNT;
            while (remainingBuyVolume > 0 && walkPrice <= stopPrice)
            {
                long virtualVol = orderbook.getRawVirtualVolumeRounded(walkPrice);
                if (virtualVol < 0)
                {
                    // Sell-side depth is negative; available volume to buy is -virtualVol (positive)
                    long availableAtLevel = -virtualVol;

                    // Cap to what the buyer can afford at this price level
                    // maxByFunds = funds * SF / price
                    long maxByFunds;
                    if (walkPrice <= 0 || availableFunds >= Long.MAX_VALUE / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR)
                        maxByFunds = remainingBuyVolume;
                    else
                        maxByFunds = availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / walkPrice;

                    long wantToFill = Math.min(remainingBuyVolume, maxByFunds);
                    if (wantToFill <= 0)
                        break;  // buyer is broke

                    // Simulate fillVirtual(walkPrice, wantToFill):
                    // fillVirtual with negative currentVolume and positive volume returns
                    // the consumed amount (positive), clamped so volume doesn't go above 0.
                    long canConsume = Math.min(availableAtLevel, wantToFill);

                    // cost = rawPrice * rawVolume / scaleFactor
                    long cost = Math.round((double) walkPrice * canConsume / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                    dollarsSpent += cost;
                    itemsReceived += canConsume;
                    remainingBuyVolume -= canConsume;
                    availableFunds -= cost;
                    lastFilledPrice = walkPrice;

                    // If we fully consumed this price level, step up
                    if (canConsume >= availableAtLevel && remainingBuyVolume > 0)
                        walkPrice++;
                    // else we partially filled, stay at this price
                }
                else
                {
                    walkPrice++;
                }

                timeout--;
                if (timeout <= 0)
                    break;
            }

            // --- Match against the next real sell order ---
            if (sellOrderIndex < sellOrders.size() && remainingBuyVolume > 0 && availableFunds > 0)
            {
                Order sellOrder = sellOrders.get(sellOrderIndex);

                // fillPotential from sell order: getRemainingVolume() is negative for sell orders
                long fillPotential = sellOrder.getRemainingVolume();  // negative
                if (-fillPotential > remainingBuyVolume)
                    fillPotential = -remainingBuyVolume;              // still negative

                // Cap fill to what the buyer can still afford
                // costFull = rawVolume * rawPrice / scaleFactor (fillPotential is negative)
                long costFull = Math.round((double) fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                // costFull is negative (negative vol * positive price / SF)
                if (availableFunds < -costFull)
                {
                    // fillPotential = -funds * SF / price (negative volume for sells)
                    fillPotential = -availableFunds * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / sellOrder.getStartPrice();
                }

                if (fillPotential >= 0)
                    break;  // buyer is completely broke, stop

                // cost = rawVolume * rawPrice / scaleFactor (fillPotential is negative, so cost is negative)
                long cost = Math.round((double) fillPotential * sellOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                // cost is negative; actual dollars spent is -cost (positive)
                dollarsSpent += (-cost);
                itemsReceived += (-fillPotential);  // positive items gained
                remainingBuyVolume += fillPotential; // fillPotential is negative, reduces remaining
                availableFunds += cost;              // cost is negative, reduces available funds
                walkPrice = sellOrder.getStartPrice();
                lastFilledPrice = sellOrder.getStartPrice();

                sellOrderIndex++;
            }
            else if (sellOrderIndex >= sellOrders.size() && stopPrice == Long.MAX_VALUE)
            {
                // No more real orders and virtual depth is exhausted
                break;
            }
            else
            {
                break;
            }
        }

        long finalPrice = lastFilledPrice > 0 ? lastFilledPrice : 1;
        return new SimResult(itemsReceived, dollarsSpent, finalPrice);
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  simulateSellForDollars — walk buy-side depth until target dollar yield
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Simulates selling items into the buy-side depth until a target dollar yield is reached.
     * This is the inverse of simulateSell: instead of specifying volume, the caller specifies
     * how much money they want to receive, and the simulation determines how many items
     * must be sold to achieve that.
     *
     * <p>At the final price level where the target would be exceeded, a partial fill is calculated:
     * {@code partialVolume = remainingDollarsNeeded * SCALE_FACTOR / currentLevelPrice}</p>
     *
     * @param orderbook     the orderbook to read depth from (NOT modified)
     * @param targetDollars the desired dollar yield (positive value)
     * @param currentPrice  the current market price to start walking from
     * @return SimResult with volumeFilled (items sold), dollarAmount (actual dollar yield,
     *         as close to targetDollars as possible), and priceAfterExecution (final price level)
     */
    public static SimResult simulateSellForDollars(Orderbook orderbook, long targetDollars, long currentPrice)
    {
        if (targetDollars <= 0 || currentPrice <= 0)
            return new SimResult(0, 0, currentPrice);

        long dollarYield = 0;                // money received so far (positive)
        long volumeSold = 0;                 // items sold so far (positive)
        long walkPrice = currentPrice;
        long lastFilledPrice = currentPrice; // tracks the last price where a fill happened

        // Snapshot real buy limit orders and sort by price descending (highest first)
        List<Order> buyOrders = new ArrayList<>(orderbook.getBuyLimitOrders());
        buyOrders.sort((a, b) -> Long.compare(b.getStartPrice(), a.getStartPrice()));

        int buyOrderIndex = 0;
        boolean done = false;

        // Walk through real orders interleaved with virtual depth
        while (!done)
        {
            // Determine the stop price for virtual drain
            long stopPrice;
            if (buyOrderIndex < buyOrders.size())
                stopPrice = buyOrders.get(buyOrderIndex).getStartPrice();
            else
                stopPrice = 0;

            // --- Drain virtual buy-side depth from walkPrice down to stopPrice ---
            long timeout = TIMEOUT_COUNT;
            while (dollarYield < targetDollars && walkPrice >= stopPrice)
            {
                long virtualVol = orderbook.getRawVirtualVolumeRounded(walkPrice);
                if (virtualVol > 0)
                {
                    // Calculate how much volume we need to sell at this price to reach
                    // the remaining dollar target
                    long remainingDollarsNeeded = targetDollars - dollarYield;

                    // partialVolume = remainingDollarsNeeded * SF / price
                    // This is the max volume we'd need to sell at this price to reach the target
                    long maxVolumeForTarget = remainingDollarsNeeded * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / walkPrice;
                    // Add 1 to handle rounding: we may need one extra raw unit to cover the target
                    if (maxVolumeForTarget * walkPrice / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR < remainingDollarsNeeded)
                        maxVolumeForTarget++;

                    long canConsume = Math.min(virtualVol, maxVolumeForTarget);
                    long cost = Math.round((double) walkPrice * canConsume / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);

                    dollarYield += cost;
                    volumeSold += canConsume;
                    lastFilledPrice = walkPrice;

                    if (dollarYield >= targetDollars)
                    {
                        done = true;
                        break;
                    }

                    // If we fully consumed this price level, step down
                    if (canConsume >= virtualVol)
                        walkPrice--;
                    // else we partially filled but didn't reach target (shouldn't happen
                    // since we computed maxVolumeForTarget, but handle gracefully)
                }
                else
                {
                    walkPrice--;
                }

                if (walkPrice <= 0)
                {
                    walkPrice = 0;
                    done = true;
                    break;
                }

                timeout--;
                if (timeout <= 0)
                {
                    done = true;
                    break;
                }
            }

            if (done)
                break;

            // --- Match against the next real buy order ---
            if (buyOrderIndex < buyOrders.size() && dollarYield < targetDollars)
            {
                Order buyOrder = buyOrders.get(buyOrderIndex);
                long fillPotential = buyOrder.getRemainingVolume();  // positive for buy orders

                // Check if filling this entire order would exceed the target
                long fullCost = Math.round((double) fillPotential * buyOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                long remainingDollarsNeeded = targetDollars - dollarYield;

                if (fullCost > remainingDollarsNeeded)
                {
                    // Partial fill: only sell enough to reach the target
                    long partialVolume = remainingDollarsNeeded * BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR / buyOrder.getStartPrice();
                    if (partialVolume <= 0)
                        partialVolume = 1;  // sell at least 1 raw unit if we need any dollars

                    long partialCost = Math.round((double) partialVolume * buyOrder.getStartPrice() / BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR);
                    dollarYield += partialCost;
                    volumeSold += partialVolume;
                    lastFilledPrice = buyOrder.getStartPrice();
                    done = true;
                }
                else
                {
                    // Consume the entire real order
                    dollarYield += fullCost;
                    volumeSold += fillPotential;
                    walkPrice = buyOrder.getStartPrice();
                    lastFilledPrice = buyOrder.getStartPrice();
                }

                buyOrderIndex++;
            }
            else if (walkPrice <= 0 || buyOrderIndex >= buyOrders.size())
            {
                // No more depth available
                break;
            }
        }

        long finalPrice = lastFilledPrice > 0 ? lastFilledPrice : 1;
        return new SimResult(volumeSold, dollarYield, finalPrice);
    }
}
