# StockMarket Mod

StockMarket adds a market for any item, with a real order book: market and limit orders, matched between players. Markets stay alive without players: plugins add volume, price movement and a trading bot.

[![Documentation](https://img.shields.io/badge/Documentation-Open_the_guide-2d8a4e?style=for-the-badge)](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/)

[![For developers](https://img.shields.io/badge/For_developers-Plugin_and_API_guide-555555)](https://kroia.github.io/BanksystemStockmarketDoku/developers/)

## Features
- New [blocks and items](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/blocks-and-recipes/) to trade at the market.
- A matching engine inspired by the real market, with [market and limit orders](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/trading/) in an [order book](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/price-chart/).
- Configurable [plugins](https://kroia.github.io/BanksystemStockmarketDoku/admin/stockmarket/plugins/) that give the market liquidity, volatility and price movements.
- A JSON-configurable [news event system](https://kroia.github.io/BanksystemStockmarketDoku/admin/stockmarket/news/) whose headlines move prices. Players read them in a craftable [newspaper](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/newspaper/), and admins can [write their own events](https://kroia.github.io/BanksystemStockmarketDoku/admin/stockmarket/news/tutorial/) without touching code.
- An optional read-only [web interface](https://kroia.github.io/BanksystemStockmarketDoku/admin/web-interface/) that shows the markets live in a browser.

## Blocks and items
[Stock Market Block](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/blocks-and-recipes/#stock-market-block), [Trading Software](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/blocks-and-recipes/#trading-software), [Stock Market Display](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/blocks-and-recipes/#stock-market-display) and [Newspaper](https://kroia.github.io/BanksystemStockmarketDoku/stockmarket/blocks-and-recipes/#newspaper).

## Supported versions
StockMarket 2.2.0 on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/stockmarket):

| Minecraft | Fabric | NeoForge |
|-----------|--------|----------|
| 26.3    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-26.3]    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-26.3] |
| 26.2    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-26.2]    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-26.2] |
| 26.1    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-26.1]    | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-26.1] |
| 1.21.11 | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.11] | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.11] |
| 1.21.10 | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.10] | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.10] |
| 1.21.9  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.9]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.9] |
| 1.21.8  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.8]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.8] |
| 1.21.7  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.7]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.7] |
| 1.21.6  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.6]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.6] |
| 1.21.5  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.5]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.5] |
| 1.21.4  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.4]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.4] |
| 1.21.3  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.3]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.3] |
| 1.21.2  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.2]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.2] |
| 1.21.1  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-fabric-1.21.1]  | [![Version](https://img.shields.io/badge/v2.2.0-green)][2.2.0-neoforge-1.21.1] |

[2.2.0-neoforge-1.21.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781895
[2.2.0-fabric-1.21.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781893
[2.2.0-neoforge-1.21.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781898
[2.2.0-fabric-1.21.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781896
[2.2.0-neoforge-1.21.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781901
[2.2.0-fabric-1.21.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781900
[2.2.0-neoforge-1.21.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781906
[2.2.0-fabric-1.21.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781904
[2.2.0-neoforge-1.21.5]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781909
[2.2.0-fabric-1.21.5]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781908
[2.2.0-neoforge-1.21.6]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781913
[2.2.0-fabric-1.21.6]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781912
[2.2.0-neoforge-1.21.7]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781918
[2.2.0-fabric-1.21.7]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781916
[2.2.0-neoforge-1.21.8]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781920
[2.2.0-fabric-1.21.8]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781919
[2.2.0-neoforge-1.21.9]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781922
[2.2.0-fabric-1.21.9]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781921
[2.2.0-neoforge-1.21.10]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781925
[2.2.0-fabric-1.21.10]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781924
[2.2.0-neoforge-1.21.11]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781927
[2.2.0-fabric-1.21.11]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781926
[2.2.0-neoforge-26.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781929
[2.2.0-fabric-26.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781928
[2.2.0-neoforge-26.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781932
[2.2.0-fabric-26.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781930
[2.2.0-neoforge-26.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781935
[2.2.0-fabric-26.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/files/9781934

## Requirements
StockMarket needs [BankSystem](https://github.com/KROIA/BankSystem) of the same version line (StockMarket `2.2.x` runs with BankSystem `2.2.x`), plus [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api) and, on Fabric, [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api). ModUtilities is bundled, you don't have to install it. Details: [Installation](https://kroia.github.io/BanksystemStockmarketDoku/admin/install/).

Release notes for every version: [changelog/](changelog/)
