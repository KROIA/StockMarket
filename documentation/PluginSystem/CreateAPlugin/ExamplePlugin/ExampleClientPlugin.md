# ExampleClientPlugin
This class is used as an interface for the GUI to the backend server.
It manages the data transfer for settings and streaming data.
It holds the instance of the GuiElement and the custom Settings object.



## Example Code
``` Java
public class ExampleClientPlugin extends ClientMarketPlugin {


    private final ExampleClientPluginGuiElement guiElement;
    private ExampleClientPlugin.Settings settings;

    public ExampleClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
        settings = new ExampleClientPlugin.Settings();
        guiElement = new ExampleClientPluginGuiElement(this);
        setCustomSettings(settings);                                                    // << Don't forget this!
        setCustomSettingsGuiElement(guiElement);                                        // << Don't forget this!
    }

    @Override
    protected void setup() {
        startStream(); // Start the stream when the plugin management window opens
    }
    @Override
    protected void close() {
        stopStream(); // Stop the stream when the plugin management window gets closed or the plugin got removed
    }

    /**
     * Receives the data which was sent by the MarketPlugin.encodeClientStreamData() methode
     */ 
    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {
        float targetPrice = buf.readFloat();
        guiElement.setTargetPrice(targetPrice);
    }
}
```

---
Setting and reading the custom settings to and from the GuiElement can be overwritten if needed
``` Java
@Override
protected void setCustomSettingsToGuiElement() {
    guiElement.setCustomSettings(settings);
}
@Override
protected void readCustomSettingsFromGuiElement() {
    guiElement.getCustomSettings(settings);
}
```

---
> [!CAUTION]  
> Like in [ExamplePlugin](ExamplePlugin.md#sending-and-receiving-custom-data-from-the-management-ui) already desribed, 
> the implementation must match the encoding/decoding for both sides (server and client)

``` Java
@Override
protected void encodeSettings(FriendlyByteBuf buf) {
    settings.encode(buf);
}
@Override
protected void decodeSettings(FriendlyByteBuf buf) {
    settings.decode(buf);
} 
```