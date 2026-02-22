# Mod Settings

The settings file gets created after the first start in this location: `WorldName\Finance\StockMarket\settings.json`

> [!NOTE]  
> Stop the server before you change settings.
> 


``` Json
{
  "Utilities": {
    "SAVE_INTERVAL_MINUTES": 5,
    "LOGGING_ENABLE_INFO": true,
    "LOGGING_ENABLE_WARNING": true,
    "LOGGING_ENABLE_ERROR": true,
    "LOGGING_ENABLE_DEBUG": false,
    "ADMIN_PERMISSION_LEVEL": 2,
    "TRADE_ITEM_CHUNK_SIZE": 100
  },
  "UISettings": {
    "PRICE_HISTORY_SIZE": 100,
    "MAX_ORDERBOOK_TILES": 100
  },
  "Market": {
    "SHIFT_PRICE_CANDLE_INTERVAL_MS": 60000,
    "MARKET_OPEN_AT_CREATION": false,
    "CURRENCY_ITEM": {
      "itemID": "banksystem:money"
    },
    "VIRTUAL_ORDERBOOK_ARRAY_SIZE": 100
  },
  "MarketBot": {
    "MARKET_BOT_ENABLED": true,
    "MARKET_BOT_UPDATE_TIMER_INTERVAL_MS": 500,
    "MARKET_BOT_ORDER_BOOK_VOLUME_SCALE": 100.0,
    "MARKET_BOT_NEAR_MARKET_VOLUME_SCALE": 2.0,
    "MARKET_BOT_VOLUME_ACCUMULATION_RATE": 0.001,
    "MARKET_BOT_VOLUME_FAST_ACCUMULATION_RATE": 0.1,
    "MARKET_BOT_VOLUME_DECUMULATION_RATE": 1.0E-4
  }
}
```

## Custom currency
To use a custom currency as default currency, change the `"CURRENCY_ITEM"`:
In this example I used a enchantment book as an example.

``` Json
"CURRENCY_ITEM": {
  "itemID": "minecraft:enchanted_book",
  "StoredEnchantments": [
    {
      "id": "minecraft:feather_falling",
      "lvl": 1
    }
  ]
}
``` 

> [!NOTE]  
> After changing the currency you have to delete the Market presets in order to generate them again with the new currency.<br>
> Click [here](MarketPresets.md/#regenerate-the-presets) to lean, how to do that.
> 