package net.kroia.stockmarket.data.table.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record MarketPriceStruct(short id, long open, long low, long high, long time) {
    private static final Random RANDOM = new Random();



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
