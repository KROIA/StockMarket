package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.minecraft.network.FriendlyByteBuf;

public class OrderBookVolumeData implements INetworkPayloadEncoder {


    public final float minPrice;
    public final float maxPrice;
    public final float[] volume;


    public OrderBookVolumeData(float minPrice, float maxPrice, float[] volume) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.volume = volume;
    }




    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(minPrice);
        buf.writeFloat(maxPrice);
        buf.writeInt(volume.length);
        for (float v : volume) {
            buf.writeFloat(v);
        }
    }


    public static OrderBookVolumeData decode(FriendlyByteBuf buf) {
        float minPrice = buf.readFloat();
        float maxPrice = buf.readFloat();
        int tiles = buf.readInt();
        float[] volume = new float[tiles];
        for (int i = 0; i < tiles; i++) {
            volume[i] = buf.readFloat();
        }
        return new OrderBookVolumeData(minPrice, maxPrice, volume);
    }

    public float getMaxVolume()
    {
        float maxVolume = 0;
        for (float l : volume) {
            maxVolume = Math.max(maxVolume, Math.abs(l));
        }
        return maxVolume;
    }
}
