package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.integration.StockMarketClientIntegration;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * SM-313: {@link StockMarketClientIntegration} for BankSystem's company screen: the embedded chart's
 * price stream subscription stays balanced, unknown markets subscribe and send nothing, the trade
 * screen's initial market choice and the OpenUIPacket market round trip. The chart tests need a client
 * in this JVM (sp cells) and run on its thread; they skip on a dedicated server.
 */
public class ClientIntegrationTestSuite extends TestSuite {

    private static final long WAIT_MS = 10_000;
    /** An item id no market uses (checked by the tests before use). */
    private static final ItemID UNKNOWN = new ItemID(Short.MAX_VALUE);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.CLIENT_INTEGRATION;
    }

    @Override
    public void registerTests() {
        addTest("chart_subscription_balanced", this::test_chartSubscriptionBalanced);
        addTest("chart_unknown_market_no_subscription", this::test_chartUnknownMarketNoSubscription);
        addTest("open_trade_screen_unknown_market_false", this::test_openTradeScreenUnknownMarketFalse);
        addTest("pick_initial_market", this::test_pickInitialMarket);
        addTest("open_ui_packet_codec_market", this::test_openUiPacketCodecMarket);
    }

    /** connect, setRoot(null), a second close and a reconnect + close each leave the count where they should. */
    private TestResult test_chartSubscriptionBalanced() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.MARKET_MANAGER == null)
            return skip("no client in this JVM");
        return onClient(() -> {
            ClientMarket market = firstCachedMarket(client);
            if (market == null)
                return skip("no market in the client cache");
            int base = market.getStreamSubscriberCount();
            GuiElement element = StockMarketClientIntegration.createPriceChart(market.getItemID());
            if (!(element instanceof DisplayCandlestickChart chart))
                return fail("createPriceChart did not return a DisplayCandlestickChart");
            try {
                TestResult r = assertTrue("connect() true for a cached market", chart.connect());
                if (!r.passed()) return r;
                r = assertEquals("created + connect() = one subscription", base + 1, market.getStreamSubscriberCount());
                if (!r.passed()) return r;
                chart.setRoot(null);
                r = assertEquals("setRoot(null) releases it", base, market.getStreamSubscriberCount());
                if (!r.passed()) return r;
                chart.close();
                r = assertEquals("second close() is a no-op", base, market.getStreamSubscriberCount());
                if (!r.passed()) return r;
                r = assertTrue("reconnect", chart.connect());
                if (!r.passed()) return r;
                r = assertEquals("reconnected", base + 1, market.getStreamSubscriberCount());
                if (!r.passed()) return r;
                chart.close();
                return assertEquals("close() back to the baseline", base, market.getStreamSubscriberCount());
            } finally {
                chart.close();
            }
        });
    }

    /** A chart of a market the client does not have connects to nothing and changes no subscriber count. */
    private TestResult test_chartUnknownMarketNoSubscription() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.MARKET_MANAGER == null)
            return skip("no client in this JVM");
        return onClient(() -> {
            if (client.MARKET_MANAGER.getMarket(UNKNOWN) != null)
                return skip("the test item id has a market");
            int before = totalSubscribers(client);
            DisplayCandlestickChart chart = (DisplayCandlestickChart) StockMarketClientIntegration.createPriceChart(UNKNOWN);
            try {
                TestResult r = assertFalse("connect() false", chart.connect());
                if (!r.passed()) return r;
                r = assertFalse("not connected", chart.isConnected());
                if (!r.passed()) return r;
                return assertEquals("no subscriber count changed", before, totalSubscribers(client));
            } finally {
                chart.close();
            }
        });
    }

    /** openTradeScreen refuses (and sends nothing) for a market the client cache does not have. */
    private TestResult test_openTradeScreenUnknownMarketFalse() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.MARKET_MANAGER == null)
            return skip("no client in this JVM");
        return onClient(() -> {
            if (client.MARKET_MANAGER.getMarket(UNKNOWN) != null)
                return skip("the test item id has a market");
            return assertFalse("unknown market -> false", StockMarketClientIntegration.openTradeScreen(UNKNOWN));
        });
    }

    /** Preselect if listed, else last, else first, else null. */
    private TestResult test_pickInitialMarket() {
        // TradeScreen extends the client-only Screen: loading it on a dedicated server crashes (SM-313).
        if (StockMarketModBackend.getClientInstances() == null)
            return skip("no client in this JVM");
        ItemID a = new ItemID((short) 101), b = new ItemID((short) 102), c = new ItemID((short) 103);
        List<ItemID> markets = List.of(a, b);
        TestResult r = assertEquals("listed preselect wins", b, TradeScreen.pickInitialMarket(b, a, markets));
        if (!r.passed()) return r;
        r = assertEquals("unlisted preselect -> last", a, TradeScreen.pickInitialMarket(c, a, markets));
        if (!r.passed()) return r;
        r = assertEquals("no preselect -> last", b, TradeScreen.pickInitialMarket(null, b, markets));
        if (!r.passed()) return r;
        r = assertEquals("unlisted last -> first", a, TradeScreen.pickInitialMarket(c, c, markets));
        if (!r.passed()) return r;
        return assertNull("no markets -> null", TradeScreen.pickInitialMarket(a, b, List.of()));
    }

    /** The optional market survives the codec, and a packet without one decodes to null. */
    private TestResult test_openUiPacketCodecMarket() {
        if (getServer() == null)
            return skip("needs a server for the registry access");
        ItemID market = new ItemID((short) 42);
        OpenUIPacket with = roundTrip(new OpenUIPacket(OpenUIPacket.GUIType.TRADE, market));
        TestResult r = assertEquals("type kept", OpenUIPacket.GUIType.TRADE, with.getGuiType());
        if (!r.passed()) return r;
        r = assertEquals("market kept", market, with.getMarket());
        if (!r.passed()) return r;
        OpenUIPacket without = roundTrip(new OpenUIPacket(OpenUIPacket.GUIType.NEWS));
        r = assertEquals("type kept without market", OpenUIPacket.GUIType.NEWS, without.getGuiType());
        if (!r.passed()) return r;
        return assertNull("no market", without.getMarket());
    }

    private OpenUIPacket roundTrip(OpenUIPacket in) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), getServer().registryAccess());
        try {
            OpenUIPacket.STREAM_CODEC.encode(buf, in);
            return OpenUIPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** Runs {@code body} on the client thread (the API's thread) and waits for it. */
    private <T> T onClient(Supplier<T> body) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.isSameThread())
            return body.get();
        return await(mc.submit(body), WAIT_MS);
    }

    private static @Nullable ClientMarket firstCachedMarket(StockMarketModBackend.ClientInstances client) {
        for (ItemID id : client.MARKET_MANAGER.getAvailableMarkets()) {
            ClientMarket market = client.MARKET_MANAGER.getMarket(id);
            if (market != null) return market;
        }
        return null;
    }

    private static int totalSubscribers(StockMarketModBackend.ClientInstances client) {
        int sum = 0;
        for (ItemID id : client.MARKET_MANAGER.getAvailableMarkets()) {
            ClientMarket market = client.MARKET_MANAGER.getMarket(id);
            if (market != null) sum += market.getStreamSubscriberCount();
        }
        return sum;
    }
}
