package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.ClientSettingsSyncPacket;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.networking.request.ServerTimeRequest;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.testing.PacketTap;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * T-140: the master's {@code PRICE_HISTORY_INITIAL_LOAD_CANDLES} reaching clients.
 * <ul>
 *   <li>{@link StockMarketModBackend#getServerInstances()} replaces the old reflective read,</li>
 *   <li>the join value travels in {@link ServerTimeRequest} and lands in {@link ClientSettings},</li>
 *   <li>{@link ClientMarket#resolveInitialLoadCandles} prefers the synced value, then the local server, then 512,</li>
 *   <li>a SET re-pushes only on change ({@link ClientSettingsSyncPacket#broadcastIfChanged}),</li>
 *   <li>a slave gets the master's answer (the request is routed to the master).</li>
 * </ul>
 * Runs on master and slave; tests skip by side.
 */
public class ClientSettingsSyncTestSuite extends TestSuite {

    /** Wait for a request to the master. */
    private static final long WAIT_MS = 10_000;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.CLIENT_SETTINGS_SYNC;
    }

    @Override
    public void registerTests() {
        addTest("server_instances_accessor", this::test_serverInstancesAccessor);
        addTest("client_settings_clamp_and_unsynced", this::test_clientSettingsClampAndUnsynced);
        addTest("resolve_fallback_order", this::test_resolveFallbackOrder);
        addTest("join_value_from_server_time_request", this::test_joinValueFromServerTimeRequest);
        addTest("server_time_output_codec_round_trip", this::test_outputCodecRoundTrip);
        addTest("set_repushes_only_on_change", this::test_setRepushesOnlyOnChange);
        addTest("slave_gets_master_value", this::test_slaveGetsMasterValue);
        addTest("sp_set_reaches_client_and_chart_request", this::test_spSetReachesClientAndChartRequest);
    }

    /** The public accessor returns the running server's instances, with settings loaded. */
    private TestResult test_serverInstancesAccessor() {
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        TestResult r = assertNotNull("getServerInstances() on a running server", srv);
        if (!r.passed()) return r;
        r = assertNotNull("SERVER_SETTINGS", srv.SERVER_SETTINGS);
        if (!r.passed()) return r;
        return assertNotNull("NETWORKING", srv.NETWORKING);
    }

    /** A fresh ClientSettings is "not synced" (0); synced values are clamped into the sanitize bounds. */
    private TestResult test_clientSettingsClampAndUnsynced() {
        ClientSettings s = new ClientSettings();
        TestResult r = assertEquals("fresh = not synced", 0, s.getInitialLoadCandles());
        if (!r.passed()) return r;
        s.setInitialLoadCandles(1);
        r = assertEquals("clamped up", ModSettingsRequest.MIN_PRICE_HISTORY_INITIAL_LOAD, s.getInitialLoadCandles());
        if (!r.passed()) return r;
        s.setInitialLoadCandles(1_000_000);
        r = assertEquals("clamped down", ModSettingsRequest.MAX_PRICE_HISTORY_INITIAL_LOAD, s.getInitialLoadCandles());
        if (!r.passed()) return r;
        s.setInitialLoadCandles(0);
        return assertEquals("0 resets to not synced", 0, s.getInitialLoadCandles());
    }

    /** Synced value wins; unsynced falls back to the local server (single-player), then to 512. */
    private TestResult test_resolveFallbackOrder() {
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        if (srv == null || srv.SERVER_SETTINGS == null)
            return skip("no server instances");
        ClientSettings s = new ClientSettings();
        TestResult r = assertEquals("nothing known -> 512", 512, ClientMarket.resolveInitialLoadCandles(s, null));
        if (!r.passed()) return r;
        r = assertEquals("null settings -> 512", 512, ClientMarket.resolveInitialLoadCandles(null, null));
        if (!r.passed()) return r;
        r = assertEquals("unsynced -> local server value",
                srv.SERVER_SETTINGS.MARKET.getPriceHistoryInitialLoadCandles(), ClientMarket.resolveInitialLoadCandles(s, srv));
        if (!r.passed()) return r;
        s.setInitialLoadCandles(777);
        return assertEquals("synced value wins over the local server", 777, ClientMarket.resolveInitialLoadCandles(s, srv));
    }

    /**
     * Master: the join request answers the live setting, and applying it like the client does
     * ({@code ClientMarketManager.syncServerTime}) gives the client that value.
     */
    private TestResult test_joinValueFromServerTimeRequest() {
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        if (srv == null || srv.MARKET_MANAGER == null || srv.MARKET_MANAGER.isSlave())
            return skip("master only");
        Integer before = srv.SERVER_SETTINGS.MARKET.PRICE_HISTORY_INITIAL_LOAD_CANDLES.get();
        try {
            srv.SERVER_SETTINGS.MARKET.PRICE_HISTORY_INITIAL_LOAD_CANDLES.set(1000);
            ServerTimeRequest.OutputData out = srv.NETWORKING.SERVER_TIME_REQUEST
                    .handleOnMasterServer(System.currentTimeMillis(), "", null).join();
            TestResult r = assertEquals("request carries the live value", 1000, out.initialLoadCandles());
            if (!r.passed()) return r;
            ClientSettings client = new ClientSettings();
            client.setInitialLoadCandles(out.initialLoadCandles());
            return assertEquals("client resolves the master's value", 1000,
                    ClientMarket.resolveInitialLoadCandles(client, null));
        } finally {
            srv.SERVER_SETTINGS.MARKET.PRICE_HISTORY_INITIAL_LOAD_CANDLES.set(before);
        }
    }

    /** The hand-written OutputData codec keeps every field, including the new one. */
    private TestResult test_outputCodecRoundTrip() {
        if (getServer() == null)
            return skip("needs a server for the registry access");
        ServerTimeRequest.OutputData in = new ServerTimeRequest.OutputData(123456789L, 42L, 600, 720, true, true, 2048);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), getServer().registryAccess());
        try {
            ServerTimeRequest.OutputData.STREAM_CODEC.encode(buf, in);
            return assertEquals("decoded equals encoded", in, ServerTimeRequest.OutputData.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    /**
     * SET path: an unchanged value sends nothing; a change broadcasts, and the packet updates a client.
     * The broadcast carries the server's CURRENT value, so online test players keep the right one.
     */
    private TestResult test_setRepushesOnlyOnChange() {
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        if (srv == null || srv.MARKET_MANAGER == null || srv.MARKET_MANAGER.isSlave())
            return skip("master only");
        int current = srv.SERVER_SETTINGS.MARKET.getPriceHistoryInitialLoadCandles();
        TestResult r = assertFalse("unchanged -> no push", ClientSettingsSyncPacket.broadcastIfChanged(current, current));
        if (!r.passed()) return r;
        r = assertTrue("changed -> push", ClientSettingsSyncPacket.broadcastIfChanged(current + 1, current));
        if (!r.passed()) return r;
        ClientSettings client = new ClientSettings();
        client.setInitialLoadCandles(512);
        new ClientSettingsSyncPacket(3000).applyTo(client);
        return assertEquals("pushed value replaces the join value", 3000, client.getInitialLoadCandles());
    }

    /**
     * Slave: the join request is answered by the master (routed), with a value inside the sanitize bounds.
     * The slave's own settings are compile-time defaults, so this only proves the routing, not a specific value.
     */
    private TestResult test_slaveGetsMasterValue() {
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        if (srv == null || srv.MARKET_MANAGER == null || !srv.MARKET_MANAGER.isSlave())
            return skip("slave only");
        if (!MultiServerUtils.canInteractWithStockMarket())
            return skip("not connected to the master");
        ServerTimeRequest.OutputData out = await(
                srv.NETWORKING.SERVER_TIME_REQUEST.sendRequestToMaster(System.currentTimeMillis()), WAIT_MS);
        TestResult r = assertNotNull("master answered", out);
        if (!r.passed()) return r;
        r = assertTrue("answer comes from the master (not the default response)", out.serverTime() > 0);
        if (!r.passed()) return r;
        return assertTrue("value inside the sanitize bounds: " + out.initialLoadCandles(),
                out.initialLoadCandles() >= ModSettingsRequest.MIN_PRICE_HISTORY_INITIAL_LOAD
                        && out.initialLoadCandles() <= ModSettingsRequest.MAX_PRICE_HISTORY_INITIAL_LOAD);
    }

    /**
     * SM-274 (sp only: the client state lives in this JVM): an admin SET of the initial-load candles through
     * {@link ModSettingsRequest} reaches the client ({@link ClientSettingsSyncPacket}), and a ClientMarket's next
     * initial price-history request asks for exactly that many candles (read from the request the server
     * received, through a {@link PacketTap}). The old value is restored through the same SET path.
     */
    private TestResult test_spSetReachesClientAndChartRequest() {
        MinecraftServer server = getServer();
        StockMarketModBackend.ServerInstances srv = StockMarketModBackend.getServerInstances();
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (server == null || server.isDedicatedServer() || client == null || client.SETTINGS == null || client.NETWORKING == null)
            return skip("sp only (needs the client in this JVM)");
        if (srv == null || srv.MARKET_MANAGER == null || srv.MARKET_MANAGER.isSlave())
            return skip("master only");
        if (server.getPlayerList().getPlayers().isEmpty())
            return skip("no player online");
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!PermissionCompat.hasPermission(player, StockMarketPermissions.FALLBACK_LEVEL))
            return skip("the player has no operator level 2 (cheats off)");
        List<ItemID> markets = srv.MARKET_MANAGER.getSync().getAvailableMarketIDs();
        if (markets.isEmpty())
            return skip("no market to request history for");

        UUID uuid = player.getUUID();
        boolean wasAdmin = srv.MARKET_MANAGER.getSync().isStockmarketAdmin(uuid);
        int before = srv.SERVER_SETTINGS.MARKET.getPriceHistoryInitialLoadCandles();
        int target = before == 777 ? 778 : 777;
        try {
            if (!wasAdmin)
                srv.MARKET_MANAGER.getSync().setStockmarketAdminMode(uuid, true);
            ModSettingsRequest.OutputData out = setInitialLoad(srv, uuid, target);
            TestResult r = assertTrue("SET accepted: " + out.message(), out.success());
            if (!r.passed()) return r;
            pumpUntil(() -> client.SETTINGS.getInitialLoadCandles() == target, WAIT_MS);
            r = assertEquals("client synced value", target, client.SETTINGS.getInitialLoadCandles());
            if (!r.passed()) return r;

            try (PacketTap tap = PacketTap.open(server, player)) {
                if (tap == null) return fail("no network connection for the player");
                String typeID = srv.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.getRequestTypeID();
                long delta = ClientMarket.getAvailableCandleTimeDeltas()[0];
                new ClientMarket(markets.get(0), 1).requestInitialWindow(delta);
                pumpUntil(() -> !tap.receivedRequests(typeID).isEmpty(), WAIT_MS);
                List<byte[]> got = tap.receivedRequests(typeID);
                if (got.isEmpty()) return fail("the server received no price-history request");
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(got.get(0)), server.registryAccess());
                MarketPriceHistoryRequest.InputData in = srv.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.decodeInput(buf);
                r = assertEquals("request delta", delta, in.candleDeltaMs());
                if (!r.passed()) return r;
                return assertEquals("candles in the initial window", (long) target,
                        (in.maxTimestamp() - in.minTimestamp()) / delta);
            }
        } finally {
            setInitialLoad(srv, uuid, before);
            if (!wasAdmin)
                srv.MARKET_MANAGER.getSync().setStockmarketAdminMode(uuid, false);
        }
    }

    /** Sets PRICE_HISTORY_INITIAL_LOAD_CANDLES through the admin SET path (only that setting in the payload). */
    private ModSettingsRequest.OutputData setInitialLoad(StockMarketModBackend.ServerInstances srv, UUID player, int value) {
        String json = "{\"ServerMarket\":{\"PRICE_HISTORY_INITIAL_LOAD_CANDLES\":" + value + "}}";
        return srv.NETWORKING.MOD_SETTINGS_REQUEST.handleOnMasterServer(
                new ModSettingsRequest.InputData(ModSettingsRequest.Action.SET, json), "", player).join();
    }
}
