package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.minecraft.item.custom.money.MoneyItem;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.villagertrading.MoneyPayment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Tests for the pure value-based merchant money payment logic in
 * {@link MoneyPayment}: the money-cost gate, the {@code satisfied} truth
 * table, optimal note consumption, exact greedy change-making, and full value
 * conservation across repeated trades (the shift-click loop equivalent).
 * <p>
 * Runs on both master and slave (no Minecraft server context required — only
 * registry-backed {@link ItemStack}s, which are always available in-game).
 */
public class VillagerMoneyPaymentTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VILLAGER_MONEY_PAYMENT;
    }

    @Override
    public void registerTests() {
        addTest("gate_detects_money_costs", this::test_gate_detects_money_costs);
        addTest("make_change_zero_is_empty", this::test_make_change_zero_is_empty);
        addTest("make_change_exact_exhaustive_small", this::test_make_change_exact_exhaustive_small);
        addTest("make_change_exact_randomized_large", this::test_make_change_exact_randomized_large);
        addTest("make_change_fewest_notes", this::test_make_change_fewest_notes);
        addTest("satisfied_truth_table_all_money", this::test_satisfied_truth_table_all_money);
        addTest("satisfied_split_payment", this::test_satisfied_split_payment);
        addTest("satisfied_contamination_and_renamed", this::test_satisfied_contamination_and_renamed);
        addTest("satisfied_mixed_offer_both_orientations", this::test_satisfied_mixed_offer_both_orientations);
        addTest("consume_exact_combos_no_change", this::test_consume_exact_combos_no_change);
        addTest("consume_minimal_overshoot", this::test_consume_minimal_overshoot);
        addTest("consume_prefers_fewest_notes", this::test_consume_prefers_fewest_notes);
        addTest("consume_mixed_offer", this::test_consume_mixed_offer);
        addTest("value_conservation_loop", this::test_value_conservation_loop);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Money denomination item by raw worth (fails the test setup when absent). */
    private static Item money(long worth) {
        for (ItemStack stack : BankSystemItems.getMoneyItems()) {
            if (stack.getItem() instanceof MoneyItem item && item.worth() == worth) {
                return stack.getItem();
            }
        }
        throw new IllegalStateException("No money denomination with worth " + worth);
    }

    private static ItemStack moneyStack(long worth, int count) {
        return new ItemStack(money(worth), count);
    }

    /** All-money offer: costA = {@code count} notes of {@code worth}, no costB. */
    private static MerchantOffer moneyOffer(long worth, int count) {
        return new MerchantOffer(new ItemCost(money(worth), count), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 2, 0.0f, 0);
    }

    /** Sums the raw money value of a list of stacks. */
    private static long totalRaw(List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack stack : stacks) {
            total += MoneyPayment.stackValue(stack);
        }
        return total;
    }

    private static int totalNotes(List<ItemStack> stacks) {
        int notes = 0;
        for (ItemStack stack : stacks) {
            notes += stack.getCount();
        }
        return notes;
    }

    // ========================================================================
    // Gate
    // ========================================================================

    /** Only offers with a genuine money cost slot pass the value-payment gate. */
    private TestResult test_gate_detects_money_costs() {
        TestResult r = assertTrue("Money costA must gate in",
                MoneyPayment.isValueBasedOffer(moneyOffer(2000, 1)));
        if (!r.passed()) return r;

        MerchantOffer moneyCostB = new MerchantOffer(new ItemCost(Items.BOOK, 1),
                Optional.of(new ItemCost(money(2000), 1)),
                new ItemStack(Items.ENCHANTED_BOOK), 0, 16, 2, 0.0f, 0);
        r = assertTrue("Money costB must gate in", MoneyPayment.isValueBasedOffer(moneyCostB));
        if (!r.passed()) return r;

        MerchantOffer vanilla = new MerchantOffer(new ItemCost(Items.EMERALD, 5), Optional.empty(),
                new ItemStack(Items.IRON_INGOT), 0, 16, 2, 0.05f, 0);
        r = assertFalse("Emerald offers must gate out (vanilla path)",
                MoneyPayment.isValueBasedOffer(vanilla));
        if (!r.passed()) return r;

        MerchantOffer moneyResult = new MerchantOffer(new ItemCost(Items.COAL, 15), Optional.empty(),
                moneyStack(1000, 1), 0, 16, 2, 0.05f, 0);
        r = assertFalse("A money RESULT alone must not gate in (villager-buys offers pay vanilla)",
                MoneyPayment.isValueBasedOffer(moneyResult));
        if (!r.passed()) return r;

        return pass("Gate keys purely off money cost slots");
    }

    // ========================================================================
    // makeChange
    // ========================================================================

    /** Zero and negative change produce no stacks. */
    private TestResult test_make_change_zero_is_empty() {
        TestResult r = assertTrue("Zero change must be empty", MoneyPayment.makeChange(0).isEmpty());
        if (!r.passed()) return r;
        r = assertTrue("Negative change must be empty", MoneyPayment.makeChange(-5).isEmpty());
        if (!r.passed()) return r;
        return pass("No change stacks for non-positive values");
    }

    /** Exhaustive small round-trip: every raw value 1..3000 is represented exactly. */
    private TestResult test_make_change_exact_exhaustive_small() {
        for (long v = 1; v <= 3000; v++) {
            List<ItemStack> change = MoneyPayment.makeChange(v);
            long total = totalRaw(change);
            if (total != v) {
                return fail("makeChange(" + v + ") sums to " + total + " instead of " + v);
            }
        }
        return pass("makeChange is exact for all raw values 1..3000");
    }

    /** Randomized large round-trip: sums always equal the requested value. */
    private TestResult test_make_change_exact_randomized_large() {
        Random random = new Random(20260715L); // fixed seed — deterministic test
        for (int i = 0; i < 500; i++) {
            long v = 1 + (long) (random.nextDouble() * 10_000_000L);
            List<ItemStack> change = MoneyPayment.makeChange(v);
            long total = totalRaw(change);
            if (total != v) {
                return fail("makeChange(" + v + ") sums to " + total + " instead of " + v);
            }
        }
        return pass("makeChange is exact for randomized large values");
    }

    /** Canonical fewest-notes cases (greedy = optimal for this denomination set). */
    private TestResult test_make_change_fewest_notes() {
        // 30.00 = 3000 raw → 1×20$ + 1×10$ (the 50-note overpay scenario).
        List<ItemStack> change = MoneyPayment.makeChange(3000);
        TestResult r = assertEquals("3000 raw should be two stacks (20$ + 10$)", 2, change.size());
        if (!r.passed()) return r;
        r = assertEquals("Largest first: 20$ note", 2000L,
                ((MoneyItem) change.get(0).getItem()).worth());
        if (!r.passed()) return r;
        r = assertEquals("Then the 10$ note", 1000L,
                ((MoneyItem) change.get(1).getItem()).worth());
        if (!r.passed()) return r;

        // 1.88 = 188 raw → 1×1.00 + 1×0.50 + 1×0.20 + 1×0.10 + 1×0.05 + 3×0.01 = 8 notes.
        change = MoneyPayment.makeChange(188);
        r = assertEquals("188 raw greedy note count", 8, totalNotes(change));
        if (!r.passed()) return r;

        // 90 raw → 50 + 20 + 20 = 3 notes (never 4×20+10 or similar).
        change = MoneyPayment.makeChange(90);
        r = assertEquals("90 raw greedy note count", 3, totalNotes(change));
        if (!r.passed()) return r;

        return pass("Greedy change uses the fewest notes for canonical cases");
    }

    // ========================================================================
    // satisfied
    // ========================================================================

    /** All-money truth table: exact, over, under, empty, and the 50-note case. */
    private TestResult test_satisfied_truth_table_all_money() {
        MerchantOffer offer = moneyOffer(2000, 1); // price 20.00

        TestResult r = assertTrue("1×20$ exact",
                MoneyPayment.satisfied(offer, moneyStack(2000, 1), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("2×10$ exact",
                MoneyPayment.satisfied(offer, moneyStack(1000, 2), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("4×5$ exact",
                MoneyPayment.satisfied(offer, moneyStack(500, 4), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("20×1$ exact",
                MoneyPayment.satisfied(offer, moneyStack(100, 20), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("1×50$ overpays but satisfies",
                MoneyPayment.satisfied(offer, moneyStack(5000, 1), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertFalse("19×1$ is insufficient",
                MoneyPayment.satisfied(offer, moneyStack(100, 19), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertFalse("Empty slots are insufficient",
                MoneyPayment.satisfied(offer, ItemStack.EMPTY, ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("Money in slot 1 only also satisfies (value semantics)",
                MoneyPayment.satisfied(offer, ItemStack.EMPTY, moneyStack(2000, 1)));
        if (!r.passed()) return r;

        return pass("All-money satisfaction is purely value-based");
    }

    /** costB-absent offers accept payment split across both slots (relaxation). */
    private TestResult test_satisfied_split_payment() {
        MerchantOffer offer = moneyOffer(2000, 1); // price 20.00

        TestResult r = assertTrue("10$ + 10$ split across both slots",
                MoneyPayment.satisfied(offer, moneyStack(1000, 1), moneyStack(1000, 1)));
        if (!r.passed()) return r;
        r = assertTrue("15$ (3×5$) + 5$ (5×1$) mixed denominations split",
                MoneyPayment.satisfied(offer, moneyStack(500, 3), moneyStack(100, 5)));
        if (!r.passed()) return r;
        r = assertFalse("10$ + 5$ split is insufficient",
                MoneyPayment.satisfied(offer, moneyStack(1000, 1), moneyStack(500, 1)));
        if (!r.passed()) return r;

        return pass("Split payment across both slots is accepted");
    }

    /** Non-money contamination and renamed money never satisfy (nor get consumed). */
    private TestResult test_satisfied_contamination_and_renamed() {
        MerchantOffer offer = moneyOffer(2000, 1);

        TestResult r = assertFalse("Non-money stack in a money position fails",
                MoneyPayment.satisfied(offer, new ItemStack(Items.DIRT, 64), ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertFalse("Sufficient money + junk in the other slot still fails",
                MoneyPayment.satisfied(offer, moneyStack(2000, 1), new ItemStack(Items.DIRT, 1)));
        if (!r.passed()) return r;

        // Renamed money fails MoneyItem.isMoney (component mismatch) → non-money.
        ItemStack renamed = moneyStack(2000, 1);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Lucky Note"));
        r = assertFalse("Renamed money must be treated as non-money",
                MoneyPayment.satisfied(offer, renamed, ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertEquals("Renamed money has zero payment value",
                0L, MoneyPayment.stackValue(renamed));
        if (!r.passed()) return r;

        return pass("Contaminated and renamed payments are rejected");
    }

    /** Mixed offer (item cost preserved + money cost): per-side checks, both orientations. */
    private TestResult test_satisfied_mixed_offer_both_orientations() {
        // Enchanted-book style: costA = 1 book (item), costB = 20.00 money.
        MerchantOffer itemFirst = new MerchantOffer(new ItemCost(Items.BOOK, 1),
                Optional.of(new ItemCost(money(2000), 1)),
                new ItemStack(Items.ENCHANTED_BOOK), 0, 16, 2, 0.0f, 0);

        TestResult r = assertTrue("Book in slot 0 + exact money in slot 1",
                MoneyPayment.satisfied(itemFirst, new ItemStack(Items.BOOK, 1), moneyStack(2000, 1)));
        if (!r.passed()) return r;
        r = assertTrue("Book + different denominations (2×10$) in slot 1",
                MoneyPayment.satisfied(itemFirst, new ItemStack(Items.BOOK, 1), moneyStack(1000, 2)));
        if (!r.passed()) return r;
        r = assertFalse("Swapped orientation fails per-side (vanilla retries swapped)",
                MoneyPayment.satisfied(itemFirst, moneyStack(2000, 1), new ItemStack(Items.BOOK, 1)));
        if (!r.passed()) return r;
        r = assertFalse("Missing item side fails",
                MoneyPayment.satisfied(itemFirst, ItemStack.EMPTY, moneyStack(2000, 1)));
        if (!r.passed()) return r;
        r = assertFalse("Insufficient money side fails",
                MoneyPayment.satisfied(itemFirst, new ItemStack(Items.BOOK, 1), moneyStack(1000, 1)));
        if (!r.passed()) return r;

        // Reverse construction: costA = money, costB = item.
        MerchantOffer moneyFirst = new MerchantOffer(new ItemCost(money(2000), 1),
                Optional.of(new ItemCost(Items.BOOK, 1)),
                new ItemStack(Items.ENCHANTED_BOOK), 0, 16, 2, 0.0f, 0);
        r = assertTrue("Money in slot 0 + book in slot 1",
                MoneyPayment.satisfied(moneyFirst, moneyStack(1000, 2), new ItemStack(Items.BOOK, 1)));
        if (!r.passed()) return r;
        r = assertFalse("Money offer with book missing fails",
                MoneyPayment.satisfied(moneyFirst, moneyStack(1000, 2), ItemStack.EMPTY));
        if (!r.passed()) return r;

        return pass("Mixed offers check the item side vanilla-style and the money side by value");
    }

    // ========================================================================
    // consume
    // ========================================================================

    /** All four exact 20.00 payment forms consume fully with zero change. */
    private TestResult test_consume_exact_combos_no_change() {
        MerchantOffer offer = moneyOffer(2000, 1); // price 20.00
        long[][] forms = {{2000, 1}, {1000, 2}, {500, 4}, {100, 20}};
        for (long[] form : forms) {
            ItemStack a = moneyStack(form[0], (int) form[1]);
            long change = MoneyPayment.consume(offer, a, ItemStack.EMPTY);
            TestResult r = assertEquals("Exact form " + form[1] + "×" + form[0]
                    + " raw must yield no change", 0L, change);
            if (!r.passed()) return r;
            r = assertTrue("Exact form must be fully consumed", a.isEmpty());
            if (!r.passed()) return r;
        }
        return pass("Exact combinations are consumed without change");
    }

    /** A single 50$ note for a 20.00 price returns exactly 30.00 change. */
    private TestResult test_consume_minimal_overshoot() {
        MerchantOffer offer = moneyOffer(2000, 1);
        ItemStack a = moneyStack(5000, 1);
        long change = MoneyPayment.consume(offer, a, ItemStack.EMPTY);

        TestResult r = assertEquals("50$ − 20$ = 30.00 change", 3000L, change);
        if (!r.passed()) return r;
        r = assertTrue("The 50$ note is consumed", a.isEmpty());
        if (!r.passed()) return r;

        // With 2×50$ available, only ONE note is taken (minimal overshoot first,
        // then fewest notes).
        ItemStack two = moneyStack(5000, 2);
        change = MoneyPayment.consume(offer, two, ItemStack.EMPTY);
        r = assertEquals("Still 30.00 change", 3000L, change);
        if (!r.passed()) return r;
        r = assertEquals("Exactly one of the two notes consumed", 1, two.getCount());
        if (!r.passed()) return r;

        return pass("Overpayment consumes the minimal-overshoot combination");
    }

    /**
     * When slot values overlap, the exact combination with the fewest notes
     * wins, and slot 0 is preferred low ({@code k1} minimal on full ties).
     */
    private TestResult test_consume_prefers_fewest_notes() {
        MerchantOffer offer = moneyOffer(2000, 1); // price 20.00

        // Slot 0: 20×1$, slot 1: 2×10$ — both exact; 2 notes beat 20.
        ItemStack ones = moneyStack(100, 20);
        ItemStack tens = moneyStack(1000, 2);
        long change = MoneyPayment.consume(offer, ones, tens);
        TestResult r = assertEquals("No change on exact combo", 0L, change);
        if (!r.passed()) return r;
        r = assertEquals("1$ notes untouched (k1 = 0 preferred)", 20, ones.getCount());
        if (!r.passed()) return r;
        r = assertTrue("Both 10$ notes consumed", tens.isEmpty());
        if (!r.passed()) return r;

        // Same denomination in both slots: fewest total notes, then lowest k1.
        ItemStack a = moneyStack(1000, 2);
        ItemStack b = moneyStack(1000, 2);
        change = MoneyPayment.consume(offer, a, b);
        r = assertEquals("No change on exact combo", 0L, change);
        if (!r.passed()) return r;
        r = assertEquals("Two notes total consumed",
                2, 4 - a.getCount() - b.getCount());
        if (!r.passed()) return r;
        r = assertEquals("k1 minimal: slot 0 keeps both notes", 2, a.getCount());
        if (!r.passed()) return r;

        return pass("Consumption minimizes (overshoot, note count, slot-0 notes)");
    }

    /** Mixed offer: item side shrinks vanilla-style, money side by value with change. */
    private TestResult test_consume_mixed_offer() {
        MerchantOffer itemFirst = new MerchantOffer(new ItemCost(Items.BOOK, 1),
                Optional.of(new ItemCost(money(2000), 1)),
                new ItemStack(Items.ENCHANTED_BOOK), 0, 16, 2, 0.0f, 0);

        ItemStack books = new ItemStack(Items.BOOK, 3);
        ItemStack fifty = moneyStack(5000, 1);
        long change = MoneyPayment.consume(itemFirst, books, fifty);

        TestResult r = assertEquals("Money change 30.00 from the 50$ note", 3000L, change);
        if (!r.passed()) return r;
        r = assertEquals("Exactly one book consumed", 2, books.getCount());
        if (!r.passed()) return r;
        r = assertTrue("The 50$ note is consumed", fifty.isEmpty());
        if (!r.passed()) return r;

        return pass("Mixed offers consume the item side exactly and the money side by value");
    }

    // ========================================================================
    // Value conservation
    // ========================================================================

    /**
     * Simulates the shift-click exhaustion loop: trade while satisfied,
     * consuming payment and re-adding the change each iteration. Total value
     * must be conserved: {@code initial == trades × price + residual}.
     */
    private TestResult test_value_conservation_loop() {
        MerchantOffer offer = moneyOffer(2000, 1); // price 20.00 = 2000 raw
        long price = 2000L;

        // Start: one 50$ note + 17×1$ = 6700 raw.
        ItemStack a = moneyStack(5000, 1);
        ItemStack b = moneyStack(100, 17);
        long initial = MoneyPayment.stackValue(a) + MoneyPayment.stackValue(b);

        // Change notes that fit neither payment slot go to the player
        // inventory in-game (deliverChange step 2) — modeled as a pocket that
        // still counts toward the residual but is not re-spent.
        long pocket = 0L;
        int trades = 0;
        while (MoneyPayment.satisfied(offer, a, b) && trades < 100) {
            long change = MoneyPayment.consume(offer, a, b);
            trades++;
            // Re-add the change like deliverChange: merge into a same-type
            // slot, then fill an empty slot, else pocket (inventory).
            for (ItemStack note : MoneyPayment.makeChange(change)) {
                if (!a.isEmpty() && ItemStack.isSameItemSameComponents(a, note)) {
                    a.grow(note.getCount());
                } else if (!b.isEmpty() && ItemStack.isSameItemSameComponents(b, note)) {
                    b.grow(note.getCount());
                } else if (a.isEmpty()) {
                    a = note;
                } else if (b.isEmpty()) {
                    b = note;
                } else {
                    pocket += MoneyPayment.stackValue(note);
                }
            }
        }

        // Trace: trade 1 consumes the 50$ note (change 30.00 → 20$ note into
        // the empty slot 0, 10$ note fits nowhere → pocket); trade 2 consumes
        // the 20$ note exactly. Residual: 17×1$ in slots + 10$ in the pocket.
        long residual = MoneyPayment.stackValue(a) + MoneyPayment.stackValue(b) + pocket;
        TestResult r = assertEquals("Expected number of trades", 2, trades);
        if (!r.passed()) return r;
        r = assertEquals("Value conservation: initial == trades × price + residual",
                initial, trades * price + residual);
        if (!r.passed()) return r;
        r = assertEquals("Pocketed change (the 10$ note)", 1000L, pocket);
        if (!r.passed()) return r;
        r = assertFalse("Loop must terminate unsatisfied (slot residual < price)",
                MoneyPayment.satisfied(offer, a, b));
        if (!r.passed()) return r;

        return pass("Value is conserved across the trade-and-change loop");
    }
}
