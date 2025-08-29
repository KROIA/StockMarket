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
    private ClientMarketPluginGuiElement settingsGuiElement = null;

    public ClientMarketPlugin(TradingPair tradingPair,String pluginTypeID) {
        super();
        setTradingPair(tradingPair);
        setPluginTypeID(pluginTypeID);
        //loadSettings();
    }

    public final void startStream()
    {
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
        applySettingsFromGuiElement_internal();
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


    protected abstract ClientMarketPluginGuiElement getSettingsGuiElement();

    protected abstract void setSettingsToGuiElement(ClientMarketPluginGuiElement element);
    protected abstract void applySettingsFromGuiElement(ClientMarketPluginGuiElement element);


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
        settingsGuiElement = getSettingsGuiElement();
        loadSettings();
        return settingsGuiElement;
    }
    public final void setSettingsToGuiElement_internal()
    {
        if(settingsGuiElement != null)
            setSettingsToGuiElement(settingsGuiElement);
    }
    public final void applySettingsFromGuiElement_internal()
    {
        if(settingsGuiElement != null)
            applySettingsFromGuiElement(settingsGuiElement);
    }
}
