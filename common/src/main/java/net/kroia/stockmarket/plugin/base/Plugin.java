package net.kroia.stockmarket.plugin.base;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public abstract class Plugin {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
    }
    private static final class KEY
    {
        public static final String PLUGIN_DATA = "plugin_data";
        public static final String CUSTOM_DATA = "custom_data";
        public static final String PLUGIN_TYPE_ID = "typeID";
        public static final String TRADING_PAIR = "trading_pair";
        public static final String SETTINGS = "settings";
    }

    public static final class Settings implements IPluginSettings
    {
        private static final class KEY
        {
            public static final String PLUGIN_NAME = "name";
            public static final String LOGGER_ENABLED = "logger_enabled";
            public static final String PLUGIN_ENABLED = "enabled";
        }
        public String name;
        public boolean loggerEnabled = false;
        public boolean pluginEnabled = true;


        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(name);
            buf.writeBoolean(loggerEnabled);
            buf.writeBoolean(pluginEnabled);
        }
        @Override
        public void decode(FriendlyByteBuf buf)
        {
            name = buf.readUtf();
            loggerEnabled = buf.readBoolean();
            pluginEnabled = buf.readBoolean();
        }
        public static Settings create(FriendlyByteBuf buf)
        {
            Settings settings = new Settings();
            settings.name = buf.readUtf();
            settings.loggerEnabled = buf.readBoolean();
            settings.pluginEnabled = buf.readBoolean();
            return settings;
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putString(KEY.PLUGIN_NAME, name);
            tag.putBoolean(KEY.LOGGER_ENABLED, loggerEnabled);
            tag.putBoolean(KEY.PLUGIN_ENABLED, pluginEnabled);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            name = tag.getString(KEY.PLUGIN_NAME);
            loggerEnabled = tag.getBoolean(KEY.LOGGER_ENABLED);
            pluginEnabled = tag.getBoolean(KEY.PLUGIN_ENABLED);
            return true;
        }
    }



    private Settings settings;
    private TradingPair tradingPair;
    private String pluginTypeID;


    public Plugin() {
        this.settings = new Settings();
    }

    /**
     * Gets called when a plugin gets created (client and server side)
     */
    protected abstract void setup();

    /**
     * Update call for the plugin
     */
    protected abstract void update();


    protected abstract void encodeSettings(FriendlyByteBuf buf);
    protected abstract void decodeSettings(FriendlyByteBuf buf);


    protected abstract boolean saveToFilesystem(CompoundTag tag);
    protected abstract boolean loadFromFilesystem(CompoundTag tag);




    public final TradingPair getTradingPair() {
        return tradingPair;
    }
    public final void setTradingPair(TradingPair tradingPair) {
        this.tradingPair = tradingPair;
    }
    public final String getPluginTypeID() {
        return pluginTypeID;
    }
    public final void setPluginTypeID(String pluginTypeID) {
        this.pluginTypeID = pluginTypeID;
    }


    public final void setName(String name)
    {
        this.settings.name = name;
    }
    public final String getName() {
        return settings.name;
    }
    public final void setLoggerEnabled(boolean loggerEnabled) {
        this.settings.loggerEnabled = loggerEnabled;
    }
    public final boolean isLoggerEnabled() {
        return settings.loggerEnabled;
    }
    public final void setPluginEnabled(boolean pluginEnabled) {
        this.settings.pluginEnabled = pluginEnabled;
    }
    public final boolean isPluginEnabled() {
        return settings.pluginEnabled;
    }

    public final void setSettings(Settings settings) {
        if(settings != null)
            this.settings = settings;
    }
    public final Settings getSettings() {
        return settings;
    }






    public final void setup_interal()
    {
        setup();
    }
    public final void update_internal()
    {
        update();
    }


    public final void encodeSettings_internal(FriendlyByteBuf buf)
    {
        settings.encode(buf);
        encodeSettings(buf);
    }
    public final void decodeSettings_internal(FriendlyByteBuf buf)
    {
        settings = Settings.create(buf);
        decodeSettings(buf);
    }



    // For saving to disk
    public final boolean saveToFilesystem_internal(CompoundTag tag) {

        CompoundTag pluginDataTag = new CompoundTag();
        CompoundTag settingsTag = new CompoundTag();
        settings.save(settingsTag);
        pluginDataTag.put(KEY.SETTINGS, settingsTag);
        CompoundTag tradingPairTag = new CompoundTag();
        tradingPair.save(tradingPairTag);
        pluginDataTag.put(KEY.TRADING_PAIR, tradingPairTag);
        pluginDataTag.putString(KEY.PLUGIN_TYPE_ID, pluginTypeID);
        tag.put(KEY.PLUGIN_DATA, pluginDataTag);



        CompoundTag customDataTag = new CompoundTag();
        if(saveToFilesystem(customDataTag))
        {
            tag.put(KEY.CUSTOM_DATA, customDataTag);
            return true;
        }
        error("Failed to save custom plugin data for plugin: " + this);
        return false;
    }

    // For loading from disk
    public final boolean loadFromFilesystem_internal(CompoundTag tag) {

        if(!tag.contains(KEY.PLUGIN_DATA) ||
           !tag.contains(KEY.CUSTOM_DATA))
        {
            error("Missing required keys in the saved plugin data!");
            return false;
        }

        CompoundTag pluginDataTag = tag.getCompound(KEY.PLUGIN_DATA);
        CompoundTag customDataTag = tag.getCompound(KEY.CUSTOM_DATA);


        if(!pluginDataTag.contains(KEY.TRADING_PAIR) ||
           !pluginDataTag.contains(KEY.SETTINGS) ||
           !pluginDataTag.contains(KEY.PLUGIN_TYPE_ID))
        {
            error("Missing required keys in the saved plugin data!");
            return false;
        }


        CompoundTag tradingPairTag = pluginDataTag.getCompound(KEY.TRADING_PAIR);
        TradingPair pair = new TradingPair(tradingPairTag);
        if(!pair.equals(this.tradingPair)) {
            error("Loaded trading pair does not match the current trading pair! Loaded: " + pair.getUltraShortDescription() + ", Current: " + this.tradingPair.getUltraShortDescription());
            return false;

        }
        CompoundTag settingsTag = pluginDataTag.getCompound(KEY.SETTINGS);
        settings.load(settingsTag);
        pluginTypeID = pluginDataTag.getString(KEY.PLUGIN_TYPE_ID);

        return loadFromFilesystem(customDataTag);
    }


    @Override
    public String toString() {
        return "Plugin{" +
                "pluginTypeID='" + pluginTypeID + '\'' +
                ", tradingPair=" + (tradingPair != null ? tradingPair.getUltraShortDescription() : "null") +
                '}';
    }


    protected final void info(String msg)
    {
        if(settings.loggerEnabled)
            BACKEND_INSTANCES.LOGGER.info(getLogPrefix() + msg);
    }
    protected final void error(String msg)
    {
        if(settings.loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg);
    }
    protected final void error(String msg, Throwable e)
    {
        if(settings.loggerEnabled)
            BACKEND_INSTANCES.LOGGER.error(getLogPrefix() + msg, e);
    }
    protected final void warn(String msg)
    {
        if(settings.loggerEnabled)
            BACKEND_INSTANCES.LOGGER.warn(getLogPrefix() + msg);
    }
    protected final void debug(String msg)
    {
        if(settings.loggerEnabled)
            BACKEND_INSTANCES.LOGGER.debug(getLogPrefix() + msg);
    }
    private String getLogPrefix() {
        return "[MarketPlugin: "+settings.name+"("+ tradingPair.getUltraShortDescription() + ")] ";
    }

}
