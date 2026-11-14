package net.kroia.stockmarket.client;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModBackend.StartupRefusal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * SM-325, singleplayer: after the StockMarket data migration refused a world, the integrated server stops cleanly.
 * Once it has shut down, this client tick replaces whatever the client shows with a screen that says why, and its
 * button goes back to the world list.
 */
public final class StartupRefusalClient {

    private StartupRefusalClient() {
    }

    /**
     * Client tick (Architectury CLIENT_POST, also without a level). Does nothing unless a refusal is pending and the
     * integrated server has finished stopping.
     *
     * @param mc the client
     */
    public static void onClientTick(Minecraft mc) {
        StartupRefusal refusal = StockMarketModBackend.getSingleplayerRefusal();
        if (refusal == null)
            return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null && !server.isShutdown())
            return; // still stopping
        StockMarketModBackend.clearSingleplayerRefusal();
        Component message = switch (refusal.kind()) {
            case TOO_NEW -> Component.translatable("gui.stockmarket.load_refused.too_new",
                    refusal.args().get(0), refusal.args().get(1));
            case MIGRATION_FAILED -> refusal.args().isEmpty()
                    ? Component.translatable("gui.stockmarket.load_refused.migration_failed_no_backup")
                    : Component.translatable("gui.stockmarket.load_refused.migration_failed", refusal.args().get(0));
        };
        mc.disconnect(new AlertScreen(() -> mc.setScreen(new SelectWorldScreen(new TitleScreen())),
                Component.translatable("gui.stockmarket.load_refused.title"), message), false);
    }
}
