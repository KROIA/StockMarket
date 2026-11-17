# Web Interface

StockMarket can serve a small read-only web page with live market data, so players can follow the markets in a browser without being in game: a candlestick chart with volume, the order book depth, the open player orders and the recent trades of every market.

## Chapters

* [Turning it on](#turning-it-on)
* [What the page shows](#what-the-page-shows)
* [Privacy](#privacy)
* [Security and HTTPS](#security-and-https)
* [Multi-server setups](#multi-server-setups)
* [Troubleshooting](#troubleshooting)

---

## Turning it on

1. Open the **Mod Settings** screen (operator level 2 and StockMarket admin mode) and go to the **Integrations** group.
2. Tick **Web interface (read-only)** and set the **Port** (default `8090`).
3. Click **Apply**. The change takes effect immediately, no restart is needed.

Then open `http://<server address>:<port>/` in a browser, for example `http://play.example.com:8090/`.

The port must be between 1024 and 65535, and it can't be the game port, the multi-server link port or a port another program already uses. A port that fails these checks is refused when you apply the settings: the screen shows the reason and the web interface keeps its previous settings. Your other changes are still saved.

On a dedicated server the page listens on all network interfaces, so you also have to open the port in your firewall or forward it in your router. In singleplayer it only listens on `127.0.0.1`, so only you can open it on the same computer.

Switching the setting off stops the web server right away.

## What the page shows

* **Market list** with search, the current price and a status dot: green = open, grey = closed (outside trading hours), amber = maintenance.
* **Candlestick chart** with a volume histogram. Timeframes are 1m, 5m, 15m, 1h, 4h and 1D. When candles follow in-game time (see [Candle timebase](candle-timebase.md)) the timeframes are in-game hours: 1h, 4h, 1D and 1W; the chart's time axis still shows real-world clock times.
* **Order book depth** within 25 % of the current price, sell volume above the price and buy volume below it. This includes the bot liquidity, the same as the in-game order book.
* **Open player orders**, grouped by limit price: buy volume, sell volume and the number of orders at each price.
* **Recent trades**: the finished player orders of the last 24 hours (filled, or cancelled after a partial fill), with side, amount and average price. One row is one order, not each individual fill.

The page updates itself every few seconds.

## Privacy

The page never shows player names, UUIDs, bank account numbers or order IDs.

It does show the open player orders grouped by price. If only one player has an order at a price, that order's price and size are visible. A large resting order can therefore be recognized by everyone who can open the page.

There is no login. Anyone who can reach the port can see the page, so only open the port in your firewall if everyone may see the market data.

## Security and HTTPS

The web interface is read-only: it has no way to place orders, change settings or log in.

It only speaks plain HTTP. If you want HTTPS or your own domain name, put a reverse proxy (for example nginx or Caddy) in front of it and keep the port itself closed in the firewall.

## Multi-server setups

Only the master server runs the web interface, because only the master has the markets. Slave servers never start it, whatever their settings say. Point the browser at the master's address.

## Troubleshooting

* **"saved, but the web interface could not start"**: the server could not open the port, for example because another program took it after the check. The server log has a line starting with `[StockMarket Web]` with the reason. Pick another port and apply again.
* **The page doesn't load from another computer**: check the firewall / port forwarding of the master server and that you use the master's address.
* **Item names look like `item.somemod.thing`**: the server has no translation for that item, so the page shows its item ID instead.

The chart is drawn with [TradingView Lightweight Charts](https://github.com/tradingview/lightweight-charts) (Apache License 2.0). The page links to its license.
