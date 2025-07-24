package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.StockMarketModSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.rmi.ServerError;
import java.util.ArrayList;
import java.util.HashMap;

public class ServerTradingBotFactory {

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
    }

    public static class ItemData implements ServerSaveable
    {
        public String itemID;
        public EnchantmentData[] enchantments;
        public PotionData potion;

        public ItemData()
        {}
        public ItemData(String itemID)
        {
            this.itemID = itemID;
            this.enchantments = new EnchantmentData[0];
        }
        public ItemData(ItemStack stack)
        {
            itemID = ItemUtilities.getItemID(stack.getItem());
            CompoundTag tag = stack.getTag();
            assert tag != null;
            ArrayList<EnchantmentData> ench = new ArrayList<>();
            int i = 0;
            if (tag != null && tag.contains("StoredEnchantments", Tag.TAG_LIST)) {
                ListTag enchantments = tag.getList("StoredEnchantments", Tag.TAG_COMPOUND);
                for (Tag enchantmentTag : enchantments) {
                    CompoundTag enchantment = (CompoundTag) enchantmentTag;
                    EnchantmentData enchantmentData = new EnchantmentData();
                    enchantmentData.load(enchantment);
                    ench.add(enchantmentData);
                    i++;
                }
                this.enchantments = new EnchantmentData[i];
                for(int j = 0; j < ench.size(); j++)
                {
                    this.enchantments[j] = ench.get(j);
                }
            }
            else {
                this.enchantments = new EnchantmentData[0];
            }

            if (tag != null && tag.contains("Potion", Tag.TAG_STRING)) {
                PotionData potion = new PotionData();
                potion.load(tag);
                this.potion = potion;
            }
            else {
                this.potion = null;
            }
        }
        public ItemData(String itemID, EnchantmentData[] enchantments, PotionData potion)
        {
            this.itemID = itemID;
            this.enchantments = enchantments;
            this.potion = potion;
        }
        public ItemStack getItemStack()
        {
            ItemStack stack = ItemUtilities.createItemStackFromId(itemID);
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
            tag.putString("itemID", itemID);
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
            itemID = tag.getString("itemID");
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
    }

    public static class DefaultBotSettings
    {
        private final ServerVolatilityBot.Settings settings;


        public DefaultBotSettings(int price, float rarity, float volatility, long udateTimerIntervallMS)
        {
            this(new ServerVolatilityBot.Settings(price, rarity, volatility, udateTimerIntervallMS, true, true, true));
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
    }

    public static ServerTradingBot loadFromTag(CompoundTag tag)
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
    }

    public static class BotBuilderContainer
    {
        //public String itemID;
        public ItemData itemData;
        public DefaultBotSettings defaultSettings;
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
