package net.kroia.stockmarket.stockmarket.market;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class MarketSettings {

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.marketOpen,
            ByteBufCodecs.VAR_LONG, p -> p.defaultPrice,
            ByteBufCodecs.FLOAT, p -> p.naturalAbundance,
            MarketSettings::new
    );

    public boolean marketOpen;
    public long defaultPrice;
    // Volume scale factor derived from item rarity (high = common, low = rare)
    public float naturalAbundance;

    public MarketSettings()
    {
        this.marketOpen = false;
        this.defaultPrice = 0;
        this.naturalAbundance = 10f;
    }
    public MarketSettings(boolean marketOpen, long defaultPrice)
    {
        this(marketOpen, defaultPrice, 10f);
    }
    public MarketSettings(boolean marketOpen, long defaultPrice, float naturalAbundance)
    {
        this.marketOpen = marketOpen;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
    }
}
