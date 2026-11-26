package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.banking.clientdata.BankAccountData;
import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.uiElements.trading_panel.LimitOrderPanel;
import net.kroia.stockmarket.screen.uiElements.trading_panel.TradingPanel;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.gui.Gui;
import net.kroia.testkit.gui.GuiNode;
import net.kroia.testkit.gui.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static net.kroia.stockmarket.testing.tests.CrossServerTradeScenarioSuite.ITEMS;
import static net.kroia.stockmarket.testing.tests.ScreenTradeScenario.ITEM;

/**
 * SM-352: the client step bodies of {@link ScreenTradeScenario}. Client-only (GUI and screen classes): only the
 * client-registered {@link ScreenTradeClientScenarioSuite} references it, so a dedicated server never loads it.
 * Runs on the client's test thread; client state is touched through {@link Gui#onClient}.
 * <p>
 * The limit price is the price the trade screen shows (its "Market" button), so no master value has to reach the
 * client; the master checks afterwards that both orders filled at its remembered price.
 */
final class ScreenTradeClientSteps {
    private ScreenTradeClientSteps() {}

    private static final String NAME = "screen_trade";
    /** Client ticks to wait for the screen, its market data, the order row and the fill. */
    private static final int OPEN_TICKS = 200, FILL_TICKS = 400;
    private static final long WAIT_MS = 10_000;
    private static final String PANEL_KEY = "gui.stockmarket.limit_order_panel.";
    /** TradingPanel's limit order tab index (its LIMIT_ORDER_TAB, private there). */
    private static final int LIMIT_ORDER_TAB = 1;

    /** Steps 1-2: this client's player is in the world. */
    static TestResult inWorld() {
        boolean in = Gui.onClient(() -> Minecraft.getInstance().player != null) && StockMarketModBackend.getClientInstances() != null;
        return in ? TestResult.pass(NAME, null) : TestResult.fail(NAME, "not in a world");
    }

    /**
     * Steps 4-5: opens the trade screen on the test market ({@link ScreenTradeScenario#ITEM}), selects the limit order tab, types {@link CrossServerTradeScenarioSuite#ITEMS}
     * into the quantity box, takes the shown market price ("Market" button) and clicks Sell or Buy (visible elements
     * only). The sell passes when its open order shows in the orders list (no buyer yet), the buy when the bought
     * items show in the player's bank.
     *
     * @param buy true for tester2's buy, false for tester1's sell
     */
    static TestResult trade(boolean buy) {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return fail("not in a world");
        if (Gui.onClient(StockMarketGuiElement::isUntrustedSlave))
            return fail("this client's server is an untrusted slave: its players may not trade");
        try {
            ItemID id = get(Gui.onClient(() -> ItemID.getOrRegisterFromItemStackClientSide(ITEM)));
            // the master created the market after this client joined: load it if the create broadcast did not
            if (!Gui.waitUntil(() -> client.MARKET_MANAGER.getMarket(id) != null, 20)) {
                Gui.onClient(() -> { client.MARKET_MANAGER.requestMarkets(); });
                if (!Gui.waitUntil(() -> client.MARKET_MANAGER.getMarket(id) != null, OPEN_TICKS))
                    return fail("market " + id + " never reached this client's market list");
            }
            UUID self = Gui.onClient(() -> Minecraft.getInstance().player.getUUID());
            double itemsBefore = buy ? itemBalance(self, id) : 0;

            Gui.onClient(() -> TradeScreen.openScreen(id)); // preselects the market
            Gui.waitForScreen(TradeScreen.class, OPEN_TICKS);
            GuiNode panel = limitPanel();
            if (panel == null)
                return fail("no shown limit order panel within " + OPEN_TICKS + " ticks; trading area: " + TradeScreenGuiTestSuite.describeTradingArea());
            LimitOrderPanel limit = (LimitOrderPanel) panel.handle();
            if (!Gui.waitUntil(() -> limit.getCurrentMarketPrice() > 0, OPEN_TICKS))
                return fail("the trade screen shows no market price");

            // quantity: clear the box, then type the amount
            GuiNode box = first(panel, n -> n.handle() instanceof TextBox); // the quantity box comes first
            if (box == null) return fail("no shown quantity box in the limit order panel");
            TextBox quantityBox = (TextBox) box.handle();
            int len = Gui.onClient(() -> quantityBox.getText().length());
            Gui.click(box);
            for (int i = 0; i < len; i++) Gui.key(Keys.RIGHT);
            for (int i = 0; i < len; i++) Gui.key(Keys.BACKSPACE);
            Gui.type(String.valueOf(ITEMS));
            double quantity = Gui.onClient(limit::getQuantity);
            if (quantity != ITEMS) return fail("quantity box reads " + quantity + " after typing " + ITEMS);

            // limit price = the shown market price
            GuiNode market = button(panel, PANEL_KEY + "market_price");
            if (market == null) return fail("no shown 'Market' price button");
            Gui.click(market);
            double price = Gui.onClient(limit::getPrice); // the master checks it against its remembered price
            if (price <= 0) return fail("limit price " + price + " after clicking 'Market'");

            // Buy/Sell stay hidden until the panel's balances arrive (async bank request): re-query the snapshot each tick
            GuiNode trade = null;
            for (int t = 0; t < OPEN_TICKS && trade == null; t++) {
                GuiNode fresh = shown(n -> n.handle() == limit);
                trade = fresh == null ? null : button(fresh, PANEL_KEY + (buy ? "buy" : "sell"));
                if (trade == null) Gui.waitTicks(1);
            }
            if (trade == null) {
                String state = Gui.onClient(() -> "marketOpen=" + limit.isMarketOpen() + ", quantity=" + limit.getQuantity()
                        + ", price=" + limit.getPrice() + ", itemBalance=" + limit.getItemBalance() + ", moneyBalance=" + limit.getMoneyBalance());
                return fail("no shown " + (buy ? "Buy" : "Sell") + " button in the limit order panel within " + OPEN_TICKS
                        + " ticks (" + state + "; client bank items=" + itemBalance(self, id) + ")");
            }
            Button tradeButton = (Button) trade.handle();
            if (!Gui.waitUntil(tradeButton::isClickable, OPEN_TICKS)) return fail((buy ? "Buy" : "Sell") + " button stays disabled");
            Gui.click(trade);

            if (!buy) {
                String row = Gui.onClient(() -> Component.translatable("gui.stockmarket.order_row.sell").getString());
                for (int t = 0; t < OPEN_TICKS; t += 5) {
                    if (!Gui.findAll(n -> n.handle() instanceof Label && row.equals(n.text())).isEmpty())
                        return TestResult.pass(NAME, "sell " + ITEMS + " @ " + price + " listed");
                    Gui.waitTicks(5);
                }
                return fail("no open sell order in the orders list within " + OPEN_TICKS + " ticks of clicking Sell (" + ITEMS + " @ " + price + ")");
            }
            double items = itemsBefore;
            for (int t = 0; t < FILL_TICKS && items < itemsBefore + ITEMS - 1e-9; t += 20) {
                Gui.waitTicks(20);
                items = itemBalance(self, id);
            }
            if (items < itemsBefore + ITEMS - 1e-9)
                return fail("bank shows " + items + " items, expected " + (itemsBefore + ITEMS) + " after buying " + ITEMS + " @ " + price);
            return TestResult.pass(NAME, "bought " + ITEMS + " @ " + price);
        } finally {
            Gui.close();
        }
    }

