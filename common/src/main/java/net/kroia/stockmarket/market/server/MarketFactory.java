package net.kroia.stockmarket.market.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.api.IServerBankManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        public int defaultPrice;
        public float rarity;
        public float volatility;
        public long updateIntervalMS = 500; // Default update interval in milliseconds
        public boolean enableTargetPrice = true;
        public boolean enableVolumeTracking = true;
        public boolean enableRandomWalk = true;

        public DefaultMarketSetupGeneratorData(ItemStack itemStack, int defaultPrice, float rarity, float volatility) {
            this.tradingPair = new TradingPair(new ItemID(itemStack), BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
        public DefaultMarketSetupGeneratorData(Item item, int defaultPrice, float rarity, float volatility) {
            this.tradingPair = new TradingPair(new ItemID(item.getDefaultInstance()), BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
            this.defaultPrice = defaultPrice;
            this.rarity = rarity;
            this.volatility = volatility;
        }
        public DefaultMarketSetupGeneratorData(TradingPair tradingPair, int defaultPrice, float rarity, float volatility) {
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
            botSettings.volatility = Math.abs(volatility);

            virtualOrderBookSettings.volumeScale = 100f/(0.01f+Math.abs(rarity));
            botSettings.volumeScale = virtualOrderBookSettings.volumeScale * this.volatility/10;

            return new DefaultMarketSetupData(this.tradingPair, botSettings, virtualOrderBookSettings);
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            this.tradingPair.decode(buf);
            this.defaultPrice = buf.readInt();
            this.rarity = buf.readFloat();
            this.volatility = buf.readFloat();
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            this.tradingPair.encode(buf);
            buf.writeInt(this.defaultPrice);
            buf.writeFloat(this.rarity);
            buf.writeFloat(this.volatility);
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
                this.defaultPrice = Math.max(0, element.getAsInt());
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
        public TradingPair tradingPair;
        public ServerVolatilityBot.Settings botSettings;
        public VirtualOrderBook.Settings virtualOrderBookSettings;


        public DefaultMarketSetupData(TradingPair pair) {
            this.tradingPair = pair;
            this.botSettings = null; // Default to no bot settings
        }
        public DefaultMarketSetupData(TradingPair pair,
                                      ServerVolatilityBot.Settings botSettings,
                                      VirtualOrderBook.Settings virtualOrderBookSettings) {
            this.tradingPair = pair;
            this.botSettings = botSettings;
            this.virtualOrderBookSettings = virtualOrderBookSettings;
        }
        private DefaultMarketSetupData() {
            this.tradingPair = new TradingPair();
            this.botSettings = null; // Default to no bot settings
            this.virtualOrderBookSettings = null; // Default to no virtual order book settings
        }

        public static DefaultMarketSetupData create(FriendlyByteBuf buf) {
            DefaultMarketSetupData data = new DefaultMarketSetupData();
            data.decode(buf);

            return data;
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

            if (tradingPairElement == null) {
                return false;
            }

            if(tradingPairElement.isJsonObject()) {
                JsonObject pairObject = tradingPairElement.getAsJsonObject();
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
            boolean success = true;
            if (botSettingsElement != null && botSettingsElement.isJsonObject()) {
                this.botSettings = new ServerVolatilityBot.Settings();
                success = this.botSettings.fromJson(botSettingsElement);
            } else {
                this.botSettings = null; // No bot settings provided
            }

            if (virtualOrderBookSettingsElement != null && virtualOrderBookSettingsElement.isJsonObject()) {
                this.virtualOrderBookSettings = new VirtualOrderBook.Settings();
                success &= this.virtualOrderBookSettings.fromJson(virtualOrderBookSettingsElement);
            } else {
                this.virtualOrderBookSettings = null; // No virtual order book settings provided
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

        public DefaultMarketSetupDataGroup()
        {

        }
        public DefaultMarketSetupDataGroup(String groupName)
        {
            this.groupName = groupName;
        }
        public DefaultMarketSetupDataGroup(String groupName, List<DefaultMarketSetupData> marketSetupDataList)
        {
            this.groupName = groupName;
            this.marketSetupDataList.addAll(marketSetupDataList);
        }

        public void add(DefaultMarketSetupData data)
        {
            if(data != null)
            {
                this.marketSetupDataList.add(data);
            }
        }
        public void add(DefaultMarketSetupGeneratorData generatorData)
        {
            if(generatorData != null)
            {
                for(DefaultMarketSetupData data : this.marketSetupDataList)
                {
                    if(data.tradingPair.equals(generatorData.tradingPair))
                    {
                        return;
                    }
                }
                DefaultMarketSetupData data = generatorData.generateDefaultMarketSetupData();
                if(data != null)
                {
                    this.marketSetupDataList.add(data);
                }
            }
        }

        public @Nullable DefaultMarketSetupData get(TradingPair pair)
        {
            for(DefaultMarketSetupData data : this.marketSetupDataList)
            {
                if(data.tradingPair.equals(pair))
                {
                    return data;
                }
            }
            return null;
        }

        public void remove(TradingPair pair)
        {
            for(int i = 0; i < this.marketSetupDataList.size(); i++)
            {
                DefaultMarketSetupData data = this.marketSetupDataList.get(i);
                if(data.tradingPair.equals(pair))
                {
                    this.marketSetupDataList.remove(i);
                    return;
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
            Map<TradingPair, Boolean> alreadyInList = new HashMap<>();
            List<DefaultMarketSetupDataGroup> groups = new ArrayList<>();
            List<String> groupNames = BACKEND_INSTANCES.SERVER_DATA_HANDLER.getDefaultMarketSetupDataFileNames();
            for(String groupName : groupNames)
            {
                DefaultMarketSetupDataGroup group = DefaultMarketSetupDataGroup.load(groupName);
                if(group != null)
                {
                    List<TradingPair> toRemove = new ArrayList<>();
                    for(DefaultMarketSetupData data : group.marketSetupDataList)
                    {
                        if(!alreadyInList.containsKey(data.tradingPair))
                        {
                            alreadyInList.put(data.tradingPair, true);
                        }
                        else
                        {
                            toRemove.add(data.tradingPair);
                        }
                    }
                    for(TradingPair pair : toRemove)
                    {
                        group.remove(pair);
                    }
                    groups.add(group);
                }
            }
            return groups;
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(this.groupName, 256); // Write group name with a max length of 256 characters
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
            int size = buf.readInt(); // Read the size of the market setup data list
            for(int i = 0; i < size; i++) {
                DefaultMarketSetupData data = DefaultMarketSetupData.decode(buf);
                // Check if the trading pair already exists in the list
                boolean pairExists = false;
                for(DefaultMarketSetupData existingData : group.marketSetupDataList)
                {
                    if(existingData.tradingPair.equals(data.tradingPair))
                    {
                        pairExists = true;
                        break;
                    }
                }
                if(!pairExists)
                    group.marketSetupDataList.add(data);
            }
            return group;
        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("groupName", groupName);
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
            JsonElement marketSetupDataListElement = jsonObject.get("markets");
            if(marketSetupDataListElement != null && marketSetupDataListElement.isJsonArray())
            {
                for(JsonElement marketJson : marketSetupDataListElement.getAsJsonArray())
                {
                    DefaultMarketSetupData data = new DefaultMarketSetupData();
                    if(data.fromJson(marketJson))
                    {
                        // Check if pair already exists in the list
                        boolean pairExists = false;
                        for(DefaultMarketSetupData existingData : this.marketSetupDataList)
                        {
                            if(existingData.tradingPair.equals(data.tradingPair))
                            {
                                pairExists = true;
                                break;
                            }
                        }
                        if(!pairExists)
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
        if(data.botSettings != null) {
            return createMarket(pair, data.botSettings, data.virtualOrderBookSettings);
        }
        return createMarket(pair, 0);
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



    public static @Nullable ServerMarket createMarket(@NotNull TradingPair pair, int startPrice)
    {
        if(!isTradingPairAllowedForTrading(pair))
        {
            error("Trading pair " + pair + " is not allowed for trading");
            return null;
        }
        return createMarket_internal(pair, startPrice);
    }
    public static @Nullable ServerMarket createMarket(@NotNull TradingPair pair,
                                                      @NotNull ServerVolatilityBot.Settings botSettings,
                                                      @NotNull VirtualOrderBook.Settings virtualOrderBookSettings)
    {
        if(!isTradingPairAllowedForTrading(pair))
        {
            error("Trading pair " + pair + " is not allowed for trading");
            return null;
        }
        ServerMarket market = createMarket_internal(pair, botSettings.defaultPrice);
        if(market != null) {
            market.createVolatilityBot(botSettings);
            market.setVirtualOrderBookSettings(virtualOrderBookSettings);
        }
        return market;
    }
    public static @Nullable ServerMarket createMarket(@NotNull ItemID itemID, @NotNull ItemID currency, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, currency);
        return createMarket(tradingPair, startPrice);
    }
    public static @Nullable ServerMarket createMarket(@NotNull ItemID itemID, int startPrice)
    {
        TradingPair tradingPair = new TradingPair(itemID, BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getDefaultCurrencyItemID());
        return createMarket(tradingPair, startPrice);
    }
    public static @Nullable ServerMarket createMarket(@NotNull ServerMarketSettingsData settingsData)
    {
        TradingPair pair = settingsData.tradingPairData.toTradingPair();
        int startPrice = 0;
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



    private static @Nullable ServerMarket createMarket_internal(TradingPair pair, int startPrice)
    {
        ItemID item = pair.getItem();
        ItemID currency = pair.getCurrency();
        IServerBankManager bankManager = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager();
        if(!bankManager.isItemIDAllowed(item))
        {
            if(!bankManager.allowItemID(item)){
                error("Pair: " + pair + " can't be allowed for trading because the item: "+ item +" is not allowed in the bank system");
                return null;
            }
        }
        if(!bankManager.isItemIDAllowed(currency))
        {
            if(!bankManager.allowItemID(currency)){
                error("Pair: " + pair + " can't be allowed for trading because the currency: "+ currency+" is not allowed in the bank system");
                return null;
            }
        }
        return new ServerMarket(pair, startPrice);
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
