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
            ByteBufCodecs.BOOL.encode(buf, s.marketOpen);
            ByteBufCodecs.VAR_LONG.encode(buf, s.defaultPrice);
            ByteBufCodecs.FLOAT.encode(buf, s.naturalAbundance);
            ByteBufCodecs.VAR_LONG.encode(buf, s.netPlayerItemFlow);
            ByteBufCodecs.BOOL.encode(buf, s.virtualOrderbookEnabled);
            ByteBufCodecs.BOOL.encode(buf, s.virtualOrderbookCleared);
            ByteBufCodecs.BOOL.encode(buf, s.ignorePluginAutosubscribe);
        }

        @Override
        public MarketSettings decode(RegistryFriendlyByteBuf buf) {
            MarketSettings s = new MarketSettings();
            s.marketOpen = ByteBufCodecs.BOOL.decode(buf);
            s.defaultPrice = ByteBufCodecs.VAR_LONG.decode(buf);
            s.naturalAbundance = ByteBufCodecs.FLOAT.decode(buf);
            s.netPlayerItemFlow = ByteBufCodecs.VAR_LONG.decode(buf);
            s.virtualOrderbookEnabled = ByteBufCodecs.BOOL.decode(buf);
            s.virtualOrderbookCleared = ByteBufCodecs.BOOL.decode(buf);
            s.ignorePluginAutosubscribe = ByteBufCodecs.BOOL.decode(buf);
            return s;
        }
    };

    public boolean marketOpen;
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

    public MarketSettings()
    {
        this.marketOpen = false;
        this.defaultPrice = 0;
        this.naturalAbundance = 10f;
        this.netPlayerItemFlow = 0;
        this.virtualOrderbookEnabled = true;
        this.virtualOrderbookCleared = false;
        this.ignorePluginAutosubscribe = false;
    }
    public MarketSettings(boolean marketOpen, long defaultPrice)
    {
        this(marketOpen, defaultPrice, 10f, 0, true);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance)
    {
        this(marketOpen, defaultPrice, naturalAbundance, 0, true);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance, long netPlayerItemFlow)
    {
        this(marketOpen, defaultPrice, naturalAbundance, netPlayerItemFlow, true);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance, long netPlayerItemFlow, boolean virtualOrderbookEnabled)
    {
        this.marketOpen = marketOpen;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
        this.netPlayerItemFlow = netPlayerItemFlow;
        this.virtualOrderbookEnabled = virtualOrderbookEnabled;
        this.virtualOrderbookCleared = false;
        this.ignorePluginAutosubscribe = false;
    }
}
