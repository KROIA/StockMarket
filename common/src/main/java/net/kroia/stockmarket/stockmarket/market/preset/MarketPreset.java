package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.util.VolatileItemComponents;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.setting.parser.ItemStackJsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A single market preset entry representing an item with its default price and natural abundance.
 * Supports both simple items (just a registry ID) and complex items with data components
 * (enchanted books, potions, tipped arrows, etc.).
 *
 * <p>JSON format (serialized by Gson):
 * <pre>
 * Simple item:
 * {
 *   "itemId": "minecraft:iron_ingot",
 *   "defaultPrice": 15.0,
 *   "naturalAbundance": 30.0
 * }
 *
 * Item with components:
 * {
 *   "itemId": "minecraft:enchanted_book",
 *   "components": {
 *     "minecraft:stored_enchantments": {
 *       "levels": {
 *         "minecraft:sharpness": {"nbt_type": "int", "value": 5}
 *       }
 *     }
 *   },
 *   "defaultPrice": 100.0,
 *   "naturalAbundance": 1.0
 * }
 * </pre>
 */
public class MarketPreset {

    private static final ItemStackJsonParser ITEM_STACK_PARSER = new ItemStackJsonParser();

    // Registry context for encoding/decoding data-driven components (enchantments, etc.)
    // Set by both server (onBankSystemSetupComplete) and client (onPlayerJoinClientSide)
    private static @Nullable HolderLookup.Provider registryAccess;

    public static void setRegistryAccess(@Nullable HolderLookup.Provider provider) {
        registryAccess = provider;
    }

    private String itemId;
    @Nullable
    private JsonObject components;
    private float defaultPrice;
    private float naturalAbundance;

    // Cached ItemStack, rebuilt on first access — not serialized by Gson
    private transient ItemStack cachedItemStack;

    // Pre-registered ItemID short value assigned by the server during startup.
    // Sent to clients via STREAM_CODEC so they can create markets without
    // building ItemStacks locally (avoids cross-registry Holder issues).
    private transient short registeredItemIDShort = -1;

    public void setRegisteredItemIDShort(short id) { this.registeredItemIDShort = id; }
    public short getRegisteredItemIDShort() { return registeredItemIDShort; }
    public boolean hasRegisteredItemID() { return registeredItemIDShort > 0; }

    // Default constructor for Gson deserialization
    public MarketPreset() {
        this.itemId = "";
        this.components = null;
        this.defaultPrice = 0;
        this.naturalAbundance = 0;
    }

    // Constructor for simple items (no components) — backward compatible
    public MarketPreset(String itemId, float defaultPrice, float naturalAbundance) {
        this.itemId = itemId;
        this.components = null;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
    }

    // Full constructor with optional component data
    public MarketPreset(String itemId, @Nullable JsonObject components, float defaultPrice, float naturalAbundance) {
        this.itemId = itemId;
        this.components = components;
        this.defaultPrice = defaultPrice;
        this.naturalAbundance = naturalAbundance;
    }

    // ===== Accessors (named to match the old record accessor style) =====

    public String getItemId()            { return itemId; }
    public float getDefaultPrice()       { return defaultPrice; }
    public float getNaturalAbundance()   { return naturalAbundance; }
    @Nullable
    public JsonObject components()    { return components; }
    public boolean hasComponents()    { return components != null && components.size() > 0; }

    // Returns a key that uniquely identifies this item including its components
    public String getUniqueKey() {
        if (!hasComponents()) return itemId;
        return itemId + "#" + components.hashCode();
    }

    // ===== ItemStack conversion =====

    /**
     * Returns the ItemStack this preset represents, including any data components.
     * The result is cached after the first call.
     */
    public ItemStack toItemStack() {
        if (cachedItemStack == null) {
            cachedItemStack = buildItemStack(itemId, components);
        }
        return cachedItemStack;
    }

    // ===== Static ItemStack serialization/deserialization =====

