package net.kroia.stockmarket.data.table.record;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * @param interMarketGroupID Nullable UUID that links trade records belonging to the same inter-market transaction.
 *                           All order records created by a single cross-market trade share the same group ID,
 *                           allowing them to be correlated after the fact. Null for regular (non-inter-market) trades.
 * @param orderID            T-191: persisted order ID (ledger ref {@code sm:order:<id>}, column {@code order_id}).
 *                           Null for inter-market legs (their ledger ref is the group ID) and pre-T-191 rows.
 * @param finishTime         SM-317: epoch ms when the order finished (column {@code finish_time}). {@code time} is
 *                           when it was placed. Null for pre-SM-317 rows.
 * @param limitPrice         SM-317: the order's limit price ({@code StartPrice}), 0 for market orders (column
 *                           {@code limit_price}). Null for pre-SM-317 rows.
 * @param targetVolume       SM-317: the wanted volume ({@code TargetVolume}), signed like {@code amount}: positive
 *                           buy, negative sell (column {@code target_volume}). Null for pre-SM-317 rows.
 * @param finalStatus        SM-317: how the order ended, one of {@link #STATUS_FILLED},
 *                           {@link #STATUS_CANCELLED_BY_PLAYER}, {@link #STATUS_CANCELLED_BY_SYSTEM} (column
 *                           {@code final_status}). Null for pre-SM-317 rows.
 *                           <p>The four SM-317 fields are server-side only: {@link #STREAM_CODEC} does not send them,
 *                           so decoded records carry null.
 */
public record OrderRecordStruct(short itemID, int bankaccountID, UUID user, int type, long amount, long price, long time,
                                @Nullable UUID interMarketGroupID, @Nullable Long orderID,
                                @Nullable Long finishTime, @Nullable Long limitPrice, @Nullable Long targetVolume,
                                @Nullable Integer finalStatus) {

    /** SM-317 {@code final_status}: the whole wanted volume was filled. */
    public static final int STATUS_FILLED = 0;
    /** SM-317 {@code final_status}: the player cancelled the order (cancel button / cancel request). */
    public static final int STATUS_CANCELLED_BY_PLAYER = 1;
    /**
     * SM-317 {@code final_status}: the server ended the order before it was fully filled: a market order's
     * unfilled remainder (no more liquidity, buyer out of money), market put under maintenance, market
     * deleted, server shutdown (buffered orders), bank account frozen, inter-market execution error.
     * Also an inter-market market order that ended before its whole wanted volume was bought.
     */
    public static final int STATUS_CANCELLED_BY_SYSTEM = 2;

    /** Record without the SM-317 finish fields (rows read back from network, older call sites). */
    public OrderRecordStruct(short itemID, int bankaccountID, UUID user, int type, long amount, long price, long time,
                             @Nullable UUID interMarketGroupID, @Nullable Long orderID) {
        this(itemID, bankaccountID, user, type, amount, price, time, interMarketGroupID, orderID, null, null, null, null);
    }

    /** Record without an order ID (inter-market legs, older call sites). */
    public OrderRecordStruct(short itemID, int bankaccountID, UUID user, int type, long amount, long price, long time, @Nullable UUID interMarketGroupID) {
        this(itemID, bankaccountID, user, type, amount, price, time, interMarketGroupID, null);
    }

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
            ExtraCodecUtils.nullable(ByteBufCodecs.VAR_LONG).encode(buf, val.orderID());
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
                    ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC).decode(buf),
                    ExtraCodecUtils.nullable(ByteBufCodecs.VAR_LONG).decode(buf)
            );
        }
    };

}
