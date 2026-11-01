package net.kroia.stockmarket.pluginsystem.screen;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.stream.PluginRuntimeDataStream;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.banksystem.util.ItemID;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base GUI element for plugin-specific UI.
 * Plugin developers extend this to provide custom settings UI and/or
 * live runtime data visualization.
 *
 * <p>Type parameters correspond to the server-side {@code ServerPlugin<TSettings, TRuntimeData>}.</p>
 *
 * <p>The framework calls {@link #setPluginSyncData(PluginSyncData)} internally
 * to pass the plugin's metadata and subscribed markets. Subclasses override
 * {@link #onPluginSyncDataReceived(PluginSyncData, Object)} to react to this data.</p>
 *
 * <p>Runtime data streaming is managed via {@link #startDataStream()} and
 * {@link #stopDataStream()}. Override {@link #onRuntimeDataReceived} to
 * process incoming data.</p>
 *
 * @param <TSettings>    the custom settings type (use Void if no settings)
 * @param <TRuntimeData> the runtime data type (use Void if no runtime data)
 */
public class PluginGuiElement<TSettings, TRuntimeData> extends StockMarketGuiElement {

    private UUID runtimeStreamID = null;
    private PluginSyncData pluginSyncData = null;
    private @Nullable ItemID activeMarketID = null;

    /**
     * Returns whether this plugin GUI element needs a dedicated full screen
     * instead of being embedded inline in the plugin entry widget.
     * <p>
     * Override and return {@code true} for complex plugins that need more space
     * (e.g. charts, visualizations). Default is {@code false} (inline mode).
     *
     * @return true to use a dedicated PluginDetailScreen, false to embed inline
     */
    public boolean needsCustomScreen() {
        return false;
    }

    /**
     * Returns the StreamCodec for this plugin's custom settings type.
     * Must match the server-side plugin's codec.
     * Return null if this plugin has no custom settings.
     */
    protected @Nullable StreamCodec<ByteBuf, TSettings> customSettingsCodec() {
        return null;
    }

    /**
     * Returns the StreamCodec for this plugin's runtime data type.
     * Must match the server-side plugin's codec.
     * Return null if this plugin does not stream runtime data.
     */
    protected @Nullable StreamCodec<ByteBuf, TRuntimeData> runtimeDataCodec() {
        return null;
    }

    /**
     * Called internally by the framework to provide plugin sync data.
     * Stores the data, decodes custom settings, and forwards to
     * {@link #onPluginSyncDataReceived(PluginSyncData, Map)}.
     * Do not override — override {@link #onPluginSyncDataReceived} instead.
     *
     * @param data the plugin sync data containing subscribed markets and metadata
     */
    public final void setPluginSyncData(PluginSyncData data) {
        this.pluginSyncData = data;
        Map<ItemID, TSettings> settingsMap = decodeAllSettings(data.getCustomSettings());
        onPluginSyncDataReceived(data, settingsMap);
    }

    /**
     * Called when the plugin's sync data is received from the server.
     * Override to initialize the element with market data, subscriptions, etc.
     *
     * @param data              the plugin sync data containing subscribed markets and metadata
     * @param customSettingsMap the decoded per-market custom settings map, or null if not available
     */
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, TSettings> customSettingsMap) {
        // Default: no-op. Subclasses override.
    }

    /** Returns the stored plugin sync data, or null if not yet received. */
    public PluginSyncData getPluginSyncData() {
        return pluginSyncData;
    }

    /** Returns the plugin instance UUID, or null if sync data not yet received. */
    public UUID getPluginInstanceID() {
        return pluginSyncData != null ? pluginSyncData.getInstanceID() : null;
    }

    /**
     * Sets the active market for settings editing.
     * Called by the management screen when a market button is clicked.
     */
    public final void setActiveMarket(@Nullable ItemID marketID) {
        this.activeMarketID = marketID;
        onActiveMarketChanged(marketID);
    }

    /**
     * Returns the currently active market for settings editing.
     */
    public @Nullable ItemID getActiveMarket() {
        return activeMarketID;
    }

    /**
     * Called when the active market for settings editing changes.
     * Override to update the settings UI for the newly selected market.
     */
    protected void onActiveMarketChanged(@Nullable ItemID marketID) {
        // Default: no-op. Subclasses override to update settings display.
    }

    /**
     * Starts the runtime data stream from the server for this plugin.
     * Uses the plugin instance ID from the stored sync data.
     * Call when the GUI element becomes visible and needs live data.
     */
    public void startDataStream() {
        UUID instanceID = getPluginInstanceID();
        if (instanceID == null || runtimeStreamID != null) return;

        runtimeStreamID = StreamSystem.startServerToClientStream(
                BACKEND_INSTANCES.NETWORKING.PLUGIN_RUNTIME_DATA_STREAM,
                instanceID,
                this::handleRuntimeDataInternal,
                () -> {
                    runtimeStreamID = null;
                }
        );
    }

    /**
     * Internal handler that decodes the raw runtime data payload and forwards
     * the typed result to {@link #onRuntimeDataReceived(Object)}.
     */
    private void handleRuntimeDataInternal(PluginRuntimeDataStream.RuntimeData data) {
        StreamCodec<ByteBuf, TRuntimeData> codec = runtimeDataCodec();
        if (codec == null) return;
        ByteBuf buf = Unpooled.wrappedBuffer(data.payload);
        try {
            TRuntimeData decoded = codec.decode(buf);
            onRuntimeDataReceived(decoded);
        } finally {
            buf.release();
        }
    }

    /**
     * Stops the runtime data stream.
     * Call when the GUI element is hidden or the screen is closed.
     */
    public void stopDataStream() {
        if (runtimeStreamID != null) {
            StreamSystem.stopStream(runtimeStreamID);
            runtimeStreamID = null;
        }
    }

    /**
     * Called when runtime data is received from the server.
     * Override to process plugin-specific runtime data.
     *
     * @param data the decoded runtime data
     */
    protected void onRuntimeDataReceived(TRuntimeData data) {
        // Default: no-op. Subclasses override to process data.
    }

    /**
     * Sends custom settings to the server for this plugin.
     * Subclasses call this when the user changes plugin-specific settings.
     * The response is delivered via {@link #onCustomSettingsResponse}.
     *
     * @param settings the typed custom settings to send
     */
    protected final void sendCustomSettings(ItemID marketID, TSettings settings) {
        StreamCodec<ByteBuf, TSettings> codec = customSettingsCodec();
        UUID id = getPluginInstanceID();
        if (codec == null || id == null || settings == null || marketID == null) {
            StockMarketMod.LOGGER.warn("[PluginGuiElement] sendCustomSettings aborted: codec={}, id={}, market={}, settings={}",
                    codec != null ? "ok" : "null", id != null ? id : "null", marketID != null ? marketID : "null", settings != null ? "ok" : "null");
            return;
        }
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, settings);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            getPluginManager().requestUpdateCustomSettings(id, marketID, bytes).thenAccept(response -> {
                if (response.success() && response.confirmedPayload() != null) {
                    TSettings confirmed = decodeSettings(response.confirmedPayload());
                    onCustomSettingsResponse(true, response.marketID(), confirmed);
                } else {
                    StockMarketMod.LOGGER.warn("[PluginGuiElement] Custom settings update failed for plugin {} market {}", id, marketID);
                    onCustomSettingsResponse(false, marketID, null);
                }
            });
        } finally {
            buf.release();
        }
    }

    /**
     * Called when the server responds to a custom settings update request.
     * Override to handle confirmation or rejection of settings changes.
     *
     * @param success           true if the settings were applied on the server
     * @param confirmedSettings the server's confirmed decoded settings, or null on failure
     */
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable TSettings confirmedSettings) {
        // Default: no-op. Subclasses override to react to settings confirmation.
    }

    /**
     * Decodes custom settings bytes using this element's settings codec.
     *
     * @param data the raw settings bytes, or null
     * @return the decoded settings, or null if codec is null or data is null
     */
    private @Nullable TSettings decodeSettings(byte[] data) {
        StreamCodec<ByteBuf, TSettings> codec = customSettingsCodec();
        if (codec == null || data == null) return null;
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        try {
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * Decodes per-market custom settings from a map of raw bytes.
     */
    private @Nullable Map<ItemID, TSettings> decodeAllSettings(@Nullable Map<ItemID, byte[]> data) {
        if (data == null) return null;
        StreamCodec<ByteBuf, TSettings> codec = customSettingsCodec();
        if (codec == null) return null;
        Map<ItemID, TSettings> result = new HashMap<>();
        for (Map.Entry<ItemID, byte[]> entry : data.entrySet()) {
            TSettings settings = decodeSettings(entry.getValue());
            if (settings != null) {
                result.put(entry.getKey(), settings);
            }
        }
        return result.isEmpty() ? null : result;
    }


    /**
     * Called by the screen to provide a reference to the shared candlestick chart.
     * Subclasses can override to register overlays on the chart.
     *
     * @param chart the chart widget, or null to clear
     */
    public void setCandlestickChart(@Nullable CandlestickChart chart) {
        // no-op by default
    }

    /**
     * Called by the screen to provide a reference to the shared orderbook volume histogram.
     * Subclasses can override to register overlays or use the histogram for visualization.
     *
     * @param histogram the histogram widget, or null to clear
     */
    public void setOrderbookVolumeHistogram(@Nullable OrderbookVolumeHistogram histogram) {
        // no-op by default
    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {

    }
}
