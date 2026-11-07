package net.kroia.stockmarket;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.api.IBankSystemEvents;
import net.kroia.banksystem.api.event.TrustChangeInfo;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.MultiServerUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.api.integration.IStockMarketIntegration;
import net.kroia.stockmarket.api.marketmanager.IClientMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.StockMarketIntegrationImpl;
import net.kroia.stockmarket.api.pluginmanager.IClientPluginManager;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.command.StockMarketCommandHandler;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;
import net.kroia.stockmarket.minecraft.compat.StockMarketPlaceholders;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.data.table.OrderRecordManager;
import net.kroia.stockmarket.minecraft.entity.StockMarketEntities;
import net.kroia.stockmarket.event.EventRegistration;
import net.kroia.stockmarket.minecraft.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.api.preset.IAsyncPresetManager;
import net.kroia.stockmarket.stockmarket.market.preset.AsyncPresetManager;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetManager;
import net.kroia.stockmarket.pluginsystem.Plugins;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ClientPluginManager;
import net.kroia.stockmarket.pluginsystem.pluginmanager.PluginManager;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ClientMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;

import net.kroia.stockmarket.minecraft.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.PlayerJoinSyncPacket;
import net.kroia.stockmarket.networking.packet.SlaveTrustSyncPacket;
import net.kroia.stockmarket.networking.packet.TrustFlagUpdatePacket;
import net.kroia.stockmarket.news.ClientNewsCache;
import net.kroia.stockmarket.news.ClientNewsPictureCache;
import net.kroia.stockmarket.util.*;
import net.kroia.stockmarket.villagertrading.VillagerTradeManager;
import net.kroia.stockmarket.villagertrading.VillagerTradeRewriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

public class StockMarketModBackend implements StockMarketAPI {