    /**
     * Serializes an ItemStack to a human-readable JsonObject.
     * Uses RegistryOps for data-driven components (enchantments) when registry context is available.
     * The output contains "id", "count", and optionally "components".
     *
     * <p>The stack is first passed through {@link VolatileItemComponents#normalize(ItemStack)}
     * so that <b>volatile components never enter preset persistence</b>. Some mods (e.g.
     * TerraFirmaCraft's {@code tfc:food} decay timestamp) attach time-varying components to
     * stacks; serializing them here would freeze a point-in-time value (a creation date or a
     * never-decay sentinel) into the preset JSON forever, corrupting BankSystem ItemID
     * identity for every market later created from the preset. Normalizing at this capture
     * boundary keeps preset JSON stable regardless of when/where the source stack was picked
     * (creative tab, JEI, inventory) and makes {@link #getUniqueKey()} time-independent.
     *
     * @param stack the ItemStack to serialize (never mutated)
     * @return a JsonObject representing the item, or an empty object for empty stacks
     */
    public static JsonObject serializeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("id", "minecraft:air");
            return empty;
        }

        // Strip volatile/deposit-gated components (BankSystem tag + config driven) before
        // encoding — presets must never persist e.g. TFC creation dates. Works on a copy;
        // the caller's stack is untouched.
        stack = VolatileItemComponents.normalize(stack);

        JsonObject json = new JsonObject();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        json.addProperty("id", itemId.toString());
        json.addProperty("count", stack.getCount());

        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            try {
                // Use RegistryOps for data-driven component types (enchantments, etc.)
                Tag componentTag;
                if (registryAccess != null) {
                    componentTag = DataComponentPatch.CODEC
                            .encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), patch)
                            .getOrThrow();
                } else {
                    componentTag = DataComponentPatch.CODEC
                            .encodeStart(NbtOps.INSTANCE, patch)
                            .getOrThrow();
                }
                if (componentTag instanceof CompoundTag compoundTag) {
                    json.add("components", ITEM_STACK_PARSER.nbtToJson(compoundTag));
                }
            } catch (Exception ignored) {
            }
        }

        return json;
    }

    /**
     * Deserializes an ItemStack from a JsonObject produced by {@link #serializeItemStack}.
     * Uses RegistryOps for data-driven components when registry context is available.
     *
     * @param json the JsonObject to deserialize
     * @return the reconstructed ItemStack, or {@link ItemStack#EMPTY} on failure
     */
    public static ItemStack deserializeItemStack(JsonObject json) {
        if (json == null || json.isEmpty()) return ItemStack.EMPTY;
        try {
            String id = json.has("id") ? json.get("id").getAsString() : "minecraft:air";
            JsonObject comps = json.has("components") ? json.getAsJsonObject("components") : null;
            return buildItemStack(id, comps);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Builds an ItemStack from a registry id and an optional serialized component patch.
     *
     * <p>The reconstructed stack is passed through
     * {@link VolatileItemComponents#normalize(ItemStack)} before being returned, for two reasons:
     * <ol>
     *   <li><b>Legacy presets:</b> presets saved before normalization existed may already have
     *       volatile components (e.g. {@code tfc:food} with a frozen creation date or a
     *       never-decay sentinel) baked into their JSON. Cleaning here means such presets are
     *       repaired on every load/use instead of poisoning ItemID registration.</li>
     *   <li><b>Bypassed constructor hooks:</b> {@code applyComponents(patch)} skips third-party
     *       {@code ItemStack}-constructor hooks (TFC re-attaches fresh food state only during
     *       construction/copy), so a deserialized volatile component would keep its stale value
     *       and a {@code null} internal parent. Volatile components must never reach ItemID
     *       registration — BankSystem strips them at its own boundary too, but the preset cache
     *       ({@link #toItemStack()}) would otherwise still hand out contaminated stacks.</li>
     * </ol>
     *
     * @param itemIdStr  item registry id, e.g. {@code "minecraft:iron_ingot"}
     * @param components serialized component patch as produced by {@link #serializeItemStack},
     *                   or null for simple items
     * @return the normalized reconstructed stack, or {@link ItemStack#EMPTY} on failure
     */
    private static ItemStack buildItemStack(String itemIdStr, @Nullable JsonObject components) {
        try {
            ResourceLocation loc = ResourceLocation.tryParse(itemIdStr);
            if (loc == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item == Items.AIR && !itemIdStr.equals("minecraft:air")) return ItemStack.EMPTY;

            ItemStack stack = new ItemStack(item);
            if (components != null && components.size() > 0) {
                CompoundTag componentTag = ITEM_STACK_PARSER.jsonToNbt(components);
                DataComponentPatch patch;
                if (registryAccess != null) {
                    var result = DataComponentPatch.CODEC
                            .parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), componentTag);
                    if (result.isError()) {
                        // WARN, not ERROR: a preset that references an enchantment/component
                        // from an absent optional mod (e.g. Create's `create:capacity` when the
                        // mod is removed from an existing world) is expected behavior — the
                        // preset is skipped, the server continues, no admin action is required.
                        // We keep the raw DataResult on DEBUG for non-obvious parse failures
                        // (schema drift, corruption) so real bugs remain diagnosable.
                        String rawError = result.error().map(Object::toString).orElse("?");
                        String missingKey = extractMissingKey(rawError);
                        net.kroia.stockmarket.StockMarketMod.LOGGER.warn(
                                "[MarketPreset] Skipping preset for {}: enchantment/component {} is not registered (mod possibly absent).",
                                itemIdStr, missingKey != null ? missingKey : "<unknown>");
                        net.kroia.stockmarket.StockMarketMod.LOGGER.debug(
                                "[MarketPreset] Raw parse error for {}: {} | NBT: {}",
                                itemIdStr, rawError, componentTag);
                    }
                    patch = result.getOrThrow();
                } else {
                    net.kroia.stockmarket.StockMarketMod.LOGGER.warn(
                            "[MarketPreset] No registryAccess for component parsing of {}", itemIdStr);
                    patch = DataComponentPatch.CODEC
                            .parse(NbtOps.INSTANCE, componentTag)
                            .getOrThrow();
                }
                stack.applyComponents(patch);
            }
            // Normalize before returning so volatile components (frozen in old presets or
            // re-attached by mod constructor hooks above) never reach ItemID registration
            // or the preset's cached stack. See method Javadoc.
            return VolatileItemComponents.normalize(stack);
        } catch (Exception e) {
            // WARN, not ERROR: the dominant cause of this exception is `result.getOrThrow()`
            // above rethrowing a missing-mod parse failure — expected behavior for optional
            // content on a world where the referenced mod is no longer present. Keep the raw
            // stack trace / message on DEBUG for non-obvious failures.
            String message = e.getMessage();
            String missingKey = message != null ? extractMissingKey(message) : null;
            if (missingKey != null) {
                net.kroia.stockmarket.StockMarketMod.LOGGER.warn(
                        "[MarketPreset] Skipping preset for {}: enchantment/component {} is not registered (mod possibly absent).",
                        itemIdStr, missingKey);
            } else {
                net.kroia.stockmarket.StockMarketMod.LOGGER.warn(
                        "[MarketPreset] Skipping preset for {}: {}", itemIdStr, message);
            }
            net.kroia.stockmarket.StockMarketMod.LOGGER.debug(
                    "[MarketPreset] Exception building ItemStack for {}", itemIdStr, e);
            return ItemStack.EMPTY;
        }
    }

    /**
     * Best-effort extraction of the missing enchantment / component registry key from a
     * {@code DataResult.Error} message. The vanilla codec framework produces messages
     * of the form {@code "Failed to get element <namespace>:<path> missed input: ..."}
     * when a referenced registry entry is absent (e.g. an enchantment provided by a mod
     * that is no longer loaded).
     *
     * @param errorMessage the DataResult error string or exception message
     * @return the missing key (e.g. {@code "create:capacity"}) or {@code null} if the
     *         message shape does not match — callers should fall back to a generic message
     */
    @Nullable
    private static String extractMissingKey(String errorMessage) {
        if (errorMessage == null) return null;
        final String marker = "Failed to get element ";
        int idx = errorMessage.indexOf(marker);
        if (idx < 0) return null;
        int start = idx + marker.length();
        int end = start;
        while (end < errorMessage.length()) {
            char c = errorMessage.charAt(end);
            // Registry keys are namespace:path with lowercase letters, digits, _, -, /, .
            // Break on whitespace, quotes, commas, brackets, etc.
            if (Character.isWhitespace(c) || c == '\'' || c == '"' || c == ',' || c == ']' || c == '}' || c == ')') break;
            end++;
        }
        return end > start ? errorMessage.substring(start, end) : null;
    }

    // ===== Network serialization =====

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPreset> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, MarketPreset preset) {
            ByteBufCodecs.STRING_UTF8.encode(buf, preset.itemId);
            boolean hasComps = preset.hasComponents();
            ByteBufCodecs.BOOL.encode(buf, hasComps);
            if (hasComps) {
                ExtraCodecUtils.JSON_ELEMENT_CODEC.encode(buf, preset.components);
            }
            ByteBufCodecs.FLOAT.encode(buf, preset.defaultPrice);
            ByteBufCodecs.FLOAT.encode(buf, preset.naturalAbundance);
            ByteBufCodecs.SHORT.encode(buf, preset.registeredItemIDShort);
        }

        @Override
        public MarketPreset decode(RegistryFriendlyByteBuf buf) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean hasComps = ByteBufCodecs.BOOL.decode(buf);
            JsonObject comps = null;
            if (hasComps) {
                JsonElement element = ExtraCodecUtils.JSON_ELEMENT_CODEC.decode(buf);
                if (element.isJsonObject()) comps = element.getAsJsonObject();
            }
            float price = ByteBufCodecs.FLOAT.decode(buf);
            float abundance = ByteBufCodecs.FLOAT.decode(buf);
            MarketPreset preset = new MarketPreset(id, comps, price, abundance);
            preset.registeredItemIDShort = ByteBufCodecs.SHORT.decode(buf);
            return preset;
        }
    };
}
