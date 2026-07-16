package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.news.ActiveNewsEvent;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsImpactEnvelope;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsSequence;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.EligibleEvent;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.Settings;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Tests for the {@link NewsPlugin} (T-071): eligibility filtering, weighted pick with
 * cooldowns and caps, announce-delay ordering (both signs), factor computation
 * (sensitivity, noise, clamp), the {@code reversal:none} permanent bake-in (exactly once,
 * incl. across save/load), unsubscribe handling and the full plugin-state round-trip.
 * <p>
 * T-095 (sequence runtime): weighted sequence pick, activation-time duration rolls
 * frozen across save/load (never re-rolled), legacy-NBT active events resuming through
 * the envelope normalization, per-step market switching, permanent-step bake, step-based
 * skip boundaries (incl. last-step retirement and permanent fast-forward) and mid-step
 * hard stops. The pre-T-095 tests double as the legacy regression net — legacy
 * {@code impact} events must behave byte-identically on the sequence code path.
 * <p>
 * All time-dependent logic is driven deterministically through
 * {@link NewsPlugin#advanceTime(long, List)} — no sleeping. Item-registry-dependent
 * matcher resolution and the custom-settings machinery are bypassed via the plugin's
 * protected test seams ({@link TestNewsPlugin}).
 */
public class NewsPluginTestSuite extends TestSuite {

    private static final double EPSILON = 1e-9;

    /** Test markets (raw shorts are enough — resolution is stubbed out). */
    private static final ItemID M1 = new ItemID((short) 1);
    private static final ItemID M2 = new ItemID((short) 2);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_PLUGIN;
    }

    @Override
    public void registerTests() {
        // Eligibility & scheduling
        addTest("eligibility_requires_resolved_markets", this::test_eligibility_requiresResolvedMarkets);
        addTest("eligibility_excludes_admin_only", this::test_eligibility_excludesAdminOnly);
        addTest("eligibility_excludes_news_disabled_markets", this::test_eligibility_excludesNewsDisabledMarkets);
        addTest("eligibility_respects_cooldown_and_active", this::test_eligibility_respectsCooldownAndActive);
        addTest("eligibility_respects_per_market_cap", this::test_eligibility_respectsPerMarketCap);
        addTest("scheduler_respects_global_cap", this::test_scheduler_respectsGlobalCap);
        addTest("weighted_pick_follows_weights", this::test_weightedPick_followsWeights);

        // Announce delay
        addTest("announce_delay_positive_publishes_before_impact", this::test_announceDelayPositive_publishesBeforeImpact);
        addTest("announce_delay_negative_impacts_before_publish", this::test_announceDelayNegative_impactsBeforePublish);

        // Factor math & finalize
        addTest("factor_term_and_clamp_math", this::test_factorTermAndClampMath);
        addTest("finalize_applies_sensitivity_scaled_delta", this::test_finalize_appliesSensitivityScaledDelta);
        addTest("finalize_clamps_extreme_factors", this::test_finalize_clampsExtremeFactors);
        addTest("finalize_skips_news_disabled_markets", this::test_finalize_skipsNewsDisabledMarkets);
        addTest("noise_jitter_stays_in_bounds", this::test_noiseJitter_staysInBounds);

        // reversal:none bake-in
        addTest("permanent_bake_happens_exactly_once", this::test_permanentBake_happensExactlyOnce);
        addTest("permanent_bake_no_double_bake_after_save_load", this::test_permanentBake_noDoubleBakeAfterSaveLoad);

        // Lifecycle & persistence
        addTest("unsubscribe_drops_market_and_retires_event", this::test_unsubscribe_dropsMarketAndRetiresEvent);
        addTest("save_load_round_trip_full_state", this::test_saveLoad_roundTripFullState);

        // Admin operations (T-076: manual trigger + stop seams)
        addTest("admin_trigger_bypasses_cooldown_and_admin_only", this::test_adminTrigger_bypassesCooldownAndAdminOnly);
        addTest("admin_trigger_market_restriction", this::test_adminTrigger_marketRestriction);
        addTest("stop_hard_stops_event_in_any_phase", this::test_stop_hardStopsEventInAnyPhase);
        addTest("stop_permanent_event_cancels_without_bake", this::test_stop_permanentEventCancelsWithoutBake);
        addTest("stop_pending_impact_cancels_silently", this::test_stop_pendingImpactCancelsSilently);
        addTest("stop_unpublished_event_suppresses_publication", this::test_stop_unpublishedEventSuppressesPublication);

        // Sequence runtime (T-095)
        addTest("sequence_pick_honors_weights", this::test_sequencePick_honorsWeights);
        addTest("sequence_rolled_durations_persist_across_save_load", this::test_sequenceRolledDurations_persistAcrossSaveLoad);
        addTest("sequence_legacy_nbt_event_loads_and_resumes", this::test_sequenceLegacyNbt_eventLoadsAndResumes);
        addTest("sequence_per_step_market_switching", this::test_sequencePerStepMarketSwitching);
        addTest("sequence_permanent_step_bakes_exactly_once", this::test_sequencePermanentStep_bakesExactlyOnce);
        addTest("sequence_skip_walks_step_boundaries", this::test_sequenceSkip_walksStepBoundaries);
        addTest("sequence_skip_permanent_last_step_bakes", this::test_sequenceSkip_permanentLastStepBakes);
        addTest("sequence_stop_mid_step_cancels_and_rearms_cooldown", this::test_sequenceStop_midStepCancelsAndRearmsCooldown);
    }

    // ========================================================================
    // Test doubles
    // ========================================================================

    /**
     * Minimal in-memory {@link IPluginMarket}: target price accumulates deltas,
     * default price is directly settable (bake-in observation point).
     */
    static final class TestMarket implements IPluginMarket {
        final ItemID id;
        double targetPrice;
        double defaultPrice;

        TestMarket(ItemID id, double price) {
            this.id = id;
            this.targetPrice = price;
            this.defaultPrice = price;
        }

        @Override public @NotNull ItemID getMarketID() { return id; }
        @Override public void setDefaultRealPrice(double v) { defaultPrice = v; }
        @Override public double getDefaultRealPrice() { return defaultPrice; }
        @Override public double getPrice() { return targetPrice; }
        @Override public double convertBackendPriceToRealPrice(long backendPrice) { return backendPrice / 100.0; }
        @Override public long convertRealPriceToBackendPrice(double realPrice) { return (long) (realPrice * 100.0); }
        @Override public double getPreviousTargetPrice() { return targetPrice; }
        @Override public double getTargetPrice() { return targetPrice; }
        @Override public void setTargetPrice(double v) { targetPrice = v; }
        @Override public void addToTargetPrice(double delta) { targetPrice += delta; }
        @Override public long placeOrder(double amount, double price) { return 0; }
        @Override public void placeOrder(double amount) { }
        @Override public float getNaturalAbundance() { return 10f; }
        @Override public double getNetPlayerItemFlow() { return 0; }
        @Override public boolean isMarketOpen() { return true; }
        @Override public float getCurrentCandleTradedVolume() { return 0; }
    }

    /**
     * NewsPlugin with the environment-dependent seams stubbed: matcher resolution comes
     * from an injected map, per-market settings from an injected map, time/game-day are
     * fixed, and published records are captured in a list.
     */
    static final class TestNewsPlugin extends NewsPlugin {
        final Map<String, Map<ItemID, Float>> resolutions = new HashMap<>();
        /**
         * Per-step matcher resolution stub (T-095), keyed {@code "<eventId>#<stepName>"}.
         * Only consulted for steps that declare their own {@code markets[]} list; steps
         * without one inherit the event-level resolution via the production path.
         */
        final Map<String, Map<ItemID, Float>> stepResolutions = new HashMap<>();
        final Map<ItemID, Settings> marketSettings = new HashMap<>();
        final List<NewsRecord> published = new ArrayList<>();

        TestNewsPlugin() {
            test_setLibraryLoaded(true); // never touch the real config folder
            test_setRandom(new Random(1234));
            setPublisher(published::add);
        }

        @Override
        protected Map<ItemID, Float> resolveEventMarkets(NewsEventDefinition definition,
                                                         Collection<ItemID> candidates) {
            Map<ItemID, Float> base = resolutions.get(definition.getId());
            Map<ItemID, Float> resolved = new LinkedHashMap<>();
            if (base != null) {
                for (Map.Entry<ItemID, Float> entry : base.entrySet()) {
                    if (candidates.contains(entry.getKey())) {
                        resolved.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            return resolved;
        }

        @Override
        protected Map<ItemID, Float> resolveStepMarkets(NewsEventDefinition definition,
                                                        NewsEventDefinition.StepDefinition step,
                                                        Map<ItemID, Float> eventLevelResolved,
                                                        Collection<ItemID> stepCandidates) {
            if (step.getMarkets() == null) {
                // Inherit path — exercised through the production implementation.
                return super.resolveStepMarkets(definition, step, eventLevelResolved, stepCandidates);
            }
            Map<ItemID, Float> base = stepResolutions.get(definition.getId() + "#" + step.getName());
            return base != null ? new LinkedHashMap<>(base) : new LinkedHashMap<>();
        }

        @Override
        protected @NotNull Settings settingsFor(ItemID marketID) {
            return marketSettings.getOrDefault(marketID, Settings.createDefault());
        }

        @Override protected long currentEpochMs() { return 1_000_000L; }
        @Override protected long currentGameDay() { return 42L; }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    // Helpers below are package-visible so NewsAdminTestSuite (T-081) reuses them
    // together with the TestNewsPlugin/TestMarket doubles instead of duplicating them.

    /** Parses one event JSON string into a definition (must be valid). */
    static NewsEventDefinition parseEvent(String json) {
        JsonObject obj = JsonUtilities.fromString(json).getAsJsonObject();
        return NewsEventDefinition.parse(obj, "test.json", new ValidationReport());
    }

    /**
     * One event object: trend envelope with ramp 10s, hold 20s, ramp reversal 10s,
     * peak +0.5 (unless overridden by {@code extra}, which is spliced in last and wins
     * for whole blocks like "impact").
     */
    static String eventJson(String id, String extra) {
        return "{\"id\":\"" + id + "\","
                + "\"headline\":\"Headline " + id + "\",\"text\":\"Text " + id + "\","
                + "\"impact\":{\"type\":\"trend\",\"peakFactor\":0.5,\"rampUpSeconds\":10,"
                + "\"durationSeconds\":20,\"reversal\":\"ramp\",\"reversalSeconds\":10},"
                + "\"markets\":[{\"item\":\"minecraft:diamond\"}]"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    /** A news file with the given scheduler block (may be empty) and event objects. */
    static String fileJson(String scheduler, String... events) {
        return "{" + (scheduler.isEmpty() ? "" : "\"scheduler\":{" + scheduler + "},")
                + "\"events\":[" + String.join(",", events) + "]}";
    }

    /**
     * One sequence-authored event object (T-095): standard headline/text/markets plus
     * the given {@code sequences[]} entries (comma-joined JSON objects). {@code extra}
     * is spliced in last (e.g. {@code "cooldownSeconds":60}).
     */
    static String sequenceEventJson(String id, String extra, String... sequences) {
        return "{\"id\":\"" + id + "\","
                + "\"headline\":\"Headline " + id + "\",\"text\":\"Text " + id + "\","
                + "\"sequences\":[" + String.join(",", sequences) + "],"
                + "\"markets\":[{\"item\":\"minecraft:diamond\"}]"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    /** Creates a plugin whose library is loaded from a temp file with the given content. */
    static TestNewsPlugin pluginWithFile(Path dir, String fileContent) throws IOException {
        Files.writeString(dir.resolve("events.json"), fileContent);
        TestNewsPlugin plugin = new TestNewsPlugin();
        plugin.getLibrary().reload(dir);
        plugin.test_setLibraryLoaded(true);
        return plugin;
    }

    static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_plugin_test");
    }

    /** Best-effort recursive cleanup of a temp directory. */
    static void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    static List<MarketInterface> markets(TestMarket... markets) {
        List<MarketInterface> list = new ArrayList<>();
        for (TestMarket market : markets) {
            list.add(new MarketInterface(market, null));
        }
        return list;
    }

    static Map<ItemID, Float> weights(ItemID id, float weight) {
        Map<ItemID, Float> map = new LinkedHashMap<>();
        map.put(id, weight);
        return map;
    }

    // ========================================================================
    // Eligibility & scheduling
    // ========================================================================

    /** Events whose matchers resolve to no subscribed market must not be eligible. */
    private TestResult test_eligibility_requiresResolvedMarkets() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("resolves", ""), eventJson("resolves_not", "")));
            plugin.resolutions.put("resolves", weights(M1, 1.0f));
            // "resolves_not" gets no resolution entry -> empty subset

            List<EligibleEvent> eligible =
                    plugin.computeEligibleEvents(markets(new TestMarket(M1, 100)));
            TestResult r = assertEquals("exactly one eligible event", 1, eligible.size());
            if (!r.passed()) return r;
            return assertEquals("the resolvable event is eligible",
                    "resolves", eligible.get(0).definition().getId());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** adminOnly events must never be eligible for random scheduling. */
    private TestResult test_eligibility_excludesAdminOnly() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("admin_event", "\"adminOnly\":true")));
            plugin.resolutions.put("admin_event", weights(M1, 1.0f));

            List<EligibleEvent> eligible =
                    plugin.computeEligibleEvents(markets(new TestMarket(M1, 100)));
            return assertEquals("adminOnly event must not be eligible", 0, eligible.size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** newsEnabled=false must remove a market from eligibility entirely. */
    private TestResult test_eligibility_excludesNewsDisabledMarkets() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("only_m1", "")));
            plugin.resolutions.put("only_m1", weights(M1, 1.0f));
            plugin.marketSettings.put(M1, new Settings(false, 1.0f));

            List<EligibleEvent> eligible =
                    plugin.computeEligibleEvents(markets(new TestMarket(M1, 100)));
            return assertEquals("event whose only market is news-disabled must not be eligible",
                    0, eligible.size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * An active event must not be eligible again, and after it expires the cooldown
     * must keep it ineligible until the cooldown has ticked down.
     */
    private TestResult test_eligibility_respectsCooldownAndActive() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Envelope total: 10s ramp + 20s hold + 10s reversal = 40s. Cooldown 60s.
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("cd_event", "\"cooldownSeconds\":60")));
            plugin.resolutions.put("cd_event", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("cd_event");
            ActiveNewsEvent event = plugin.activate(def, weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;

            r = assertEquals("active event must not be eligible",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            // Past the envelope: event retired (it published at activation, delay 0)...
            plugin.advanceTime(41_000, markets);
            r = assertEquals("event must be retired after the envelope", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            // ...but the cooldown (60s - 41s = 19s left) must still block it.
            r = assertEquals("event must still be on cooldown",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            plugin.advanceTime(20_000, markets); // cooldown fully elapsed
            return assertEquals("event must be eligible again after the cooldown",
                    1, plugin.computeEligibleEvents(markets).size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * maxActiveEventsPerMarket must drop capped markets from the candidate set: an event
     * resolving only to a capped market is ineligible; one resolving to more markets is
     * eligible with the capped market excluded.
     */
    private TestResult test_eligibility_respectsPerMarketCap() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("\"maxActiveEventsPerMarket\":1",
                            eventJson("active_on_m1", ""),
                            eventJson("wants_m1", ""),
                            eventJson("wants_both", "")));
            plugin.resolutions.put("wants_m1", weights(M1, 1.0f));
            Map<ItemID, Float> both = weights(M1, 1.0f);
            both.put(M2, 0.5f);
            plugin.resolutions.put("wants_both", both);

            NewsEventDefinition activeDef = plugin.getLibrary().getDefinition("active_on_m1");
            plugin.activate(activeDef, weights(M1, 1.0f)); // M1 is now at its cap

            List<EligibleEvent> eligible = plugin.computeEligibleEvents(
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100)));
            TestResult r = assertEquals("only the multi-market event stays eligible",
                    1, eligible.size());
            if (!r.passed()) return r;
            r = assertEquals("eligible event id", "wants_both", eligible.get(0).definition().getId());
            if (!r.passed()) return r;
            r = assertEquals("capped market M1 must be excluded from the impact subset",
                    1, eligible.get(0).resolvedMarkets().size());
            if (!r.passed()) return r;
            return assertTrue("remaining impact market must be M2",
                    eligible.get(0).resolvedMarkets().containsKey(M2));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** maxActiveEventsGlobal must gate the scheduler attempt entirely. */
    private TestResult test_scheduler_respectsGlobalCap() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("\"maxActiveEventsGlobal\":1,"
                                    + "\"minSecondsBetweenEvents\":1,\"maxSecondsBetweenEvents\":1",
                            eventJson("first", ""), eventJson("second", "")));
            plugin.resolutions.put("first", weights(M1, 1.0f));
            plugin.resolutions.put("second", weights(M2, 1.0f));
            List<MarketInterface> markets =
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100));

            NewsEventDefinition firstDef = plugin.getLibrary().getDefinition("first");
            plugin.activate(firstDef, weights(M1, 1.0f)); // global cap (1) reached

            plugin.test_setSchedulerRemainingMs(1);
            plugin.advanceTime(100, markets); // scheduler fires, but the cap must block it
            TestResult r = assertEquals("no second event may activate at the global cap",
                    1, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            return assertTrue("scheduler must have re-armed",
                    plugin.test_getSchedulerRemainingMs() > 0);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** The pure weighted pick must be proportional to the event weights. */
    private TestResult test_weightedPick_followsWeights() {
        NewsEventDefinition light = parseEvent(eventJson("light", "\"weight\":1"));
        NewsEventDefinition heavy = parseEvent(eventJson("heavy", "\"weight\":3"));
        List<EligibleEvent> eligible = List.of(
                new EligibleEvent(light, weights(M1, 1.0f)),
                new EligibleEvent(heavy, weights(M1, 1.0f)));

        // Total weight 4: rolls in [0, 0.25) pick "light", rolls in [0.25, 1) pick "heavy".
        EligibleEvent picked = NewsPlugin.pickWeighted(eligible, 0.1);
        TestResult r = assertEquals("roll 0.1 picks the light event", "light",
                picked != null ? picked.definition().getId() : "null");
        if (!r.passed()) return r;
        picked = NewsPlugin.pickWeighted(eligible, 0.9);
        r = assertEquals("roll 0.9 picks the heavy event", "heavy",
                picked != null ? picked.definition().getId() : "null");
        if (!r.passed()) return r;
        r = assertNull("empty list picks nothing", NewsPlugin.pickWeighted(List.of(), 0.5));
        if (!r.passed()) return r;
        return assertNotNull("roll at the numeric edge (1.0) must still pick",
                NewsPlugin.pickWeighted(eligible, 1.0));
    }

    // ========================================================================
    // Announce delay
    // ========================================================================

    /** Positive delay: headline publishes at activation, impact starts delay ms later. */
    private TestResult test_announceDelayPositive_publishesBeforeImpact() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("early_news", "\"announceDelayMs\":{\"min\":5000,\"max\":5000}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("early_news");
            ActiveNewsEvent event = plugin.activate(def, weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;

            r = assertEquals("record must publish immediately (positive delay)",
                    1, plugin.published.size());
            if (!r.passed()) return r;
            r = assertTrue("impact must not have started yet (activeMillis "
                    + event.activeMillis() + ")", event.activeMillis() < 0);
            if (!r.passed()) return r;
            r = assertTrue("no price influence before the impact start",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < EPSILON);
            if (!r.passed()) return r;

            // 5s delay + 10s ramp: at 15s the envelope is exactly at peak.
            plugin.advanceTime(15_000, markets);
            double factor = plugin.combinedFactorFor(M1, 1.0f, false);
            return assertTrue("impact must be at peak after delay+ramp (factor " + factor + ")",
                    Math.abs(factor - 1.5) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Negative delay: impact starts at activation, headline publishes |delay| ms later. */
    private TestResult test_announceDelayNegative_impactsBeforePublish() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Instant impact (shock, ramp 0) so the influence is visible immediately.
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("insider_news",
                            "\"announceDelayMs\":{\"min\":-5000,\"max\":-5000},"
                                    + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                                    + "\"rampUpSeconds\":0,\"durationSeconds\":60,"
                                    + "\"reversal\":\"ramp\",\"reversalSeconds\":10}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("insider_news");
            ActiveNewsEvent event = plugin.activate(def, weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;

            r = assertEquals("record must NOT publish yet (negative delay)",
                    0, plugin.published.size());
            if (!r.passed()) return r;
            double factor = plugin.combinedFactorFor(M1, 1.0f, false);
            r = assertTrue("impact must already apply before publication (factor " + factor + ")",
                    Math.abs(factor - 1.5) < 1e-6);
            if (!r.passed()) return r;

            plugin.advanceTime(4_000, markets);
            r = assertEquals("still unpublished 1s before the publish moment",
                    0, plugin.published.size());
            if (!r.passed()) return r;

            plugin.advanceTime(1_000, markets);
            r = assertEquals("record must publish once |delay| has elapsed",
                    1, plugin.published.size());
            if (!r.passed()) return r;
            return assertEquals("published uid must match the active event",
                    event.getNewsUid(), plugin.published.get(0).getNewsUid());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Factor math & finalize
    // ========================================================================

    /** Pure math: factor term composition and the safety clamp. */
    private TestResult test_factorTermAndClampMath() {
        TestResult r = assertTrue("term(1, 0.5, 1, 1, 0) = 1.5",
                Math.abs(NewsPlugin.eventFactorTerm(1, 0.5, 1, 1, 0) - 1.5) < EPSILON);
        if (!r.passed()) return r;
        r = assertTrue("sensitivity doubles the influence: term(1, 0.5, 1, 2, 0) = 2.0",
                Math.abs(NewsPlugin.eventFactorTerm(1, 0.5, 1, 2, 0) - 2.0) < EPSILON);
        if (!r.passed()) return r;
        r = assertTrue("negative weight inverts: term(1, 0.5, -1, 1, 0) = 0.5",
                Math.abs(NewsPlugin.eventFactorTerm(1, 0.5, -1, 1, 0) - 0.5) < EPSILON);
        if (!r.passed()) return r;
        r = assertTrue("half envelope halves the influence: term(0.5, 0.5, 1, 1, 0) = 1.25",
                Math.abs(NewsPlugin.eventFactorTerm(0.5, 0.5, 1, 1, 0) - 1.25) < EPSILON);
        if (!r.passed()) return r;

        r = assertTrue("clamp caps at MAX_COMBINED_FACTOR",
                NewsPlugin.clampCombinedFactor(50.0) == NewsPlugin.MAX_COMBINED_FACTOR);
        if (!r.passed()) return r;
        r = assertTrue("clamp floors at MIN_COMBINED_FACTOR",
                NewsPlugin.clampCombinedFactor(-3.0) == NewsPlugin.MIN_COMBINED_FACTOR);
        if (!r.passed()) return r;
        return assertTrue("NaN factor sanitizes to 1 (no influence)",
                NewsPlugin.clampCombinedFactor(Double.NaN) == 1.0);
    }

    /** finalize() must add targetPrice * (F - 1), scaled by the market sensitivity. */
    private TestResult test_finalize_appliesSensitivityScaledDelta() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("boost", "")));
            plugin.marketSettings.put(M1, new Settings(true, 2.0f)); // double sensitivity
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("boost");
            plugin.activate(def, weights(M1, 1.0f));
            plugin.advanceTime(15_000, markets); // mid-hold: envelope = 1

            // F = 1 + 1 * 0.5(peak) * 1(weight) * 2(sensitivity) = 2 -> target 100 -> 200
            plugin.finalize(markets);
            return assertTrue("target must double under sensitivity 2 (got " + m1.targetPrice + ")",
                    Math.abs(m1.targetPrice - 200.0) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Extreme stacked influence must clamp to the safety band in finalize(). */
    private TestResult test_finalize_clampsExtremeFactors() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("huge", "\"impact\":{\"type\":\"shock\",\"peakFactor\":80.0,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":60,"
                            + "\"reversal\":\"ramp\",\"reversalSeconds\":10}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("huge");
            plugin.activate(def, weights(M1, 1.0f));
            plugin.advanceTime(1_000, markets);

            plugin.finalize(markets); // raw F = 81 -> clamped to 10 -> target 1000
            TestResult r = assertTrue("upward clamp at 10x (got " + m1.targetPrice + ")",
                    Math.abs(m1.targetPrice - 100.0 * NewsPlugin.MAX_COMBINED_FACTOR) < 1e-6);
            if (!r.passed()) return r;

            // Downward: peak -0.99 with sensitivity 2 -> raw term 1 - 1.98 < 0 -> clamp 0.1
            TestNewsPlugin plugin2 = pluginWithFile(dir, fileJson("",
                    eventJson("crash", "\"impact\":{\"type\":\"crash\",\"peakFactor\":-0.99,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":60,"
                            + "\"reversal\":\"ramp\",\"reversalSeconds\":10}")));
            plugin2.marketSettings.put(M1, new Settings(true, 2.0f));
            TestMarket m2 = new TestMarket(M1, 100);
            List<MarketInterface> markets2 = markets(m2);
            plugin2.activate(plugin2.getLibrary().getDefinition("crash"), weights(M1, 1.0f));
            plugin2.advanceTime(1_000, markets2);
            plugin2.finalize(markets2);
            return assertTrue("downward clamp at 0.1x (got " + m2.targetPrice + ")",
                    Math.abs(m2.targetPrice - 100.0 * NewsPlugin.MIN_COMBINED_FACTOR) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** newsEnabled=false must also exclude a market from the impact of active events. */
    private TestResult test_finalize_skipsNewsDisabledMarkets() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("boost", "")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            plugin.activate(plugin.getLibrary().getDefinition("boost"), weights(M1, 1.0f));
            plugin.advanceTime(15_000, markets);

            plugin.marketSettings.put(M1, new Settings(false, 1.0f)); // admin disables mid-event
            plugin.finalize(markets);
            return assertTrue("news-disabled market must not move (got " + m1.targetPrice + ")",
                    Math.abs(m1.targetPrice - 100.0) < EPSILON);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Noise jitter must stay within +-noise around the deterministic factor term. */
    private TestResult test_noiseJitter_staysInBounds() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("noisy", "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":600,"
                            + "\"reversal\":\"ramp\",\"reversalSeconds\":10,\"noise\":0.1}")));
            TestMarket m1 = new TestMarket(M1, 100);

            plugin.activate(plugin.getLibrary().getDefinition("noisy"), weights(M1, 1.0f));
            plugin.advanceTime(1_000, markets(m1));

            // Deterministic term = 1.5; jitter scales the 0.5 influence by (1 +- 0.1).
            double min = 1.0 + 0.5 * 0.9;
            double max = 1.0 + 0.5 * 1.1;
            for (int i = 0; i < 200; i++) {
                double factor = plugin.combinedFactorFor(M1, 1.0f, true);
                if (factor < min - EPSILON || factor > max + EPSILON) {
                    return fail("noisy factor " + factor + " left [" + min + ", " + max + "]");
                }
            }
            double noiseless = plugin.combinedFactorFor(M1, 1.0f, false);
            return assertTrue("noise-free factor must be exactly 1.5 (got " + noiseless + ")",
                    Math.abs(noiseless - 1.5) < EPSILON);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // reversal:none bake-in
    // ========================================================================

    /**
     * When the hold phase of a reversal:none event ends, the shift must be baked into
     * the default price exactly once and the event must retire; further time must not
     * change the default price again.
     */
    private TestResult test_permanentBake_happensExactlyOnce() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("perma", "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":10,\"reversal\":\"none\"}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            plugin.activate(plugin.getLibrary().getDefinition("perma"), weights(M1, 1.0f));
            plugin.advanceTime(5_000, markets); // mid-hold
            TestResult r = assertTrue("default price untouched during the hold",
                    Math.abs(m1.defaultPrice - 100.0) < EPSILON);
            if (!r.passed()) return r;

            plugin.advanceTime(5_001, markets); // crosses into PERMANENT
            r = assertTrue("default price must be baked to 150 (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
            if (!r.passed()) return r;
            r = assertEquals("event must be retired after the bake",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("no residual factor influence after the bake",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < EPSILON);
            if (!r.passed()) return r;

            plugin.advanceTime(60_000, markets);
            return assertTrue("default price must not be baked twice (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A save/load cycle around the permanence threshold must not double-bake:
     * (a) restart before the bake -> bake happens exactly once afterwards;
     * (b) restart after the bake -> nothing is applied again.
     */
    private TestResult test_permanentBake_noDoubleBakeAfterSaveLoad() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("",
                    eventJson("perma", "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":10,\"reversal\":\"none\"}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);

            plugin.activate(plugin.getLibrary().getDefinition("perma"), weights(M1, 1.0f));
            // Cross the permanence threshold WITHOUT the market present: the bake must
            // stay pending (no interface to write to)...
            plugin.advanceTime(10_001, markets());
            TestResult r = assertTrue("bake pending without a market interface",
                    Math.abs(m1.defaultPrice - 100.0) < EPSILON);
            if (!r.passed()) return r;

            // (a) ...restart inside the pending window...
            CompoundTag saved = new CompoundTag();
            plugin.save(saved);
            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);
            r = assertEquals("active event must survive the restart",
                    1, restored.getActiveEvents().size());
            if (!r.passed()) return r;

            // ...then the bake happens exactly once when the market ticks again.
            restored.advanceTime(1, markets(m1));
            r = assertTrue("bake must apply once after the restart (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
            if (!r.passed()) return r;

            // (b) restart after the bake: baked bookkeeping (event retired here) must
            // prevent any second application.
            CompoundTag savedAfter = new CompoundTag();
            restored.save(savedAfter);
            TestNewsPlugin restored2 = pluginWithFile(dir, file);
            restored2.load(savedAfter);
            restored2.advanceTime(60_000, markets(m1));
            return assertTrue("no double-bake after the second restart (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Lifecycle & persistence
    // ========================================================================

    /** Unsubscribing drops the market from active events; empty events retire. */
    private TestResult test_unsubscribe_dropsMarketAndRetiresEvent() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("solo", ""), eventJson("duo", "")));

            plugin.activate(plugin.getLibrary().getDefinition("solo"), weights(M1, 1.0f));
            Map<ItemID, Float> both = weights(M1, 1.0f);
            both.put(M2, 0.5f);
            plugin.activate(plugin.getLibrary().getDefinition("duo"), both);

            plugin.onMarketUnsubscribed(M1);
            TestResult r = assertEquals("the single-market event must retire",
                    1, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            ActiveNewsEvent remaining = plugin.getActiveEvents().get(0);
            r = assertEquals("the two-market event survives", "duo", remaining.getDefinitionId());
            if (!r.passed()) return r;
            r = assertFalse("M1 must be dropped from the surviving event",
                    remaining.getMarketWeights().containsKey(M1));
            if (!r.passed()) return r;
            return assertTrue("M2 must still be influenced",
                    remaining.getMarketWeights().containsKey(M2));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Admin operations (T-076)
    // ========================================================================

    /**
     * A manual admin trigger must bypass the cooldown, the adminOnly flag and the
     * random-scheduler eligibility entirely: an adminOnly event that is still on
     * cooldown must not be schedulable, but must be triggerable.
     */
    private TestResult test_adminTrigger_bypassesCooldownAndAdminOnly() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Envelope total 40s, cooldown 60s, adminOnly (never fires randomly).
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("admin_event", "\"adminOnly\":true,\"cooldownSeconds\":60")));
            plugin.resolutions.put("admin_event", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);
            NewsEventDefinition def = plugin.getLibrary().getDefinition("admin_event");

            // First manual trigger through the admin seam.
            Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(def, markets, null);
            TestResult r = assertEquals("admin resolution ignores adminOnly", 1, resolved.size());
            if (!r.passed()) return r;
            r = assertNotNull("first manual trigger must succeed", plugin.activate(def, resolved));
            if (!r.passed()) return r;

            // Let the event finish; the 60s cooldown still has ~19s left.
            plugin.advanceTime(41_000, markets);
            r = assertEquals("event retired after the envelope", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("event must still be on cooldown",
                    plugin.getCooldownRemainingMs("admin_event") > 0);
            if (!r.passed()) return r;
            r = assertEquals("random scheduler must never see the adminOnly event",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            // Second manual trigger: cooldown + adminOnly are bypassed.
            resolved = plugin.resolveAdminTriggerMarkets(def, markets, null);
            r = assertEquals("admin resolution ignores the cooldown", 1, resolved.size());
            if (!r.passed()) return r;
            return assertNotNull("manual trigger must bypass the cooldown",
                    plugin.activate(def, resolved));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The optional TRIGGER market restriction must reduce the impact to exactly that
     * market (an unmatched restriction yields an empty subset), and news-disabled
     * markets must stay excluded even from manual triggers.
     */
    private TestResult test_adminTrigger_marketRestriction() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("dual", "")));
            Map<ItemID, Float> both = weights(M1, 1.0f);
            both.put(M2, 0.5f);
            plugin.resolutions.put("dual", both);
            List<MarketInterface> markets = markets(new TestMarket(M1, 100), new TestMarket(M2, 100));
            NewsEventDefinition def = plugin.getLibrary().getDefinition("dual");

            // Restriction to M2: only M2, with its own weight factor.
            Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(def, markets, List.of(M2));
            TestResult r = assertEquals("restriction to M2 keeps exactly one market", 1, resolved.size());
            if (!r.passed()) return r;
            r = assertEquals("restricted market keeps its weight factor", 0.5f, resolved.get(M2));
            if (!r.passed()) return r;

            // Restriction to a market the event does not match: empty subset -> trigger error.
            ItemID unmatched = new ItemID((short) 99);
            resolved = plugin.resolveAdminTriggerMarkets(def, markets, List.of(unmatched));
            r = assertEquals("unmatched restriction yields an empty subset", 0, resolved.size());
            if (!r.passed()) return r;

            // newsEnabled=false must exclude a market even from manual triggers.
            plugin.marketSettings.put(M1, new Settings(false, 1.0f));
            resolved = plugin.resolveAdminTriggerMarkets(def, markets, null);
            r = assertEquals("news-disabled market excluded from the manual trigger", 1, resolved.size());
            if (!r.passed()) return r;
            return assertTrue("only the news-enabled market remains", resolved.containsKey(M2));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Hard-stop semantics (T-093): stopping a running event — here mid-ramp — must
     * terminate it <b>immediately</b>: the event leaves the active list, its price
     * influence disappears from the very next finalize pass (no reversal fast-forward,
     * no gradual decay) and the market returns to its pre-event target on its own.
     */
    private TestResult test_stop_hardStopsEventInAnyPhase() {
        Path dir = null;
        try {
            dir = createTempDir();
            // trend: ramp 10s, hold 20s, ramp reversal 10s, peak +0.5
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("stopme", "")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("stopme"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(5_000, markets); // mid-ramp: envelope 0.5 -> factor 1.25

            double before = plugin.combinedFactorFor(M1, 1.0f, false);
            r = assertTrue("event must influence the market before the stop (factor "
                    + before + ")", before > 1.0 + 1e-6);
            if (!r.passed()) return r;

            NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
            r = assertEquals("outcome must be STOPPED", NewsPlugin.StopOutcome.STOPPED, outcome);
            if (!r.passed()) return r;
            r = assertEquals("event must be gone from the active list immediately",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("influence must be removed immediately (factor "
                            + plugin.combinedFactorFor(M1, 1.0f, false) + ")",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < EPSILON);
            if (!r.passed()) return r;

            // The very next finalize pass must not touch the target price anymore.
            plugin.finalize(markets);
            return assertTrue("target price untouched after the stop (got " + m1.targetPrice + ")",
                    Math.abs(m1.targetPrice - 100.0) < EPSILON);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Hard-stop semantics for {@code reversal:none} (T-093): stop = <b>cancel</b>, so
     * the pending permanent shift must be reverted, NOT baked — the default price stays
     * untouched and the event is gone immediately. (Finalizing such an event early is
     * what {@code skipPhase} is for — skip means fast-forward, stop means cancel.)
     */
    private TestResult test_stop_permanentEventCancelsWithoutBake() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("perma", "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":60,\"reversal\":\"none\"}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("perma"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(2_000, markets); // mid-hold, far before the 60s hold end

            NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
            r = assertEquals("outcome must be CANCELLED_PERMANENT",
                    NewsPlugin.StopOutcome.CANCELLED_PERMANENT, outcome);
            if (!r.passed()) return r;
            r = assertEquals("event must be gone from the active list immediately",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;

            plugin.advanceTime(1_000, markets); // nothing may bake afterwards
            r = assertTrue("default price must stay unbaked (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 100.0) < 1e-6);
            if (!r.passed()) return r;
            return assertTrue("no lingering influence (factor "
                            + plugin.combinedFactorFor(M1, 1.0f, false) + ")",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < EPSILON);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Stopping an event whose impact has not started yet (positive announce delay)
     * must cancel it outright: no price influence is ever applied and the event
     * retires without any market shift.
     */
    private TestResult test_stop_pendingImpactCancelsSilently() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("early", "\"announceDelayMs\":{\"min\":5000,\"max\":5000}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("early"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(1_000, markets); // still inside the 5s pending window

            NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
            r = assertEquals("outcome must be CANCELLED_BEFORE_IMPACT",
                    NewsPlugin.StopOutcome.CANCELLED_BEFORE_IMPACT, outcome);
            if (!r.passed()) return r;
            r = assertEquals("cancelled event must be gone immediately (T-093 hard stop)",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;

            plugin.advanceTime(60_000, markets); // way past what the impact would have been
            plugin.finalize(markets);
            return assertTrue("no price influence may ever apply (target " + m1.targetPrice + ")",
                    Math.abs(m1.targetPrice - 100.0) < EPSILON);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Stopping an event that has not published yet (negative announce delay, impact
     * already running) must suppress the publication: the headline of an admin-cancelled
     * event never reaches the players, and the event terminates immediately (T-093
     * hard stop — the influence is removed, not wound down).
     */
    private TestResult test_stop_unpublishedEventSuppressesPublication() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("insider", "\"announceDelayMs\":{\"min\":-5000,\"max\":-5000},"
                            + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":60,"
                            + "\"reversal\":\"ramp\",\"reversalSeconds\":10}")));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("insider"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(1_000, markets); // impact running, publish still 4s away
            r = assertEquals("nothing published before the stop", 0, plugin.published.size());
            if (!r.passed()) return r;

            NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
            r = assertEquals("outcome must be STOPPED (T-093 hard stop)",
                    NewsPlugin.StopOutcome.STOPPED, outcome);
            if (!r.passed()) return r;
            r = assertEquals("stopped event must be gone immediately",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;

            // Cross the original publish moment: the removed event must never publish.
            plugin.advanceTime(60_000, markets);
            return assertEquals("suppressed publication must never happen",
                    0, plugin.published.size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Persistence round-trip
    // ========================================================================

    /**
     * Full state round-trip: uid counter, scheduler countdown, cooldowns and an active
     * event inside the pending-publish window (negative delay) must all survive, and the
     * publish after the restart must use the pre-restart uid.
     */
    private TestResult test_saveLoad_roundTripFullState() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("",
                    eventJson("pending", "\"cooldownSeconds\":120,"
                            + "\"announceDelayMs\":{\"min\":-8000,\"max\":-8000}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("pending"), weights(M1, 0.75f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.test_setSchedulerRemainingMs(123_456);
            plugin.advanceTime(3_000, markets(m1)); // age 3s of the 8s pending window

            CompoundTag saved = new CompoundTag();
            plugin.save(saved);

            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);

            r = assertEquals("uid counter", plugin.test_getNewsUidCounter(),
                    restored.test_getNewsUidCounter());
            if (!r.passed()) return r;
            r = assertEquals("scheduler countdown (123456 - 3000)", 120_456L,
                    restored.test_getSchedulerRemainingMs());
            if (!r.passed()) return r;
            r = assertEquals("cooldown remaining (120s - 3s)", 117_000L,
                    restored.test_getCooldownRemainingMs("pending"));
            if (!r.passed()) return r;
            r = assertEquals("one active event restored", 1, restored.getActiveEvents().size());
            if (!r.passed()) return r;

            ActiveNewsEvent restoredEvent = restored.getActiveEvents().get(0);
            r = assertEquals("definition id", "pending", restoredEvent.getDefinitionId());
            if (!r.passed()) return r;
            r = assertEquals("news uid", event.getNewsUid(), restoredEvent.getNewsUid());
            if (!r.passed()) return r;
            r = assertEquals("age accumulator", 3_000L, restoredEvent.getAgeMillis());
            if (!r.passed()) return r;
            r = assertFalse("still unpublished after the restart", restoredEvent.isPublished());
            if (!r.passed()) return r;
            r = assertEquals("market weight preserved", 0.75f,
                    restoredEvent.getMarketWeights().get(M1));
            if (!r.passed()) return r;
            r = assertEquals("headline snapshot preserved", "Headline pending",
                    restoredEvent.getHeadline().get("en_us"));
            if (!r.passed()) return r;

            // The remaining 5s of the pending-publish window must play out post-restart.
            restored.advanceTime(4_999, markets(m1));
            r = assertEquals("not published 1ms early", 0, restored.published.size());
            if (!r.passed()) return r;
            restored.advanceTime(1, markets(m1));
            r = assertEquals("published exactly at the publish moment",
                    1, restored.published.size());
            if (!r.passed()) return r;
            return assertEquals("published uid must be the pre-restart uid",
                    event.getNewsUid(), restored.published.get(0).getNewsUid());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Sequence runtime (T-095)
    // ========================================================================

    /** A minimal, fixed-duration linear step JSON object. */
    private static String stepJson(String name, String durationSeconds, double targetFactor,
                                   String extra) {
        return "{\"name\":\"" + name + "\",\"durationSeconds\":" + durationSeconds
                + ",\"targetFactor\":" + targetFactor
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    /**
     * The activation-time sequence pick must be proportional to the per-sequence
     * weights — both through the pure {@link NewsPlugin#pickSequenceIndex} math and
     * through a real activation with a pinned RNG (deterministic, no re-roll surface).
     */
    private TestResult test_sequencePick_honorsWeights() {
        Path dir = null;
        try {
            dir = createTempDir();
            String eventJson = sequenceEventJson("weighted", "",
                    "{\"name\":\"light\",\"weight\":1,\"steps\":["
                            + stepJson("a", "10", 0.2, "") + "," + stepJson("z", "10", 0.0, "") + "]}",
                    "{\"name\":\"heavy\",\"weight\":3,\"steps\":["
                            + stepJson("a", "10", 0.4, "") + "," + stepJson("z", "10", 0.0, "") + "]}");
            NewsEventDefinition def = parseEvent(eventJson);
            TestResult r = assertNotNull("weighted sequence event must parse", def);
            if (!r.passed()) return r;
            r = assertEquals("two sequences parsed", 2, def.getSequences().size());
            if (!r.passed()) return r;

            // Pure math: total weight 4 — rolls in [0, 0.25) pick "light", rest "heavy".
            r = assertEquals("roll 0.1 picks the light sequence", 0,
                    NewsPlugin.pickSequenceIndex(def.getSequences(), 0.1));
            if (!r.passed()) return r;
            r = assertEquals("roll 0.24 still picks the light sequence", 0,
                    NewsPlugin.pickSequenceIndex(def.getSequences(), 0.24));
            if (!r.passed()) return r;
            r = assertEquals("roll 0.25 picks the heavy sequence", 1,
                    NewsPlugin.pickSequenceIndex(def.getSequences(), 0.25));
            if (!r.passed()) return r;
            r = assertEquals("roll 0.9 picks the heavy sequence", 1,
                    NewsPlugin.pickSequenceIndex(def.getSequences(), 0.9));
            if (!r.passed()) return r;
            r = assertEquals("numeric edge roll 1.0 stays in range", 1,
                    NewsPlugin.pickSequenceIndex(def.getSequences(), 1.0));
            if (!r.passed()) return r;

            // Real activation with a pinned RNG: the picked name is frozen into the event.
            String file = fileJson("", eventJson);
            TestNewsPlugin heavyPlugin = pluginWithFile(dir, file);
            heavyPlugin.test_setRandom(new Random() {
                @Override public double nextDouble() { return 0.9; }
            });
            ActiveNewsEvent heavyEvent = heavyPlugin.activate(
                    heavyPlugin.getLibrary().getDefinition("weighted"), weights(M1, 1.0f));
            r = assertNotNull("activation with roll 0.9 must succeed", heavyEvent);
            if (!r.passed()) return r;
            r = assertEquals("roll 0.9 froze the heavy sequence", "heavy",
                    heavyEvent.getSequenceName());
            if (!r.passed()) return r;

            TestNewsPlugin lightPlugin = pluginWithFile(dir, file);
            lightPlugin.test_setRandom(new Random() {
                @Override public double nextDouble() { return 0.1; }
            });
            ActiveNewsEvent lightEvent = lightPlugin.activate(
                    lightPlugin.getLibrary().getDefinition("weighted"), weights(M1, 1.0f));
            r = assertNotNull("activation with roll 0.1 must succeed", lightEvent);
            if (!r.passed()) return r;
            return assertEquals("roll 0.1 froze the light sequence", "light",
                    lightEvent.getSequenceName());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Step durations rolled from a {@code {min, max}} range at activation must land
     * inside the range and must survive a save/load cycle verbatim — a restart never
     * re-rolls (self-contained snapshot doctrine).
     */
    private TestResult test_sequenceRolledDurations_persistAcrossSaveLoad() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", sequenceEventJson("rolled", "",
                    "{\"name\":\"swing\",\"steps\":["
                            + stepJson("up", "{\"min\":10,\"max\":20}", 0.3, "")
                            + "," + stepJson("down", "{\"min\":5,\"max\":10}", -0.2, "")
                            + "," + stepJson("settle", "10", 0.0, "") + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("rolled"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;

            List<NewsSequence.Step> steps = event.getSequence().getSteps();
            r = assertEquals("three resolved steps", 3, steps.size());
            if (!r.passed()) return r;
            long up = steps.get(0).durationMs();
            long down = steps.get(1).durationMs();
            r = assertTrue("'up' rolled inside [10s, 20s] (got " + up + " ms)",
                    up >= 10_000 && up <= 20_000);
            if (!r.passed()) return r;
            r = assertTrue("'down' rolled inside [5s, 10s] (got " + down + " ms)",
                    down >= 5_000 && down <= 10_000);
            if (!r.passed()) return r;
            r = assertEquals("fixed duration stays fixed", 10_000L, steps.get(2).durationMs());
            if (!r.passed()) return r;

            plugin.advanceTime(1_000, markets(new TestMarket(M1, 100)));
            CompoundTag saved = new CompoundTag();
            plugin.save(saved);

            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);
            r = assertEquals("active event restored", 1, restored.getActiveEvents().size());
            if (!r.passed()) return r;
            ActiveNewsEvent restoredEvent = restored.getActiveEvents().get(0);
            r = assertEquals("sequence name preserved", "swing", restoredEvent.getSequenceName());
            if (!r.passed()) return r;
            r = assertTrue("resolved steps (incl. rolled durations) preserved verbatim",
                    steps.equals(restoredEvent.getSequence().getSteps()));
            if (!r.passed()) return r;
            return assertEquals("age accumulator preserved", 1_000L,
                    restoredEvent.getAgeMillis());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A pre-T-095 save (no {@code steps} list, only the legacy envelope keys) must load
     * through the envelope normalization and resume mid-event with identical influence
     * values — a restart across the sequences upgrade must be seamless.
     */
    private TestResult test_sequenceLegacyNbt_eventLoadsAndResumes() {
        Path dir = null;
        try {
            dir = createTempDir();
            // trend: ramp 10s, hold 20s, ramp reversal 10s, peak +0.5 (eventJson default)
            String file = fileJson("", eventJson("legacy", "\"cooldownSeconds\":120"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);

            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("legacy"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(3_000, markets(m1)); // 3s into the 10s ramp

            CompoundTag saved = new CompoundTag();
            plugin.save(saved);
            // Simulate a pre-T-095 save: strip the new sequence-snapshot keys so only
            // the legacy envelope vocabulary remains.
            ListTag eventsTag = saved.getList("activeEvents", Tag.TAG_COMPOUND);
            r = assertEquals("one persisted active event", 1, eventsTag.size());
            if (!r.passed()) return r;
            CompoundTag eventTag = eventsTag.getCompound(0);
            eventTag.remove("steps");
            eventTag.remove("sequenceName");

            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);
            r = assertEquals("legacy-format event restored", 1, restored.getActiveEvents().size());
            if (!r.passed()) return r;
            ActiveNewsEvent restoredEvent = restored.getActiveEvents().get(0);
            r = assertEquals("age accumulator preserved", 3_000L, restoredEvent.getAgeMillis());
            if (!r.passed()) return r;
            r = assertEquals("normalized into the implicit 3-step sequence",
                    3, restoredEvent.getSequence().stepCount());
            if (!r.passed()) return r;
            r = assertEquals("implicit sequence name", "impact", restoredEvent.getSequenceName());
            if (!r.passed()) return r;
            r = assertNotNull("legacy envelope descriptor restored",
                    restoredEvent.getLegacyEnvelope());
            if (!r.passed()) return r;

            // Influence continuity: 3s into the ramp = 0.3 * 0.5 peak → factor 1.15 ...
            double factor = restored.combinedFactorFor(M1, 1.0f, false);
            r = assertTrue("mid-ramp factor resumes identically (got " + factor + ")",
                    Math.abs(factor - 1.15) < 1e-6);
            if (!r.passed()) return r;
            // ... and 12s later (age 15s, mid-hold) the peak applies in full.
            restored.advanceTime(12_000, markets(m1));
            factor = restored.combinedFactorFor(M1, 1.0f, false);
            r = assertTrue("mid-hold factor after resume (got " + factor + ")",
                    Math.abs(factor - 1.5) < 1e-6);
            if (!r.passed()) return r;
            return assertEquals("remaining time follows the normalized sequence",
                    25_000L, restoredEvent.remainingMillis());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Per-step markets (T-095): a step with its own {@code markets[]} list impacts only
     * its own resolved markets — step A hits M1 only, step B hits M2 only — while the
     * event's union set (caps/records/unsubscribe) carries both.
     */
    private TestResult test_sequencePerStepMarketSwitching() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Both steps use 'instant' so the influence is fully visible mid-step.
            String file = fileJson("", sequenceEventJson("switch", "",
                    "{\"name\":\"s\",\"steps\":["
                            + stepJson("first", "10", 0.5,
                                    "\"curve\":\"instant\",\"markets\":[{\"item\":\"minecraft:gold_ingot\"}]")
                            + "," + stepJson("second", "10", -0.4,
                                    "\"curve\":\"instant\",\"markets\":[{\"item\":\"minecraft:emerald\"}]")
                            + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            plugin.stepResolutions.put("switch#first", weights(M1, 1.0f));
            plugin.stepResolutions.put("switch#second", weights(M2, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            TestMarket m2 = new TestMarket(M2, 100);
            List<MarketInterface> markets = markets(m1, m2);

            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("switch"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            r = assertEquals("union market set carries both steps' markets",
                    2, event.getMarketWeights().size());
            if (!r.passed()) return r;
            r = assertTrue("union contains M1 and M2",
                    event.getMarketWeights().containsKey(M1)
                            && event.getMarketWeights().containsKey(M2));
            if (!r.passed()) return r;

            // Step "first" (instant +0.5): only M1 is influenced.
            plugin.advanceTime(1_000, markets);
            double f1 = plugin.combinedFactorFor(M1, 1.0f, false);
            double f2 = plugin.combinedFactorFor(M2, 1.0f, false);
            r = assertTrue("step 'first': M1 at +50% (got " + f1 + ")",
                    Math.abs(f1 - 1.5) < 1e-9);
            if (!r.passed()) return r;
            r = assertTrue("step 'first': M2 untouched (got " + f2 + ")",
                    Math.abs(f2 - 1.0) < 1e-9);
            if (!r.passed()) return r;

            // Step "second" (instant -0.4): influence switches to M2 only.
            plugin.advanceTime(10_000, markets); // age 11s → inside step 2
            r = assertEquals("current step switched", "second", event.currentStepName());
            if (!r.passed()) return r;
            f1 = plugin.combinedFactorFor(M1, 1.0f, false);
            f2 = plugin.combinedFactorFor(M2, 1.0f, false);
            r = assertTrue("step 'second': M1 released (got " + f1 + ")",
                    Math.abs(f1 - 1.0) < 1e-9);
            if (!r.passed()) return r;
            return assertTrue("step 'second': M2 at -40% (got " + f2 + ")",
                    Math.abs(f2 - 0.6) < 1e-9);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A {@code permanent} last step must bake the sequence's final value into the
     * default price exactly once at sequence end, retire the event and never bake again.
     */
    private TestResult test_sequencePermanentStep_bakesExactlyOnce() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", sequenceEventJson("permaseq", "",
                    "{\"name\":\"shift\",\"steps\":["
                            + stepJson("rise", "10", 0.5, "")
                            + ",{\"name\":\"lock\",\"durationSeconds\":5,\"curve\":\"hold\",\"permanent\":true}"
                            + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("permaseq"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            r = assertTrue("sequence flagged permanent", event.isPermanent());
            if (!r.passed()) return r;

            plugin.advanceTime(12_000, markets); // inside the 'lock' hold step
            r = assertTrue("default price untouched before the sequence end",
                    Math.abs(m1.defaultPrice - 100.0) < EPSILON);
            if (!r.passed()) return r;
            r = assertTrue("hold step keeps the full influence (got "
                            + plugin.combinedFactorFor(M1, 1.0f, false) + ")",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.5) < 1e-6);
            if (!r.passed()) return r;

            plugin.advanceTime(3_001, markets); // crosses the sequence end → bake
            r = assertTrue("final value baked into the default price (got "
                    + m1.defaultPrice + ")", Math.abs(m1.defaultPrice - 150.0) < 1e-6);
            if (!r.passed()) return r;
            r = assertEquals("event retired after the bake", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;

            plugin.advanceTime(60_000, markets);
            return assertTrue("no double-bake afterwards (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * skipPhase on a sequence event must walk the step boundaries one at a time
     * (PENDING → step 0 → step 1 → …), report the generic step outcome, end the event
     * normally on the last-step skip (activation cooldown keeps ticking — NOT re-armed)
     * and report NOTHING_TO_SKIP once terminal.
     */
    private TestResult test_sequenceSkip_walksStepBoundaries() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", sequenceEventJson("skipseq",
                    "\"cooldownSeconds\":60,\"announceDelayMs\":{\"min\":5000,\"max\":5000}",
                    "{\"name\":\"walk\",\"steps\":["
                            + stepJson("alpha", "10", 0.3, "")
                            + "," + stepJson("beta", "10", -0.2, "")
                            + "," + stepJson("gamma", "10", 0.0, "") + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("skipseq"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(1_000, markets); // cooldown 59s left, still pending

            // PENDING → step 0 ("alpha")
            r = assertEquals("skip 1: pending → first step",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_STEP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("impact starts exactly now", 0L, event.activeMillis());
            if (!r.passed()) return r;
            r = assertEquals("landed in 'alpha'", "alpha", event.currentStepName());
            if (!r.passed()) return r;

            // alpha → beta
            r = assertEquals("skip 2: step boundary",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_STEP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("'beta' starts at 10s", 10_000L, event.activeMillis());
            if (!r.passed()) return r;
            r = assertEquals("landed in 'beta'", "beta", event.currentStepName());
            if (!r.passed()) return r;

            // beta → gamma
            r = assertEquals("skip 3: step boundary",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_STEP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("landed in 'gamma'", "gamma", event.currentStepName());
            if (!r.passed()) return r;

            // gamma (last step) → ended: normal retirement, cooldown NOT re-armed
            r = assertEquals("skip 4: last step ends the event",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_END, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("terminal state has nothing to skip",
                    NewsPlugin.SkipOutcome.NOTHING_TO_SKIP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            plugin.advanceTime(0, markets); // realize the retirement
            r = assertEquals("event retired normally", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            return assertEquals("activation cooldown keeps ticking (no re-arm)",
                    59_000L, plugin.getCooldownRemainingMs("skipseq"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Skipping the permanent last step is a fast-forward (T-093 doctrine: skip =
     * fast-forward, stop = cancel): the full final value bakes like a natural completion.
     */
    private TestResult test_sequenceSkip_permanentLastStepBakes() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", sequenceEventJson("permskip", "",
                    "{\"name\":\"shift\",\"steps\":["
                            + stepJson("rise", "10", 0.5, "")
                            + ",{\"name\":\"lock\",\"durationSeconds\":30,\"curve\":\"hold\",\"permanent\":true}"
                            + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("permskip"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(1_000, markets); // mid-'rise'

            r = assertEquals("skip 1: rise → lock",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_STEP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("landed in 'lock'", "lock", event.currentStepName());
            if (!r.passed()) return r;
            r = assertEquals("skip 2: permanent last step fast-forwards to the bake",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_PERMANENT, plugin.skipPhase(event));
            if (!r.passed()) return r;

            plugin.advanceTime(0, markets); // realize the bake + retirement
            r = assertTrue("full permanent shift baked (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 150.0) < 1e-6);
            if (!r.passed()) return r;
            return assertEquals("event retired after the bake",
                    0, plugin.getActiveEvents().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Hard-stopping a sequence event mid-step must cancel it (any step), remove the
     * influence immediately and re-arm the full cooldown; a stopped permanent sequence
     * is cancelled without baking (CANCELLED_PERMANENT).
     */
    private TestResult test_sequenceStop_midStepCancelsAndRearmsCooldown() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("",
                    sequenceEventJson("stopseq", "\"cooldownSeconds\":60",
                            "{\"name\":\"walk\",\"steps\":["
                                    + stepJson("alpha", "10", 0.3, "")
                                    + "," + stepJson("beta", "10", 0.0, "") + "]}"),
                    sequenceEventJson("stopperma", "\"cooldownSeconds\":30",
                            "{\"name\":\"shift\",\"steps\":["
                                    + stepJson("rise", "10", 0.5, "")
                                    + ",{\"name\":\"lock\",\"durationSeconds\":30,\"curve\":\"hold\",\"permanent\":true}"
                                    + "]}"));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            // Non-permanent sequence, stopped inside its second step.
            ActiveNewsEvent event = plugin.activate(
                    plugin.getLibrary().getDefinition("stopseq"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation must succeed", event);
            if (!r.passed()) return r;
            plugin.advanceTime(15_000, markets); // 5s into 'beta'
            r = assertEquals("mid-step check", "beta", event.currentStepName());
            if (!r.passed()) return r;
            r = assertTrue("event influences the market before the stop",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) > 1e-6);
            if (!r.passed()) return r;

            r = assertEquals("stop outcome mid-step", NewsPlugin.StopOutcome.STOPPED,
                    plugin.stopEvent(event));
            if (!r.passed()) return r;
            r = assertEquals("event gone immediately", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("influence removed immediately",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < EPSILON);
            if (!r.passed()) return r;
            r = assertEquals("full cooldown re-armed from the stop moment",
                    60_000L, plugin.getCooldownRemainingMs("stopseq"));
            if (!r.passed()) return r;

            // Permanent sequence, stopped before its bake: cancel, never bake.
            ActiveNewsEvent permanent = plugin.activate(
                    plugin.getLibrary().getDefinition("stopperma"), weights(M1, 1.0f));
            r = assertNotNull("permanent activation must succeed", permanent);
            if (!r.passed()) return r;
            plugin.advanceTime(15_000, markets); // inside 'lock', bake still pending
            r = assertEquals("stop outcome for the un-baked permanent sequence",
                    NewsPlugin.StopOutcome.CANCELLED_PERMANENT, plugin.stopEvent(permanent));
            if (!r.passed()) return r;
            // Cooldown re-arm asserted BEFORE further time passes (advanceTime ticks it).
            r = assertEquals("permanent event's cooldown re-armed in full",
                    30_000L, plugin.getCooldownRemainingMs("stopperma"));
            if (!r.passed()) return r;
            plugin.advanceTime(60_000, markets); // nothing may bake afterwards
            return assertTrue("default price stays unbaked (got " + m1.defaultPrice + ")",
                    Math.abs(m1.defaultPrice - 100.0) < 1e-6);
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }
}
