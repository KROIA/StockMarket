package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
     * @param stack the ItemStack to serialize
     * @return a JsonObject representing the item, or an empty object for empty stacks
     */
    public static JsonObject serializeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            JsonObject empty = new JsonObject();
            empty.addProperty("id", "minecraft:air");
            return empty;
        }

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
                        net.kroia.stockmarket.StockMarketMod.LOGGER.error(
                                "[MarketPreset] Failed to parse components for {}: {} | NBT: {}",
                                itemIdStr, result.error().map(Object::toString).orElse("?"), componentTag);
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
            return stack;
        } catch (Exception e) {
            net.kroia.stockmarket.StockMarketMod.LOGGER.error(
                    "[MarketPreset] Exception building ItemStack for {}: {}", itemIdStr, e.getMessage());
            return ItemStack.EMPTY;
        }
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
