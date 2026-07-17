package net.kroia.stockmarket.news;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One published news entry in the history (NewsEventSystem plan §3).
 * <p>
 * A record is created by the NewsPlugin at <b>publish time</b> (not impact start —
 * see {@code announceDelayMs} semantics) and is the unit that travels to clients:
 * it is persisted as NBT in the master's history file and sent over the network via
 * {@link #STREAM_CODEC} (both live broadcast and paginated history responses).
 * <p>
 * Design notes:
 * <ul>
 *   <li>{@code headline}/{@code text} carry the <b>full inline translation maps</b>
 *       (language code → text). The client resolves its language at render time with
 *       the fallback chain <i>exact client language → {@code en_us} → first map entry</i>,
 *       so map insertion order is preserved through NBT and network round-trips.</li>
 *   <li>Each {@link AffectedMarket} stores both the {@link ItemID} (icon lookup) <b>and</b>
 *       the item registry name string, so history entries still render after the market
 *       (or its item's mod) was deleted. The name string — not the short id — is the
 *       portable identity.</li>
 *   <li>The impact summary fields ({@code impactType}, {@code peakFactor}, {@code reversal},
 *       {@code totalDurationSeconds}) are snapshots of the <b>resolved</b> impact so the
 *       UI can render direction/LIVE state even if the definition changed or vanished
 *       after a reload.</li>
 * </ul>
 */
public class NewsRecord implements ServerSaveable {

    // ── NBT keys ─────────────────────────────────────────────────────────

    private static final String KEY_NEWS_UID = "newsUid";
    private static final String KEY_EVENT_ID = "eventId";
    private static final String KEY_TIMESTAMP = "timestampEpochMs";
    private static final String KEY_GAME_DAY = "gameDay";
    private static final String KEY_HEADLINE = "headline";
    private static final String KEY_TEXT = "text";
    private static final String KEY_AFFECTED_MARKETS = "affectedMarkets";
    private static final String KEY_IMPACT_TYPE = "impactType";
    private static final String KEY_PEAK_FACTOR = "peakFactor";
    private static final String KEY_REVERSAL = "reversal";
    private static final String KEY_TOTAL_DURATION = "totalDurationSeconds";
    private static final String KEY_PICTURE_HASH = "pictureHash";
    private static final String KEY_SEQUENCE_NAME = "sequenceName";
    private static final String KEY_STEP_COUNT = "stepCount";
    // Translation maps are stored as ListTag of {lang, text} pairs instead of a
    // CompoundTag because CompoundTag does not preserve insertion order — and the
    // client-side "first map entry" language fallback depends on it.
    private static final String KEY_LANG = "lang";
    private static final String KEY_VALUE = "value";

    /**
     * One market actually impacted by the published event (the matched∩subscribed subset).
     *
     * @param marketId     the market's ItemID (used for icon lookup via
     *                     {@code ItemIDManager.getItemStackTemplate}); may be stale after
     *                     market deletion — always fall back to {@code itemName} for display
     * @param itemName     the item registry name (e.g. {@code "minecraft:diamond"}) captured
     *                     at publish time, so history renders after market deletion
     * @param weightFactor the effective per-market impact scale (negative = inverted impact)
     */
    public record AffectedMarket(ItemID marketId, String itemName, float weightFactor) {

        private static final String KEY_MARKET_ID = "marketId";
        private static final String KEY_ITEM_NAME = "itemName";
        private static final String KEY_WEIGHT_FACTOR = "weightFactor";

        /** Network codec (mirrors the manual STREAM_CODEC pattern used across the mod). */
        public static final StreamCodec<RegistryFriendlyByteBuf, AffectedMarket> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, AffectedMarket val) {
                ItemID.STREAM_CODEC.encode(buf, val.marketId());
                ByteBufCodecs.STRING_UTF8.encode(buf, val.itemName());
                ByteBufCodecs.FLOAT.encode(buf, val.weightFactor());
            }

            @Override
            public AffectedMarket decode(RegistryFriendlyByteBuf buf) {
                ItemID marketId = ItemID.STREAM_CODEC.decode(buf);
                String itemName = ByteBufCodecs.STRING_UTF8.decode(buf);
                float weightFactor = ByteBufCodecs.FLOAT.decode(buf);
                return new AffectedMarket(marketId, itemName, weightFactor);
            }
        };

        /** @return this affected market serialized into a new CompoundTag */
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            CompoundTag idTag = new CompoundTag();
            marketId.save(idTag);
            tag.put(KEY_MARKET_ID, idTag);
            tag.putString(KEY_ITEM_NAME, itemName);
            tag.putFloat(KEY_WEIGHT_FACTOR, weightFactor);
            return tag;
        }

        /** @return the affected market read from the tag, or null if the tag is malformed */
        static @Nullable AffectedMarket fromTag(CompoundTag tag) {
            if (!tag.contains(KEY_MARKET_ID) || !tag.contains(KEY_ITEM_NAME)) return null;
            ItemID marketId = ItemID.createFromTag(tag.getCompound(KEY_MARKET_ID));
            return new AffectedMarket(marketId, tag.getString(KEY_ITEM_NAME),
                    tag.getFloat(KEY_WEIGHT_FACTOR));
        }
    }

    // ── Fields (non-final to support the ServerSaveable load(tag) pattern) ──

    private long newsUid;
    private String eventId = "";
    private long timestampEpochMs;
    private long gameDay;
    private Map<String, String> headline = new LinkedHashMap<>();
    private Map<String, String> text = new LinkedHashMap<>();
    private List<AffectedMarket> affectedMarkets = new ArrayList<>();
    private String impactType = "";
    private float peakFactor;
    private String reversal = "";
    private int totalDurationSeconds;

    /**
     * Optional 20-byte SHA-1 of the record's newspaper picture (picture plan §4.1);
     * null = text-only record (the pre-picture default). This is <b>identity only</b> —
     * the PNG bytes never travel inside the record: they are snapshotted into the
     * master's content-addressed {@link NewsPictureStore} at publish time and fetched
     * lazily by clients (T-089/T-090).
     */
    private byte @Nullable [] pictureHash;

    /**
     * Name of the sequence the published run played (sequences plan §5, T-099) —
     * <b>empty for legacy {@code impact}-authored events</b> (their implicit "impact"
     * sequence is an internal normalization detail, not authored content) and for
     * records written before T-099. Non-empty only for {@code sequences[]}-authored
     * events, where it names the sequence the activation-time weighted pick chose.
     */
    private String sequenceName = "";

    /**
     * Number of steps of the published run's resolved sequence (T-099); {@code 0} for
     * records written before T-099 (unknown). Legacy events report their implicit
     * 3-step normalization ({@code ramp}/{@code hold}/{@code reversal|permanent}).
     */
    private int stepCount;

    /** Empty record for the {@code load(tag)} / codec-decode paths. */
    public NewsRecord() {
    }

    /**
     * Full constructor used by the NewsPlugin at publish time.
     *
     * @param newsUid              monotonic unique id of this record (pagination key)
     * @param eventId              the definition id this record was published from
     * @param timestampEpochMs     publish time as master epoch milliseconds (not impact start)
     * @param gameDay              the world's game day at publish time ("Day N" display)
     * @param headline             full language → headline map (insertion order preserved)
     * @param text                 full language → newspaper-text map (insertion order preserved)
     * @param affectedMarkets      the actually-impacted (matched∩subscribed) market subset
     * @param impactType           resolved impact type name ({@code "shock"|"trend"|"crash"})
     * @param peakFactor           resolved peak multiplicative influence of the impact
     * @param reversal             resolved reversal mode name ({@code "ramp"|"exponential"|"none"})
     * @param totalDurationSeconds total envelope length in seconds (LIVE-badge/remaining-time
     *                             display; for {@code reversal:none} the time until permanence)
     */
    public NewsRecord(long newsUid, String eventId, long timestampEpochMs, long gameDay,
                      Map<String, String> headline, Map<String, String> text,
                      List<AffectedMarket> affectedMarkets, String impactType,
                      float peakFactor, String reversal, int totalDurationSeconds) {
        this(newsUid, eventId, timestampEpochMs, gameDay, headline, text, affectedMarkets,
                impactType, peakFactor, reversal, totalDurationSeconds, "", 0);
    }

    /**
     * Full constructor including the T-099 sequence descriptor fields — used by the
     * NewsPlugin at publish time; the shorter overload above keeps pre-T-099 call
     * sites source compatible.
     *
     * @param newsUid              monotonic unique id of this record (pagination key)
     * @param eventId              the definition id this record was published from
     * @param timestampEpochMs     publish time as master epoch milliseconds (not impact start)
     * @param gameDay              the world's game day at publish time ("Day N" display)
     * @param headline             full language → headline map (insertion order preserved)
     * @param text                 full language → newspaper-text map (insertion order preserved)
     * @param affectedMarkets      the actually-impacted (matched∩subscribed) market subset
     * @param impactType           resolved impact type name ({@code "shock"|"trend"|"crash"};
     *                             {@code "sequence"} for sequences[]-authored events, T-095)
     * @param peakFactor           resolved peak multiplicative influence of the impact
     * @param reversal             resolved reversal mode name ({@code "ramp"|"exponential"|"none"};
     *                             {@code "sequence"} for non-permanent sequence events, T-095)
     * @param totalDurationSeconds total sequence length in seconds (LIVE-badge/remaining-time
     *                             display; for permanent endings the time until permanence)
     * @param sequenceName         the picked sequence's name for sequences[]-authored events,
     *                             or an <b>empty string for legacy impact events</b> (T-099)
     * @param stepCount            the resolved sequence's step count (T-099; legacy events
     *                             report their implicit 3 normalization steps)
     */
    public NewsRecord(long newsUid, String eventId, long timestampEpochMs, long gameDay,
                      Map<String, String> headline, Map<String, String> text,
                      List<AffectedMarket> affectedMarkets, String impactType,
                      float peakFactor, String reversal, int totalDurationSeconds,
                      String sequenceName, int stepCount) {
        this.newsUid = newsUid;
        this.eventId = eventId;
        this.timestampEpochMs = timestampEpochMs;
        this.gameDay = gameDay;
        this.headline = new LinkedHashMap<>(headline);
        this.text = new LinkedHashMap<>(text);
        this.affectedMarkets = new ArrayList<>(affectedMarkets);
        this.impactType = impactType;
        this.peakFactor = peakFactor;
        this.reversal = reversal;
        this.totalDurationSeconds = totalDurationSeconds;
        this.sequenceName = sequenceName != null ? sequenceName : "";
        this.stepCount = Math.max(0, stepCount);
    }

    // ── Accessors ────────────────────────────────────────────────────────

    /** @return the monotonic unique id of this record (newest-first pagination key) */
    public long getNewsUid() { return newsUid; }

    /** @return the definition id this record was published from */
    public String getEventId() { return eventId; }

    /** @return publish time as master epoch milliseconds */
    public long getTimestampEpochMs() { return timestampEpochMs; }

    /** @return the world's game day at publish time */
    public long getGameDay() { return gameDay; }

    /** @return the language → headline map (insertion-ordered, unmodifiable) */
    public Map<String, String> getHeadline() { return Collections.unmodifiableMap(headline); }

    /** @return the language → newspaper-text map (insertion-ordered, unmodifiable) */
    public Map<String, String> getText() { return Collections.unmodifiableMap(text); }

    /** @return the actually-impacted market subset (unmodifiable) */
    public List<AffectedMarket> getAffectedMarkets() { return Collections.unmodifiableList(affectedMarkets); }

    /** @return the resolved impact type name ({@code "shock"|"trend"|"crash"}) */
    public String getImpactType() { return impactType; }

    /** @return the resolved peak multiplicative influence */
    public float getPeakFactor() { return peakFactor; }

    /** @return the resolved reversal mode name ({@code "ramp"|"exponential"|"none"}) */
    public String getReversal() { return reversal; }

    /** @return the total envelope length in seconds (see full constructor) */
    public int getTotalDurationSeconds() { return totalDurationSeconds; }

    /**
     * @return the name of the sequence the published run played (T-099), or an
     *         <b>empty string</b> for legacy {@code impact}-authored events and for
     *         records written before T-099 — clients key their "sequence vs. simple
     *         impact" rendering off this emptiness
     */
    public String getSequenceName() { return sequenceName; }

    /**
     * @return the resolved sequence's step count (T-099); {@code 0} for records
     *         written before T-099 (unknown)
     */
    public int getStepCount() { return stepCount; }

    /**
     * @return the 20-byte SHA-1 of this record's newspaper picture, or null for a
     *         text-only record; the bytes are served by the master's
     *         {@link NewsPictureStore} keyed by exactly this hash (do not mutate)
     */
    public byte @Nullable [] getPictureHash() { return pictureHash; }

    /**
     * Sets the picture content hash. Called by the {@link ServerNewsPublisher} at
     * publish time — <b>before</b> the record is appended to the history and
     * broadcast — after it snapshotted the picture bytes into the published store.
     *
     * @param pictureHash the 20-byte SHA-1 of the picture bytes, or null for
     *                    text-only; a hash of any other length is rejected (kept null)
     */
    public void setPictureHash(byte @Nullable [] pictureHash) {
        this.pictureHash = (pictureHash != null
                && pictureHash.length == NewsPictureLibrary.SHA1_LENGTH) ? pictureHash : null;
    }

    // ── NBT persistence (ServerSaveable) ─────────────────────────────────

    /**
     * Writes this record into the given tag.
     * Translation maps are stored as ordered lists of {@code {lang, value}} pairs
     * because CompoundTag would not preserve the "first map entry" fallback order.
     *
     * @param tag the tag to populate
     * @return true (this save cannot fail)
     */
    @Override
    public boolean save(CompoundTag tag) {
        tag.putLong(KEY_NEWS_UID, newsUid);
        tag.putString(KEY_EVENT_ID, eventId);
        tag.putLong(KEY_TIMESTAMP, timestampEpochMs);
        tag.putLong(KEY_GAME_DAY, gameDay);
        tag.put(KEY_HEADLINE, saveTextMap(headline));
        tag.put(KEY_TEXT, saveTextMap(text));

        ListTag marketsTag = new ListTag();
        for (AffectedMarket market : affectedMarkets) {
            marketsTag.add(market.toTag());
        }
        tag.put(KEY_AFFECTED_MARKETS, marketsTag);

        tag.putString(KEY_IMPACT_TYPE, impactType);
        tag.putFloat(KEY_PEAK_FACTOR, peakFactor);
        tag.putString(KEY_REVERSAL, reversal);
        tag.putInt(KEY_TOTAL_DURATION, totalDurationSeconds);
        // Picture hash is optional: the tag is only written when present, so
        // text-only records (and pre-picture saves) stay byte-identical.
        if (pictureHash != null) {
            tag.putByteArray(KEY_PICTURE_HASH, pictureHash);
        }
        // T-099 sequence descriptor: same conditional-save pattern as the picture
        // hash — legacy records (empty name / unknown count) stay byte-identical.
        if (!sequenceName.isEmpty()) {
            tag.putString(KEY_SEQUENCE_NAME, sequenceName);
        }
        if (stepCount > 0) {
            tag.putInt(KEY_STEP_COUNT, stepCount);
        }
        return true;
    }

    /**
     * Restores this record from the given tag.
     *
     * @param tag the tag to read from
     * @return false if mandatory fields are missing (record should be discarded)
     */
    @Override
    public boolean load(CompoundTag tag) {
        if (!tag.contains(KEY_NEWS_UID) || !tag.contains(KEY_EVENT_ID)) return false;
        newsUid = tag.getLong(KEY_NEWS_UID);
        eventId = tag.getString(KEY_EVENT_ID);
        timestampEpochMs = tag.getLong(KEY_TIMESTAMP);
        gameDay = tag.getLong(KEY_GAME_DAY);
        headline = loadTextMap(tag.getList(KEY_HEADLINE, Tag.TAG_COMPOUND));
        text = loadTextMap(tag.getList(KEY_TEXT, Tag.TAG_COMPOUND));

        affectedMarkets = new ArrayList<>();
        ListTag marketsTag = tag.getList(KEY_AFFECTED_MARKETS, Tag.TAG_COMPOUND);
        for (int i = 0; i < marketsTag.size(); i++) {
            AffectedMarket market = AffectedMarket.fromTag(marketsTag.getCompound(i));
            if (market != null) affectedMarkets.add(market);
        }

        impactType = tag.getString(KEY_IMPACT_TYPE);
        peakFactor = tag.getFloat(KEY_PEAK_FACTOR);
        reversal = tag.getString(KEY_REVERSAL);
        totalDurationSeconds = tag.getInt(KEY_TOTAL_DURATION);
        // contains() guard: history files written before the picture feature (T-088)
        // have no such tag and load unchanged as text-only records.
        pictureHash = null;
        if (tag.contains(KEY_PICTURE_HASH)) {
            byte[] hash = tag.getByteArray(KEY_PICTURE_HASH);
            if (hash.length == NewsPictureLibrary.SHA1_LENGTH) {
                pictureHash = hash;
            }
        }
        // contains() guard (T-099): history files written before the sequence fields
        // load unchanged as "no sequence descriptor" (empty name, count 0).
        sequenceName = tag.contains(KEY_SEQUENCE_NAME) ? tag.getString(KEY_SEQUENCE_NAME) : "";
        stepCount = tag.contains(KEY_STEP_COUNT) ? Math.max(0, tag.getInt(KEY_STEP_COUNT)) : 0;
        return true;
    }

    /**
     * Convenience factory mirroring {@link ItemID#createFromTag}.
     *
     * @param tag the tag to read from
     * @return the loaded record, or null if the tag is malformed
     */
    public static @Nullable NewsRecord createFromTag(@NotNull CompoundTag tag) {
        NewsRecord record = new NewsRecord();
        return record.load(tag) ? record : null;
    }

    /** Serializes a translation map into an order-preserving ListTag of {lang, value} pairs. */
    private static ListTag saveTextMap(Map<String, String> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.putString(KEY_LANG, entry.getKey());
            pair.putString(KEY_VALUE, entry.getValue());
            list.add(pair);
        }
        return list;
    }

    /** Restores a translation map from the order-preserving ListTag form. */
    private static Map<String, String> loadTextMap(ListTag list) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pair = list.getCompound(i);
            if (pair.contains(KEY_LANG) && pair.contains(KEY_VALUE)) {
                map.put(pair.getString(KEY_LANG), pair.getString(KEY_VALUE));
            }
        }
        return map;
    }

    // ── Network serialization ────────────────────────────────────────────

    /**
     * Network codec for both the live publish broadcast and paginated history responses.
     * LinkedHashMap suppliers keep the translation-map order intact (client language
     * fallback relies on "first map entry").
     * <p>
     * <b>No version byte:</b> encoder and decoder ship lockstep in the same mod jar
     * (server and client halves of one build; master and slave servers must run the
     * same mod version anyway), so appending fields — like the T-088 picture hash —
     * is safe as long as {@code encode} and {@code decode} are always updated together.
     * There is no cross-version compatibility layer.
     * <p>
     * <b>Append-only, unconditional trailing fields:</b> this codec is used <i>inside
     * list codecs</i> ({@code NewsHistoryRequest.OutputData} via
     * {@code ExtraCodecUtils.listStreamCodec}), so optional trailing fields must be
     * encoded <b>unconditionally</b> as {@code presence BOOL + value} — a
     * {@code buf.isReadable()} trick would misread the next list element's bytes.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, NewsRecord> STREAM_CODEC = new StreamCodec<>() {

        private final StreamCodec<RegistryFriendlyByteBuf, LinkedHashMap<String, String>> TEXT_MAP_CODEC =
                ExtraCodecUtils.mapStreamCodec(ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8,
                        LinkedHashMap::new);
        private final StreamCodec<RegistryFriendlyByteBuf, List<AffectedMarket>> MARKET_LIST_CODEC =
                ExtraCodecUtils.listStreamCodec(AffectedMarket.STREAM_CODEC);

        @Override
        public void encode(RegistryFriendlyByteBuf buf, NewsRecord val) {
            ByteBufCodecs.VAR_LONG.encode(buf, val.newsUid);
            ByteBufCodecs.STRING_UTF8.encode(buf, val.eventId);
            ByteBufCodecs.VAR_LONG.encode(buf, val.timestampEpochMs);
            ByteBufCodecs.VAR_LONG.encode(buf, val.gameDay);
            TEXT_MAP_CODEC.encode(buf, new LinkedHashMap<>(val.headline));
            TEXT_MAP_CODEC.encode(buf, new LinkedHashMap<>(val.text));
            MARKET_LIST_CODEC.encode(buf, val.affectedMarkets);
            ByteBufCodecs.STRING_UTF8.encode(buf, val.impactType);
            ByteBufCodecs.FLOAT.encode(buf, val.peakFactor);
            ByteBufCodecs.STRING_UTF8.encode(buf, val.reversal);
            ByteBufCodecs.VAR_INT.encode(buf, val.totalDurationSeconds);
            // T-088: optional picture hash — unconditional presence bool, then the
            // fixed 20 raw digest bytes (see the append-only codec caveat above).
            ByteBufCodecs.BOOL.encode(buf, val.pictureHash != null);
            if (val.pictureHash != null) {
                buf.writeBytes(val.pictureHash);
            }
            // T-099: sequence descriptor — appended UNCONDITIONALLY after the picture
            // hash (list-codec constraint, never buf.isReadable(); empty string / 0
            // are the legacy defaults, see the append-only codec caveat above).
            ByteBufCodecs.STRING_UTF8.encode(buf, val.sequenceName);
            ByteBufCodecs.VAR_INT.encode(buf, val.stepCount);
        }

        @Override
        public NewsRecord decode(RegistryFriendlyByteBuf buf) {
            NewsRecord record = new NewsRecord();
            record.newsUid = ByteBufCodecs.VAR_LONG.decode(buf);
            record.eventId = ByteBufCodecs.STRING_UTF8.decode(buf);
            record.timestampEpochMs = ByteBufCodecs.VAR_LONG.decode(buf);
            record.gameDay = ByteBufCodecs.VAR_LONG.decode(buf);
            record.headline = TEXT_MAP_CODEC.decode(buf);
            record.text = TEXT_MAP_CODEC.decode(buf);
            record.affectedMarkets = new ArrayList<>(MARKET_LIST_CODEC.decode(buf));
            record.impactType = ByteBufCodecs.STRING_UTF8.decode(buf);
            record.peakFactor = ByteBufCodecs.FLOAT.decode(buf);
            record.reversal = ByteBufCodecs.STRING_UTF8.decode(buf);
            record.totalDurationSeconds = ByteBufCodecs.VAR_INT.decode(buf);
            // T-088: optional picture hash (unconditional presence bool, lockstep
            // with encode — never buf.isReadable(), see the codec caveat above).
            if (ByteBufCodecs.BOOL.decode(buf)) {
                byte[] hash = new byte[NewsPictureLibrary.SHA1_LENGTH];
                buf.readBytes(hash);
                record.pictureHash = hash;
            }
            // T-099: sequence descriptor (unconditional append, lockstep with encode).
            record.sequenceName = ByteBufCodecs.STRING_UTF8.decode(buf);
            record.stepCount = Math.max(0, ByteBufCodecs.VAR_INT.decode(buf));
            return record;
        }
    };

    // ── Equality (value semantics, used by round-trip tests) ─────────────

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NewsRecord other)) return false;
        return newsUid == other.newsUid
                && timestampEpochMs == other.timestampEpochMs
                && gameDay == other.gameDay
                && Float.compare(peakFactor, other.peakFactor) == 0
                && totalDurationSeconds == other.totalDurationSeconds
                && eventId.equals(other.eventId)
                && headline.equals(other.headline)
                && text.equals(other.text)
                && affectedMarkets.equals(other.affectedMarkets)
                && impactType.equals(other.impactType)
                && reversal.equals(other.reversal)
                && Arrays.equals(pictureHash, other.pictureHash)
                && sequenceName.equals(other.sequenceName)
                && stepCount == other.stepCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(newsUid, eventId, timestampEpochMs);
    }

    @Override
    public String toString() {
        return "NewsRecord{uid=" + newsUid + ", eventId='" + eventId
                + "', time=" + timestampEpochMs + ", gameDay=" + gameDay
                + ", markets=" + affectedMarkets.size()
                + ", impact=" + impactType + "/" + reversal
                + ", peak=" + peakFactor
                + ", picture=" + (pictureHash != null ? NewsPictureLibrary.toHex(pictureHash) : "none")
                + "}";
    }
}
