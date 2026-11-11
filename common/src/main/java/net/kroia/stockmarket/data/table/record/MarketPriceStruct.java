package net.kroia.stockmarket.data.table.record;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;


/**
 * One stored price candle (one row of the SQLite {@code MarketPrice} table).
 *
 * @param id           market short ID
 * @param open         open price (raw units)
 * @param low          lowest price (raw units)
 * @param high         highest price (raw units)
 * @param time         candle start, master wall clock in ms. Stays the order and paging key in
 *                     both candle timebases.
 * @param tradedVolume traded volume during the candle (real units)
 * @param gameTime     T-218: master overworld day time (ticks, not wrapped at 24000) at candle
 *                     start, or {@link #NO_GAME_TIME} for rows written before the column existed.
 *                     Stamped in both timebases, so switching the timebase keeps all history.
 */
public record MarketPriceStruct(short id, long open, long low, long high, long time, float tradedVolume, long gameTime) {

    /** T-218: {@link #gameTime} of legacy rows (SQL column default) and when no overworld is available. */
    public static final long NO_GAME_TIME = -1L;

    /**
     * Hand-rolled: {@code StreamCodec.composite} supports at most 6 fields. Field order = wire format.
     * T-218 appended {@link #gameTime} as the 7th field, so master and slaves must run the same version.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPriceStruct> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, MarketPriceStruct p) {
            ByteBufCodecs.SHORT.encode(buf, p.id);
            ByteBufCodecs.VAR_LONG.encode(buf, p.open);
            ByteBufCodecs.VAR_LONG.encode(buf, p.low);
            ByteBufCodecs.VAR_LONG.encode(buf, p.high);
            ByteBufCodecs.VAR_LONG.encode(buf, p.time);
            ByteBufCodecs.FLOAT.encode(buf, p.tradedVolume);
            ByteBufCodecs.VAR_LONG.encode(buf, p.gameTime);
        }

        @Override
        public MarketPriceStruct decode(RegistryFriendlyByteBuf buf) {
            return new MarketPriceStruct(ByteBufCodecs.SHORT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf));
        }
    };

    /**
     * Candle without an in-game start time ({@link #gameTime} = {@link #NO_GAME_TIME}).
     * Kept for callers that only know the wall-clock time (tests, placeholders).
     */
    public MarketPriceStruct(short id, long open, long low, long high, long time, float tradedVolume) {
        this(id, open, low, high, time, tradedVolume, NO_GAME_TIME);
    }
}
