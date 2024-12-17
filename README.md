# StockMarket Mod

## About
StockMarket is a Minecraft mod that brings a better way for trading to the game. It enables players to trade items on a virtual stock market, making it ideal for multiplayer servers with active economies. For Server that have not enough players or even for single player, a market bot can be added that simulates a large player base and provides liquidity to the market.
<table>
<tr>
<td>
<div align="center">
    <img src="documentation/images/tradingView.png" > 
</div>
</td>
</table>

---
## Chapters
* [Features](#features)
* [Blocks](#blocks)
* [Items](#items)
* [Matching Engine](#matching-engine)
    * [Market Order](#market-order)
    * [Limit Order](#limit-order)
* [How to use](#how-to-use)
    * [For server player / single player](#for-server-player--single-player)
    * [For admins / single player](#for-admins--single-player)
* [Commands](#commands)
* [Stock Market Bot](#stock-market-bot)


---
## Features
- Adds a banking system to the game for money and items.
- Adds new blocks to interact with the market or bank account.
- Implementation of a [matching engine](#matching-engine) inspired by the real market.
- Configurable bot which provides the market with liquidity and volatility 



---
## Blocks
<table>
<tr>
<td>
<b>Metal Case Block</b><br>
Casing for the Terminal block.<br>
8 Iron ingots
</td>
<td>
<div align="center">
    <img src="documentation/images/metalCaseBlock.png" width="300"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Terminal Block</b><br>
Unprogrammed terminal.<br>
Can be programmed using a software item.<br>
4 Iron nuggets<br>
1 Metal Case Block<br>
1 Display<br>
1 Circuit Board<br>
2 Redstone
</td>
<td>
<div align="center">
    <img src="documentation/images/terminalBlock.png" width="300"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Banking Terminal Block</b><br>
Used to get access to the bank account.<br>
Interaction using right click.<br>
Right click on a <b>Terminal Block</b> using a <b>Banking Software</b> to create this block.
</td>
<td>
<div align="center">
    <img src="documentation/images/bankingTerminalBlock.png" width="100"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Banking Terminal Block</b><br>
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
<b>Circuit Board</b><br>
Electronics for other Items.<br>
1 Nether Quartz<br>
3 Copper Ingots<br>
3 Paper<br>
</td>
<td>
<div align="center">
    <img src="documentation/images/circuitBoard.png" width="300"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Display</b><br>
Display for the <b>Terminal Block</b><br>
6 Glass Planes<br>
2 Iron Ingots<br>
1 Ciruit Board
</td>
<td>
<div align="center">
    <img src="documentation/images/display.png" width="300"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Empty Software</b><br>
Used to create a specific software<br>
4 Iron nuggets<br>
2 Ink Sacs<br>
3 Paper
</td>
<td>
<div align="center">
    <img src="documentation/images/emptySoftware.png" width="300"> 
</div>
</td>
</tr>


<tr>
<td>
<b>Banking Software</b><br>
Used to programm the <b>Terminal Block</b> to be a <b>Bank Terminal Block</b><br>
1 Empty Software<br>
1 Gold Ingot<br>
</td>
<td>
<div align="center">
    <img src="documentation/images/bankingSoftware.png" width="300"> 
</div>
</td>
</tr>


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
A market order requires only the **amount** to buy/sell. The trade is executed on the best available price.<br>

**Benefits**
- The trade is executed immediately

**Disadvantages**
- When buing/selling, the average buy/sell price is mostly higher/lower than the market price before the trade.
- Creates high volatility for markets with low liquidity, see: [Order Book Swipe](#order-book-swipe)
- A market order can only be executed when there are enough volume present on the opposite side to fill the order. Otherwise the order may only be filled partially.


#### Limit Order
A limit order requires an **amount** and **price** at which the trade gets executed. A buy order gets mostly placed below the current market price. The order is executed when the market price moves towards the order price.

**Benefits**
- For a buy order, the price a trader has to pay is at the **price** level or lower.
- For a sell order, the received amount is at least the **price** or higher.
- Limit orders are important for a market because it provides the required liquidity for the market to be able to process market orders.

**Disadvantages**
- The order needs time to be filled by the market.
- When the price never reaches the desired buy/sell price, the order never gets executed and stays pending.


---
## How to use
### For server player / single player
#### Selling Items
To sell items the item must be available on the market. This is managed by the server admin.<br>
Assuming the item can be traded on the market, you first have to deposit the item to your bank account. This can be done using the **Bank Terminal Block**.<br>
Open that block using right click. A window will appear.
Place your item stacks in the blocks inventory and press the button **Send items to market**.
This will transfer the items to your bank account.
<div align="center">
    <img src="documentation/images/bankingView.png" > 
</div>
<br>

Now open the **Stock Market Terminal Block** using right click.
This opens the trading window.
On the left side you can see the [Candle stick chart](#candle-stick-chart)
On the left edge of the chart is the price axis.
On the right edge of the chart is the [Order Book](#order-book) volume displayed.
On the right side of the window is the interaction panel. There you can select the desired item to be traded. Just click on the button and a list of all tradable items will appear.
Type the amount of items you want to buy/sell.
Decide whether you want to execute a market order or a limit order.
If you want to create a limt order, you have to type in the desired price.
Click on the **sell** button for market or limit order to execute the trade.
A market order is executet directly but the limit order will be listed on the left side until it is filled or gets canceled.
<div align="center">
    <img src="documentation/images/tradingView.png" > 
</div>

---
### Buying Items
To buy a item you have to get some money first. You can do that by selling other items or receiving money from other players. A server admin can also provide you money...
Assuming you have money, open the **Stock Market Terminal Block**
Select the item you want to trade by pressing the button on the top right with the Label "Price: XX".
Type the amount you want to buy, type a limit price if you want to execute a limit order.
Clock on the **buy** button.
A market order gets executed directly but a limit order may take some time until it gets filled by the market.

Once you have received the items, you can go to the **Bank Terminal Block**.
On the left side you can see your items on the bank account.
Type the amount you want to receive back in to the text field and click **Receive items from market**. The items will be transfered to the Bank Terminal Block



<div align="center">
    <img src="documentation/images/bankingView.png" > 
</div>
<br>

---
### For admins / single player
:construction:

## Commands


| Command | Description | Admin only |
|---------|-------------|------------|
| /money                           											| Show balance                                  |  |
| /money add [amount]              											| Add money to self                             | :heavy_check_mark: |
| /money add [user] [amount]       											| Add money to another player                   | :heavy_check_mark: |
| /money set [amount]              											| Set money to self                             | :heavy_check_mark: |
| /money set [user] [amount]       											| Set money to another player                   | :heavy_check_mark: |
| /money remove [amount]           											| Remove money from self                        | :heavy_check_mark: |
| /money remove [user] [amount]    											| Remove money from another player              | :heavy_check_mark: |
| /money send [user] [amount]      											| Send money to another player                  |  |
| /money circulation               											| Show money circulation of all players + bots  |  |
| /bank                                                						| Show bank balance (money and items)      		|  |
| /bank [username] show                                						| Show bank balance of another player      		| :heavy_check_mark: |
| /bank [username] create [itemID] [amount]            						| Create a bank for another player         		| :heavy_check_mark: |
| /bank [username] setBalance [itemID] [amount]        						| Set balance of a bank for another player 		| :heavy_check_mark: |
| /bank [username] delete [itemID]                     						| Delete a bank for another player         		| :heavy_check_mark: |
| /StockMarket setPriceCandleTimeInterval <seconds>                         | Set price candle time interval                | :heavy_check_mark: |
| /StockMarket createDefaultBots                                            | Create default bots                           | :heavy_check_mark: |
| /StockMarket order cancelAll                                              | Cancel all orders                             |  |
| /StockMarket order cancelAll <itemID>                                     | Cancel all orders of an item                  |  |
| /StockMarket order <username> cancelAll                                   | Cancel all orders of a player                 | :heavy_check_mark: |
| /StockMarket order <username> cancelAll <itemID>                          | Cancel all orders of a player for an item     | :heavy_check_mark: |
| /StockMarket [itemID] bot settings get                                    | Get bot settings                         		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set enabled                            | Enable bot                               		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set disabled                           | Disable bot                              		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set volatility [volatility]            | Set volatility                           		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set imbalancePriceRange [priceRange]   | Set imbalance price range                		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set targetItemBalance [balance]        | Set target item balance                  		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set maxOrderCount [orderCount]         | Set max order count                           | :heavy_check_mark: |
| /StockMarket [itemID] bot settings set volumeScale [volumeScale]          | Set volume scale                              | :heavy_check_mark: |
| /StockMarket [itemID] bot settings set volumeSpread [volumeSpread]        | Set volume spread                             | :heavy_check_mark: |
| /StockMarket [itemID] bot settings set volumeRandomness [volumeRandomness]| Set volume randomness                         | :heavy_check_mark: |
| /StockMarket [itemID] bot settings set timer [timer]                      | Set timer                                		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set minTimer [timer]                   | Set min timer                            		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set maxTimer [timer]                   | Set max timer                            		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set pidP [pidP]                        | Set PID P                                		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set pidI [pidI]                        | Set PID I                                		| :heavy_check_mark: |
| /StockMarket [itemID] bot settings set pidD [pidD]                        | Set PID D                                		| :heavy_check_mark: |
| /StockMarket [itemID] bot create                                          | Create bot                               		| :heavy_check_mark: |
| /StockMarket [itemID] bot remove                                          | Remove bot                               		| :heavy_check_mark: |
| /StockMarket [itemID] create                                              | Create marketplace                       		| :heavy_check_mark: |
| /StockMarket [itemID] remove                                              | Remove marketplace                            | :heavy_check_mark: |
| /StockMarket [itemID] currentPrice                                        | Get current price                        		| :heavy_check_mark: |





## Stock Market Bot
The StockMarketBot has multiple purposes. 
- Providing the market with liquidity
- Changing price depending on supply and demand
- Source for capital for players (if they start with 0 money)

#### Providing liquidity
Liquidity is a very important part of every market. 
For example if player A wants to buy wood, someone has to sell the same amount.
Without the counterpart, a trade can't be made.
Since it is unlikely that a server creates enough sell/buy orders to create this
liquidity, the bot is used to provide both buy and sell orders.
The bot will buy if a player wants to sell, and he will sell if a player wants to buy.
The bot can only sell so many items it has and he also can only buy as long as he has money.
The bot always needs some balance of items and money. Without that its function will break.

#### Changing price
The bot is responsible for the most price movements you see on the chart.
The bot is always buying and selling to it self. 
Using a combination of [limit](#limit-order) and [market](#market-order) -orders will create 
movements in price.<br>
To create a random price chart that also stays in its realistic bounds, multiple stages are needed.
1) **Defining parameters**
    - target stock balance
    - target price range
    - Otherwise

2) **Defining a target price the market should move to**
    Depending on how many items the bot holds and what its target item balance is,
    it will try to rise or lower the price.
    For example if the bot holds 1000 items, wants to hold 500,
    the bot will lower the target price, making it attractive to players to buy.
    In the case the bot has more items than the target stock balance,
    the target price fall linear and also can reach a price of 0.
    In the case the bot holds less than he wants, the target price will 
    rise quadraticly, producing a even larger price increase.
    The reason for that is, that the bot is dependend on enough supplies to work correct,
    a larger price increase makes it lucrative for players to sell to the bot.


    Additionaly some random noise is used to create the volatility.
    This noise is added to the target price and is generated using a 
    [Random Walk](#random-walk) generator.

3) **Move the market to the target price**
    To change the price the bot creates market sell/buy orders. 
    The market oder will fill the previously placed limit orders from the bot or a player.
    [Learn why a price moves here](#why-does-a-price-move).
    The size of the market order defines how fast the price will change.
    A [PID-Controller](#pid-controller) is used to generate the buy/sell market orders.
    The PID-Controller will make sure to generate the right amount of orders until the 
    target price is reached.


:construction:

---

<table>
<tr>
<td>
<div align="center">
    <img src="documentation/images/tradingView.PNG" > 
</div>
</td>
<td>
<div align="center">
    <img src="documentation/images/bankingView.PNG" > 
</div>
</td>
</tr>
</table>




## Why does a price move?



## Terminology
### Order Book
:construction:

### Order Book Swipe
:construction:

### Candle Stick Chart
:construction:

### Random Walk

### PID-Controller