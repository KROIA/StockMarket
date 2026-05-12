package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores per-player trading preferences such as favorite markets and last-selected market.
 * Persisted via NBT (ServerSaveable) and synced to clients via STREAM_CODEC.
 */
public class PlayerPreferences implements ServerSaveable {

    /**
     * StreamCodec for network serialization of PlayerPreferences.
     * Uses nullable codec for lastMarketID and list codec for favoriteMarketIDs.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPreferences> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerPreferences prefs) {
            ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).encode(buf, prefs.lastMarketID);
            ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).encode(buf, prefs.favoriteMarketIDs);
        }

        @Override
        public PlayerPreferences decode(RegistryFriendlyByteBuf buf) {
            PlayerPreferences prefs = new PlayerPreferences();
            prefs.lastMarketID = ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).decode(buf);
            prefs.favoriteMarketIDs = new ArrayList<>(ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).decode(buf));
            return prefs;
        }
    };

    /** The last market the player had selected in the trade screen (null if never opened). */
    @Nullable
    private ItemID lastMarketID;

    /** Ordered list of favorite market IDs, in the order they were added. */
    private List<ItemID> favoriteMarketIDs;

    /**
     * Creates empty default preferences (no last market, no favorites).
     */
    public PlayerPreferences() {
        this.lastMarketID = null;
        this.favoriteMarketIDs = new ArrayList<>();
    }

    // --- Getters / Setters ---

    /**
     * @return the last selected market ID, or null if the player has never selected a market
     */
    public @Nullable ItemID getLastMarketID() {
        return lastMarketID;
    }

    /**
     * Sets the last selected market ID.
     * @param lastMarketID the market ID, or null to clear
     */
    public void setLastMarketID(@Nullable ItemID lastMarketID) {
        this.lastMarketID = lastMarketID;
    }

    /**
     * @return unmodifiable view of the favorite market IDs, in the order they were added
     */
    public List<ItemID> getFavoriteMarketIDs() {
        return favoriteMarketIDs;
    }

    /**
     * Replaces the entire favorites list.
     * @param favoriteMarketIDs the new favorites list
     */
    public void setFavoriteMarketIDs(List<ItemID> favoriteMarketIDs) {
        this.favoriteMarketIDs = new ArrayList<>(favoriteMarketIDs);
    }

    // --- Favorite management ---

    /**
     * Appends a market to the end of the favorites list.
     * If already present, the list is not modified (no reordering).
     * @param marketID the market to favorite
     */
    public void addFavorite(ItemID marketID) {
        if (!favoriteMarketIDs.contains(marketID)) {
            favoriteMarketIDs.add(marketID);
        }
    }

    /**
     * Removes a market from the favorites list.
     * @param marketID the market to unfavorite
     */
    public void removeFavorite(ItemID marketID) {
        favoriteMarketIDs.remove(marketID);
    }

    /**
     * Checks whether a market is in the favorites list.
     * @param marketID the market to check
     * @return true if the market is a favorite
     */
    public boolean isFavorite(ItemID marketID) {
        return favoriteMarketIDs.contains(marketID);
    }

    // --- NBT persistence (ServerSaveable) ---

    @Override
    public boolean save(CompoundTag tag) {
        // Save last market as a sub-tag (ItemID writes "itemID" key inside the tag)
        tag.putBoolean("hasLastMarket", lastMarketID != null);
        if (lastMarketID != null) {
            CompoundTag lastMarketTag = new CompoundTag();
            lastMarketID.save(lastMarketTag);
            tag.put("lastMarket", lastMarketTag);
        }

        // Save favorites as a ListTag of CompoundTags
        ListTag favTag = new ListTag();
        for (ItemID id : favoriteMarketIDs) {
            CompoundTag t = new CompoundTag();
            id.save(t);
            favTag.add(t);
        }
        tag.put("favorites", favTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        // Load last market
        if (tag.getBoolean("hasLastMarket") && tag.contains("lastMarket")) {
            CompoundTag lastMarketTag = tag.getCompound("lastMarket");
            lastMarketID = ItemID.createFromTag(lastMarketTag);
            if (lastMarketID != null && !lastMarketID.isValid()) {
                lastMarketID = null;
            }
        } else {
            lastMarketID = null;
        }

        // Load favorites
        favoriteMarketIDs = new ArrayList<>();
        if (tag.contains("favorites")) {
            ListTag favTag = tag.getList("favorites", 10); // 10 = CompoundTag type
            for (int i = 0; i < favTag.size(); i++) {
                CompoundTag t = favTag.getCompound(i);
                ItemID id = ItemID.createFromTag(t);
                if (id != null && id.isValid()) {
                    favoriteMarketIDs.add(id);
                }
            }
        }
        return true;
    }
}
