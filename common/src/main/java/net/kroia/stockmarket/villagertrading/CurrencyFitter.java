package net.kroia.stockmarket.villagertrading;

import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits a raw currency value (100 raw = 1.00 display units) into a limited
 * number of {@link ItemStack} slots of the configured trading-currency item.
 * <p>
 * Two strategies (selected at runtime — the currency is never assumed to be
 * BankSystem money):
 * <ul>
 *   <li><b>BankSystem money — single-denomination fit:</b> the value is
 *       expressed as ONE denomination type in a single stack whenever possible.
 *       Every denomination is scored with
 *       {@code count = clamp(round(value / worth), 1, maxStack)} and
 *       {@code error = |count × worth − value|}; the smallest error wins, and
 *       ties prefer the LARGER denomination (fewer notes — e.g. 2000 raw
 *       becomes 1 × 20-dollar note, not 2 × 10). Users find mixed
 *       denominations inconvenient, so exactness is secondary to uniformity;
 *       thanks to the cent coins most realistic values still fit exactly
 *       (240 raw = 12 × 20-cent). Only values beyond one full stack of the
 *       largest denomination spill into further slots — and each spill slot is
 *       itself a full single-denomination stack. Never emits zero items — the
 *       minimum is one 1-cent coin.</li>
 *   <li><b>Generic item:</b> one physical item is worth 1.00 units (= 100 raw,
 *       BankSystem's deposit semantics for non-money items). The item count is
 *       rounded to nearest (minimum 1) and split into stacks respecting the
 *       item's own max stack size (which may be 16 or 1). Single-type by
 *       nature.</li>
 * </ul>
 * <p>
 * <b>Overflow policy: clamp, don't scale.</b> When the value does not fit into
 * the available slots the result is capped at maximum capacity and flagged as
 * {@code clamped} — the price is capped in the player's favor instead of
 * silently altering gameplay-tuned item quantities. Callers should log a
 * one-time warning so admins know to configure a finer-grained currency.
 * <p>
 * All methods are pure/static so the algorithm is unit-testable without a
 * running market.
 */
public final class CurrencyFitter {

    /** Raw value of one generic (non-money) currency item: 1 item = 1.00 units. */
    private static final long GENERIC_ITEM_RAW_VALUE = 100L;

    private CurrencyFitter() {
    }

    /**
     * Result of a fit operation.
     *
     * @param stacks  the currency stacks to place into the available slots
     *                (never more than the requested slot count; never empty for
     *                a valid request)
     * @param clamped {@code true} when the representable value fell short of the
     *                requested value by more than the final-slot rounding step —
     *                i.e. the slots could not hold the full price
     */
    public record FitResult(List<ItemStack> stacks, boolean clamped) {
    }

    /**
     * Fits {@code rawValue} into at most {@code slots} stacks of the given currency.
     *
     * @param rawValue the value to represent, in raw units (min 1 is enforced)
     * @param slots    the number of ItemStack slots available (1 for a trade result,
     *                 1-2 for trade cost slots)
     * @param currency the trading-currency item template (count ignored)
     * @return the fitted stacks plus the clamp flag; an empty stack list is only
     *         returned for invalid input ({@code slots <= 0} or empty currency)
     */
    public static FitResult fit(long rawValue, int slots, ItemStack currency) {
        if (slots <= 0 || currency == null || currency.isEmpty()) {
            return new FitResult(List.of(), false); // defensive: callers guarantee valid input
        }
        if (rawValue < 1) {
            rawValue = 1;
        }
        // Runtime detection only — the configured currency may be any item from
        // any mod. Only actual BankSystem MoneyItems use the denomination path.
        if (currency.getItem() instanceof MoneyItem) {
            return fitMoney(rawValue, slots);
        }
        return fitGeneric(rawValue, slots, currency);
    }

    /**
     * Generic-item path: 1 item = 1.00 units, split into stacks of the item's
     * own max stack size, largest stacks first.
     */
    private static FitResult fitGeneric(long rawValue, int slots, ItemStack currency) {
        int maxPerStack = Math.max(1, currency.getMaxStackSize());
        long desiredItems = Math.max(1, Math.round(rawValue / (double) GENERIC_ITEM_RAW_VALUE));
        long capacity = (long) maxPerStack * slots;
        boolean clamped = desiredItems > capacity;
        long items = Math.min(desiredItems, capacity);

        List<ItemStack> out = new ArrayList<>(slots);
        for (int s = 0; s < slots && items > 0; s++) {
            int count = (int) Math.min(items, maxPerStack);
            ItemStack stack = currency.copy();
            stack.setCount(count);
            out.add(stack);
            items -= count;
        }
        return new FitResult(out, clamped);
    }

    /**
     * BankSystem money path: single-denomination fit.
     * <p>
     * The whole value is expressed as ONE denomination type in a single stack
     * whenever possible: every denomination is scored with
     * {@code count = clamp(round(value / worth), 1, maxStack)} and
     * {@code error = |count × worth − value|}; the smallest error wins and ties
     * prefer the larger denomination (fewer notes). Only when the value exceeds
     * one full stack of the largest denomination does it spill into additional
     * slots — each spill slot being a full single-denomination stack of the
     * largest note, with the final slot best-fitted as above.
     */
    private static FitResult fitMoney(long rawValue, int slots) {
        // All money denominations, largest worth first (descending iteration
        // makes "ties prefer the larger denomination" fall out naturally).
        // Shared with the value-based payment system (change-making).
        List<ItemStack> denominations = MoneyPayment.moneyDenominationsDesc();
        if (denominations.isEmpty()) {
            return new FitResult(List.of(), false); // registry not populated — cannot fit
        }
        ItemStack largest = denominations.get(0);
        long largestWorth = ((MoneyItem) largest.getItem()).worth();
        long largestStackValue = (long) Math.max(1, largest.getMaxStackSize()) * largestWorth;

        long remaining = rawValue;
        long lastUsedWorth = largestWorth;
        List<ItemStack> out = new ArrayList<>(slots);
        for (int s = 0; s < slots && remaining > 0; s++) {
            boolean lastSlot = (s == slots - 1);

            // Spill rule: values beyond one full stack of the largest note fill
            // whole single-denomination stacks first (still one type per slot).
            if (!lastSlot && remaining > largestStackValue) {
                ItemStack stack = largest.copy();
                stack.setCount(Math.max(1, largest.getMaxStackSize()));
                out.add(stack);
                remaining -= largestStackValue;
                lastUsedWorth = largestWorth;
                continue;
            }

            // Single-denomination best fit: smallest |count × worth − value|,
            // ties resolved toward the larger denomination by iteration order.
            ItemStack bestDenom = largest;
            long bestWorth = largestWorth;
            int bestCount = 1;
            long bestError = Long.MAX_VALUE;
            for (ItemStack denom : denominations) {
                long worth = ((MoneyItem) denom.getItem()).worth();
                int maxStack = Math.max(1, denom.getMaxStackSize());
                int count = (int) Math.max(1, Math.min(maxStack, Math.round(remaining / (double) worth)));
                long error = Math.abs((long) count * worth - remaining);
                if (error < bestError) {
                    bestError = error;
                    bestDenom = denom;
                    bestWorth = worth;
                    bestCount = count;
                }
            }
            ItemStack stack = bestDenom.copy();
            stack.setCount(bestCount);
            out.add(stack);
            remaining -= (long) bestCount * bestWorth;
            lastUsedWorth = bestWorth;
            break; // the best-fit slot always concludes the fit
        }

        if (out.isEmpty()) {
            // Never emit "free": the minimum price is one 1-cent coin.
            ItemStack cent = denominations.get(denominations.size() - 1).copy();
            cent.setCount(1);
            out.add(cent);
            remaining = 0;
        }

        // Clamped when the shortfall exceeds the best-fit rounding step
        // (half of the last used denomination's worth). Overshoot (negative
        // remainder) is regular rounding, never a clamp.
        boolean clamped = remaining > lastUsedWorth / 2;
        return new FitResult(out, clamped);
    }
}
