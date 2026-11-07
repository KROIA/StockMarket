package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.AdminFlagSync;
import net.kroia.stockmarket.util.AdminFlagSync.DeopFollowUp;
import net.kroia.stockmarket.util.AdminFlagSync.ListenerAction;

import java.util.List;
import java.util.Optional;

/**
 * Pure-logic tests for the admin-flag / LuckPerms sync decisions in {@link AdminFlagSync}
 * (mirrors BankSystem's AdminFlagSyncTests), every input combination of each rule.
 */
public class AdminFlagSyncTestSuite extends TestSuite {

    private static final boolean[] BOOLS = {false, true};
    private static final List<Optional<Boolean>> EFFECTIVE = List.of(Optional.empty(), Optional.of(false), Optional.of(true));

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.USER;
    }

    @Override
    public void registerTests() {
        addTest("push_to_luckperms_all_combinations", this::pushToLuckPerms);
        addTest("listener_action_all_combinations", this::listenerAction);
        addTest("migrate_all_combinations", this::migrate);
        addTest("deop_follow_up_group_wins", this::deopFollowUp);
        addTest("new_user_apply_only_true", this::newUserApply);
        addTest("master_accepts_slave_sync_all_combinations", this::acceptSlaveSync);
    }

    private TestResult pushToLuckPerms() {
        for (boolean setting : BOOLS)
            for (boolean lp : BOOLS) {
                TestResult res = assertEquals("setting=" + setting + " lp=" + lp, setting && lp,
                        AdminFlagSync.shouldPushToLuckPerms(setting, lp));
                if (!res.passed()) return res;
            }
        return pass("ok");
    }

    private TestResult listenerAction() {
        for (boolean setting : BOOLS)
            for (boolean master : BOOLS)
                for (boolean trusted : BOOLS) {
                    ListenerAction expected = !setting ? ListenerAction.IGNORE
                            : master ? ListenerAction.WRITE_LOCAL
                            : trusted ? ListenerAction.FORWARD : ListenerAction.IGNORE;
                    TestResult res = assertEquals("setting=" + setting + " master=" + master + " trusted=" + trusted,
                            expected, AdminFlagSync.listenerAction(setting, master, trusted));
                    if (!res.passed()) return res;
                }
        // Untrusted slave never writes or forwards, whatever the setting.
        return assertEquals("untrusted slave, setting on", ListenerAction.IGNORE,
                AdminFlagSync.listenerAction(true, false, false));
    }

    private TestResult migrate() {
        for (boolean setting : BOOLS)
            for (boolean lp : BOOLS)
                for (boolean migrated : BOOLS) {
                    TestResult res = assertEquals("setting=" + setting + " lp=" + lp + " migrated=" + migrated,
                            setting && lp && !migrated, AdminFlagSync.shouldMigrate(setting, lp, migrated));
                    if (!res.passed()) return res;
                }
        return pass("ok");
    }

    private TestResult deopFollowUp() {
        for (Optional<Boolean> effective : EFFECTIVE) {
            DeopFollowUp expected = effective.equals(Optional.of(true)) ? DeopFollowUp.RESTORE_AND_NOTE : DeopFollowUp.NONE;
            TestResult res = assertEquals("effective=" + effective, expected, AdminFlagSync.deopFollowUp(effective));
            if (!res.passed()) return res;
        }
        return pass("ok");
    }

    private TestResult newUserApply() {
        for (Optional<Boolean> effective : EFFECTIVE) {
            TestResult res = assertEquals("effective=" + effective, effective.equals(Optional.of(true)),
                    AdminFlagSync.newUserApply(effective));
            if (!res.passed()) return res;
        }
        return pass("ok");
    }

    /** Only a server-internal write from a trusted slave, with the master's setting on, is applied. */
    private TestResult acceptSlaveSync() {
        for (boolean fromSlave : BOOLS)
            for (boolean fromPlayer : BOOLS)
                for (boolean trusted : BOOLS)
                    for (boolean setting : BOOLS) {
                        TestResult res = assertEquals("slave=" + fromSlave + " player=" + fromPlayer + " trusted=" + trusted
                                        + " setting=" + setting, fromSlave && !fromPlayer && trusted && setting,
                                AdminFlagSync.acceptSlaveSync(fromSlave, fromPlayer, trusted, setting));
                        if (!res.passed()) return res;
                    }
        return pass("ok");
    }
}
