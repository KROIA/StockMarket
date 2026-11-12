package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.modutilities.testing.autotest.AutoTest;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.block.custom.StockMarketBlock;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.testing.PacketTap;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MultiServerUtils;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult; // mc<=1.21.1
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * T-246: StockMarket screens on a slave whose master is down (the T-245 refusal), in the ModUtilities
 * {@code slave-masterdown} autotest cell ({@link AutoTest#isMasterDown()}: the master was killed while the
 * autotest client played on this slave). Skips everywhere else.
 * <p>
 * Uses the autotest client's real {@link ServerPlayer}. A refusal sends that player the "not connected" chat
 * line; the tests check the decision and the message component, not what the client received.
 */
public class MasterDownTestSuite extends TestSuite {

    private static final String SM_MESSAGE_KEY = "message.stockmarket.slave_not_connected";

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MASTER_DOWN;
    }

    @Override
    public void registerTests() {
        addTest("can_interact_false", this::test_canInteractFalse);
        addTest("open_ui_refuses_master_screens", this::test_openUiRefusesMasterScreens);
        addTest("block_use_refuses_with_sm_message", this::test_blockUseRefuses);
        addTest("newspaper_use_refuses_with_sm_message", this::test_newspaperRefuses);
        addTest("news_command_refuses_with_sm_message", this::test_newsCommandRefuses);
    }

    /** @return a skip result outside the slave-masterdown cell, or null when the test should run. */
    private @Nullable TestResult skipUnlessMasterDown() {
        if (!AutoTest.isMasterDown())
            return skip("not the slave-masterdown cell");
        if (!BankSystemMod.getAPI().isSlave())
            return skip("not a slave");
        return null;
    }

    /** @return the autotest client's player on this slave, or null if none is online. */
    private @Nullable ServerPlayer player() {
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    /** With the master gone, the slave reports that StockMarket cannot be used (both overloads). */
    private TestResult test_canInteractFalse() {
        TestResult s = skipUnlessMasterDown();
        if (s != null) return s;
        TestResult r = assertFalse("canInteractWithStockMarket()", MultiServerUtils.canInteractWithStockMarket());
        if (!r.passed()) return r;
        ServerPlayer p = player();
        if (p == null) return fail("no player on the slave");
        return assertFalse("canInteractWithStockMarket(uuid)", MultiServerUtils.canInteractWithStockMarket(p.getUUID()));
    }

    /**
     * {@link OpenUIPacket#canOpen} (the check inside {@code sendToClient}) refuses every screen that needs the master
     * and still allows the dev-only ones (DEVELOPMENT, EXPORT_RECIPES). Only the decision is called, so no screen
     * opens on the client.
     */
    private TestResult test_openUiRefusesMasterScreens() {
        TestResult s = skipUnlessMasterDown();
        if (s != null) return s;
        ServerPlayer p = player();
        if (p == null) return fail("no player on the slave");
        for (OpenUIPacket.GUIType type : OpenUIPacket.GUIType.values()) {
            boolean expected = !type.needsMaster();
            TestResult r = assertEquals("canOpen(" + type + ")", expected, OpenUIPacket.canOpen(p, type));
            if (!r.passed()) return r;
        }
        TestResult r = assertFalse("DEVELOPMENT needs no master", OpenUIPacket.GUIType.DEVELOPMENT.needsMaster());
        if (!r.passed()) return r;
        return assertFalse("EXPORT_RECIPES needs no master", OpenUIPacket.GUIType.EXPORT_RECIPES.needsMaster());
    }

    /**
     * Right-clicking the StockMarket block, with an empty hand and with a non-software item, fails on the server
     * (the BankSystem base class refuses before {@code openGui}), and the refusal line is StockMarket's message
     * via the {@code getNoMasterMessage()} hook. The block need not be placed: the refusal comes before any
     * world access.
     */
    private TestResult test_blockUseRefuses() {
        TestResult s = skipUnlessMasterDown();
        if (s != null) return s;
        ServerPlayer p = player();
        if (p == null) return fail("no player on the slave");
        if (!(StockMarketBlocks.STOCK_MARKET_BLOCK.get() instanceof StockMarketBlock block))
            return fail("STOCK_MARKET_BLOCK is not a StockMarketBlock");

        BlockState state = block.defaultBlockState();
        BlockPos pos = p.blockPosition();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        TestResult r = assertEquals("useWithoutItem", InteractionResult.FAIL,
                state.useWithoutItem(p.level(), p, hit));
        if (!r.passed()) return r;
        Object itemResult = state.useItemOn(new ItemStack(Items.STICK), p.level(), p, InteractionHand.MAIN_HAND, hit);
        r = assertEquals("useItemOn (item in hand)", ItemInteractionResult.FAIL, itemResult); // mc<=1.21.1
        //r = assertEquals("useItemOn (item in hand)", InteractionResult.FAIL, itemResult); // mc>=1.21.2
        if (!r.passed()) return r;

        if (!(block.getNoMasterMessage().getContents() instanceof TranslatableContents tc))
            return fail("getNoMasterMessage() is not translatable");
        return assertEquals("refusal message key", SM_MESSAGE_KEY, tc.getKey());
    }

    /** SM-285: using the newspaper item is refused: the SM "not connected" chat line, no OpenUIPacket. */
    private TestResult test_newspaperRefuses() {
        return refusesNewsScreen("newspaper use", p ->
                StockMarketItems.NEWSPAPER.get().use(p.level(), p, InteractionHand.MAIN_HAND));
    }

    /** SM-285: {@code /stockmarket news} run by the player is refused the same way. */
    private TestResult test_newsCommandRefuses() {
        return refusesNewsScreen("/stockmarket news", p -> {
            try {
                getServer().getCommands().getDispatcher().execute("stockmarket news", p.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("command did not parse: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Runs {@code action} as the autotest player and checks what the server sent that player (through a
     * {@link PacketTap}): the StockMarket "not connected" chat line and no {@link OpenUIPacket}, so no screen opens.
     *
     * @param label  what was done, for messages
     * @param action the player action that would open the news screen
     */
    private TestResult refusesNewsScreen(String label, java.util.function.Consumer<ServerPlayer> action) {
        TestResult s = skipUnlessMasterDown();
        if (s != null) return s;
        ServerPlayer p = player();
        if (p == null) return fail("no player on the slave");
        try (PacketTap tap = PacketTap.open(getServer(), p)) {
            if (tap == null) return fail("no network connection for the player");
            try {
                action.accept(p);
            } catch (RuntimeException e) {
                return fail(label + " threw: " + e.getMessage());
            }
            pumpUntil(() -> tap.findTranslatable(SM_MESSAGE_KEY) != null, 3000);
            TestResult r = assertNotNull(label + ": " + SM_MESSAGE_KEY + " sent, got " + tap.systemMessages(),
                    tap.findTranslatable(SM_MESSAGE_KEY));
            if (!r.passed()) return r;
            pump(250); // a screen request would go out right after the check; give it time to show up
            return assertFalse(label + ": no OpenUIPacket sent", tap.sawPayload(OpenUIPacket.class));
        }
    }
}
