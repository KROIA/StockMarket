package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.networking.request.NewsAdminRequest.EventDetails;
import net.kroia.stockmarket.news.ActiveNewsEvent;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsImpactEnvelope;
import net.kroia.stockmarket.news.NewsSequence;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.RuntimeStreamData;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game checks of the news admin output that the user would otherwise check by hand:
 * <ul>
 *   <li>MU-181: an event's permanent-shift backlog in {@code /stockmarket news info} (the permanent event and its
 *       counterpart), {@code /stockmarket news list} and the event details screen data.</li>
 *   <li>SM-366 / SM-382: {@code /stockmarket news info} shows the step durations and targets as they run (scaled by
 *       the scheduler's impactDurationMultiplier / impactStrengthMultiplier), and the details screen data (Duration
 *       column, step targets, step countdown, "Event remaining") matches a really triggered event.</li>
 * </ul>
 * Master only, and it needs an online player: the news admin commands take a player with the StockMarket admin
 * flag. The suite gives the first online player the flag for each test and restores it afterwards.
 */
public class NewsInGameCheckTestSuite extends TestSuite {

    /** MU-181 shipped pair: the permanent event and the counterpart that reverts it. */
    private static final String PERMANENT_EVENT = "amethyst_geode_discovery";
    private static final String COUNTERPART_EVENT = "amethyst_geode_exhausted";
    /** Shipped sequence event with duration ranges, a fixed duration and a hold step. */
    private static final String SEQUENCE_EVENT = "great_forest_fire";
    /**
     * Shipped legacy event with a negative announce delay: its impact starts at the trigger and the headline would
     * follow seconds later. No server tick runs during a test, so stopping it right away leaves no news record,
     * broadcast or fire record behind.
     */
    private static final String IMPACT_FIRST_EVENT = "henhouse_fire_egg_shortage";
    /** The legacy backlog the MU-181 migration gives a long-running world (its cap). */
    private static final int BACKLOG = 10;

