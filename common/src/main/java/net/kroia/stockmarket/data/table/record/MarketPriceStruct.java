package net.kroia.stockmarket.data.table.record;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;


public record MarketPriceStruct(short id, long open, long low, long high, long time, float tradedVolume) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPriceStruct> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT, p -> p.id,
            ByteBufCodecs.VAR_LONG, p -> p.open,
            ByteBufCodecs.VAR_LONG, p -> p.low,
            ByteBufCodecs.VAR_LONG, p -> p.high,
            ByteBufCodecs.VAR_LONG, p -> p.time,
            ByteBufCodecs.FLOAT, p -> p.tradedVolume,
            MarketPriceStruct::new
    );

}
