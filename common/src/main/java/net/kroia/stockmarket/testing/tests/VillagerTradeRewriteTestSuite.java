package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.villagertrading.VillagerTradePriceTable;
import net.kroia.stockmarket.villagertrading.VillagerTradeRewriter;
import net.kroia.stockmarket.villagertrading.VillagerTradeSavedData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToLongFunction;

/**
 * Master-only in-game tests for the villager trade rewrite flow
 * ({@link VillagerTradeRewriter#processOffers},
 * {@link VillagerTradeRewriter#restoreAll},
 * {@link VillagerTradeRewriter#classify},
 * {@link VillagerTradeRewriter#tableLookup}) using synthetic
 * {@link MerchantOffers} and price tables — no live villager entity needed.
 */
public class VillagerTradeRewriteTestSuite extends TestSuite {

    private static final float BUY_MARGIN = 0.8f;
    private static final float SELL_MARGIN = 1.2f;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VILLAGER_REWRITE;
    }

    @Override
    public void registerTests() {
        addTest("sell_offer_rewritten_to_currency", this::test_sell_offer_rewritten_to_currency);
        addTest("buy_offer_rewritten_result", this::test_buy_offer_rewritten_result);
        addTest("idempotent_same_version", this::test_idempotent_same_version);
        addTest("version_bump_reprices_from_original", this::test_version_bump_reprices_from_original);
        addTest("tail_append_after_level_up", this::test_tail_append_after_level_up);
        addTest("book_offer_keeps_item_cost_slot", this::test_book_offer_keeps_item_cost_slot);
        addTest("map_offer_keeps_compass_cost", this::test_map_offer_keeps_compass_cost);
        addTest("no_market_offer_stays_emerald", this::test_no_market_offer_stays_emerald);
        addTest("market_removed_restores_emerald", this::test_market_removed_restores_emerald);
        addTest("market_created_later_converts", this::test_market_created_later_converts);
        addTest("partially_used_offer_preserved", this::test_partially_used_offer_preserved);
        addTest("restore_on_disabled", this::test_restore_on_disabled);
        addTest("passthrough_classification", this::test_passthrough_classification);
        addTest("item_cost_accepts_plain_currency", this::test_item_cost_accepts_plain_currency);
        addTest("clamp_warns_once_per_offer", this::test_clamp_warns_once_per_offer);
        addTest("table_lookup_resolves_item_ids", this::test_table_lookup_resolves_item_ids);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static ItemStack moneyCurrency() {
        return new ItemStack(BankSystemItems.MONEY.get());
    }

    private static VillagerTradePriceTable table(long version, boolean enabled, ItemStack currency,
                                                 Map<Short, Long> prices) {
        return new VillagerTradePriceTable(version, enabled, currency,
                BUY_MARGIN, SELL_MARGIN, prices);
    }

    private static VillagerTradePriceTable moneyTable(long version) {
        return table(version, true, moneyCurrency(), Map.of());
    }

    /** Villager sells: 5 emeralds → 1 iron ingot. */
    private static MerchantOffer sellIronOffer() {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 2, 0.05f, 0);
    }

    /** Villager buys: 15 coal → 1 emerald. */
    private static MerchantOffer buyCoalOffer() {
        return new MerchantOffer(new ItemCost(Items.COAL, 15), Optional.empty(),
                new ItemStack(Items.EMERALD), 0, 16, 2, 0.05f, 0);
    }

    /**
     * Synthetic market lookup: iron 200 raw, coal 100 raw, enchanted book
     * 1000 raw, map 500 raw — everything else unpriced (no market).
     */
    private static ToLongFunction<ItemStack> syntheticLookup() {
        return stack -> {
            if (stack.is(Items.IRON_INGOT)) return 200L;
            if (stack.is(Items.COAL)) return 100L;
            if (stack.is(Items.ENCHANTED_BOOK)) return 1000L;
            if (stack.is(Items.MAP)) return 500L;
            return 0L;
        };
    }

    /** Sums the raw value of the given currency stacks (money worth, or 100/item). */
    private static long rawValue(ItemStack... stacks) {
        long total = 0;
        for (ItemStack stack : stacks) {
            long perItem = stack.getItem() instanceof MoneyItem money ? money.worth() : 100L;
            total += perItem * stack.getCount();
        }
        return total;
    }

    private static boolean isMoney(ItemStack stack) {
        return stack.getItem() instanceof MoneyItem;
    }

    // ========================================================================
    // Tests
    // ========================================================================

    /** A sell offer's emerald cost becomes currency; multiplier 0; result untouched. */
    private TestResult test_sell_offer_rewritten_to_currency() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        boolean changed = VillagerTradeRewriter.processOffers(
                offers, entry, moneyTable(1L), syntheticLookup(), null);

        TestResult r = assertTrue("First process must rewrite the offer", changed);
        if (!r.passed()) return r;
        MerchantOffer rewritten = offers.get(0);
        r = assertTrue("Cost A must be currency", isMoney(rewritten.getBaseCostA()));
        if (!r.passed()) return r;
        // iron market 200 raw × count 1 × sell margin 1.2 = 240 raw
        r = assertEquals("Cost must equal market price × sell margin (240 raw)",
                240L, rawValue(rewritten.getBaseCostA(), rewritten.getCostB()));
        if (!r.passed()) return r;
        r = assertEquals("Money-cost offer neutralizes discounts", 0.0f, rewritten.getPriceMultiplier());
        if (!r.passed()) return r;
        r = assertEquals("Result stays the sold item", Items.IRON_INGOT, rewritten.getResult().getItem());
        if (!r.passed()) return r;
        r = assertEquals("Entry tracks the table version", 1L, entry.pricedVersion());
        if (!r.passed()) return r;

        return pass("Sell offer rewritten to currency costs with multiplier 0");
    }

    /** A buy offer's emerald result becomes currency; item costs + multiplier preserved. */
    private TestResult test_buy_offer_rewritten_result() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(buyCoalOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        MerchantOffer rewritten = offers.get(0);
        TestResult r = assertTrue("Result must be currency", isMoney(rewritten.getResult()));
        if (!r.passed()) return r;
        // coal 100 raw × 15 × buy margin 0.8 = 1200 raw → 12 × money (100 raw),
        // the largest exact single-denomination fit.
        r = assertEquals("Payout must be the exact buy-margin value (1200 raw)",
                1200L, rawValue(rewritten.getResult()));
        if (!r.passed()) return r;
        r = assertEquals("Item cost side untouched", Items.COAL, rewritten.getBaseCostA().getItem());
        if (!r.passed()) return r;
        r = assertEquals("Item cost count untouched", 15, rewritten.getBaseCostA().getCount());
        if (!r.passed()) return r;
        r = assertEquals("Buy offer keeps its original priceMultiplier",
                0.05f, rewritten.getPriceMultiplier());
        if (!r.passed()) return r;

        return pass("Buy offer rewritten to a currency payout, item side preserved");
    }

    /** Processing twice with the same table version must be a no-op the second time. */
    private TestResult test_idempotent_same_version() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();
        VillagerTradePriceTable table = moneyTable(1L);

        boolean first = VillagerTradeRewriter.processOffers(offers, entry, table, syntheticLookup(), null);
        MerchantOffer afterFirst = offers.get(0);
        boolean second = VillagerTradeRewriter.processOffers(offers, entry, table, syntheticLookup(), null);

        TestResult r = assertTrue("First process rewrites", first);
        if (!r.passed()) return r;
        r = assertFalse("Second process with the same version is a no-op", second);
        if (!r.passed()) return r;
        r = assertTrue("Offer instance unchanged by the no-op", afterFirst == offers.get(0));
        if (!r.passed()) return r;

        return pass("Rewrite is idempotent for the same table version");
    }

    /** A version bump must reprice from the ORIGINAL emerald offer, not the money offer. */
    private TestResult test_version_bump_reprices_from_original() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        // New table version, iron now 1000 raw → 1000 × 1.2 = 1200 raw, which
        // the single-denomination fitter represents exactly (12 × money
        // [100 raw]) — chosen so the assertion is not affected by best-fit
        // rounding on values no denomination hits exactly.
        ToLongFunction<ItemStack> risen = stack -> stack.is(Items.IRON_INGOT) ? 1000L : 0L;
        boolean changed = VillagerTradeRewriter.processOffers(offers, entry, moneyTable(2L), risen, null);

        TestResult r = assertTrue("Version bump must reprice", changed);
        if (!r.passed()) return r;
        MerchantOffer rewritten = offers.get(0);
        r = assertEquals("Repriced cost must reflect the new market price (1200 raw)",
                1200L, rawValue(rewritten.getBaseCostA(), rewritten.getCostB()));
        if (!r.passed()) return r;
        r = assertEquals("Result still the original sold item", Items.IRON_INGOT,
                rewritten.getResult().getItem());
        if (!r.passed()) return r;
        r = assertEquals("Entry tracks the new version", 2L, entry.pricedVersion());
        if (!r.passed()) return r;

        return pass("Version bump reprices from the stored original offer");
    }

    /** New tail offers (villager level-up) are snapshot and repriced immediately. */
    private TestResult test_tail_append_after_level_up() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();
        VillagerTradePriceTable table = moneyTable(1L);

        VillagerTradeRewriter.processOffers(offers, entry, table, syntheticLookup(), null);

        // Simulated level-up: vanilla appends a new emerald-form offer.
        offers.add(buyCoalOffer());
        boolean changed = VillagerTradeRewriter.processOffers(offers, entry, table, syntheticLookup(), null);

        TestResult r = assertTrue("Tail append must trigger a rewrite despite the same version", changed);
        if (!r.passed()) return r;
        r = assertEquals("Entry now tracks both offers", 2, entry.size());
        if (!r.passed()) return r;
        r = assertTrue("New tail offer must be rewritten too", isMoney(offers.get(1).getResult()));
        if (!r.passed()) return r;
        r = assertTrue("Existing offer stays rewritten", isMoney(offers.get(0).getBaseCostA()));
        if (!r.passed()) return r;

        return pass("Level-up tail offers are snapshot and repriced on the next interaction");
    }

    /** Enchanted-book style offer: money goes into the emerald slot, the book cost stays. */
    private TestResult test_book_offer_keeps_item_cost_slot() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10),
                Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.ENCHANTED_BOOK), 0, 12, 5, 0.2f, 0));
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        MerchantOffer rewritten = offers.get(0);
        TestResult r = assertTrue("Emerald cost slot must hold currency now", isMoney(rewritten.getBaseCostA()));
        if (!r.passed()) return r;
        r = assertEquals("Book cost slot must be preserved", Items.BOOK, rewritten.getCostB().getItem());
        if (!r.passed()) return r;
        // Enchanted-book market: 1000 raw × 1.2 = 1200 raw, one free slot →
        // exact single-denomination fit 12 × money (100 raw).
        r = assertEquals("Money fits the single free slot (1200 raw, single type)",
                1200L, rawValue(rewritten.getBaseCostA()));
        if (!r.passed()) return r;
        r = assertEquals("Result unchanged", Items.ENCHANTED_BOOK, rewritten.getResult().getItem());
        if (!r.passed()) return r;

        return pass("Second item cost slot (book) is preserved through the rewrite");
    }

    /** Treasure-map style offer: costA emeralds, costB compass — compass stays put. */
    private TestResult test_map_offer_keeps_compass_cost() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 13),
                Optional.of(new ItemCost(Items.COMPASS, 1)),
                new ItemStack(Items.MAP), 0, 12, 5, 0.2f, 0));
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        MerchantOffer rewritten = offers.get(0);
        TestResult r = assertTrue("Emerald slot must hold currency", isMoney(rewritten.getBaseCostA()));
        if (!r.passed()) return r;
        r = assertEquals("Compass cost preserved", Items.COMPASS, rewritten.getCostB().getItem());
        if (!r.passed()) return r;

        return pass("Treasure-map compass cost slot survives the rewrite");
    }

    /** Offers for items without a market keep their vanilla emerald form untouched. */
    private TestResult test_no_market_offer_stays_emerald() {
        MerchantOffers offers = new MerchantOffers();
        MerchantOffer vanilla = sellIronOffer();
        offers.add(vanilla);
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        // No market for iron in this lookup → the offer must stay untouched.
        boolean changed = VillagerTradeRewriter.processOffers(
                offers, entry, moneyTable(1L), stack -> 0L, null);

        TestResult r = assertFalse("Nothing was rewritten", changed);
        if (!r.passed()) return r;
        r = assertTrue("Live offer instance untouched", offers.get(0) == vanilla);
        if (!r.passed()) return r;
        r = assertTrue("Offer still costs emeralds", offers.get(0).getBaseCostA().is(Items.EMERALD));
        if (!r.passed()) return r;
        r = assertEquals("Snapshot is still tracked for a later market listing", 1, entry.size());
        if (!r.passed()) return r;
        r = assertFalse("Snapshot is not a pass-through marker", entry.get(0).isPassthrough());
        if (!r.passed()) return r;

        return pass("Unlisted items keep their vanilla emerald trades");
    }

    /**
     * A rewritten offer whose market disappears must be RESTORED to its original
     * emerald form on the next reprice — and convert again once re-listed.
     */
    private TestResult test_market_removed_restores_emerald() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        // v1: iron is listed → currency form.
        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);
        TestResult r = assertTrue("Offer rewritten while the market exists", isMoney(offers.get(0).getBaseCostA()));
        if (!r.passed()) return r;

        // Player uses the offer once while it is in currency form.
        offers.get(0).increaseUses();

        // v2: iron market deleted → offer must return to the emerald original.
        boolean changed = VillagerTradeRewriter.processOffers(
                offers, entry, moneyTable(2L), stack -> 0L, null);
        r = assertTrue("Market removal must change the offer", changed);
        if (!r.passed()) return r;
        r = assertTrue("Offer restored to emerald cost", offers.get(0).getBaseCostA().is(Items.EMERALD));
        if (!r.passed()) return r;
        r = assertEquals("Original emerald count restored", 5, offers.get(0).getBaseCostA().getCount());
        if (!r.passed()) return r;
        r = assertEquals("Live uses carried through the restore", 1, offers.get(0).getUses());
        if (!r.passed()) return r;
        r = assertFalse("Snapshot kept in the sidecar (not converted to pass-through)",
                entry.get(0).isPassthrough());
        if (!r.passed()) return r;

        // v3: market re-created → the kept snapshot converts the offer again.
        changed = VillagerTradeRewriter.processOffers(offers, entry, moneyTable(3L), syntheticLookup(), null);
        r = assertTrue("Re-listing must rewrite again", changed);
        if (!r.passed()) return r;
        r = assertTrue("Offer back in currency form", isMoney(offers.get(0).getBaseCostA()));
        if (!r.passed()) return r;
        r = assertEquals("Uses still preserved", 1, offers.get(0).getUses());
        if (!r.passed()) return r;

        return pass("Market removal restores the emerald original; re-listing converts again");
    }

    /** An untouched (unlisted) offer converts to currency once its market appears. */
    private TestResult test_market_created_later_converts() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        // v1: no iron market → snapshot taken, offer left in emerald form.
        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), stack -> 0L, null);
        TestResult r = assertTrue("Offer still emerald before the market exists",
                offers.get(0).getBaseCostA().is(Items.EMERALD));
        if (!r.passed()) return r;

        // v2: iron market created → offer converts on the next version bump.
        boolean changed = VillagerTradeRewriter.processOffers(
                offers, entry, moneyTable(2L), syntheticLookup(), null);
        r = assertTrue("New market must trigger the rewrite", changed);
        if (!r.passed()) return r;
        r = assertTrue("Offer now in currency form", isMoney(offers.get(0).getBaseCostA()));
        if (!r.passed()) return r;
        // iron 200 raw × 1.2 = 240 raw
        r = assertEquals("Priced from the new market (240 raw)",
                240L, rawValue(offers.get(0).getBaseCostA(), offers.get(0).getCostB()));
        if (!r.passed()) return r;

        return pass("Items listed after the snapshot convert on the next reprice");
    }

    /** Live uses survive both reprice and restore; maxUses comes from the original. */
    private TestResult test_partially_used_offer_preserved() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        // Player trades three times on the rewritten offer.
        offers.get(0).increaseUses();
        offers.get(0).increaseUses();
        offers.get(0).increaseUses();

        // Reprice at a newer version → uses must carry over.
        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(2L), syntheticLookup(), null);
        TestResult r = assertEquals("Uses carried through the reprice", 3, offers.get(0).getUses());
        if (!r.passed()) return r;
        r = assertEquals("MaxUses restored from the original", 16, offers.get(0).getMaxUses());
        if (!r.passed()) return r;

        // Restore → uses still carried over, emerald cost back.
        VillagerTradeRewriter.restoreAll(offers, entry);
        r = assertEquals("Uses carried through the restore", 3, offers.get(0).getUses());
        if (!r.passed()) return r;
        r = assertTrue("Cost is emerald again", offers.get(0).getBaseCostA().is(Items.EMERALD));
        if (!r.passed()) return r;

        return pass("Partially used offers stay partially used through reprice and restore");
    }

    /** Full restore returns the exact emerald-form offers. */
    private TestResult test_restore_on_disabled() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        offers.add(buyCoalOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);
        VillagerTradeRewriter.restoreAll(offers, entry);

        MerchantOffer sell = offers.get(0);
        TestResult r = assertTrue("Sell cost restored to emeralds", sell.getBaseCostA().is(Items.EMERALD));
        if (!r.passed()) return r;
        r = assertEquals("Emerald count restored", 5, sell.getBaseCostA().getCount());
        if (!r.passed()) return r;
        r = assertEquals("Original priceMultiplier restored", 0.05f, sell.getPriceMultiplier());
        if (!r.passed()) return r;

        MerchantOffer buy = offers.get(1);
        r = assertTrue("Buy result restored to emeralds", buy.getResult().is(Items.EMERALD));
        if (!r.passed()) return r;
        r = assertEquals("Buy item cost unchanged", Items.COAL, buy.getBaseCostA().getItem());
        if (!r.passed()) return r;

        return pass("Disabled feature restores the exact emerald-form offers");
    }

    /** Non-emerald offers, currency-trading offers and emerald↔emerald offers pass through. */
    private TestResult test_passthrough_classification() {
        ItemStack currency = moneyCurrency();

        // No emerald side at all (modded trade): wheat → bread
        MerchantOffer noEmerald = new MerchantOffer(new ItemCost(Items.WHEAT, 3), Optional.empty(),
                new ItemStack(Items.BREAD), 0, 16, 1, 0.05f, 0);
        TestResult r = assertEquals("No emerald side → PASS",
                VillagerTradeRewriter.OfferKind.PASS,
                VillagerTradeRewriter.classify(noEmerald, currency));
        if (!r.passed()) return r;

        // Offer already trades the currency itself
        MerchantOffer currencyOffer = new MerchantOffer(
                new ItemCost(currency.getItem(), 2), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 1, 0.05f, 0);
        r = assertEquals("Currency on a cost side → PASS",
                VillagerTradeRewriter.OfferKind.PASS,
                VillagerTradeRewriter.classify(currencyOffer, currency));
        if (!r.passed()) return r;

        // Emeralds on both sides
        MerchantOffer emeraldBoth = new MerchantOffer(new ItemCost(Items.EMERALD, 4), Optional.empty(),
                new ItemStack(Items.EMERALD, 5), 0, 16, 1, 0.05f, 0);
        r = assertEquals("Emeralds on both sides → PASS",
                VillagerTradeRewriter.OfferKind.PASS,
                VillagerTradeRewriter.classify(emeraldBoth, currency));
        if (!r.passed()) return r;

        // Currency configured as emerald → everything passes through
        r = assertEquals("Emerald currency → PASS",
                VillagerTradeRewriter.OfferKind.PASS,
                VillagerTradeRewriter.classify(sellIronOffer(), new ItemStack(Items.EMERALD)));
        if (!r.passed()) return r;

        // Sanity: the standard cases still classify correctly
        r = assertEquals("Emerald cost → VILLAGER_SELLS",
                VillagerTradeRewriter.OfferKind.VILLAGER_SELLS,
                VillagerTradeRewriter.classify(sellIronOffer(), currency));
        if (!r.passed()) return r;
        r = assertEquals("Emerald result → VILLAGER_BUYS",
                VillagerTradeRewriter.OfferKind.VILLAGER_BUYS,
                VillagerTradeRewriter.classify(buyCoalOffer(), currency));
        if (!r.passed()) return r;

        // Pass-through offers get marker entries that keep index alignment.
        MerchantOffers offers = new MerchantOffers();
        offers.add(noEmerald);
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();
        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);
        r = assertTrue("Pass-through offer keeps a marker at its index", entry.get(0).isPassthrough());
        if (!r.passed()) return r;
        r = assertTrue("Pass-through live offer untouched", offers.get(0) == noEmerald);
        if (!r.passed()) return r;
        r = assertFalse("Rewritten offer stores its original", entry.get(1).isPassthrough());
        if (!r.passed()) return r;

        return pass("Pass-through cases classified and index alignment preserved");
    }

    /** The rewritten ItemCost must accept a plain currency stack as payment. */
    private TestResult test_item_cost_accepts_plain_currency() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(sellIronOffer());
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        VillagerTradeRewriter.processOffers(offers, entry, moneyTable(1L), syntheticLookup(), null);

        MerchantOffer rewritten = offers.get(0);
        ItemCost costA = rewritten.getItemCostA();
        ItemStack payment = new ItemStack(rewritten.getBaseCostA().getItem(), costA.count());

        TestResult r = assertTrue("ItemCost.test must accept a plain stack of the currency item",
                costA.test(payment));
        if (!r.passed()) return r;

        ItemStack paymentB = rewritten.getCostB().isEmpty() ? ItemStack.EMPTY
                : new ItemStack(rewritten.getCostB().getItem(), rewritten.getCostB().getCount());
        r = assertTrue("satisfiedBy must accept plain currency stacks",
                rewritten.satisfiedBy(payment, paymentB));
        if (!r.passed()) return r;

        return pass("Players can pay rewritten offers with plain currency stacks");
    }

    /** The clamp callback fires once per offer index, not on every reprice. */
    private TestResult test_clamp_warns_once_per_offer() {
        // Unstackable currency: diamond market 6400 raw × 1.2 = 7680 raw = ~77 items
        // into 2 slots of max stack 1 → guaranteed clamp.
        VillagerTradePriceTable v1 = table(1L, true, new ItemStack(Items.IRON_SWORD), Map.of());
        VillagerTradePriceTable v2 = table(2L, true, new ItemStack(Items.IRON_SWORD), Map.of());
        ToLongFunction<ItemStack> diamondMarket = stack -> stack.is(Items.DIAMOND) ? 6400L : 0L;
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 64), Optional.empty(),
                new ItemStack(Items.DIAMOND), 0, 16, 2, 0.05f, 0));
        VillagerTradeSavedData.VillagerEntry entry = new VillagerTradeSavedData.VillagerEntry();

        AtomicInteger clampCount = new AtomicInteger();
        VillagerTradeRewriter.processOffers(offers, entry, v1, diamondMarket, i -> clampCount.incrementAndGet());
        TestResult r = assertEquals("First clamped reprice fires the callback", 1, clampCount.get());
        if (!r.passed()) return r;

        VillagerTradeRewriter.processOffers(offers, entry, v2, diamondMarket, i -> clampCount.incrementAndGet());
        r = assertEquals("Repeated clamps on the same offer stay silent", 1, clampCount.get());
        if (!r.passed()) return r;
        r = assertTrue("Clamp flag persisted in the entry", entry.isClampFlagged(0));
        if (!r.passed()) return r;

        return pass("Clamp warning fires once per villager offer index");
    }

    /** Production price lookup resolves stacks through the BankSystem ItemID registry. */
    private TestResult test_table_lookup_resolves_item_ids() {
        // Register (or resolve) the iron ingot ItemID on the master.
        ItemID ironID = ItemID.getOrRegisterFromItemStackServerSide_direct(new ItemStack(Items.IRON_INGOT));
        TestResult r = assertTrue("Iron ItemID must be registered", ironID.isValid());
        if (!r.passed()) return r;

        Map<Short, Long> prices = new HashMap<>();
        prices.put(ironID.getShort(), 200L);
        VillagerTradePriceTable table = table(1L, true, moneyCurrency(), prices);
        ToLongFunction<ItemStack> lookup = VillagerTradeRewriter.tableLookup(table);

        r = assertEquals("Registered item resolves to its table price",
                200L, lookup.applyAsLong(new ItemStack(Items.IRON_INGOT)));
        if (!r.passed()) return r;

        // A stack that was never registered as an ItemID must resolve to 0.
        ItemStack unregistered = new ItemStack(Items.PAPER);
        CompoundTag marker = new CompoundTag();
        marker.putString("stockmarket_villager_test", UUID.randomUUID().toString());
        unregistered.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        r = assertEquals("Unregistered item resolves to 0 (no market)",
                0L, lookup.applyAsLong(unregistered));
        if (!r.passed()) return r;

        r = assertEquals("Empty stack resolves to 0", 0L, lookup.applyAsLong(ItemStack.EMPTY));
        if (!r.passed()) return r;

        return pass("tableLookup resolves ItemIDs and falls back to 0 for unknown items");
    }
}
