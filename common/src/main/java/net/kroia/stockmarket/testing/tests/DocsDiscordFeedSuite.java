package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.discord.DiscordWebhooks;
import net.kroia.modutilities.setting.Setting;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.compat.StockMarketDiscord;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SM-359: posts the example Discord feed of the docs picture {@code shared/discord-feed.png} to a real webhook: a market
 * buy of 64 diamonds, a market sell of 128 wheat and the profit alert the sale raises, all of the client player
 * through the real order and alert paths and the shipped en_us templates.
 * <p>
 * The posts are first captured at {@link StockMarketDiscord#sink} and checked (exactly these three, fully filled, no
 * test wording); only then are they handed to {@link DiscordWebhooks#post}, in that order. A failed check posts
 * nothing, so a rerun cannot duplicate messages in the channel. Webhook and config handling:
 * {@link DiscordIntegrationTestSuite#withLiveWebhook}; skips when {@code MC_DISCORD_TEST_WEBHOOK_FILE} is unset.
 * <p>
 * Opt-in: posts to a real channel, so it runs only when named with {@code --only stockmarket/sm_docs_discord}
 * ({@link #onDemand()}), never with the other docs shots. Start the client with {@code --client-name MinerMia}
 * (the name shows in every post). Needs the integrated server (sp cells).
 */
public class DocsDiscordFeedSuite extends DocsScreenshotSuite {

    /** The invented player of the picture. */
    private static final String PLAYER = "MinerMia";
    /** Routed to the webhook; market-exchange stays off. */
    private static final List<String> TYPES = List.of("market-buy", "market-sell", "market-alert");
    /** Profit alert threshold for the sale: 128 wheat at about 2 cross it. */
    private static final float THRESHOLD = 100f;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DOCS_DISCORD;
    }

    @Override
    public void registerTests() {
        addTest("discord_feed", this::test_discordFeed);
    }

    private TestResult test_discordFeed() {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        MinecraftServer server = Gui.onClient(() -> Minecraft.getInstance().getSingleplayerServer());
        if (server == null || b == null || b.MARKET_MANAGER.isSlave()) return skip("needs the integrated server (sp cell)");
        return DiscordIntegrationTestSuite.withLiveWebhook(b, TYPES, () -> tradeAndPost(b, server));
    }

    /** Body of the live harness: setup, the two trades with the sink capturing, the check, then the three posts. */
    private TestResult tradeAndPost(StockMarketModBackend.ServerInstances b, MinecraftServer server) {
        String name = Gui.onClient(() -> Minecraft.getInstance().player.getGameProfile().getName());
        if (!PLAYER.equals(name)) return fail("start the client with --client-name " + PLAYER + " (is " + name + "); nothing posted");
        UUID self = Gui.onClient(() -> Minecraft.getInstance().player.getUUID());
        var client = StockMarketModBackend.getClientInstances();

        // the diamond and wheat markets, created on the server thread if the world lacks them (SM-369)
        Map<Item, ItemID> ids = new LinkedHashMap<>();
        for (Item item : List.of(Items.DIAMOND, Items.WHEAT)) {
            ItemID id = await(ItemIDManager.registerItemStackServerSide(new ItemStack(item)), WAIT_MS);
            if (!Boolean.TRUE.equals(await(b.MARKET_MANAGER.getAsync().marketExistsAsync(id), WAIT_MS))
                    && await(onServer(server, () -> b.MARKET_MANAGER.getAsync().createMarketAsync(id)), WAIT_MS) == null)
                return fail("market " + id + " not created; nothing posted");
            ids.put(item, id);
        }
        await(Gui.onClient(() -> client.MARKET_MANAGER.requestMarkets()), WAIT_MS);
        if (!Gui.waitUntil(() -> ids.values().stream().allMatch(id -> client.MARKET_MANAGER.getMarket(id) != null), 200))
            return fail("the client has no diamond or wheat market; nothing posted");
        ClientMarket diamond = Gui.onClient(() -> client.MARKET_MANAGER.getMarket(ids.get(Items.DIAMOND)));
        ClientMarket wheat = Gui.onClient(() -> client.MARKET_MANAGER.getMarket(ids.get(Items.WHEAT)));

        // money for the buy, wheat for the sale
        IAsyncBankManager bank = BankSystemMod.getAPI().getServerBankManager().getAsync();
        long scale = await(bank.getItemFractionScaleFactorAsync(), WAIT_MS);
        ItemID money = await(b.MARKET_MANAGER.getAsync().getTradingCurrencyIDAsync(), WAIT_MS);
        Integer account = await(bank.createPersonalBankAccountGetAccountNrAsync(self), WAIT_MS);
        if (account == null || account < 0) return fail("no personal account: " + account + "; nothing posted");
        String error = deposit(bank, self, money, 30_000L * scale);
        if (error == null) error = deposit(bank, self, ids.get(Items.WHEAT), 128L * scale);
        if (error != null) return fail(error + "; nothing posted");

        // the trades, captured (the harness restores the sink)
        List<Map.Entry<String, Map<String, String>>> posts = new CopyOnWriteArrayList<>();
        StockMarketDiscord.sink = (type, v) -> posts.add(Map.entry(type, v));
        Setting<Float> threshold = b.SERVER_SETTINGS.MARKET.PROFIT_ALERT_THRESHOLD;
        Float before = threshold.get();
        try {
            threshold.set(0f); // the buy's cost stays out of the profit window: the sale alone crosses the threshold
            error = order(() -> diamond.createMarketBuyOrder(account, 64), "market buy of 64 diamonds");
            if (error == null && !Gui.waitUntil(() -> !posts.isEmpty(), 200)) error = "no market-buy post";
            threshold.set(THRESHOLD);
            if (error == null) error = order(() -> wheat.createMarketSellOrder(account, -128), "market sell of 128 wheat");
            if (error == null && !Gui.waitUntil(() -> posts.size() >= 3, 200)) error = "no market-sell and alert posts";
            Gui.waitTicks(20); // anything that would follow
        } finally {
            threshold.set(before);
        }
        if (error != null) return fail(error + "; captured " + posts + "; nothing posted");
        error = check(posts, ids);
        if (error != null) return fail(error + "; captured " + posts + "; nothing posted");

        for (Map.Entry<String, Map<String, String>> p : posts)
            DiscordWebhooks.post(StockMarketMod.MOD_ID, p.getKey(), p.getValue());
        // ponytail: no public drain hook on DiscordWebhooks; fixed wait for the sender (as live_webhook_posts)
        pump(10_000);
        Map<String, String> buy = posts.get(0).getValue(), sell = posts.get(1).getValue();
        return pass("posted market-buy " + buy.get("amount") + " " + buy.get("item") + " @ " + buy.get("price")
                + ", market-sell " + sell.get("amount") + " " + sell.get("item") + " @ " + sell.get("price")
                + ", market-alert \"" + posts.get(2).getValue().get("message") + "\"");
    }

    /** @return null if {@code posts} are exactly the buy, the sale and the profit alert of the picture, else why not */
    private static String check(List<Map.Entry<String, Map<String, String>>> posts, Map<Item, ItemID> ids) {
        if (!posts.stream().map(Map.Entry::getKey).toList().equals(TYPES))
            return "expected the types " + TYPES;
        Map<String, String> buy = posts.get(0).getValue(), sell = posts.get(1).getValue();
        String alert = posts.get(2).getValue().get("message");
        if (!PLAYER.equals(buy.get("player")) || !"64".equals(buy.get("amount")) || !ids.get(Items.DIAMOND).getName().equals(buy.get("item_id")))
            return "the buy is not 64 diamonds of " + PLAYER;
        if (!PLAYER.equals(sell.get("player")) || !"128".equals(sell.get("amount")) || !ids.get(Items.WHEAT).getName().equals(sell.get("item_id")))
            return "the sale is not 128 wheat of " + PLAYER;
        if (alert == null || !alert.startsWith("Player " + PLAYER + " (") || !alert.contains(" realized "))
            return "the alert is not the profit alert of " + PLAYER;
        String all = posts.toString().toLowerCase(Locale.ROOT);
        for (String word : List.of("test", "cell", "please ignore"))
            if (all.contains(word)) return "a post contains \"" + word + "\"";
        return null;
    }
}
