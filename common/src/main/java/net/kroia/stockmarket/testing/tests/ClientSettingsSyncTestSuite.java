package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.packet.ClientSettingsSyncPacket;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.networking.request.ServerTimeRequest;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;

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
}
