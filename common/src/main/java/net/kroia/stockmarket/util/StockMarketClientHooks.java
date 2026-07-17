package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.RecipeImageExporter;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.networking.request.NewsHistoryRequest;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsToastCatchUp;
import net.kroia.stockmarket.screen.DevTestScreen;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.NewsScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.uiElements.NewsToast;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        Minecraft.getInstance().submit(TradeScreen::openScreen);
        return InteractionResult.SUCCESS;
    }

    /**
     * Notifies the currently open StockMarket screen that a market has been deleted
     * on the server. Called by {@code MarketRemovedPacket} on the client main thread
     * after the client market caches have already been purged.
     * Screens that keep a market selection (TradeScreen, ManagementScreen) override
     * {@link StockMarketGuiScreen#onMarketRemoved} to deselect the dead market.
     *
     * @param marketID the market that was deleted on the server
     */
    public static void notifyScreenMarketRemoved(ItemID marketID)
    {
        if (Minecraft.getInstance().screen instanceof StockMarketGuiScreen guiScreen) {
            guiScreen.onMarketRemoved(marketID);
        }
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
        Minecraft.getInstance().getToasts().addToast(new NewsToast(record));
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

    public static void openGUI(OpenUIPacket.GUIType type)
    {
        Minecraft mc = Minecraft.getInstance();
        switch(type)
        {
            case DEVELOPMENT:
            {
                if(StockMarketMod.ENABLE_DEV_FEATURES) {
                    DevTestScreen screen = new DevTestScreen();
                    mc.setScreen(screen);
                }
                break;
            }
            case MANAGEMENT:
            {
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
            mc.gui.getChat().addMessage(Component.literal(message));
        });
    }
}
