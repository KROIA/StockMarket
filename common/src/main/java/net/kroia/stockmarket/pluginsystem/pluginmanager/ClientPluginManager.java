package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginmanager.IAsyncPluginManager;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.networking.request.PluginCustomSettingsRequest;
import net.kroia.stockmarket.networking.request.PluginReorderRequest;
import net.kroia.stockmarket.networking.request.PluginSettingsRequest;
import net.kroia.stockmarket.networking.request.PluginSubscriptionRequest;
import net.kroia.stockmarket.networking.stream.PluginPerformanceSnapshot;
import net.kroia.stockmarket.pluginsystem.plugin.ClientPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ClientPluginManager implements IClientPluginManager {

    protected static StockMarketModBackend.ClientInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ClientInstances backend) {
        BACKEND_INSTANCES = backend;
        ClientPlugin.setBackend(backend);
    }
    private final IAsyncPluginManager asyncPluginManager;
    private List<PluginSyncData> pluginDataList = new ArrayList<>();

    /**
     * Latest per-plugin performance snapshot pushed by the server-side
     * {@code PluginPerformanceStream} (T-137). {@code null} until the first
     * snapshot arrives — UI code MUST null-check this before rendering.
     * Updated on the client network thread; read on the render thread. Access
     * through {@link #getLatestTimingSnapshot()} which reads the volatile ref.
     */
    private volatile @Nullable PluginPerformanceSnapshot latestTimingSnapshot = null;

    /**
     * Active timing-stream subscription ID, or {@code null} if no subscription
     * is currently open. Set by {@link #startTimingStream()} and cleared by
     * {@link #stopTimingStream()} or the framework's stream-stopped callback.
     */
    private @Nullable UUID timingStreamID = null;


    public ClientPluginManager()
    {
        asyncPluginManager = AsyncPluginManager.createClientManager();
    }

    public void updatePluginData(List<PluginSyncData> data) {
        this.pluginDataList = new ArrayList<>(data);
        // Screen refresh will be added in Phase 2
    }

    @Override
    public List<PluginSyncData> getPluginDataList() {
        return Collections.unmodifiableList(pluginDataList);
    }

    @Override
    public int getPluginCount() {
        return pluginDataList.size();
    }

    @Override
    public int getEnabledPluginCount() {
        return (int) pluginDataList.stream().filter(PluginSyncData::isEnabled).count();
    }

    @Override
    public CompletableFuture<List<PluginSyncData>> requestPluginList() {
        CompletableFuture<List<PluginSyncData>> future = new CompletableFuture<>();
        AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_LIST_REQUEST, 0).thenAccept((response) -> {
            pluginDataList = new ArrayList<>(response);
            future.complete(response);
        });
        return future;
    }

    @Override
    public CompletableFuture<PluginSettingsRequest.OutputData> requestUpdateSettings(UUID instanceID, GenericPluginData updatedData) {
        PluginSettingsRequest.InputData input = new PluginSettingsRequest.InputData(instanceID, updatedData);
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_SETTINGS_REQUEST, input);
    }

    @Override
    public CompletableFuture<PluginCustomSettingsRequest.OutputData> requestUpdateCustomSettings(UUID instanceID, ItemID marketID, byte[] payload) {
        PluginCustomSettingsRequest.InputData input = new PluginCustomSettingsRequest.InputData(instanceID, marketID, payload);
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_CUSTOM_SETTINGS_REQUEST, input);
    }

    @Override
    public CompletableFuture<PluginSubscriptionRequest.OutputData> requestUpdateSubscription(UUID instanceID, ItemID marketID, boolean subscribe) {
        PluginSubscriptionRequest.InputData input = new PluginSubscriptionRequest.InputData(instanceID, marketID, subscribe);
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_SUBSCRIPTION_REQUEST, input);
    }

    @Override
    public CompletableFuture<Boolean> requestCreatePlugin(String pluginTypeID) {
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_CREATE_REQUEST, pluginTypeID);
    }

    @Override
    public CompletableFuture<Boolean> requestDeletePlugin(UUID instanceID) {
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_DELETE_REQUEST, instanceID);
    }

    @Override
    public CompletableFuture<List<PluginSyncData>> requestReorderPlugin(UUID instanceID, int direction) {
        PluginReorderRequest.InputData input = new PluginReorderRequest.InputData(instanceID, direction);
        CompletableFuture<List<PluginSyncData>> future = new CompletableFuture<>();
        AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_REORDER_REQUEST, input).thenAccept((response) -> {
            pluginDataList = new ArrayList<>(response);
            future.complete(response);
        });
        return future;
    }

    /* ----------------------------------------------------------------------------------------------------------------
     *                     PLUGIN PERFORMANCE STREAM (T-137)
     * --------------------------------------------------------------------------------------------------------------*/

    /**
     * Opens the master-wide plugin timing stream so subsequent
     * {@link #getLatestTimingSnapshot()} calls return live data. Safe to call
     * more than once — a second call while a subscription is already open is
     * a no-op. Intended to be invoked when the Plugin Management screen opens.
     * <p>
     * The stream is trust- and admin-gated on the server (see
     * {@code PluginPerformanceStream}); an untrusted slave or a non-admin
     * subscriber will simply never receive a snapshot, which the client
     * observes as {@link #getLatestTimingSnapshot()} staying {@code null}.
     */
    public void startTimingStream() {
        if (timingStreamID != null) return;
        timingStreamID = StreamSystem.startServerToClientStream(
                BACKEND_INSTANCES.NETWORKING.PLUGIN_PERFORMANCE_STREAM,
                (byte) 0,
                snapshot -> this.latestTimingSnapshot = snapshot,
                () -> {
                    // Called by the framework when the server tears the
                    // stream down (e.g. trust revoked, admin flag removed).
                    timingStreamID = null;
                }
        );
    }

    /**
     * Closes the plugin timing subscription opened by
     * {@link #startTimingStream()}. Safe to call when no subscription is
     * open. Intended to be invoked when the Plugin Management screen closes.
     */
    public void stopTimingStream() {
        if (timingStreamID != null) {
            StreamSystem.stopStream(timingStreamID);
            timingStreamID = null;
        }
    }

    /**
     * Returns the latest per-plugin performance snapshot pushed by the server,
     * or {@code null} if the stream is not yet running or no snapshot has
     * arrived yet. UI code MUST null-check the result before use.
     * <p>
     * The returned instance is safe to read from any thread and is immutable
     * (its {@code entries} list is unmodifiable). A subsequent broadcast will
     * replace the reference wholesale, not mutate the existing object.
     *
     * @return the latest timing snapshot, or {@code null} if none has arrived
     */
    public @Nullable PluginPerformanceSnapshot getLatestTimingSnapshot() {
        return latestTimingSnapshot;
    }




    protected void info(String message) {
        BACKEND_INSTANCES.LOGGER.info("[ClientPluginManager]: "+message);
    }
    protected void error(String message) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager]: "+message);
    }
    protected void error(String message, Throwable throwable) {
        BACKEND_INSTANCES.LOGGER.error("[ClientPluginManager]: "+message, throwable);
    }
    protected void warn(String message) {
        BACKEND_INSTANCES.LOGGER.warn("[ClientPluginManager]: "+message);
    }
    protected void debug(String message) {
        BACKEND_INSTANCES.LOGGER.debug("[ClientPluginManager]: "+message);
    }
}
