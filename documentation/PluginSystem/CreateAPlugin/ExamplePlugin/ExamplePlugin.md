# ExamplePlugin
This class extends the MarketPlugin class gets executed on the server and
contains the core logic of the plugin.


## Content
- [IMarketPluginInterface](#imarketplugininterface)
- [Example Code](#example-code)
  - [Settings](#settings)
  - [Save custom runtime data](#save-custom-runtime-data)
  - [Sending and receiving custom data from the management UI](#sending-and-receiving-custom-data-from-the-management-ui)
  - [Streaming data](#streaming-data)



---
## IMarketPluginInterface
The market can be accessed through the IMarketPluginInterface, that is provided by the **getPluginInterface()** methode.

---
## Example Code

``` Java
public class ExamplePlugin extends MarketPlugin {

    public static class Settings implements IPluginSettings
    {
        private static final class TAGS {
            public static final String MY_SETTING = "mySetting";
        }
        public float mySetting = 1.0f;
        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(mySetting);
        }

        public void decode(FriendlyByteBuf buf) {
            mySetting = buf.readFloat();
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat(TAGS.MY_SETTING, mySetting);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains(TAGS.MY_SETTING))
                mySetting = tag.getFloat(TAGS.MY_SETTING);
            return true;
        }
    }

    private final Settings settings = new Settings();
    private float targetPrice = 0;

    // Helper function for Plugin registration
    public static String getNameStatic()
    {
        return "Example Plugin";
    }

    // Helper function for Plugin registration
    public static String getDescriptionStatic()
    {
        return "A demo plugin";
    }

    public ExamplePlugin()
    {
        setCustomSettings(settings);                                        // << Don't forget this!
    }

    /**
     * Encode live data that gets sent to the client
     * @param buf The buffer to write to
     */
    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {
        buf.writeFloat(targetPrice);
    }

    /**
     * Gets called when a plugin gets created
     */
    @Override
    protected void setup() {
        pluginInterface.setStreamPacketSendTickInterval(5);
    }

    /**
     * Update call for the plugin
     */
    @Override
    protected void update() {

        IMarketPluginInterface pluginInterface = getPluginInterface();
        targetPrice = pluginInterface.getTargetPrice();
        float currentPrice = pluginInterface.getPrice();

        long marketOrderAmount = 0;

        if(targetPrice > currentPrice)
            marketOrderAmount = 1;  // Create a buy order to increase the price
        else if(targetPrice < currentPrice)
            marketOrderAmount = -1; // Create a sell order to decrease the price

    
        if(marketOrderAmount != 0)
        {
            pluginInterface.placeOrder(marketOrderAmount);
            info("Placing order: " + marketOrderAmount);
        }
    }
}
```


---
### Settings
The nasted Settings class contains settings for the specific plugin instance.
Add the Settings instance to the MarketPlugin by calling **setCustomSettings()**. 
That will handle the saving and loading automatically.

``` Java
public class ExamplePlugin extends MarketPlugin {

    public static class Settings implements IPluginSettings
    {
        private static final class TAGS {
            public static final String MY_SETTING = "mySetting";
        }
        public float mySetting = 1.0f;
        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(mySetting);
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            mySetting = buf.readFloat();
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat(TAGS.MY_SETTING, mySetting);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains(TAGS.MY_SETTING))
                mySetting = tag.getFloat(TAGS.MY_SETTING);
            return true;
        }
    }

    private final Settings settings = new Settings();


    public ExamplePlugin()
    {
        setCustomSettings(settings);
    }
}
```






---
### Save custom runtime data
If there is additional data in a plugin that does not belong to the settings, overwrite the 
following functions and store and load the data for the **FriendlyByteBuf** and **CompoundTag**.
The baseclass **MarketPlugin** will only save/load the custom settings object, set by calling **setCustomSettings()**.


``` Java
@Override
protected boolean saveToFilesystem(CompoundTag tag) {
    CompoundTag customDataTag = new CompoundTag();
    customDataTag.putFloat("targetPrice", targetPrice); // << Any data from the plugin

    CompoundTag settingsTag  = new CompoundTag(); // << Also save the settings
    settings.save(settingsTag);

    tag.put("customData", customDataTag); // Combine the tags
    tag.put("settings", settingsTag);     //
    return true;
}
@Override
protected boolean loadFromFilesystem(CompoundTag tag) {
    CompoundTag customDataTag = tag.getCompound("customData");
    targetPrice = customDataTag.getFloat("targetPrice");

    CompoundTag settingsTag = customDataTag.getCompound("settings");
    return settings.load(settingsTag);
}
```





---
### Sending and receiving custom data from the management UI
The settings can be changed in the Management UI. 
If there is the need of sending and receiving other data from the UI,
overwrite the following methodes and reimplement them to encode/decode also the custom data for the UI

> [!CAUTION]  
> If you change this implementation, you must also change the implementation of the [ExampleClientPlugin](ExampleClientPlugin.md) class to match
> Otherwise the data does not get encoded/decoded the same way on the server and the client!

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






---
### Streaming data
Plugins contain an interface for streaming data. 
This feature gets used in the management UI to visualize live data from the plugin.
In this example it contains the **targetPrice** that gets displayed in the management UI as marker.
``` Java
public class ExamplePlugin extends MarketPlugin {

    private float targetPrice = 0;

    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {
        buf.writeFloat(targetPrice);
    }
}
```

The streaming interval can be defined using:
``` Java
@Override
protected void setup() {
    getPluginInterface().setStreamPacketSendTickInterval(5); // 5 ticks
}
```
Since the markets are updated in chunks, it is not guaranteed that the interval is exact at the 
specified value. For a huge amount of markets, the real interval may be larger. 