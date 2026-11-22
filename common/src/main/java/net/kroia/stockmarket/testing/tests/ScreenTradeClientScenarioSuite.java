package net.kroia.stockmarket.testing.tests;

import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;

/**
 * SM-352: the game client's part of {@link ScreenTradeScenario} (a {@link Side#CLIENT_SCENARIO} suite: runs on the
 * client's test thread after its GUI suites). Registered on the client only (DevBootstrap); the step bodies are in
 * {@link ScreenTradeClientSteps}.
 */
public class ScreenTradeClientScenarioSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.SCENARIO;
    }

    @Override
    public Side side() {
        return Side.CLIENT_SCENARIO;
    }

    @Override
    public void registerTests() {
        addTest("screen_trade", this::test_screenTrade);
    }

    private TestResult test_screenTrade() {
        return ScreenTradeScenario.build(ScreenTradeScenario::elsewhere, ScreenTradeScenario::elsewhere,
                ScreenTradeClientSteps::inWorld, () -> ScreenTradeClientSteps.trade(false),
                () -> ScreenTradeClientSteps.trade(true)).runOnClient("screen_trade");
    }
}
