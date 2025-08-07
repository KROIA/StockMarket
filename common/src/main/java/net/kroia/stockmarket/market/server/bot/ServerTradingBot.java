package net.kroia.stockmarket.market.server.bot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;

/**
 * The ServerTradingBot simulates buy and sell orders to generate a movement in the market if no players are trading
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {
    protected static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }

    public static class Settings implements ServerSaveable, INetworkPayloadConverter
    {
        public boolean enabled = true;
        public long updateTimerIntervallMS;
        public int defaultPrice;
        public Settings()
        {
            if(BACKEND_INSTANCES.SERVER_SETTINGS != null)
                updateTimerIntervallMS = BACKEND_INSTANCES.SERVER_SETTINGS.MARKET_BOT.UPDATE_TIMER_INTERVAL_MS.get();
            else
                updateTimerIntervallMS = 500; // Default to 1 second if not set
        }

        public Settings(Settings other)
        {
            this.enabled = other.enabled;
            this.defaultPrice = other.defaultPrice;
            this.updateTimerIntervallMS = other.updateTimerIntervallMS;
        }
        @Override
        public boolean save(CompoundTag tag) {
            tag.putBoolean("enabled", enabled);
            tag.putInt("defaultPrice", defaultPrice);
            tag.putLong("updateTimerIntervallMS", updateTimerIntervallMS);
            return false;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            if(!tag.contains("enabled") ||
                    !tag.contains("defaultPrice") ||
                    !tag.contains("updateTimerIntervallMS"))
                return false;

            enabled = tag.getBoolean("enabled");
            defaultPrice = tag.getInt("defaultPrice");
            updateTimerIntervallMS = tag.getLong("updateTimerIntervallMS");
            return true;
        }

        public void copyFrom(Settings settings)
        {
            this.enabled = settings.enabled;
            this.defaultPrice = settings.defaultPrice;
            this.updateTimerIntervallMS = settings.updateTimerIntervallMS;
        }


        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(this.enabled);
            buf.writeInt(this.defaultPrice);
            buf.writeLong(this.updateTimerIntervallMS);
        }
        @Override
        public void decode(FriendlyByteBuf buf) {
            this.enabled = buf.readBoolean();
            this.defaultPrice = buf.readInt();
            this.updateTimerIntervallMS = buf.readLong();
        }

        public JsonElement toJson()
        {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("enabled", enabled);
            jsonObject.addProperty("defaultPrice", defaultPrice);
            jsonObject.addProperty("updateTimerIntervallMS", updateTimerIntervallMS);
            return jsonObject;
        }

        public boolean fromJson(JsonElement json) {
            if (json == null || !json.isJsonObject()) {
                return false; // Invalid JSON
            }
            JsonObject jsonObject = json.getAsJsonObject();
            JsonElement element = jsonObject.get("enabled");
            if(element != null && !element.isJsonPrimitive()) 
                this.enabled = element.getAsBoolean();

            element = jsonObject.get("defaultPrice");
            if(element != null && element.isJsonPrimitive()) {
                this.defaultPrice = element.getAsInt();
                if(this.defaultPrice < 0) {
                    this.defaultPrice = 0; // Ensure default price is non-negative
                }
            }


            return true;
        }


    }

    private final ServerMarket serverMarket;

    protected Settings settings;
    //private MatchingEngine matchingEngine;
    //TradeManager parent;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();
    private long[] tmp_load_buyOrderIDs = null;
    private long[] tmp_load_sellOrderIDs = null;


    private long lastMillis = 0;

    public ServerTradingBot(ServerMarket serverMarket) {
        this.serverMarket = serverMarket;
        settings = new Settings();
    }
    protected ServerTradingBot(ServerMarket market, Settings settings) {
        this.serverMarket = market;
        this.settings = settings;
    }

    public void setSettings(Settings settings)
    {
        this.settings = settings;
    }
    public Settings getSettings()
    {
        return this.settings;
    }

    protected void update()
    {
        //clearOrders();
        createOrders();
    }

    public void setUpdateInterval(long intervalMillis)
    {
        this.settings.updateTimerIntervallMS = intervalMillis;
    }
    public long getUpdateInterval()
    {
        return this.settings.updateTimerIntervallMS;
    }
    public void setEnabled(boolean enabled)
    {
        this.settings.enabled = enabled;
    }
    public boolean isEnabled()
    {
        return this.settings.enabled;
    }



    public void clearOrders()
    {
        serverMarket.cancelAllBotOrders();
    }

    protected void createOrders()
    {

    }

    protected long getOrderBookVolume(int price)
    {
        return serverMarket.getOrderBook().getVolume(price, serverMarket.getCurrentPrice());
    }
    protected long getOrderBookVolume(int minPrice, int maxPrice)
    {
        return serverMarket.getOrderBook().getVolumeInRange(minPrice, maxPrice);
    }

    protected int getCurrentPrice()
    {
        return serverMarket.getCurrentPrice();
    }
    protected long getItemImbalance()
    {
        return serverMarket.getItemImbalance();
    }

    protected ItemID getCurrencyItemID()
    {
        return serverMarket.getTradingPair().getCurrency();
    }

    protected boolean buyLimit(long volume, int price)
    {
        if(volume <= 0 || price < 0)
            return false;

        return serverMarket.createBotLimitOrder(volume, price);
    }
    protected boolean sellLimit(long volume, int price)
    {
        if(volume <= 0 || price < 0)
            return false;
        return serverMarket.createBotLimitOrder(-volume, price);
    }
    protected boolean limitTrade(long volume, int price)
    {
        if(volume == 0 || price < 0)
            return false;
        return serverMarket.createBotLimitOrder(volume, price);
    }
    protected boolean buyMarket(long volume)
    {
        if(volume <= 0)
            return false;
        return serverMarket.createBotMarketOrder(volume);
    }
    protected boolean sellMarket(long volume)
    {
        if(volume <= 0)
            return false;
        return serverMarket.createBotMarketOrder(-volume);
    }
    protected boolean marketTrade(long volume)
    {
        if(volume == 0)
            return false;
        return serverMarket.createBotMarketOrder(volume);
    }

    @Override
    public boolean save(CompoundTag tag) {
        CompoundTag settingsTag = new CompoundTag();
        settings.save(settingsTag);
        tag.putString("class", this.getClass().getSimpleName());
        tag.put("settings", settingsTag);

        // Save order ids
        long[] buyOrderIDs = new long[buyOrders.size()];
        for(int i=0; i<buyOrders.size(); i++)
        {
            buyOrderIDs[i] = buyOrders.get(i).getOrderID();
        }
        tag.putLongArray("buyOrderIDs", buyOrderIDs);

        long[] sellOrderIDs = new long[sellOrders.size()];
        for(int i=0; i<sellOrders.size(); i++)
        {
            sellOrderIDs[i] = sellOrders.get(i).getOrderID();
        }
        tag.putLongArray("sellOrderIDs", sellOrderIDs);

        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {

        boolean success = true;
        if(tag == null)
            return false;
        if(!tag.contains("settings"))
            return false;
        CompoundTag settingsTag = tag.getCompound("settings");
        success &= settings.load(settingsTag);

        if(tag.contains("buyOrderIDs"))
            tmp_load_buyOrderIDs = tag.getLongArray("buyOrderIDs");
        if(tag.contains("sellOrderIDs"))
            tmp_load_sellOrderIDs = tag.getLongArray("sellOrderIDs");

        setSettings(settings);
        return success;
    }

    public void update(MinecraftServer server) {
        if(!this.settings.enabled)
            return;
        long currentTime = System.currentTimeMillis();

        if(currentTime - lastMillis > this.settings.updateTimerIntervallMS) {
            lastMillis = currentTime;
            update();
        }
    }
}
