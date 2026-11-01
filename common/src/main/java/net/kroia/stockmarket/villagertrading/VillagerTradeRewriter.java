package net.kroia.stockmarket.villagertrading;

import dev.architectury.event.EventResult;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Rewrites villager (and wandering trader) merchant offers from emerald form to
 * the configured stock-market trading currency — but <b>only</b> for offers
 * whose traded item is listed on the stock market. Unlisted items keep their
 * vanilla emerald trades; offers are restored to emerald form when their market
 * disappears or the feature is disabled.
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
    static void processVillager(AbstractVillager villager, VillagerTradeSavedData savedData,
                                VillagerTradePriceTable table) {
        MerchantOffers offers = villager.getOffers(); // server-side lazy generation happens here
        VillagerTradeSavedData.VillagerEntry entry = savedData.getEntry(villager.getUUID());

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

        VillagerTradeSavedData.VillagerEntry finalEntry = entry;
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
     *   <li>traded item has no market price → the offer stays in (or is
     *       <b>restored</b> to) its original emerald form. The snapshot is kept,
     *       so a later re-listing converts the offer again.</li>
     * </ul>
     *
     * @param offers        the villager's live offer list (mutated in place)
     * @param entry         the villager's sidecar entry (mutated in place)
     * @param table         the current, enabled price table
     * @param priceLookup   resolves an ItemStack to its raw market price (0 = no market)
     * @param clampCallback fired once per newly clamped offer index; may be {@code null}
     * @return {@code true} when any offer was rewritten or restored
     */
    public static boolean processOffers(MerchantOffers offers, VillagerTradeSavedData.VillagerEntry entry,
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
                entry.append(new VillagerTradeSavedData.OriginalOffer(live.copy(), false));
            } else {
                entry.append(VillagerTradeSavedData.OriginalOffer.PASSTHROUGH);
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
            VillagerTradeSavedData.OriginalOffer original = entry.get(i);
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
                // The item's market disappeared (or the currency changed so the
                // offer no longer qualifies): restore the vanilla emerald form.
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
    public static void restoreAll(MerchantOffers offers, VillagerTradeSavedData.VillagerEntry entry) {
        int count = Math.min(offers.size(), entry.size());
        for (int i = 0; i < count; i++) {
            VillagerTradeSavedData.OriginalOffer original = entry.get(i);
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
     * Unlisted offers return {@code null} — they keep their vanilla emerald
     * form. All math in raw units.
     * <p>
     * Demand/discount semantics: when the <b>cost</b> side becomes currency
     * (villager sells) the new offer uses {@code priceMultiplier = 0}, which
     * neutralizes both demand scaling and gossip/Hero-of-the-Village discounts
     * (both derive from {@code getPriceMultiplier()} in
     * {@code Villager.updateSpecialPrices()}, which runs after this rewrite) —
     * reputation must not grind the price below the sell margin. When the cost
     * side stays an item (villager buys), the original multiplier and the live
     * demand are preserved so discounts only reduce the item count handed over.
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
     * currency. Only applies when the sold item has a market price; otherwise
     * returns {@code null} and the offer keeps its vanilla emerald form.
     */
    private static @Nullable RewriteResult rewriteSellOffer(MerchantOffer original, MerchantOffer live,
                                                            VillagerTradePriceTable table,
                                                            ToLongFunction<ItemStack> priceLookup) {
        ItemCost costA = original.getItemCostA();
        Optional<ItemCost> costB = original.getItemCostB();
        ItemStack result = original.getResult();

        boolean costAEmerald = costA.itemStack().is(Items.EMERALD);
        boolean costBEmerald = costB.isPresent() && costB.get().itemStack().is(Items.EMERALD);

        // The sold item MUST be market-listed (component-aware via the price
        // lookup) — unlisted items stay on their vanilla emerald pricing.
        long resultPriceRaw = priceLookup.applyAsLong(result);
        if (resultPriceRaw <= 0) {
            return null;
        }
        long basisRaw = resultPriceRaw * result.getCount();
        long moneyRaw = Math.max(1, Math.round(basisRaw * (double) table.sellMargin()));

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

        // priceMultiplier = 0: money-cost offers are immune to demand and
        // reputation discounts (see method javadoc).
        MerchantOffer offer = new MerchantOffer(newCostA, newCostB, result.copy(),
                live.getUses(), original.getMaxUses(), original.getXp(), 0.0f, live.getDemand());
        return new RewriteResult(offer, fit.clamped());
    }

    /**
     * Villager buys items for emeralds → the emerald result stack becomes
     * currency. Only applies when <b>every</b> item cost has a market price;
     * otherwise returns {@code null} and the offer keeps its vanilla emerald
     * form.
     */
    private static @Nullable RewriteResult rewriteBuyOffer(MerchantOffer original, MerchantOffer live,
                                                           VillagerTradePriceTable table,
                                                           ToLongFunction<ItemStack> priceLookup) {
        ItemCost costA = original.getItemCostA();
        Optional<ItemCost> costB = original.getItemCostB();

        // ALL item costs must be market-listed — a single unlisted ingredient
        // keeps the whole offer on its vanilla emerald pricing.
        long basisRaw;
        long priceA = priceLookup.applyAsLong(costA.itemStack());
        if (priceA <= 0) {
            return null;
        }
        basisRaw = priceA * costA.count();
        if (costB.isPresent()) {
            long priceB = priceLookup.applyAsLong(costB.get().itemStack());
            if (priceB <= 0) {
                return null;
            }
            basisRaw += priceB * costB.get().count();
        }
        long moneyRaw = Math.max(1, Math.round(basisRaw * (double) table.buyMargin()));

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
        return stack -> {
            if (stack == null || stack.isEmpty()) {
                return 0L;
            }
            ItemID itemID = ItemIDManager.getItemID(stack);
            if (!itemID.isValid()) {
                return 0L;
            }
            return table.getPriceRaw(itemID.getShort());
        };
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
    private static ItemCost toItemCost(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return new ItemCost(stack.getItem(), stack.getCount());
        }
        DataComponentPredicate.Builder builder = DataComponentPredicate.builder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            // Removed components (empty Optional) cannot be expressed in the
            // predicate — only added/changed components are expected.
            entry.getValue().ifPresent(value -> expect(builder, entry.getKey(), value));
        }
        return new ItemCost(stack.getItemHolder(), stack.getCount(), builder.build());
    }

    /** Generic helper to funnel a wildcard component entry into the predicate builder. */
    @SuppressWarnings("unchecked")
    private static <T> void expect(DataComponentPredicate.Builder builder,
                                   DataComponentType<T> type, Object value) {
        builder.expect(type, (T) value);
    }
}
