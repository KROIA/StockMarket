package net.kroia.stockmarket.pluginsystem.pluginmanager;

import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.pluginmanager.IAsyncPluginManager;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.stockmarket.networking.request.PluginCustomSettingsRequest;
import net.kroia.stockmarket.networking.request.PluginReorderRequest;
import net.kroia.stockmarket.networking.request.PluginSettingsRequest;
import net.kroia.stockmarket.pluginsystem.plugin.ClientPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;

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
    public CompletableFuture<PluginCustomSettingsRequest.OutputData> requestUpdateCustomSettings(UUID instanceID, byte[] payload) {
        PluginCustomSettingsRequest.InputData input = new PluginCustomSettingsRequest.InputData(instanceID, payload);
        return AsynchronousRequestResponseSystem.sendRequestToServer(BACKEND_INSTANCES.NETWORKING.PLUGIN_CUSTOM_SETTINGS_REQUEST, input);
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
