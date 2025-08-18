package net.kroia.stockmarket.market.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.api.IServerBankManager;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarketFactory
{
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }


    /**
     * Data needed to generate a DefaultMarketSetupData object.
     * It uses some values out of which the DefaultMarketSetupData object is generated.
     */
    public static class DefaultMarketSetupGeneratorData implements INetworkPayloadConverter
    {
        public TradingPair tradingPair;

        public float defaultPrice;
        public float rarity;
        public float volatility;
        public long updateIntervalMS = 500; // Default update interval in milliseconds
        public boolean enableTargetPrice = true;
        public boolean enableVolumeTracking = true;
        public boolean enableRandomWalk = true;
        public int priceScaleFactor = 1;

        public DefaultMarketSetupGeneratorData(ItemStack itemStack, float defaultPrice, float rarity, float volatility) {
            this.tradingPair = new TradingPair(new ItemID(itemStack), BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getDefaultCurrencyItemID());
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
        public DefaultMarketSetupGeneratorData(Item item, float defaultPrice, float rarity, float volatility) {
            this.tradingPair = new TradingPair(new ItemID(item.getDefaultInstance()), BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getDefaultCurrencyItemID());
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
        public DefaultMarketSetupGeneratorData(TradingPair tradingPair, float defaultPrice, float rarity, float volatility) {
            this.tradingPair = tradingPair;
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
        public DefaultMarketSetupGeneratorData() {
            this.tradingPair = new TradingPair();
            this.defaultPrice = 0;
            this.rarity = 0.0f;
            this.volatility = 0.0f;
        }

        public DefaultMarketSetupData generateDefaultMarketSetupData() {
            ServerVolatilityBot.Settings botSettings = new ServerVolatilityBot.Settings();
            VirtualOrderBook.Settings virtualOrderBookSettings = new VirtualOrderBook.Settings();



            botSettings.defaultPrice = defaultPrice;
            botSettings.updateTimerIntervallMS = updateIntervalMS;

            botSettings.enableTargetPrice = enableTargetPrice;
            botSettings.targetPriceSteeringFactor = Math.max(rarity*0.1f,0.00001f);

            botSettings.enableVolumeTracking = enableVolumeTracking;
            botSettings.volumeSteeringFactor = Math.max(0.0000001f/(1.2f-rarity),0.0000001f);

            botSettings.enableRandomWalk = enableRandomWalk;
            botSettings.volatility = Math.abs(1000.f * volatility / Math.max(defaultPrice, 1000));

            virtualOrderBookSettings.volumeScale = 100f/(0.01f+Math.abs(rarity));
            virtualOrderBookSettings.volumeAccumulationRate = 0.0001f * Math.max(0.01f, 1/(Math.max(rarity, 0.01f)));
            virtualOrderBookSettings.volumeFastAccumulationRate = virtualOrderBookSettings.volumeAccumulationRate * 10f;
            virtualOrderBookSettings.volumeDecumulationRate = virtualOrderBookSettings.volumeFastAccumulationRate * 0.1f;
            botSettings.volumeScale = virtualOrderBookSettings.volumeScale * this.volatility;



            long candleTimeMin = 1;
            if(BACKEND_INSTANCES.SERVER_SETTINGS != null)
            {
                candleTimeMin = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get() / 60000; // Convert milliseconds to minutes
                // Auto set the price scale factor based on the initial price
                if (botSettings.defaultPrice < BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.PRICE_SCALE_100_DEFAULT_PRICE_THRESHOLD.get())
                    this.priceScaleFactor = 100; // Use cents for prices below 20
                else if (botSettings.defaultPrice < BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.PRICE_SCALE_10_DEFAULT_PRICE_THRESHOLD.get())
                    this.priceScaleFactor = 10; // Use deci for prices below 200
                else
                    this.priceScaleFactor = 1; // Use whole units for prices above 200
            }

            DefaultMarketSetupData setupData = new DefaultMarketSetupData(this.tradingPair, botSettings, virtualOrderBookSettings, false, candleTimeMin, this.priceScaleFactor);
            return setupData;
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            this.tradingPair.decode(buf);
            this.defaultPrice = buf.readFloat();
            this.rarity = buf.readFloat();
            this.volatility = buf.readFloat();
            this.updateIntervalMS = buf.readLong(); // Read update interval in milliseconds
            this.enableTargetPrice = buf.readBoolean(); // Read enable target price flag
            this.enableVolumeTracking = buf.readBoolean(); // Read enable volume tracking flag
            this.enableRandomWalk = buf.readBoolean(); // Read enable random walk flag
            this.priceScaleFactor = buf.readInt(); // Read price scale factor


        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            this.tradingPair.encode(buf);
            buf.writeFloat(this.defaultPrice);
            buf.writeFloat(this.rarity);
            buf.writeFloat(this.volatility);
            buf.writeLong(this.updateIntervalMS); // Write update interval in milliseconds
            buf.writeBoolean(this.enableTargetPrice); // Write enable target price flag
            buf.writeBoolean(this.enableVolumeTracking); // Write enable volume tracking flag
            buf.writeBoolean(this.enableRandomWalk); // Write enable random walk flag
            buf.writeInt(this.priceScaleFactor); // Write price scale factor

        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();



            // Use minimalistic JSON structure for trading pair instead of a full TradingPair object data
            ItemID item = this.tradingPair.getItem();
            ItemID currency = this.tradingPair.getCurrency();
            JsonObject pairData = new JsonObject();
            pairData.add("item", item.toJson());
            pairData.add("currency", currency.toJson());

            jsonObject.add("tradingPair", pairData);
            jsonObject.addProperty("defaultPrice", this.defaultPrice);
            jsonObject.addProperty("rarity", this.rarity);
            jsonObject.addProperty("volatility", this.volatility);
            jsonObject.addProperty("updateIntervalMS", this.updateIntervalMS); // Add update interval in milliseconds
            jsonObject.addProperty("enableTargetPrice", this.enableTargetPrice); // Add enable target price flag
            jsonObject.addProperty("enableVolumeTracking", this.enableVolumeTracking); // Add enable volume tracking flag
            jsonObject.addProperty("enableRandomWalk", this.enableRandomWalk); // Add enable random walk flag
            jsonObject.addProperty("priceScaleFactor", this.priceScaleFactor); // Add price scale factor
            return jsonObject;
        }
        public boolean fromJson(JsonElement json) {
            if(!json.isJsonObject())
                return false;

            JsonObject jsonObject = json.getAsJsonObject();

            JsonElement element = jsonObject.get("tradingPair");
            if(element != null && element.isJsonObject()) {
                JsonObject pairObject = element.getAsJsonObject();
                JsonElement itemElement = pairObject.get("item");
                JsonElement currencyElement = pairObject.get("currency");

                if(itemElement == null || currencyElement == null)
                    return false; // Invalid trading pair data


                ItemID item = new ItemID(itemElement);
                if(!item.fromJson(itemElement))
                    return false; // Invalid item data

                ItemID currency = new ItemID(currencyElement);
                if(!currency.fromJson(currencyElement))
                    return false; // Invalid currency data

                this.tradingPair = new TradingPair(item, currency);
            }

            element = jsonObject.get("defaultPrice");
            if(element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                this.defaultPrice = Math.max(0, element.getAsFloat());
            } else {
                this.defaultPrice = 0; // Default value if not present
            }

            element = jsonObject.get("rarity");
            if(element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                this.rarity = Math.max(0, element.getAsFloat());
            } else {
                this.rarity = 0.0f; // Default value if not present
            }

            element = jsonObject.get("volatility");
            if(element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                this.volatility = Math.max(0, element.getAsFloat());
            } else {
                this.volatility = 0.0f; // Default value if not present
            }

            element = jsonObject.get("updateIntervalMS");
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                this.updateIntervalMS = Math.max(100, element.getAsLong()); // Default to 500ms if not specified
            } else {
                this.updateIntervalMS = 500; // Default value if not present
            }
            element = jsonObject.get("enableTargetPrice");
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                this.enableTargetPrice = element.getAsBoolean(); // Default to true if not specified
            } else {
                this.enableTargetPrice = true; // Default value if not present
            }
            element = jsonObject.get("enableVolumeTracking");
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                this.enableVolumeTracking = element.getAsBoolean(); // Default to true if not specified
            } else {
                this.enableVolumeTracking = true; // Default value if not present
            }
            element = jsonObject.get("enableRandomWalk");
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                this.enableRandomWalk = element.getAsBoolean(); // Default to true if not specified
            } else {
                this.enableRandomWalk = true; // Default value if not present
            }
            element = jsonObject.get("priceScaleFactor");
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                this.priceScaleFactor = Math.max(1, element.getAsInt()); // Default to 1 if not specified
            } else {
                this.priceScaleFactor = 1; // Default value if not present
            }
            return true;
        }

        @Override
        public String toString()
        {
            return JsonUtilities.toPrettyString(toJson());
        }
    }

    public static class DefaultMarketSetupData implements INetworkPayloadEncoder
    {
        @NotNull public TradingPair tradingPair;

        @Nullable public ServerVolatilityBot.Settings botSettings;
        @Nullable public VirtualOrderBook.Settings virtualOrderBookSettings;
        public boolean isMarketOpen = false;
        public long candleTimeMin = 1;
        public float defaultPrice;
        public int priceScaleFactor = 1;


        public DefaultMarketSetupData(TradingPair pair) {
            this.tradingPair = pair;
            this.botSettings = null; // Default to no bot settings
        }
        public DefaultMarketSetupData(TradingPair pair,
                                      ServerVolatilityBot.Settings botSettings,
                                      VirtualOrderBook.Settings virtualOrderBookSettings,
                                      boolean isMarketOpen,
                                      long candleTimeMin,
                                      int priceScaleFactor) {
            this.tradingPair = pair;
            this.botSettings = botSettings;
            defaultPrice = (botSettings != null) ? botSettings.defaultPrice : 0; // Use bot settings default price if available
            this.virtualOrderBookSettings = virtualOrderBookSettings;
            this.isMarketOpen = isMarketOpen;
            this.candleTimeMin = candleTimeMin; // Default to 1 minute candle time
            this.priceScaleFactor = priceScaleFactor; // Default to 1 if not specified
        }
        private DefaultMarketSetupData() {
            this.tradingPair = new TradingPair();
            this.botSettings = null; // Default to no bot settings
            this.virtualOrderBookSettings = null; // Default to no virtual order book settings
        }

        public DefaultMarketSetupData(DefaultMarketSetupData other)
        {
            this.tradingPair = new TradingPair(other.tradingPair);
            if(other.botSettings != null)
                this.botSettings = new ServerVolatilityBot.Settings(other.botSettings);
            else
                this.botSettings = null; // No bot settings provided
            if(other.virtualOrderBookSettings != null)
                this.virtualOrderBookSettings = new VirtualOrderBook.Settings(other.virtualOrderBookSettings);
            else
                this.virtualOrderBookSettings = null; // No virtual order book settings provided

            this.isMarketOpen = other.isMarketOpen; // Copy market open status
            this.candleTimeMin = other.candleTimeMin; // Copy candle time in minutes, default to 1 minute
            this.defaultPrice = other.defaultPrice; // Copy default price, if needed
            this.priceScaleFactor = other.priceScaleFactor; // Copy price scale factor
        }

        public static DefaultMarketSetupData create(FriendlyByteBuf buf) {
            DefaultMarketSetupData data = new DefaultMarketSetupData();
            data.decode(buf);

            return data;
        }

        public void setDefaultPrice(float defaultPrice) {
            this.defaultPrice = defaultPrice;
            if (this.botSettings != null) {
                this.botSettings.defaultPrice = defaultPrice; // Update bot settings default price if available
            }
        }
        public float getDefaultPrice() {
            if (this.botSettings != null) {
                return this.botSettings.defaultPrice;
            }
            return this.defaultPrice;
        }


        @Override
        public void encode(FriendlyByteBuf buf) {
            this.tradingPair.encode(buf);
            buf.writeBoolean(this.botSettings != null);
            if (this.botSettings != null) {
                this.botSettings.encode(buf);
            }

            buf.writeBoolean(this.virtualOrderBookSettings != null);
            if (this.virtualOrderBookSettings != null) {
                this.virtualOrderBookSettings.encode(buf);
            }

            buf.writeBoolean(this.isMarketOpen);
            buf.writeLong(this.candleTimeMin);
            buf.writeFloat(this.defaultPrice);
            buf.writeInt(this.priceScaleFactor);
        }


        public static DefaultMarketSetupData decode(FriendlyByteBuf buf) {
            DefaultMarketSetupData data = new DefaultMarketSetupData();
            data.tradingPair.decode(buf);
            boolean hasBotSettings = buf.readBoolean();
            if (hasBotSettings) {
                data.botSettings = new ServerVolatilityBot.Settings();
                data.botSettings.decode(buf);
            } else {
                data.botSettings = null; // No bot settings provided
            }

            boolean hasVirtualOrderBookSettings = buf.readBoolean();
            if (hasVirtualOrderBookSettings) {
                data.virtualOrderBookSettings = new VirtualOrderBook.Settings();
                data.virtualOrderBookSettings.decode(buf);
            } else {
                data.virtualOrderBookSettings = null; // No virtual order book settings provided
            }

            data.isMarketOpen = buf.readBoolean();
            data.candleTimeMin = buf.readLong();
            data.defaultPrice = buf.readFloat();
            data.priceScaleFactor = buf.readInt(); // Read price scale factor
            return data;
        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();

            // Use minimalistic JSON structure for trading pair instead of a full TradingPair object data
            ItemID item = this.tradingPair.getItem();
            ItemID currency = this.tradingPair.getCurrency();
            JsonObject pairData = new JsonObject();
            pairData.add("item", item.toJson());
            pairData.add("currency", currency.toJson());

            jsonObject.add("tradingPair", pairData);

            if (this.botSettings != null) {
                jsonObject.add("botSettings", this.botSettings.toJson());
            }
            if (this.virtualOrderBookSettings != null) {
                jsonObject.add("virtualOrderBookSettings", this.virtualOrderBookSettings.toJson());
            }

            jsonObject.addProperty("isMarketOpen", this.isMarketOpen); // Add market open status
            jsonObject.addProperty("candleTimeMin", this.candleTimeMin); // Add candle time in minutes, default to 1 minute
            if (this.botSettings == null)
                jsonObject.addProperty("defaultPrice", this.defaultPrice); // Add default price, if needed

            jsonObject.addProperty("priceScaleFactor", this.priceScaleFactor); // Add price scale factor

            return jsonObject;
        }
        public boolean fromJson(JsonElement json) {
            if(!json.isJsonObject())
            {
                return false;
            }

            JsonObject jsonObject = json.getAsJsonObject();
            JsonElement tradingPairElement = jsonObject.get("tradingPair");
            JsonElement botSettingsElement = jsonObject.get("botSettings");
            JsonElement virtualOrderBookSettingsElement = jsonObject.get("virtualOrderBookSettings");

            if (    tradingPairElement == null) {
                return false;
            }

            if(tradingPairElement.isJsonObject()) {
                JsonObject pairObject = tradingPairElement.getAsJsonObject();
                JsonElement itemElement = pairObject.get("item");
                JsonElement currencyElement = pairObject.get("currency");

                if(itemElement == null)
                    return false; // Invalid trading pair data


                ItemID item = new ItemID(itemElement);
                if(!item.fromJson(itemElement))
                    return false; // Invalid item data

                ItemID currency = null;
                if(currencyElement == null) {
                    if(BACKEND_INSTANCES.SERVER_MARKET_MANAGER != null)
                        currency = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getDefaultCurrencyItemID();
                    else
                        currency = new ItemID(BankSystemItems.MONEY.get().getDefaultInstance());
                }
                else {
                    currency = new ItemID(currencyElement);
                    if (!currency.fromJson(currencyElement))
                        return false; // Invalid currency data
                }

                this.tradingPair = new TradingPair(item, currency);
            }
            boolean success = true;
            if (botSettingsElement != null && botSettingsElement.isJsonObject()) {
                this.botSettings = new ServerVolatilityBot.Settings();
                success = this.botSettings.fromJson(botSettingsElement);
                this.defaultPrice = this.botSettings.defaultPrice; // Use bot settings default price if available
            } else {
                this.botSettings = null; // No bot settings provided

                // Fallback for default price
                JsonElement defaultPriceElement = jsonObject.get("defaultPrice");
                if (defaultPriceElement != null && defaultPriceElement.isJsonPrimitive() && defaultPriceElement.getAsJsonPrimitive().isNumber()) {
                    this.defaultPrice = Math.max(0, defaultPriceElement.getAsFloat()); // Default to 0 if not specified
                } else {
                    this.defaultPrice = 0; // Default value if not present
                }
            }

            if (virtualOrderBookSettingsElement != null && virtualOrderBookSettingsElement.isJsonObject()) {
                this.virtualOrderBookSettings = new VirtualOrderBook.Settings();
                success &= this.virtualOrderBookSettings.fromJson(virtualOrderBookSettingsElement);
            } else {
                this.virtualOrderBookSettings = null; // No virtual order book settings provided
            }

            JsonElement isMarketOpenElement = jsonObject.get("isMarketOpen");
            if (isMarketOpenElement != null && isMarketOpenElement.isJsonPrimitive() && isMarketOpenElement.getAsJsonPrimitive().isBoolean()) {
                this.isMarketOpen = isMarketOpenElement.getAsBoolean();
            } else {
                if(BACKEND_INSTANCES.SERVER_SETTINGS != null)
                    this.isMarketOpen = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.MARKET_OPEN_AT_CREATION.get(); // Use server settings for market open status
                else
                    this.isMarketOpen = false; // Default to closed market if not specified
            }
            JsonElement candleTimeMinElement = jsonObject.get("candleTimeMin");
            if (candleTimeMinElement != null && candleTimeMinElement.isJsonPrimitive() && candleTimeMinElement.getAsJsonPrimitive().isNumber()) {
                this.candleTimeMin = Math.max(1, candleTimeMinElement.getAsInt()); // Default to 1 minute if not specified
            } else {
                if(BACKEND_INSTANCES.SERVER_SETTINGS != null)
                    this.candleTimeMin = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get()/ 60000; // Use server settings for candle time in minutes
                else
                    this.candleTimeMin = 1; // Default to 1 minute if not specified
            }


            JsonElement priceScaleFactorElement = jsonObject.get("priceScaleFactor");
            if (priceScaleFactorElement != null && priceScaleFactorElement.isJsonPrimitive() && priceScaleFactorElement.getAsJsonPrimitive().isNumber()) {
                this.priceScaleFactor = Math.max(1, priceScaleFactorElement.getAsInt()); // Default to 1 if not specified
            } else {
                this.priceScaleFactor = 1; // Default value if not present
            }


            return success;
        }

        @Override
        public String toString()
        {
            return JsonUtilities.toPrettyString(toJson());
        }
    }

    public static class DefaultMarketSetupDataGroup implements INetworkPayloadEncoder
    {
        public String groupName = "Group";
        public final List<DefaultMarketSetupData> marketSetupDataList = new ArrayList<>();

        private ItemID iconItemID;

        public DefaultMarketSetupDataGroup()
        {

        }
        public DefaultMarketSetupDataGroup(String groupName)
        {
            this.groupName = groupName;
        }
        public DefaultMarketSetupDataGroup(String groupName, ItemStack icon)
        {
            this.groupName = groupName;
            if(icon != null && !icon.isEmpty())
            {
                this.iconItemID = new ItemID(icon);
            }
            else
            {
                this.iconItemID = null; // No icon item ID provided
            }
        }
        public DefaultMarketSetupDataGroup(String groupName, Item icon)
        {
            this.groupName = groupName;
            if(icon != null)
            {
                this.iconItemID = new ItemID(icon.getDefaultInstance());
            }
            else
            {
                this.iconItemID = null; // No icon item ID provided
            }
        }
        public void setIconItem(ItemStack icon)
        {
            if(icon != null && !icon.isEmpty())
            {
                this.iconItemID = new ItemID(icon);
            }
            else
            {
                this.iconItemID = null; // No icon item ID provided
            }
        }
        public void setIconItem(Item icon)
        {
            if(icon != null)
            {
                this.iconItemID = new ItemID(icon.getDefaultInstance());
            }
            else
            {
                this.iconItemID = null; // No icon item ID provided
            }
        }
        public void setIconItemID(ItemID iconItemID)
        {
            this.iconItemID = iconItemID;
        }
        //public DefaultMarketSetupDataGroup(String groupName, List<DefaultMarketSetupData> marketSetupDataList)
        //{
        //    this.groupName = groupName;
        //    this.marketSetupDataList.addAll(marketSetupDataList);
        //}

        public boolean isEmpty()
        {
            return this.marketSetupDataList.isEmpty();
        }
        public int size()
        {
            return this.marketSetupDataList.size();
        }


        public void add(DefaultMarketSetupData data)
        {
            if(data != null)
            {
                /*for(DefaultMarketSetupData existingData : this.marketSetupDataList)
                {
                    if(existingData.tradingPair.equals(data.tradingPair))
                    {
                        warn("Trading pair " + data.tradingPair + " already exists in group " + this.groupName + ", skipping addition.");
                        return;
                    }
                }*/
                this.marketSetupDataList.add(data);
            }
        }
        public void add(DefaultMarketSetupGeneratorData generatorData)
        {
            if(generatorData != null)
            {
                /*for(DefaultMarketSetupData data : this.marketSetupDataList)
                {
                    if(data.tradingPair.equals(generatorData.tradingPair))
                    {
                        warn("Trading pair " + generatorData.tradingPair + " already exists in group " + this.groupName + ", skipping addition.");
                        return;
                    }
                }*/
                DefaultMarketSetupData data = generatorData.generateDefaultMarketSetupData();
                if(data != null)
                {
                    this.marketSetupDataList.add(data);
                }
            }
        }

        public List<DefaultMarketSetupData> get(TradingPair pair)
        {
            List<DefaultMarketSetupData> matchingData = new ArrayList<>();
            for(DefaultMarketSetupData data : this.marketSetupDataList)
            {
                if(data.tradingPair.equals(pair))
                {
                    matchingData.add(data);
                }
            }
            return matchingData;
        }

        public void remove(TradingPair pair)
        {
            for(int i = 0; i < this.marketSetupDataList.size(); i++)
            {
                DefaultMarketSetupData data = this.marketSetupDataList.get(i);
                if(data.tradingPair.equals(pair))
                {
                    this.marketSetupDataList.remove(i);
                }
            }
        }
        public boolean contains(TradingPair pair)
        {
            for(DefaultMarketSetupData data : this.marketSetupDataList)
            {
                if(data.tradingPair.equals(pair))
                {
                    return true;
                }
            }
            return false;
        }
        public @Nullable ItemID getIconItemID()
        {
            return iconItemID;
        }


        public boolean save()
        {
            return BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveDefaultMarketSetupDataGroup(this);
        }
        public boolean saveIfNotExists()
        {
            List<String> groupNames = BACKEND_INSTANCES.SERVER_DATA_HANDLER.getDefaultMarketSetupDataFileNames();
            if(groupNames.contains(this.groupName))
            {
                return false;
            }
            return BACKEND_INSTANCES.SERVER_DATA_HANDLER.saveDefaultMarketSetupDataGroup(this);
        }

        public boolean load()
        {
            DefaultMarketSetupDataGroup loadedGroup = BACKEND_INSTANCES.SERVER_DATA_HANDLER.loadDefaultMarketSetupDataGroup(this.groupName);
            if(loadedGroup != null)
            {
                this.groupName = loadedGroup.groupName;
                this.marketSetupDataList.clear();
                this.marketSetupDataList.addAll(loadedGroup.marketSetupDataList);
                this.iconItemID = loadedGroup.iconItemID; // Load icon item ID if it exists
                return true;
            }
            return false;
        }
        public static DefaultMarketSetupDataGroup load(String groupName)
        {
            DefaultMarketSetupDataGroup group = new DefaultMarketSetupDataGroup(groupName);
            if(group.load())
            {
                return group;
            }
            return null;
        }
        public static List<DefaultMarketSetupDataGroup> loadAll()
        {
            List<DefaultMarketSetupDataGroup> groups = new ArrayList<>();
            List<String> groupNames = BACKEND_INSTANCES.SERVER_DATA_HANDLER.getDefaultMarketSetupDataFileNames();
            for(String groupName : groupNames)
            {
                DefaultMarketSetupDataGroup group = DefaultMarketSetupDataGroup.load(groupName);
                if(group != null)
                {
                    groups.add(group);
                }
            }
            return groups;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(this.groupName, 256); // Write group name with a max length of 256 characters
            buf.writeBoolean(this.iconItemID != null);
            if(this.iconItemID != null) {
                this.iconItemID.encode(buf); // Encode the icon item ID if it exists
            }
            buf.writeInt(this.marketSetupDataList.size()); // Write the size of the market setup data list
            for(DefaultMarketSetupData data : this.marketSetupDataList) {
                if(data != null) {
                    data.encode(buf);
                } else {
                    DefaultMarketSetupData emptyData = new DefaultMarketSetupData();
                    emptyData.encode(buf); // Encode an empty data object if null
                }
            }
        }

        public static DefaultMarketSetupDataGroup decode(FriendlyByteBuf buf) {
            DefaultMarketSetupDataGroup group = new DefaultMarketSetupDataGroup();
            group.groupName = buf.readUtf(256); // Read group name with a max length of 256 characters
            if(buf.readBoolean()) {
                group.iconItemID = new ItemID(buf); // Decode the icon item ID if it exists
            } else {
                group.iconItemID = null; // No icon item ID provided
            }
            int size = buf.readInt(); // Read the size of the market setup data list
            for(int i = 0; i < size; i++) {
                DefaultMarketSetupData data = DefaultMarketSetupData.decode(buf);
                group.marketSetupDataList.add(data);
            }
            return group;
        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("groupName", groupName);

            if(iconItemID != null) {
                jsonObject.add("iconItemID", iconItemID.toJson());
            }

            JsonArray jsonArray = new JsonArray();
            for(DefaultMarketSetupData data : marketSetupDataList)
            {
                JsonElement marketJson = data.toJson();
                if(marketJson != null && marketJson.isJsonObject())
                {
                    jsonArray.add(marketJson);
                }
            }
            jsonObject.add("markets", jsonArray);
            return jsonObject;
        }
        public boolean fromJson(JsonElement json)
        {
            if(!json.isJsonObject())
            {
                return false;
            }
            JsonObject jsonObject = json.getAsJsonObject();
            JsonElement groupNameElement = jsonObject.get("groupName");
            if(groupNameElement != null && groupNameElement.isJsonPrimitive() && groupNameElement.getAsJsonPrimitive().isString())
            {
                this.groupName = groupNameElement.getAsString();
            }

            JsonElement iconItemIDElement = jsonObject.get("iconItemID");
            if(iconItemIDElement != null && iconItemIDElement.isJsonObject())
            {
                this.iconItemID = new ItemID(iconItemIDElement);
                if(!this.iconItemID.fromJson(iconItemIDElement))
                {
                    this.iconItemID = null; // Invalid icon item ID data
                }
            }
            else
            {
                this.iconItemID = null; // No icon item ID provided
            }

            JsonElement marketSetupDataListElement = jsonObject.get("markets");
            if(marketSetupDataListElement != null && marketSetupDataListElement.isJsonArray())
            {
                for(JsonElement marketJson : marketSetupDataListElement.getAsJsonArray())
                {
                    DefaultMarketSetupData data = new DefaultMarketSetupData();
                    if(data.fromJson(marketJson))
                    {
                        this.marketSetupDataList.add(data);
                    }
                }
            }
            return true;
        }





        @Override
        public String toString()
        {
            return JsonUtilities.toPrettyString(toJson());
        }
    }


    /**
     * Calculates a new default price, given its old default price.
     * The factors can change the function of the price calculation.
     * @param inputPrice        The old default price, which is >= 0
     * @param linearFactor      A factor that is multiplied with the input price
     * @param quadraticFactor   A factor that is multiplied with the square of the input price
     * @param exponentialFactor A factor that is used in an exponential function with the input price
     * @return The adjusted price, which is always >= 0
     */
    public static int getAjustedPrice(int inputPrice, float linearFactor, float quadraticFactor, float exponentialFactor)
    {
        return Math.round(getAjustedPriceF((float)inputPrice, linearFactor, quadraticFactor, exponentialFactor));
    }
    public static float getAjustedPriceF(float inputPrice, float linearFactor, float quadraticFactor, float exponentialFactor)
    {
        float inpFloat = Math.max(0.f, inputPrice);
        float adjustedPrice = inpFloat * linearFactor + inpFloat * inpFloat * quadraticFactor + (float) Math.exp(inpFloat * exponentialFactor)-1.f;
        return Math.max(0, adjustedPrice);
    }


    public static @Nullable ServerMarket createMarket(DefaultMarketSetupData data)
    {
        if(data == null)
        {
            error("DefaultMarketSetupData is null, can't create market");
            return null;
        }
        TradingPair pair = data.tradingPair;
        if(!isTradingPairAllowedForTrading(pair))
        {
            error("Trading pair " + pair + " is not allowed for trading");
            return null;
        }

        int virtualOrderBookSize = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get();
        if(data.virtualOrderBookSettings == null)
        {
            virtualOrderBookSize = 0;
        }
        float defaultPrice = data.defaultPrice;
        if(data.botSettings != null)
            defaultPrice = data.botSettings.defaultPrice; // Use bot settings default price if available
        ServerMarket market = createMarket_internal(pair, virtualOrderBookSize, defaultPrice, data.priceScaleFactor);
        if(market != null) {
            if(data.botSettings != null)
                market.createVolatilityBot(data.botSettings);
            if(data.virtualOrderBookSettings != null) {
                market.setVirtualOrderBookSettings(data.virtualOrderBookSettings);
                market.resetVirtualOrderBookVolumeDistribution(); // Reset the volume distribution to ensure it starts fresh
            }
            market.setShiftPriceCandleIntervalMS(data.candleTimeMin * 60 * 1000); // Convert minutes to milliseconds
            market.setMarketOpen(data.isMarketOpen);
        }
        return market;
    }
    public static List<ServerMarket> createMarkets(DefaultMarketSetupDataGroup group)
    {
        if(group == null)
        {
            error("DefaultMarketSetupDataGroup is null, can't create markets");
            return new ArrayList<>();
        }
        List<ServerMarket> markets = new ArrayList<>();
        for(DefaultMarketSetupData data : group.marketSetupDataList)
        {
            ServerMarket market = createMarket(data);
            if(market != null)
            {
                markets.add(market);
            }
        }
        return markets;
    }



    public static @Nullable ServerMarket createMarket(@NotNull TradingPair pair, float startPrice)
    {
        if(!isTradingPairAllowedForTrading(pair))
        {
            error("Trading pair " + pair + " is not allowed for trading");
            return null;
        }
        return createMarket_internal(pair, BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.VIRTUAL_ORDERBOOK_ARRAY_SIZE.get(),  startPrice, 1);
    }
    public static @Nullable ServerMarket createMarket(@NotNull ItemID itemID, @NotNull ItemID currency, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, currency);
        return createMarket(tradingPair, startPrice);
    }
    public static @Nullable ServerMarket createMarket(@NotNull ItemID itemID, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getDefaultCurrencyItemID());
        return createMarket(tradingPair, startPrice);
    }
    public static @Nullable ServerMarket createMarket(@NotNull ServerMarketSettingsData settingsData)
    {
        TradingPair pair = settingsData.tradingPairData.toTradingPair();
        float startPrice = 0;
        if(settingsData.botSettingsData != null) {
            startPrice = settingsData.botSettingsData.settings.defaultPrice;
        }
        ServerMarket market = createMarket(pair, startPrice);
        if(market != null) {
            if(settingsData.botSettingsData != null)
            {
                ServerVolatilityBot.Settings botSettings = settingsData.botSettingsData.settings;
                market.createVolatilityBot(botSettings);
            }
            if(settingsData.virtualOrderBookSettingsData != null)
            {
                VirtualOrderBook.Settings virtualOrderBookSettings = settingsData.virtualOrderBookSettingsData.settings;
                market.setVirtualOrderBookSettings(virtualOrderBookSettings);
            }
        }
        return market;
    }

    public static List<ServerMarket> createMarkets(@NotNull List<ServerMarketSettingsData> settingsDataList)
    {
        List<ServerMarket> markets = new ArrayList<>();
        for(ServerMarketSettingsData settingsData : settingsDataList)
        {
            ServerMarket market = createMarket(settingsData);
            if(market != null)
            {
                markets.add(market);
            }
        }
        return markets;
    }



    private static @Nullable ServerMarket createMarket_internal(TradingPair pair, int virtualOrderBookSize, float startPrice, int priceScaleFactor)
    {
        ItemID item = pair.getItem();
        ItemID currency = pair.getCurrency();
        IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager();
        if(!bankManager.isItemIDAllowed(item))
        {
            if(!bankManager.allowItemID(item, 1)){
                error("Pair: " + pair + " can't be allowed for trading because the item: "+ item +" is not allowed in the bank system");
                return null;
            }
        }
        if(!bankManager.isItemIDAllowed(currency))
        {
            if(!bankManager.allowItemID(currency, priceScaleFactor)){
                error("Pair: " + pair + " can't be allowed for trading because the currency: "+ currency+" is not allowed in the bank system");
                return null;
            }
        }

        // Adjust the priceScaleFactor to match the minimum of the bank system's scale factor and the provided scale factor
        int currencyItemScaleFactor = bankManager.getItemFractionScaleFactor(currency);
        if(priceScaleFactor > currencyItemScaleFactor)
        {
            warn("The provided price scale factor " + priceScaleFactor + " is greater than the bank system's scale factor " + currencyItemScaleFactor +
                    " for currency " + currency + ". Adjusting to match the bank system's scale factor.");
            priceScaleFactor = currencyItemScaleFactor;
        }

        return new ServerMarket(pair,
                startPrice,
                virtualOrderBookSize,
                BACKEND_INSTANCES.SERVER_SETTINGS.UI.PRICE_HISTORY_SIZE.get(),
                priceScaleFactor);
    }



    public static boolean isTradingPairAllowedForTrading(TradingPair pair)
    {
        pair.checkValidity(BACKEND_INSTANCES.SERVER_SETTINGS.MARKET.getNotTradableItems());
        return pair.isValid();
    }
    protected static void info(String msg)
    {
        BACKEND_INSTANCES.LOGGER.info("[MarketFactory] " + msg);
    }
    protected static void error(String msg)
    {
        BACKEND_INSTANCES.LOGGER.error("[MarketFactory] " + msg);
    }
    protected static void error(String msg, Throwable e)
    {
        BACKEND_INSTANCES.LOGGER.error("[MarketFactory] " + msg, e);
    }
    protected static void warn(String msg)
    {
        BACKEND_INSTANCES.LOGGER.warn("[MarketFactory] " + msg);
    }
    protected static void debug(String msg)
    {
        BACKEND_INSTANCES.LOGGER.debug("[MarketFactory] " + msg);
    }

}
