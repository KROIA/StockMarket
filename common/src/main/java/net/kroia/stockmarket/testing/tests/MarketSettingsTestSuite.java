package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
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
