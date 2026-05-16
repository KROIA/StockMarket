package net.kroia.stockmarket.stockmarket.market.preset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Single item preset with default price and natural abundance
public record MarketPreset(
    String itemId,           // e.g. "minecraft:iron_ingot"
    float defaultPrice,      // relative wealth value
    float naturalAbundance   // how common in nature (high = common, low = rare)
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPreset> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MarketPreset::itemId,
            ByteBufCodecs.FLOAT, MarketPreset::defaultPrice,
            ByteBufCodecs.FLOAT, MarketPreset::naturalAbundance,
            MarketPreset::new
    );
}
