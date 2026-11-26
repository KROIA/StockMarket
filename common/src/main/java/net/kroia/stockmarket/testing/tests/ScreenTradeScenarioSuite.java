package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.banking.User;
import net.kroia.testkit.TestResult;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * SM-352: the servers' part of {@link ScreenTradeScenario} (a {@link Side#SCENARIO} suite, runs on every server of
 * the setup). Only the master has steps; it reuses {@link CrossServerTradeScenarioSuite}'s master helpers with the
 * real autotest players instead of fake ones, on its own market ({@link ScreenTradeScenario#ITEM}). Master values (price, balances) stay in {@link MasterState} in this
 * JVM: the clients take the price from their trade screen.
 */
public class ScreenTradeScenarioSuite extends CrossServerTradeScenarioSuite {

    @Override
    public void registerTests() {
        addTest("screen_trade", this::test_screenTrade);
    }

    /** AutoTester1 sells {@link #ITEMS} items to AutoTester2 through their trade screens; the master checks the fill. */
    private TestResult test_screenTrade() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server in this JVM");
        MasterState s = new MasterState();
        return ScreenTradeScenario.build(() -> setUp(s), () -> checkOnMaster(s), ScreenTradeScenario::elsewhere,
                ScreenTradeScenario::elsewhere, ScreenTradeScenario::elsewhere).run("screen_trade", server);
    }

    /** Finds both players' BankSystem users (added by their join, also on a slave) and sets up market and funds. */
    private TestResult setUp(MasterState s) {
        IAsyncBankManager bank = BankSystemMod.getAPI().getServerBankManager().getAsync();
        User seller = user(bank, ScreenTradeScenario.SELLER), buyer = user(bank, ScreenTradeScenario.BUYER);
        if (seller == null || buyer == null)
            return fail((seller == null ? ScreenTradeScenario.SELLER : ScreenTradeScenario.BUYER) + " is no BankSystem user on the master");
        return setUpOnMaster(s, ScreenTradeScenario.ITEM, seller.getUUID(), null, ScreenTradeScenario.SELLER,
                buyer.getUUID(), null, ScreenTradeScenario.BUYER);
    }

    /** @return the user, waiting up to {@link #WAIT_MS} for a slave's join to reach the master; null if none */
    private @Nullable User user(IAsyncBankManager bank, String name) {
        for (long end = System.currentTimeMillis() + WAIT_MS; ; pump(250)) {
            User user = await(bank.getUserByNameAsync(name), WAIT_MS);
            if (user != null || System.currentTimeMillis() > end) return user;
        }
    }
}
