package net.kroia.stockmarket.data.Table.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public record MarketPriceStruct(short id, int price, int low, int high, long time) {
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
