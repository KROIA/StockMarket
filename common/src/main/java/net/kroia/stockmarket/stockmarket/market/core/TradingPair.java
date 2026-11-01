package net.kroia.stockmarket.stockmarket.market.core;

import net.kroia.banksystem.util.ItemID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an ordered pair of items forming a trading pair (e.g. base/quote).
 * <p>
 * Equality and hashing are based on the <b>canonical form</b>, meaning
 * {@code TradingPair(A, B)} and {@code TradingPair(B, A)} are considered equal
 * and will produce the same hash code. This makes TradingPair safe and
 * convenient to use as a HashMap key regardless of item order.
 *
 * @param baseItem  the base item of the pair (the item being "bought")
 * @param quoteItem the quote item of the pair (the item used as "currency")
 */
public record TradingPair(@NotNull ItemID baseItem, @NotNull ItemID quoteItem) {

    // ── NBT keys ────────────────────────────────────────────────────────
    private static final String NBT_BASE_ITEM = "baseItem";
    private static final String NBT_QUOTE_ITEM = "quoteItem";

    // ── StreamCodec for network serialization ───────────────────────────

    /**
     * Network codec that encodes/decodes both ItemIDs using {@link ItemID#STREAM_CODEC}.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, TradingPair> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buf, @NotNull TradingPair pair) {
            ItemID.STREAM_CODEC.encode(buf, pair.baseItem);
            ItemID.STREAM_CODEC.encode(buf, pair.quoteItem);
        }

        @Override
        public @NotNull TradingPair decode(@NotNull RegistryFriendlyByteBuf buf) {
            ItemID base = ItemID.STREAM_CODEC.decode(buf);
            ItemID quote = ItemID.STREAM_CODEC.decode(buf);
            return new TradingPair(base, quote);
        }
    };

    // ── Core methods ────────────────────────────────────────────────────

    /**
     * Returns a new TradingPair with items in a deterministic alphabetical
     * order based on their short value.
     * <p>
     * If {@code baseItem.getShort() > quoteItem.getShort()}, the items are
     * swapped. This guarantees that two pairs representing the same market
     * (regardless of direction) produce the same canonical form.
     *
     * @return a canonically ordered TradingPair
     */
    public TradingPair canonical() {
        if (baseItem.getShort() > quoteItem.getShort()) {
            return new TradingPair(quoteItem, baseItem);
        }
        return this;
    }

    /**
     * Returns a new TradingPair with base and quote swapped.
     *
     * @return the inverted TradingPair
     */
    public TradingPair invert() {
        return new TradingPair(quoteItem, baseItem);
    }

    /**
     * Checks whether this trading pair is valid.
     * A pair is invalid if the base and quote items are the same
     * (you cannot trade an item against itself).
     *
     * @return true if base and quote are different items
     */
    public boolean isValid() {
        return !baseItem.equals(quoteItem);
    }

    // ── Equality based on canonical form ────────────────────────────────

    /**
     * Two TradingPairs are equal if their canonical forms are identical.
     * This means {@code TradingPair(A, B).equals(TradingPair(B, A))} is true.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TradingPair other)) return false;

        TradingPair thisCanonical = this.canonical();
        TradingPair otherCanonical = other.canonical();

        return thisCanonical.baseItem.equals(otherCanonical.baseItem)
                && thisCanonical.quoteItem.equals(otherCanonical.quoteItem);
    }

    /**
     * Hash code based on canonical form so that inverse pairs hash identically.
     */
    @Override
    public int hashCode() {
        TradingPair c = this.canonical();
        int result = 31 * c.baseItem.hashCode() + c.quoteItem.hashCode();
        return result;
    }

    // ── NBT serialization ───────────────────────────────────────────────

    /**
     * Serializes this TradingPair to an NBT CompoundTag.
     * Stores both item IDs as short values under "baseItem" and "quoteItem" keys.
     *
     * @return a CompoundTag containing the pair data
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putShort(NBT_BASE_ITEM, baseItem.getShort());
        tag.putShort(NBT_QUOTE_ITEM, quoteItem.getShort());
        return tag;
    }

    /**
     * Deserializes a TradingPair from an NBT CompoundTag.
     *
     * @param tag the CompoundTag to read from
     * @return the deserialized TradingPair, or null if required keys are missing
     */
    @Nullable
    public static TradingPair fromNBT(@Nullable CompoundTag tag) {
        if (tag == null) return null;
        if (!tag.contains(NBT_BASE_ITEM) || !tag.contains(NBT_QUOTE_ITEM)) {
            return null;
        }
        ItemID base = new ItemID(tag.getShort(NBT_BASE_ITEM));
        ItemID quote = new ItemID(tag.getShort(NBT_QUOTE_ITEM));
        return new TradingPair(base, quote);
    }

    // ── toString ────────────────────────────────────────────────────────

    /**
     * Returns a human-readable string representation of the trading pair.
     */
    @Override
    public String toString() {
        return baseItem.toString() + "/" + quoteItem.toString();
    }
}
