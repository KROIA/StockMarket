package net.kroia.stockmarket.util;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerPlayerUtilities;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * T-192: pump-and-dump detector (master server only). Detection and alerting only: it observes
 * finished player orders and never changes prices, orders or a player's net flow.
 * <ul>
 *     <li><b>Pump:</b> a player MARKET buy during a tick in which the market price rose by more than
 *     {@code PUMP_DUMP_PRICE_MOVE_PERCENT}, while the same player has an own SELL limit order resting
 *     above the price before that tick.</li>
 *     <li><b>Cycle:</b> a player sells items on a market that the same player bought there within
 *     {@code PUMP_DUMP_CYCLE_MINUTES}, with a profit over the average buy price above
 *     {@code PUMP_DUMP_CYCLE_PROFIT}.</li>
 * </ul>
 * Every hit goes through {@link TradeAlerts#raise} (server log, Discord feed, admin chat subject to the
 * per-admin toggle). Bot orders never reach this class (filtered in {@code ServerMarket}).
 * <p>
 * Cost per finished order: O(1) map work. The pump check scans the market's real sell book only after
 * a buy already passed the price-move threshold.
 */
public final class PumpDumpDetector {

    /**
     * One player's open buys on one market inside the cycle window.
     * ponytail: one lot per market whose window starts at its first buy (later buys add to it); FIFO lots
     * if exploiters learn to refresh the window by selling everything and buying again.
     */
    private static final class Lot {
        long startMillis;
        long volume;   // raw item volume bought and not yet sold again
        double cost;   // real money paid for {@link #volume}
    }

    // ponytail: static, master-only state, never persisted; expired lots are dropped when the player
    // trades again, so a player who never trades again keeps at most one lot per market until restart.
    private static final Map<UUID, Map<ItemID, Lot>> LOTS = new HashMap<>();

    private PumpDumpDetector() {}

    /** @return whether the detector runs at all ({@code PUMP_DUMP_ENABLED}, default on) */
    private static boolean enabled(StockMarketModSettings.Market s) {
        Boolean on = s.PUMP_DUMP_ENABLED.get();
        return on == null || on;
    }

    // ------------------------------------------------------------------ cycle check

    /**
     * Feeds one finished player order (consumed or canceled, fill state final, filled volume != 0) into
     * the cycle check and raises a cycle alert when it completes a profitable buy-sell cycle.
     * Called on the server thread.
     *
     * @param market the order's market
     * @param order  the finished player order
     * @param s      the live market settings
     */
    public static void onOrderFinished(ItemID market, Order order, StockMarketModSettings.Market s) {
        if (!enabled(s)) return;
        Float threshold = s.PUMP_DUMP_CYCLE_PROFIT.get();
        Long minutes = s.PUMP_DUMP_CYCLE_MINUTES.get();
        UUID player = order.getExecutorPlayerUUID();
        if (threshold == null || !(threshold > 0) || minutes == null || player == null) return;
        long windowMs = minutes * 60_000L;
        long now = System.currentTimeMillis();
        double money = MarketManager.convertToRealAmountStatic(order.getTransferredMoney());
        long volume = Math.abs(order.getFilledVolume());
        if (order.isBuyOrder()) {
            recordBuy(player, market, volume, -money, now, windowMs);
            return;
        }
        double profit = recordSell(player, market, volume, money, now, windowMs, threshold);
        if (Double.isNaN(profit)) return;
        String name = playerName(player);
        String amount = String.format(Locale.ROOT, "%.2f", profit);
        TradeAlerts.raise("Pump-and-dump cycle: player " + name + " (" + player + ") sold " + market.getName()
                        + " bought within " + minutes + " min for " + amount + " profit (threshold " + threshold + ")",
                Component.translatable("message.stockmarket.alert.pump_dump_cycle", name, market.getName(), amount, minutes));
    }

    /**
     * Pure bookkeeping of a buy, separated for tests. Drops the player's expired lots first.
     *
     * @param player    buyer
     * @param market    market
     * @param volume    raw item volume bought (&gt; 0)
     * @param cost      real money paid (&gt; 0)
     * @param nowMillis current time
     * @param windowMs  cycle window in ms
     */
    public static void recordBuy(UUID player, ItemID market, long volume, double cost, long nowMillis, long windowMs) {
        if (volume <= 0) return;
        Map<ItemID, Lot> lots = LOTS.computeIfAbsent(player, k -> new HashMap<>());
        lots.values().removeIf(l -> nowMillis - l.startMillis > windowMs);
        Lot lot = lots.computeIfAbsent(market, k -> {
            Lot l = new Lot();
            l.startMillis = nowMillis;
            return l;
        });
        lot.volume += volume;
        lot.cost += cost;
    }

