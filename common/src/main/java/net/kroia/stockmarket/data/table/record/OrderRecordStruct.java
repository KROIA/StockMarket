package net.kroia.stockmarket.data.table.record;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * @param interMarketGroupID Nullable UUID that links trade records belonging to the same inter-market transaction.
 *                           All order records created by a single cross-market trade share the same group ID,
 *                           allowing them to be correlated after the fact. Null for regular (non-inter-market) trades.
 */
public record OrderRecordStruct(/*long orderID, */short itemID, int bankaccountID, UUID user, int type, long amount, long price, long time, @Nullable UUID interMarketGroupID) {

    /**
     * Backward-compatible constructor for regular (non-inter-market) trades.
     * Sets interMarketGroupID to null.
     */
    public OrderRecordStruct(short itemID, int bankaccountID, UUID user, int type, long amount, long price, long time) {
        this(itemID, bankaccountID, user, type, amount, price, time, null);
    }

    /**
     * StreamCodec for network transport of OrderRecordStruct.
     * Uses manual encode/decode because StreamCodec.composite supports at most 6 fields in MC 1.21.1.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderRecordStruct> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, OrderRecordStruct val) {
            ByteBufCodecs.SHORT.encode(buf, val.itemID());
            ByteBufCodecs.VAR_INT.encode(buf, val.bankaccountID());
            UUIDUtil.STREAM_CODEC.encode(buf, val.user());
            ByteBufCodecs.VAR_INT.encode(buf, val.type());
            ByteBufCodecs.VAR_LONG.encode(buf, val.amount());
            ByteBufCodecs.VAR_LONG.encode(buf, val.price());
            ByteBufCodecs.VAR_LONG.encode(buf, val.time());
            ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC).encode(buf, val.interMarketGroupID());
        }

        @Override
        public OrderRecordStruct decode(RegistryFriendlyByteBuf buf) {
            return new OrderRecordStruct(
                    ByteBufCodecs.SHORT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    UUIDUtil.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC).decode(buf)
            );
        }
    };

    private static final Random RANDOM = new Random();



    public static List<OrderRecordStruct> generateExampleData(int num){
        int[] ints = RANDOM.ints(num*3L,0, Short.MAX_VALUE).toArray();
        int bankaccountID = RANDOM.nextInt();
        long time = Long.MAX_VALUE;
        UUID uuid = UUID.randomUUID();
        ArrayList<OrderRecordStruct> list = new ArrayList<>();
        for(int i=0;i<num;i++){
            int idx = i*3;
            list.add(new OrderRecordStruct(/*System.currentTimeMillis(),*/ (short) ints[idx], bankaccountID, uuid,0, ints[idx+1], ints[idx+2], time));
        }

        return list;

    }
}
