package net.kroia.stockmarket.data.Table.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public record OrderRecordStruct(short itemID, UUID userID, int type, int amount, int price, long time) {

    private static final Random RANDOM = new Random();



    public static List<OrderRecordStruct> generateExampleData(int num){
        int[] ints = RANDOM.ints(num*3L,0, Short.MAX_VALUE).toArray();
        UUID uuid = UUID.randomUUID();
        long time = Long.MAX_VALUE;
        ArrayList<OrderRecordStruct> list = new ArrayList<>();
        for(int i=0;i<num;i++){
            int idx = i*3;
            list.add(new OrderRecordStruct((short) ints[idx], uuid, 0, ints[idx+1], ints[idx+2], time));
        }

        return list;

    }
}
