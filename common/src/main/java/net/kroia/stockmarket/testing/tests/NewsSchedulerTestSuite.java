package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.RuntimeStreamData;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.TestMarket;
import net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.TestNewsPlugin;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.createTempDir;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.deleteRecursively;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.eventJson;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.fileJson;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.markets;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.pluginWithFile;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.weights;

/**
 * Tests for the T-082 news scheduler features: the admin scheduler overrides
 * (effective-value precedence over the JSON file values, atomic validation, NBT
 * persistence, per-value/all reset) and the pre-scheduled activation queue — refill to
 * {@link NewsPlugin#PLANNED_QUEUE_SIZE}, fire-time re-validation with repick/lapse,
 * resampling on interval change, planned-id re-validation after a library reload, the
 * legacy {@code schedulerRemainingMs} NBT migration and the extended runtime-stream
 * payload (upcoming timeline + effective scheduler state) round-trip.
 * <p>
 * Reuses the {@link NewsPluginTestSuite} test doubles ({@link TestNewsPlugin},
 * {@link TestMarket}) and JSON helpers — all time/eligibility logic is driven
 * deterministically via {@link NewsPlugin#advanceTime}, no sleeping, no Minecraft
 * context required.
 */
public class NewsSchedulerTestSuite extends TestSuite {

