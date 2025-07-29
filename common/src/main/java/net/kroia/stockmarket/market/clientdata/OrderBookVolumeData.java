package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.server.OrderBook;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class OrderBookVolumeData implements INetworkPayloadEncoder {


    public final int minPrice;
    public final int maxPrice;
    public final int tiles;
    public final long[] volume;


    public OrderBookVolumeData(int minPrice, int maxPrice, int tileCount, @NotNull OrderBook book) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.tiles = tileCount;
        this.volume = new long[tileCount];

        int stepSize = (maxPrice - minPrice) / tileCount;
        for(int i = 0; i < tileCount; i++) {
            int lowerBound = minPrice + i * stepSize;
            int upperBound = lowerBound + stepSize;
            //int upperBound = (i == tileCount - 1) ? maxPrice : lowerBound + stepSize;
            this.volume[i] = book.getVolumeInRange(lowerBound, upperBound);
        }
    }

    private OrderBookVolumeData(int minPrice, int maxPrice, int tiles, long @NotNull [] volume) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.tiles = tiles;
        this.volume = volume;
    }




    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(minPrice);
        buf.writeInt(maxPrice);
        buf.writeInt(tiles);
        for (long v : volume) {
            buf.writeLong(v);
        }
    }


    public static OrderBookVolumeData decode(FriendlyByteBuf buf) {
        int minPrice = buf.readInt();
        int maxPrice = buf.readInt();
        int tiles = buf.readInt();
        long[] volume = new long[tiles];
        for (int i = 0; i < tiles; i++) {
            volume[i] = buf.readLong();
        }
        return new OrderBookVolumeData(minPrice, maxPrice, tiles, volume);
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
