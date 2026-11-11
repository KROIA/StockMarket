package net.kroia.stockmarket.minecraft.compat;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.discord.DiscordWebhooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * T-217 — Discord webhook feed of StockMarket trades and alerts through ModUtilities' {@link DiscordWebhooks}.
 * <p>
 * Master / standalone only: {@link #register} runs at the master's server start and slaves never call it, so a
 * slave never posts. Gated live by {@code Integrations.DISCORD_ENABLED} (default off). Which types go to which
 * webhook is configured in {@code config/modutilities/discord/stockmarket.json}, where every type starts disabled;
 * the embeds come from {@code assets/stockmarket/discord/<lang>.json}.
 * <ul>
 *   <li>{@code market-buy} / {@code market-sell}: one post per finished player order against money (fully filled,
 *       or canceled after fills). Bot orders and orders without fills post nothing. {@code amount} is the item
 *       quantity, so the type's {@code minAmount} filters by quantity.</li>
 *   <li>{@code market-exchange}: one post per finished player inter-market order (item for item), see
 *       {@link #postExchange}. Its legs never post as market-buy / market-sell.</li>
 *   <li>{@code market-alert}: every {@link net.kroia.stockmarket.util.TradeAlerts} alert, with its log text.</li>
 * </ul>
 * Buy / sell values: {@code player}, {@code player_uuid}, {@code account}, {@code item}, {@code item_id},
 * {@code amount}, {@code price}, {@code time}. Exchange values: see {@link #postExchange}. Every number is a plain
 * real number ({@link #real}). The shipped templates leave {@code player_uuid} and {@code account} out (privacy);
 * an admin adds them on purpose through the type's {@code fields} override in {@code stockmarket.json}.
 * Callers run on the server thread (matching engine, cancel path, alerts).
 */
public final class StockMarketDiscord {

    /** Every message type this mod may post; registered with ModUtilities on the master. */
    public static final List<String> TYPES = List.of("market-buy", "market-sell", "market-exchange", "market-alert");

    /** Test seam: receives every finished post (DiscordWebhooks' own seams are package-private). */
    public static volatile BiConsumer<String, Map<String, String>> sink =
            (type, values) -> DiscordWebhooks.post(StockMarketMod.MOD_ID, type, values);

    /** The master's instances while registered; null on slaves and after {@link #shutdown()}. */
    private static volatile StockMarketModBackend.ServerInstances backend;

    private StockMarketDiscord() {}

    /**
     * Master / standalone server start: declares the types and creates the config files if missing.
     *
     * @param instances the master's server instances (settings and user names)
     */
    public static void register(StockMarketModBackend.ServerInstances instances) {
        backend = instances;
        DiscordWebhooks.register(StockMarketMod.MOD_ID, TYPES);
    }

    /** Server stop: nothing is posted until the next master start. */
    public static void shutdown() {
        backend = null;
    }

    /**
     * Posts one finished player order against money. No-op unless the feed is enabled on the master, and for
     * null records, records without a player (bot orders) and records without fills.
     *
     * @param record the order's historical record; amount and price are raw, amount &gt; 0 = buy, &lt; 0 = sell
     */
    public static void postOrder(@Nullable OrderRecordStruct record) {
        StockMarketModBackend.ServerInstances b = enabledBackend();
        if (b == null || record == null || record.user() == null || record.amount() == 0)
            return;
        try {
            ItemID id = new ItemID(record.itemID());
            Map<String, String> v = new HashMap<>();
            v.put("player", playerName(b, record.user()));
            v.put("player_uuid", record.user().toString());
            v.put("account", "#" + record.bankaccountID());
            v.put("item", itemName(id));
            v.put("item_id", id.getName());
            v.put("amount", real(Math.abs(record.amount())));
            v.put("price", real(record.price()));
            v.put("time", time(System.currentTimeMillis()));
            sink.accept(record.amount() > 0 ? "market-buy" : "market-sell", v);
        } catch (RuntimeException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Discord] order not posted: {}", e.toString());
        }
    }

    /**
     * T-228: posts one finished player inter-market order (item for item) as a single {@code market-exchange}
     * message: what was sold, what was bought and the money left over. Called once when the order closes (filled or
     * canceled), when both legs are final. No-op unless the feed is enabled on the master, for bot orders and when
     * neither leg filled; a partial fill posts what happened (e.g. sold 64, bought 0, the proceeds as leftover).
     * <p>
     * Values: {@code player}, {@code player_uuid}, {@code account}, {@code sold_item}, {@code sold_item_id},
     * {@code sold_amount}, {@code sold_price} (avg), {@code bought_item}, {@code bought_item_id},
     * {@code bought_amount}, {@code bought_price} (avg), {@code leftover} (money returned to the player: sell proceeds
     * minus buy cost), {@code currency} (its name), {@code amount}, {@code time}, {@code interMarketGroup}.
     * <p>
     * {@code amount} (what the type's {@code minAmount} compares) is the quantity the player specified, by the
     * direction rule of {@code InterMarketExecutor}: a sell-direction order (buy target 0) specifies the sold
     * quantity, every other order the bought quantity. For a sell-direction limit order the order request derives
     * that buy target from the sold quantity at the limit rate.
     *
     * @param order    the closed inter-market order; its legs hold the final fills and money
     * @param currency the trading currency ({@code leftover} is in it)
     */
    public static void postExchange(InterMarketOrder order, ItemID currency) {
        StockMarketModBackend.ServerInstances b = enabledBackend();
        UUID owner = order.getOwnerUUID();
        Order sold = order.getSellOrder(), bought = order.getBuyOrder();
        if (b == null || order.isBotOrder() || owner == null || (sold.getFilledVolume() == 0 && bought.getFilledVolume() == 0))
            return;
        try {
            Map<String, String> v = new HashMap<>();
            v.put("player", playerName(b, owner));
            v.put("player_uuid", owner.toString());
            v.put("account", "#" + order.getBankAccountNr());
            v.put("sold_item", itemName(sold.getItemID()));
            v.put("sold_item_id", sold.getItemID().getName());
            v.put("sold_amount", real(Math.abs(sold.getFilledVolume())));
            v.put("sold_price", real(sold.getAverageExecutionPrice()));
            v.put("bought_item", itemName(bought.getItemID()));
            v.put("bought_item_id", bought.getItemID().getName());
            v.put("bought_amount", real(Math.abs(bought.getFilledVolume())));
            v.put("bought_price", real(bought.getAverageExecutionPrice()));
            // the sell leg's money is positive (proceeds), the buy leg's negative (cost); the rest went to the player
            v.put("leftover", real(Math.max(0, sold.getTransferredMoney() + bought.getTransferredMoney())));
            v.put("currency", itemName(currency));
            v.put("amount", real(order.getTargetBuyVolume() == 0 ? order.getTargetSellVolume() : order.getTargetBuyVolume()));
            v.put("time", time(System.currentTimeMillis()));
            v.put("interMarketGroup", String.valueOf(order.getInterMarketGroupID()));
            sink.accept("market-exchange", v);
        } catch (RuntimeException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Discord] exchange not posted: {}", e.toString());
        }
    }

    /**
     * Posts one admin alert. No-op unless the feed is enabled on the master.
     *
     * @param text the alert's plain, language-independent log text
     */
    public static void alert(String text) {
        if (enabledBackend() == null)
            return;
        try {
            sink.accept("market-alert", Map.of("message", text, "time", time(System.currentTimeMillis())));
        } catch (RuntimeException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Discord] alert not posted: {}", e.toString());
        }
    }

    /** @return the registered master instances if {@code DISCORD_ENABLED} is on, else null */
    private static @Nullable StockMarketModBackend.ServerInstances enabledBackend() {
        StockMarketModBackend.ServerInstances b = backend;
        return b != null && b.SERVER_SETTINGS != null && b.SERVER_SETTINGS.INTEGRATIONS.DISCORD_ENABLED.get() ? b : null;
    }

    /** StockMarket user name, else the online player's name, else the UUID. */
    private static String playerName(StockMarketModBackend.ServerInstances b, UUID uuid) {
        ServerMarketManager m = b.MARKET_MANAGER == null ? null : b.MARKET_MANAGER.getServerMarketManager();
        String name = m == null ? null : m.getUserName(uuid);
        return name != null ? name : ServerStockMarketCommandHandler.tryGetPlayerName(uuid);
    }

    /** The item's display name, else its ID name. */
    private static String itemName(ItemID id) {
        ItemStack stack = id.getStackTemplate();
        return stack.isEmpty() ? id.getName() : stack.getHoverName().getString();
    }

    /** Raw amount as a plain real number without trailing zeros (locale independent), e.g. 250 -> "2.5". */
    static String real(long raw) {
        return BigDecimal.valueOf(MarketManager.convertToRealAmountStatic(raw)).stripTrailingZeros().toPlainString();
    }

    /** Discord timestamp, rendered in each reader's time zone. */
    private static String time(long ms) {
        return "<t:" + ms / 1000 + ":f>";
    }
}
