package net.kroia.stockmarket.stockmarket.market;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class MarketSettings {

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.marketOpen,
            ByteBufCodecs.VAR_LONG, p -> p.defaultPrice,
            ByteBufCodecs.FLOAT, p -> p.naturalAbundance,
            ByteBufCodecs.VAR_LONG, p -> p.netPlayerItemFlow,
            MarketSettings::new
    );

    public boolean marketOpen;
    public long defaultPrice;
    // Volume scale factor derived from item rarity (high = common, low = rare)
    public float naturalAbundance;
    // Net items put into market by players (sell increases, buy decreases). Read-only on client.
    public long netPlayerItemFlow;

    public MarketSettings()
    {
        this.marketOpen = false;
        this.defaultPrice = 0;
        this.naturalAbundance = 10f;
        this.netPlayerItemFlow = 0;
    }
    public MarketSettings(boolean marketOpen, long defaultPrice)
    {
        this(marketOpen, defaultPrice, 10f, 0);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance)
    {
        this(marketOpen, defaultPrice, naturalAbundance, 0);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance, long netPlayerItemFlow)
    {
        this.marketOpen = marketOpen;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
        this.netPlayerItemFlow = netPlayerItemFlow;
    }
}
