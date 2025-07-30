package net.kroia.stockmarket.market.server.bot;

import com.google.gson.*;
import net.kroia.banksystem.banking.bank.MoneyBank;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerTradingBotFactory {
    private final static Gson gson = new GsonBuilder().setPrettyPrinting().create();


    public static class EnchantmentData implements ServerSaveable
    {
        public String enchantmentID;
        public int level;

        public EnchantmentData()
        {}
        public EnchantmentData(String enchantmentID, int level)
        {
            this.enchantmentID = enchantmentID;
            this.level = level;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putString("id", enchantmentID);
            tag.putInt("lvl", level);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            enchantmentID = tag.getString("id");
            level = tag.getInt("lvl");
            return true;
        }

        public JsonElement toJson()
        {
            JsonObject data = new JsonObject();
            data.addProperty("enchantmentID", enchantmentID);
            data.addProperty("level", level);
            return data;
        }

        public boolean fromJson(JsonElement json) {
            if (json.isJsonObject()) {
                JsonObject data = json.getAsJsonObject();
                if (data.has("enchantmentID") && data.has("level")) {
                    enchantmentID = data.get("enchantmentID").getAsString();
                    level = data.get("level").getAsInt();
                    return true;
                }
            }
            return false;
        }
    }
    public static class PotionData implements ServerSaveable
    {
        public String potionID;
        //public int amplifier;

        public PotionData()
        {}
        public PotionData(String potionID)
        {
            this.potionID = potionID;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putString("Potion", potionID);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            potionID = tag.getString("Potion");
            return true;
        }

        public JsonElement toJson()
        {
            JsonObject data = new JsonObject();
            data.addProperty("potionID", potionID);
            //data.addProperty("amplifier", amplifier);
            return data;
        }
        public boolean fromJson(JsonElement json) {
            if (json.isJsonObject()) {
                JsonObject data = json.getAsJsonObject();
                if (data.has("potionID")) {
                    potionID = data.get("potionID").getAsString();
                    //amplifier = data.get("amplifier").getAsInt();
                    return true;
                }
            }
            return false;
        }
    }

    public static class ItemData implements ServerSaveable
    {
        public ItemID itemID;
        public EnchantmentData[] enchantments;
        public PotionData potion;

        public ItemData()
        {}
        public ItemData(ItemID itemID)
        {
            this.itemID = itemID;
            this.enchantments = new EnchantmentData[0];
        }
        public ItemData(ItemStack stack)
        {
            itemID = new ItemID(stack);
            CompoundTag tag = stack.getTag();
            ArrayList<EnchantmentData> ench = new ArrayList<>();
            int i = 0;
            this.potion = null;
            if(tag != null) {
                if (tag.contains("StoredEnchantments", Tag.TAG_COMPOUND)) {
                    ListTag enchantments = tag.getList("StoredEnchantments", Tag.TAG_COMPOUND);
                    for (Tag enchantmentTag : enchantments) {
                        CompoundTag enchantment = (CompoundTag) enchantmentTag;
                        EnchantmentData enchantmentData = new EnchantmentData();
                        enchantmentData.load(enchantment);
                        ench.add(enchantmentData);
                        i++;
                    }
                    this.enchantments = new EnchantmentData[i];
                    for (int j = 0; j < ench.size(); j++) {
                        this.enchantments[j] = ench.get(j);
                    }
                }
                if (tag.contains("Potion", Tag.TAG_STRING)) {
                    PotionData potion = new PotionData();
                    potion.load(tag);
                    this.potion = potion;
                }
            }
            if(this.enchantments == null)
                this.enchantments = new EnchantmentData[0];
        }
        public ItemData(ItemID itemID, EnchantmentData[] enchantments, PotionData potion)
        {
            this.itemID = itemID;
            this.enchantments = enchantments;
            this.potion = potion;
        }
        public ItemStack getItemStack()
        {
            ItemStack stack = itemID.getStack();
            if(stack == null)
                return ItemStack.EMPTY;
            CompoundTag tag = null;
            if(enchantments.length>0) {
                if(tag == null)
                    tag = new CompoundTag();
                ListTag enchantmentsTag = new ListTag();
                for (EnchantmentData enchantment : enchantments) {
                    CompoundTag enchantmentTag = new CompoundTag();
                    enchantment.save(enchantmentTag);
                    enchantmentsTag.add(enchantmentTag);
                }
                tag.put("StoredEnchantments", enchantmentsTag);
            }

            if(potion != null) {
                if(tag == null)
                    tag = new CompoundTag();
                potion.save(tag);
            }

            if(tag != null)
                stack.setTag(tag);
            return stack;
        }
        public ItemID getItemID()
        {
            return new ItemID(getItemStack());
        }
        @Override
        public boolean save(CompoundTag tag) {
            CompoundTag itemTag = new CompoundTag();
            itemID.save(itemTag);
            tag.put("itemID", itemTag);
            CompoundTag enchantmentsTag = new CompoundTag();
            for(EnchantmentData enchantment : enchantments)
            {
                CompoundTag enchantmentTag = new CompoundTag();
                enchantment.save(enchantmentTag);
                enchantmentsTag.put(enchantment.enchantmentID, enchantmentTag);
            }
            tag.put("StoredEnchantments", enchantmentsTag);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            itemID = MoneyBank.compatibilityMoneyItemIDConvert(tag.getString("itemID"));
            // Compatibility with old money item ID format
            if(itemID == null)
            {
                CompoundTag itemTag = tag.getCompound("itemID");
                itemID = new ItemID(itemTag);
            }
            CompoundTag enchantmentsTag = tag.getCompound("StoredEnchantments");
            enchantments = new EnchantmentData[enchantmentsTag.size()];
            int i = 0;
            for(String key : enchantmentsTag.getAllKeys())
            {
                CompoundTag enchantmentTag = enchantmentsTag.getCompound(key);
                EnchantmentData enchantment = new EnchantmentData();
                enchantment.load(enchantmentTag);
                enchantments[i] = enchantment;
                i++;
            }
            return true;
        }

        JsonElement toJson()
        {
            JsonObject data = new JsonObject();
            data.addProperty("itemID", itemID.getName());
            if(enchantments.length > 0) {
                JsonArray enchantmentsTag = new JsonArray();
                for (EnchantmentData enchantment : enchantments) {
                    enchantmentsTag.add(enchantment.toJson());
                }
                data.add("enchantments", enchantmentsTag);
            }
            if(potion != null)
                data.add("potion", potion.toJson());
            return data;
        }

        boolean fromJson(JsonElement json) {
            if (json.isJsonObject()) {
                JsonObject data = json.getAsJsonObject();
                if (data.has("itemID")) {
                    itemID = new ItemID(data.get("itemID").getAsString());
                }
                if (data.has("enchantments")) {
                    JsonArray enchantmentsTag = data.getAsJsonArray("enchantments");
                    enchantments = new EnchantmentData[enchantmentsTag.size()];
                    for (int i = 0; i < enchantmentsTag.size(); i++) {
                        EnchantmentData enchantment = new EnchantmentData();
                        enchantment.fromJson(enchantmentsTag.get(i));
                        enchantments[i] = enchantment;
                    }
                } else {
                    enchantments = new EnchantmentData[0];
                }
                if (data.has("potion")) {
                    potion = new PotionData();
                    potion.fromJson(data.get("potion"));
                } else {
                    potion = null;
                }
                return true;
            }
            return false;
        }

    }

    public static class DefaultBotSettings
    {
        private final ServerVolatilityBot.Settings settings;


        public DefaultBotSettings(int price, float rarity, float volatility, long udateTimerIntervallMS)
        {
            this(new ServerVolatilityBot.Settings());
            //this(new ServerVolatilityBot.Settings(price, rarity, volatility, udateTimerIntervallMS, true, true, true));
        }
        public DefaultBotSettings(ServerVolatilityBot.Settings settings)
        {
            this.settings = new ServerVolatilityBot.Settings();
            this.settings.copyFrom(settings);
        }
        public void loadDefaultSettings(ServerVolatilityBot.Settings settings)
        {
            settings.copyFrom(this.settings);
        }
        public ServerVolatilityBot.Settings getSettings()
        {
            return settings;
        }
        public DefaultBotSettings setUpdateTimerIntervallMS(long updateTimerIntervallMS)
        {
            this.settings.updateTimerIntervallMS = updateTimerIntervallMS;
            return this;
        }
        public DefaultBotSettings setVolatility(float volatility)
        {
            this.settings.volatility = volatility;
            return this;
        }
        public DefaultBotSettings setDefaultPrice(int defaultPrice)
        {
            this.settings.defaultPrice = defaultPrice;
            return this;
        }

        JsonElement toJson()
        {
            JsonObject data = new JsonObject();
            data.addProperty("enabled", settings.enabled);
            data.addProperty("defaultPrice", settings.defaultPrice);
            data.addProperty("updateTimerIntervallMS", settings.updateTimerIntervallMS);
            data.addProperty("orderBookVolumeScale", settings.orderBookVolumeScale);
            data.addProperty("nearMarketVolumeScale", settings.nearMarketVolumeScale);
            data.addProperty("volumeAccumulationRate", settings.volumeAccumulationRate);
            data.addProperty("volumeFastAccumulationRate", settings.volumeFastAccumulationRate);
            data.addProperty("volumeDecumulationRate", settings.volumeDecumulationRate);

            data.addProperty("volumeScale", settings.volumeScale);
            data.addProperty("enableTargetPrice", settings.enableTargetPrice);
            data.addProperty("targetPriceSteeringFactor", settings.targetPriceSteeringFactor);
            data.addProperty("enableVolumeTracking", settings.enableVolumeTracking);
            data.addProperty("volumeSteeringFactor", settings.volumeSteeringFactor);
            data.addProperty("enableRandomWalk", settings.enableRandomWalk);
            data.addProperty("volatility", settings.volatility);
            return data;
        }

        boolean fromJson(JsonElement json) {
            if (json.isJsonObject()) {
                JsonObject data = json.getAsJsonObject();
                settings.enabled = data.get("enabled").getAsBoolean();
                settings.defaultPrice = data.get("defaultPrice").getAsInt();
                settings.updateTimerIntervallMS = data.get("updateTimerIntervallMS").getAsLong();
                settings.orderBookVolumeScale = data.get("orderBookVolumeScale").getAsFloat();
                settings.nearMarketVolumeScale = data.get("nearMarketVolumeScale").getAsFloat();
                settings.volumeAccumulationRate = data.get("volumeAccumulationRate").getAsFloat();
                settings.volumeFastAccumulationRate = data.get("volumeFastAccumulationRate").getAsFloat();
                settings.volumeDecumulationRate = data.get("volumeDecumulationRate").getAsFloat();

                settings.volumeScale = data.get("volumeScale").getAsFloat();
                settings.enableTargetPrice = data.get("enableTargetPrice").getAsBoolean();
                settings.targetPriceSteeringFactor = data.get("targetPriceSteeringFactor").getAsFloat();
                settings.enableVolumeTracking = data.get("enableVolumeTracking").getAsBoolean();
                settings.volumeSteeringFactor = data.get("volumeSteeringFactor").getAsFloat();
                settings.enableRandomWalk = data.get("enableRandomWalk").getAsBoolean();
                settings.volatility = data.get("volatility").getAsFloat();
                return true;
            }
            return false;
        }
    }

    /*public static ServerTradingBot loadFromTag(CompoundTag tag)
    {
        if(tag == null)
            return null;

        if(!tag.contains("class"))
            return null;

        String className = tag.getString("class");

        if(className.compareTo(ServerTradingBot.class.getSimpleName()) == 0)
        {
            ServerTradingBot bot = new ServerTradingBot();
            if(bot.load(tag))
                return bot;
        }
        else if(className.compareTo(ServerVolatilityBot.class.getSimpleName()) == 0)
        {
            ServerVolatilityBot bot = new ServerVolatilityBot();
            if(bot.load(tag))
                return bot;
        }

        return null;
    }*/

    public static class BotBuilderContainer
    {
        //public String itemID;
        public ItemData itemData;
        public DefaultBotSettings defaultSettings;

        public JsonElement toJson()
        {
            JsonObject data = new JsonObject();
            if(itemData != null)
                data.add("itemData", itemData.toJson());
            if(defaultSettings != null)
                data.add("defaultSettings", defaultSettings.toJson());
            return data;
        }

        public boolean fromJson(JsonElement json) {
            if (json.isJsonObject()) {
                JsonObject data = json.getAsJsonObject();
                if (data.has("itemData")) {
                    itemData = new ItemData();
                    itemData.fromJson(data.get("itemData"));
                } else {
                    itemData = null;
                }
                if (data.has("defaultSettings")) {
                    defaultSettings = new DefaultBotSettings(new ServerVolatilityBot.Settings());
                    defaultSettings.fromJson(data.get("defaultSettings"));
                } else {
                    defaultSettings = null;
                }
                return true;
            }
            return false;
        }
    }
    public static void botTableBuilder(
            HashMap<ItemID, BotBuilderContainer> table,
            ItemData itemData,
            DefaultBotSettings settings)
    {
        BotBuilderContainer container = new BotBuilderContainer();
        //container.itemData = new ItemData(itemID.getStack());
        container.defaultSettings = settings;
        table.put(itemData.getItemID(), container);
    }
}
