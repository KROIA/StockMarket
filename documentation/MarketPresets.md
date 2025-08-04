# Market Presets
In order to make it easy in the beginning to get started with some commonly used items, the mod generates market presets as json files. These files are generated on the first start of the savegame and can be modified afterwards.


You can find all related files in the following Path: `<WorldName>\Finance\StockMarket`. Where the "WorldName" is the wold folder, located in the `saves` directory of Minecraft.

---
## Base Price File
In order to generate the presets, a file is needed that specifies the price for different type of items.
In the beginning, the file does not exist and will be generated on the first start of the savegame.
If you want to change the base price of the preset markets, do the following steps:
1) Change the prices in the file: `StockMarket\base_prices.json`.
2) Delete the folder: `StockMarket\DefaultMarketSetupData`.
3) Restart the Game/Server.
4) The folder: `StockMarket\DefaultMarketSetupData` gets created again with the new market preset settings.

### base_prices.json
The file contains the base prices for all assets. The first parameter: `INFLATION_SCALE` can be used as factor for each other price. The mod does multiply each price in this list with that inflation scale value.
This makes it easy to just ajust the inflation of the currency but letting the price ratios between the assets constant.
``` Json
{
  "INFLATION_SCALE": 1.0,
  "log_price": 20,
  "plank_price": 5,
  "stick_price": 2,
  "stone_price": 20,
  "sand_price": 20,
  "gravel_price": 20,
  "clay_ball_price": 5,
  "wool_price": 5,
  "coal_price": 8,
  "iron_price": 30,
  "copper_price": 20,
  "gold_price": 100,
  "diamond_price": 200,
  "emerald_price": 300,
  "lapis_lazuli_price": 50,
  "ancient_debris_price": 500,
  "netherite_scrap_price": 500,
  "redstone_dust_price": 10,
  "nether_quartz_price": 10,
  "bamboo_price": 2,
  "prismarine_shard_price": 10,
  "chorus_fruit_price": 10,
  "honeycomb_price": 10,
  "dye_price": 1,
  "enchanted_book_price": 200
}
```

## Default Market Setup Data Directory
This folder contains a list of json files. Each file represents a **Asset Category** and gets displayed in the [MarketCreationByCategory Screen](SetupMarkets.md/#use-presets-to-create-a-market).
You can add/remove/change the files on the fly. In game the MarketCreationByCategory Screen must be reopend to refresh the content.

### Asset Category File
The most basic group is a empty group, only containing its name.
``` Json
{
  "groupName": "Ores",
  "markets": []
}
```


---
Market settings are added in the **"markets"** array.
This example shows the market where coal gets traded for money.
It consists of 3 sections:
- **"tradingPair"**: Contains informations about both items, most basic information is the itemID but sometimes this is not enough. More about that in the section [ItemID](#itemid)
- **"botSettings"**: Contains the settings for the market bot.
- **"virtualOrderBookSettings"**: Contains the settings for the virtual order book

All 3 sections must be contained in a market settings json entry.
  
``` Json
{
  "groupName": "Ores",
  "markets": [
    {
      "tradingPair": {
        "item": {
          "itemID": "minecraft:coal"
        },
        "currency": {
          "itemID": "banksystem:money"
        }
      },
      "botSettings": {
        "enabled": true,
        "defaultPrice": 80,
        "updateTimerIntervallMS": 500,
        "volumeScale": 3.3333335,
        "enableTargetPrice": true,
        "targetPriceSteeringFactor": 0.002,
        "enableVolumeTracking": true,
        "volumeSteeringFactor": 1.0E-7,
        "enableRandomWalk": true,
        "volatility": 0.01
      },
      "virtualOrderBookSettings": {
        "volumeScale": 3333.3335,
        "nearMarketVolumeScale": 2.0,
        "volumeAccumulationRate": 0.001,
        "volumeFastAccumulationRate": 0.1,
        "volumeDecumulationRate": 1.0E-4
      }
    }
  ]
}
```

---
### Item
To identify a item, the minecraft item id string is not enough. Some items are different but can have the same item id string. For exampe: **Enchanted Books** they have all the item id = `minecraft:enchanted_book` but can have different enchantments. In such a case the **"item"** json entry can look like this:

``` Json
"item": {
  "itemID": "minecraft:enchanted_book",
  "enchantments": [
    {
      "enchantmentID": "minecraft:protection",
      "level": 1
    }
  ]
}
``` 