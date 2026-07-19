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
            ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).encode(buf, prefs.lastPairHaveMarketID);
            ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).encode(buf, prefs.lastPairWantMarketID);
            ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).encode(buf, prefs.favoriteMarketIDs);
            buf.writeLong(prefs.orderHistoryClearedBeforeMs);
            buf.writeBoolean(prefs.newsToastEnabled);
            // T-109: unconditional append (backward-compatible codec — mirrors T-074's
            // toast-flag append; codec is used inside list codecs, no readable-guard).
            buf.writeLong(prefs.newsClearedBeforeMs);
            // T-131: last bank account selected for trading (-1 = none/personal).
            // Unconditional append, mirroring the fields above (client and server
            // agree on this ordering — see the matching decode below).
            buf.writeInt(prefs.lastTradingBankAccountNr);
        }

        @Override
        public PlayerPreferences decode(RegistryFriendlyByteBuf buf) {
            PlayerPreferences prefs = new PlayerPreferences();
            prefs.lastMarketID = ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).decode(buf);
            prefs.lastPairHaveMarketID = ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).decode(buf);
            prefs.lastPairWantMarketID = ExtraCodecUtils.nullable(ItemID.STREAM_CODEC).decode(buf);
            prefs.favoriteMarketIDs = new ArrayList<>(ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).decode(buf));
            prefs.orderHistoryClearedBeforeMs = buf.readLong();
            prefs.newsToastEnabled = buf.readBoolean();
            prefs.newsClearedBeforeMs = buf.readLong();
            // T-131: decode the last trading bank account (matches the encode order).
            prefs.lastTradingBankAccountNr = buf.readInt();
            return prefs;
        }
    };

    /** The last market the player had selected in the trade screen (null if never opened). */
    @Nullable
    private ItemID lastMarketID;

    /** The last "I HAVE" pair selection in the trade screen (null if never selected). */
    @Nullable
    private ItemID lastPairHaveMarketID;

    /** The last "I WANT" pair selection in the trade screen (null if never selected). */
    @Nullable
    private ItemID lastPairWantMarketID;

    /** Ordered list of favorite market IDs, in the order they were added. */
    private List<ItemID> favoriteMarketIDs;

    /** Epoch-millis timestamp; order history records older than this are hidden from the player's personal view. */
    private long orderHistoryClearedBeforeMs = 0;

    /**
     * News toast opt-in (T-074): when true, the player gets a headline toast popup
     * whenever a news event publishes. <b>Default is false (off)</b> — the opt-in
     * checkbox lives in the newspaper screen, and players who never touched it must
     * receive no notification at all (user decision: no chat message, opt-in toast
     * is the only push notification).
     */
    private boolean newsToastEnabled = false;

    /**
     * Timestamp (epoch ms) before which this player has soft-cleared the newspaper (T-109).
     * Records with {@code timestampEpochMs > newsClearedBeforeMs} are visible; older
     * records are filtered out of the newspaper UI and toast catch-up. Value {@code 0}
     * = never cleared. Underlying {@link net.kroia.stockmarket.news.NewsRecord}s on the
     * server are never touched — this is a purely client-visible per-player filter
     * (admins see all records).
     */
    private long newsClearedBeforeMs = 0;

    /**
     * The bank account number last selected for trading in the trade screen (T-131).
     * {@code -1} means "not set" — the client then falls back to the player's personal
     * account. Validated on use (the account may no longer exist or be owned), so a
     * stale value simply reverts to the personal account.
     */
    private int lastTradingBankAccountNr = -1;

    /**
     * Creates empty default preferences (no last market, no favorites, no clear timestamp,
     * news toasts off, news never cleared, no trading account selected).
     */
    public PlayerPreferences() {
        this.lastMarketID = null;
        this.lastPairHaveMarketID = null;
        this.lastPairWantMarketID = null;
        this.favoriteMarketIDs = new ArrayList<>();
        this.orderHistoryClearedBeforeMs = 0;
        this.newsToastEnabled = false;
        this.newsClearedBeforeMs = 0;
        this.lastTradingBankAccountNr = -1;
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
     * @return the last selected "I HAVE" pair market ID, or null if never selected
     */
    public @Nullable ItemID getLastPairHaveMarketID() {
        return lastPairHaveMarketID;
    }

    /**
     * Sets the last selected "I HAVE" pair market ID.
     * @param id the market ID, or null to clear
     */
    public void setLastPairHaveMarketID(@Nullable ItemID id) {
        this.lastPairHaveMarketID = id;
    }

    /**
     * @return the last selected "I WANT" pair market ID, or null if never selected
     */
    public @Nullable ItemID getLastPairWantMarketID() {
        return lastPairWantMarketID;
    }

    /**
     * Sets the last selected "I WANT" pair market ID.
     * @param id the market ID, or null to clear
     */
    public void setLastPairWantMarketID(@Nullable ItemID id) {
        this.lastPairWantMarketID = id;
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

    /**
     * @return epoch-millis timestamp before which order history records are hidden, or 0 if never cleared
     */
    public long getOrderHistoryClearedBeforeMs() {
        return orderHistoryClearedBeforeMs;
    }

    /**
     * Sets the epoch-millis timestamp before which order history records should be hidden.
     * @param timestamp epoch millis, or 0 to show all records
     */
    public void setOrderHistoryClearedBeforeMs(long timestamp) {
        this.orderHistoryClearedBeforeMs = timestamp;
    }

    /**
     * @return true if the player opted in to news toast popups (default false — off)
     */
    public boolean isNewsToastEnabled() {
        return newsToastEnabled;
    }

    /**
     * Sets the news toast opt-in flag (checkbox in the newspaper screen).
     * @param newsToastEnabled true to show a headline toast on every news publish
     */
    public void setNewsToastEnabled(boolean newsToastEnabled) {
        this.newsToastEnabled = newsToastEnabled;
    }

    /**
     * @return epoch-millis timestamp before which news records are filtered from this
     *         player's view (newspaper feed + join-time toast catch-up), or {@code 0}
     *         if the player has never cleared their newspaper (T-109)
     */
    public long getNewsClearedBeforeMs() {
        return newsClearedBeforeMs;
    }

    /**
     * Sets the soft-clear timestamp used by the newspaper filter (T-109).
     * Server-side {@link net.kroia.stockmarket.news.NewsRecord}s are never modified;
     * this only shifts the per-player visibility cutoff.
     * @param timestamp epoch millis, or {@code 0} to un-clear (show everything again)
     */
    public void setNewsClearedBeforeMs(long timestamp) {
        this.newsClearedBeforeMs = timestamp;
    }

    /**
     * @return the bank account number last selected for trading, or {@code -1} if none
     *         has been chosen (in which case the personal account is used) (T-131)
     */
    public int getLastTradingBankAccountNr() {
        return lastTradingBankAccountNr;
    }

    /**
     * Sets the bank account number to remember as the trading account (T-131).
     * @param accountNr the account number, or {@code -1} to clear (fall back to personal)
     */
    public void setLastTradingBankAccountNr(int accountNr) {
        this.lastTradingBankAccountNr = accountNr;
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

        // Save last pair "I HAVE" selection
        tag.putBoolean("hasLastPairHave", lastPairHaveMarketID != null);
        if (lastPairHaveMarketID != null) {
            CompoundTag t = new CompoundTag();
            lastPairHaveMarketID.save(t);
            tag.put("lastPairHave", t);
        }

        // Save last pair "I WANT" selection
        tag.putBoolean("hasLastPairWant", lastPairWantMarketID != null);
        if (lastPairWantMarketID != null) {
            CompoundTag t = new CompoundTag();
            lastPairWantMarketID.save(t);
            tag.put("lastPairWant", t);
        }

        // Save favorites as a ListTag of CompoundTags
        ListTag favTag = new ListTag();
        for (ItemID id : favoriteMarketIDs) {
            CompoundTag t = new CompoundTag();
            id.save(t);
            favTag.add(t);
        }
        tag.put("favorites", favTag);

        // Save order history clear timestamp
        tag.putLong("orderHistoryClearedBeforeMs", orderHistoryClearedBeforeMs);

        // Save news toast opt-in flag
        tag.putBoolean("newsToastEnabled", newsToastEnabled);

        // T-109: save the newspaper soft-clear cutoff (0 = never cleared).
        tag.putLong("newsClearedBeforeMs", newsClearedBeforeMs);

        // T-131: save the last trading bank account number (-1 = none/personal).
        tag.putInt("lastTradingBankAccountNr", lastTradingBankAccountNr);
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

        // Load last pair "I HAVE" selection
        if (tag.getBoolean("hasLastPairHave") && tag.contains("lastPairHave")) {
            lastPairHaveMarketID = ItemID.createFromTag(tag.getCompound("lastPairHave"));
            if (lastPairHaveMarketID != null && !lastPairHaveMarketID.isValid()) lastPairHaveMarketID = null;
        } else {
            lastPairHaveMarketID = null;
        }

        // Load last pair "I WANT" selection
        if (tag.getBoolean("hasLastPairWant") && tag.contains("lastPairWant")) {
            lastPairWantMarketID = ItemID.createFromTag(tag.getCompound("lastPairWant"));
            if (lastPairWantMarketID != null && !lastPairWantMarketID.isValid()) lastPairWantMarketID = null;
        } else {
            lastPairWantMarketID = null;
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

        // Load order history clear timestamp (getLong returns 0 if key missing — backward compatible)
        orderHistoryClearedBeforeMs = tag.getLong("orderHistoryClearedBeforeMs");

        // Load news toast opt-in flag (getBoolean returns false if key missing —
        // backward compatible AND guarantees the default-off contract for old saves)
        newsToastEnabled = tag.getBoolean("newsToastEnabled");

        // T-109: load the newspaper soft-clear cutoff (getLong returns 0 if key missing
        // — backward compatible; 0 = never cleared, so old saves show everything).
        newsClearedBeforeMs = tag.getLong("newsClearedBeforeMs");

        // T-131: load the last trading bank account number. Default to -1 (not 0,
        // which getInt would return for a missing key) so old saves fall back to
        // the personal account instead of accidentally targeting account 0.
        lastTradingBankAccountNr = tag.contains("lastTradingBankAccountNr")
                ? tag.getInt("lastTradingBankAccountNr") : -1;
        return true;
    }
}
