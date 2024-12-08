package net.kroia.stockmarket.util;

import net.minecraft.network.FriendlyByteBuf;

public class OrderbookVolume {

    private final int minPrice;
    private final int maxPrice;
    private final int tiles;
    private final int[] volume;

    public OrderbookVolume(int tiles, int minPrice, int maxPrice) {
        this.tiles = tiles;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.volume = new int[tiles];
    }

    public OrderbookVolume(FriendlyByteBuf buf)
    {
        tiles = buf.readInt();
        minPrice = buf.readInt();
        maxPrice = buf.readInt();
        volume = new int[tiles];
        for (int i = 0; i < tiles; i++)
        {
            volume[i] = buf.readInt();
        }
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeInt(tiles);
        buf.writeInt(minPrice);
        buf.writeInt(maxPrice);
        for (int i = 0; i < tiles; i++)
        {
            buf.writeInt(volume[i]);
        }
    }

    public int getVolume(int index) {
        return volume[index];
    }
    public int[] getVolume() {
        return volume;
    }

    public int getMaxVolume()
    {
        int maxVolume = 0;
        for(int i = 0; i < volume.length; i++)
        {
            maxVolume = Math.max(maxVolume, volume[i]);
        }
        return maxVolume;
    }

    public void setVolume(int index, int value) {
        volume[index] = value;
    }

    public void setVolume(int[] volume) {
        System.arraycopy(volume, 0, this.volume, 0, this.volume.length);
    }

    public int getMinPrice() {
        return minPrice;
    }

    public int getMaxPrice() {
        return maxPrice;
    }

    public int getTiles() {
        return tiles;
    }

}
