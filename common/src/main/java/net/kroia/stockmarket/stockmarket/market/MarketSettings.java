package net.kroia.stockmarket.stockmarket.market;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class MarketSettings {

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.marketOpen,
            ByteBufCodecs.VAR_LONG, p -> p.defaultPrice,
            MarketSettings::new
    );

    public boolean marketOpen;
    public long defaultPrice;

    public MarketSettings()
    {
        this.marketOpen = false;
        this.defaultPrice = 0;
    }
    public MarketSettings(boolean  marketOpen, long defaultPrice)
    {
        this.marketOpen = marketOpen;
        this.defaultPrice = defaultPrice;
    }
}