    /** Test markets (raw shorts are enough — matcher resolution is stubbed). */
    private static final ItemID M1 = new ItemID((short) 1);
    private static final ItemID M2 = new ItemID((short) 2);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_SCHEDULER;
    }

    @Override
    public void registerTests() {
        // Pre-scheduled queue (timeline)
        addTest("queue_refills_to_size_with_offsets_in_range", this::test_queue_refillsToSizeWithOffsetsInRange);
        addTest("fire_time_revalidation_repicks_disabled_planned_id", this::test_fireTime_revalidationRepicksDisabledPlannedId);
        addTest("disabled_event_never_fires_from_queue", this::test_disabledEvent_neverFiresFromQueue);
        addTest("admin_trigger_does_not_consume_queue", this::test_adminTrigger_doesNotConsumeQueue);
        addTest("queue_survives_save_load_round_trip", this::test_queue_survivesSaveLoadRoundTrip);
        addTest("legacy_scheduler_remaining_ms_nbt_loads", this::test_legacySchedulerRemainingMs_nbtLoads);
        addTest("time_only_slots_upgrade_when_events_become_eligible", this::test_timeOnlySlots_upgradeWhenEventsBecomeEligible);

        // Scheduler overrides
        addTest("override_precedence_and_persistence", this::test_override_precedenceAndPersistence);
        addTest("override_validation_rejects_invalid_values", this::test_override_validationRejectsInvalidValues);
        addTest("interval_override_resamples_queue", this::test_intervalOverride_resamplesQueue);
        addTest("reload_keeps_overrides_and_revalidates_planned_ids", this::test_reload_keepsOverridesAndRevalidatesPlannedIds);

        // Runtime stream payload
        addTest("stream_payload_round_trip", this::test_streamPayload_roundTrip);
        addTest("provide_runtime_data_carries_timeline_and_state", this::test_provideRuntimeData_carriesTimelineAndState);
    }

    // ========================================================================
    // Pre-scheduled queue (timeline)
    // ========================================================================

    /**
     * T-085: slots planned while nothing was eligible (empty library resolution, all
     * on cooldown, server start before market subscription) must not stay time-only
     * forever — {@link NewsPlugin#upgradeTimeOnlySlots} assigns planned ids once
     * events become eligible (keeping the fire times), and {@code advanceTime} runs
     * it automatically every {@link NewsPlugin#TIME_ONLY_UPGRADE_INTERVAL_MS} ticked
     * ms. Exactly one slot picks the single eligible event (already-planned ids are
     * excluded like at refill time).
     */
    private TestResult test_timeOnlySlots_upgradeWhenEventsBecomeEligible() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Huge interval so no slot can fire during the test.
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson(
                    "\"minSecondsBetweenEvents\":100000,\"maxSecondsBetweenEvents\":200000",
                    eventJson("late", "")));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            // No resolution registered yet -> nothing eligible -> all slots time-only.
            plugin.advanceTime(0, markets);
            List<RuntimeStreamData.UpcomingEvent> before = plugin.getPlannedActivations();
            TestResult r = assertEquals("queue planned to full size",
                    NewsPlugin.PLANNED_QUEUE_SIZE, before.size());
            if (!r.passed()) return r;
            for (RuntimeStreamData.UpcomingEvent slot : before) {
                r = assertTrue("slot planned time-only while nothing is eligible",
                        slot.eventId().isEmpty());
                if (!r.passed()) return r;
            }

            // The event becomes eligible now (matcher resolves to M1).
            plugin.resolutions.put("late", weights(M1, 1.0f));

            // Below the throttle: no upgrade yet.
            plugin.advanceTime(NewsPlugin.TIME_ONLY_UPGRADE_INTERVAL_MS - 1, markets);
            for (RuntimeStreamData.UpcomingEvent slot : plugin.getPlannedActivations()) {
                r = assertTrue("still time-only below the upgrade throttle",
                        slot.eventId().isEmpty());
                if (!r.passed()) return r;
            }

            // Crossing the throttle triggers the automatic upgrade.
            plugin.advanceTime(1, markets);
            List<RuntimeStreamData.UpcomingEvent> after = plugin.getPlannedActivations();
            int planned = 0;
            for (RuntimeStreamData.UpcomingEvent slot : after) {
                if (slot.eventId().isEmpty()) continue;
                planned++;
                r = assertEquals("the upgraded slot plans the eligible event",
                        "late", slot.eventId());
                if (!r.passed()) return r;
            }
            r = assertEquals("exactly one slot upgraded (already-planned ids excluded)",
                    1, planned);
            if (!r.passed()) return r;

            // Fire times are kept: every ETA only decreased by the ticked ms.
            for (int i = 0; i < after.size(); i++) {
                long expected = before.get(i).etaMs() - NewsPlugin.TIME_ONLY_UPGRADE_INTERVAL_MS;
                r = assertEquals("slot " + i + " keeps its fire time",
                        expected, after.get(i).etaMs());
                if (!r.passed()) return r;
            }
            return pass("time-only slots upgraded in place");
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A single zero-advance must plan the queue up to {@link NewsPlugin#PLANNED_QUEUE_SIZE}
     * slots with per-slot deltas sampled from the effective min/max interval. With one
     * eligible event the first slot plans it and the later slots (planning excludes
     * already-queued ids) become time-only.
     */
    private TestResult test_queue_refillsToSizeWithOffsetsInRange() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("\"minSecondsBetweenEvents\":2,\"maxSecondsBetweenEvents\":3",
                            eventJson("e", "")));
            plugin.resolutions.put("e", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            TestResult r = assertEquals("queue empty before the first advance",
                    0, plugin.getPlannedActivations().size());
            if (!r.passed()) return r;

            plugin.advanceTime(0, markets); // plans without ticking any time
            List<RuntimeStreamData.UpcomingEvent> upcoming = plugin.getPlannedActivations();
            r = assertEquals("queue refilled to PLANNED_QUEUE_SIZE",
                    NewsPlugin.PLANNED_QUEUE_SIZE, upcoming.size());
            if (!r.passed()) return r;
            r = assertEquals("first slot plans the only eligible event",
                    "e", upcoming.get(0).eventId());
            if (!r.passed()) return r;

            long previousEta = 0;
            for (int i = 0; i < upcoming.size(); i++) {
                RuntimeStreamData.UpcomingEvent slot = upcoming.get(i);
                long delta = slot.etaMs() - previousEta;
                if (delta < 2_000 || delta > 3_000) {
                    return fail("slot " + i + " delta " + delta + " ms left [2000, 3000]");
                }
                previousEta = slot.etaMs();
                if (i > 0 && !slot.eventId().isEmpty()) {
                    return fail("slot " + i + " should be time-only ('e' is already planned), got '"
                            + slot.eventId() + "'");
                }
            }

            // A second zero-advance must be idempotent (queue already full).
            plugin.advanceTime(0, markets);
            return assertEquals("refill is idempotent once the queue is full",
                    upcoming, plugin.getPlannedActivations());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Fire-time re-validation: when the planned event became ineligible after planning
     * (here: admin-disabled), the due slot must repick freshly among the currently
     * eligible events — the other event fires, the disabled one never does.
     */
    private TestResult test_fireTime_revalidationRepicksDisabledPlannedId() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("a", ""), eventJson("b", "")));
            plugin.resolutions.put("a", weights(M1, 1.0f));
            plugin.resolutions.put("b", weights(M2, 1.0f));
            List<MarketInterface> markets =
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100));

            plugin.advanceTime(0, markets); // plan the queue
            String plannedId = plugin.getPlannedActivations().get(0).eventId();
            TestResult r = assertTrue("head slot planned a concrete event",
                    plannedId.equals("a") || plannedId.equals("b"));
            if (!r.passed()) return r;
            String otherId = plannedId.equals("a") ? "b" : "a";

            // Invalidate the planned event AFTER planning, then make the slot due.
            plugin.setEventEnabled(plannedId, false);
            plugin.test_setSchedulerRemainingMs(1);
            plugin.advanceTime(10, markets);

            r = assertEquals("exactly one event fired from the queue",
                    1, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertEquals("the repick fired the still-eligible event",
                    otherId, plugin.getActiveEvents().get(0).getDefinitionId());
            if (!r.passed()) return r;
            return assertEquals("queue refilled back to PLANNED_QUEUE_SIZE after the fire",
                    NewsPlugin.PLANNED_QUEUE_SIZE, plugin.getPlannedActivations().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Hard requirement carried over from T-081: a disabled event can never fire — not
     * even from a queue slot that planned it while it was still enabled. With nothing
     * else eligible the slot lapses (no retry until the next slot).
     */
    private TestResult test_disabledEvent_neverFiresFromQueue() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("solo", "")));
            plugin.resolutions.put("solo", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            plugin.advanceTime(0, markets); // plan: head slot plans "solo"
            TestResult r = assertEquals("head slot planned the event",
                    "solo", plugin.getPlannedActivations().get(0).eventId());
            if (!r.passed()) return r;

            plugin.setEventEnabled("solo", false);
            plugin.test_setSchedulerRemainingMs(1);
            plugin.advanceTime(10, markets); // slot due: nothing eligible -> lapse

            r = assertEquals("no event may fire (slot lapsed)",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            return assertEquals("queue refilled after the lapsed slot",
                    NewsPlugin.PLANNED_QUEUE_SIZE, plugin.getPlannedActivations().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A manual admin trigger must not consume or shift any planned queue slot. */
    private TestResult test_adminTrigger_doesNotConsumeQueue() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("ev", "")));
            plugin.resolutions.put("ev", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            plugin.advanceTime(0, markets); // plan the queue
            List<RuntimeStreamData.UpcomingEvent> before = plugin.getPlannedActivations();

            // Manual trigger through the same seams the TRIGGER op uses.
            Map<ItemID, Float> resolved = plugin.resolveAdminTriggerMarkets(
                    plugin.getLibrary().getDefinition("ev"), markets, null);
            TestResult r = assertNotNull("admin trigger must activate",
                    plugin.activate(plugin.getLibrary().getDefinition("ev"), resolved));
            if (!r.passed()) return r;

            return assertEquals("queue unchanged by the admin trigger",
                    before, plugin.getPlannedActivations());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** The planned queue (offsets + planned ids) must survive a save/load round-trip. */
    private TestResult test_queue_survivesSaveLoadRoundTrip() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", eventJson("ev", ""));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            plugin.resolutions.put("ev", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            plugin.advanceTime(0, markets); // plan
            plugin.advanceTime(30_000, markets); // tick some head time away
            List<RuntimeStreamData.UpcomingEvent> before = plugin.getPlannedActivations();
            TestResult r = assertEquals("queue planned before the save",
                    NewsPlugin.PLANNED_QUEUE_SIZE, before.size());
            if (!r.passed()) return r;

            CompoundTag saved = new CompoundTag();
            plugin.save(saved);
            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);

            return assertEquals("planned queue identical after the round-trip",
                    before, restored.getPlannedActivations());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Legacy migration: a pre-T-082 save carries only {@code schedulerRemainingMs}. A
     * positive value must convert into a single time-only head slot (the old countdown
     * still fires when it was due, the rest refills behind it); the -1 "not sampled yet"
     * value must leave the queue empty for a from-scratch rebuild.
     */
    private TestResult test_legacySchedulerRemainingMs_nbtLoads() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("ev", "")));
            plugin.resolutions.put("ev", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            CompoundTag legacy = new CompoundTag();
            legacy.putLong("schedulerRemainingMs", 5_000);
            TestResult r = assertTrue("legacy tag loads cleanly", plugin.load(legacy));
            if (!r.passed()) return r;
            r = assertEquals("legacy countdown became the head slot ETA",
                    5_000L, plugin.test_getSchedulerRemainingMs());
            if (!r.passed()) return r;
            List<RuntimeStreamData.UpcomingEvent> upcoming = plugin.getPlannedActivations();
            r = assertEquals("exactly the converted head slot exists", 1, upcoming.size());
            if (!r.passed()) return r;
            r = assertEquals("converted slot is time-only", "", upcoming.get(0).eventId());
            if (!r.passed()) return r;

            // The refill must top up behind the converted slot without touching its ETA.
            plugin.advanceTime(0, markets);
            upcoming = plugin.getPlannedActivations();
            r = assertEquals("queue refilled behind the legacy slot",
                    NewsPlugin.PLANNED_QUEUE_SIZE, upcoming.size());
            if (!r.passed()) return r;
            r = assertEquals("legacy head ETA kept", 5_000L, upcoming.get(0).etaMs());
            if (!r.passed()) return r;

            // Legacy "-1 = not sampled yet" leaves the queue empty for a full rebuild.
            CompoundTag unsampled = new CompoundTag();
            unsampled.putLong("schedulerRemainingMs", -1);
            TestNewsPlugin plugin2 = pluginWithFile(dir, fileJson("", eventJson("ev", "")));
            r = assertTrue("legacy -1 tag loads cleanly", plugin2.load(unsampled));
            if (!r.passed()) return r;
            return assertEquals("unsampled legacy countdown leaves the queue empty",
                    0, plugin2.getPlannedActivations().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Scheduler overrides
    // ========================================================================

    /**
     * Override precedence: an admin override wins over the file value, values without an
     * override keep following the file; the override set must survive a plugin save/load
     * round-trip; a per-value reset (negative sentinel) returns exactly that value to
     * the file while the other overrides stay.
     */
    private TestResult test_override_precedenceAndPersistence() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson(
                    "\"minSecondsBetweenEvents\":10,\"maxSecondsBetweenEvents\":20,"
                            + "\"maxActiveEventsGlobal\":3,\"maxActiveEventsPerMarket\":1",
                    eventJson("ev", ""));
            TestNewsPlugin plugin = pluginWithFile(dir, file);
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            TestResult r = assertEquals("file min applies without overrides",
                    10L, plugin.getEffectiveMinSecondsBetweenEvents());
            if (!r.passed()) return r;

            String error = plugin.applySchedulerOverrides(5L, 50L, 7, 2, false, markets);
            r = assertNull("valid override set must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;
            r = assertEquals("min override effective", 5L, plugin.getEffectiveMinSecondsBetweenEvents());
            if (!r.passed()) return r;
            r = assertEquals("max override effective", 50L, plugin.getEffectiveMaxSecondsBetweenEvents());
            if (!r.passed()) return r;
            r = assertEquals("global cap override effective", 7, plugin.getEffectiveMaxActiveEventsGlobal());
            if (!r.passed()) return r;
            r = assertEquals("per-market cap override effective", 2, plugin.getEffectiveMaxActiveEventsPerMarket());
            if (!r.passed()) return r;
            RuntimeStreamData.SchedulerState state = plugin.getSchedulerState();
            r = assertTrue("all four values report overridden", state.minOverridden()
                    && state.maxOverridden() && state.globalOverridden() && state.perMarketOverridden());
            if (!r.passed()) return r;

            // Persistence round-trip.
            CompoundTag saved = new CompoundTag();
            plugin.save(saved);
            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);
            r = assertEquals("restored scheduler state matches",
                    plugin.getSchedulerState(), restored.getSchedulerState());
            if (!r.passed()) return r;

            // Per-value reset: min falls back to the file (10), max override stays (50).
            error = restored.applySchedulerOverrides(-1L, null, null, null, false, markets);
            r = assertNull("per-value reset must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;
            r = assertEquals("min back to the file value", 10L, restored.getEffectiveMinSecondsBetweenEvents());
            if (!r.passed()) return r;
            r = assertFalse("min no longer reports overridden", restored.getSchedulerState().minOverridden());
            if (!r.passed()) return r;
            r = assertTrue("max override untouched by the min reset",
                    restored.getSchedulerState().maxOverridden());
            if (!r.passed()) return r;

            // Reset-all clears the remaining overrides.
            error = restored.applySchedulerOverrides(null, null, null, null, true, markets);
            r = assertNull("reset-all must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;
            RuntimeStreamData.SchedulerState reset = restored.getSchedulerState();
            r = assertEquals("max back to the file value", 20L, reset.maxSecondsBetweenEvents());
            if (!r.passed()) return r;
            return assertFalse("nothing reports overridden after reset-all", reset.minOverridden()
                    || reset.maxOverridden() || reset.globalOverridden() || reset.perMarketOverridden());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Validation is atomic and checks the resulting <b>effective</b> values: zero
     * min/max, min &gt; max (within one call or against the file value) and caps &lt; 1
     * are all rejected with a message and must leave the state untouched. (Negative
     * inputs are not invalid — they are the documented per-value reset sentinel and can
     * never come from user input, the command layer only accepts values &gt;= 1.)
     */
    private TestResult test_override_validationRejectsInvalidValues() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson(
                    "\"minSecondsBetweenEvents\":10,\"maxSecondsBetweenEvents\":20",
                    eventJson("ev", "")));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));
            RuntimeStreamData.SchedulerState initial = plugin.getSchedulerState();

            TestResult r = assertNotNull("min = 0 must be rejected",
                    plugin.applySchedulerOverrides(0L, null, null, null, false, markets));
            if (!r.passed()) return r;
            r = assertNotNull("min > max in one call must be rejected",
                    plugin.applySchedulerOverrides(30L, 20L, null, null, false, markets));
            if (!r.passed()) return r;
            r = assertNotNull("min above the effective (file) max must be rejected",
                    plugin.applySchedulerOverrides(100L, null, null, null, false, markets));
            if (!r.passed()) return r;
            r = assertNotNull("max below the effective (file) min must be rejected",
                    plugin.applySchedulerOverrides(null, 5L, null, null, false, markets));
            if (!r.passed()) return r;
            r = assertNotNull("global cap 0 must be rejected",
                    plugin.applySchedulerOverrides(null, null, 0, null, false, markets));
            if (!r.passed()) return r;
            r = assertNotNull("per-market cap 0 must be rejected",
                    plugin.applySchedulerOverrides(null, null, null, 0, false, markets));
            if (!r.passed()) return r;

            return assertEquals("rejected calls must not change any state",
                    initial, plugin.getSchedulerState());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Changing the effective min/max interval must resample the whole planned queue with
     * the new pacing (keeping the old fire times would pace the next
     * {@link NewsPlugin#PLANNED_QUEUE_SIZE} slots with the outdated interval).
     */
    private TestResult test_intervalOverride_resamplesQueue() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Degenerate file interval min=max=100 s -> every delta is exactly 100000 ms.
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson(
                    "\"minSecondsBetweenEvents\":100,\"maxSecondsBetweenEvents\":100",
                    eventJson("ev", "")));
            plugin.resolutions.put("ev", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            plugin.advanceTime(0, markets);
            TestResult r = assertEquals("file-paced head ETA",
                    100_000L, plugin.getPlannedActivations().get(0).etaMs());
            if (!r.passed()) return r;

            String error = plugin.applySchedulerOverrides(1L, 1L, null, null, false, markets);
            r = assertNull("interval override must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;

            List<RuntimeStreamData.UpcomingEvent> upcoming = plugin.getPlannedActivations();
            r = assertEquals("resampled queue is full again",
                    NewsPlugin.PLANNED_QUEUE_SIZE, upcoming.size());
            if (!r.passed()) return r;
            for (int i = 0; i < upcoming.size(); i++) {
                if (upcoming.get(i).etaMs() != (i + 1) * 1_000L) {
                    return fail("slot " + i + " ETA " + upcoming.get(i).etaMs()
                            + " ms, expected " + ((i + 1) * 1_000L) + " (override pacing)");
                }
            }

            // A cap-only change must NOT resample (interval unchanged).
            List<RuntimeStreamData.UpcomingEvent> before = plugin.getPlannedActivations();
            error = plugin.applySchedulerOverrides(null, null, 9, null, false, markets);
            r = assertNull("cap override must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;
            return assertEquals("cap-only change keeps the planned times",
                    before, plugin.getPlannedActivations());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Library reload semantics (T-082): the admin overrides survive (they are plugin
     * state) and the planned queue keeps its fire times while ids whose definition
     * vanished are re-validated — here the vanished id cannot repick (the only other
     * event is already planned in another slot), so its slot becomes time-only.
     */
    private TestResult test_reload_keepsOverridesAndRevalidatesPlannedIds() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("a", ""), eventJson("b", "")));
            plugin.resolutions.put("a", weights(M1, 1.0f));
            plugin.resolutions.put("b", weights(M2, 1.0f));
            List<MarketInterface> markets =
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100));

            String error = plugin.applySchedulerOverrides(null, null, 9, null, false, markets);
            TestResult r = assertNull("override must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;

            plugin.advanceTime(0, markets); // plan: slots 1+2 plan "a" and "b" (some order)
            List<RuntimeStreamData.UpcomingEvent> before = plugin.getPlannedActivations();
            r = assertTrue("both events are planned",
                    before.stream().anyMatch(u -> u.eventId().equals("a"))
                            && before.stream().anyMatch(u -> u.eventId().equals("b")));
            if (!r.passed()) return r;

            // Remove "a" from the library and re-validate (what reloadLibrary() does).
            Files.writeString(dir.resolve("events.json"), fileJson("", eventJson("b", "")));
            plugin.getLibrary().reload(dir);
            plugin.revalidatePlannedQueue(markets);

            r = assertEquals("override survives the library reload",
                    9, plugin.getEffectiveMaxActiveEventsGlobal());
            if (!r.passed()) return r;

            List<RuntimeStreamData.UpcomingEvent> after = plugin.getPlannedActivations();
            r = assertEquals("slot count unchanged", before.size(), after.size());
            if (!r.passed()) return r;
            for (int i = 0; i < before.size(); i++) {
                if (before.get(i).etaMs() != after.get(i).etaMs()) {
                    return fail("slot " + i + " fire time changed by the re-validation ("
                            + before.get(i).etaMs() + " -> " + after.get(i).etaMs() + ")");
                }
                String beforeId = before.get(i).eventId();
                String afterId = after.get(i).eventId();
                if (beforeId.equals("a")) {
                    // "b" is already planned elsewhere -> the invalid slot goes time-only.
                    if (!afterId.isEmpty()) {
                        return fail("vanished id 'a' in slot " + i
                                + " must be re-validated away, got '" + afterId + "'");
                    }
                } else if (!afterId.equals(beforeId)) {
                    return fail("valid slot " + i + " must keep its planned id ('"
                            + beforeId + "' -> '" + afterId + "')");
                }
            }
            return pass("overrides kept, times kept, vanished id re-validated");
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Runtime stream payload
    // ========================================================================

    /**
     * The extended {@link RuntimeStreamData#CODEC} must round-trip the T-082 additions
     * exactly: upcoming entries (incl. empty time-only ids) and the effective scheduler
     * state with mixed override flags. Guards the no-version-byte lockstep contract.
     */
    private TestResult test_streamPayload_roundTrip() {
        List<RuntimeStreamData.MarketFactor> factors = new ArrayList<>();
        factors.add(new RuntimeStreamData.MarketFactor((short) 3, 1.25f));
        List<RuntimeStreamData.ActiveEventInfo> events = new ArrayList<>();
        events.add(new RuntimeStreamData.ActiveEventInfo(
                "running", "Headline", "HOLDING", 12_345L, true, factors));

        List<RuntimeStreamData.UpcomingEvent> upcoming = new ArrayList<>();
        upcoming.add(new RuntimeStreamData.UpcomingEvent("planned_event", 60_000L));
        upcoming.add(new RuntimeStreamData.UpcomingEvent("", 120_000L)); // time-only slot

        RuntimeStreamData.SchedulerState scheduler = new RuntimeStreamData.SchedulerState(
                600L, true, 3_600L, false, 5, true, 1, false); // mixed override flags

        RuntimeStreamData original = new RuntimeStreamData(events, upcoming, scheduler);
        ByteBuf buf = Unpooled.buffer();
        try {
            RuntimeStreamData.CODEC.encode(buf, original);
            RuntimeStreamData decoded = RuntimeStreamData.CODEC.decode(buf);
            TestResult r = assertEquals("record equality after the round-trip", original, decoded);
            if (!r.passed()) return r;
            return assertEquals("no bytes left unread", 0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    /**
     * End-to-end wiring: the plugin's streamed snapshot ({@code encodeRuntimeData()},
     * the exact bytes the framework ships) must carry the current planned timeline and
     * the effective scheduler state including override flags.
     */
    private TestResult test_provideRuntimeData_carriesTimelineAndState() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson(
                    "\"minSecondsBetweenEvents\":10,\"maxSecondsBetweenEvents\":20",
                    eventJson("ev", "")));
            plugin.resolutions.put("ev", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            String error = plugin.applySchedulerOverrides(null, null, 4, null, false, markets);
            TestResult r = assertNull("override must be accepted (got '" + error + "')", error);
            if (!r.passed()) return r;
            plugin.advanceTime(0, markets); // plan the queue

            byte[] bytes = plugin.encodeRuntimeData();
            r = assertNotNull("plugin must stream runtime data", bytes);
            if (!r.passed()) return r;
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            RuntimeStreamData decoded = RuntimeStreamData.CODEC.decode(buf);

            r = assertEquals("streamed upcoming timeline matches the queue",
                    plugin.getPlannedActivations(), decoded.upcoming());
            if (!r.passed()) return r;
            r = assertEquals("streamed scheduler state matches the plugin",
                    plugin.getSchedulerState(), decoded.scheduler());
            if (!r.passed()) return r;
            r = assertTrue("the overridden cap is flagged as override",
                    decoded.scheduler().globalOverridden());
            if (!r.passed()) return r;
            return assertFalse("the untouched min is flagged as file value",
                    decoded.scheduler().minOverridden());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }
}