    public static class CommonInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketLogger LOGGER;
        public StockMarketNetworking NETWORKING;
    }

    public static class ServerInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketModSettings SERVER_SETTINGS;
        public DataManager DATA_MANAGER;
        public DatabaseManager DATABASE_MANAGER;
        public MarketManager MARKET_MANAGER;
        public PluginManager PLUGIN_MANAGER;
        public StockMarketCommandHandler COMMAND_HANDLER;

        public MarketPriceManager MARKET_PRICE_HISTORY_MANAGER;
        public OrderRecordManager ORDER_RECORD_MANAGER;

        public MarketPresetManager PRESET_MANAGER;

        /** Villager trade repricing (master: computes/broadcasts the price table; slave: receives it). */
        public VillagerTradeManager VILLAGER_TRADE_MANAGER;

        public StockMarketNetworking NETWORKING;
        public StockMarketLogger LOGGER;
    }
    public static class ClientInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;

        public IClientMarketManager MARKET_MANAGER;
        public IClientPluginManager PLUGIN_MANAGER;
        public IAsyncPresetManager PRESET_MANAGER;
        public StockMarketNetworking NETWORKING;
        public StockMarketLogger LOGGER;
        public ClientSettings SETTINGS;
        /**
         * Recently published news records received via NewsPublishedPacket (T-073).
         * Per-connection like the other client managers: created on join, discarded
         * with the whole ClientInstances on disconnect (implicit clear).
         */
        public ClientNewsCache NEWS_CACHE;
        /**
         * Published news pictures fetched by content hash, converted to newsprint
         * grayscale and registered as GPU textures (T-090). Per-connection like
         * NEWS_CACHE — but unlike it, discarding the object is NOT enough: its
         * DynamicTextures hold GL resources, so releaseAll() MUST be called at the
         * disconnect discard site (see onPlayerLeaveClientSide).
         */
        public ClientNewsPictureCache NEWS_PICTURE_CACHE;
    }

    private static final CommonInstances COMMON_INSTANCES = new CommonInstances();
    private static ServerInstances SERVER_INSTANCES = null;
    private static ClientInstances CLIENT_INSTANCES = null;

    /**
     * @return the current client-side instances holder, or {@code null} if the
     * client has not joined a world yet (or has already disconnected). Read-only
     * accessor for static helpers that need to reach the cached BankSystem API
     * or the client managers without a per-caller setBackend() dance.
     */
    public static @Nullable ClientInstances getClientInstances() {
        return CLIENT_INSTANCES;
    }

    /**
     * T-179: current {@code MARKET.MAX_ORDER_VOLUME_FACTOR} of the running (master) server, read live.
     * Without a running server (remote client, or before setup) the compile-time default is returned —
     * client-side derivations are never authoritative (markets/presets come from the master with explicit values).
     *
     * @return the factor, &ge; 1
     */
    public static long getMaxOrderVolumeFactor() {
        ServerInstances server = SERVER_INSTANCES;
        if (server == null || server.SERVER_SETTINGS == null)
            return MarketSettings.DEFAULT_MAX_ORDER_VOLUME_FACTOR;
        Long factor = server.SERVER_SETTINGS.MARKET.MAX_ORDER_VOLUME_FACTOR.get();
        return factor == null ? MarketSettings.DEFAULT_MAX_ORDER_VOLUME_FACTOR
                : Math.max(1L, factor);
    }

    StockMarketModBackend()
    {
        COMMON_INSTANCES.LOGGER = new StockMarketLogger();
        COMMON_INSTANCES.NETWORKING = new StockMarketNetworking();



        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();

        // Display GUI elements must be known to ModUtilities' GuiElementRegistry on both
        // sides: if a display GUI's structure changes at runtime, the server ships a guiTree
        // and the client rebuilds it by type key. Unregistered elements would be dropped.
        // (Label and ItemView, also used by the display builders, are registered by ModUtilities.)
        net.kroia.modutilities.gui.GuiElementRegistry.register("stockmarket_display_candlestick_chart",
                net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart.class,
                net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart::new);

        EventRegistration.init();
        AdminFlagSync.init(); // LuckPerms listener for stockmarket.admin, no-op without LuckPerms
        if (StockMarketMod.ENABLE_DEV_FEATURES) {
            invokeDevBootstrap("init", new Class<?>[0]);
        }

        StockMarketNetworking.setBackend(COMMON_INSTANCES);

    }



    // Called from the client side
    public static void onClientSetup()
    {

        StockMarketMenus.setupScreens();
        StockMarketEntities.registerRenderers();

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(StockMarketModBackend::onPlayerLeaveClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(StockMarketModBackend::onPlayerJoinClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(StockMarketModBackend::onPlayerRespawnClientSide);
        ClientTickEvent.CLIENT_LEVEL_POST.register(StockMarketModBackend::onClientTickEvent);

        Plugins.clientSetup();
    }

    // Called from the server side
    public static void onServerSetup()
    {


        // Fabric/Quilt call this on every SERVER_STARTING, NeoForge once at common setup, so it is
        // not undone in onServerStop. Remove-then-add keeps exactly one listener either way.
        IBankSystemEvents events = BankSystemMod.getAPI().getEvents();
        events.getBanksystemSetupCompleteSignal().removeListener(BANK_SETUP_COMPLETE_LISTENER);
        events.getBanksystemSetupCompleteSignal().addListener(BANK_SETUP_COMPLETE_LISTENER);
        events.getBankDataLoadedFromFileSignal().removeListener(BANK_DATA_LOADED_LISTENER);
        events.getBankDataLoadedFromFileSignal().addListener(BANK_DATA_LOADED_LISTENER);

        Plugins.serverSetup();
    }


    // Called from the server side
    public static void onServerStart(MinecraftServer server)
    {


        //INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener();

        /*if(BankSystemDataHandler.isBankDataLoaded())
        {
            BankSystemDataHandler.resetBankDataLoaded();
            onPostBankSystemDataLoaded();
        }
        else
        {
            SERVER_INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onPostBankSystemDataLoaded, 1);
        }*/

        /*if(SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.ENABLE_SERVER_SERVER_COMMUNICATION.get()) {
            boolean isMaster = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.IS_MASTER.get();
            String sharedSecret = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.SHARED_SECRET.get();
            int port = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.MASTER_TCP_PORT.get();

            if(!MultiServerManager.instanceExists()) {
                if (isMaster) {
                    MultiServerManager.createMaster(server, sharedSecret, port);
                    MultiServerManager.start();
                } else {
                    String hostIP = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.MASTER_IP.get();
                    String thisServerID = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.SLAVE_ID.get();
                    MultiServerManager.createSlave(server, sharedSecret, thisServerID, hostIP, port);
                    MultiServerManager.start();
                }
            }
        }*/
    }
    private static void onPostBankSystemDataLoaded()
    {

    }
    private static void onBankSystemSetupComplete()
    {
        SERVER_INSTANCES =  new ServerInstances();
        SERVER_INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        SERVER_INSTANCES.LOGGER = COMMON_INSTANCES.LOGGER;
        SERVER_INSTANCES.NETWORKING = COMMON_INSTANCES.NETWORKING;
        SERVER_INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        SERVER_INSTANCES.SERVER_SETTINGS.setLogger(SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::debug);

        MarketPreset.setRegistryAccess(UtilitiesPlatform.getServer().registryAccess());
        SERVER_INSTANCES.PRESET_MANAGER = new MarketPresetManager();
        SERVER_INSTANCES.PRESET_MANAGER.loadOrGenerate(DataManager.getPresetPath(), UtilitiesPlatform.getServer().registryAccess());
        AsyncPresetManager.setServerBackend(SERVER_INSTANCES);

        MarketManager.setBackend(SERVER_INSTANCES);
        PluginManager.setBackend(SERVER_INSTANCES);
        StockMarketNetworking.setBackend(SERVER_INSTANCES);
        StockMarketCommands.setBackend(SERVER_INSTANCES);
        StockMarketCommandHandler.setBackend(SERVER_INSTANCES);
        DataManager.setBackend(SERVER_INSTANCES);
        StockMarketLogger.setBackend(SERVER_INSTANCES);
        VillagerTradeRewriter.setBackend(SERVER_INSTANCES);
        AdminFlagSync.setBackend(SERVER_INSTANCES);

        boolean isMaster = BankSystemMod.getAPI().getServerBankManager().isMaster();
        villagerAutosaveTicks = 0;
        if (StockMarketMod.ENABLE_DEV_FEATURES) {
            invokeDevBootstrap("onServerSetup", new Class<?>[]{ServerInstances.class, boolean.class}, SERVER_INSTANCES, isMaster);
        }
        if(isMaster) {
            SERVER_INSTANCES.DATABASE_MANAGER = new DatabaseManager();
            SERVER_INSTANCES.DATABASE_MANAGER.connectToDatabase(UtilitiesPlatform.getServer());
            SERVER_INSTANCES.MARKET_PRICE_HISTORY_MANAGER = new MarketPriceManager(SERVER_INSTANCES.DATABASE_MANAGER);
            SERVER_INSTANCES.ORDER_RECORD_MANAGER = new OrderRecordManager(SERVER_INSTANCES.DATABASE_MANAGER);
            SERVER_INSTANCES.MARKET_MANAGER = MarketManager.createMaster();
            SERVER_INSTANCES.PLUGIN_MANAGER = PluginManager.createMaster();
            SERVER_INSTANCES.COMMAND_HANDLER = StockMarketCommandHandler.createMaster();
            SERVER_INSTANCES.DATA_MANAGER = new DataManager();
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER = new VillagerTradeManager(SERVER_INSTANCES);
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.load(UtilitiesPlatform.getServer());

            // Master-only: listen for BankSystem ItemID merges and consolidate our own
            // markets under the resulting canonical IDs. The load-time reconcile inside
            // ServerMarketManager.load() catches merges that fired during BankSystem's
            // own startup (before this listener was registered); this listener catches
            // merges that happen later (e.g. VolatileItemComponents config edits + admin
            // confirmation via CONFIRM_ITEMID_MERGE).
            BankSystemMod.getAPI().getEvents().getItemIDsMergedEvent().addListener(ITEM_IDS_MERGED_LISTENER);

            loadDataFromFiles(UtilitiesPlatform.getServer());
            preRegisterPresetItemStacks();
            AdminFlagSync.migrateIfNeeded(); // one-time push of admins to LuckPerms (setting on), users are loaded now
            registerItemPriceProvider();

            // Villager trade repricing: build the initial price table now that
            // settings + markets are loaded, and push a fresh table to every
            // slave that connects (fires AFTER BankSystem's ItemID sync, so the
            // table's ItemID shorts are resolvable on the slave).
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.recomputeTable();
            BankSystemMod.getAPI().getEvents().getMasterServerSlaveConnected().addListener(SLAVE_CONNECTED_LISTENER);

            // T-128 master-side runtime trust-toggle propagation.
            //
            // T-126/T-127 populate the slave-side SlaveTrustCache at handshake
            // time (SLAVE_CONNECTION_ACCEPTED). That closes first-connect +
            // reconnect, but leaves the case where an admin runs
            // `/banksystem trust <slaveID>` or `/banksystem untrust <slaveID>`
            // WHILE a slave is already connected: the slave's cache stays stale,
            // its clients keep the old UI, orders get blocked/allowed by the
            // server-side NetworkGate but the buy/sell buttons still say the
            // wrong thing.
            //
            // Fix: subscribe to BankSystem's new TRUST_CHANGED event (fired
            // from ServerBankManager.trustSlaveServer/untrustSlaveServer AFTER
            // the trust set mutation). Push a TrustFlagUpdatePacket to the
            // affected slave via the S2S packet layer. The slave's handler
            // refreshes SlaveTrustCache and re-broadcasts SlaveTrustSyncPacket
            // to every currently-connected client on that slave, reusing
            // T-126's existing client-broadcast plumbing.
            //
            // Slaves that are not currently connected are silently skipped —
            // MultiServerManager.sendToSlave logs and returns false, and the
            // slave will pick up the fresh trust value at its next handshake
            // via T-126's SLAVE_CONNECTION_ACCEPTED wiring anyway.
            //
            // Fires on the server main thread (BankSystem fires from the
            // manager mutator, not from an async worker). Wrapped in try/catch
            // so an exception from the send path never breaks event dispatch
            // for other listeners.
            BankSystemMod.getAPI().getEvents().getTrustChangedSignal().addListener(TRUST_CHANGED_LISTENER);

            autosaveTimer_lastMs = System.currentTimeMillis();
            // T-137: pair SERVER_PRE (stamp start) with SERVER_POST (measure work).
            // SERVER_PRE runs before any vanilla tick work and stamps the tick
            // start time into a static; onServerTick (SERVER_POST) later
            // computes (now - lastTickStartNs) = actual CPU work per tick and
            // feeds it to the plugin manager's non-plugin rolling average.
            // We deliberately DO NOT use POST-to-POST as the interval — that
            // would include the vanilla server's inter-tick sleep and always
            // report ~50 ms regardless of real load, which would render the
            // effective-budget calculation meaningless.
            // Same instances as in onServerStop; isRegistered guards against a double register.
            if (!TickEvent.SERVER_PRE.isRegistered(SERVER_TICK_PRE))
                TickEvent.SERVER_PRE.register(SERVER_TICK_PRE);
            if (!TickEvent.SERVER_POST.isRegistered(SERVER_TICK_POST))
                TickEvent.SERVER_POST.register(SERVER_TICK_POST);

        }
        else
        {
            SERVER_INSTANCES.MARKET_MANAGER = MarketManager.createSlave();
            SERVER_INSTANCES.PLUGIN_MANAGER = PluginManager.createSlave();
            SERVER_INSTANCES.COMMAND_HANDLER = StockMarketCommandHandler.createSlave();
            // Slave: only receives price tables from the master (applyTable);
            // recompute/tick are inert without sync market access.
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER = new VillagerTradeManager(SERVER_INSTANCES);
            // The villager trade file is local to every server, slaves included.
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.load(UtilitiesPlatform.getServer());
            // Slaves use the post-tick handler only for the villager trade autosave
            // (onServerTick skips the master work there). Unregistered in onServerStop.
            if (!TickEvent.SERVER_POST.isRegistered(SERVER_TICK_POST))
                TickEvent.SERVER_POST.register(SERVER_TICK_POST);

            // T-126 slave-trust cache wiring.
            //
            // The trust flag "does the master trust me?" comes from
            // BankSystem's AsyncBankManager.isSlaveServerTrustedAsync(slaveID),
            // but that accessor short-circuits synchronously to `false`
            // whenever MultiServerUtils.canInteractWithBankSystem() is not yet
            // true — i.e. before the slave→master handshake completes. That
            // window covers the entire PlayerJoinSyncPacket call at server
            // startup, which is why the previous T-125 attempt observed
            // trusted=false on a genuinely trusted slave.
            //
            // Fix: subscribe to BankSystem's new SLAVE_CONNECTION_ACCEPTED
            // signal (fired inside BankSystemModBackend.onSlaveConnectionAccepted
            // AFTER the handshake completes). At that point the async forwarder
            // is usable, so we can issue a proper thenAccept round-trip, cache
            // the master's answer in SlaveTrustCache, and — if the value
            // actually changed — broadcast a SlaveTrustSyncPacket to every
            // currently-connected player so any client that received the
            // fail-closed default at join time transitions to the correct state.
            //
            // The paired SLAVE_CONNECTION_LOST signal invalidates the cache so
            // the fail-closed default applies again until the next handshake.
            // Reconnects re-fire SLAVE_CONNECTION_ACCEPTED naturally, refreshing
            // the cache with no extra plumbing.
            //
            // Signals are fired on Netty event-loop threads; the callbacks
            // below don't touch any Minecraft main-thread-only state (BankSystem
            // request-forwarding + NetworkManager.sendToPlayers are both safe
            // from arbitrary threads).
            IBankSystemEvents events = BankSystemMod.getAPI().getEvents();
            events.getSlaveConnectionAcceptedSignal().addListener(SLAVE_ACCEPTED_LISTENER);
            events.getSlaveConnectionLostSignal().addListener(SLAVE_LOST_LISTENER);

            // T-127: close the race where the SLAVE_CONNECTION_ACCEPTED signal already
            // fired before we subscribed (BankSystem inits first, TCP handshake to master
            // completes in sub-millisecond on localhost, signal fires with zero listeners).
            // Signal has no sticky/replay semantics, so we probe MultiServerUtils here —
            // same synchronous state the TerminalBlock use-handler consults — and if the
            // handshake has already completed, manually invoke the refresh to backfill the
            // cache. If the handshake has NOT completed yet, this is a no-op and the
            // natural signal fire will do the work.
            if (MultiServerUtils.canInteractWithBankSystem()) {
                onSlaveConnectedRefreshTrustCache();
            }
        }

        // Placeholders (master + slave): registers player placeholders and the refresh tick.
        // Placed after the master/slave branch so MARKET_MANAGER exists when the tick runs.
        StockMarketPlaceholders.init(SERVER_INSTANCES);
    }

    /**
     * Fetches the master-authoritative "is this slave trusted?" flag via
     * BankSystem's async forwarder and populates {@link SlaveTrustCache}. If the
     * value changed vs. what was previously cached, broadcasts a
     * {@link SlaveTrustSyncPacket} to every currently-connected client so any
     * client that received the fail-closed default in its
     * {@code PlayerJoinSyncPacket} transitions to the correct state within a tick.
     * <p>
     * Wired to BankSystem's {@code SLAVE_CONNECTION_ACCEPTED} signal — see the
     * slave branch of {@link #onServerStart(MinecraftServer)}. Fired on a Netty
     * event-loop thread. Non-blocking: uses {@code thenAccept} on the future.
     */
    private static void onSlaveConnectedRefreshTrustCache() {
        // Guard against the reconnect race that also protects BankSystem's own
        // onSlaveConnectionAccepted: if this JVM is mid-teardown, SERVER_INSTANCES
        // may already be null.
        if (SERVER_INSTANCES == null)
            return;
        String slaveID = MultiServerManager.getSlaveID();
        if (slaveID == null || slaveID.isEmpty())
            return;
        try {
            BankSystemMod.getAPI().getServerBankManager().getAsync()
                    .isSlaveServerTrustedAsync(slaveID)
                    .thenAccept(trusted -> {
                        if (trusted == null) return; // shouldn't happen — BankSystem uses BOOL codec
                        boolean changed = SlaveTrustCache.set(trusted);
                        if (changed) {
                            SlaveTrustSyncPacket.broadcast(trusted);
                        }
                    });
        } catch (Exception e) {
            // BankSystem torn down mid-flight — leave the cache alone. The next
            // handshake (or the paired _LOST signal) will resolve it.
            if (SERVER_INSTANCES != null && SERVER_INSTANCES.LOGGER != null) {
                SERVER_INSTANCES.LOGGER.warn(
                        "[StockMarketModBackend] Failed to refresh slave trust cache: " + e.getMessage());
            }
        }
    }

    /**
     * Invalidates {@link SlaveTrustCache} so a subsequent join-sync falls back
     * to the fail-closed default until the next handshake refreshes it.
     * <p>
     * Wired to BankSystem's {@code SLAVE_CONNECTION_LOST} signal — see the slave
     * branch of {@link #onServerStart(MinecraftServer)}. Fired on a Netty
     * event-loop thread; safe to run there ({@link SlaveTrustCache#clear()} is
     * a single volatile write).
     * <p>
     * Deliberately does NOT broadcast a "trust flipped to false" packet: the
     * disconnected slave cannot reach any client through the ARRS anyway, and
     * a client that stays connected across the master outage keeps its existing
     * UI state until the master comes back and the ACCEPTED signal broadcasts
     * the fresh value. This mirrors the original T-123 tradeoff of not shipping
     * a live master-side toggle.
     */
    private static void onSlaveDisconnectedInvalidateTrustCache() {
        SlaveTrustCache.clear();
    }

    /**
     * Master-only ItemID-merge listener: consolidates our markets under the canonical IDs
     * and re-publishes the trading currency to BankSystem. Registered in onServerStart.
     *
     * @param aliasToCanonical merged alias ItemID mapped to its canonical ItemID
     */
    private static void onItemIDsMerged(Map<ItemID, ItemID> aliasToCanonical) {
        if (SERVER_INSTANCES == null || SERVER_INSTANCES.MARKET_MANAGER == null) return;
        var serverMarketManager = SERVER_INSTANCES.MARKET_MANAGER.getServerMarketManager();
        if (serverMarketManager == null) return; // slave; guard is redundant here but cheap
        serverMarketManager.consolidateMergedMarkets(aliasToCanonical);

        // Refresh the currency id used by BankSystem's Total-Wealth balance-history
        // calculation. It MUST equal the market TRADING currency (the item short where
        // trades settle and where player cash is actually stored) — NOT
        // MoneyItem.getItemID().getShort(), which resolves via a ConcurrentHashMap scan
        // and can land on the WRONG money denomination. A wrong short pushes the real
        // cash bank into the market-price branch (money has no market -> price 0), which
        // silently zeroes out the player's total wealth. An ItemID merge can also re-key
        // the trading currency, so we re-publish it here. Guarded like the startup path
        // to avoid an NPE during teardown/ordering edge cases.
        if (SERVER_INSTANCES.BANK_SYSTEM_API != null) {
            ISyncServerMarketManager syncMarketManager = SERVER_INSTANCES.MARKET_MANAGER.getSync();
            if (syncMarketManager != null) {
                ItemID tradingCurrency = syncMarketManager.getTradingCurrencyID();
                if (tradingCurrency != null) {
                    SERVER_INSTANCES.BANK_SYSTEM_API.setPriceCurrencyItem(tradingCurrency.getShort());
                }
            }
        }
    }

    /**
     * (Master only) Handles BankSystem's runtime trust-toggle event (T-128).
     * Fired on the master's server thread whenever
     * {@code ServerBankManager.trustSlaveServer(String)} or
     * {@code .untrustSlaveServer(String)} mutates the trust set — typically as a
     * result of {@code /banksystem trust}/{@code /banksystem untrust} while
     * slaves are connected.
     * <p>
     * If the affected slave is currently connected, dispatches a
     * {@link TrustFlagUpdatePacket} to it via
     * {@link MultiServerManager#sendToSlave(String, net.minecraft.network.protocol.common.custom.CustomPacketPayload)}
     * so the slave refreshes its {@link SlaveTrustCache} and re-broadcasts
     * {@link SlaveTrustSyncPacket} to every currently-connected client. If the
     * slave is not connected, no-op — the next
     * {@code SLAVE_CONNECTION_ACCEPTED} handshake will populate the cache with
     * the current authoritative value anyway (T-126 wiring).
     * <p>
     * Exceptions from the send path are logged at WARN and swallowed so a
     * transient network failure never blocks other listeners from receiving
     * the event.
     *
     * @param info the payload delivered by BankSystem's TRUST_CHANGED event
     */
    private static void onTrustChangedNotifyAffectedSlave(TrustChangeInfo info) {
        if (SERVER_INSTANCES == null || info == null || info.slaveID() == null)
            return;
        String targetSlaveID = info.slaveID();
        if (targetSlaveID.isEmpty())
            return;
        try {
            // Skip the send when the affected slave isn't among the currently
            // connected slaves — MultiServerManager.sendToSlave would log an
            // error and return false anyway, but avoiding the noisy log for a
            // routine "admin toggled trust for a slave that is offline" case
            // keeps the master's console clean.
            if (!MultiServerManager.getConnectedSlaveIDs().contains(targetSlaveID))
                return;
            TrustFlagUpdatePacket.sendTo(targetSlaveID, info.trusted());
        } catch (Exception e) {
            if (SERVER_INSTANCES != null && SERVER_INSTANCES.LOGGER != null) {
                SERVER_INSTANCES.LOGGER.warn(
                        "[StockMarketModBackend] Failed to push trust update to slave '"
                                + targetSlaveID + "': " + e.getMessage());
            }
        }
    }


    private static void registerItemPriceProvider() {
        BankSystemAPI api = SERVER_INSTANCES.BANK_SYSTEM_API;
        api.setItemPriceProvider(itemId -> {
            ISyncServerMarketManager marketManager = SERVER_INSTANCES.MARKET_MANAGER.getSync();
            if (marketManager == null) return 0;
            IServerMarket market = marketManager.getMarket(new ItemID(itemId));
            if (market == null) return 0;
            return market.getCurrentMarketPrice();
        });

        // The wealth currency handed to BankSystem MUST equal the market TRADING currency
        // (the item short where cash is actually stored / trades settle), NOT
        // MoneyItem.getItemID().getShort(): that call mis-resolves via a ConcurrentHashMap
        // scan to the wrong money denomination, which would make BankSystem value the real
        // cash bank at market price (0) and drop it from the "Total Wealth" figure.
        // Guard for the sync manager / trading currency not being ready yet — skip rather
        // than NPE (mirrors the getSync() null-guard used above for the price provider).
        ISyncServerMarketManager marketManager = SERVER_INSTANCES.MARKET_MANAGER.getSync();
        if (marketManager != null) {
            ItemID tradingCurrency = marketManager.getTradingCurrencyID();
            if (tradingCurrency != null) {
                api.setPriceCurrencyItem(tradingCurrency.getShort());
            }
        }
    }

    /**
     * Calls a static method of the dev-only DevBootstrap (unit tests, test commands). It is compiled only
     * when gradle.properties has {@code enable_dev_features=true}, so it is reached reflectively: release
     * code must compile without it.
     *
     * @param method the DevBootstrap method name
     * @param types  its parameter types
     * @param args   its arguments
     */
    private static void invokeDevBootstrap(String method, Class<?>[] types, Object... args) {
        try {
            Class.forName(StockMarketMod.DEV_BOOTSTRAP).getMethod(method, types).invoke(null, args);
        } catch (ReflectiveOperationException e) {
            StockMarketMod.LOGGER.error("Dev features are enabled but " + StockMarketMod.DEV_BOOTSTRAP + "." + method
                    + " is missing or failed; check enable_dev_features in gradle.properties", e);
        }
    }

    // Called from the server side
    public static void onServerStop(MinecraftServer server)
    {
        if (SERVER_INSTANCES != null && SERVER_INSTANCES.BANK_SYSTEM_API != null) {
            SERVER_INSTANCES.BANK_SYSTEM_API.setItemPriceProvider(null);
        }
        TickEvent.SERVER_PRE.unregister(SERVER_TICK_PRE);
        TickEvent.SERVER_POST.unregister(SERVER_TICK_POST);
        // Listeners added in onServerStart (master or slave branch); removing an absent one is a no-op.
        IBankSystemEvents events = BankSystemMod.getAPI().getEvents();
        events.getItemIDsMergedEvent().removeListener(ITEM_IDS_MERGED_LISTENER);
        events.getMasterServerSlaveConnected().removeListener(SLAVE_CONNECTED_LISTENER);
        events.getTrustChangedSignal().removeListener(TRUST_CHANGED_LISTENER);
        events.getSlaveConnectionAcceptedSignal().removeListener(SLAVE_ACCEPTED_LISTENER);
        events.getSlaveConnectionLostSignal().removeListener(SLAVE_LOST_LISTENER);
        StockMarketPlaceholders.shutdown();
        saveDataToFiles(server);

        if(SERVER_INSTANCES != null && SERVER_INSTANCES.DATABASE_MANAGER != null) {
            SERVER_INSTANCES.DATABASE_MANAGER.close();
            SERVER_INSTANCES.DATABASE_MANAGER = null;
        }

        if(MultiServerManager.instanceExists())
        {
            MultiServerManager.stop();
            MultiServerManager.cleanup();
        }
        SERVER_INSTANCES = null;
    }


    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {
        SERVER_INSTANCES.MARKET_MANAGER.getAsync().onPlayerJoinAsync(player.getUUID(), player.getName().getString());
        PlayerJoinSyncPacket.send(player);
    }


    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {

    }

    // Called from the client side
    private static void onPlayerJoinClientSide(@Nullable LocalPlayer localPlayer)
    {
        if(CLIENT_INSTANCES != null)
            return;
        CLIENT_INSTANCES = new ClientInstances();

        StockMarketGuiScreen.setBackend(CLIENT_INSTANCES);
        StockMarketNetworking.setBackend(CLIENT_INSTANCES);
        ClientMarketManager.setBackend(CLIENT_INSTANCES);
        ClientPluginManager.setBackend(CLIENT_INSTANCES);
        StockMarketTextMessages.setBackend(CLIENT_INSTANCES);
        MarketManager.setClientBackend(CLIENT_INSTANCES);


        CLIENT_INSTANCES.LOGGER = COMMON_INSTANCES.LOGGER;
        CLIENT_INSTANCES.NETWORKING = COMMON_INSTANCES.NETWORKING;
        CLIENT_INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        CLIENT_INSTANCES.MARKET_MANAGER = MarketManager.createClient();
        CLIENT_INSTANCES.PLUGIN_MANAGER = new ClientPluginManager();
        AsyncPresetManager.setClientBackend(CLIENT_INSTANCES);
        CLIENT_INSTANCES.PRESET_MANAGER = AsyncPresetManager.createClientManager();
        CLIENT_INSTANCES.SETTINGS = new ClientSettings();
        CLIENT_INSTANCES.NEWS_CACHE = new ClientNewsCache();
        // Picture cache (T-090): real network fetcher (batched NewsPictureRequest,
        // response hopped onto the client main thread) + real GL texture sink.
        // Flushed once per client tick in onClientTickEvent, released on disconnect.
        CLIENT_INSTANCES.NEWS_PICTURE_CACHE = new ClientNewsPictureCache(
                new ClientNewsPictureCache.RequestFetcher(CLIENT_INSTANCES.NETWORKING),
                new ClientNewsPictureCache.MinecraftTextureSink());
        MarketPreset.setRegistryAccess(Minecraft.getInstance().getConnection().registryAccess());




        CLIENT_INSTANCES.MARKET_MANAGER.onPlayerJoin(localPlayer);
    }
    // Called from the client side
    private static void onPlayerLeaveClientSide(@Nullable LocalPlayer localPlayer)
    {
        if(CLIENT_INSTANCES == null)
            return;
        CLIENT_INSTANCES.MARKET_MANAGER.onPlayerLeave(localPlayer);

        // Free the news-picture GPU textures BEFORE the ClientInstances object is
        // discarded — dropping the object alone would leak the registered
        // DynamicTextures across connect/disconnect cycles (T-090).
        if (CLIENT_INSTANCES.NEWS_PICTURE_CACHE != null)
            CLIENT_INSTANCES.NEWS_PICTURE_CACHE.releaseAll();

        StockMarketNetworking.setBackend((ClientInstances)null);
        StockMarketGuiScreen.setBackend(null);
        ClientMarketManager.setBackend(null);
        ClientPluginManager.setBackend(null);
        StockMarketTextMessages.setBackend(null);
        CLIENT_INSTANCES = null;


    }


    private static void onPlayerRespawnClientSide(LocalPlayer oldPlayer, LocalPlayer newPlayer)
    {

    }


    private static void onClientTickEvent(ClientLevel clientLevel)
    {
        if (CLIENT_INSTANCES != null) {
            CLIENT_INSTANCES.MARKET_MANAGER.update();
            // Flush the news-picture fetch queue: at most one batched request per
            // tick, HIGH (visible widgets) before BACKGROUND (prefetch) — T-090.
            if (CLIENT_INSTANCES.NEWS_PICTURE_CACHE != null)
                CLIENT_INSTANCES.NEWS_PICTURE_CACHE.clientTick();
        }
    }

    private static long autosaveTimer_lastMs = 0;

    /** Server ticks since the last villager trade autosave; reset on every server start. */
    private static long villagerAutosaveTicks = 0;

    /**
     * Saves the villager trade file (dirty-only, atomic) every SAVE_INTERVAL_MINUTES, counted in
     * server ticks (20 per second). Runs on master and slave, so a crash loses at most one interval
     * of villager trades. The master's full autosave saves it too; the second call is a no-op
     * because the data is no longer dirty.
     *
     * @param server the ticking server
     */
    private static void tickVillagerAutosave(MinecraftServer server)
    {
        if (SERVER_INSTANCES.VILLAGER_TRADE_MANAGER == null || SERVER_INSTANCES.SERVER_SETTINGS == null)
            return;
        long intervalTicks = SERVER_INSTANCES.SERVER_SETTINGS.UTILITIES.SAVE_INTERVAL_MINUTES.get() * 60L * 20L;
        if (intervalTicks <= 0 || ++villagerAutosaveTicks < intervalTicks)
            return;
        villagerAutosaveTicks = 0;
        SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.save(server);
    }

    // Listeners held as single instances: every `Class::method` expression creates a new
    // object, so unregister() with a fresh method reference never matches the registered
    // one and the handlers stack across singleplayer world switches. Always register and
    // unregister through these fields.
    private static final TickEvent.Server SERVER_TICK_PRE = StockMarketModBackend::onServerTickPre;
    private static final TickEvent.Server SERVER_TICK_POST = StockMarketModBackend::onServerTick;

    // Same pattern for BankSystem signals: BankSystemEvents is created once per JVM (mod init),
    // so listeners added per server start stack across world switches unless removed with the
    // identical instance.
    private static final Runnable BANK_SETUP_COMPLETE_LISTENER = StockMarketModBackend::onBankSystemSetupComplete;
    private static final Runnable BANK_DATA_LOADED_LISTENER = StockMarketModBackend::onPostBankSystemDataLoaded;
    private static final Consumer<Map<ItemID, ItemID>> ITEM_IDS_MERGED_LISTENER = StockMarketModBackend::onItemIDsMerged;
    private static final Runnable SLAVE_CONNECTED_LISTENER = () -> {
        if (SERVER_INSTANCES == null || SERVER_INSTANCES.VILLAGER_TRADE_MANAGER == null) return;
        SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.broadcastTable();
    };
    private static final Consumer<TrustChangeInfo> TRUST_CHANGED_LISTENER = StockMarketModBackend::onTrustChangedNotifyAffectedSlave;
    private static final Runnable SLAVE_ACCEPTED_LISTENER = StockMarketModBackend::onSlaveConnectedRefreshTrustCache;
    private static final Runnable SLAVE_LOST_LISTENER = StockMarketModBackend::onSlaveDisconnectedInvalidateTrustCache;

    /**
     * Nanosecond timestamp captured at the start of the current server tick
     * (T-137). Written by {@link #onServerTickPre(MinecraftServer)} on every
     * {@code TickEvent.SERVER_PRE}, read by {@link #onServerTick(MinecraftServer)}
     * at the end of {@code TickEvent.SERVER_POST}. The difference is the actual
     * CPU work performed for one server tick (world tick, entities, networking,
     * plugin loop, ...) — deliberately measured pre-to-post so the vanilla
     * inter-tick sleep is excluded. Master-only; single thread.
     * <p>
     * Zero-sentinel: if a POST fires without a preceding PRE (extraordinary,
     * e.g. immediately after a hot-swap during dev), the tick sample is
     * skipped rather than reporting a bogus 50 ms average.
     */
    private static long lastTickStartNs = 0L;

    /**
     * Master-only {@code TickEvent.SERVER_PRE} handler — stamps
     * {@link #lastTickStartNs} so the paired {@link #onServerTick} can measure
     * total tick CPU work. See {@link #lastTickStartNs} for the reasoning
     * behind pre-to-post rather than post-to-post.
     */
    private static void onServerTickPre(MinecraftServer server)
    {
        lastTickStartNs = System.nanoTime();
    }

    // Called from the master side
    private static void onServerTick(MinecraftServer server)
    {
        if (SERVER_INSTANCES == null || SERVER_INSTANCES.MARKET_MANAGER == null)
            return;
        // Every server (master and slave) keeps its own villager trade file.
        tickVillagerAutosave(server);
        // The rest is master work; a slave has no sync market manager.
        if (SERVER_INSTANCES.MARKET_MANAGER.getSync() == null)
            return;

        SERVER_INSTANCES.PLUGIN_MANAGER.getSync().update();
        SERVER_INSTANCES.MARKET_MANAGER.getSync().update();

        // Villager trade repricing: refresh + broadcast the price table when
        // the configured interval elapsed (master-only tick).
        if (SERVER_INSTANCES.VILLAGER_TRADE_MANAGER != null)
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.tickMaster();

        // Periodic full NBT autosave
        long now = System.currentTimeMillis();
        long intervalMs = SERVER_INSTANCES.SERVER_SETTINGS.UTILITIES.SAVE_INTERVAL_MINUTES.get() * 60_000L;
        if (intervalMs > 0 && now - autosaveTimer_lastMs >= intervalMs) {
            autosaveTimer_lastMs = now;
            saveDataToFiles(server);
        }

        // T-137: total tick CPU work (SERVER_PRE → end of SERVER_POST). This
        // must be the LAST statement in the tick handler so the sample
        // encompasses every other tick action above. The plugin manager
        // subtracts its own recorded plugin time to get the non-plugin sample
        // that feeds the rolling average consumed by the effective budget.
        if (lastTickStartNs != 0L
                && SERVER_INSTANCES != null
                && SERVER_INSTANCES.PLUGIN_MANAGER != null
                && SERVER_INSTANCES.PLUGIN_MANAGER.getSync() instanceof ServerPluginManager mgr) {
            long totalTickWorkNs = System.nanoTime() - lastTickStartNs;
            mgr.recordNonPluginTickNs(totalTickWorkNs);
        }
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        if(SERVER_INSTANCES != null && SERVER_INSTANCES.DATA_MANAGER != null)
            SERVER_INSTANCES.DATA_MANAGER.load(server);
    }
    /**
     * Pre-registers all preset ItemStacks in the BankSystem ItemIDManager using
     * the server's registry.  This ensures enchanted books, potions, etc. have
     * Holder.References bound to the server's registry and can be saved to NBT.
     * Called after loadDataFromFiles so existing ItemIDs are loaded first.
     */
    private static void preRegisterPresetItemStacks() {
        if (SERVER_INSTANCES == null || SERVER_INSTANCES.PRESET_MANAGER == null) return;
        int count = 0;
        for (var category : SERVER_INSTANCES.PRESET_MANAGER.getCategories()) {
            for (MarketPreset preset : category.getPresets()) {
                net.minecraft.world.item.ItemStack stack = preset.toItemStack();
                if (!stack.isEmpty()) {
                    ItemID id = net.kroia.banksystem.util.ItemIDManager.registerItemStackServerSide_direct(stack);
                    if (id.isValid()) {
                        preset.setRegisteredItemIDShort(id.getShort());
                    }
                    count++;
                }
            }
        }
        SERVER_INSTANCES.LOGGER.info("[StockMarketModBackend] Pre-registered " + count + " preset ItemStacks in ItemIDManager");
    }

    public static void saveDataToFiles(MinecraftServer server)
    {
        if(SERVER_INSTANCES != null && SERVER_INSTANCES.DATA_MANAGER != null)
            SERVER_INSTANCES.DATA_MANAGER.save(server);
        // Villager trade file: saved on master and slave (local per server).
        if(SERVER_INSTANCES != null && SERVER_INSTANCES.VILLAGER_TRADE_MANAGER != null)
            SERVER_INSTANCES.VILLAGER_TRADE_MANAGER.save(server);
    }



    @Override
    public String getModID()
    {
        return StockMarketMod.MOD_ID;
    }

    @Override
    public String getModVersion() {
        return StockMarketMod.VERSION;
    }

    /**
     * Lazily-cached singleton integration SPI. The impl reads the CURRENT
     * {@link #SERVER_INSTANCES} on every call via a supplier, so it stays valid
     * across server stop/start cycles within a single JVM lifetime (a new
     * server binds to the same lambda). Null on a pure-client JVM where no
     * server has ever started.
     */
    private static volatile IStockMarketIntegration INTEGRATION_INSTANCE = null;

    @Override
    public @Nullable IStockMarketIntegration getIntegration() {
        if (SERVER_INSTANCES == null) {
            return null;
        }
        IStockMarketIntegration cached = INTEGRATION_INSTANCE;
        if (cached == null) {
            synchronized (StockMarketModBackend.class) {
                cached = INTEGRATION_INSTANCE;
                if (cached == null) {
                    cached = new StockMarketIntegrationImpl(() -> SERVER_INSTANCES);
                    INTEGRATION_INSTANCE = cached;
                }
            }
        }
        return cached;
    }
}
