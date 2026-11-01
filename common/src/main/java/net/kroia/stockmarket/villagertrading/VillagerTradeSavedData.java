package net.kroia.stockmarket.villagertrading;

import net.kroia.modutilities.NbtCompat;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Vanilla {@link SavedData} sidecar storing the <b>original (emerald-form)</b>
 * merchant offers of every villager whose trades have been rewritten to the
 * stock-market currency, keyed by villager UUID.
 * <p>
 * <b>Why a sidecar and not {@code custom_data} on the offer stacks:</b>
 * {@code ItemCost.CODEC} does not persist the cached display stack, components
 * placed in the cost predicate would have to be present on the player's payment
 * items ({@code ItemCost.test}), and components on the sell result would leak
 * into player inventories — breaking stacking and BankSystem's
 * {@code MoneyItem.isMoney} (component-sensitive). A plain SavedData file works
 * identically on master and slave servers (slaves have no StockMarket
 * DataManager, but vanilla world storage is always available).
 * <p>
 * Stored in the <b>overworld</b> data storage as the vanilla-global store —
 * villagers from all dimensions are keyed by UUID here.
 * <p>
 * The {@code originals} list of an entry is <b>index-aligned</b> with the
 * villager's live {@code MerchantOffers} (vanilla only appends offers on
 * level-up; restocks mutate uses in place). Offers that are not rewritten keep
 * a {@link OriginalOffer#PASSTHROUGH} marker at their index so alignment is
 * preserved.
 */
public class VillagerTradeSavedData extends SavedData {

    /** File name inside {@code world/data}. */
    public static final String NAME = "stockmarket_villager_trades";

    /** Schema version written to disk for future migrations. */
    private static final int DATA_VERSION = 1;

    /** Entries not seen for ~100 in-game days are pruned (villager gone/discarded). */
    private static final long PRUNE_AGE_GAME_TICKS = 100L * 24000L;

    // DataFixTypes is intentionally null: this is mod data, vanilla datafixers
    // never apply (the vanilla 2-arg convenience constructor is NeoForge-only,
    // so the canonical 3-arg form is used for cross-loader compatibility).
    //private static final SavedData.Factory<VillagerTradeSavedData> FACTORY = new SavedData.Factory<>(VillagerTradeSavedData::new, VillagerTradeSavedData::load, null); // mc<=1.21.4
    // 1.21.5 replaced SavedData.Factory by the codec-based SavedDataType; the codec wraps the same NBT load/save.
    //private static final net.minecraft.world.level.saveddata.SavedDataType<VillagerTradeSavedData> FACTORY = new net.minecraft.world.level.saveddata.SavedDataType<>(NAME, ctx -> new VillagerTradeSavedData(), ctx -> CompoundTag.CODEC.xmap(tag -> load(tag, ctx.levelOrThrow().registryAccess()), data -> data.save(new CompoundTag(), ctx.levelOrThrow().registryAccess())), null); // mc>=1.21.5 mc<=1.21.10
    // 1.21.11 dropped the SavedDataType context; the registries come from the running server instead.
    private static final net.minecraft.world.level.saveddata.SavedDataType<VillagerTradeSavedData> FACTORY = new net.minecraft.world.level.saveddata.SavedDataType<>(NAME, VillagerTradeSavedData::new, CompoundTag.CODEC.xmap(tag -> load(tag, net.kroia.modutilities.UtilitiesPlatform.getRegistryAccessServerSide()), data -> data.save(new CompoundTag(), net.kroia.modutilities.UtilitiesPlatform.getRegistryAccessServerSide())), null); // mc>=1.21.11 mc<=1.21.11
    // 26.1 keys saved data by Identifier and stores it at <world>/data/<namespace>/<path>.dat.
    //private static final net.minecraft.resources.Identifier ID = net.minecraft.resources.Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "villager_trades"); // mc>=26.1
    //private static final net.minecraft.world.level.saveddata.SavedDataType<VillagerTradeSavedData> FACTORY = new net.minecraft.world.level.saveddata.SavedDataType<>(ID, VillagerTradeSavedData::new, CompoundTag.CODEC.xmap(tag -> load(tag, net.kroia.modutilities.UtilitiesPlatform.getRegistryAccessServerSide()), data -> data.save(new CompoundTag(), net.kroia.modutilities.UtilitiesPlatform.getRegistryAccessServerSide())), null); // mc>=26.1

    /** Villager UUID → sidecar entry. */
    private final Map<UUID, VillagerEntry> villagers = new HashMap<>();

    /**
     * Snapshot of a single original offer. Either holds the exact pre-rewrite
     * emerald-form {@link MerchantOffer}, or is the {@link #PASSTHROUGH} marker
     * for offers the rewriter never touches (keeps index alignment with the
     * live offer list).
     * <p>
     * The {@code rewritten} flag tracks whether the corresponding <b>live</b>
     * offer is currently in currency form. It drives the two market-listing
     * transitions: a snapshot whose item gains a market is rewritten
     * (emerald → currency, flag set); a rewritten offer whose market disappears
     * is restored from this snapshot (currency → emerald, flag cleared) while
     * the snapshot itself is kept — so a later re-listing converts it again.
     */
    public static final class OriginalOffer {
        /** Marker instance for offers that are never rewritten. */
        public static final OriginalOffer PASSTHROUGH = new OriginalOffer(null, false);

        private final @Nullable MerchantOffer offer;
        /** Whether the live offer at this index is currently in currency form. */
        private boolean rewritten;

        public OriginalOffer(@Nullable MerchantOffer offer, boolean rewritten) {
            this.offer = offer;
            this.rewritten = rewritten;
        }

        /** @return {@code true} when this is a pass-through marker (no stored offer). */
        public boolean isPassthrough() {
            return offer == null;
        }

        /** @return the stored original offer, or {@code null} for pass-through markers. */
        public @Nullable MerchantOffer offer() {
            return offer;
        }

        /** @return {@code true} when the live offer at this index is currently in currency form. */
        public boolean isRewritten() {
            return rewritten;
        }

        /** Records whether the live offer at this index is currently in currency form. */
        public void setRewritten(boolean rewritten) {
            this.rewritten = rewritten;
        }
    }

    /**
     * Per-villager sidecar entry: the original offers plus bookkeeping for
     * staleness checks, pruning and one-time clamp warnings.
     * <p>
     * Public with a public constructor so test suites can drive the rewriter's
     * pure processing functions with synthetic entries.
     */
    public static final class VillagerEntry {
        private long lastSeenGameTime;
        private long pricedVersion;
        /** Bitmask of offer indices whose currency fit was capacity-clamped (warn-once). */
        private long clampedMask;
        private final List<OriginalOffer> originals = new ArrayList<>();

        public VillagerEntry() {
        }

        /** @return the price-table version the current rewrite is based on. */
        public long pricedVersion() {
            return pricedVersion;
        }

        /** Records the price-table version used for the current rewrite. */
        public void setPricedVersion(long version) {
            this.pricedVersion = version;
        }

        /** @return the overworld game time of the last interaction (used for pruning). */
        public long lastSeenGameTime() {
            return lastSeenGameTime;
        }

        /** Updates the last-interaction game time (used for pruning). */
        public void setLastSeenGameTime(long gameTime) {
            this.lastSeenGameTime = gameTime;
        }

        /** @return {@code true} when the clamp warning for the given offer index already fired. */
        public boolean isClampFlagged(int offerIndex) {
            return offerIndex >= 0 && offerIndex < 64 && (clampedMask & (1L << offerIndex)) != 0;
        }

        /** Marks the clamp warning for the given offer index as fired. */
        public void flagClamped(int offerIndex) {
            if (offerIndex >= 0 && offerIndex < 64) {
                clampedMask |= (1L << offerIndex);
            }
        }

        /** @return the number of snapshot entries (index-aligned with the live offers). */
        public int size() {
            return originals.size();
        }

        /** @return the snapshot at the given offer index. */
        public OriginalOffer get(int index) {
            return originals.get(index);
        }

        /** Appends a snapshot for a newly discovered tail offer (villager level-up). */
        public void append(OriginalOffer original) {
            originals.add(original);
        }

        /**
         * Truncates the snapshot list when the live offer list shrank (another mod
         * removed offers) so index alignment is restored.
         *
         * @param newSize the live offer count to align to
         */
        public void truncate(int newSize) {
            while (originals.size() > newSize) {
                originals.remove(originals.size() - 1);
            }
        }
    }

    /**
     * Gets (or creates) the sidecar for the given server. Uses the overworld's
     * data storage as the global store.
     *
     * @param server the running server (master or slave)
     * @return the sidecar instance
     */
    public static VillagerTradeSavedData get(MinecraftServer server) {
        //return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME); // mc<=1.21.4
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY); // mc>=1.21.5 mc<=1.21.11
        //migrateLegacyFile(server); // mc>=26.1
        // 26.1 split the overworld storage off into dimensions/; the server-wide storage is <world>/data.
        //return server.getDataStorage().computeIfAbsent(FACTORY); // mc>=26.1
    }

    /** Server the legacy-file migration last ran for, so it runs once per server start. */
    //private static MinecraftServer migratedServer; // mc>=26.1

    /**
     * Moves the pre-26.1 sidecar file to its 26.1 location before the first load.
     * <p>
     * Up to 1.21.11 the file was {@code <world>/data/stockmarket_villager_trades.dat}. 26.1 stores
     * {@link #ID} at {@code <world>/data/stockmarket/villager_trades.dat}, and vanilla's world upgrade
     * ({@code DimensionStorageFileFix}) only moves its own named files, so without this the villager
     * trade state of an upgraded world would be silently reset. Also picks up the file from the
     * {@code minecraft} namespace paths an early 26.1 build used. Only moves if the new file is
     * missing; never deletes the source on failure.
     *
     * @param server the running server whose world is migrated
     */
    //private static void migrateLegacyFile(MinecraftServer server) { // mc>=26.1
    //    if (migratedServer == server) return; // mc>=26.1
    //    migratedServer = server; // mc>=26.1
    //    java.nio.file.Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT); // mc>=26.1
    //    java.nio.file.Path target = ID.withSuffix(".dat").resolveAgainst(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATA)); // mc>=26.1
    //    if (java.nio.file.Files.exists(target)) return; // mc>=26.1
    //    for (String legacy : new String[]{"data/" + NAME + ".dat", "data/minecraft/" + NAME + ".dat", "dimensions/minecraft/overworld/data/minecraft/" + NAME + ".dat"}) { // mc>=26.1
    //        java.nio.file.Path source = world.resolve(legacy); // mc>=26.1
    //        if (!java.nio.file.Files.isRegularFile(source)) continue; // mc>=26.1
    //        try { // mc>=26.1
    //            java.nio.file.Files.createDirectories(target.getParent()); // mc>=26.1
    //            java.nio.file.Files.move(source, target); // mc>=26.1
    //            StockMarketMod.LOGGER.info("Migrated villager trade data from {} to {}", source, target); // mc>=26.1
    //        } catch (java.io.IOException e) { // mc>=26.1
    //            StockMarketMod.LOGGER.warn("Could not migrate villager trade data from {} to {}; the old file is kept", source, target, e); // mc>=26.1
    //        } // mc>=26.1
    //        return; // mc>=26.1
    //    } // mc>=26.1
    //} // mc>=26.1

    /**
     * Returns the entry for the given villager UUID.
     *
     * @param villagerUUID the villager's entity UUID
     * @return the entry, or {@code null} if the villager was never rewritten
     */
    public @Nullable VillagerEntry getEntry(UUID villagerUUID) {
        return villagers.get(villagerUUID);
    }

    /**
     * Returns the entry for the given villager, creating an empty one if absent.
     *
     * @param villagerUUID the villager's entity UUID
     * @param nowGameTime  the current overworld game time (initial last-seen stamp)
     * @return the (possibly new) entry
     */
    public VillagerEntry getOrCreateEntry(UUID villagerUUID, long nowGameTime) {
        return villagers.computeIfAbsent(villagerUUID, uuid -> {
            VillagerEntry entry = new VillagerEntry();
            entry.setLastSeenGameTime(nowGameTime);
            setDirty();
            return entry;
        });
    }

    /**
     * Removes the entry for the given villager (called after its offers were
     * restored to emerald form).
     *
     * @param villagerUUID the villager's entity UUID
     */
    public void removeEntry(UUID villagerUUID) {
        if (villagers.remove(villagerUUID) != null) {
            setDirty();
        }
    }

    /**
     * Drops entries that have not been interacted with for
     * {@link #PRUNE_AGE_GAME_TICKS} (~100 in-game days). Orphaned villagers keep
     * their currency-form offers — the rewriter's skip rules leave unknown
     * currency offers untouched (documented tradeoff: they are no longer
     * repriced or restorable).
     *
     * @param nowGameTime the current overworld game time
     */
    public void pruneStale(long nowGameTime) {
        boolean removed = false;
        Iterator<Map.Entry<UUID, VillagerEntry>> it = villagers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, VillagerEntry> entry = it.next();
            if (nowGameTime - entry.getValue().lastSeenGameTime() > PRUNE_AGE_GAME_TICKS) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            setDirty();
        }
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    /**
     * Deserializes the sidecar from NBT. Offers that fail to decode (e.g. items
     * from removed mods) degrade to pass-through markers so index alignment is
     * preserved; those offers simply become unrestorable.
     *
     * @param tag        the root NBT tag
     * @param registries registry context required by {@code MerchantOffer.CODEC}
     * @return the loaded sidecar
     */
    public static VillagerTradeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillagerTradeSavedData data = new VillagerTradeSavedData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);

        ListTag villagerList = NbtCompat.getList(tag, "villagers", Tag.TAG_COMPOUND);
        for (int i = 0; i < villagerList.size(); i++) {
            CompoundTag villagerTag = NbtCompat.getCompound(villagerList, i);
            if (!NbtCompat.hasUUID(villagerTag, "uuid")) {
                continue;
            }
            VillagerEntry entry = new VillagerEntry();
            entry.lastSeenGameTime = NbtCompat.getLong(villagerTag, "lastSeenGameTime");
            entry.pricedVersion = NbtCompat.getLong(villagerTag, "pricedVersion");
            entry.clampedMask = NbtCompat.getLong(villagerTag, "clampedMask");

            ListTag originalsList = NbtCompat.getList(villagerTag, "originals", Tag.TAG_COMPOUND);
            for (int j = 0; j < originalsList.size(); j++) {
                CompoundTag originalTag = NbtCompat.getCompound(originalsList, j);
                if (NbtCompat.getBoolean(originalTag, "passthrough") || !originalTag.contains("offer")) {
                    entry.originals.add(OriginalOffer.PASSTHROUGH);
                    continue;
                }
                Optional<MerchantOffer> offer = MerchantOffer.CODEC
                        .parse(ops, originalTag.get("offer"))
                        .resultOrPartial(error -> StockMarketMod.LOGGER.warn(
                                "[VillagerTradeSavedData] Failed to decode original offer: {}", error));
                // Missing flag (data written before the flag existed) defaults to
                // true: back then every stored snapshot was immediately rewritten,
                // so the live offer is in currency form and must be restorable.
                boolean rewritten = !originalTag.contains("rewritten") || NbtCompat.getBoolean(originalTag, "rewritten");
                // Decode failure degrades to pass-through to keep index alignment.
                entry.originals.add(offer
                        .map(o -> new OriginalOffer(o, rewritten))
                        .orElse(OriginalOffer.PASSTHROUGH));
            }
            data.villagers.put(NbtCompat.getUUID(villagerTag, "uuid"), entry);
        }
        return data;
    }

    //@Override // mc<=1.21.4
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        tag.putInt("version", DATA_VERSION);

        ListTag villagerList = new ListTag();
        for (Map.Entry<UUID, VillagerEntry> mapEntry : villagers.entrySet()) {
            VillagerEntry entry = mapEntry.getValue();
            CompoundTag villagerTag = new CompoundTag();
            NbtCompat.putUUID(villagerTag, "uuid", mapEntry.getKey());
            villagerTag.putLong("lastSeenGameTime", entry.lastSeenGameTime);
            villagerTag.putLong("pricedVersion", entry.pricedVersion);
            villagerTag.putLong("clampedMask", entry.clampedMask);

            ListTag originalsList = new ListTag();
            for (OriginalOffer original : entry.originals) {
                CompoundTag originalTag = new CompoundTag();
                if (original.isPassthrough()) {
                    originalTag.putBoolean("passthrough", true);
                } else {
                    Optional<Tag> encoded = MerchantOffer.CODEC
                            .encodeStart(ops, original.offer())
                            .resultOrPartial(error -> StockMarketMod.LOGGER.warn(
                                    "[VillagerTradeSavedData] Failed to encode original offer: {}", error));
                    if (encoded.isPresent()) {
                        originalTag.put("offer", encoded.get());
                        originalTag.putBoolean("rewritten", original.isRewritten());
                    } else {
                        // Encode failure degrades to pass-through to keep alignment.
                        originalTag.putBoolean("passthrough", true);
                    }
                }
                originalsList.add(originalTag);
            }
            villagerTag.put("originals", originalsList);
            villagerList.add(villagerTag);
        }
        tag.put("villagers", villagerList);
        return tag;
    }
}
