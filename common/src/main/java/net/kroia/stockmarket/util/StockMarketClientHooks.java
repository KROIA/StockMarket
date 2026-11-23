package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.RecipeImageExporter;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.MarketChangedPacket;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.networking.request.NewsHistoryRequest;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsToastCatchUp;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.NewsScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.uiElements.NewsToast;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class StockMarketClientHooks {
    /**
     * The one client hook for market changes (SM-310): tells the currently open StockMarket screen that a
     * market was added, removed, opened, closed, or entered/left maintenance. Called by the client market
     * manager after its cache is updated, for a {@code MarketChangedPacket} and for a {@code requestMarkets()}
     * answer that brought new markets. Scheduled on the client main thread, since the cache fill can complete
     * on a request-future thread.
     * <p>
     * REMOVED goes to {@link StockMarketGuiScreen#onMarketRemoved} (deselect the dead market, the cache is
     * already purged); every other kind to {@link StockMarketGuiScreen#onMarketsChanged}.
     *
     * @param kind     what changed
     * @param marketID the market that changed, or null when several markets were added at once
     */
    public static void notifyScreenMarketsChanged(MarketChangedPacket.Kind kind, @Nullable ItemID marketID)
    {
        Minecraft.getInstance().execute(() -> {
            if (!(net.kroia.modutilities.gui.client.ClientCompat.screen() instanceof StockMarketGuiScreen guiScreen))
                return;
            if (kind == MarketChangedPacket.Kind.REMOVED) {
                if (marketID != null)
                    guiScreen.onMarketRemoved(marketID);
            } else {
                guiScreen.onMarketsChanged(kind, marketID);
            }
        });
    }

    /**
     * Opens the newspaper screen ({@code NewsScreen}). Entry points: the newspaper
     * item's right-click and the {@code /stockmarket news} command (via
     * {@code OpenUIPacket.GUIType.NEWS}, T-076).
     * Scheduled on the client main thread like the other screen-open hooks.
     */
    public static void openNewsScreen()
    {
        Minecraft.getInstance().submit(NewsScreen::openScreen);
    }

    /**
     * Shows the news headline toast for a freshly published record — but only if
     * this player opted in via the "enable news popups" checkbox in the newspaper
     * screen ({@code PlayerPreferences.isNewsToastEnabled()}, default off).
     * Players who did not opt in get <b>no notification at all</b> (user decision:
     * no chat message ever; the toast is the only push notification).
     * <p>
     * Called by {@code NewsPublishedPacket}'s client handler on the client main
     * thread, after the record was appended to the client news cache.
     *
     * @param record the record that was just published; null is ignored
     */
    public static void showNewsToastIfEnabled(NewsRecord record)
    {
        if (record == null)
            return;
        // Preferences are fetched once at join; an unfetched state yields the
        // defaults (toast flag off), so nothing is ever shown by accident.
        if (!StockMarketGuiElement.getPlayerPreferences().isNewsToastEnabled())
            return;
        Minecraft.getInstance().getToasts().addToast(new NewsToast(record)); // mc<=1.21.1
        //Minecraft.getInstance().getToastManager().addToast(new NewsToast(record)); // mc>=1.21.2 mc<=26.1
        //Minecraft.getInstance().gui.toastManager().addToast(new NewsToast(record)); // mc>=26.2
    }

    /**
     * Join-time news toast catch-up (T-077, plan §6.6). Players who join after an
     * event published never received the live {@code NewsPublishedPacket}; for
     * <b>opted-in</b> players this fetches the first (newest) news history page and
     * re-announces the freshest headlines as toasts.
     * <p>
     * Called by {@code ClientMarketManager.onPlayerJoin} — chained onto the
     * {@code fetchPlayerPreferences()} future, so {@code prefs} is always the value
     * <b>fetched from the server</b>, never the pre-fetch client default. Non-opted-in
     * players get nothing at all: no toast, no chat, no sound — not even the history
     * fetch is sent (the newspaper screen fetches its own pages on open anyway).
     * <p>
     * Selection rules (window, cap, clock-skew handling) live in
     * {@link NewsToastCatchUp#selectCatchUpToasts}. The fetched page also seeds the
     * per-connection {@link net.kroia.stockmarket.news.ClientNewsCache} (uid-deduped
     * merge) so the newspaper opens instantly populated. Works identically on slave
     * servers: {@code NewsHistoryRequest} auto-routes slave→master.
     *
     * @param backend the client backend captured at join (news cache + networking);
     *                null-safe
     * @param prefs   the player's preferences <b>as fetched from the server</b>;
     *                null-safe
     */
    public static void runNewsToastCatchUp(@Nullable StockMarketModBackend.ClientInstances backend,
                                           @Nullable PlayerPreferences prefs)
    {
        if (backend == null || backend.NETWORKING == null || backend.NEWS_CACHE == null)
            return;
        // Opt-in gate: non-opted-in players get no visible or invisible effect at all.
        if (prefs == null || !prefs.isNewsToastEnabled())
            return;

        backend.NETWORKING.NEWS_HISTORY_REQUEST
                .sendRequestToServer(new NewsHistoryRequest.InputData(0, NewsToastCatchUp.CATCH_UP_FETCH_SIZE))
                .thenAccept(response -> Minecraft.getInstance().execute(() -> {
                    // Guard against a disconnect while the request was in flight.
                    if (response == null || Minecraft.getInstance().player == null)
                        return;
                    List<NewsRecord> page = response.records();
                    // Seed the cache first (uid-deduped merge) — live publishes that
                    // raced ahead of this response keep their newest-first position.
                    backend.NEWS_CACHE.seed(page);
                    // Re-read the live prefs mirror: the player may have toggled the
                    // opt-in checkbox or hit "Clear" (T-109) between the join fetch
                    // and this response. T-109: honor newsClearedBeforeMs so no toast
                    // fires on rejoin for pre-clear events.
                    PlayerPreferences livePrefs = StockMarketGuiElement.getPlayerPreferences();
                    List<NewsRecord> toToast = NewsToastCatchUp.selectCatchUpToasts(
                            page,
                            System.currentTimeMillis(),
                            NewsToastCatchUp.CATCH_UP_WINDOW_MS,
                            NewsToastCatchUp.MAX_CATCH_UP_TOASTS,
                            livePrefs.isNewsToastEnabled(),
                            livePrefs.getNewsClearedBeforeMs());
                    for (NewsRecord record : toToast)
                        showNewsToastIfEnabled(record); // re-checks the opt-in per toast
                }));
    }

    /**
     * Opens a server-requested StockMarket screen (see {@link OpenUIPacket}).
     *
     * @param type   the screen to open
     * @param market the market the screen opens on (TRADE only, SM-313), or null for the screen's default
     */
    public static void openGUI(OpenUIPacket.GUIType type, @org.jetbrains.annotations.Nullable net.kroia.banksystem.util.ItemID market)
    {
        Minecraft mc = Minecraft.getInstance();
        switch(type)
        {
            case DEVELOPMENT:
            {
                if(StockMarketMod.ENABLE_DEV_FEATURES) {
                    // DevTestScreen lives in the dev-only testing package, so it is opened reflectively.
                    try {
                        Class.forName("net.kroia.stockmarket.testing.DevTestScreen").getMethod("open").invoke(null);
                    } catch (ReflectiveOperationException e) {
                        StockMarketMod.LOGGER.error("Could not open the dev test screen", e);
                    }
                }
                break;
            }
            case MANAGEMENT:
            {
                // ManagementScreen.openScreen() itself has the T-125 untrusted
                // slave guard (sends a chat notice and returns). Calling
                // through here so both /stockmarket manage command opens AND
                // any future direct-open path go through the same gate.
                ManagementScreen.openScreen();
                break;
            }
            case EXPORT_RECIPES:
            {
                exportRecipeImages();
                break;
            }
            case NEWS:
            {
                // Player-level /stockmarket news command (T-076): the command runs
                // server-side, so the screen open travels via OpenUIPacket like the
                // management GUI. openNewsScreen() schedules on the client main thread.
                openNewsScreen();
                break;
            }
            case TRADE:
            {
                // StockMarket block (T-245) or StockMarketClientIntegration.openTradeScreen (SM-313):
                // the server checked the master link before sending this.
                Minecraft.getInstance().submit(() -> TradeScreen.openScreen(market));
                break;
            }
        }
    }

    /**
     * Exports all StockMarket mod recipe images to the game directory.
     * Runs on the render thread via Minecraft.execute() for GL safety.
     * Output: <gameDir>/recipe_exports/stockmarket/recipe_<name>.png
     */
    private static void exportRecipeImages()
    {
        Minecraft mc = Minecraft.getInstance();

        // Schedule on the render thread to ensure GL context is available
        mc.execute(() -> {
            Path exportDir = mc.gameDirectory.toPath().resolve("recipe_exports/stockmarket");
            int successCount = 0;

            // Recipe 1: Trading Software
            // Pattern: "ST" (1x2 shaped recipe)
            // S = banksystem:software, T = minecraft:emerald
            {
                String[] pattern = {"ST"};
                Map<Character, String> keys = Map.of(
                        'S', "banksystem:software",
                        'T', "minecraft:emerald"
                );
                Path outputPath = exportDir.resolve("recipe_trading_software.png");
                if (RecipeImageExporter.exportShapedRecipe(pattern, keys, "stockmarket:trading_software", outputPath))
                {
                    successCount++;
                }
            }

            // Recipe 2: Stock Market Display Block
            // Pattern: 3x3 shaped recipe
            // I = minecraft:iron_nugget, G = minecraft:glass_pane,
            // D = banksystem:display, E = minecraft:emerald
            {
                String[] pattern = {"IGI", "GDG", "IEI"};
                Map<Character, String> keys = Map.of(
                        'I', "minecraft:iron_nugget",
                        'G', "minecraft:glass_pane",
                        'D', "banksystem:display",
                        'E', "minecraft:emerald"
                );
                Path outputPath = exportDir.resolve("recipe_stockmarket_display_block.png");
                if (RecipeImageExporter.exportShapedRecipe(pattern, keys, "stockmarket:stockmarket_display_block", outputPath))
                {
                    successCount++;
                }
            }

            // Notify the player in chat
            String message = "StockMarket: Exported " + successCount + "/2 recipe images to " + exportDir;
            mc.gui.getChat().addMessage(Component.literal(message)); // mc<=1.21.11
            //mc.gui.getChat().addClientSystemMessage(Component.literal(message)); // mc>=26.1 mc<=26.1
            //mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message)); // mc>=26.2
        });
    }
}
