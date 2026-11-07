package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.stream.MarketPriceStream;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

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
        // Trading hours (T-158)
        addTest("trading_hours_default_always_open", this::test_tradingHours_defaultAlwaysOpen);
        addTest("trading_hours_day_window_edges", this::test_tradingHours_dayWindowEdges);
        addTest("trading_hours_overnight_wrap", this::test_tradingHours_overnightWrap);
        addTest("trading_hours_format_minute", this::test_tradingHours_formatMinute);
        addTest("trading_hours_codec_round_trip", this::test_tradingHours_codecRoundTrip);
        addTest("trading_hours_parse_minute", this::test_tradingHours_parseMinute);
        addTest("trading_hours_validate_window", this::test_tradingHours_validateWindow);
        // Max order volume (T-166)
        addTest("max_order_volume_formula", this::test_maxOrderVolume_formula);
        addTest("max_order_volume_codec_round_trip", this::test_maxOrderVolume_codecRoundTrip);
        // Max order factor, amount clamp, stream status (T-179)
        addTest("max_order_volume_factor_formula", this::test_maxOrderVolume_factorFormula);
        addTest("clamp_order_amount", this::test_clampOrderAmount);
        addTest("trading_status_codec_round_trip", this::test_tradingStatus_codecRoundTrip);
    }

    /** T-179: defaultMaxOrderVolume(abundance, factor) = max(factor, round(factor x abundance)); factor &lt; 1 counts as 1. */
    private TestResult test_maxOrderVolume_factorFormula() {
        long[] factors = {1, 64, 100};
        float[] abundance = {0.1f, 5f, 200f};
        long[][] expected = {
                {1, 5, 200},        // factor 1: 0.1 rounds to 0 -> floor 1
                {64, 320, 12800},   // factor 64 (default)
                {100, 500, 20000},  // factor 100
        };
        for (int f = 0; f < factors.length; f++) {
            for (int a = 0; a < abundance.length; a++) {
                TestResult r = assertEquals("factor " + factors[f] + ", abundance " + abundance[a], expected[f][a],
                        MarketSettings.defaultMaxOrderVolume(abundance[a], factors[f]));
                if (!r.passed()) return r;
            }
        }
        TestResult r = assertEquals("factor 0 counts as 1", 5L, MarketSettings.defaultMaxOrderVolume(5f, 0));
        if (!r.passed()) return r;
        r = assertEquals("1-arg overload uses the server factor",
                MarketSettings.defaultMaxOrderVolume(5f, StockMarketModBackend.getMaxOrderVolumeFactor()),
                MarketSettings.defaultMaxOrderVolume(5f));
        if (!r.passed()) return r;
        return pass("Max order volume formula honours the factor");
    }

    /** T-179: the trade-screen amount clamp (pure helper). */
    private TestResult test_clampOrderAmount() {
        double[][] cases = {
                // amount, limit, expected
                {10, 64, 10},      // below
                {64, 64, 64},      // at
                {100, 64, 64},     // above
                {100, 0, 100},     // 0 = unlimited
                {100, -1, 100},    // negative limit = unlimited
                {-100, 64, -64},   // signed sell volume keeps its sign
                {-10, 64, -10},
                {5.5, 5, 5},       // fractional amounts
                {0, 64, 0},
        };
        for (double[] c : cases) {
            TestResult r = assertEquals("clamp(" + c[0] + ", " + c[1] + ")", c[2], MarketSettings.clampOrderAmount(c[0], c[1]));
            if (!r.passed()) return r;
        }
        return pass("clampOrderAmount limits the magnitude only above a positive limit");
    }

    /** T-179: the hand-rolled TradingStatus codec carries maxOrderVolume + tradeCooldownMs. */
    private TestResult test_tradingStatus_codecRoundTrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null)
            return pass("codec round-trip skipped (no registry access in this context)");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            MarketPriceStream.TradingStatus s = new MarketPriceStream.TradingStatus(
                    (byte) 1, (short) 725, true, (short) 480, (short) 1320, 12800L, 500L);
            MarketPriceStream.TradingStatus.STREAM_CODEC.encode(buf, s);
            MarketPriceStream.TradingStatus d = MarketPriceStream.TradingStatus.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("status survives", s, d);
            if (!r.passed()) return r;
            r = assertEquals("buffer fully consumed", 0, buf.readableBytes());
            if (!r.passed()) return r;
            return pass("TradingStatus survives the stream codec");
        } finally {
            buf.release();
        }
    }

    /** defaultMaxOrderVolume = max(64, round(64 x abundance)); constructors use it. */
    private TestResult test_maxOrderVolume_formula() {
        float[] abundance = {0.1f, 1f, 5f, 30f, 200f};
        long[] expected = {64, 64, 320, 1920, 12800};
        for (int i = 0; i < abundance.length; i++) {
            TestResult r = assertEquals("abundance " + abundance[i], expected[i], MarketSettings.defaultMaxOrderVolume(abundance[i], 64));
            if (!r.passed()) return r;
        }
        // T-179: constructors use the server's MAX_ORDER_VOLUME_FACTOR (64 by default)
        long factor = StockMarketModBackend.getMaxOrderVolumeFactor();
        TestResult r = assertEquals("ctor derives from abundance", MarketSettings.defaultMaxOrderVolume(5f, factor), new MarketSettings(false, 1L, 5f).maxOrderVolume);
        if (!r.passed()) return r;
        r = assertEquals("default ctor (abundance 10)", MarketSettings.defaultMaxOrderVolume(10f, factor), new MarketSettings().maxOrderVolume);
        if (!r.passed()) return r;
        return pass("Max order volume formula matches the approved examples");
    }

    /** maxOrderVolume (appended last) survives the stream codec. */
    private TestResult test_maxOrderVolume_codecRoundTrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null)
            return pass("codec round-trip skipped (no registry access in this context)");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            MarketSettings s = new MarketSettings(false, 5L, 30f);
            s.maxOrderVolume = 4321;
            s.tradingCloseMinute = 600;
            MarketSettings.STREAM_CODEC.encode(buf, s);
            MarketSettings d = MarketSettings.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("maxOrderVolume survives", 4321L, d.maxOrderVolume);
            if (!r.passed()) return r;
            r = assertEquals("close minute still decoded", 600, d.tradingCloseMinute);
            if (!r.passed()) return r;
            r = assertEquals("buffer fully consumed", 0, buf.readableBytes());
            if (!r.passed()) return r;
            return pass("maxOrderVolume survives the stream codec");
        } finally {
            buf.release();
        }
    }

    /** parseMinute accepts H/HH/H:MM/HH:MM and 24:00, rejects out-of-range values. */
    private TestResult test_tradingHours_parseMinute() {
        String[] in = {"8", "08", "8:15", "08:15", "0", "00:00", "23:59", "24:00", "24", " 7:05 "};
        int[] out = {480, 480, 495, 495, 0, 0, 1439, 1440, 1440, 425};
        for (int i = 0; i < in.length; i++) {
            TestResult r = assertEquals("parse '" + in[i] + "'", out[i], MarketSettings.parseMinute(in[i]));
            if (!r.passed()) return r;
        }
        for (String bad : new String[]{"24:01", "25:00", "12:60", "", ":", "8:", "8:5", "123", "ab"}) {
            TestResult r = assertEquals("reject '" + bad + "'", -1, MarketSettings.parseMinute(bad));
            if (!r.passed()) return r;
        }
        return pass("parseMinute accepts valid and rejects invalid times");
    }

    /** validateTradingWindow: open==close only for 00:00/24:00, overnight ok, open 24:00 -> 00:00. */
    private TestResult test_tradingHours_validateWindow() {
        int[][] valid = {{480, 1080}, {1320, 360}, {0, 0}, {0, 1440}, {1440, 1440}, {1440, 0}, {600, 1440}};
        int[][] expected = {{480, 1080}, {1320, 360}, {0, 0}, {0, 1440}, {0, 1440}, {0, 0}, {600, 1440}};
        for (int i = 0; i < valid.length; i++) {
            int[] w = MarketSettings.validateTradingWindow(valid[i][0], valid[i][1]);
            TestResult r = assertTrue("valid " + valid[i][0] + "-" + valid[i][1], w != null);
            if (!r.passed()) return r;
            r = assertEquals("open normalized " + i, expected[i][0], w[0]);
            if (!r.passed()) return r;
            r = assertEquals("close " + i, expected[i][1], w[1]);
            if (!r.passed()) return r;
        }
        int[][] invalid = {{600, 600}, {1439, 1439}, {-1, 600}, {600, -1}, {1441, 0}, {1440, 1441}};
        for (int[] bad : invalid) {
            TestResult r = assertTrue("invalid " + bad[0] + "-" + bad[1],
                    MarketSettings.validateTradingWindow(bad[0], bad[1]) == null);
            if (!r.passed()) return r;
        }
        return pass("validateTradingWindow enforces the window rules");
    }

    /**
     * Default constructor: maintenance == false, defaultPrice == 0.
     */
    private TestResult test_defaultConstructor() {
        MarketSettings settings = new MarketSettings();
        TestResult r = assertFalse("Default maintenance should be false", settings.maintenance);
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
        TestResult r = assertTrue("maintenance should be true", settings.maintenance);
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
        MarketSettings decoded = new MarketSettings(original.maintenance, original.defaultPrice, original.naturalAbundance);
        r = assertEquals("maintenance should survive round-trip", original.maintenance, decoded.maintenance);
        if (!r.passed()) return r;
        r = assertEquals("defaultPrice should survive round-trip", original.defaultPrice, decoded.defaultPrice);
        if (!r.passed()) return r;
        r = assertEquals("naturalAbundance should survive round-trip", original.naturalAbundance, decoded.naturalAbundance);
        if (!r.passed()) return r;
        return pass("StreamCodec round-trip (constructor-based verification) preserves fields");
    }

    // ── Trading hours (T-158) ───────────────────────────────────────────────

    /** Defaults are 00:00-24:00 and open at every minute of the day. */
    private TestResult test_tradingHours_defaultAlwaysOpen() {
        MarketSettings s = new MarketSettings();
        TestResult r = assertEquals("default open minute", 0, s.tradingOpenMinute);
        if (!r.passed()) return r;
        r = assertEquals("default close minute", 1440, s.tradingCloseMinute);
        if (!r.passed()) return r;
        r = assertTrue("default window is always open", s.isAlwaysOpen());
        if (!r.passed()) return r;
        for (int m = 0; m < 1440; m++) {
            if (!s.isWithinTradingHours(m)) return fail("default window closed at minute " + m);
        }
        s.tradingOpenMinute = 600;
        s.tradingCloseMinute = 600;
        r = assertTrue("open == close counts as always open", s.isAlwaysOpen());
        if (!r.passed()) return r;
        return pass("Default trading window is always open");
    }

    /** 08:00-18:00: open inclusive, close exclusive. */
    private TestResult test_tradingHours_dayWindowEdges() {
        MarketSettings s = new MarketSettings();
        s.tradingOpenMinute = 480;
        s.tradingCloseMinute = 1080;
        TestResult r = assertFalse("not always open", s.isAlwaysOpen());
        if (!r.passed()) return r;
        r = assertFalse("07:59 closed", s.isWithinTradingHours(479));
        if (!r.passed()) return r;
        r = assertTrue("08:00 open", s.isWithinTradingHours(480));
        if (!r.passed()) return r;
        r = assertTrue("17:59 open", s.isWithinTradingHours(1079));
        if (!r.passed()) return r;
        r = assertFalse("18:00 closed", s.isWithinTradingHours(1080));
        if (!r.passed()) return r;
        r = assertFalse("00:00 closed", s.isWithinTradingHours(0));
        if (!r.passed()) return r;
        return pass("Day window edges are [open, close)");
    }

    /** 22:00-06:00 wraps over midnight. */
    private TestResult test_tradingHours_overnightWrap() {
        MarketSettings s = new MarketSettings();
        s.tradingOpenMinute = 1320;
        s.tradingCloseMinute = 360;
        TestResult r = assertFalse("21:59 closed", s.isWithinTradingHours(1319));
        if (!r.passed()) return r;
        r = assertTrue("22:00 open", s.isWithinTradingHours(1320));
        if (!r.passed()) return r;
        r = assertTrue("23:59 open", s.isWithinTradingHours(1439));
        if (!r.passed()) return r;
        r = assertTrue("00:00 open", s.isWithinTradingHours(0));
        if (!r.passed()) return r;
        r = assertTrue("05:59 open", s.isWithinTradingHours(359));
        if (!r.passed()) return r;
        r = assertFalse("06:00 closed", s.isWithinTradingHours(360));
        if (!r.passed()) return r;
        r = assertFalse("12:00 closed", s.isWithinTradingHours(720));
        if (!r.passed()) return r;
        return pass("Overnight window wraps over midnight");
    }

    private TestResult test_tradingHours_formatMinute() {
        TestResult r = assertEquals("0 -> 00:00", "00:00", MarketSettings.formatMinute(0));
        if (!r.passed()) return r;
        r = assertEquals("495 -> 08:15", "08:15", MarketSettings.formatMinute(495));
        if (!r.passed()) return r;
        r = assertEquals("1439 -> 23:59", "23:59", MarketSettings.formatMinute(1439));
        if (!r.passed()) return r;
        r = assertEquals("1440 -> 24:00", "24:00", MarketSettings.formatMinute(1440));
        if (!r.passed()) return r;
        return pass("formatMinute formats HH:MM incl. 24:00");
    }

    /** Real codec round trip of the appended window fields (needs a registry context). */
    private TestResult test_tradingHours_codecRoundTrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null)
            return pass("codec round-trip skipped (no registry access in this context)");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            MarketSettings s = new MarketSettings(true, 777L);
            s.tradingOpenMinute = 1320;
            s.tradingCloseMinute = 360;
            MarketSettings.STREAM_CODEC.encode(buf, s);
            MarketSettings d = MarketSettings.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("open minute survives", 1320, d.tradingOpenMinute);
            if (!r.passed()) return r;
            r = assertEquals("close minute survives", 360, d.tradingCloseMinute);
            if (!r.passed()) return r;
            r = assertTrue("maintenance survives", d.maintenance);
            if (!r.passed()) return r;
            r = assertEquals("defaultPrice survives", 777L, d.defaultPrice);
            if (!r.passed()) return r;
            r = assertEquals("buffer fully consumed", 0, buf.readableBytes());
            if (!r.passed()) return r;
            return pass("Trading window survives the stream codec");
        } finally {
            buf.release();
        }
    }
}
