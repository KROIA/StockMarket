package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.bankmanager.IClientBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.GuiScreen;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public abstract class StockMarketGuiScreen extends GuiScreen {

    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        StockMarketGuiElement.setBackend(backend);
    }


    public static final float guiScale = 0.5f;

    protected StockMarketGuiScreen(Component pTitle) {
        super(pTitle);
        setGuiScale(guiScale);
        BACKEND_INSTANCES.MARKET_MANAGER.requestMarkets();
    }
    protected StockMarketGuiScreen(Component pTitle, Screen parent) {
        super(pTitle, parent);
        setGuiScale(guiScale);
        BACKEND_INSTANCES.MARKET_MANAGER.requestMarkets();
    }

    /**
     * Called on the client main thread when the server reports that a market was
     * deleted (see {@code MarketRemovedPacket}). Invoked after the client market
     * caches have been purged, so {@code getMarket(marketID)} already returns null.
     * <p>
     * Default implementation does nothing. Screens that hold a market selection
     * (TradeScreen, ManagementScreen) override this to deselect the dead market and
     * refresh their market lists. Note: only the currently displayed screen is
     * notified — screens hidden behind a popup must re-validate their selection
     * when they resume (TradeScreen does this in {@code tick()}).
     *
     * @param marketID the market that no longer exists on the server
     */
    public void onMarketRemoved(ItemID marketID)
    {
        // Default: nothing to do. Screens without a market selection ignore this.
    }

    protected IClientMarketManager getMarketManager()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER;
    }
    protected IClientBankManager getBankManager()
    {
        return BACKEND_INSTANCES.BANK_SYSTEM_API.getClientBankManager();
    }
    protected List<ItemID> getAvailableMarkets()
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getAvailableMarkets();
    }
    protected IClientPluginManager getPluginManager()
    {
        return BACKEND_INSTANCES.PLUGIN_MANAGER;
    }

    protected @Nullable ClientMarket getMarket(ItemID itemID)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getMarket(itemID);
    }

    /**
     * Whether the server this client is connected to is the MASTER server
     * (single servers count as their own master). The flag is synced once at
     * player join via {@code PlayerJoinSyncPacket} → {@link ClientSettings}.
     * <p>
     * Used to gate master-only UI such as the "Mod Settings" button in the
     * ManagementScreen. This is a UI convenience only — the server independently
     * enforces master + permission checks in {@code ModSettingsRequest}.
     *
     * @return true if connected to the master server, false on slaves or when
     *         the sync has not arrived yet
     */
    protected static boolean isMasterServer()
    {
        return BACKEND_INSTANCES != null
                && BACKEND_INSTANCES.SETTINGS != null
                && BACKEND_INSTANCES.SETTINGS.isMasterServer();
    }

    protected LocalPlayer getThisPlayer()
    {
        return Minecraft.getInstance().player;
    }
    protected UUID getThisPlayerUUID()
    {
        return getThisPlayer().getUUID();
    }
    protected String getThisPlayerName()
    {
        return getThisPlayer().getDisplayName().getString();
    }




    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[StockMarketGuiScreen] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiScreen] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiScreen] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[StockMarketGuiScreen] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[StockMarketGuiScreen] " + msg);
    }
}
