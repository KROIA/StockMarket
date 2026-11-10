package net.kroia.stockmarket.minecraft.compat;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.discord.DiscordWebhooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.command.ServerStockMarketCommandHandler;
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
 *   <li>{@code market-buy} / {@code market-sell}: one post per finished player order (fully filled, or canceled
 *       after fills), one per leg of an inter-market order. Bot orders and orders without fills post nothing.
 *       {@code amount} is the item quantity, so the type's {@code minAmount} filters by quantity.</li>
 *   <li>{@code market-alert}: every {@link net.kroia.stockmarket.util.TradeAlerts} alert, with its log text.</li>
 * </ul>
 * Callers run on the server thread (matching engine, cancel path, alerts).
 */
public final class StockMarketDiscord {

    /** Every message type this mod may post; registered with ModUtilities on the master. */
    public static final List<String> TYPES = List.of("market-buy", "market-sell", "market-alert");

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
     * Posts one finished player order or inter-market leg. No-op unless the feed is enabled on the master, and for
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
            ItemStack stack = id.getStackTemplate();
            Map<String, String> v = new HashMap<>();
            v.put("player", playerName(b, record.user()));
            v.put("player_uuid", record.user().toString());
            v.put("account", "#" + record.bankaccountID());
            v.put("item", stack.isEmpty() ? id.getName() : stack.getHoverName().getString());
            v.put("item_id", id.getName());
            v.put("amount", real(Math.abs(record.amount())));
            v.put("price", real(record.price()));
            v.put("time", time(System.currentTimeMillis()));
            if (record.interMarketGroupID() != null) // a missing key leaves the field out of the embed
                v.put("interMarketGroup", record.interMarketGroupID().toString());
            sink.accept(record.amount() > 0 ? "market-buy" : "market-sell", v);
        } catch (RuntimeException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Discord] order not posted: {}", e.toString());
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

    /** Raw amount as a plain real number without trailing zeros (locale independent), e.g. 250 -> "2.5". */
    static String real(long raw) {
        return BigDecimal.valueOf(MarketManager.convertToRealAmountStatic(raw)).stripTrailingZeros().toPlainString();
    }

    /** Discord timestamp, rendered in each reader's time zone. */
    private static String time(long ms) {
        return "<t:" + ms / 1000 + ":f>";
    }
}
