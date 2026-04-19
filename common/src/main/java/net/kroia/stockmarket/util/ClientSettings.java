package net.kroia.stockmarket.util;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class ClientSettings {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, p -> p.dummy,
            ClientSettings::new
    );

    boolean dummy;

    boolean fillMissingCandlesticks = true;

    public ClientSettings() {
    }
    public ClientSettings(boolean dummy) {
        this.dummy = dummy;
    }
    public void loadFrom(ClientSettings settings)
    {

    }

    public void setFillMissingCandlesticks(boolean fillMissingCandlesticks) {
        this.fillMissingCandlesticks = fillMissingCandlesticks;
    }
    public boolean isFillMissingCandlesticks() {
        return fillMissingCandlesticks;
    }

}
