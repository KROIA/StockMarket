package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.modutilities.setting.parser.ItemStackJsonParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
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

    private String itemId;
    @Nullable
    private JsonObject components;
    private float defaultPrice;
    private float naturalAbundance;

    // Cached ItemStack, rebuilt on first access — not serialized by Gson
    private transient ItemStack cachedItemStack;

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
            JsonObject json = new JsonObject();
            json.addProperty("id", itemId);
            json.addProperty("count", 1);
            if (hasComponents()) {
                json.add("components", components);
            }
            try {
                cachedItemStack = ITEM_STACK_PARSER.fromJson(json);
            } catch (Exception e) {
                cachedItemStack = ItemStack.EMPTY;
            }
        }
        return cachedItemStack;
    }

    // ===== Static ItemStack serialization/deserialization =====

    /**
     * Serializes an ItemStack to a human-readable JsonObject.
     * The output contains "id", "count", and optionally "components" for items
     * with non-default data components (enchantments, potion effects, etc.).
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
        JsonElement json = ITEM_STACK_PARSER.toJson(stack);
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    /**
     * Deserializes an ItemStack from a JsonObject produced by {@link #serializeItemStack}.
     * Expects at minimum an "id" field; "count" defaults to 1 if absent.
     *
     * @param json the JsonObject to deserialize
     * @return the reconstructed ItemStack, or {@link ItemStack#EMPTY} on failure
     */
    public static ItemStack deserializeItemStack(JsonObject json) {
        if (json == null || json.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            return ITEM_STACK_PARSER.fromJson(json);
        } catch (Exception e) {
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
            return new MarketPreset(id, comps, price, abundance);
        }
    };
}
