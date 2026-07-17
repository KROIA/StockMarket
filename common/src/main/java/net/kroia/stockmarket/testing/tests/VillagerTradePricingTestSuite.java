package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.villagertrading.CurrencyFitter;
import net.kroia.stockmarket.villagertrading.VillagerTradePriceTable;
import net.kroia.stockmarket.villagertrading.VillagerTradeRewriter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * Tests for the pure villager-trade pricing logic: {@link CurrencyFitter}
 * (generic-item path and BankSystem money-denomination path), margin
 * application and the market-listing requirement in
 * {@link VillagerTradeRewriter#rewriteOffer}, and the
 * {@link VillagerTradePriceTable} stream-codec round-trip.
 * <p>
 * Runs on both master and slave (no Minecraft server context required — only
 * registry-backed {@link ItemStack}s, which are always available in-game).
 */
public class VillagerTradePricingTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VILLAGER_PRICING;
    }

    @Override
    public void registerTests() {
        addTest("generic_fit_round_to_nearest", this::test_generic_fit_round_to_nearest);
        addTest("generic_fit_min_one_item", this::test_generic_fit_min_one_item);
        addTest("generic_fit_max_stack_16_clamps", this::test_generic_fit_max_stack_16_clamps);
        addTest("generic_fit_stack_size_one_clamps", this::test_generic_fit_stack_size_one_clamps);
        addTest("generic_fit_slot_split", this::test_generic_fit_slot_split);
        addTest("money_fit_single_type_exact", this::test_money_fit_single_type_exact);
        addTest("money_fit_single_type_rounding", this::test_money_fit_single_type_rounding);
        addTest("money_fit_prefers_larger_denomination", this::test_money_fit_prefers_larger_denomination);
        addTest("money_fit_spills_single_type_per_slot", this::test_money_fit_spills_single_type_per_slot);
        addTest("money_fit_never_zero", this::test_money_fit_never_zero);
        addTest("money_fit_capacity_clamp", this::test_money_fit_capacity_clamp);
        addTest("no_market_offer_not_rewritten", this::test_no_market_offer_not_rewritten);
        addTest("sell_margin_item_basis", this::test_sell_margin_item_basis);
        addTest("buy_margin_applied", this::test_buy_margin_applied);
        addTest("price_table_stream_codec_roundtrip", this::test_price_table_stream_codec_roundtrip);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Sums the raw value of a list of currency stacks (money worth or 100/item). */
    private static long totalRawValue(List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack stack : stacks) {
            long perItem = stack.getItem() instanceof MoneyItem money ? money.worth() : 100L;
            total += perItem * stack.getCount();
        }
        return total;
    }

    /** Builds an enabled price table with the given currency, margins and prices. */
    private static VillagerTradePriceTable table(ItemStack currency, float buyMargin, float sellMargin,
                                                 Map<Short, Long> prices) {
        return new VillagerTradePriceTable(1L, true, currency, buyMargin, sellMargin, prices);
    }

    private static ItemStack moneyCurrency() {
        return new ItemStack(BankSystemItems.MONEY.get());
    }

    // ========================================================================
    // CurrencyFitter — generic item path
    // ========================================================================

    /** 2.5 items round to 3, 2.4 items round to 2. */
    private TestResult test_generic_fit_round_to_nearest() {
        ItemStack paper = new ItemStack(Items.PAPER);

        CurrencyFitter.FitResult up = CurrencyFitter.fit(250, 1, paper);
        TestResult r = assertEquals("250 raw with 1 slot should emit one stack", 1, up.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("2.5 items should round up to 3", 3, up.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertFalse("Rounding up is not a clamp", up.clamped());
        if (!r.passed()) return r;

        CurrencyFitter.FitResult down = CurrencyFitter.fit(240, 1, paper);
        r = assertEquals("2.4 items should round down to 2", 2, down.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertFalse("Rounding down is not a clamp", down.clamped());
        if (!r.passed()) return r;

        return pass("Generic fit rounds the item count to nearest");
    }

    /** Tiny raw values never produce an empty/free price — minimum is one item. */
    private TestResult test_generic_fit_min_one_item() {
        ItemStack paper = new ItemStack(Items.PAPER);
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(10, 1, paper);

        TestResult r = assertEquals("Should emit exactly one stack", 1, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("0.1 items must clamp up to the 1-item minimum", 1, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertFalse("Minimum enforcement is not a capacity clamp", fit.clamped());
        if (!r.passed()) return r;

        return pass("Generic fit never emits less than one item");
    }

    /** Max-stack-16 currencies respect their stack limit and flag the clamp. */
    private TestResult test_generic_fit_max_stack_16_clamps() {
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL); // max stack size 16
        // 40 items desired, capacity 2 × 16 = 32 → clamped to [16, 16]
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(4000, 2, pearl);

        TestResult r = assertEquals("Two slots should both be used", 2, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("First stack capped at max stack size", 16, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertEquals("Second stack capped at max stack size", 16, fit.stacks().get(1).getCount());
        if (!r.passed()) return r;
        r = assertTrue("Capacity overflow must be flagged as clamped", fit.clamped());
        if (!r.passed()) return r;

        return pass("Generic fit respects max stack size 16 and flags the clamp");
    }

    /** Unstackable currencies (max stack 1) still work, one item per slot. */
    private TestResult test_generic_fit_stack_size_one_clamps() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD); // max stack size 1
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(500, 2, sword); // 5 items desired, capacity 2

        TestResult r = assertEquals("Two slots of one item each", 2, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("Slot 1 holds a single item", 1, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertEquals("Slot 2 holds a single item", 1, fit.stacks().get(1).getCount());
        if (!r.passed()) return r;
        r = assertTrue("5 items into capacity 2 must clamp", fit.clamped());
        if (!r.passed()) return r;

        return pass("Generic fit handles unstackable currency items");
    }

    /** Item counts above one stack split into multiple slots, largest first. */
    private TestResult test_generic_fit_slot_split() {
        ItemStack paper = new ItemStack(Items.PAPER);
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(8000, 2, paper); // 80 items

        TestResult r = assertEquals("80 items should use two stacks", 2, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("First stack is full (64)", 64, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertEquals("Second stack holds the remainder (16)", 16, fit.stacks().get(1).getCount());
        if (!r.passed()) return r;
        r = assertFalse("80 items fit into two stacks — no clamp", fit.clamped());
        if (!r.passed()) return r;

        return pass("Generic fit splits across slots largest-first");
    }

    // ========================================================================
    // CurrencyFitter — BankSystem money path
    // ========================================================================

    /**
     * 240 raw = 12 × 20-cent — a single denomination type in a single stack,
     * even though two slots are available and mixed denominations could also
     * represent it. Larger exact candidates (24 × 10-cent, 48 × 5-cent) lose
     * the tie to the largest exact denomination.
     */
    private TestResult test_money_fit_single_type_exact() {
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(240, 2, moneyCurrency());

        TestResult r = assertEquals("240 raw should use a single stack despite two slots",
                1, fit.stacks().size());
        if (!r.passed()) return r;
        ItemStack stack = fit.stacks().get(0);
        r = assertTrue("Stack must be a money denomination", stack.getItem() instanceof MoneyItem);
        if (!r.passed()) return r;
        r = assertEquals("Largest exact denomination is the 20-cent coin",
                20L, ((MoneyItem) stack.getItem()).worth());
        if (!r.passed()) return r;
        r = assertEquals("12 coins expected", 12, stack.getCount());
        if (!r.passed()) return r;
        r = assertFalse("Exact fit must not be clamped", fit.clamped());
        if (!r.passed()) return r;

        return pass("Money fit expresses 240 raw as a single denomination type");
    }

    /**
     * 123456 raw: no denomination hits it exactly → the best single-type
     * candidate wins (62 × money20 [2000 raw] = 124000, error 544). The error
     * stays within half of the chosen denomination, so it is NOT clamped.
     */
    private TestResult test_money_fit_single_type_rounding() {
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(123456, 2, moneyCurrency());

        TestResult r = assertEquals("A single best-fit stack expected", 1, fit.stacks().size());
        if (!r.passed()) return r;
        long total = totalRawValue(fit.stacks());
        long worth = ((MoneyItem) fit.stacks().get(0).getItem()).worth();
        r = assertTrue("Represented value (" + total + ") must be within half of the chosen"
                        + " denomination (" + worth + ") of the requested 123456",
                Math.abs(123456L - total) <= worth / 2);
        if (!r.passed()) return r;
        r = assertFalse("Best-fit rounding is not a clamp", fit.clamped());
        if (!r.passed()) return r;

        return pass("Money fit rounds to the closest single-denomination representation");
    }

    /**
     * The user-reported scenario: 2000 raw (20.00) has several exact
     * single-type fits (1 × money20, 2 × money10, 4 × money5, ...) — the tie
     * must go to the LARGER denomination: exactly one 20-dollar note.
     */
    private TestResult test_money_fit_prefers_larger_denomination() {
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(2000, 2, moneyCurrency());

        TestResult r = assertEquals("A single stack expected", 1, fit.stacks().size());
        if (!r.passed()) return r;
        ItemStack stack = fit.stacks().get(0);
        r = assertEquals("Exactly one note expected", 1, stack.getCount());
        if (!r.passed()) return r;
        r = assertEquals("The 20-dollar note (2000 raw) must win the tie over 2 × money10",
                2000L, ((MoneyItem) stack.getItem()).worth());
        if (!r.passed()) return r;
        r = assertFalse("Exact fit must not be clamped", fit.clamped());
        if (!r.passed()) return r;

        return pass("Ties between exact fits prefer the larger denomination");
    }

    /**
     * Values beyond one full stack of the largest note (64 × 100000 raw) spill
     * into the second slot — and each slot stays a single denomination type.
     */
    private TestResult test_money_fit_spills_single_type_per_slot() {
        long value = 6_500_000L; // 64 × money1000 (6.4M) + 1 × money1000 (100k)
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(value, 2, moneyCurrency());

        TestResult r = assertEquals("Two slots expected for a value beyond one full stack",
                2, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("Slot 1 is a full stack of the largest note", 64, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertEquals("Slot 1 uses the largest denomination",
                100000L, ((MoneyItem) fit.stacks().get(0).getItem()).worth());
        if (!r.passed()) return r;
        r = assertEquals("Slot 2 is single-type as well",
                100000L, ((MoneyItem) fit.stacks().get(1).getItem()).worth());
        if (!r.passed()) return r;
        r = assertEquals("Exact total expected", value, totalRawValue(fit.stacks()));
        if (!r.passed()) return r;
        r = assertFalse("Exact spill must not be clamped", fit.clamped());
        if (!r.passed()) return r;

        return pass("Spill slots each hold a single denomination type");
    }

    /** Zero/negative raw values still cost at least one 1-cent coin. */
    private TestResult test_money_fit_never_zero() {
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(0, 1, moneyCurrency());

        TestResult r = assertEquals("Exactly one stack expected", 1, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("Minimum price is one item", 1, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertTrue("Minimum denomination (1 cent) expected",
                fit.stacks().get(0).getItem() instanceof MoneyItem money && money.worth() == 1L);
        if (!r.passed()) return r;

        return pass("Money fit never emits a free trade — minimum one 1-cent coin");
    }

    /** Values beyond 2 full stacks of the largest denomination are capped and flagged. */
    private TestResult test_money_fit_capacity_clamp() {
        long huge = 100000L * 200; // 200 × money1000 — capacity is 2 × 64
        CurrencyFitter.FitResult fit = CurrencyFitter.fit(huge, 2, moneyCurrency());

        TestResult r = assertEquals("Both slots used at capacity", 2, fit.stacks().size());
        if (!r.passed()) return r;
        r = assertEquals("Slot 1 full stack of largest denomination", 64, fit.stacks().get(0).getCount());
        if (!r.passed()) return r;
        r = assertEquals("Slot 2 full stack of largest denomination", 64, fit.stacks().get(1).getCount());
        if (!r.passed()) return r;
        r = assertTrue("Unrepresentable value must be flagged as clamped", fit.clamped());
        if (!r.passed()) return r;

        return pass("Money fit clamps at slot capacity and reports it");
    }

    // ========================================================================
    // Margins + market-listing requirement (rewriteOffer with synthetic lookup)
    // ========================================================================

    /**
     * Offers whose traded item has NO market must not be rewritten at all —
     * they keep their vanilla emerald form (no emerald-conversion fallback).
     */
    private TestResult test_no_market_offer_not_rewritten() {
        VillagerTradePriceTable table = table(moneyCurrency(), 0.8f, 1.2f, Map.of());
        ToLongFunction<ItemStack> noMarkets = stack -> 0L;

        // Villager sells: unlisted result item → no rewrite
        MerchantOffer sell = new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 2, 0.05f, 0);
        TestResult r = assertNull("Sell offer without a market for the result must not be rewritten",
                VillagerTradeRewriter.rewriteOffer(sell, sell, table, noMarkets));
        if (!r.passed()) return r;

        // Villager buys: one of two item costs unlisted → no rewrite
        MerchantOffer buy = new MerchantOffer(new ItemCost(Items.COAL, 15),
                Optional.of(new ItemCost(Items.STICK, 2)),
                new ItemStack(Items.EMERALD), 0, 16, 2, 0.05f, 0);
        ToLongFunction<ItemStack> coalOnly = stack -> stack.is(Items.COAL) ? 100L : 0L;
        r = assertNull("Buy offer with any unlisted item cost must not be rewritten",
                VillagerTradeRewriter.rewriteOffer(buy, buy, table, coalOnly));
        if (!r.passed()) return r;

        // Sanity: with all items listed the same offers DO rewrite
        ToLongFunction<ItemStack> allListed = stack -> 100L;
        r = assertNotNull("Sell offer with a listed result must be rewritten",
                VillagerTradeRewriter.rewriteOffer(sell, sell, table, allListed));
        if (!r.passed()) return r;
        r = assertNotNull("Buy offer with all costs listed must be rewritten",
                VillagerTradeRewriter.rewriteOffer(buy, buy, table, allListed));
        if (!r.passed()) return r;

        return pass("Unlisted items keep their vanilla emerald trades");
    }

    /** Iron HAS a market price (200 raw) → priced from the item market, margin 1.2 → 240 raw. */
    private TestResult test_sell_margin_item_basis() {
        VillagerTradePriceTable table = table(moneyCurrency(), 0.8f, 1.2f, Map.of());
        MerchantOffer original = new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 2, 0.05f, 0);
        ToLongFunction<ItemStack> ironMarket = stack -> stack.is(Items.IRON_INGOT) ? 200L : 0L;

        VillagerTradeRewriter.RewriteResult result =
                VillagerTradeRewriter.rewriteOffer(original, original, table, ironMarket);
        TestResult r = assertNotNull("Sell offer must be rewritten", result);
        if (!r.passed()) return r;

        long costValue = totalRawValue(List.of(result.offer().getBaseCostA(), result.offer().getCostB()));
        r = assertEquals("Money cost must be market price × sell margin (240 raw)", 240L, costValue);
        if (!r.passed()) return r;
        r = assertEquals("Money-cost offers must neutralize demand/discounts (multiplier 0)",
                0.0f, result.offer().getPriceMultiplier());
        if (!r.passed()) return r;
        r = assertTrue("Cost A must be a money item",
                result.offer().getBaseCostA().getItem() instanceof MoneyItem);
        if (!r.passed()) return r;

        return pass("Sell rewrite prices from the item's own market with the sell margin");
    }

    /**
     * Villager buys 15 coal for 1 emerald; coal market 100 raw; buy margin 0.8 →
     * payout 1200 raw; original priceMultiplier preserved on the item-cost side.
     */
    private TestResult test_buy_margin_applied() {
        VillagerTradePriceTable table = table(moneyCurrency(), 0.8f, 1.2f, Map.of());
        MerchantOffer original = new MerchantOffer(new ItemCost(Items.COAL, 15), Optional.empty(),
                new ItemStack(Items.EMERALD), 0, 16, 2, 0.05f, 0);
        ToLongFunction<ItemStack> coalMarket = stack -> stack.is(Items.COAL) ? 100L : 0L;

        VillagerTradeRewriter.RewriteResult result =
                VillagerTradeRewriter.rewriteOffer(original, original, table, coalMarket);
        TestResult r = assertNotNull("Buy offer must be rewritten", result);
        if (!r.passed()) return r;

        // 15 × 100 raw × 0.8 = 1200 raw = 12 × money (100 raw) — the largest
        // exact single-denomination fit.
        r = assertTrue("Result must be a money item",
                result.offer().getResult().getItem() instanceof MoneyItem);
        if (!r.passed()) return r;
        long payout = totalRawValue(List.of(result.offer().getResult()));
        r = assertEquals("Payout must be the exact buy-margin value (1200 raw)", 1200L, payout);
        if (!r.passed()) return r;
        r = assertEquals("Item costs stay untouched", Items.COAL, result.offer().getBaseCostA().getItem());
        if (!r.passed()) return r;
        r = assertEquals("Buy offers keep the original priceMultiplier",
                0.05f, result.offer().getPriceMultiplier());
        if (!r.passed()) return r;

        return pass("Buy rewrite applies the buy margin and preserves the item-cost side");
    }

    // ========================================================================
    // Price table codec
    // ========================================================================

    /** Full STREAM_CODEC round-trip (skipped when no registry access is available). */
    private TestResult test_price_table_stream_codec_roundtrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("STREAM_CODEC round-trip skipped (no registry access in this context)");
        }
        Map<Short, Long> prices = new HashMap<>();
        prices.put((short) 12, 3456L);
        prices.put((short) 99, 100L);
        VillagerTradePriceTable original = new VillagerTradePriceTable(
                42L, true, moneyCurrency(), 0.75f, 1.25f, prices);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        VillagerTradePriceTable.STREAM_CODEC.encode(buf, original);
        VillagerTradePriceTable decoded = VillagerTradePriceTable.STREAM_CODEC.decode(buf);

        TestResult r = assertEquals("version survives", original.version(), decoded.version());
        if (!r.passed()) return r;
        r = assertEquals("enabled survives", original.enabled(), decoded.enabled());
        if (!r.passed()) return r;
        r = assertTrue("currency survives",
                ItemStack.isSameItemSameComponents(original.currency(), decoded.currency()));
        if (!r.passed()) return r;
        r = assertEquals("buyMargin survives", original.buyMargin(), decoded.buyMargin());
        if (!r.passed()) return r;
        r = assertEquals("sellMargin survives", original.sellMargin(), decoded.sellMargin());
        if (!r.passed()) return r;
        r = assertEquals("price map survives", original.pricesRawByItemIdShort(), decoded.pricesRawByItemIdShort());
        if (!r.passed()) return r;

        return pass("VillagerTradePriceTable STREAM_CODEC round-trips all fields");
    }
}
