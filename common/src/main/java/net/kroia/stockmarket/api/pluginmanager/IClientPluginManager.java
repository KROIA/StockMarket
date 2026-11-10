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
 * <p>
 * Request failures (2.2.0): the request futures fail with ModUtilities'
 * {@link net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException} when no answer came in
 * time or the connection was lost, often wrapped in a {@link java.util.concurrent.CompletionException}. The server
 * may have applied the change: never undo or resend it automatically, reload the plugin list instead. A failed
 * {@link #requestPluginList()} or {@link #requestReorderPlugin} leaves the cached list as it was. See
 * {@code documentation/developer-guide/api/async-market-api.md}, section 3.
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
    CompletableFuture<PluginCustomSettingsRequest.OutputData> requestUpdateCustomSettings(UUID instanceID, ItemID marketID, byte[] payload);

    /** Sends a subscription change request for a plugin instance to the server. */
    CompletableFuture<PluginSubscriptionRequest.OutputData> requestUpdateSubscription(UUID instanceID, ItemID marketID, boolean subscribe);

    /** Sends a request to create a new plugin instance of the given type on the server. */
    CompletableFuture<Boolean> requestCreatePlugin(String pluginTypeID);

    /** Sends a request to delete a plugin instance on the server. */
    CompletableFuture<Boolean> requestDeletePlugin(UUID instanceID);
}
