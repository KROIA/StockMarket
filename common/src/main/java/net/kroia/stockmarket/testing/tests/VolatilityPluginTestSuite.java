package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin.Settings;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * T-213: tests for the VolatilityPlugin after the net-flow split: the 1-float settings codec (also reading the
 * pre-T-213 multi-field payloads), NBT, and that the random walk sits on the published equilibrium.
 * The flow-equilibrium tests live in {@link NetFlowPluginTestSuite}.
 */
public class VolatilityPluginTestSuite extends TestSuite {

    private static final double DEFAULT_PRICE = 100.0;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.VOLATILITY_PLUGIN;
    }

    @Override
    public void registerTests() {
        addTest("codec_round_trip_and_legacy", this::test_codec);
        addTest("nbt_round_trip", this::test_nbt);
        addTest("walks_around_published_equilibrium", this::test_equilibrium);
    }

    /** One float round-trips; pre-T-213 payloads (scale + flow fields) decode their scale and are fully consumed. */
    private TestResult test_codec() {
        ByteBuf buf = Unpooled.buffer();
        try {
            Settings.CODEC.encode(buf, new Settings(0.7f));
            TestResult r = assertEquals("round-trip", new Settings(0.7f), Settings.CODEC.decode(buf));
            if (!r.passed()) return r;
            buf.clear();
            buf.writeFloat(0.55f).writeBoolean(true).writeFloat(2000f).writeFloat(0.05f).writeFloat(3f).writeFloat(0.5f);
            r = assertEquals("pre-T-213 6-field payload keeps its scale", new Settings(0.55f), Settings.CODEC.decode(buf));
            if (!r.passed()) return r;
            return assertTrue("trailing legacy fields consumed", !buf.isReadable());
        } finally {
            buf.release();
        }
    }

    /** The volatility scale survives save/load. */
    private TestResult test_nbt() {
        ItemID id = new ItemID((short) 1);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        plugin.replaceCustomSettings(id, new Settings(0.42f));
        CompoundTag tag = new CompoundTag();
        plugin.save(tag);
        VolatilityPlugin loaded = new VolatilityPlugin();
        loaded.onMarketSubscribed(id);
        loaded.load(tag);
        return assertEquals("scale after load", 0.42f, loaded.getMarketSettings(id).volatilityScale());
    }

    /** Scale 0: the target is exactly the published equilibrium, or the default price when none is published. */
    private TestResult test_equilibrium() {
        ItemID id = new ItemID((short) 1);
        NewsPluginTestSuite.TestMarket market = new NewsPluginTestSuite.TestMarket(id, DEFAULT_PRICE);
        VolatilityPlugin plugin = new VolatilityPlugin();
        plugin.onMarketSubscribed(id);
        plugin.replaceCustomSettings(id, new Settings(0f));
        List<MarketInterface> markets = List.of(new MarketInterface(market, null));
        market.targetPrice = 0;
        plugin.update(markets);
        TestResult r = assertEquals("no equilibrium -> default price", DEFAULT_PRICE, market.targetPrice);
        if (!r.passed()) return r;
        market.setEquilibriumPrice(123.25);
        plugin.update(markets);
        return assertEquals("target = published equilibrium", 123.25, market.targetPrice);
    }
}