    private final TestFixture fixture = new TestFixture("NewsInGameCheck");
    private ServerPlayer admin;
    private NewsPlugin plugin;
    private boolean wasAdmin;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_ADMIN;
    }

    @Override
    public void registerTests() {
        addTest("mu181_backlog_shown_in_info_list_and_details", this::test_backlogShown);
        addTest("sm366_sm382_info_shows_effective_steps", this::test_infoShowsEffectiveSteps);
        addTest("sm366_sm382_running_event_matches_details", this::test_runningEventMatchesDetails);
    }

    // ── MU-181 ───────────────────────────────────────────────────────────

    /**
     * MU-181: with a backlog of {@value #BACKLOG} unreverted shifts of the permanent event (what the migration leaves
     * on a long-running world), /news info of both events, /news list and the details data show it.
     */
    private TestResult test_backlogShown() {
        String notReady = prepare();
        if (notReady != null) return skip(notReady);
        ItemID market = null;
        NewsWorldRegistry registry = plugin.getNewsWorldRegistry();
        try {
            if (registry == null) return fail("the NewsPlugin has no world registry");
            TestResult r = requireDefinitions(PERMANENT_EVENT, COUNTERPART_EVENT);
            if (!r.passed()) return r;

            // The registry keys shifts by market only; a scratch ID keeps real markets untouched.
            market = fixture.scratchItemID("backlog");
            int pending = Math.max(registry.pendingUndos(PERMANENT_EVENT, null), BACKLOG);
            registry.addShifts(PERMANENT_EVENT, market, BACKLOG, 10.0);

            String permanentText = "permanent shift active, " + pending + " reversals pending, next eligible";
            String counterpartText = "reverts '" + PERMANENT_EVENT + "': " + pending + " reversals pending, next eligible";

            List<String> info = runAdminCommand("stockmarket news info " + PERMANENT_EVENT);
            r = assertTrue("/news info " + PERMANENT_EVENT + " shows the blocked backlog: " + info,
                    containsLine(info, "Permanent shift: [BLOCKED] " + permanentText));
            if (!r.passed()) return r;

            info = runAdminCommand("stockmarket news info " + COUNTERPART_EVENT);
            r = assertTrue("/news info " + COUNTERPART_EVENT + " shows the reversals it has to do: " + info,
                    containsLine(info, "] " + counterpartText));
            if (!r.passed()) return r;

            List<String> list = runAdminCommand("stockmarket news list");
            String listLine = list.stream().filter(l -> l.startsWith("- " + PERMANENT_EVENT)).findFirst().orElse("");
            r = assertTrue("/news list marks " + PERMANENT_EVENT + " with its backlog: '" + listLine + "'",
                    listLine.contains("[" + permanentText));
            if (!r.passed()) return r;

            // The details screen lists the status with the requirements (red/green by "met").
            List<EventDetails> details = NewsAdminRequest.buildEventDetails(plugin, plugin.getMarketInterfaces());
            EventDetails permanent = findDetails(details, PERMANENT_EVENT);
            EventDetails counterpart = findDetails(details, COUNTERPART_EVENT);
            r = assertTrue("details of " + PERMANENT_EVENT + " show the backlog as not met",
                    permanent != null && permanent.requirements().stream()
                            .anyMatch(s -> s.description().startsWith(permanentText) && !s.met()));
            if (!r.passed()) return r;
            r = assertTrue("details of " + COUNTERPART_EVENT + " show the reversals it has to do",
                    counterpart != null && counterpart.requirements().stream()
                            .anyMatch(s -> s.description().startsWith(counterpartText)));
            if (!r.passed()) return r;
            return pass("backlog of " + pending + " shown in /news info (both events), /news list and the details");
        } finally {
            if (registry != null && market != null) {
                while (registry.removeShift(PERMANENT_EVENT, market)) {
                    // takes the test's shifts off one by one; the market's normal price goes with the last
                }
            }
            restore();
        }
    }

    // ── SM-366 / SM-382 ──────────────────────────────────────────────────

    /**
     * SM-366 / SM-382: /news info shows every step of a sequence event, and the impact line of a legacy event, with
     * the effective durations and targets (authored x the scheduler multipliers; the authored target stays in the
     * parentheses). With the shipped multipliers (3 and 0.5) e.g. great_forest_fire's 1500 s step shows 4500 s.
     */
    private TestResult test_infoShowsEffectiveSteps() {
        String notReady = prepare();
        if (notReady != null) return skip(notReady);
        try {
            TestResult r = requireDefinitions(SEQUENCE_EVENT, IMPACT_FIRST_EVENT);
            if (!r.passed()) return r;
            double durationMultiplier = plugin.getLibrary().getSchedulerConfig().getImpactDurationMultiplier();
            double strengthMultiplier = plugin.getLibrary().getSchedulerConfig().getImpactStrengthMultiplier();
            r = assertTrue("the test world runs with non-1 multipliers (shipped 3 / 0.5), else nothing is checked: "
                    + durationMultiplier + " / " + strengthMultiplier,
                    durationMultiplier != 1.0 && strengthMultiplier != 1.0);
            if (!r.passed()) return r;

            List<String> info = runAdminCommand("stockmarket news info " + SEQUENCE_EVENT);
            int checked = 0;
            for (NewsEventDefinition.SequenceDefinition sequence : plugin.getLibrary().getDefinition(SEQUENCE_EVENT).getSequences()) {
                for (NewsEventDefinition.StepDefinition step : sequence.getSteps()) {
                    long min = Math.round(step.getDurationMinMs() * durationMultiplier) / 1000L;
                    long max = Math.round(step.getDurationMaxMs() * durationMultiplier) / 1000L;
                    String expected = "'" + step.getName() + "' " + (min == max ? min + " s" : min + ".." + max + " s")
                            + " -> " + NewsUiFormatting.formatFactorPercent(1.0 + step.getTargetFactor() * strengthMultiplier)
                            + " (targetFactor " + step.getTargetFactor() + ")";
                    r = assertTrue("/news info " + SEQUENCE_EVENT + " shows step " + expected + ": " + info,
                            containsLine(info, expected));
                    if (!r.passed()) return r;
                    checked++;
                }
            }

            NewsImpactEnvelope impact = plugin.getLibrary().getDefinition(IMPACT_FIRST_EVENT).getImpact();
            if (impact == null) return fail(IMPACT_FIRST_EVENT + " is no longer a legacy impact event");
            info = runAdminCommand("stockmarket news info " + IMPACT_FIRST_EVENT);
            String expected = "peak " + NewsUiFormatting.formatFactorPercent(1.0 + impact.getPeakFactor() * strengthMultiplier)
                    + " (peakFactor " + impact.getPeakFactor() + ")"
                    + ", rampUp " + Math.round(impact.getRampUpMillis() * durationMultiplier) / 1000L + " s"
                    + ", hold " + Math.round(impact.getHoldMillis() * durationMultiplier) / 1000L + " s";
            r = assertTrue("/news info " + IMPACT_FIRST_EVENT + " shows the scaled impact '" + expected + "': " + info,
                    containsLine(info, expected));
            if (!r.passed()) return r;
            return pass(checked + " sequence steps and the legacy impact line show effective values (x"
                    + durationMultiplier + " time, x" + strengthMultiplier + " strength)");
        } finally {
            restore();
        }
    }

    /**
     * SM-366 / SM-382: triggers a real event on a scratch market with the admin command and compares the running
     * event with what the details screen gets: every rolled step duration lies in its Duration column range, every
     * running step target equals the shown target, the step countdown stays within its step's range and "Event
     * remaining" (the live runtime stream value) fits the sum of the Duration column.
     */
    private TestResult test_runningEventMatchesDetails() {
        String notReady = prepare();
        if (notReady != null) return skip(notReady);
        ItemID market = null;
        long cooldownBefore = 0;
        try {
            TestResult r = requireDefinitions(IMPACT_FIRST_EVENT);
            if (!r.passed()) return r;
            if (plugin.isEventActive(IMPACT_FIRST_EVENT))
                return skip(IMPACT_FIRST_EVENT + " is already running in this world");
            cooldownBefore = plugin.getCooldownRemainingMs(IMPACT_FIRST_EVENT);

            // A scratch egg market (getName() is minecraft:egg, which the event's matcher targets).
            market = fixture.createMarket("egg", new ItemStack(Items.EGG)).getItemID();
            fixture.registerPluginCache(market);
            plugin.subscribeToMarket(market);

            List<String> reply = runAdminCommand("stockmarket news trigger " + IMPACT_FIRST_EVENT + " " + market.getShort());
            ActiveNewsEvent event = plugin.findActiveEvent(IMPACT_FIRST_EVENT);
            r = assertTrue("the trigger command started the event: " + reply, event != null);
            if (!r.passed()) return r;

            EventDetails details = findDetails(
                    NewsAdminRequest.buildEventDetails(plugin, plugin.getMarketInterfaces()), IMPACT_FIRST_EVENT);
            if (details == null) return fail("no details for " + IMPACT_FIRST_EVENT);
            EventDetails.SequenceInfo shown = details.sequences().stream()
                    .filter(s -> s.name().equals(event.getSequenceName())).findFirst().orElse(null);
            List<NewsSequence.Step> running = event.getSequence().getSteps();
            r = assertTrue("details show the running sequence '" + event.getSequenceName() + "' with all its steps",
                    shown != null && shown.steps().size() == running.size());
            if (!r.passed()) return r;

            long sumMin = 0, sumMax = 0;
            for (int i = 0; i < running.size(); i++) {
                EventDetails.StepInfo row = shown.steps().get(i);
                NewsSequence.Step step = running.get(i);
                r = assertTrue("step '" + step.name() + "' runs " + step.durationMs() + " ms, within its Duration column "
                                + row.durationMinMs() + ".." + row.durationMaxMs() + " ms",
                        step.durationMs() >= row.durationMinMs() && step.durationMs() <= row.durationMaxMs());
                if (!r.passed()) return r;
                r = assertTrue("step '" + step.name() + "' runs to " + step.targetValue() + ", the details show "
                        + row.targetFactor(), Math.abs(step.targetValue() - row.targetFactor()) < 1e-9);
                if (!r.passed()) return r;
                sumMin += row.durationMinMs();
                sumMax += row.durationMaxMs();
            }

            // What the details screen's live part reads: the plugin's runtime stream, decoded like the client does.
            RuntimeStreamData stream = RuntimeStreamData.CODEC.decode(Unpooled.wrappedBuffer(plugin.encodeRuntimeData()));
            RuntimeStreamData.ActiveEventInfo live = stream.events().stream()
                    .filter(e -> e.eventId().equals(IMPACT_FIRST_EVENT)).findFirst().orElse(null);
            r = assertNotNull("the runtime stream carries the running event", live);
            if (!r.passed()) return r;
            r = assertEquals("'Event remaining' is the running event's remaining time", event.remainingMillis(), live.remainingMs());
            if (!r.passed()) return r;
            long total = live.remainingMs() + event.activeMillis();
            r = assertTrue("'Event remaining' (" + NewsUiFormatting.formatRemainingTime(live.remainingMs())
                            + ") fits the Duration column: total " + total + " ms within " + sumMin + ".." + sumMax,
                    total >= sumMin && total <= sumMax);
            if (!r.passed()) return r;
            if (live.stepRemainingMs() >= 0) {
                long stepMax = shown.steps().get(live.stepIndex()).durationMaxMs();
                r = assertTrue("step countdown " + NewsUiFormatting.formatRemainingTime(live.stepRemainingMs())
                                + " is within its Duration column (max " + NewsUiFormatting.formatRemainingTime(stepMax) + ")",
                        live.stepRemainingMs() <= stepMax);
                if (!r.passed()) return r;
            }
            return pass("running " + IMPACT_FIRST_EVENT + " matches its details: " + running.size()
                    + " steps, event remaining " + NewsUiFormatting.formatRemainingTime(live.remainingMs()));
        } finally {
            if (plugin != null) {
                if (plugin.findActiveEvent(IMPACT_FIRST_EVENT) != null)
                    NewsAdminRequest.performStopEvent(plugin, IMPACT_FIRST_EVENT); // before its headline: nothing published
                if (cooldownBefore <= 0) plugin.resetCooldown(IMPACT_FIRST_EVENT);
                if (market != null) plugin.unsubscribeFromMarket(market);
            }
            restore();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Finds the live NewsPlugin and an online player and gives the player the StockMarket admin flag
     * (undone by {@link #restore()}).
     *
     * @return null when ready, otherwise why the test cannot run here (reported as skip)
     */
    private @Nullable String prepare() {
        plugin = null;
        admin = null;
        MinecraftServer server = getServer();
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (server == null || backend == null || backend.MARKET_MANAGER == null || backend.PLUGIN_MANAGER == null)
            return "needs a running StockMarket server";
        if (!backend.MARKET_MANAGER.isMaster()) return "master only (the news plugin runs on the master)";
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return "needs an online player (the news admin commands take a player)";
        for (ServerPlugin<?, ?> candidate : ((ServerPluginManager) backend.PLUGIN_MANAGER.getSync()).getPlugins().values()) {
            if (candidate instanceof NewsPlugin news) plugin = news;
        }
        if (plugin == null) return "this world has no NewsPlugin";
        ISyncServerMarketManager markets = backend.MARKET_MANAGER.getSync();
        ServerPlayer player = players.get(0);
        wasAdmin = markets.isStockmarketAdmin(player.getUUID());
        if (!markets.setStockmarketAdminMode(player.getUUID(), true))
            return "could not give the online player the StockMarket admin flag";
        admin = player;
        fixture.setBackend(backend);
        return null;
    }

    /** Gives the player back their admin flag and drops the scratch market/ItemIDs. */
    private void restore() {
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (admin != null && backend != null && backend.MARKET_MANAGER.getSync() != null)
            backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(admin.getUUID(), wasAdmin);
        admin = null;
        fixture.cleanup();
    }

    /**
     * Runs a command as the admin player and returns its chat replies, one entry per message.
     * <p>
     * The news admin commands reply after two hops back to the server thread (admin check, then the request). Tests
     * run inside a server task, where those hops are queued, so the replies come only while the test pumps the
     * server. TestKit's {@code CommandRunner} returns right after the command and keeps only synchronous replies,
     * so this collects them itself and pumps until they arrive.
     */
    private List<String> runAdminCommand(String command) {
        List<String> replies = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { replies.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        getServer().getCommands().performPrefixedCommand(admin.createCommandSourceStack().withSource(capture), command);
        pumpUntil(() -> !replies.isEmpty(), 10_000);
        return replies;
    }

    /** Fails when one of the shipped events is not loaded in the live library. */
    private TestResult requireDefinitions(String... ids) {
        for (String id : ids) {
            if (plugin.getLibrary().getDefinition(id) == null)
                return fail("shipped news event '" + id + "' is not loaded");
        }
        return pass("loaded");
    }

    private static boolean containsLine(List<String> lines, String text) {
        return lines.stream().anyMatch(l -> l.contains(text));
    }

    private static @Nullable EventDetails findDetails(List<EventDetails> details, String id) {
        return details.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }
}
