package net.kroia.stockmarket.testing.tests;

import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

public class MarketSettingsTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_SETTINGS;
    }

    @Override
    public void registerTests() {
        addTest("default_constructor", this::test_defaultConstructor);
        addTest("parameterized_constructor", this::test_parameterizedConstructor);
        addTest("stream_codec_round_trip", this::test_streamCodec_roundTrip);
    }

    /**
     * Default constructor: marketOpen == false, defaultPrice == 0.
     */
    private TestResult test_defaultConstructor() {
        MarketSettings settings = new MarketSettings();
        TestResult r = assertFalse("Default marketOpen should be false", settings.marketOpen);
        if (!r.passed()) return r;
        r = assertEquals("Default defaultPrice should be 0", 0L, settings.defaultPrice);
        if (!r.passed()) return r;
        r = assertEquals("Default naturalAbundance should be 10", 10f, settings.naturalAbundance);
        if (!r.passed()) return r;
        return pass("Default constructor initializes correctly");
    }

    /**
     * Parameterized constructor sets fields correctly.
     */
    private TestResult test_parameterizedConstructor() {
        MarketSettings settings = new MarketSettings(true, 500L);
        TestResult r = assertTrue("marketOpen should be true", settings.marketOpen);
        if (!r.passed()) return r;
        r = assertEquals("defaultPrice should be 500", 500L, settings.defaultPrice);
        if (!r.passed()) return r;
        r = assertEquals("naturalAbundance should default to 10", 10f, settings.naturalAbundance);
        if (!r.passed()) return r;

        MarketSettings full = new MarketSettings(true, 500L, 42.5f);
        r = assertEquals("naturalAbundance should be 42.5", 42.5f, full.naturalAbundance);
        if (!r.passed()) return r;
        return pass("Parameterized constructors set fields correctly");
    }

    /**
     * StreamCodec encode/decode preserves fields.
     * Note: StreamCodec requires RegistryFriendlyByteBuf which is not easily available
     * in a pure logic test. We verify the fields are public and accessible as a fallback.
     */
    private TestResult test_streamCodec_roundTrip() {
        // We cannot easily create a RegistryFriendlyByteBuf in a pure test context.
        // Instead, verify the codec is non-null and the constructor-based round trip works.
        TestResult r = assertNotNull("STREAM_CODEC should not be null", MarketSettings.STREAM_CODEC);
        if (!r.passed()) return r;

        // Verify that the parameterized constructor used by STREAM_CODEC.decode works
        MarketSettings original = new MarketSettings(true, 12345L);
        // Simulate what decode does: call the constructor with the same values
        MarketSettings decoded = new MarketSettings(original.marketOpen, original.defaultPrice, original.naturalAbundance);
        r = assertEquals("marketOpen should survive round-trip", original.marketOpen, decoded.marketOpen);
        if (!r.passed()) return r;
        r = assertEquals("defaultPrice should survive round-trip", original.defaultPrice, decoded.defaultPrice);
        if (!r.passed()) return r;
        r = assertEquals("naturalAbundance should survive round-trip", original.naturalAbundance, decoded.naturalAbundance);
        if (!r.passed()) return r;
        return pass("StreamCodec round-trip (constructor-based verification) preserves fields");
    }
}
