# StockMarket Mod

## About
StockMarket is a Minecraft mod that brings a better way for trading to the game. It enables players to trade items on a virtual stock market, making it ideal for multiplayer servers with active economies. For Server that have not enough players or even for single player, a market bot can be added that simulates a large player base and provides liquidity to the market.

<tr>
<td>
<div align="center">
    <img src="documentation/images/tradingView.gif" > 
</div>
</td>

---


## Chapters 
* [Features](#features)
* [Blocks](#blocks)
* [Items](#items)
* [Matching Engine](#matching-engine)
    * [market-order](#market-order)
    * [Limit Order](#limit-order)
* [How to use](#how-to-use)
    * [For server player / single player](#for-server-player--single-player)
    * [For admins / single player](documentation/AdminSection.md)
* [Commands](#commands)
* [Stock Market Bot](#stock-market-bot)


---
## Features
- Adds a realistic trading system to the game 
  - Custom trading pairs can be defined 
  - A list of preconfigured trading pairs are ready to be activated to avoid long parameter tweaking.
  - Players have real impact on the price movement.

- Adds new [blocks](#blocks) to interact with the market.
- Implementation of a [matching engine](#matching-engine) inspired by the real market.
- Configurable [bot](#stock-market-bot) which provides the market with liquidity and volatility 

## Dependencies
- [Bank System Mod](https://github.com/KROIA/BankSystem)
- [Architectury](https://www.curseforge.com/minecraft/mc-mods/architectury-api)
- [Quilted Fabric API](https://www.curseforge.com/minecraft/mc-mods/qsl) (Only needed for Quilt)
- [Mod Utilities](https://www.curseforge.com/minecraft/mc-mods/modutilities) (Only needed for Quilt)
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) (Only needed for Fabric)

---
## Downloads
<!--
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stockmarket)


| Version | Download |
|---------|----------|
|1.3.0    | [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002691)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.6-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002684)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.4-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002682)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.3-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002681)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.2-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002679)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6002676)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.4-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6004639)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.3-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6004641)<br>[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.2-blue)](https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6004643) |

-->
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/stockmarket)
| Minecraft | Fabric | Forge | Quilt | Neoforge |
|-----------|--------|-------|-------|----------|
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.21.1] <br> |                                                                                  |                                                                                   | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-neoforge-1.21.1]  |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-green)      | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.21]   <br> |                                                                                  |                                                                                   | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-neoforge-1.21]    |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.6-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.20.6] <br> |                                                                                  |                                                                                   | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-neoforge-1.20.6]  |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.4-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.20.4] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.20.4] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-quilt-1.20.4] <br>  |                                                                                 |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.2-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.20.2] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.20.2] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-quilt-1.20.2] <br>  |                                                                                 |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.20.1] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.20.1] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-quilt-1.20.1] <br>  |                                                                                 |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.4-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.19.4] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.19.4] <br> |                                                                             <br>  |                                                                                 |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.3-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.19.3] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.19.3] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-quilt-1.19.3] <br>  |                                                                                 |
| ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19.2-green)    | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-fabric-1.19.2] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-forge-1.19.2] <br> | [![Version](https://img.shields.io/badge/v1.3.1-green)][1.3.1-quilt-1.19.2] <br>  |                                                                                 |





<!--	Links to curseforge:	-->
[1.3.1-fabric-1.21.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200719
[1.3.1-fabric-1.21]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200713
[1.3.1-fabric-1.20.6]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200704
[1.3.1-fabric-1.20.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200697
[1.3.1-fabric-1.20.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200673
[1.3.1-fabric-1.20.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200639
[1.3.1-fabric-1.19.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200635
[1.3.1-fabric-1.19.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200628
[1.3.1-fabric-1.19.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200617

[1.3.1-forge-1.20.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200698
[1.3.1-forge-1.20.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200684
[1.3.1-forge-1.20.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200647
[1.3.1-forge-1.19.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200638
[1.3.1-forge-1.19.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200631
[1.3.1-forge-1.19.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200620
					 
[1.3.1-quilt-1.20.4]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200701
[1.3.1-quilt-1.20.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200692
[1.3.1-quilt-1.20.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200659
[1.3.1-quilt-1.19.3]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200632
[1.3.1-quilt-1.19.2]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200622

[1.3.1-neoforge-1.21.1]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200720
[1.3.1-neoforge-1.21]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200717
[1.3.1-neoforge-1.20.6]:https://www.curseforge.com/minecraft/mc-mods/stockmarket/download/6200707




---
## Blocks
<table>
<tr>
<td>
<b>Stock Market Block</b><br>
Used to get access to the stock market.<br>
Interaction using right click.<br>
Right click on a <b>Terminal Block</b> using a <b>Trading Software</b> to create this block.
</td>
<td>
<div align="center">
    <img src="documentation/images/stockMarketTerminalBlock.png" width="100"> 
</div>
</td>
</tr>
</table>

---
## Items
<table>
<tr>
<td>
<b>Trading Software</b><br>
Used to programm the <b>Terminal Block</b> to be a <b>Stock Market Terminal Block</b><br>
1 Empty Software<br>
1 Emerald<br>
</td>
<td>
<div align="center">
    <img src="documentation/images/tradingSoftware.png" width="300"> 
</div>
</td>
</tr>
</table>

---
## Matching Engine
##### What is a Matching Engine?
A matching engine is the core component of a trading system that facilitates the buying and selling of items, stocks, or other assets. Its primary function is to match buy orders and sell orders based on specific rules to execute trades efficiently and fairly.

##### Matching Roles
For simplicity are no special roles implemented. The type of order and the price is all what the matching engine needs to match the orders.

---
### Type of Trades
#### Market Order
A market-order requires only the **amount** to buy/sell. The trade is executed on the best available price.<br>

**Benefits**
- The trade is executed immediately

**Disadvantages**
- When buing/selling, the average buy/sell price is mostly higher/lower than the market price before the trade.
- Creates high volatility for markets with low liquidity, see: [Order Book Swipe](#order-book-swipe)
- A market-order can only be executed when there are enough volume present on the opposite side to fill the order. Otherwise the order may only be filled partially.


#### Limit Order
A limit order requires an **amount** and **price** at which the trade gets executed. A buy order gets mostly placed below the current market price. The order is executed when the market price moves towards the order price.

**Benefits**
- For a buy order, the price a trader has to pay is at the **price** level or lower.
- For a sell order, the received amount is at least the **price** or higher.
- Limit orders are important for a market because it provides the required liquidity for the market to be able to process market-orders.

**Disadvantages**
- The order needs time to be filled by the market.
- When the price never reaches the desired buy/sell price, the order never gets executed and stays pending.


---
## How to use
### For server player / single player
#### Selling Items
To sell items the item must be available on the market. [This is managed by the server admin](documentation/AdminSection.md).<br>
Assuming the item can be traded on the market, you first have to deposit the item to your bank account. This can be done using the **Bank Terminal Block**.<br>
See [Bank System Mod](https://github.com/KROIA/BankSystem) to learn how.

Now open the **Stock Market Terminal Block** using right click.<br>
This opens the trading window.<br>
On the left side you can see the [Candle stick chart](#candle-stick-chart)<br>
On the left edge of the chart is the price axis.<br>
On the right edge of the chart is the [Order Book](#order-book) volume displayed.<br>
On the right side of the window is the interaction panel. There you can select the desired item to be traded. Just click on the button and a list of all tradable items will appear.<br>
Type the amount of items you want to buy/sell.<br>
Decide whether you want to execute a market-order or a limit order.<br>
If you want to create a limt order, you have to type in the desired price.<br>
Click on the **sell** button for market or limit order to execute the trade.<br>
A market-order is executet directly but the limit order will be listed on the left side until it is filled or gets canceled.<br>
<div align="center">
    <img src="documentation/images/tradingView.png" > 
</div>

---
### Buying Items
To buy a item you have to get some money first. You can do that by selling other items or receiving money from other players.<br>
A server admin can also provide you money...<br>
Assuming you have money, open the **Stock Market Terminal Block**<br>
Select the item you want to trade by pressing the button on the top right with the Label "Price: XX".<br>
Type the amount you want to buy, type a limit price if you want to execute a limit order.<br>
Click on the **buy** button.<br>
A market-order gets executed directly but a limit order may take some time until it gets filled by the market.<br>

Once you have received the items, you can go to the **Bank Terminal Block** receive the bought items.<br>

### Changing an order
A limit order can be changed by clicking on the marker on the position of the order. There is a small botton on the left sinde of <br>
the market. Drag and drop the order to the new price level. <br>
If you do not have enough money to move a buy order higher, the order will not be moved to that location.

<tr>
<td>
<div align="center">
    <img src="documentation/images/tradingView.gif" > 
</div>
</td>







