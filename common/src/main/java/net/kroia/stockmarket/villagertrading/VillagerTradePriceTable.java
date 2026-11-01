package net.kroia.stockmarket.villagertrading;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of everything a server needs to reprice villager trades:
 * the feature toggle, the trading-currency item, the pricing margins and the
 * current market prices keyed by BankSystem ItemID short.
 * <p>
 * Only items with a listed market price are ever repriced — offers whose traded
 * item has no market keep (or return to) their vanilla emerald form, so this
 * table intentionally carries no emerald conversion rate.
 * <p>
 * The master server computes instances of this table from its live markets and
 * settings ({@link VillagerTradeManager#recomputeTable()}) and broadcasts them
 * to all slave servers via
 * {@link net.kroia.stockmarket.networking.packet.VillagerTradePriceTablePacket}.
 * Slaves never read {@code settings.json} — this table is their only source of
 * truth for the feature, which keeps the master authoritative.
 * <p>
 * All price values are in <b>raw units</b> (100 raw = 1.00 display units,
 * matching BankSystem's {@code ITEM_FRACTION_SCALE_FACTOR}).
 *
 * @param version               monotonically increasing table version (wall-clock ms at
 *                              computation time); used by the rewriter as a staleness check
 * @param enabled               whether villager trade repricing is active (already includes
 *                              the "currency resolvable" check done by the master)
 * @param currency              the trading-currency item template (count ignored); may be
 *                              {@link ItemStack#EMPTY} when unresolvable (then {@code enabled}
 *                              is always {@code false})
 * @param buyMargin             margin on trades where the villager buys from the player
 * @param sellMargin            margin on trades where the villager sells to the player
 * @param pricesRawByItemIdShort current market price (raw) per ItemID short; only markets
 *                              with a price &gt; 0 are included
 */
public record VillagerTradePriceTable(
        long version,
        boolean enabled,
        ItemStack currency,
        float buyMargin,
        float sellMargin,
        Map<Short, Long> pricesRawByItemIdShort)
{
    public VillagerTradePriceTable {
        // Defensive copy → the record is truly immutable and safe to share across
        // threads (the slave receives it on a network thread and swaps it into a
        // volatile field read by the server main thread).
        pricesRawByItemIdShort = Map.copyOf(pricesRawByItemIdShort);
    }

    /**
     * Returns the market price (raw units) for the given ItemID short.
     *
     * @param itemIdShort the BankSystem ItemID short of the item
     * @return the raw market price, or {@code 0} if the item has no (valid) market
     */
    public long getPriceRaw(short itemIdShort) {
        Long price = pricesRawByItemIdShort.get(itemIdShort);
        return price != null ? price : 0L;
    }

    /**
     * Wire codec for the master→slave broadcast. Written manually because of
     * the price map. {@link ItemStack#OPTIONAL_STREAM_CODEC} is used for the
     * currency so an empty stack (unresolvable currency, feature
     * force-disabled) still encodes.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradePriceTable> STREAM_CODEC = StreamCodec.of(
            (buf, table) -> {
                buf.writeLong(table.version);
                buf.writeBoolean(table.enabled);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, table.currency);
                buf.writeFloat(table.buyMargin);
                buf.writeFloat(table.sellMargin);
                buf.writeVarInt(table.pricesRawByItemIdShort.size());
                for (Map.Entry<Short, Long> entry : table.pricesRawByItemIdShort.entrySet()) {
                    buf.writeShort(entry.getKey());
                    buf.writeLong(entry.getValue());
                }
            },
            buf -> {
                long version = buf.readLong();
                boolean enabled = buf.readBoolean();
                ItemStack currency = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                float buyMargin = buf.readFloat();
                float sellMargin = buf.readFloat();
                int size = buf.readVarInt();
                Map<Short, Long> prices = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    short key = buf.readShort();
                    long value = buf.readLong();
                    prices.put(key, value);
                }
                return new VillagerTradePriceTable(version, enabled, currency, buyMargin, sellMargin, prices);
            }
    );
}
