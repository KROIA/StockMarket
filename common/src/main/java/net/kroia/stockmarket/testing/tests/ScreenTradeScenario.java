package net.kroia.stockmarket.testing.tests;

import net.kroia.testkit.TestResult;
import net.kroia.testkit.scenario.Scenario;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

/**
 * SM-352 (TestKit TK-7): two real game clients trade through the trade screen. The step list is built here, in shared
 * code, so the servers ({@link ScreenTradeScenarioSuite}) and the clients ({@link ScreenTradeClientScenarioSuite}) run
 * the same scenario. Each side passes its own step bodies and {@link #elsewhere()} for the other side's steps, which
 * it never runs: a dedicated server so never references the client-only {@link ScreenTradeClientSteps}.
 * <p>
 * Instances: {@code tester1} (player {@value #SELLER}), {@code tester2} (player {@value #BUYER}) and {@code master}.
 * Setups: mp-clients (both clients on the master) and slave2-clients (tester1 on slaveA, tester2 on slaveB, both
 * slaves trusted; the slaves have no steps). {@link Scenario} skips on every instance when the setup lacks one of
 * the step instances, so every other cell skips.
 */
public final class ScreenTradeScenario {
    private ScreenTradeScenario() {}

    public static final String NAME = "sm_screen_trade";
    /** The autotest client players (fake names, never a real player's). */
    public static final String SELLER = "AutoTester1", BUYER = "AutoTester2";
    /**
     * The traded item (master set-up and both clients). A vanilla item no default preset and no other test uses, present
     * in every supported MC version. It must differ from {@link CrossServerTradeScenarioSuite#ITEM}: in slave2-clients
     * both scenarios run at once on the master, and each set-up deletes and recreates its market, which would wipe the
     * other scenario's open orders.
     */
    static final ItemStack ITEM = new ItemStack(Items.COBWEB);

    /**
     * @param setUp   master: clean market, seller holds the items, buyer the money
     * @param check   master: both orders filled, balances moved; deletes the market
     * @param inWorld client: the player is in the world (both clients, so the master funds only online players)
     * @param sell    tester1: limit sell on the trade screen at the shown price
     * @param buy     tester2: limit buy on the trade screen at the shown price
     */
    static Scenario build(Supplier<TestResult> setUp, Supplier<TestResult> check, Supplier<TestResult> inWorld,
                          Supplier<TestResult> sell, Supplier<TestResult> buy) {
        return Scenario.named(NAME)
                .step("tester1", SELLER + " is in the world", inWorld)
                .step("tester2", BUYER + " is in the world", inWorld)
                .step("master", "clean market, " + SELLER + " holds the items, " + BUYER + " the money", setUp)
                .step("tester1", SELLER + " sells on the trade screen at the shown price", sell)
                .step("tester2", BUYER + " buys on the trade screen at the same price", buy)
                .step("master", "orders filled and balances moved", check);
    }

    /** Body for a step another instance runs; never called (each instance runs only its own label's steps). */
    static TestResult elsewhere() {
        return TestResult.fail(NAME, "this step belongs to another instance");
    }
}
