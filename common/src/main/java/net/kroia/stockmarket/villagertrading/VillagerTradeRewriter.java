package net.kroia.stockmarket.villagertrading;

import dev.architectury.event.EventResult;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager; // mc<=1.21.10
//import net.minecraft.world.entity.npc.villager.AbstractVillager; // mc>=1.21.11
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Rewrites villager (and wandering trader) merchant offers from emerald form to
 * the configured stock-market trading currency — but <b>only</b> for offers
 * whose traded item is listed on the stock market. Unlisted items keep their
 * vanilla emerald trades, unless {@code VillagerTrading.REPLACE_EMERALDS_FOR_UNLISTED}
 * is on — then they are converted without margins at the rounded emerald
 * market price, or at the rounded preset default price with
 * {@code USE_PRESET_PRICES_FOR_VILLAGERS} (see {@link #convertedPriceRaw}).
 * Offers are restored to emerald form when their pricing basis disappears or
 * the feature/option is disabled. Discounts on rewritten offers are capped at
 * {@link #MAX_DISCOUNT_PERCENT} ({@link #capDiscounts}).
 * <p>
 * Villager trades never touch the stock market: the rewriter only reads
 * prices, it places no orders and changes no net flow, order book or ledger.
 * <p>
 * Registered once on Architectury's {@code InteractionEvent.INTERACT_ENTITY},
 * which fires server-side in the interaction packet handler <i>before</i>
 * {@code Villager.mobInteract} opens the trade menu — so the player always sees
 * already-rewritten offers. The event fires on both logical sides and once per
 * hand; the handler guards accordingly and the rewrite itself is idempotent.
 * <p>
 * The heavy lifting is done by pure static functions
 * ({@link #classify}, {@link #rewriteOffer}, {@link #restoreOffer},
 * {@link #processOffers}) so the in-game test suites can drive them with
 * synthetic offers, entries and price tables.
 */
public final class VillagerTradeRewriter {

    private static StockMarketModBackend.ServerInstances BACKEND;

    /**
     * Injects the per-server backend (established static-backend pattern).
     * Called from {@code StockMarketModBackend.onBankSystemSetupComplete} on
     * master and slave alike.
     *
     * @param backend the current server instances (or {@code null} to detach)
     */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND = backend;
    }

    private VillagerTradeRewriter() {
    }

    /** Classification of a merchant offer relative to the repricing rules. */
    public enum OfferKind {
        /** Villager sells an item for emeralds → costs become currency, priceMultiplier 0. */
        VILLAGER_SELLS,
        /** Villager buys items for emeralds → result becomes currency, multiplier preserved. */
        VILLAGER_BUYS,
        /** Offer is never touched (no emerald side, trades the currency itself, ...). */
        PASS
    }

    /**
     * Result of rewriting a single offer.
     *
     * @param offer   the rewritten currency-form offer
     * @param clamped whether the currency fit was capacity-clamped
     */
    public record RewriteResult(MerchantOffer offer, boolean clamped) {
    }

    /**
     * Callback fired the first time an offer's currency fit is capacity-clamped
     * (per villager and offer index). Production logs a warning; tests may pass
     * {@code null}.
     */
    @FunctionalInterface
    public interface ClampCallback {
        void onClamped(int offerIndex);
    }

    /**
     * {@code InteractionEvent.INTERACT_ENTITY} handler. Never cancels the event —
     * it only rewrites/restores the villager's offers in place before vanilla
     * opens the trade menu.
     *
     * @param player the interacting player
     * @param entity the clicked entity
     * @param hand   the hand used
     * @return always {@link EventResult#pass()}
     */
    public static EventResult onInteractEntity(Player player, Entity entity, InteractionHand hand) {
        if (entity == null || entity.level().isClientSide()) {
            return EventResult.pass();
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return EventResult.pass(); // event fires once per hand — handle main hand only
        }
        if (!(entity instanceof AbstractVillager villager)) {
            return EventResult.pass();
        }
        StockMarketModBackend.ServerInstances backend = BACKEND;
        if (backend == null || backend.VILLAGER_TRADE_MANAGER == null) {
            return EventResult.pass();
        }
        if (villager.getTradingPlayer() != null) {
            return EventResult.pass(); // never mutate offers while a trade menu is open
        }
        VillagerTradePriceTable table = backend.VILLAGER_TRADE_MANAGER.getTable();
        if (table == null) {
            return EventResult.pass(); // slave before first table broadcast, or master before setup
        }
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) {
            return EventResult.pass();
        }
        try {
            processVillager(villager, backend.VILLAGER_TRADE_MANAGER.getSavedData(server), table);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[VillagerTradeRewriter] Failed to process villager "
                    + villager.getUUID(), e);
        }
        return EventResult.pass();
    }

    /**
     * Full per-villager flow: restore when disabled, otherwise snapshot new
     * offers and reprice when the table version changed.
     *
     * @param villager  the interacted villager (server side)
     * @param savedData the sidecar store
     * @param table     the current price table (non-null)
     */
    static void processVillager(AbstractVillager villager, VillagerTradeData savedData,
                                VillagerTradePriceTable table) {
        MerchantOffers offers = villager.getOffers(); // server-side lazy generation happens here
        VillagerTradeData.VillagerEntry entry = savedData.getEntry(villager.getUUID());

        if (!table.enabled()) {
            // Feature disabled (or currency unresolvable): lazily restore this
            // villager's original offers and forget about it.
            if (entry != null) {
                restoreAll(offers, entry);
                savedData.removeEntry(villager.getUUID());
            }
            return;
        }

        if (offers.isEmpty() && entry == null) {
            return; // baby villager / no profession — nothing to track
        }

        long nowGameTime = villager.level().getGameTime();
        if (entry == null) {
            entry = savedData.getOrCreateEntry(villager.getUUID(), nowGameTime);
        }

        VillagerTradeData.VillagerEntry finalEntry = entry;
        boolean changed = processOffers(offers, entry, table, tableLookup(table),
                offerIndex -> StockMarketMod.LOGGER.warn(
                        "[VillagerTradeRewriter] Offer " + offerIndex + " of villager " + villager.getUUID()
                                + " could not fully fit its price into the available currency slots"
                                + " (fitted value was capped in the player's favor)."
                                + " Consider configuring a finer-grained currency item."));
        finalEntry.setLastSeenGameTime(nowGameTime);
        // setDirty() unconditionally: lastSeenGameTime changed, and it is only a flag.
        savedData.setDirty();
        if (changed) {
            StockMarketMod.LOGGER.debug("[VillagerTradeRewriter] Repriced offers of villager "
                    + villager.getUUID() + " (table version " + table.version() + ")");
        }
    }

    /**
     * Pure core of the rewrite flow (testable without an entity): truncates a
     * stale entry, snapshots new tail offers (villager level-up), and — when the
     * price-table version changed or new offers appeared — brings every snapshot
     * offer into the state its market listing dictates:
     * <ul>
     *   <li>traded item has a market price → rewrite to currency form
     *       (also covers the market-created-later transition);</li>
     *   <li>traded item has no market price but the emerald-replacement option
     *       is on → rewrite to currency form at the rounded emerald price or
     *       preset price, without margins ({@link #convertedPriceRaw});</li>
     *   <li>otherwise → the offer stays in (or is <b>restored</b> to) its
     *       original emerald form. The snapshot is kept, so a later re-listing
     *       (or re-enabled option) converts the offer again.</li>
     * </ul>
     *
     * @param offers        the villager's live offer list (mutated in place)
     * @param entry         the villager's sidecar entry (mutated in place)
     * @param table         the current, enabled price table
     * @param priceLookup   resolves an ItemStack to its raw market price (0 = no market)
     * @param clampCallback fired once per newly clamped offer index; may be {@code null}
     * @return {@code true} when any offer was rewritten or restored
     */
    public static boolean processOffers(MerchantOffers offers, VillagerTradeData.VillagerEntry entry,
                                        VillagerTradePriceTable table, ToLongFunction<ItemStack> priceLookup,
                                        @Nullable ClampCallback clampCallback) {
        // Another mod removed offers → realign by truncating the snapshot list.
        if (offers.size() < entry.size()) {
            StockMarketMod.LOGGER.debug("[VillagerTradeRewriter] Live offer list shrank ("
                    + offers.size() + " < " + entry.size() + ") — truncating snapshots");
            entry.truncate(offers.size());
        }

        // Snapshot newly appeared tail offers (level-up) in their emerald form.
        boolean tailAppended = false;
        for (int i = entry.size(); i < offers.size(); i++) {
            MerchantOffer live = offers.get(i);
            if (classify(live, table.currency()) != OfferKind.PASS) {
                // Not rewritten yet — the live offer is still the emerald form.
                entry.append(new VillagerTradeData.OriginalOffer(live.copy(), false));
            } else {
                entry.append(VillagerTradeData.OriginalOffer.PASSTHROUGH);
            }
            tailAppended = true;
        }

        // Reprice only when stale — repeated interactions with the same table
        // version are no-ops (idempotency for the double-firing event).
        if (entry.pricedVersion() == table.version() && !tailAppended) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < entry.size(); i++) {
            VillagerTradeData.OriginalOffer original = entry.get(i);
            if (original.isPassthrough()) {
                continue;
            }
            RewriteResult result = rewriteOffer(original.offer(), offers.get(i), table, priceLookup);
            if (result != null) {
                // Market-listed item (possibly newly listed): currency form.
                offers.set(i, result.offer());
                original.setRewritten(true);
                changed = true;
                if (result.clamped() && !entry.isClampFlagged(i)) {
                    entry.flagClamped(i);
                    if (clampCallback != null) {
                        clampCallback.onClamped(i);
                    }
                }
            } else if (original.isRewritten()) {
                // The item's market disappeared, the emerald conversion no longer
                // applies (option off / emerald market gone), or the currency
                // changed so the offer no longer qualifies: restore the vanilla
                // emerald form.
                // The snapshot stays in the sidecar so a re-listed market
                // converts the offer again on a later reprice.
                offers.set(i, restoreOffer(original.offer(), offers.get(i)));
                original.setRewritten(false);
                changed = true;
            }
            // else: emerald-form offer for an unlisted item — leave untouched.
        }
        entry.setPricedVersion(table.version());
        return changed;
    }

    /**
     * Restores all snapshot offers back into the live offer list (emerald form),
     * keeping the live {@code uses} so partially used offers stay partially used.
     *
     * @param offers the villager's live offer list (mutated in place)
     * @param entry  the villager's sidecar entry
     */
    public static void restoreAll(MerchantOffers offers, VillagerTradeData.VillagerEntry entry) {
        int count = Math.min(offers.size(), entry.size());
        for (int i = 0; i < count; i++) {
            VillagerTradeData.OriginalOffer original = entry.get(i);
            // Only offers currently in currency form need restoring — snapshots
            // that were never (or are no longer) rewritten are already emerald.
            if (original.isPassthrough() || !original.isRewritten()) {
                continue;
            }
            offers.set(i, restoreOffer(original.offer(), offers.get(i)));
            original.setRewritten(false);
        }
    }

    /**
     * Classifies an offer relative to the repricing rules.
     * <p>
     * Pass-through cases (never touched): the configured currency is emerald
     * itself, any side of the offer already trades the currency item, offers
     * with emeralds on both sides (cost and result), and offers without any
     * emerald side.
     *
     * @param offer    the offer to classify (base costs are inspected, without
     *                 demand/discount adjustments)
     * @param currency the trading-currency item template
     * @return the classification
     */
    public static OfferKind classify(MerchantOffer offer, ItemStack currency) {
        if (currency == null || currency.isEmpty() || currency.is(Items.EMERALD)) {
            return OfferKind.PASS;
        }
        ItemStack costA = offer.getBaseCostA();
        ItemStack costB = offer.getCostB();
        ItemStack result = offer.getResult();

        // Offers that already involve the currency item are never rewritten
        // (they may be our own rewritten offers of an orphaned villager).
        if (isSameAsCurrency(costA, currency) || isSameAsCurrency(costB, currency)
                || isSameAsCurrency(result, currency)) {
            return OfferKind.PASS;
        }

        boolean costAEmerald = costA.is(Items.EMERALD);
        boolean costBEmerald = !costB.isEmpty() && costB.is(Items.EMERALD);
        boolean resultEmerald = result.is(Items.EMERALD);

        if ((costAEmerald || costBEmerald) && !resultEmerald && !result.isEmpty()) {
            return OfferKind.VILLAGER_SELLS;
        }
        if (resultEmerald && !costAEmerald && !costBEmerald) {
            return OfferKind.VILLAGER_BUYS;
        }
        return OfferKind.PASS;
    }

    /**
     * Rewrites a single offer from its emerald-form original into currency form,
     * <b>only</b> when the traded item is listed on the stock market: a
     * villager-sells offer requires a market price for the result item; a
     * villager-buys offer requires a market price for <b>every</b> item cost.
     * Unlisted offers are converted when
     * {@link VillagerTradePriceTable#replaceEmeraldsForUnlisted()} is set, at the
     * price of {@link #convertedPriceRaw} (no margins); otherwise they return
     * {@code null} and keep their vanilla emerald form. All math in raw units.
     * <p>
     * Demand/discount semantics: a listed villager-sells offer uses
     * {@code priceMultiplier = 0}, which removes demand scaling and reputation
     * discounts (Hero of the Village still applies, it does not depend on the
     * multiplier). Converted villager-sells offers and all villager-buys offers
     * keep the original multiplier and the live demand, so vanilla discounts
     * apply. For every rewritten offer the total discount is capped at
     * {@link #MAX_DISCOUNT_PERCENT} by {@link #capDiscounts}, which runs after
     * {@code Villager.updateSpecialPrices()}.
     * <p>
     * {@code uses} and {@code demand} are copied from the <b>live</b> offer so
     * partially used offers stay partially used; {@code maxUses}/{@code xp} come
     * from the original. Known limitation: the vanilla constructor forces
     * {@code rewardExp = true} (all vanilla offers reward exp anyway).
     *
     * @param original    the emerald-form original offer (snapshot)
     * @param live        the current live offer (source of uses/demand)
     * @param table       the current price table
     * @param priceLookup resolves an ItemStack to its raw market price (0 = no market)
     * @return the rewrite result, or {@code null} when the offer classifies as
     *         pass-through or its traded item(s) have no market price
     */
    public static @Nullable RewriteResult rewriteOffer(MerchantOffer original, MerchantOffer live,
                                                       VillagerTradePriceTable table,
                                                       ToLongFunction<ItemStack> priceLookup) {
        OfferKind kind = classify(original, table.currency());
        switch (kind) {
            case VILLAGER_SELLS:
                return rewriteSellOffer(original, live, table, priceLookup);
            case VILLAGER_BUYS:
                return rewriteBuyOffer(original, live, table, priceLookup);
            default:
                return null;
        }
    }

    /**
     * Villager sells an item for emeralds → the emerald cost slots become
     * currency. Priced from the sold item's market (with the sell margin);
     * without one, converted by {@link #convertedPriceRaw} if the replacement
     * option is on. Otherwise returns {@code null} and the offer keeps its
     * vanilla emerald form.
     */
    private static @Nullable RewriteResult rewriteSellOffer(MerchantOffer original, MerchantOffer live,
                                                            VillagerTradePriceTable table,
                                                            ToLongFunction<ItemStack> priceLookup) {
        ItemCost costA = original.getItemCostA();
        Optional<ItemCost> costB = original.getItemCostB();
        ItemStack result = original.getResult();

        boolean costAEmerald = costA.itemStack().is(Items.EMERALD);
        boolean costBEmerald = costB.isPresent() && costB.get().itemStack().is(Items.EMERALD);

        // A market-listed sold item (component-aware via the price lookup) is
        // priced from its own market. Unlisted items stay on their vanilla
        // emerald pricing — unless the emerald-replacement option converts the
        // emerald cost via the emerald market.
        long moneyRaw;
        float priceMultiplier;
        long resultPriceRaw = priceLookup.applyAsLong(result);
        if (resultPriceRaw > 0) {
            // Listed item: market price x sell margin; multiplier 0 (no demand/reputation).
            moneyRaw = Math.max(1, Math.round(resultPriceRaw * result.getCount() * (double) table.sellMargin()));
            priceMultiplier = 0.0f;
        } else {
            // Converted (unlisted) item: rounded reference price, no margin.
            int emeralds = (costAEmerald ? costA.count() : 0) + (costBEmerald ? costB.get().count() : 0);
            moneyRaw = convertedPriceRaw(table, priceLookup, emeralds, result);
            if (moneyRaw <= 0) {
                return null;
            }
            // Original multiplier: vanilla demand and reputation discounts apply,
            // the total discount is capped by capDiscount().
            priceMultiplier = original.getPriceMultiplier();
        }

        // Currency may only occupy cost slots not held by a non-emerald item
        // (treasure map's compass / enchanted book's book stay put).
        int freeSlots = (costAEmerald ? 1 : 0) + (costB.isEmpty() || costBEmerald ? 1 : 0);
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(moneyRaw, freeSlots, table.currency());
        if (fit.stacks().isEmpty()) {
            return null; // defensive: no valid currency representation
        }

        ItemCost newCostA;
        Optional<ItemCost> newCostB;
        if (costAEmerald) {
            newCostA = toItemCost(fit.stacks().get(0));
            if (costB.isPresent() && !costBEmerald) {
                newCostB = costB; // preserve the original item cost (book/compass)
            } else {
                newCostB = fit.stacks().size() > 1
                        ? Optional.of(toItemCost(fit.stacks().get(1)))
                        : Optional.empty();
            }
        } else {
            // costA is the item side, costB holds the emeralds → single free slot.
            newCostA = costA;
            newCostB = Optional.of(toItemCost(fit.stacks().get(0)));
        }

        MerchantOffer offer = new MerchantOffer(newCostA, newCostB, result.copy(),
                live.getUses(), original.getMaxUses(), original.getXp(), priceMultiplier, live.getDemand());
        return new RewriteResult(offer, fit.clamped());
    }

    /**
     * Villager buys items for emeralds → the emerald result stack becomes
     * currency. Priced from the item markets when <b>every</b> item cost has a
     * market price (with the buy margin); otherwise converted by
     * {@link #convertedPriceRaw} if the replacement option is on. Otherwise
     * returns {@code null} and the offer keeps its vanilla emerald form.
     */
    private static @Nullable RewriteResult rewriteBuyOffer(MerchantOffer original, MerchantOffer live,
                                                           VillagerTradePriceTable table,
                                                           ToLongFunction<ItemStack> priceLookup) {
        ItemCost costA = original.getItemCostA();
        Optional<ItemCost> costB = original.getItemCostB();

        // ALL item costs must be market-listed to price from the item markets —
        // a single unlisted ingredient keeps the whole offer on its vanilla
        // emerald pricing, unless the emerald-replacement option converts the
        // emerald payout via the emerald market.
        long moneyRaw;
        long priceA = priceLookup.applyAsLong(costA.itemStack());
        long priceB = costB.isPresent() ? priceLookup.applyAsLong(costB.get().itemStack()) : 0L;
        if (priceA > 0 && (costB.isEmpty() || priceB > 0)) {
            // Listed items: market prices x buy margin.
            long basisRaw = priceA * costA.count() + (costB.isPresent() ? priceB * costB.get().count() : 0L);
            moneyRaw = Math.max(1, Math.round(basisRaw * (double) table.buyMargin()));
        } else {
            // Converted (unlisted) offer: rounded reference price(s), no margin.
            ItemStack[] costs = costB.isPresent()
                    ? new ItemStack[]{costA.itemStack(), costB.get().itemStack()}
                    : new ItemStack[]{costA.itemStack()};
            moneyRaw = convertedPriceRaw(table, priceLookup, original.getResult().getCount(), costs);
            if (moneyRaw <= 0) {
                return null;
            }
        }

        // The payout must fit into the single result stack.
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(moneyRaw, 1, table.currency());
        if (fit.stacks().isEmpty()) {
            return null; // defensive: no valid currency representation
        }

        // Item costs preserved as-is; original priceMultiplier and live demand kept
        // so reputation discounts only reduce the item count handed over.
        MerchantOffer offer = new MerchantOffer(costA, costB, fit.stacks().get(0),
                live.getUses(), original.getMaxUses(), original.getXp(),
                original.getPriceMultiplier(), live.getDemand());
        return new RewriteResult(offer, fit.clamped());
    }

    /**
     * Money price (raw units) of a converted offer, i.e. an offer whose traded
     * item has no market ({@code VillagerTrading.REPLACE_EMERALDS_FOR_UNLISTED}).
     * No buy/sell margin is applied (the market spread does not concern villagers).
     * <ul>
     *   <li>Preset prices on ({@link VillagerTradePriceTable#usePresetPrices()}):
     *       sum over {@code items} of {@code rounded preset price × count}. If any
     *       item has no preset price the offer is not converted; there is
     *       deliberately no fallback to the emerald price.</li>
     *   <li>Otherwise: {@code emeralds × rounded emerald market price}; not
     *       converted without an emerald market.</li>
     * </ul>
     *
     * @param table       the current price table (option flags, preset prices)
     * @param priceLookup resolves an ItemStack to its raw market price (0 = no market)
     * @param emeralds    emerald count of the offer's emerald side
     * @param items       the traded item stacks (with counts) valued by the preset prices
     * @return the raw money price (&ge; 1), or {@code 0} when the offer keeps its emerald form
     */
    private static long convertedPriceRaw(VillagerTradePriceTable table, ToLongFunction<ItemStack> priceLookup,
                                          int emeralds, ItemStack... items) {
        if (!table.replaceEmeraldsForUnlisted()) {
            return 0L;
        }
        if (table.usePresetPrices()) {
            ToLongFunction<ItemStack> presetLookup = presetLookup(table);
            long total = 0L;
            for (ItemStack item : items) {
                long presetRaw = presetLookup.applyAsLong(item);
                if (presetRaw <= 0) {
                    return 0L; // no preset price: the trade stays in emeralds
                }
                total += roundReferencePriceRaw(presetRaw) * item.getCount();
            }
            return total;
        }
        long emeraldRaw = priceLookup.applyAsLong(new ItemStack(Items.EMERALD));
        if (emeraldRaw <= 0) {
            return 0L; // no emerald market: the trade stays in emeralds
        }
        return roundReferencePriceRaw(emeraldRaw) * emeralds;
    }

    /**
     * Rounds a reference price to two significant digits (half up), so converted
     * villager offers do not change with every small market move. Works on raw
     * units (100 raw = 1.00): 1234.00 → 1200.00, 87.00 → 87.00, 0.54 → 0.54
     * (raw values below 100 have at most two digits and stay as they are; a
     * display price like 0.537 does not exist in raw units).
     * Never returns 0: the result is at least 1 raw, the smallest money unit.
     *
     * @param raw the reference price in raw units
     * @return the rounded price in raw units, at least 1
     */
    public static long roundReferencePriceRaw(long raw) {
        if (raw < 100) {
            return Math.max(1L, raw);
        }
        long factor = 1L;
        while (raw / factor >= 100) {
            factor *= 10;
        }
        return (raw + factor / 2) / factor * factor;
    }

    /**
     * Largest total discount (reputation, Hero of the Village, cured zombie)
     * allowed on a rewritten offer, in percent of its undiscounted cost count.
     */
    public static final int MAX_DISCOUNT_PERCENT = 20;

    /**
     * Caps the discount vanilla gave an offer. Vanilla charges
     * {@code base + demandBonus + specialPriceDiff} items for the first cost
     * ({@code MerchantOffer.getModifiedCostCount}), where
     * {@code demandBonus = max(0, floor(base × demand × priceMultiplier))}.
     * {@code Villager.updateSpecialPrices} lowers {@code specialPriceDiff} by
     * {@code floor(reputation × priceMultiplier)} (gossip, including cured
     * zombies) and by the Hero of the Village effect
     * ({@code max(1, floor((0.3 + 0.0625 × amplifier) × base))}, independent of
     * the multiplier). This raises {@code specialPriceDiff} to at least
     * {@code -floor((base + demandBonus) × MAX_DISCOUNT_PERCENT / 100)}.
     * A positive {@code specialPriceDiff} (bad reputation) is left alone.
     * <p>
     * The cap is in whole cost items, so a cost of fewer than five items (e.g.
     * one money note) gets no discount at all.
     *
     * @param offer the offer, after {@code updateSpecialPrices} (mutated in place)
     */
    public static void capDiscount(MerchantOffer offer) {
        int base = offer.getBaseCostA().getCount();
        int demandBonus = Math.max(0, Mth.floor((float) (base * offer.getDemand()) * offer.getPriceMultiplier()));
        int maxDiscount = (base + demandBonus) * MAX_DISCOUNT_PERCENT / 100;
        if (offer.getSpecialPriceDiff() < -maxDiscount) {
            offer.setSpecialPriceDiff(-maxDiscount);
        }
    }

    /**
     * Applies {@link #capDiscount} to every offer of the villager that this
     * rewriter converted to currency form (sidecar flag {@code rewritten}).
     * Called by {@code VillagerMixin} at the end of
     * {@code Villager.updateSpecialPrices}, right after vanilla applied the
     * discounts for the player opening the trade menu. Offers still in emerald
     * form keep their full vanilla discounts.
     *
     * @param villager the villager whose trade menu is opening (server side)
     */
    public static void capDiscounts(AbstractVillager villager) {
        StockMarketModBackend.ServerInstances backend = BACKEND;
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (backend == null || backend.VILLAGER_TRADE_MANAGER == null || server == null) {
            return;
        }
        VillagerTradeData.VillagerEntry entry =
                backend.VILLAGER_TRADE_MANAGER.getSavedData(server).getEntry(villager.getUUID());
        if (entry == null) {
            return;
        }
        MerchantOffers offers = villager.getOffers();
        int count = Math.min(offers.size(), entry.size());
        for (int i = 0; i < count; i++) {
            VillagerTradeData.OriginalOffer original = entry.get(i);
            if (!original.isPassthrough() && original.isRewritten()) {
                capDiscount(offers.get(i));
            }
        }
    }

    /**
     * Rebuilds the original emerald-form offer, keeping the live {@code uses}
     * (partially used offers stay partially used) and live {@code demand}.
     *
     * @param original the emerald-form snapshot
     * @param live     the current (currency-form) live offer
     * @return the restored offer
     */
    public static MerchantOffer restoreOffer(MerchantOffer original, MerchantOffer live) {
        return new MerchantOffer(original.getItemCostA(), original.getItemCostB(),
                original.getResult().copy(), live.getUses(), original.getMaxUses(),
                original.getXp(), original.getPriceMultiplier(), live.getDemand());
    }

    /**
     * Production price lookup: resolves a stack to its BankSystem ItemID
     * (component-aware, works on master and slave — slaves receive the ItemID
     * table via BankSystem's sync) and looks the price up in the table.
     *
     * @param table the current price table
     * @return a lookup returning the raw price, or 0 when the item has no market
     */
    public static ToLongFunction<ItemStack> tableLookup(VillagerTradePriceTable table) {
        return stack -> priceById(stack, table::getPriceRaw);
    }

    /**
     * Preset price lookup: like {@link #tableLookup}, but reads the preset
     * default prices the master put into the table
     * ({@link VillagerTradePriceTable#getPresetPriceRaw}).
     *
     * @param table the current price table
     * @return a lookup returning the raw preset price, or 0 when the item has no preset
     */
    public static ToLongFunction<ItemStack> presetLookup(VillagerTradePriceTable table) {
        return stack -> priceById(stack, table::getPresetPriceRaw);
    }

    /** Resolves a stack's ItemID short and looks its price up; 0 for empty or unknown stacks. */
    private static long priceById(@Nullable ItemStack stack, ShortPriceLookup byIdShort) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        ItemID itemID = ItemIDManager.getItemID(stack);
        if (!itemID.isValid()) {
            return 0L;
        }
        return byIdShort.priceRaw(itemID.getShort());
    }

    /** A price map lookup by ItemID short (the table's getters). */
    @FunctionalInterface
    private interface ShortPriceLookup {
        long priceRaw(short itemIdShort);
    }

    /** Count-insensitive, component-sensitive comparison against the currency template. */
    private static boolean isSameAsCurrency(ItemStack stack, ItemStack currency) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, currency);
    }

    /**
     * Converts a currency stack into an {@link ItemCost}. Currencies without
     * component patches use the plain item+count constructor; component-carrying
     * currencies get a predicate expecting exactly those components, so the
     * player's payment items must match them (correct behavior for
     * component-identified currency items).
     *
     * @param stack the currency stack (item, components, count)
     * @return the equivalent ItemCost
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemCost toItemCost(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return new ItemCost(stack.getItem(), stack.getCount());
        }
        // withComponents keeps the predicate builder type implicit: it is
        // DataComponentPredicate.Builder up to 1.21.4 and
        // DataComponentExactPredicate.Builder from 1.21.5, so this compiles on every version.
        return new ItemCost(stack.getItem(), stack.getCount()).withComponents(builder -> {
            // Removed components cannot be expressed in the predicate; only the
            // added/changed ones are expected. split() exists on every version,
            // entrySet() is gone in 26.3.
            for (TypedDataComponent<?> component : patch.split().added()) {
                builder.expect((DataComponentType) component.type(), component.value());
            }
            return builder;
        });
    }
}
