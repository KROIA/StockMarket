package net.kroia.stockmarket.api.integration;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.OpenTradeScreenPacket;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import org.jetbrains.annotations.NotNull;

/**
 * Client-side integration entry points for other mods (SM-313), e.g. BankSystem's company screen.
 * <p>
 * <b>Reflection contract:</b> BankSystem has no compile dependency on StockMarket and calls this class
 * by reflection, by name. The class name, the method names and their signatures must never change.
 * <p>
 * All methods are <b>client thread only</b> (the render thread, e.g. from a screen's button handler or
 * {@code init()}). Unlike {@link IStockMarketIntegration} (server thread, bank master), these work on
 * any client: on a slave server too, through the player's own server and client market cache.
 */
public final class StockMarketClientIntegration {

    private StockMarketClientIntegration() {}

    /**
     * Opens the StockMarket trade screen with {@code market} selected, without the StockMarket block.
     * The server still checks every order.
     * <ul>
     *   <li>Returns {@code false} and sends nothing if the client market cache has no market for
     *       {@code market}: no market exists, it was deleted, this slave cannot resolve its item, the
     *       master was down when the player joined, or the player is not in a world.</li>
     *   <li>Otherwise sends a request to the player's own server and returns {@code true}. That server
     *       opens the screen the same way as the block: a slave without a master link refuses and
     *       the player gets the "not connected" chat message instead. A closed market or one under
     *       maintenance opens normally and shows its banner.</li>
     * </ul>
     * Client thread only.
     *
     * @param market the market to show (the company share item for BankSystem)
     * @return true if the request was sent; the screen opens when the server answers
     */
    public static boolean openTradeScreen(@NotNull ItemID market) {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.MARKET_MANAGER == null || client.MARKET_MANAGER.getMarket(market) == null)
            return false;
        OpenTradeScreenPacket.send(market);
        return true;
    }

    /**
     * Creates a live candlestick chart of {@code market} to embed in another mod's screen.
     * <ul>
     *   <li>The returned element is a {@link DisplayCandlestickChart} and implements {@link AutoCloseable}.</li>
     *   <li>It subscribes to the market's live prices on creation if the market is in the client cache,
     *       else (no market, deleted, unresolvable on this slave) it stays empty and retries on every
     *       render, so it fills in once the market appears.</li>
     *   <li>It unsubscribes on {@code close()} and when it is removed from its GUI ({@code setRoot(null)}),
     *       and reconnects lazily on the next render if it is shown again.</li>
     *   <li>Its zoom and position are stored separately from the trade screen's; the first time it
     *       starts from the player's trade screen view of that market.</li>
     * </ul>
     * <b>The host MUST call {@code ((AutoCloseable) chart).close()} in its screen's {@code removed()}</b>,
     * otherwise the price stream subscription leaks for the rest of the session. Calling it more than
     * once is harmless.
     * <p>
     * Client thread only.
     *
     * @param market the market to chart (the company share item for BankSystem)
     * @return a new chart element; never null
     */
    public static @NotNull GuiElement createPriceChart(@NotNull ItemID market) {
        return DisplayCandlestickChart.createEmbedded(market);
    }
}
