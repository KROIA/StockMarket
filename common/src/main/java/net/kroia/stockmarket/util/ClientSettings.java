package net.kroia.stockmarket.util;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class ClientSettings {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, p -> p.candleTimeMs,
            ClientSettings::new
    );

    private long candleTimeMs;

    public ClientSettings()
    {

    }
    public ClientSettings(long candleTimeMs) {
        this.candleTimeMs = candleTimeMs;
    }
    public void loadFrom(ClientSettings settings)
    {
        this.candleTimeMs = settings.candleTimeMs;
    }


    public long getCandleTimeMs() {
        return candleTimeMs;
    }
    public void setCandleTimeMs(long candleTimeMs) {
        this.candleTimeMs = candleTimeMs;
    }
}
