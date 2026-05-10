package net.kroia.stockmarket.pluginsystem.screen;

import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.networking.stream.PluginRuntimeDataStream;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.util.UUID;

/**
 * Base GUI element for plugin-specific UI.
 * Plugin developers extend this to provide custom settings UI and/or
 * live runtime data visualization.
 *
 * <p>The framework calls {@link #setPluginSyncData(PluginSyncData)} internally
 * to pass the plugin's metadata and subscribed markets. Subclasses override
 * {@link #onPluginSyncDataReceived(PluginSyncData)} to react to this data.</p>
 *
 * <p>Runtime data streaming is managed via {@link #startDataStream()} and
 * {@link #stopDataStream()}. Override {@link #onRuntimeDataReceived} to
 * process incoming data.</p>
 */
public class PluginGuiElement extends StockMarketGuiElement {

    private UUID runtimeStreamID = null;
    private PluginSyncData pluginSyncData = null;

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
     * Called internally by the framework to provide plugin sync data.
     * Stores the data and forwards to {@link #onPluginSyncDataReceived(PluginSyncData)}.
     * Do not override — override {@link #onPluginSyncDataReceived} instead.
     *
     * @param data the plugin sync data containing subscribed markets and metadata
     */
    public final void setPluginSyncData(PluginSyncData data) {
        this.pluginSyncData = data;
        onPluginSyncDataReceived(data);
    }

    /**
     * Called when the plugin's sync data is received from the server.
     * Override to initialize the element with market data, subscriptions, etc.
     *
     * @param data the plugin sync data containing subscribed markets and metadata
     */
    protected void onPluginSyncDataReceived(PluginSyncData data) {
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
                this::onRuntimeDataReceived,
                () -> {
                    runtimeStreamID = null;
                }
        );
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
     * @param data the runtime data payload
     */
    protected void onRuntimeDataReceived(PluginRuntimeDataStream.RuntimeData data) {
        // Default: no-op. Subclasses override to process data.
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {

    }
}
