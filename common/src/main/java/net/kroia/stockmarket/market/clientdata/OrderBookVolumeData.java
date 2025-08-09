package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.minecraft.network.FriendlyByteBuf;

public class OrderBookVolumeData implements INetworkPayloadEncoder {


    public final float minPrice;
    public final float maxPrice;
    public final long[] volume;


    public OrderBookVolumeData(float minPrice, float maxPrice, long[] volume) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.volume = volume;
    }




    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(minPrice);
        buf.writeFloat(maxPrice);
        buf.writeInt(volume.length);
        for (long v : volume) {
            buf.writeLong(v);
        }
    }


    public static OrderBookVolumeData decode(FriendlyByteBuf buf) {
        float minPrice = buf.readFloat();
        float maxPrice = buf.readFloat();
        int tiles = buf.readInt();
        long[] volume = new long[tiles];
        for (int i = 0; i < tiles; i++) {
            volume[i] = buf.readLong();
        }
        return new OrderBookVolumeData(minPrice, maxPrice, volume);
    }

    public long getMaxVolume()
    {
        long maxVolume = 0;
        for (long l : volume) {
            maxVolume = Math.max(maxVolume, Math.abs(l));
        }
        return maxVolume;
    }
}