    /** @return the shown limit order panel, selecting its tab first; null if none within {@link #OPEN_TICKS} */
    private static @Nullable GuiNode limitPanel() {
        for (int t = 0; t < OPEN_TICKS; t++) {
            GuiNode panel = shown(n -> n.handle() instanceof LimitOrderPanel);
            if (panel != null) return panel;
            // the panels show once the market's trading status says it is open; the limit tab may not be selected
            GuiNode trading = shown(n -> n.handle() instanceof TradingPanel);
            if (trading != null) {
                TradingPanel tabs = (TradingPanel) trading.handle();
                // programmatic tab switch: TabElement hit-tests the real cursor (TestKit TK-11 / MU)
                Gui.onClient(() -> tabs.selectTab(LIMIT_ORDER_TAB));
            }
            Gui.waitTicks(1);
        }
        return null;
    }

    /** @return the first shown node of the open screen matching {@code filter}, or null */
    private static @Nullable GuiNode shown(Predicate<GuiNode> filter) {
        return Gui.findAll(n -> n.visible() && filter.test(n)).stream().findFirst().orElse(null);
    }

    /** @return the shown button of {@code panel} with the translated text of {@code key}, or null */
    private static @Nullable GuiNode button(GuiNode panel, String key) {
        String text = Gui.onClient(() -> Component.translatable(key).getString());
        return first(panel, n -> n.handle() instanceof Button && text.equals(n.text()));
    }

    /** Depth first over shown nodes only: a hidden element's children do not count. */
    private static @Nullable GuiNode first(GuiNode node, Predicate<GuiNode> filter) {
        if (!node.visible()) return null;
        if (filter.test(node)) return node;
        for (GuiNode child : node.children()) {
            GuiNode found = first(child, filter);
            if (found != null) return found;
        }
        return null;
    }

    /** @return the player's personal bank balance of {@code item} in real items (0 without a bank) */
    private static double itemBalance(UUID self, ItemID item) {
        BankAccountData account = get(Gui.onClient(() -> BankSystemMod.getAPI().getClientBankManager().getPersonalBankAccountDataAsync(self)));
        BankData data = account == null ? null : account.bankData.get(item);
        return data == null ? 0 : data.getRealBalance();
    }

    private static <T> T get(CompletableFuture<T> future) {
        try {
            return future.get(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("no answer within " + WAIT_MS + " ms: " + e, e);
        }
    }

    private static TestResult fail(String message) {
        return TestResult.fail(NAME, message);
    }
}
