package net.kroia.stockmarket.api.pluginmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.networking.request.PluginCustomSettingsRequest;
import net.kroia.stockmarket.networking.request.PluginSettingsRequest;
import net.kroia.stockmarket.networking.request.PluginSubscriptionRequest;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side plugin manager interface.
 * Provides access to synced plugin data received from the server.
 * Data is fetched on-demand via requests when the management GUI is opened.
 */
public interface IClientPluginManager {
    /** Returns the last synced list of plugin data (metadata + subscriptions). */
    List<PluginSyncData> getPluginDataList();

    /** Returns the total number of plugins registered on the server. */
    int getPluginCount();

    /** Returns the number of currently enabled plugins. */
    int getEnabledPluginCount();

    /** Requests the full plugin list from the server. Returns a future that completes with the synced data. */
    CompletableFuture<List<PluginSyncData>> requestPluginList();

    /** Sends a settings update request for a plugin instance to the server. */
    CompletableFuture<PluginSettingsRequest.OutputData> requestUpdateSettings(UUID instanceID, GenericPluginData updatedData);

    /** Sends a reorder request to move a plugin up or down in the execution order. */
    CompletableFuture<List<PluginSyncData>> requestReorderPlugin(UUID instanceID, int direction);

    /** Sends a custom settings update request for a plugin instance to the server. */
    CompletableFuture<PluginCustomSettingsRequest.OutputData> requestUpdateCustomSettings(UUID instanceID, byte[] payload);

    /** Sends a subscription change request for a plugin instance to the server. */
    CompletableFuture<PluginSubscriptionRequest.OutputData> requestUpdateSubscription(UUID instanceID, ItemID marketID, boolean subscribe);

    /** Sends a request to create a new plugin instance of the given type on the server. */
    CompletableFuture<Boolean> requestCreatePlugin(String pluginTypeID);

    /** Sends a request to delete a plugin instance on the server. */
    CompletableFuture<Boolean> requestDeletePlugin(UUID instanceID);
}
