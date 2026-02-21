package net.kroia.stockmarket.plugin.base;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public abstract class MarketPlugin extends Plugin {


    protected IMarketPluginInterface pluginInterface = null;
    private IPluginSettings customPluginSettings = null;
    //public MarketPlugin(IMarketPluginInterface pluginInterface, IPluginSettings settings) {}
    public MarketPlugin() {
        super();
    }
    public final void setInterface(IMarketPluginInterface market)
    {
        this.pluginInterface = market;
        this.setTradingPair(market.getTradingPair());
    }

    /**
     * Set the custom settings instance to this super class in order to automatically handle:
     * - encodeSettings()
     * - decodeSettings()
     * - saveToFilesystem()
     * - loadFromFilesystem()
     * @param settings instance, private member instance of the derived class
     */
    public final void setCustomSettings(IPluginSettings settings)
    {
        this.customPluginSettings = settings;
    }

    /**
     * Gets the interface to access the stock-market
     * @return Market interface
     * @apiNote Do not access the plugin interface in the constructor,
     *          it is only available for the setup() and after that in the update()
     */
    public IMarketPluginInterface getPluginInterface() {
        return pluginInterface;
    }


    /**
     * Encode live data that gets sent to the client
     * @param buf The buffer to write to
     */
    public abstract void encodeClientStreamData(FriendlyByteBuf buf);

    /**
     * Encodes the settings to a ByteBuffer that gets sent to the client
     * This is used to display the current settings to the management UI
     * @param buf to hold the data
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        customPluginSettings.encode(buf);
    }


    /**
     * Decodes the settings from a ByteBuffer received from the client
     * Changed settings from the management UI are received here
     * @param buf containing the data
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        customPluginSettings.decode(buf);
    }


    /**
     * Saves the custom data from the derived plugin implementation.
     * This can be the settings like in this implementation, and or runtime data.
     * Overwrite this methode to save custom runtime data
     * @param tag to store the data to
     * @return true if it was successfully, otherwise false
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected boolean saveToFilesystem(CompoundTag tag) {
        return customPluginSettings.save(tag);
    }

    /**
     * Loads the custom data for the derived plugin implementation.
     * This can be the settings like in this implementation, and or runtime data.
     * Overwrite this methode to load custom runtime data
     * @param tag to read the data from
     * @return true if it was successfully, otherwise false
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected boolean loadFromFilesystem(CompoundTag tag) {
        return customPluginSettings.load(tag);
    }

    /*public static MarketPlugin createFromBuf(FriendlyByteBuf buf)
    {
        String typeID = buf.readUtf();
        MarketPlugin plugin = PluginRegistry.createServerPluginInstance(typeID);
        if(plugin == null)
        {
            BACKEND_INSTANCES.LOGGER.error("Failed to create MarketPlugin instance from buffer: unknown type ID " + typeID+
                    ".\nMake sure the plugin is registered on both client and server.");
            return null;
        }
        try{
            plugin.decode(buf);
        }catch(Exception e)
        {
            BACKEND_INSTANCES.LOGGER.error("Failed to decode MarketPlugin instance from buffer: " + typeID, e);
            return null;
        }
        return plugin;
    }*/





















}