    /**
     * Pure bookkeeping of a sell, separated for tests. The sold volume is matched against the player's
     * open lot on this market (average cost); the matched part leaves the lot. Volume sold beyond the
     * lot (items bought earlier or elsewhere) is ignored.
     *
     * @param player    seller
     * @param market    market
     * @param volume    raw item volume sold (&gt; 0)
     * @param proceeds  real money received
     * @param nowMillis current time
     * @param windowMs  cycle window in ms
     * @param threshold profit threshold in real money
     * @return the cycle profit when it exceeds {@code threshold}, else {@link Double#NaN}
     */
    public static double recordSell(UUID player, ItemID market, long volume, double proceeds,
                                    long nowMillis, long windowMs, double threshold) {
        Map<ItemID, Lot> lots = LOTS.get(player);
        Lot lot = lots == null ? null : lots.get(market);
        if (lot == null || volume <= 0) return Double.NaN;
        if (nowMillis - lot.startMillis > windowMs) {
            drop(player, lots, market);
            return Double.NaN;
        }
        long matched = Math.min(volume, lot.volume);
        double basis = lot.cost * matched / lot.volume;
        double profit = proceeds * matched / volume - basis;
        lot.volume -= matched;
        lot.cost -= basis;
        if (lot.volume <= 0) drop(player, lots, market);
        return profit > threshold ? profit : Double.NaN;
    }

    private static void drop(UUID player, Map<ItemID, Lot> lots, ItemID market) {
        lots.remove(market);
        if (lots.isEmpty()) LOTS.remove(player);
    }

    // ------------------------------------------------------------------ pump check

    /**
     * Runs the pump check for the player MARKET buys a market executed in one tick, after its matching
     * pass. Raises one pump alert per flagged buy.
     *
     * @param market     the market
     * @param buys       finished player MARKET buys of this tick (with fills)
     * @param startPrice market price before the tick's matching pass
     * @param endPrice   market price after it
     * @param sellBook   the market's resting real sell limit orders
     * @param s          the live market settings
     */
    public static void checkPumps(ItemID market, Collection<Order> buys, long startPrice, long endPrice,
                                  Collection<Order> sellBook, StockMarketModSettings.Market s) {
        if (buys.isEmpty() || !enabled(s)) return;
        Float percent = s.PUMP_DUMP_PRICE_MOVE_PERCENT.get();
        if (percent == null || !isPriceMove(startPrice, endPrice, percent)) return;
        for (Order buy : buys) {
            UUID player = buy.getExecutorPlayerUUID();
            if (player == null || !hasOwnSellAbove(player, startPrice, sellBook)) continue;
            String name = playerName(player);
            String move = String.format(Locale.ROOT, "%.1f", (endPrice - startPrice) * 100.0 / startPrice);
            TradeAlerts.raise("Pump-and-dump pump: player " + name + " (" + player + ") market-bought " + market.getName()
                            + " moving the price " + move + "% (" + startPrice + " -> " + endPrice
                            + " raw, threshold " + percent + "%) with an own sell order resting above " + startPrice,
                    Component.translatable("message.stockmarket.alert.pump_dump_pump", name, market.getName(), move));
        }
    }

    /**
     * @param startPrice price before (raw)
     * @param endPrice   price after (raw)
     * @param percent    threshold in percent; &lt;= 0 = pump check off
     * @return true if the price rose by more than {@code percent}
     */
    public static boolean isPriceMove(long startPrice, long endPrice, double percent) {
        return percent > 0 && startPrice > 0 && (endPrice - startPrice) * 100.0 / startPrice > percent;
    }

    /**
     * @param player     the buyer
     * @param startPrice price before the buy (raw)
     * @param sellBook   resting real sell limit orders
     * @return true if the player has an own SELL order resting above {@code startPrice}
     */
    public static boolean hasOwnSellAbove(UUID player, long startPrice, Collection<Order> sellBook) {
        for (Order o : sellBook)
            if (player.equals(o.getExecutorPlayerUUID()) && o.isSellOrder() && o.getStartPrice() > startPrice)
                return true;
        return false;
    }

    private static String playerName(UUID player) {
        ServerPlayer online = ServerPlayerUtilities.getOnlinePlayer(player);
        return online != null ? online.getName().getString() : player.toString();
    }
}
