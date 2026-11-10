package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.discord.DiscordWebhooks;
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
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.TradeAlerts;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * T-217 — Discord feed ({@link StockMarketDiscord}) through its {@code sink} seam; never sends a real webhook,
 * except {@code live_webhook_posts} (see below).
 * <p>
 * Covers the generated config file, the default templates (no account or UUID fields), a player buy and sell
 * through the real order-finished path (resting order with fills, canceled via {@code ServerMarket.cancelOrder}
 * -> {@code saveOrderRecord}), an inter-market order as one market-exchange post, fully and partially filled
 * (canceled via {@code ServerMarketManager.cancelInterMarketOrder} -> {@code saveInterMarketOrderRecord}), unfilled
 * and bot orders, the live switch, and alerts through {@link TradeAlerts#raise}.
 * <p>
 * <b>Server type:</b> master only (the backend is only set on the master; a slave never registers the feed), except
 * the template check, which runs everywhere. Scratch markets, account and user; the order history rows of the
 * scratch user are deleted afterwards and the BankSystem trade-ledger rows go to a no-op sink.
 * <p>
 * {@code live_webhook_posts} runs only when env var {@value #LIVE_ENV} names an existing text file holding a webhook
 * URL (read at runtime, so the URL never lands in the repo, a launch command or a log); otherwise it skips. It posts
 * one embed per type through the real {@link DiscordWebhooks} pipeline, to check the look in a Discord channel.
 */
public class DiscordIntegrationTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    /** @param backend the master's server instances (set by DevBootstrap on the master only) */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        DiscordIntegrationTestSuite.backend = backend;
    }

    private static final String PLAYER_NAME = "DiscordTestTrader";
    private static final String MARKER = "discord-integration-test-marker";
    /** Env var naming the file that holds the live-test webhook URL (the URL itself is never in the environment). */
    private static final String LIVE_ENV = "MC_DISCORD_TEST_WEBHOOK_FILE";
    /** Marker of the live test's alert; its trades are recognized by the scratch player's UUID. */
    private static final String LIVE = "StockMarket live webhook test";

    private final TestFixture fixture = new TestFixture("Discord");
    private IServerMarket market;
    /** Second scratch market: the buy leg of the inter-market tests (the sell leg is {@link #market}). */
    private IServerMarket other;
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
        addTest("default_templates_hide_account_and_uuid", this::defaultTemplates);
        addTest("player_buy_posts_values", () -> master(this::playerBuy));
        addTest("player_sell_posts_sell", () -> master(this::playerSell));
        addTest("unfilled_and_bot_post_nothing", () -> master(this::unfilledAndBot));
        addTest("inter_market_posts_one_exchange", () -> master(this::interMarketExchange));
        addTest("inter_market_partial_fill_posts_what_filled", () -> master(this::interMarketPartialFill));
        addTest("inter_market_unfilled_and_bot_post_nothing", () -> master(this::interMarketUnfilledAndBot));
        addTest("switch_off_posts_nothing", () -> master(this::switchOff));
        addTest("alert_posts_message", () -> master(this::alert));
        addTest("live_webhook_posts", () -> master(this::live));
    }

    @Override
    public void setup() {
        if (backend == null)
            return;
        fixture.setBackend(backend);
        market = fixture.createMarket("main");
        other = fixture.createMarket("other");
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
            // A failed inter-market test may leave its order queued: cancel it with the feed off and the sink captured.
            if (other != null && player != null && backend != null)
                capture(false, () -> manager().cancelInterMarketOrdersForMarket(other.getItemID()));
            if (market != null) {
                market.test_clearOrderbook();
                market.test_clearIncomingOrderBuffers();
            }
            if (player != null && backend != null) {
                manager().removeUser(player);
                if (backend.ORDER_RECORD_MANAGER != null && backend.DATABASE_MANAGER != null) {
                    // Earlier suites can leave a long db-worker backlog: drain it first, so the 5 s below
                    // time the delete itself, not the queue ahead of it.
                    if (!pumpUntil(TestFixture.dbBarrier(backend.DATABASE_MANAGER)::isDone, TestFixture.DB_DRAIN_TIMEOUT_MS))
                        throw new IllegalStateException("db-worker queue did not drain within "
                                + TestFixture.DB_DRAIN_TIMEOUT_MS + " ms");
                    await(backend.ORDER_RECORD_MANAGER.removeHistory(Optional.empty(), Optional.empty(),
                            Optional.of(new UUIDFilter(player)), Optional.empty()), 5000);
                }
            }
        } finally {
            market = null;
            other = null;
            player = null;
            fixture.cleanup();
        }
    }

    // ------------------------------------------------------------------ tests

    /** The master start registered the feed: stockmarket.json lists every type. */
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

    /**
     * The shipped market-buy / market-sell / market-exchange templates of every language show no account number and
     * no UUID (an admin adds them via {@code fields} in stockmarket.json), but keep every other field.
     */
    private TestResult defaultTemplates() {
        List<String> trade = List.of("{player}", "{item}", "{item_id}", "{amount}", "{price}", "{time}");
        Map<String, List<String>> requiredByType = Map.of("market-buy", trade, "market-sell", trade,
                "market-exchange", List.of("{player}", "{sold_item}", "{sold_item_id}", "{sold_amount}", "{sold_price}",
                        "{bought_item}", "{bought_item_id}", "{bought_amount}", "{bought_price}", "{leftover}",
                        "{currency}", "{interMarketGroup}", "{time}"));
        for (String lang : List.of("en_us", "de_de", "de_ch", "zh_cn")) {
            // classpath, not Mod.findResource: in a Loom dev run the common module's resources are not part of the
            // mod container's root paths (they are in the release jar)
            try (var in = DiscordIntegrationTestSuite.class.getResourceAsStream("/assets/stockmarket/discord/" + lang + ".json")) {
                if (in == null)
                    return fail("missing template file " + lang + ".json");
                JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : requiredByType.entrySet()) {
                    String type = e.getKey();
                    if (!root.has(type))
                        return fail(lang + " lacks " + type);
                    String values = root.getAsJsonObject(type).getAsJsonArray("fields").toString();
                    if (values.contains("{account}") || values.contains("{player_uuid}"))
                        return fail(lang + " " + type + " shows account or UUID: " + values);
                    for (String r : e.getValue())
                        if (!values.contains(r))
                            return fail(lang + " " + type + " lacks " + r);
                }
            } catch (Exception e) {
                return fail("cannot read " + lang + ".json: " + e);
            }
        }
        return pass("4 languages: market-buy/-sell/-exchange without {account} and {player_uuid}, all other fields kept");
    }

    /**
     * A canceled player buy with fills posts one market-buy with the player's values. {@code account} and
     * {@code player_uuid} are still provided (for admin templates) although the default embed omits them.
     */
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
        return pass("1 market-buy: player, uuid and account values, item, amount 2, price 9, time");
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

    /**
     * A canceled player inter-market order with fills on both legs posts exactly one market-exchange (no
     * market-buy / market-sell per leg) with the sold and bought values and the leftover money. A buy-direction
     * order: {@code amount} is the bought quantity the player asked for.
     */
    private TestResult interMarketExchange() {
        InterMarketOrder imo = filledExchange();
        var posts = capture(true, () -> putAndCancel(imo));
        if (posts.size() != 1 || !"market-exchange".equals(posts.get(0).getKey()))
            return fail("expected 1 market-exchange, got " + posts);
        Map<String, String> v = posts.get(0).getValue();
        if (!PLAYER_NAME.equals(v.get("player")) || !("#" + accountNr).equals(v.get("account"))
                || !imo.getInterMarketGroupID().toString().equals(v.get("interMarketGroup"))
                || v.get("time") == null || !v.get("time").matches("<t:\\d+:f>")
                || v.get("currency") == null || v.get("currency").isBlank())
            return fail("player, account, group, time or currency " + v);
        if (!itemID.getName().equals(v.get("sold_item_id")) || v.get("sold_item") == null
                || !"1".equals(v.get("sold_amount")) || !"20".equals(v.get("sold_price")))
            return fail("sold values " + v);
        if (!other.getItemID().getName().equals(v.get("bought_item_id")) || v.get("bought_item") == null
                || !"2".equals(v.get("bought_amount")) || !"9".equals(v.get("bought_price")))
            return fail("bought values " + v);
        if (!"2".equals(v.get("leftover")) || !"5".equals(v.get("amount")))
            return fail("leftover / amount " + v);
        return pass("1 market-exchange: sold 1 @ 20, bought 2 @ 9, leftover 2, amount 5 (buy target)");
    }

    /**
     * A sell-direction inter-market order (buy target 0) that sold half its items and bought nothing posts what
     * happened: sold 1, bought 0, the proceeds as leftover; {@code amount} is the sold quantity the player asked
     * for. The cancel unlocks the unsold rest.
     */
    private TestResult interMarketPartialFill() {
        InterMarketOrder imo = new InterMarketOrder(other.getItemID(), itemID, Order.Type.MARKET,
                0, 0, 200, 0, System.nanoTime(), player, accountNr);
        imo.getSellOrder().edit(-100, 1500); // sold 1 of 2 for 15, bought nothing
        IServerBankAccount account = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(accountNr);
        var posts = capture(true, () -> {
            account.getBank(itemID).lockAmount(100); // the unsold item still locked by the order; the cancel unlocks it
            putAndCancel(imo);
        });
        if (posts.size() != 1 || !"market-exchange".equals(posts.get(0).getKey()))
            return fail("expected 1 market-exchange, got " + posts);
        Map<String, String> v = posts.get(0).getValue();
        if (!"1".equals(v.get("sold_amount")) || !"15".equals(v.get("sold_price"))
                || !"0".equals(v.get("bought_amount")) || !"0".equals(v.get("bought_price"))
                || !"15".equals(v.get("leftover")) || !"2".equals(v.get("amount")))
            return fail("values " + v);
        long locked = account.getBank(itemID).getLockedBalance();
        return locked == 0 ? pass("1 market-exchange: sold 1 @ 15, bought 0, leftover 15, amount 2 (sell target)")
                : fail("cancel left " + locked + " locked");
    }

    /** A canceled player inter-market order without fills, and a bot inter-market order with fills, post nothing. */
    private TestResult interMarketUnfilledAndBot() {
        InterMarketOrder unfilled = new InterMarketOrder(other.getItemID(), itemID, Order.Type.MARKET,
                500, 0, 100, 0, System.nanoTime(), player, accountNr);
        InterMarketOrder bot = new InterMarketOrder(other.getItemID(), itemID, Order.Type.MARKET,
                500, 0, 100, 0, System.nanoTime());
        bot.getBuyOrder().edit(200, -1800);
        bot.getSellOrder().edit(-100, 1500);
        IServerBankAccount account = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getBankAccount(accountNr);
        var posts = capture(true, () -> {
            account.getBank(itemID).lockAmount(100); // what the order request locks; the cancel unlocks it again
            if (!manager().putInterMarketOrder(unfilled) || !manager().putInterMarketOrder(bot))
                throw new IllegalStateException("inter-market order not enqueued");
            if (!manager().cancelInterMarketOrder(unfilled.getInterMarketGroupID(), player))
                throw new IllegalStateException("inter-market cancel did not find the order");
            manager().cancelInterMarketOrdersForMarket(other.getItemID()); // the bot order
        });
        if (!posts.isEmpty())
            return fail("posts " + posts);
        long locked = account.getBank(itemID).getLockedBalance();
        return locked == 0 ? pass("unfilled player order and bot order post nothing")
                : fail("cancel left " + locked + " locked");
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

    /**
     * Posts one embed per type (market-buy, market-sell, market-exchange, market-alert) to the webhook in the file named by
     * {@value #LIVE_ENV}, through the real {@link DiscordWebhooks} config and sender. Swaps webhooks.json and
     * stockmarket.json for the test and restores both byte for byte (finally). The URL is a secret: it never goes
     * into a result, log or exception text. Skips when the env var is unset or the file does not exist.
     */
    private TestResult live() {
        String file = System.getenv(LIVE_ENV);
        if (file == null || file.isBlank())
            return skip("env " + LIVE_ENV + " not set");
        Path secret = Path.of(file.strip());
        if (!Files.isRegularFile(secret))
            return skip("env " + LIVE_ENV + ": file not found");
        String url;
        try {
            url = Files.readString(secret, StandardCharsets.UTF_8).strip();
        } catch (IOException | RuntimeException e) { // no e text: keep file content out of the result
            return fail("env " + LIVE_ENV + ": cannot read the file (" + e.getClass().getSimpleName() + ")");
        }
        if (url.isEmpty())
            return fail("env " + LIVE_ENV + ": the file is blank");
        Path dir = Platform.getConfigFolder().resolve("modutilities").resolve("discord");
        Path hooksFile = dir.resolve("webhooks.json"), routesFile = dir.resolve(StockMarketMod.MOD_ID + ".json");
        byte[] oldHooks, oldRoutes;
        try {
            oldHooks = Files.exists(hooksFile) ? Files.readAllBytes(hooksFile) : null;
            oldRoutes = Files.exists(routesFile) ? Files.readAllBytes(routesFile) : null;
        } catch (IOException e) {
            return fail("cannot back up the Discord config: " + e);
        }
        Setting<Boolean> setting = backend.SERVER_SETTINGS.INTEGRATIONS.DISCORD_ENABLED;
        boolean before = setting.get();
        BiConsumer<String, Map<String, String>> previous = StockMarketDiscord.sink;
        TestResult result;
        boolean restored;
        try {
            result = livePosts(url, dir, hooksFile, routesFile, setting);
        } catch (IOException e) {
            result = fail("cannot write the Discord config: " + e);
        } finally {
            setting.set(before);
            StockMarketDiscord.sink = previous;
            OrderLedger.Writer.testSink = null;
            restored = put(hooksFile, oldHooks) & put(routesFile, oldRoutes); // both, even if the first fails
            DiscordWebhooks.reload();
        }
        return restored ? result : fail("could not restore webhooks.json / stockmarket.json in " + dir + "; fix by hand");
    }

    /** Body of {@link #live()}: writes the test config, posts every type once, waits for the sender. Caller restores. */
    private TestResult livePosts(String url, Path dir, Path hooksFile, Path routesFile, Setting<Boolean> setting) throws IOException {
        JsonObject hooks = new JsonObject();
        hooks.addProperty("live-test", url);
        JsonObject hooksRoot = new JsonObject();
        hooksRoot.add("webhooks", hooks);
        JsonObject types = new JsonObject();
        for (String t : StockMarketDiscord.TYPES) {
            JsonObject route = new JsonObject();
            JsonArray names = new JsonArray();
            names.add("live-test");
            route.addProperty("enabled", true);
            route.add("webhooks", names);
            route.addProperty("minAmount", 0);
            types.add(t, route);
        }
        JsonObject routesRoot = new JsonObject();
        routesRoot.add("types", types);
        Files.createDirectories(dir);
        Files.writeString(hooksFile, hooksRoot.toString(), StandardCharsets.UTF_8);
        Files.writeString(routesFile, routesRoot.toString(), StandardCharsets.UTF_8);
        DiscordWebhooks.reload();
        if (!DiscordWebhooks.isActive())
            return fail("DiscordWebhooks inactive after reload (webhook URL rejected?)");

        // only this test's posts reach Discord: trades of the scratch player, the alert with the LIVE marker
        Map<String, Integer> forwarded = new ConcurrentHashMap<>();
        StockMarketDiscord.sink = (type, v) -> {
            String message = v.get("message");
            if (!player.toString().equals(v.get("player_uuid")) && (message == null || !message.contains(LIVE)))
                return;
            forwarded.merge(type, 1, Integer::sum);
            DiscordWebhooks.post(StockMarketMod.MOD_ID, type, v);
        };
        OrderLedger.Writer.testSink = e -> {}; // keep the scratch fills out of BankSystem's trade ledger
        setting.set(true);
        filledAndCanceled(500, 200, -1800);  // market-buy: 2 for 18 -> avg 9
        filledAndCanceled(-500, -200, 4000); // market-sell: 2 for 40 -> avg 20
        putAndCancel(filledExchange());      // market-exchange: sold 1 @ 20, bought 2 @ 9, leftover 2
        String alert = LIVE + ": test alert, please ignore";
        TradeAlerts.raise(alert, Component.literal(alert));
        for (String t : StockMarketDiscord.TYPES)
            if (forwarded.getOrDefault(t, 0) != 1)
                return fail("forwarded per type " + forwarded);
        // ponytail: no public drain hook on DiscordWebhooks; fixed wait for the sender (batching, rate limits).
        // Queued items keep their URL, so a slower send still goes out after the caller's restore.
        pump(10_000);
        int posted = forwarded.values().stream().mapToInt(Integer::intValue).sum();
        return pass("live webhook: posted " + posted);
    }

    /** Writes {@code content} to {@code file}, or deletes it if null (it did not exist). @return false on an I/O error */
    private static boolean put(Path file, @Nullable byte[] content) {
        try {
            if (content == null) Files.deleteIfExists(file);
            else Files.write(file, content);
            return true;
        } catch (IOException e) {
            return false;
        }
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
     * A buy-direction player inter-market order (buy 5 of {@link #other} for {@link #market}'s item) with fills on
     * both legs: sold the whole 1 for 20 (nothing left to unlock on cancel), bought 2 for 18, so 2 money is left over.
     */
    private InterMarketOrder filledExchange() {
        InterMarketOrder imo = new InterMarketOrder(other.getItemID(), itemID, Order.Type.MARKET,
                500, 0, 100, 0, System.nanoTime(), player, accountNr);
        imo.getSellOrder().edit(-100, 2000);
        imo.getBuyOrder().edit(200, -1800);
        return imo;
    }

    /** Enqueues {@code imo} and cancels it through the player cancel path, which closes it (saveInterMarketOrderRecord -> Discord). */
    private void putAndCancel(InterMarketOrder imo) {
        if (!manager().putInterMarketOrder(imo))
            throw new IllegalStateException("inter-market order not enqueued");
        if (!manager().cancelInterMarketOrder(imo.getInterMarketGroupID(), player))
            throw new IllegalStateException("inter-market cancel did not find the order");
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
