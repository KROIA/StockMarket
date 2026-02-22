package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.plugin.networking.MarketPluginNetworkStream;
import net.kroia.stockmarket.plugin.networking.MarketPluginSettingsRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public abstract class ClientMarketPlugin extends Plugin{
    private UUID streamID = null;
    private ClientMarketPluginGuiElement customSettingsGuiElement = null;
    private IPluginSettings customPluginSettings = null;

    public ClientMarketPlugin(TradingPair tradingPair,String pluginTypeID) {
        super();
        setTradingPair(tradingPair);
        setPluginTypeID(pluginTypeID);
        //loadSettings();
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

    public final void setCustomSettingsGuiElement(ClientMarketPluginGuiElement guiElement)
    {
        this.customSettingsGuiElement = guiElement;
    }

    protected abstract void close();

    public final void startStream()
    {
        if(streamID != null)
        {
            warn("ClientMarketPlugin: Starting stream when one is already active. Stopping the old stream first. Plugin: "+this);
            stopStream();
        }
        MarketPluginNetworkStream.StartData startData = new MarketPluginNetworkStream.StartData(getTradingPair(), getPluginTypeID());
        streamID = StreamSystem.startServerToClientStream(StockMarketNetworking.MARKET_PLUGIN_NETWORK_STREAM, startData, this::onStreamPacketReceived_internal,
                this::onStreamClosed);
    }


    protected abstract void onStreamPacketReceived(FriendlyByteBuf buf);


    public final void loadSettings()
    {
        MarketPluginSettingsRequest.Input input = new MarketPluginSettingsRequest.Input(getTradingPair(), getPluginTypeID(), null);
        StockMarketNetworking.MARKET_PLUGIN_SETTINGS_REQUEST.sendRequestToServer(input, (responseBuf)->
        {
            if(responseBuf != null)
            {
                decodeSettings_internal(responseBuf);
                setSettingsToGuiElement_internal();
            }
        });
    }
    public final void saveSettings()
    {
        readSettingsFromGuiElement_internal();
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        encodeSettings_internal(buf);
        MarketPluginSettingsRequest.Input input = new MarketPluginSettingsRequest.Input(getTradingPair(), getPluginTypeID(), buf);
        StockMarketNetworking.MARKET_PLUGIN_SETTINGS_REQUEST.sendRequestToServer(input, (responseBuf)->
        {
            if(responseBuf != null)
            {
                decodeSettings_internal(responseBuf);
                setSettingsToGuiElement_internal();
            }
        });
    }

    public final void stopStream()
    {
        if(streamID == null)
            return;
        StreamSystem.stopStream(streamID);
    }
    public final boolean isStreamActive()
    {
        return streamID != null;
    }
    protected final void onStreamClosed()
    {
        streamID = null;
    }


    protected ClientMarketPluginGuiElement getCustomSettingsGuiElement()
    {
        return customSettingsGuiElement;
    }

    protected void setCustomSettingsToGuiElement()
    {
        customSettingsGuiElement.setCustomSettings(customPluginSettings);
    }
    protected void readCustomSettingsFromGuiElement()
    {
        customSettingsGuiElement.getCustomSettings(customPluginSettings);
    }


    @Override
    protected final void update() {

    }
    @Override
    protected final boolean saveToFilesystem(CompoundTag tag) {
        return true;
    }

    @Override
    protected final boolean loadFromFilesystem(CompoundTag tag) {
        return true;
    }

    /**
     * Encodes the settings to a ByteBuffer that gets sent to the server
     * This is used to save the current settings from the management UI back to the server
     * @param buf to hold the data
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        customPluginSettings.encode(buf);
    }

    /**
     * Decodes the settings from a ByteBuffer received from the server
     * @param buf containing the data
     * @implNote If setCustomSettings() was not called with a valid instance, this will crash!
     */
    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        customPluginSettings.decode(buf);
    }


    private void onStreamPacketReceived_internal(FriendlyByteBuf buf)
    {
        if(buf == null)
        {
            error("ClientMarketPlugin: Received null stream packet for plugin: "+this);
        }
        else
        {
            onStreamPacketReceived(buf);
        }
    }
    public final ClientMarketPluginGuiElement getSettingsGuiElement_internal()
    {
        //customSettingsGuiElement = getSettingsGuiElement();
        loadSettings();
        return customSettingsGuiElement;
    }
    public final void setSettingsToGuiElement_internal()
    {
        if(customSettingsGuiElement != null) {
            customSettingsGuiElement.setPluginSettings_internal(this.getSettings());
            setCustomSettingsToGuiElement();
        }
    }
    public final void readSettingsFromGuiElement_internal()
    {
        Settings settings = getSettings();
        if(customSettingsGuiElement != null && settings != null) {
            customSettingsGuiElement.getPluginSettings_internal(settings);
            readCustomSettingsFromGuiElement();
        }
    }

    public void close_internal()
    {
        close();
        if(customSettingsGuiElement != null)
        {
            customSettingsGuiElement.setMoveUpDownCallbacks(null, null);
        }
        stopStream();
    }
}
