package net.kroia.stockmarket.villagertrading;

import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Value-based money payment for merchant offers whose cost is BankSystem money
 * (offers produced by the T-064 villager trade repricing, see
 * {@link VillagerTradeRewriter}).
 * <p>
 * Vanilla {@code MerchantOffer.satisfiedBy}/{@code take} match payment stacks
 * item-for-item, so a cost of 1 × 20-dollar note rejects 2 × 10-dollar notes.
 * This helper replaces that matching with <b>value</b> semantics for
 * money-cost offers: any combination of denominations totaling at least the
 * price is accepted, the cheapest sufficient combination is consumed, and
 * overpayment returns exact change (fewest notes).
 * <p>
 * Everything except {@link #deliverChange} is a pure function of the offer and
 * the two payment-slot stacks — deliberately: the client's {@code MerchantMenu}
 * runs {@code satisfiedBy} (trade preview / red X) and {@code take} (take
 * prediction) locally on offers synced via {@code ClientboundMerchantOffersPacket},
 * so the logic must be evaluable on both sides <b>without any price-table or
 * backend lookup</b> to stay desync-free. All arithmetic in raw units
 * (100 raw = 1.00 display units).
 * <p>
 * Invoked from the trivial mixin delegates in
 * {@code net.kroia.stockmarket.mixin} ({@code MerchantOfferMixin},
 * {@code MerchantResultSlotMixin}, {@code MerchantMenuMixin}).
 */
public final class MoneyPayment {

    private MoneyPayment() {
    }

    // ========================================================================
    // Denominations
    // ========================================================================

    /**
     * All BankSystem money denominations as single-count template stacks,
     * sorted by {@link MoneyItem#worth()} descending (largest note first).
     * <p>
     * Shared with {@link CurrencyFitter} (single-denomination price fitting)
     * and used here for change-making. The returned list is freshly built on
     * every call (the stacks are safe to mutate/copy); it is empty only when
     * the BankSystem item registry is not populated yet.
     *
     * @return money denomination templates, largest worth first
     */
    public static List<ItemStack> moneyDenominationsDesc() {
        List<ItemStack> denominations = new ArrayList<>(BankSystemItems.getMoneyItems());
        denominations.removeIf(stack -> !(stack.getItem() instanceof MoneyItem));
        denominations.sort(Comparator.comparingLong(
                (ItemStack stack) -> ((MoneyItem) stack.getItem()).worth()).reversed());
        return denominations;
    }

    // ========================================================================
    // Gate + values (pure, client-safe)
    // ========================================================================

    /**
     * Gate: does this offer charge BankSystem money on any cost side?
     * <p>
     * Checked purely via the cost slots' {@link ItemCost#itemStack()} being a
     * genuine {@link MoneyItem} — <b>no price table, settings or backend
     * lookup</b>. Money-cost offers only exist because the T-064 rewriter
     * created them (feature enabled + MoneyItem currency); generic currencies
     * produce non-MoneyItem costs, so they fail this gate and keep exact
     * vanilla matching. The same predicate evaluates identically on client and
     * server, giving zero desync with zero extra networking.
     *
     * @param offer the offer to test
     * @return {@code true} when at least one cost side is BankSystem money
     */
    public static boolean isValueBasedOffer(MerchantOffer offer) {
        if (offer == null) {
            return false;
        }
        return isMoneyCost(offer.getItemCostA())
                || offer.getItemCostB().map(MoneyPayment::isMoneyCost).orElse(false);
    }

    /** Whether a cost slot's template stack is a genuine BankSystem money item. */
    private static boolean isMoneyCost(ItemCost cost) {
        return isMoneyStack(cost.itemStack());
    }

    /**
     * Whether a stack is a genuine, unmodified BankSystem money item.
     * Renamed/component-modified money fails {@link MoneyItem#isMoney} and is
     * treated as a regular (non-money) item — it is never value-matched or
     * consumed as payment.
     *
     * @param stack the stack to test (empty stacks are not money)
     * @return {@code true} for unmodified money items
     */
    public static boolean isMoneyStack(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof MoneyItem
                && MoneyItem.isMoney(stack);
    }

    /**
     * Raw value of a payment stack: {@code worth × count} for genuine money,
     * {@code 0} for anything else (including empty stacks).
     *
     * @param stack the stack to value
     * @return the raw value
     */
    public static long stackValue(ItemStack stack) {
        if (!isMoneyStack(stack)) {
            return 0L;
        }
        return ((MoneyItem) stack.getItem()).worth() * stack.getCount();
    }

    /**
     * Combined raw value of both payment slots.
     *
     * @param a payment slot 0 stack
     * @param b payment slot 1 stack
     * @return {@code stackValue(a) + stackValue(b)}
     */
    public static long totalValue(ItemStack a, ItemStack b) {
        return stackValue(a) + stackValue(b);
    }

    /**
     * Total raw money price of an offer — the sum over its money cost sides.
     * <p>
     * The costA side mirrors vanilla by using {@code getCostA().getCount()}
     * (the demand/gossip-modified count; T-064 money offers have
     * {@code priceMultiplier = 0}, so modified = base). The costB side uses the
     * raw {@code count()} exactly like vanilla {@code satisfiedBy}. Item
     * (non-money) cost sides contribute nothing — they are matched vanilla-style.
     *
     * @param offer the money-cost offer
     * @return the raw price of the money side(s)
     */
    public static long costValue(MerchantOffer offer) {
        long need = 0L;
        ItemCost costA = offer.getItemCostA();
        if (isMoneyCost(costA)) {
            need += ((MoneyItem) costA.itemStack().getItem()).worth() * offer.getCostA().getCount();
        }
        Optional<ItemCost> costB = offer.getItemCostB();
        if (costB.isPresent() && isMoneyCost(costB.get())) {
            need += ((MoneyItem) costB.get().itemStack().getItem()).worth() * costB.get().count();
        }
        return need;
    }

    // ========================================================================
    // Matching (pure, client-safe)
    // ========================================================================

    /**
     * Value-based replacement for {@code MerchantOffer.satisfiedBy} on
     * money-cost offers ({@link #isValueBasedOffer} must be {@code true};
     * anything else returns {@code false}).
     * <ul>
     *   <li><b>All-money costs</b> (costA money and costB money or absent):
     *       satisfied iff every non-empty payment stack is genuine money AND
     *       the combined value covers the total price. Deliberate relaxation
     *       vs. vanilla: costB-absent offers accept payment split across both
     *       slots (vanilla would require slot 1 empty).</li>
     *   <li><b>Mixed offer</b> (one money cost + one preserved item cost, e.g.
     *       enchanted book): the item side is vanilla-checked per slot, the
     *       money side is value-checked per slot. Swapped orientation is
     *       handled by vanilla's caller-side retry
     *       ({@code getRecipeFor} / {@code onTake} both retry with swapped
     *       arguments).</li>
     * </ul>
     *
     * @param offer the money-cost offer
     * @param a     payment slot 0 stack
     * @param b     payment slot 1 stack
     * @return whether the payment satisfies the offer
     */
    public static boolean satisfied(MerchantOffer offer, ItemStack a, ItemStack b) {
        ItemCost costA = offer.getItemCostA();
        Optional<ItemCost> costB = offer.getItemCostB();
        boolean moneyA = isMoneyCost(costA);
        boolean moneyB = costB.isPresent() && isMoneyCost(costB.get());

        if (moneyA && (costB.isEmpty() || moneyB)) {
            // All-money: a non-money stack in a money position never satisfies
            // (and is never consumed).
            if (!a.isEmpty() && !isMoneyStack(a)) {
                return false;
            }
            if (!b.isEmpty() && !isMoneyStack(b)) {
                return false;
            }
            return totalValue(a, b) >= costValue(offer);
        }
        if (moneyA) {
            // Mixed: money in slot 0, preserved item cost in slot 1.
            return stackValue(a) >= costValue(offer) && itemSideSatisfied(costB.get(), b, costB.get().count());
        }
        if (moneyB) {
            // Mixed: preserved item cost in slot 0, money in slot 1.
            return itemSideSatisfied(costA, a, offer.getCostA().getCount())
                    && stackValue(b) >= costValue(offer);
        }
        return false; // not a value-based offer — callers gate on isValueBasedOffer
    }

    /**
     * Vanilla-equivalent item-cost check for the preserved item side of a
     * mixed offer: the cost predicate must match and the stack must hold at
     * least the required count.
     */
    private static boolean itemSideSatisfied(ItemCost cost, ItemStack payment, int requiredCount) {
        return cost.test(payment) && payment.getCount() >= requiredCount;
    }

    // ========================================================================
    // Consumption (pure w.r.t. everything but the two payment stacks)
    // ========================================================================

    /**
     * Consumes payment for a satisfied money-cost offer by mutating the two
     * payment stacks in place (mirroring vanilla {@code take}'s shrink
     * semantics) and returns the raw overpayment to hand back as change.
     * <p>
     * The money side picks the note combination {@code (k1, k2)} —
     * {@code k1} notes from slot 0, {@code k2} from slot 1 — subject to
     * {@code k1·worthA + k2·worthB >= need}, minimizing the tuple
     * {@code (overshoot, k1 + k2, k1)}. That total order is deterministic, so
     * client prediction and server execution always consume identical notes.
     * The search is a brute force over at most 65 × 65 combinations, which
     * guarantees exact combinations are found whenever they exist (this is
     * intentionally NOT {@link CurrencyFitter#fit}, whose single-denomination
     * fitting approximates by rounding — consumption and change must be exact,
     * never voiding player value). The preserved item side of a mixed offer is
     * shrunk exactly like vanilla.
     * <p>
     * Callers must check {@link #satisfied} first; on an unsatisfied payment
     * this method consumes nothing and returns 0.
     *
     * @param offer the money-cost offer
     * @param a     payment slot 0 stack (mutated)
     * @param b     payment slot 1 stack (mutated)
     * @return the raw change owed to the player ({@code >= 0})
     */
    public static long consume(MerchantOffer offer, ItemStack a, ItemStack b) {
        if (!satisfied(offer, a, b)) {
            return 0L; // defensive: mirror vanilla take()'s no-op on unsatisfied
        }
        ItemCost costA = offer.getItemCostA();
        Optional<ItemCost> costB = offer.getItemCostB();
        boolean moneyA = isMoneyCost(costA);
        boolean moneyB = costB.isPresent() && isMoneyCost(costB.get());
        long need = costValue(offer);

        if (moneyA && (costB.isEmpty() || moneyB)) {
            // All-money: both slots participate in the combination search.
            return consumeMoney(a, b, need);
        }
        if (moneyA) {
            // Mixed: money from slot 0 only; item side shrinks vanilla-style.
            long change = consumeMoney(a, ItemStack.EMPTY, need);
            b.shrink(costB.get().count());
            return change;
        }
        // Mixed: item side (slot 0) shrinks by the modified cost count exactly
        // like vanilla take(); money from slot 1 only.
        a.shrink(offer.getCostA().getCount());
        return consumeMoney(ItemStack.EMPTY, b, need);
    }

    /**
     * Picks and removes the optimal note combination covering {@code need}
     * from up to two money stacks (see {@link #consume} for the ordering).
     *
     * @return the raw overpayment ({@code paid − need})
     */
    private static long consumeMoney(ItemStack a, ItemStack b, long need) {
        long worthA = isMoneyStack(a) ? ((MoneyItem) a.getItem()).worth() : 0L;
        long worthB = isMoneyStack(b) ? ((MoneyItem) b.getItem()).worth() : 0L;
        int maxK1 = worthA > 0 ? a.getCount() : 0;
        int maxK2 = worthB > 0 ? b.getCount() : 0;

        int bestK1 = -1;
        int bestK2 = -1;
        long bestPaid = Long.MAX_VALUE;
        for (int k1 = 0; k1 <= maxK1; k1++) {
            for (int k2 = 0; k2 <= maxK2; k2++) {
                long paid = k1 * worthA + k2 * worthB;
                if (paid < need) {
                    continue;
                }
                // Total order (overshoot, k1+k2, k1): strict improvement only,
                // and the ascending scan order makes lower k1+k2 and lower k1
                // win ties naturally.
                if (paid < bestPaid
                        || (paid == bestPaid && k1 + k2 < bestK1 + bestK2)
                        || (paid == bestPaid && k1 + k2 == bestK1 + bestK2 && k1 < bestK1)) {
                    bestPaid = paid;
                    bestK1 = k1;
                    bestK2 = k2;
                }
            }
        }
        if (bestK1 < 0) {
            return 0L; // unreachable when satisfied() held — defensive only
        }
        if (bestK1 > 0) {
            a.shrink(bestK1);
        }
        if (bestK2 > 0) {
            b.shrink(bestK2);
        }
        return bestPaid - need;
    }

    // ========================================================================
    // Change
    // ========================================================================

    /**
     * Converts a raw value into money stacks, greedy largest-denomination
     * first — always <b>exact</b> and, because the BankSystem denomination
     * system is canonical (1/5/10/20/50 pattern per magnitude), also the
     * fewest notes. The 1-raw cent coin guarantees a zero remainder: change is
     * never rounded and player value is never voided.
     *
     * @param changeRaw the raw value to represent ({@code <= 0} yields an
     *                  empty list)
     * @return change stacks, largest denomination first
     */
    public static List<ItemStack> makeChange(long changeRaw) {
        List<ItemStack> out = new ArrayList<>();
        if (changeRaw <= 0) {
            return out;
        }
        List<ItemStack> denominations = moneyDenominationsDesc();
        long remaining = changeRaw;
        for (ItemStack denom : denominations) {
            long worth = ((MoneyItem) denom.getItem()).worth();
            long count = remaining / worth;
            // Split into max-stack-size chunks (defensive — canonical greedy
            // never needs more than 4 notes of one denomination).
            int maxStack = Math.max(1, denom.getMaxStackSize());
            while (count > 0) {
                int stackCount = (int) Math.min(count, maxStack);
                ItemStack stack = denom.copy();
                stack.setCount(stackCount);
                out.add(stack);
                count -= stackCount;
                remaining -= (long) stackCount * worth;
            }
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            // Only possible when the item registry is unpopulated — a state in
            // which money offers cannot exist in the first place.
            StockMarketMod.LOGGER.error("[MoneyPayment] Could not represent change of "
                    + changeRaw + " raw (" + remaining + " raw unrepresented) — "
                    + "money denominations unavailable");
        }
        return out;
    }

    /**
     * Delivers change to the player (the only non-pure method here). Per change
     * stack, largest denomination first:
     * <ol>
     *   <li>merge into a payment slot already holding the same denomination,
     *       then fill an empty payment slot — via
     *       {@link MerchantContainer#setItem}, which re-triggers
     *       {@code updateSellItem} so the trade preview and the shift-click
     *       repeat loop stay correct;</li>
     *   <li>remainder into the player inventory via
     *       {@code Inventory.placeItemBackInInventory}, which itself falls back
     *       to dropping at the player's feet when the inventory is full (the
     *       drop only spawns an entity server-side through the
     *       {@code ServerPlayer} override — the client prediction voids it
     *       visually and the container sync corrects the view).</li>
     * </ol>
     * Runs on both logical sides, exactly like vanilla's payment-slot returns.
     *
     * @param changeRaw the raw change value ({@code <= 0} is a no-op)
     * @param slots     the merchant trade container (payment slots 0 and 1)
     * @param player    the trading player
     */
    public static void deliverChange(long changeRaw, MerchantContainer slots, Player player) {
        if (changeRaw <= 0 || slots == null || player == null) {
            return;
        }
        for (ItemStack note : makeChange(changeRaw)) {
            // 1a. Merge into a payment slot holding the same denomination.
            for (int slot = 0; slot <= 1 && !note.isEmpty(); slot++) {
                ItemStack current = slots.getItem(slot);
                if (!current.isEmpty()
                        && ItemStack.isSameItemSameComponents(current, note)
                        && current.getCount() < current.getMaxStackSize()) {
                    int move = Math.min(note.getCount(),
                            current.getMaxStackSize() - current.getCount());
                    current.grow(move);
                    note.shrink(move);
                    slots.setItem(slot, current); // re-triggers updateSellItem
                }
            }
            // 1b. Place into an empty payment slot (never displaces an item
            // stack — e.g. a mixed offer's preserved book/compass cost).
            for (int slot = 0; slot <= 1 && !note.isEmpty(); slot++) {
                if (slots.getItem(slot).isEmpty()) {
                    slots.setItem(slot, note.copy());
                    note.setCount(0);
                }
            }
            // 2. Inventory (with built-in server-side ground-drop fallback).
            if (!note.isEmpty()) {
                player.getInventory().placeItemBackInInventory(note);
            }
        }
    }

    // ========================================================================
    // Payment-slot top-up (Phase 2 convenience — offer-row click auto-fill)
    // ========================================================================

    /** First player-inventory slot index in the merchant menu (vanilla layout). */
    private static final int MENU_INV_SLOT_START = 3;
    /** One past the last player-inventory slot index (hotbar included). */
    private static final int MENU_INV_SLOT_END = 39;

    /**
     * Tops up the payment slots with money from the player inventory after
     * vanilla's exact auto-fill ({@code MerchantMenu.tryMoveItems}) ran for a
     * money-cost offer. Vanilla only moves stacks matching the offer's exact
     * fitted denomination; this fills the remaining value with whatever
     * denominations the player actually owns.
     * <p>
     * Greedy, one note per step: the largest denomination not exceeding the
     * remaining value that has a merge-compatible payment slot; when no such
     * note exists, a single smallest note that covers the remainder (minimal
     * single-note overshoot — the change system refunds it exactly at trade
     * time). A payment slot is merge-compatible when it is empty or holds the
     * same denomination with room — an item stack already in a slot (e.g. a
     * mixed offer's book) is never displaced. Terminates because every step
     * either reduces the remaining value or runs out of compatible slots
     * (≤ 64 notes per slot).
     * <p>
     * Runs on both logical sides (the client calls {@code tryMoveItems}
     * locally, the server via {@code ServerboundSelectTradePacket}); the
     * algorithm is a deterministic function of the synced menu state, so both
     * sides move identical notes.
     *
     * @param menu           the merchant menu (source of the player-inventory
     *                       slots)
     * @param tradeContainer the trade container (payment slots 0 and 1)
     * @param offer          the clicked offer
     */
    public static void topUpPaymentSlots(AbstractContainerMenu menu, MerchantContainer tradeContainer,
                                         MerchantOffer offer) {
        if (!isValueBasedOffer(offer)) {
            return; // vanilla behavior for non-money offers
        }
        long need = costValue(offer);
        int guard = 0; // hard bound: 2 slots × 64 notes
        while (guard++ <= 128) {
            long have = totalValue(tradeContainer.getItem(0), tradeContainer.getItem(1));
            long remaining = need - have;
            if (remaining <= 0) {
                return; // payment complete
            }
            // Best fitting note: largest worth <= remaining (exact progress),
            // tracked alongside the smallest available overshoot note.
            Slot bestFit = null;
            long bestFitWorth = 0L;
            Slot smallestOver = null;
            long smallestOverWorth = Long.MAX_VALUE;
            for (int i = MENU_INV_SLOT_START; i < MENU_INV_SLOT_END; i++) {
                Slot invSlot = menu.getSlot(i);
                ItemStack stack = invSlot.getItem();
                if (!isMoneyStack(stack) || findCompatiblePaymentSlot(tradeContainer, stack) < 0) {
                    continue;
                }
                long worth = ((MoneyItem) stack.getItem()).worth();
                if (worth <= remaining) {
                    if (worth > bestFitWorth) {
                        bestFitWorth = worth;
                        bestFit = invSlot;
                    }
                } else if (worth < smallestOverWorth) {
                    smallestOverWorth = worth;
                    smallestOver = invSlot;
                }
            }
            Slot source = bestFit != null ? bestFit : smallestOver;
            if (source == null) {
                return; // no usable money or no compatible payment slot left
            }
            moveOneNote(tradeContainer, source);
            if (bestFit == null) {
                return; // the overshoot note completes the payment
            }
        }
    }

    /**
     * Finds a payment slot (0/1) that can accept one note of the given
     * denomination: empty, or same denomination with room. Never displaces a
     * different stack.
     *
     * @return the slot index, or -1 when none is compatible
     */
    private static int findCompatiblePaymentSlot(MerchantContainer tradeContainer, ItemStack note) {
        // Prefer merging over occupying the second slot.
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack current = tradeContainer.getItem(slot);
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, note)
                    && current.getCount() < current.getMaxStackSize()) {
                return slot;
            }
        }
        for (int slot = 0; slot <= 1; slot++) {
            if (tradeContainer.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Moves exactly one note from an inventory slot into a compatible payment slot. */
    private static void moveOneNote(MerchantContainer tradeContainer, Slot source) {
        ItemStack stack = source.getItem();
        int target = findCompatiblePaymentSlot(tradeContainer, stack);
        if (target < 0) {
            return; // pre-checked by the caller — defensive only
        }
        ItemStack current = tradeContainer.getItem(target);
        if (current.isEmpty()) {
            tradeContainer.setItem(target, stack.copyWithCount(1));
        } else {
            current.grow(1);
            tradeContainer.setItem(target, current); // re-triggers updateSellItem
        }
        stack.shrink(1);
        source.setChanged();
    }
}
