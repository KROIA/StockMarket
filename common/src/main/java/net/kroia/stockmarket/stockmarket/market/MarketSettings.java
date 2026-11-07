package net.kroia.stockmarket.stockmarket.market;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class MarketSettings {

    // Hand-rolled codec: StreamCodec.composite maxes out at 6 fields and we now carry 7.
    // Encoding order is stable; new fields are appended at the end.
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSettings> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, MarketSettings s) {
            ByteBufCodecs.BOOL.encode(buf, s.maintenance);
            ByteBufCodecs.VAR_LONG.encode(buf, s.defaultPrice);
            ByteBufCodecs.FLOAT.encode(buf, s.naturalAbundance);
            ByteBufCodecs.VAR_LONG.encode(buf, s.netPlayerItemFlow);
            ByteBufCodecs.BOOL.encode(buf, s.virtualOrderbookEnabled);
            ByteBufCodecs.BOOL.encode(buf, s.virtualOrderbookCleared);
            ByteBufCodecs.BOOL.encode(buf, s.ignorePluginAutosubscribe);
            ByteBufCodecs.VAR_INT.encode(buf, s.tradingOpenMinute);
            ByteBufCodecs.VAR_INT.encode(buf, s.tradingCloseMinute);
            ByteBufCodecs.VAR_LONG.encode(buf, s.maxOrderVolume);
        }

        @Override
        public MarketSettings decode(RegistryFriendlyByteBuf buf) {
            MarketSettings s = new MarketSettings();
            s.maintenance = ByteBufCodecs.BOOL.decode(buf);
            s.defaultPrice = ByteBufCodecs.VAR_LONG.decode(buf);
            s.naturalAbundance = ByteBufCodecs.FLOAT.decode(buf);
            s.netPlayerItemFlow = ByteBufCodecs.VAR_LONG.decode(buf);
            s.virtualOrderbookEnabled = ByteBufCodecs.BOOL.decode(buf);
            s.virtualOrderbookCleared = ByteBufCodecs.BOOL.decode(buf);
            s.ignorePluginAutosubscribe = ByteBufCodecs.BOOL.decode(buf);
            s.tradingOpenMinute = ByteBufCodecs.VAR_INT.decode(buf);
            s.tradingCloseMinute = ByteBufCodecs.VAR_INT.decode(buf);
            s.maxOrderVolume = ByteBufCodecs.VAR_LONG.decode(buf);
            return s;
        }
    };

    // Admin/integration maintenance mode (formerly "marketOpen", inverted). When true, player
    // orders are rejected; bots keep trading. Persisted in ServerMarket NBT. Default false.
    public boolean maintenance;
    public long defaultPrice;
    // Volume scale factor derived from item rarity (high = common, low = rare)
    public float naturalAbundance;
    // Net items put into market by players (sell increases, buy decreases). Read-only on client.
    public long netPlayerItemFlow;
    // Whether the market has a virtual orderbook. Plugins that write to or read from the
    // virtual orderbook should skip this market entirely when this is false. Defaults to true
    // so existing markets (loaded from NBT / JSON that predate this field) keep working.
    public boolean virtualOrderbookEnabled;
    // "Sticky clear" flag. When true, the virtual orderbook's default-volume provider yields
    // 0 for all price levels, so any shift-fill that repopulates newly-visible array slots
    // during a price move also lands at 0. Cleared by the admin "Reset Virtual Orderbook"
    // action; set by "Clear Virtual Orderbook". Independent of virtualOrderbookEnabled —
    // "disabled" prevents plugins from touching it, "cleared" makes the shift-fill zero.
    public boolean virtualOrderbookCleared;
    // When true, markets created from a preset carrying this flag skip the plugin auto-
    // subscribe pass in ServerPluginManager.autoSubscribeNewMarket(). Manual subscribe /
    // unsubscribe still works. Default false (i.e. existing markets get autosubscribed).
    public boolean ignorePluginAutosubscribe;
    // Daily trading window in minutes of the day [0, 1440]. Clock source (in-game or server
    // real time) is the global setting StockMarketModSettings.Market.TRADING_HOURS_USE_REAL_TIME.
    // open > close wraps over midnight (e.g. 22:00-06:00). 0/1440 = always open (default).
    public int tradingOpenMinute = 0;
    public int tradingCloseMinute = 1440;
    /**
     * Maximum volume (real item count, buy or sell) a single player order may have on this market.
     * 0 = unlimited. Bots are not limited. Defaults to {@link #defaultMaxOrderVolume(float)} of the
     * natural abundance; markets loaded from saves that predate this field derive it the same way.
     */
    public long maxOrderVolume;

    /**
     * Default max order volume derived from a market's natural abundance:
     * {@code max(64, round(64 * naturalAbundance))} - one stack per abundance unit, at least one stack.
     * Single source of the formula, used for new markets, presets and old saves.
     *
     * @param naturalAbundance the market's natural abundance
     * @return the default max order volume in real items
     */
    public static long defaultMaxOrderVolume(float naturalAbundance)
    {
        return Math.max(64L, Math.round(64.0 * naturalAbundance));
    }

    /**
     * Trading state shown to clients. MAINTENANCE (admin/integration maintenance mode) takes precedence over
     * CLOSED (outside the scheduled trading hours or closed by command override).
     */
    public enum TradingState { OPEN, CLOSED, MAINTENANCE }

    /**
     * @return true if the window covers the whole day (00:00-24:00).
     */
    public boolean isAlwaysOpen()
    {
        return tradingOpenMinute == tradingCloseMinute
                || (tradingOpenMinute <= 0 && tradingCloseMinute >= 1440);
    }

    /**
     * Checks whether the given minute of the day lies inside the trading window.
     * The window is half-open [open, close). If open > close the window wraps over midnight.
     * @param minuteOfDay minute of the day, 0..1439
     * @return true if trading is scheduled to be open at that minute
     */
    public boolean isWithinTradingHours(int minuteOfDay)
    {
        if (isAlwaysOpen())
            return true;
        if (tradingOpenMinute < tradingCloseMinute)
            return minuteOfDay >= tradingOpenMinute && minuteOfDay < tradingCloseMinute;
        return minuteOfDay >= tradingOpenMinute || minuteOfDay < tradingCloseMinute;
    }

    /**
     * Formats a minute of the day as "HH:MM". 1440 is formatted as "24:00".
     * @param minuteOfDay minute of the day, 0..1440
     */
    public static String formatMinute(int minuteOfDay)
    {
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    /** Complete time: "H", "HH", "H:MM" or "HH:MM". */
    private static final java.util.regex.Pattern TIME_PATTERN = java.util.regex.Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?$");

    /**
     * Parses a user-entered time into a minute of the day.
     * Accepts "H", "HH", "H:MM", "HH:MM"; hour 0-24, minute 0-59, 24 only as 24:00.
     *
     * @param text the time text (surrounding whitespace is ignored)
     * @return minute of the day in [0, 1440], or -1 if invalid
     */
    public static int parseMinute(String text)
    {
        java.util.regex.Matcher m = TIME_PATTERN.matcher(text.trim());
        if (!m.matches())
            return -1;
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (hour > 24 || minute > 59 || (hour == 24 && minute != 0))
            return -1;
        return hour * 60 + minute;
    }

    /**
     * Validates and normalizes a trading window given as minutes of the day.
     * An open time of 1440 (24:00) is treated as 0 (00:00). open == close is only valid for the
     * whole-day window (00:00/24:00); open &gt; close is an overnight window and valid.
     *
     * @param openMinute  open time, 0..1440, or negative if unparsable
     * @param closeMinute close time, 0..1440, or negative if unparsable
     * @return the normalized {@code {open, close}}, or {@code null} if the window is invalid
     */
    public static int[] validateTradingWindow(int openMinute, int closeMinute)
    {
        if (openMinute < 0 || closeMinute < 0 || openMinute > 1440 || closeMinute > 1440)
            return null;
        if (openMinute == 1440)
            openMinute = 0;
        if (openMinute == closeMinute % 1440 && openMinute != 0)
            return null;
        return new int[]{openMinute, closeMinute};
    }

    public MarketSettings()
    {
        this.maintenance = false;
        this.defaultPrice = 0;
        this.naturalAbundance = 10f;
        this.netPlayerItemFlow = 0;
        this.virtualOrderbookEnabled = true;
        this.virtualOrderbookCleared = false;
        this.ignorePluginAutosubscribe = false;
        this.maxOrderVolume = defaultMaxOrderVolume(naturalAbundance);
    }
    public MarketSettings(boolean maintenance, long defaultPrice)
    {
        this(maintenance, defaultPrice, 10f, 0, true);
    }
    public MarketSettings(boolean maintenance, long defaultPrice, float naturalAbundance)
    {
        this(maintenance, defaultPrice, naturalAbundance, 0, true);
    }
    public MarketSettings(boolean maintenance, long defaultPrice, float naturalAbundance, long netPlayerItemFlow)
    {
        this(maintenance, defaultPrice, naturalAbundance, netPlayerItemFlow, true);
    }
    public MarketSettings(boolean maintenance, long defaultPrice, float naturalAbundance, long netPlayerItemFlow, boolean virtualOrderbookEnabled)
    {
        this.maintenance = maintenance;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
        this.netPlayerItemFlow = netPlayerItemFlow;
        this.virtualOrderbookEnabled = virtualOrderbookEnabled;
        this.virtualOrderbookCleared = false;
        this.ignorePluginAutosubscribe = false;
        this.maxOrderVolume = defaultMaxOrderVolume(naturalAbundance);
    }
}
