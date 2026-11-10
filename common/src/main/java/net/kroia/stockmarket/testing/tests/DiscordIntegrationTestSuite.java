package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.filter.UUIDFilter;
import net.kroia.stockmarket.minecraft.compat.StockMarketDiscord;
import net.kroia.stockmarket.stockmarket.market.OrderLedger;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.TradeAlerts;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * T-217 — Discord feed ({@link StockMarketDiscord}) through its {@code sink} seam; never sends a real webhook.
 * <p>
 * Covers the generated config file, a player buy and sell through the real order-finished path (resting order
 * with fills, canceled via {@code ServerMarket.cancelOrder} -> {@code saveOrderRecord}), unfilled and bot orders,
 * the live switch, and alerts through {@link TradeAlerts#raise}.
 * <p>
 * <b>Server type:</b> master only (the backend is only set on the master; a slave never registers the feed).
 * Scratch market, account and user; the order history rows of the scratch user are deleted afterwards and the
 * BankSystem trade-ledger rows go to a no-op sink.
 */
public class DiscordIntegrationTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    /** @param backend the master's server instances (set by DevBootstrap on the master only) */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        DiscordIntegrationTestSuite.backend = backend;
    }

    private static final String PLAYER_NAME = "DiscordTestTrader";
    private static final String MARKER = "discord-integration-test-marker";

    private final TestFixture fixture = new TestFixture("Discord");
    private IServerMarket market;
    private ItemID itemID;
    private int accountNr;
    private UUID player;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MOD_SETTINGS;
    }

    @Override
    public void registerTests() {
        addTest("config_file_lists_types", () -> master(this::configFile));
        addTest("player_buy_posts_values", () -> master(this::playerBuy));
        addTest("player_sell_posts_sell", () -> master(this::playerSell));
        addTest("unfilled_and_bot_post_nothing", () -> master(this::unfilledAndBot));
        addTest("switch_off_posts_nothing", () -> master(this::switchOff));
        addTest("alert_posts_message", () -> master(this::alert));
    }

    @Override
    public void setup() {
        if (backend == null)
            return;
        fixture.setBackend(backend);
        market = fixture.createMarket("main");
        itemID = market.getItemID();
        IServerBankAccount account = fixture.createBankAccount("trader");
        accountNr = account.getAccountNumber();
        account.createBank(itemID, 100);
        account.createBank(ItemID.getOrRegisterFromItemStackServerSide_direct(BankSystemItems.MONEY.get().getDefaultInstance()), 10000);
        player = UUID.randomUUID();
        manager().onPlayerJoin(player, PLAYER_NAME);
    }

    @Override
    public void teardown() {
        try {
            if (market != null) {
                market.test_clearOrderbook();
                market.test_clearIncomingOrderBuffers();
            }
            if (player != null && backend != null) {
                manager().removeUser(player);
                if (backend.ORDER_RECORD_MANAGER != null)
                    await(backend.ORDER_RECORD_MANAGER.removeHistory(Optional.empty(), Optional.empty(),
                            Optional.of(new UUIDFilter(player)), Optional.empty()), 5000);
            }
        } finally {
            market = null;
            player = null;
            fixture.cleanup();
        }
    }

    // ------------------------------------------------------------------ tests

    /** The master start registered the feed: stockmarket.json lists the three types. */
    private TestResult configFile() {
        Path file = Platform.getConfigFolder().resolve("modutilities").resolve("discord").resolve(StockMarketMod.MOD_ID + ".json");
        try {
            JsonObject types = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("types");
            for (String t : StockMarketDiscord.TYPES)
                if (types == null || !types.has(t))
                    return fail("missing type " + t + " in " + file);
            return pass("stockmarket.json lists " + StockMarketDiscord.TYPES);
        } catch (Exception e) {
            return fail("cannot read " + file + ": " + e);
        }
    }

    /** A canceled player buy with fills posts one market-buy with the player's values. */
    private TestResult playerBuy() {
        var posts = capture(true, () -> filledAndCanceled(500, 200, -1800)); // 2 items for 18 -> avg 9
        if (posts.size() != 1 || !"market-buy".equals(posts.get(0).getKey()))
            return fail("posts " + posts);
        Map<String, String> v = posts.get(0).getValue();
        if (!PLAYER_NAME.equals(v.get("player")) || !player.toString().equals(v.get("player_uuid"))
                || !("#" + accountNr).equals(v.get("account")) || !itemID.getName().equals(v.get("item_id"))
                || v.get("item") == null || v.get("item").isEmpty()
                || !"2".equals(v.get("amount")) || !"9".equals(v.get("price"))
                || v.get("time") == null || !v.get("time").matches("<t:\\d+:f>") || v.containsKey("interMarketGroup"))
            return fail("values " + v);
        return pass("1 market-buy: player, uuid, account, item, amount 2, price 9, time");
    }

    /** A canceled player sell with fills posts one market-sell with the absolute amount. */
    private TestResult playerSell() {
        var posts = capture(true, () -> filledAndCanceled(-500, -200, 4000)); // 2 items for 40 -> avg 20
        if (posts.size() != 1 || !"market-sell".equals(posts.get(0).getKey()))
            return fail("posts " + posts);
        Map<String, String> v = posts.get(0).getValue();
        if (!"2".equals(v.get("amount")) || !"20".equals(v.get("price")))
            return fail("values " + v);
        return pass("1 market-sell: amount 2, price 20");
    }

    /** A cancel without fills and a bot order with fills post nothing. */
    private TestResult unfilledAndBot() {
        var posts = capture(true, () -> {
            filledAndCanceled(500, 0, 0);
            Order bot = new Order(itemID, Order.Type.LIMIT, 500, 90, System.nanoTime());
            bot.edit(200, -1800);
            StockMarketDiscord.postOrder(bot.getHistoricalRecord());
        });
        return posts.isEmpty() ? pass("unfilled cancel and bot record post nothing") : fail("posts " + posts);
    }

    /** With DISCORD_ENABLED off, a filled player order posts nothing. */
    private TestResult switchOff() {
        var posts = capture(false, () -> filledAndCanceled(500, 200, -1800));
        return posts.isEmpty() ? pass("DISCORD_ENABLED=false -> nothing posted") : fail("posts " + posts);
    }

    /** TradeAlerts.raise posts one market-alert with the log text. */
    private TestResult alert() {
        String text = MARKER + " test alert, please ignore";
        var posts = capture(true, () -> TradeAlerts.raise(text, Component.literal(text)));
        if (posts.size() != 1 || !"market-alert".equals(posts.get(0).getKey()))
            return fail("posts " + posts);
        Map<String, String> v = posts.get(0).getValue();
        if (!text.equals(v.get("message")) || v.get("time") == null || !v.get("time").matches("<t:\\d+:f>"))
            return fail("values " + v);
        return pass("1 market-alert with message and time");
    }

    // ------------------------------------------------------------------ helpers

    private TestResult master(Supplier<TestResult> body) {
        if (backend == null)
            return skip("master only");
        if (market == null || player == null)
            return fail("setup missing");
        return body.get();
    }

    private static ServerMarketManager manager() {
        return backend.MARKET_MANAGER.getServerMarketManager();
    }

    /**
     * Puts a resting player limit order into the book, books fills on it and cancels it through the normal
     * cancel path, which finishes the order (saveOrderRecord -> Discord).
     *
     * @param volume       target volume (raw, &gt; 0 = buy)
     * @param filledVolume filled volume to book (raw, same sign as volume; 0 = no fill)
     * @param money        transferred money of the fills (raw, negative for a buy)
     */
    private void filledAndCanceled(long volume, long filledVolume, long money) {
        Order order = new Order(itemID, Order.Type.LIMIT, volume, volume > 0 ? 90 : 200, System.nanoTime(), player, accountNr);
        market.getOrderbook().putOrder(order);
        order.edit(filledVolume, money);
        if (!market.cancelOrder(player, order.getTime(), order.getType(), order.getStartPrice(), order.getTargetVolume()))
            throw new IllegalStateException("cancel did not find the order");
    }

    /**
     * Runs {@code body} with the feed switched {@code on} and the sink capturing; returns the posts of this suite
     * (the scratch player's orders, or alerts containing MARKER). Restores the switch, the sink and the ledger sink.
     */
    private List<Map.Entry<String, Map<String, String>>> capture(boolean on, Runnable body) {
        Setting<Boolean> setting = backend.SERVER_SETTINGS.INTEGRATIONS.DISCORD_ENABLED;
        boolean before = setting.get();
        BiConsumer<String, Map<String, String>> previous = StockMarketDiscord.sink;
        List<Map.Entry<String, Map<String, String>>> posts = new ArrayList<>();
        StockMarketDiscord.sink = (type, v) -> {
            if (player.toString().equals(v.get("player_uuid")) || (v.get("message") != null && v.get("message").contains(MARKER)))
                synchronized (posts) { posts.add(Map.entry(type, v)); }
        };
        OrderLedger.Writer.testSink = e -> {}; // keep the scratch fills out of BankSystem's trade ledger
        setting.set(on);
        try {
            body.run();
        } finally {
            setting.set(before);
            StockMarketDiscord.sink = previous;
            OrderLedger.Writer.testSink = null;
        }
        synchronized (posts) { return new ArrayList<>(posts); }
    }
}
