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
The file contains the base prices for all assets.
``` Json
{
  "CommonBlock": {
    "log": 20,
    "plank": 5,
    "stone": 20,
    "cobblestone": 10,
    "sand": 20,
    "dirt": 5,
    "gravel": 20,
    "clay_ball": 5,
    "wool": 5,
    "glass": 20,
    "obsidian": 100,
    "nether_brick": 100
  },
  "Ore": {
    "coal": 8,
    "iron": 30,
    "copper": 20,
    "gold": 100,
    "diamond": 200,
    "emerald": 300,
    "lapis_lazuli": 50,
    "ancient_debris": 500,
    "netherite_scrap": 500,
    "redstone_dust": 10,
    "nether_quartz": 10,
    "prismarine_shard": 10,
    "amethyst_shard": 10
  },
  "Gardening": {
    "bamboo": 2,
    "chorus_fruit": 10,
    "dye": 1,
    "beehive": 20,
    "bee_nest": 20,
    "sapling": 5,
    "seed": 2,
    "potato": 5,
    "sugar_cane": 5,
    "cocoa_beans": 5,
    "wheat": 5,
    "sweet_berries": 5,
    "beetroot": 5,
    "glow_berries": 5
  },
  "Food": {
    "cooked_beef": 5,
    "cooked_chicken": 5,
    "cooked_porkchop": 5,
    "cooked_mutton": 5,
    "cooked_rabbit": 5,
    "cooked_salmon": 5,
    "cooked_cod": 5,
    "bread": 5,
    "cookie": 5,
    "cake": 5,
    "pumpkin_pie": 5,
    "mushroom_stew": 5,
    "rabbit_stew": 5,
    "suspicious_stew": 5,
    "enchanted_golden_apple": 1000,
    "apple": 5,
    "carrot": 5,
    "golden_apple": 805,
    "golden_carrot": 105,
    "beetroot_soup": 10,
    "baked_potato": 10
  },
  "AnimalLoot": {
    "rabbit_hide": 5,
    "leather": 20,
    "bone": 5,
    "feather": 5,
    "raw_beef": 5,
    "raw_chicken": 5,
    "raw_porkchop": 5,
    "raw_mutton": 5,
    "raw_rabbit": 5,
    "raw_salmon": 5,
    "cod": 5,
    "tropical_fish": 5,
    "puffer_fish": 5,
    "honeycomb": 10,
    "honey_bottle": 10,
    "ink_sac": 10,
    "spider_eye": 10
  },
  "Furniture": {},
  "Enchantment": {
    "enchantment_factor": 10.0
  },
  "Potion": {
    "potion_factor": 1.0
  },
  "Arrow": {
    "potion_factor": 1.0
  },
  "Misc": {
    "book": 10,
    "ender_pearl": 50,
    "slime_ball": 30,
    "flint": 10,
    "blaze_powder": 100,
    "ghast_tear": 100,
    "nether_star": 1000,
    "glowstone_dust": 10,
    "string": 10
  },
  "Dye": {
    "dye": 10
  }
}
```

## Default Market Setup Data Directory
This folder contains a list of json files. Each file represents a **Asset Category** and gets displayed in the [MarketCreationByCategory Screen](SetupMarkets.md/#use-presets-to-create-a-market).
You can add/remove/change the files on the fly. In game the MarketCreationByCategory Screen must be reopend to refresh the content.

### Asset Category File
The most basic group is a empty group, only containing its name and a [Item](#item) entry for displaying a icon.
``` Json
{
  "groupName": "Ores",
  "iconItemID": {
    "itemID": "minecraft:iron_ingot"
  },
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
  "StoredEnchantments": [
    {
      "id": "minecraft:protection",
      "lvl": 1
    }
  ]
}
``` 