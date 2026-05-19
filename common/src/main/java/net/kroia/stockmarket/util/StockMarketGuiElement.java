package net.kroia.stockmarket.util;

import net.kroia.banksystem.api.bankmanager.IClientBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IClientMarket;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.api.preset.IAsyncPresetManager;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public abstract class StockMarketGuiElement extends GuiElement {

    /**
     * Draws an X mark (two crossing diagonal lines) at the given position.
     * Used by MarketItemButton close button and order marker cancel button.
     *
     * @param target    the GuiElement to draw on
     * @param x         left edge of the X area
     * @param y         top edge of the X area
     * @param width     width of the X area
     * @param height    height of the X area
     * @param padding   inset from edges
     * @param thickness line thickness
     * @param color     line color (ARGB)
     */
    public static void drawXMark(GuiElement target, int x, int y, int width, int height, int padding, float thickness, int color) {
        target.drawLine(x + padding, y + padding, x + width - padding, y + height - padding, thickness, color);
        target.drawLine(x + width - padding, y + padding, x + padding, y + height - padding, thickness, color);
    }
    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    protected static @Nullable ClientMarket selectedMarket;

    /** Client-side cache of the player's trading preferences, fetched from server on join. */
    protected static @Nullable PlayerPreferences playerPreferences;

    /**
     * Comparator that groups markets by item type using reversed registry path segments.
     * e.g. "minecraft:red_stained_glass" sorts as "glass_stained_red",
     * grouping all glass, ore, planks, etc. together.
     */
    public static final Comparator<ItemID> MARKET_TYPE_COMPARATOR =
            Comparator.comparing(id -> marketTypeSortKey(id.getName()));

    // Reverses underscore-separated segments of a registry path for type-based sorting.
    public static String marketTypeSortKey(String registryName) {
        int colon = registryName.indexOf(':');
        String path = colon >= 0 ? registryName.substring(colon + 1) : registryName;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            sb.append(parts[i]);
            if (i > 0) sb.append('_');
        }
        return sb.toString();
    }

    public final static float hoverToolTipFontSize = 0.8f;
    public final static int padding = 4;
    public final static int spacing = 4;
    public final static int defaultElementHeight = 20;




    public StockMarketGuiElement() {
        super();
    }
    public StockMarketGuiElement(int x, int y, int width, int height) {
        super(x, y, width, height);
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
    protected IAsyncPresetManager getPresetManager()
    {
        return BACKEND_INSTANCES.PRESET_MANAGER;
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


    protected @Nullable ClientMarket getMarket(ItemID marketID)
    {
        return BACKEND_INSTANCES.MARKET_MANAGER.getMarket(marketID);
    }
    public static void selectMarket(ItemID marketID) {
        if(marketID == null)
            selectedMarket = null;
        else
            selectedMarket = BACKEND_INSTANCES.MARKET_MANAGER.getMarket(marketID);
    }
    public static @Nullable ClientMarket getSelectedMarket()
    {
        return selectedMarket;
    }

    /**
     * Fetches player preferences from the server. Call on player join.
     */
    public static void fetchPlayerPreferences() {
        BACKEND_INSTANCES.NETWORKING.PLAYER_PREFERENCES_GET_REQUEST.sendRequestToServer((byte) 0)
            .thenAccept(prefs -> {
                playerPreferences = prefs;
            });
    }

    /**
     * Returns cached player preferences, or empty if not yet fetched.
     */
    public static @NotNull PlayerPreferences getPlayerPreferences() {
        if (playerPreferences == null)
            return new PlayerPreferences();
        return playerPreferences;
    }

    /**
     * Updates player preferences locally and syncs to server.
     * @param prefs the updated preferences to save
     */
    public static void updatePlayerPreferences(PlayerPreferences prefs) {
        playerPreferences = prefs;
        BACKEND_INSTANCES.NETWORKING.PLAYER_PREFERENCES_UPDATE_REQUEST.sendRequestToServer(prefs);
    }

    protected void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[StockMarketGuiElement] " + msg);
    }
    protected void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiElement] " + msg);
    }
    protected void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[StockMarketGuiElement] " + msg, e);
    }
    protected void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[StockMarketGuiElement] " + msg);
    }
    protected void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[StockMarketGuiElement] " + msg);
    }
}
