package net.kroia.stockmarket.data.table.record;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record MarketPriceStruct(short id, long open, long low, long high, long time) {
    private static final Random RANDOM = new Random();

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPriceStruct> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT, p -> p.id,
            ByteBufCodecs.VAR_LONG, p -> p.open,
            ByteBufCodecs.VAR_LONG, p -> p.low,
            ByteBufCodecs.VAR_LONG, p -> p.high,
            ByteBufCodecs.VAR_LONG, p -> p.time,
            MarketPriceStruct::new
    );


    public static List<MarketPriceStruct> generateExampleData(int num){
        int[] ints = RANDOM.ints(num*4L,0, Short.MAX_VALUE).toArray();
        long time = Long.MAX_VALUE;
        ArrayList<MarketPriceStruct> list = new ArrayList<>();
        for(int i=0;i<num;i++){
            int idx = i*4;
            list.add(new MarketPriceStruct((short) ints[idx], ints[idx+1], ints[idx+2], ints[idx+3], time));
        }

        return list;

    }

}
