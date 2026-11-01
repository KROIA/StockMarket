package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.networking.request.NewsAdminRequest;
import net.kroia.stockmarket.networking.request.NewsAdminRequest.EventDetails;
import net.kroia.stockmarket.news.ActiveNewsEvent;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.RuntimeStreamData;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.TestMarket;
import net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.TestNewsPlugin;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.createTempDir;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.deleteRecursively;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.eventJson;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.fileJson;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.markets;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.pluginWithFile;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.sequenceEventJson;
import static net.kroia.stockmarket.testing.tests.NewsPluginTestSuite.weights;

/**
 * Tests for the T-081 news admin features: per-event enable/disable gating (a disabled
 * event can NEVER activate — neither via the random scheduler nor via a manual admin
 * trigger), the disabled-set persistence (plugin save/load round-trip, survival of
 * library reloads and definition absence, absent-NBT-key backward compatibility), the
 * {@code /stockmarket news info} line rendering and the structured {@link EventDetails}
 * payload (content + hand-written wire codec round-trip) consumed by the T-083 GUI.
 * <p>
 * T-093 additions: the per-event hard-stop core ({@code Op.STOP_EVENT} /
 * {@link NewsAdminRequest#performStopEvent} — event ends in any phase incl. recovery,
 * influence removed immediately, full cooldown restarted), the skip-phase core
 * ({@code Op.SKIP_PHASE} / {@link NewsAdminRequest#performSkipPhase} — pending →
 * ramp-up → hold → recovery → ended, {@code reversal:none} bakes on a terminal skip)
 * and the extended wire-ordinal guard for the appended ops 8/9.
 * <p>
 * Reuses the {@link NewsPluginTestSuite} test doubles ({@link TestNewsPlugin},
 * {@link TestMarket}) and JSON helpers — all time/eligibility logic is driven
 * deterministically, no sleeping, no Minecraft context required.
 */
public class NewsAdminTestSuite extends TestSuite {

    /** Test markets (raw shorts are enough — matcher resolution is stubbed). */
    private static final ItemID M1 = new ItemID((short) 1);
    private static final ItemID M2 = new ItemID((short) 2);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_ADMIN;
    }

    @Override
    public void registerTests() {
        // Enable/disable gating
        addTest("disabled_event_never_eligible_or_activatable", this::test_disabledEvent_neverEligibleOrActivatable);
        addTest("enable_restores_eligibility_and_activation", this::test_enable_restoresEligibilityAndActivation);

        // Persistence
        addTest("disabled_state_survives_save_load_round_trip", this::test_disabledState_survivesSaveLoadRoundTrip);
        addTest("disabled_id_survives_library_reload_and_absence", this::test_disabledId_survivesLibraryReloadAndAbsence);

        // INFO rendering + structured GUI payload (NewsAdminRequest server-side logic)
        addTest("render_event_info_contains_key_fields", this::test_renderEventInfo_containsKeyFields);
        addTest("event_details_reflect_plugin_state", this::test_eventDetails_reflectPluginState);
        addTest("event_details_codec_round_trip", this::test_eventDetails_codecRoundTrip);

        // T-085: RESET_COOLDOWN
        addTest("reset_cooldown_restores_eligibility", this::test_resetCooldown_restoresEligibility);
        addTest("reset_cooldown_unknown_or_idle_id_is_a_no_op", this::test_resetCooldown_unknownOrIdleIdIsNoOp);
        addTest("admin_op_wire_ordinals_are_appended", this::test_adminOp_wireOrdinalsAreAppended);

        // T-093: per-event hard stop (STOP_EVENT) + skip phase (SKIP_PHASE)
        addTest("stop_event_in_buildup_restarts_cooldown", this::test_stopEvent_inBuildupRestartsCooldown);
        addTest("stop_event_in_recovery_removes_influence", this::test_stopEvent_inRecoveryRemovesInfluence);
        addTest("stop_all_in_recovery_hard_stops_every_event", this::test_stopAll_inRecoveryHardStopsEveryEvent);
        addTest("skip_phase_walks_all_phases_to_normal_end", this::test_skipPhase_walksAllPhasesToNormalEnd);
        addTest("skip_phase_permanent_hold_bakes_like_completion", this::test_skipPhase_permanentHoldBakesLikeCompletion);
        addTest("stop_or_skip_non_active_is_clean_status", this::test_stopOrSkip_nonActiveIsCleanStatus);

        // T-099: registry admin ops (REGISTRY_LIST/REGISTRY_CLEAR static cores)
        addTest("registry_list_renders_fires_and_values", this::test_registryList_rendersFiresAndValues);
        addTest("registry_clear_variants_and_no_ops", this::test_registryClear_variantsAndNoOps);

        // T-099: INFO/EventDetails for sequence events (getImpact() NPE fix) + legacy parity
        addTest("info_and_details_render_sequence_event", this::test_infoAndDetails_renderSequenceEvent);
        addTest("info_legacy_rendering_unchanged", this::test_infoLegacyRenderingUnchanged);
        addTest("event_details_requirement_status_tracks_registry", this::test_eventDetails_requirementStatusTracksRegistry);

        // T-099: wire appends (NewsRecord sequence fields, ActiveEventInfo step fields)
        addTest("news_record_sequence_fields_round_trip", this::test_newsRecord_sequenceFieldsRoundTrip);
        addTest("active_event_info_step_fields_round_trip", this::test_activeEventInfo_stepFieldsRoundTrip);
    }

    // ========================================================================
    // Enable/disable gating
    // ========================================================================

    /**
     * Hard requirement (T-081): a disabled event can NEVER activate. The random
     * scheduler must not see it ({@code computeEligibleEvents}) and even a direct
     * {@code activate()} call — the seam every trigger path funnels through — must
     * refuse it.
     */
    private TestResult test_disabledEvent_neverEligibleOrActivatable() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("solo", "")));
            plugin.resolutions.put("solo", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            TestResult r = assertEquals("event eligible while enabled",
                    1, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;
            r = assertTrue("event enabled by default", plugin.isEventEnabled("solo"));
            if (!r.passed()) return r;

            plugin.setEventEnabled("solo", false);
            r = assertFalse("event reports disabled", plugin.isEventEnabled("solo"));
            if (!r.passed()) return r;
            r = assertEquals("disabled event must not be eligible for random scheduling",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            // The activation seam itself must refuse (defense in depth — covers the
            // manual admin trigger path and any future caller).
            NewsEventDefinition def = plugin.getLibrary().getDefinition("solo");
            r = assertNull("activate() must refuse a disabled event",
                    plugin.activate(def, weights(M1, 1.0f)));
            if (!r.passed()) return r;
            return assertEquals("no active event may exist after the refused activation",
                    0, plugin.getActiveEvents().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Re-enabling a disabled event must fully restore eligibility and activation. */
    private TestResult test_enable_restoresEligibilityAndActivation() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("solo", "")));
            plugin.resolutions.put("solo", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            plugin.setEventEnabled("solo", false);
            TestResult r = assertEquals("disabled: not eligible",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            plugin.setEventEnabled("solo", true);
            r = assertTrue("event reports enabled again", plugin.isEventEnabled("solo"));
            if (!r.passed()) return r;
            r = assertEquals("re-enabled: eligible again",
                    1, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;
            return assertNotNull("re-enabled: activation must succeed",
                    plugin.activate(plugin.getLibrary().getDefinition("solo"), weights(M1, 1.0f)));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    /**
     * The disabled set must survive a plugin save/load round-trip — including ids whose
     * definition is not in the library ("ghost" ids from removed files). Loading a tag
     * without the {@code disabledEvents} key (pre-T-081 save) must reset to all-enabled
     * (backward compatibility: absent NBT keys → defaults).
     */
    private TestResult test_disabledState_survivesSaveLoadRoundTrip() {
        Path dir = null;
        try {
            dir = createTempDir();
            String file = fileJson("", eventJson("a", ""), eventJson("b", ""));
            TestNewsPlugin plugin = pluginWithFile(dir, file);

            plugin.setEventEnabled("a", false);
            plugin.setEventEnabled("ghost", false); // id without a loaded definition

            CompoundTag saved = new CompoundTag();
            plugin.save(saved);

            TestNewsPlugin restored = pluginWithFile(dir, file);
            restored.load(saved);
            TestResult r = assertFalse("'a' still disabled after the round-trip",
                    restored.isEventEnabled("a"));
            if (!r.passed()) return r;
            r = assertFalse("absent 'ghost' id still disabled after the round-trip",
                    restored.isEventEnabled("ghost"));
            if (!r.passed()) return r;
            r = assertTrue("'b' untouched (enabled)", restored.isEventEnabled("b"));
            if (!r.passed()) return r;
            r = assertEquals("disabled snapshot carries both ids",
                    2, restored.getDisabledEventIds().size());
            if (!r.passed()) return r;

            // Pre-T-081 save data: no "disabledEvents" key -> everything enabled.
            TestNewsPlugin legacy = pluginWithFile(dir, file);
            legacy.setEventEnabled("a", false);
            legacy.load(new CompoundTag());
            return assertTrue("loading a tag without the key resets to all-enabled",
                    legacy.isEventEnabled("a") && legacy.getDisabledEventIds().isEmpty());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * A disabled id must survive library reloads — even a reload where its definition
     * file was removed. Re-adding the file must find the event still disabled, while
     * other events stay unaffected.
     */
    private TestResult test_disabledId_survivesLibraryReloadAndAbsence() {
        Path dir = null;
        try {
            dir = createTempDir();
            String bothEvents = fileJson("", eventJson("a", ""), eventJson("b", ""));
            TestNewsPlugin plugin = pluginWithFile(dir, bothEvents);
            plugin.resolutions.put("a", weights(M1, 1.0f));
            plugin.resolutions.put("b", weights(M2, 1.0f));
            List<MarketInterface> markets =
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100));

            plugin.setEventEnabled("a", false);

            // Reload without 'a' (file rewritten): the disabled id must be kept.
            Files.writeString(dir.resolve("events.json"), fileJson("", eventJson("b", "")));
            plugin.getLibrary().reload(dir);
            TestResult r = assertNull("'a' is gone from the library",
                    plugin.getLibrary().getDefinition("a"));
            if (!r.passed()) return r;
            r = assertFalse("'a' stays disabled while its definition is absent",
                    plugin.isEventEnabled("a"));
            if (!r.passed()) return r;

            // Re-add 'a': it must come back still disabled.
            Files.writeString(dir.resolve("events.json"), bothEvents);
            plugin.getLibrary().reload(dir);
            r = assertNotNull("'a' is loaded again", plugin.getLibrary().getDefinition("a"));
            if (!r.passed()) return r;
            r = assertFalse("re-added 'a' is still disabled", plugin.isEventEnabled("a"));
            if (!r.passed()) return r;

            List<net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin.EligibleEvent> eligible =
                    plugin.computeEligibleEvents(markets);
            r = assertEquals("only the enabled event is eligible", 1, eligible.size());
            if (!r.passed()) return r;
            return assertEquals("the eligible event is 'b'",
                    "b", eligible.get(0).definition().getId());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // INFO rendering + EventDetails payload
    // ========================================================================

    /**
     * The {@code news info} rendering must contain the load-bearing facts: the id with
     * its state markers, headline/text, impact parameters, the matchers and the
     * matched∩subscribed markets with weight factors.
     */
    private TestResult test_renderEventInfo_containsKeyFields() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("infoevent", "\"adminOnly\":true")));
            plugin.resolutions.put("infoevent", weights(M1, 0.75f));
            plugin.setEventEnabled("infoevent", false);

            NewsEventDefinition def = plugin.getLibrary().getDefinition("infoevent");
            List<String> lines = NewsAdminRequest.renderEventInfo(plugin, def,
                    markets(new TestMarket(M1, 100)));
            String joined = String.join("\n", lines);

            TestResult r = assertTrue("header names the event", joined.contains("'infoevent'"));
            if (!r.passed()) return r;
            r = assertTrue("disabled marker present", joined.contains("[disabled]"));
            if (!r.passed()) return r;
            r = assertTrue("adminOnly marker present", joined.contains("[adminOnly]"));
            if (!r.passed()) return r;
            r = assertTrue("headline text present", joined.contains("Headline infoevent"));
            if (!r.passed()) return r;
            r = assertTrue("news text present", joined.contains("Text infoevent"));
            if (!r.passed()) return r;
            // eventJson: trend, peak 0.5, ramp 10 s, hold 20 s, ramp reversal 10 s
            r = assertTrue("peak factor rendered", joined.contains("peakFactor 0.5"));
            if (!r.passed()) return r;
            r = assertTrue("ramp-up rendered", joined.contains("rampUp 10 s"));
            if (!r.passed()) return r;
            r = assertTrue("reversal rendered", joined.contains("reversal ramp"));
            if (!r.passed()) return r;
            r = assertTrue("matcher rendered", joined.contains("minecraft:diamond"));
            if (!r.passed()) return r;
            r = assertTrue("matched market count rendered",
                    joined.contains("Would impact 1 subscribed market(s)"));
            if (!r.passed()) return r;
            return assertTrue("per-market weight factor rendered",
                    joined.contains("weightFactor 0.75"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * {@code buildEventDetails} must snapshot the full plugin/definition state: enabled
     * and active flags, cooldown, weight, the complete (insertion-ordered) translation
     * maps, the envelope parameters and the matched∩subscribed markets.
     */
    private TestResult test_eventDetails_reflectPluginState() {
        Path dir = null;
        try {
            dir = createTempDir();
            // "multi": explicit translation maps, custom weight/cooldown/delay range.
            String multi = "{\"id\":\"multi\","
                    + "\"headline\":{\"en_us\":\"H en\",\"de_de\":\"H de\"},"
                    + "\"text\":{\"en_us\":\"T en\",\"de_de\":\"T de\"},"
                    + "\"weight\":2.5,\"cooldownSeconds\":60,"
                    + "\"announceDelayMs\":{\"min\":-1000,\"max\":2000},"
                    + "\"impact\":{\"type\":\"trend\",\"peakFactor\":0.5,\"rampUpSeconds\":10,"
                    + "\"durationSeconds\":20,\"reversal\":\"ramp\",\"reversalSeconds\":10},"
                    + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}";
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", multi, eventJson("other", "")));
            Map<ItemID, Float> both = weights(M1, 1.0f);
            both.put(M2, -0.5f);
            plugin.resolutions.put("multi", both);
            plugin.resolutions.put("other", weights(M1, 1.0f));
            List<MarketInterface> markets =
                    markets(new TestMarket(M1, 100), new TestMarket(M2, 100));

            plugin.setEventEnabled("other", false);
            TestResult r = assertNotNull("'multi' activates (cooldown starts)",
                    plugin.activate(plugin.getLibrary().getDefinition("multi"), both));
            if (!r.passed()) return r;

            List<EventDetails> details = NewsAdminRequest.buildEventDetails(plugin, markets);
            r = assertEquals("one details entry per loaded definition", 2, details.size());
            if (!r.passed()) return r;

            EventDetails d = details.stream().filter(e -> e.id().equals("multi")).findFirst().orElse(null);
            r = assertNotNull("'multi' entry exists", d);
            if (!r.passed()) return r;
            r = assertTrue("'multi' is enabled", d.enabled());
            if (!r.passed()) return r;
            r = assertTrue("'multi' is active", d.active());
            if (!r.passed()) return r;
            r = assertEquals("cooldown remaining (60s, no time advanced)",
                    60_000L, d.cooldownRemainingMs());
            if (!r.passed()) return r;
            r = assertEquals("weight", 2.5f, d.weight());
            if (!r.passed()) return r;
            r = assertEquals("full headline map shipped", 2, d.headline().size());
            if (!r.passed()) return r;
            r = assertEquals("headline insertion order preserved (en_us first)",
                    "en_us", d.headline().keySet().iterator().next());
            if (!r.passed()) return r;
            r = assertEquals("de_de headline", "H de", d.headline().get("de_de"));
            if (!r.passed()) return r;
            r = assertEquals("de_de text", "T de", d.text().get("de_de"));
            if (!r.passed()) return r;
            r = assertEquals("announce min", -1000L, d.announceMinMs());
            if (!r.passed()) return r;
            r = assertEquals("announce max", 2000L, d.announceMaxMs());
            if (!r.passed()) return r;
            r = assertTrue("peak factor", Math.abs(d.peakFactor() - 0.5) < 1e-9);
            if (!r.passed()) return r;
            r = assertEquals("ramp-up seconds", 10L, d.rampUpSeconds());
            if (!r.passed()) return r;
            r = assertEquals("duration seconds", 20L, d.durationSeconds());
            if (!r.passed()) return r;
            r = assertEquals("reversal mode", "ramp", d.reversal());
            if (!r.passed()) return r;
            r = assertEquals("reversal seconds", 10L, d.reversalSeconds());
            if (!r.passed()) return r;
            r = assertEquals("both matched markets listed", 2, d.markets().size());
            if (!r.passed()) return r;
            EventDetails.MarketImpact m2 = d.markets().stream()
                    .filter(m -> m.marketId() == M2.getShort()).findFirst().orElse(null);
            r = assertNotNull("M2 impact entry exists", m2);
            if (!r.passed()) return r;
            r = assertEquals("M2 keeps its negative weight factor", -0.5f, m2.weightFactor());
            if (!r.passed()) return r;
            r = assertNotNull("M2 display name captured server-side", m2.displayName());
            if (!r.passed()) return r;

            EventDetails o = details.stream().filter(e -> e.id().equals("other")).findFirst().orElse(null);
            r = assertNotNull("'other' entry exists", o);
            if (!r.passed()) return r;
            r = assertFalse("'other' reports disabled", o.enabled());
            if (!r.passed()) return r;
            return assertFalse("'other' is not active", o.active());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // RESET_COOLDOWN (T-085)
    // ========================================================================

    /**
     * {@code NewsPlugin.resetCooldown} must clear a running cooldown and thereby make
     * the event eligible for the random scheduler again (the GUI's per-row
     * Reset-cooldown button and {@code /stockmarket news resetcooldown} funnel into
     * this seam via {@code NewsAdminRequest.Op.RESET_COOLDOWN}).
     * <p>
     * The request-level admin gate is the shared pre-dispatch {@code playerIsAdmin}
     * check of {@code handleOnMasterServer} (identical for every op) and needs a
     * master-server context — it is therefore not separately drivable here.
     */
    private TestResult test_resetCooldown_restoresEligibility() {
        Path dir = null;
        try {
            dir = createTempDir();
            // Huge scheduler interval so no planned slot can fire during the test.
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson(
                    "\"minSecondsBetweenEvents\":100000,\"maxSecondsBetweenEvents\":200000",
                    eventJson("cool", "\"cooldownSeconds\":60")));
            plugin.resolutions.put("cool", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            TestResult r = assertNotNull("activation succeeds",
                    plugin.activate(plugin.getLibrary().getDefinition("cool"), weights(M1, 1.0f)));
            if (!r.passed()) return r;
            r = assertEquals("cooldown starts at activation (60 s)",
                    60_000L, plugin.getCooldownRemainingMs("cool"));
            if (!r.passed()) return r;

            // Play the run out (10 s ramp + 20 s hold + 10 s reversal = 40 s) — the
            // event retires but 15 s of cooldown remain.
            plugin.advanceTime(45_000, markets);
            r = assertEquals("event retired after its envelope", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertEquals("cooldown ticked down to 15 s",
                    15_000L, plugin.getCooldownRemainingMs("cool"));
            if (!r.passed()) return r;
            r = assertEquals("on cooldown: not eligible",
                    0, plugin.computeEligibleEvents(markets).size());
            if (!r.passed()) return r;

            r = assertTrue("resetCooldown reports a cleared cooldown",
                    plugin.resetCooldown("cool"));
            if (!r.passed()) return r;
            r = assertEquals("cooldown is gone", 0L, plugin.getCooldownRemainingMs("cool"));
            if (!r.passed()) return r;
            return assertEquals("event eligible again after the reset",
                    1, plugin.computeEligibleEvents(markets).size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Resetting an id without a running cooldown must be a reported no-op — for
     * loaded-but-ready definitions and for unknown ids alike ({@code resetCooldown}
     * returns false; the request layer additionally rejects unknown ids with an
     * error message before calling the seam).
     */
    private TestResult test_resetCooldown_unknownOrIdleIdIsNoOp() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("idle", "")));
            plugin.resolutions.put("idle", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            TestResult r = assertFalse("loaded id without cooldown: nothing to clear",
                    plugin.resetCooldown("idle"));
            if (!r.passed()) return r;
            r = assertFalse("unknown id: nothing to clear", plugin.resetCooldown("ghost"));
            if (!r.passed()) return r;
            return assertEquals("eligibility unaffected by the no-op resets",
                    1, plugin.computeEligibleEvents(markets).size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Guards the append-only wire contract of {@link NewsAdminRequest.Op} (ordinal-byte
     * codec): RESET_COOLDOWN(7), STOP_EVENT(8), SKIP_PHASE(9), REGISTRY_LIST(10) and
     * REGISTRY_CLEAR(11) must be appended at the enum end and every pre-existing op
     * must keep its ordinal — reordering would silently remap old ops on the wire.
     */
    private TestResult test_adminOp_wireOrdinalsAreAppended() {
        TestResult r = assertEquals("op count", 12, NewsAdminRequest.Op.values().length);
        if (!r.passed()) return r;
        NewsAdminRequest.Op[] expected = {
                NewsAdminRequest.Op.RELOAD, NewsAdminRequest.Op.TRIGGER,
                NewsAdminRequest.Op.LIST, NewsAdminRequest.Op.STOP,
                NewsAdminRequest.Op.INFO, NewsAdminRequest.Op.SET_ENABLED,
                NewsAdminRequest.Op.SET_SCHEDULER, NewsAdminRequest.Op.RESET_COOLDOWN,
                NewsAdminRequest.Op.STOP_EVENT, NewsAdminRequest.Op.SKIP_PHASE,
                NewsAdminRequest.Op.REGISTRY_LIST, NewsAdminRequest.Op.REGISTRY_CLEAR};
        for (int ordinal = 0; ordinal < expected.length; ordinal++) {
            r = assertEquals("ordinal of " + expected[ordinal].name(),
                    ordinal, expected[ordinal].ordinal());
            if (!r.passed()) return r;
        }
        return pass("all 12 op ordinals stable, REGISTRY_LIST/REGISTRY_CLEAR appended at 10/11");
    }

    // ========================================================================
    // T-093: per-event hard stop (STOP_EVENT) + skip phase (SKIP_PHASE)
    // ========================================================================

    /**
     * Hard-stopping an event in its <b>buildup (ramp-up)</b> phase via the STOP_EVENT
     * core must end it immediately and restart the <b>full</b> cooldown from the stop
     * moment — the event has to wait the entire cooldown again.
     */
    private TestResult test_stopEvent_inBuildupRestartsCooldown() {
        Path dir = null;
        try {
            dir = createTempDir();
            // trend: ramp 10s, hold 20s, ramp reversal 10s, peak +0.5, cooldown 60s
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("build", "\"cooldownSeconds\":60")));
            plugin.resolutions.put("build", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            TestResult r = assertNotNull("activation succeeds",
                    plugin.activate(plugin.getLibrary().getDefinition("build"), weights(M1, 1.0f)));
            if (!r.passed()) return r;
            plugin.advanceTime(5_000, markets); // mid-ramp; cooldown ticked to 55s
            r = assertEquals("cooldown ticked down before the stop",
                    55_000L, plugin.getCooldownRemainingMs("build"));
            if (!r.passed()) return r;

            NewsAdminRequest.AdminActionResult result =
                    NewsAdminRequest.performStopEvent(plugin, "build");
            r = assertTrue("stop reports success", result.success());
            if (!r.passed()) return r;
            r = assertTrue("stop reports a state change", result.changed());
            if (!r.passed()) return r;
            r = assertEquals("event ended immediately", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertEquals("full cooldown restarted from the stop moment",
                    60_000L, plugin.getCooldownRemainingMs("build"));
            if (!r.passed()) return r;
            return assertEquals("on (restarted) cooldown: not eligible",
                    0, plugin.computeEligibleEvents(markets).size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The T-093 core requirement: hard-stopping an event in its <b>recovery
     * (reversal)</b> phase — which the pre-T-093 graceful stop left running — must end
     * the event, remove its price influence immediately and restart the full cooldown.
     */
    private TestResult test_stopEvent_inRecoveryRemovesInfluence() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", eventJson("recov", "\"cooldownSeconds\":60")));
            plugin.resolutions.put("recov", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            TestResult r = assertNotNull("activation succeeds",
                    plugin.activate(plugin.getLibrary().getDefinition("recov"), weights(M1, 1.0f)));
            if (!r.passed()) return r;
            plugin.advanceTime(35_000, markets); // 10s ramp + 20s hold + 5s INTO the reversal
            ActiveNewsEvent event = plugin.findActiveEvent("recov");
            r = assertNotNull("event still active mid-recovery", event);
            if (!r.passed()) return r;
            r = assertEquals("event is in its recovery phase",
                    "REVERTING", event.phaseName());
            if (!r.passed()) return r;
            r = assertTrue("event still influences the market mid-recovery",
                    plugin.combinedFactorFor(M1, 1.0f, false) > 1.0 + 1e-6);
            if (!r.passed()) return r;

            NewsAdminRequest.AdminActionResult result =
                    NewsAdminRequest.performStopEvent(plugin, "recov");
            r = assertTrue("stop reports success + change", result.success() && result.changed());
            if (!r.passed()) return r;
            r = assertEquals("event ended immediately despite the recovery phase",
                    0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("influence removed immediately (factor "
                            + plugin.combinedFactorFor(M1, 1.0f, false) + ")",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < 1e-9);
            if (!r.passed()) return r;
            plugin.finalize(markets);
            r = assertTrue("finalize no longer touches the target price",
                    Math.abs(m1.targetPrice - 100.0) < 1e-9);
            if (!r.passed()) return r;
            return assertEquals("full cooldown restarted from the stop moment",
                    60_000L, plugin.getCooldownRemainingMs("recov"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Stop-all ({@code Op.STOP} with {@code "all"} funnels each active event through the
     * same {@link NewsPlugin#stopEvent} seam) must hard-stop events in the recovery
     * phase too: every event ends, all influence disappears, every cooldown restarts.
     */
    private TestResult test_stopAll_inRecoveryHardStopsEveryEvent() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("one", "\"cooldownSeconds\":60"),
                    eventJson("two", "\"cooldownSeconds\":90")));
            plugin.resolutions.put("one", weights(M1, 1.0f));
            plugin.resolutions.put("two", weights(M2, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            TestMarket m2 = new TestMarket(M2, 200);
            List<MarketInterface> markets = markets(m1, m2);

            TestResult r = assertNotNull("'one' activates",
                    plugin.activate(plugin.getLibrary().getDefinition("one"), weights(M1, 1.0f)));
            if (!r.passed()) return r;
            r = assertNotNull("'two' activates",
                    plugin.activate(plugin.getLibrary().getDefinition("two"), weights(M2, 1.0f)));
            if (!r.passed()) return r;
            plugin.advanceTime(35_000, markets); // both 5s into their reversal

            // Same loop as NewsAdminRequest.handleStop with the "all" target.
            for (ActiveNewsEvent event : plugin.getActiveEvents()) {
                NewsPlugin.StopOutcome outcome = plugin.stopEvent(event);
                r = assertEquals("stop-all outcome for '" + event.getDefinitionId() + "'",
                        NewsPlugin.StopOutcome.STOPPED, outcome);
                if (!r.passed()) return r;
            }
            r = assertEquals("no active events remain", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            r = assertTrue("no influence remains on either market",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.0) < 1e-9
                            && Math.abs(plugin.combinedFactorFor(M2, 1.0f, false) - 1.0) < 1e-9);
            if (!r.passed()) return r;
            r = assertEquals("'one' cooldown restarted in full",
                    60_000L, plugin.getCooldownRemainingMs("one"));
            if (!r.passed()) return r;
            return assertEquals("'two' cooldown restarted in full",
                    90_000L, plugin.getCooldownRemainingMs("two"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Skip-phase must walk an event through every phase boundary of its timeline —
     * pending announce delay → ramp-up → hold → recovery → ended — and ending it via the
     * last skip must use the <b>normal</b> end-of-event handling: the activation-time
     * cooldown keeps ticking unchanged (no restart, unlike a stop).
     */
    private TestResult test_skipPhase_walksAllPhasesToNormalEnd() {
        Path dir = null;
        try {
            dir = createTempDir();
            // trend ramp 10s / hold 20s / ramp reversal 10s, +5s announce delay, CD 60s
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("walk", "\"cooldownSeconds\":60,"
                            + "\"announceDelayMs\":{\"min\":5000,\"max\":5000}")));
            plugin.resolutions.put("walk", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            ActiveNewsEvent event =
                    plugin.activate(plugin.getLibrary().getDefinition("walk"), weights(M1, 1.0f));
            TestResult r = assertNotNull("activation succeeds", event);
            if (!r.passed()) return r;
            plugin.advanceTime(1_000, markets); // cooldown 59s, still 4s inside the delay
            r = assertTrue("event starts in the pending state", event.activeMillis() < 0);
            if (!r.passed()) return r;

            // PENDING → ramp-up start
            r = assertEquals("skip 1: pending → ramp-up",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_RAMP_UP, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("impact starts exactly now", 0L, event.activeMillis());
            if (!r.passed()) return r;

            // ramp-up → hold (factor jumps to the full peak: 1 + 0.5)
            r = assertEquals("skip 2: ramp-up → hold",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_HOLD, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("hold starts at the ramp end", 10_000L, event.activeMillis());
            if (!r.passed()) return r;
            r = assertTrue("holding at full peak influence",
                    Math.abs(plugin.combinedFactorFor(M1, 1.0f, false) - 1.5) < 1e-9);
            if (!r.passed()) return r;

            // hold → recovery
            r = assertEquals("skip 3: hold → recovery",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_REVERSAL, plugin.skipPhase(event));
            if (!r.passed()) return r;
            r = assertEquals("recovery starts at the hold end", 30_000L, event.activeMillis());
            if (!r.passed()) return r;

            // recovery → ended (normal retirement on the follow-up advance pass)
            r = assertEquals("skip 4: recovery → ended",
                    NewsPlugin.SkipOutcome.SKIPPED_TO_END, plugin.skipPhase(event));
            if (!r.passed()) return r;
            plugin.advanceTime(0, markets); // realize the retirement
            r = assertEquals("event retired normally", 0, plugin.getActiveEvents().size());
            if (!r.passed()) return r;
            return assertEquals("normal end: activation cooldown keeps ticking, no restart",
                    59_000L, plugin.getCooldownRemainingMs("walk"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Skipping the hold of a {@code reversal:none} event means fast-forwarding to its
     * natural completion: the <b>full permanent shift bakes</b> into the default price
     * (unlike a stop, which cancels without baking). Driven through the SKIP_PHASE
     * request core, which realizes the bake immediately.
     */
    private TestResult test_skipPhase_permanentHoldBakesLikeCompletion() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("",
                    eventJson("perma", "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,"
                            + "\"rampUpSeconds\":0,\"durationSeconds\":60,\"reversal\":\"none\"}")));
            plugin.resolutions.put("perma", weights(M1, 1.0f));
            TestMarket m1 = new TestMarket(M1, 100);
            List<MarketInterface> markets = markets(m1);

            TestResult r = assertNotNull("activation succeeds",
                    plugin.activate(plugin.getLibrary().getDefinition("perma"), weights(M1, 1.0f)));
            if (!r.passed()) return r;
            plugin.advanceTime(2_000, markets); // mid-hold, far before the 60s hold end

            NewsAdminRequest.AdminActionResult result =
                    NewsAdminRequest.performSkipPhase(plugin, "perma", markets);
            r = assertTrue("skip reports success + change", result.success() && result.changed());
            if (!r.passed()) return r;
            r = assertTrue("full permanent shift baked like a natural completion (got "
                    + m1.defaultPrice + ")", Math.abs(m1.defaultPrice - 150.0) < 1e-6);
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
     * STOP_EVENT/SKIP_PHASE on a target that is not running must be a <b>clean status</b>:
     * a loaded-but-idle id reports a friendly no-op (success, nothing changed), an
     * unknown id reports the standard unknown-id error — and neither touches any state.
     */
    private TestResult test_stopOrSkip_nonActiveIsCleanStatus() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("idle", "")));
            plugin.resolutions.put("idle", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));

            NewsAdminRequest.AdminActionResult stop =
                    NewsAdminRequest.performStopEvent(plugin, "idle");
            TestResult r = assertTrue("stop on idle id: clean no-op status",
                    stop.success() && !stop.changed());
            if (!r.passed()) return r;
            r = assertTrue("stop no-op names the problem",
                    stop.message().contains("not active"));
            if (!r.passed()) return r;

            NewsAdminRequest.AdminActionResult skip =
                    NewsAdminRequest.performSkipPhase(plugin, "idle", markets);
            r = assertTrue("skip on idle id: clean no-op status",
                    skip.success() && !skip.changed());
            if (!r.passed()) return r;
            r = assertTrue("skip no-op names the problem",
                    skip.message().contains("not active"));
            if (!r.passed()) return r;

            r = assertFalse("stop on unknown id: error status",
                    NewsAdminRequest.performStopEvent(plugin, "ghost").success());
            if (!r.passed()) return r;
            r = assertFalse("skip on unknown id: error status",
                    NewsAdminRequest.performSkipPhase(plugin, "ghost", markets).success());
            if (!r.passed()) return r;
            return assertEquals("eligibility untouched by all no-ops",
                    1, plugin.computeEligibleEvents(markets).size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The hand-written {@link EventDetails#CODEC} must round-trip every field exactly,
     * including multi-entry translation maps (order preserved), negative values, the
     * market list and the T-099 appends (sequence block with per-step markets,
     * requirement statuses, chain lines). Guards the no-version-byte lockstep contract:
     * encode and decode must always agree field for field.
     */
    private TestResult test_eventDetails_codecRoundTrip() {
        Map<String, String> headline = new LinkedHashMap<>();
        headline.put("de_de", "Schlagzeile"); // deliberately NOT en_us-first
        headline.put("en_us", "Headline");
        Map<String, String> text = new LinkedHashMap<>();
        text.put("en_us", "Body text");

        List<EventDetails.MarketImpact> markets = new ArrayList<>();
        markets.add(new EventDetails.MarketImpact((short) 7, "minecraft:diamond", 1.0f));
        markets.add(new EventDetails.MarketImpact((short) 9, "minecraft:gold_ingot", -0.25f));

        // T-099 appends: a two-step sequence (one step with its own market subset,
        // one inheriting = empty list), one met + one unmet requirement, one chain.
        List<EventDetails.SequenceInfo> sequences = List.of(new EventDetails.SequenceInfo(
                "crash_path", 2.0f, List.of(
                        new EventDetails.StepInfo("spike", 10_000L, 30_000L, 0.4,
                                "instant", false, List.of(new EventDetails.MarketImpact(
                                        (short) 7, "minecraft:diamond", 1.0f))),
                        new EventDetails.StepInfo("fade", 5_000L, 5_000L, -0.6,
                                "exponential", true, List.of()))));
        List<EventDetails.RequirementStatus> requirements = List.of(
                new EventDetails.RequirementStatus("never fired: round_trip", true),
                new EventDetails.RequirementStatus("registry key 'era' is 'gold'", false));
        List<String> chains = List.of("on step 'fade' -> follow_up (chance 0.5)");

        EventDetails original = new EventDetails("round_trip", false, true, true,
                12_345L, 2.5f, headline, text, -5_000L, 5_000L,
                -0.4, 10L, 20L, "none", 0L, markets, "", null,
                sequences, requirements, chains);

        ByteBuf buf = Unpooled.buffer();
        try {
            EventDetails.CODEC.encode(buf, original);
            EventDetails decoded = EventDetails.CODEC.decode(buf);

            TestResult r = assertEquals("record equality after the round-trip", original, decoded);
            if (!r.passed()) return r;
            r = assertEquals("no bytes left unread", 0, buf.readableBytes());
            if (!r.passed()) return r;
            // LinkedHashMap.equals ignores order — verify insertion order explicitly,
            // the NewsTranslations first-entry fallback depends on it.
            return assertEquals("headline insertion order survives the wire",
                    "de_de", decoded.headline().keySet().iterator().next());
        } finally {
            buf.release();
        }
    }

    // ========================================================================
    // T-099: registry admin ops (REGISTRY_LIST / REGISTRY_CLEAR static cores)
    // ========================================================================

    /**
     * {@code performRegistryList} must render both registry sections with their entry
     * counts: fire records (count, first/last timestamps, relative age, game day) and
     * custom key/value pairs — and an empty registry must still render both headers
     * (count 0) instead of nothing.
     */
    private TestResult test_registryList_rendersFiresAndValues() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        // "boom" fired twice (1 000 000 and 4 600 000 epoch ms), "once" fired once.
        registry.recordFire("boom", 1_000_000L, 12L);
        registry.recordFire("boom", 4_600_000L, 42L);
        registry.recordFire("once", 4_600_000L, 42L);
        registry.putValue("era", "gold_standard");

        // now = one hour after the last fire → the relative age must render "1h".
        List<String> lines = NewsAdminRequest.performRegistryList(registry,
                4_600_000L + 3_600_000L);
        String joined = String.join("\n", lines);

        TestResult r = assertTrue("fire section header with count",
                joined.contains("Fire records (2):"));
        if (!r.passed()) return r;
        r = assertTrue("fire count rendered", joined.contains("boom: fired 2 times"));
        if (!r.passed()) return r;
        r = assertTrue("first-fire timestamp rendered for multi-fire events (UTC)",
                joined.contains("first 1970-01-01 00:16:40 UTC"));
        if (!r.passed()) return r;
        r = assertTrue("last-fire timestamp rendered",
                joined.contains("last 1970-01-01 01:16:40 UTC"));
        if (!r.passed()) return r;
        r = assertTrue("relative age rendered", joined.contains("(1h ago)"));
        if (!r.passed()) return r;
        r = assertTrue("game day of the last fire rendered", joined.contains("game day 42"));
        if (!r.passed()) return r;
        r = assertTrue("single-fire events skip the redundant first timestamp",
                joined.contains("once: fired 1 time, last"));
        if (!r.passed()) return r;
        r = assertTrue("custom section header with count", joined.contains("Custom values (1):"));
        if (!r.passed()) return r;
        r = assertTrue("custom pair rendered", joined.contains("- era = gold_standard"));
        if (!r.passed()) return r;

        // Empty registry: both headers must still appear (count 0).
        List<String> empty = NewsAdminRequest.performRegistryList(new NewsWorldRegistry(), 0L);
        r = assertEquals("empty registry renders exactly the two headers", 2, empty.size());
        if (!r.passed()) return r;
        return assertTrue("empty headers carry count 0",
                empty.get(0).equals("Fire records (0):") && empty.get(1).equals("Custom values (0):"));
    }

    /**
     * {@code performRegistryClear} — all three variants ({@code all} / one event id /
     * one custom key) plus the no-op and error cases: absent targets are clean no-op
     * statuses ({@code changed() == false} — the handlers then skip the admin audit),
     * an empty target is an error, and the success messages (= the audited text) name
     * exactly what was cleared.
     */
    private TestResult test_registryClear_variantsAndNoOps() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("boom", 1_000L, 1L);
        registry.recordFire("boom", 2_000L, 2L);
        registry.recordFire("other", 3_000L, 3L);
        registry.putValue("era", "gold");

        // Variant 1: clear one event's fire record.
        NewsAdminRequest.AdminActionResult result =
                NewsAdminRequest.performRegistryClear(registry, "boom", false);
        TestResult r = assertTrue("event clear succeeds + changed",
                result.success() && result.changed());
        if (!r.passed()) return r;
        r = assertTrue("event clear message names the event and its fire count",
                result.message().contains("'boom'") && result.message().contains("2 fire(s)"));
        if (!r.passed()) return r;
        r = assertFalse("'boom' counts as never fired again", registry.hasFired("boom"));
        if (!r.passed()) return r;
        r = assertTrue("'other' untouched", registry.hasFired("other"));
        if (!r.passed()) return r;

        // No-op: the same event again (no record anymore) — clean status, not changed.
        result = NewsAdminRequest.performRegistryClear(registry, "boom", false);
        r = assertTrue("repeat event clear is a clean no-op",
                result.success() && !result.changed());
        if (!r.passed()) return r;
        r = assertTrue("no-op names the problem", result.message().contains("nothing to clear"));
        if (!r.passed()) return r;

        // Variant 2: clear one custom key.
        result = NewsAdminRequest.performRegistryClear(registry, "era", true);
        r = assertTrue("key clear succeeds + changed", result.success() && result.changed());
        if (!r.passed()) return r;
        r = assertTrue("key clear message names key and previous value",
                result.message().contains("'era'") && result.message().contains("'gold'"));
        if (!r.passed()) return r;
        r = assertNull("key is gone", registry.getValue("era"));
        if (!r.passed()) return r;
        result = NewsAdminRequest.performRegistryClear(registry, "era", true);
        r = assertTrue("absent key clear is a clean no-op",
                result.success() && !result.changed());
        if (!r.passed()) return r;

        // Variant 3: clear everything.
        registry.putValue("era", "fiat");
        result = NewsAdminRequest.performRegistryClear(registry,
                NewsAdminRequest.REGISTRY_CLEAR_ALL, false);
        r = assertTrue("clear-all succeeds + changed", result.success() && result.changed());
        if (!r.passed()) return r;
        r = assertTrue("clear-all message carries both counts",
                result.message().contains("1 fire record(s)")
                        && result.message().contains("1 custom key(s)"));
        if (!r.passed()) return r;
        r = assertTrue("registry is empty afterwards", registry.isEmpty());
        if (!r.passed()) return r;
        result = NewsAdminRequest.performRegistryClear(registry,
                NewsAdminRequest.REGISTRY_CLEAR_ALL, false);
        r = assertTrue("clear-all on an empty registry is a clean no-op",
                result.success() && !result.changed());
        if (!r.passed()) return r;

        // Error: an empty target is a hard input error.
        result = NewsAdminRequest.performRegistryClear(registry, "  ", false);
        return assertFalse("blank target is an error", result.success());
    }

    // ========================================================================
    // T-099: INFO/EventDetails for sequence events + legacy parity + requirements
    // ========================================================================

    /**
     * The T-099 NPE fix: {@code getImpact()} is null for {@code sequences[]}-authored
     * events (T-094) — {@code renderEventInfo} and {@code buildEventDetails} previously
     * dereferenced it unconditionally and crashed. Both must now render sequence events
     * through {@code getSequences()}: INFO shows the sequence/step breakdown, the
     * details snapshot carries the structured sequence block plus safe legacy-field
     * analogues (peak = largest-magnitude step target, reversal = "sequence").
     */
    private TestResult test_infoAndDetails_renderSequenceEvent() {
        Path dir = null;
        try {
            dir = createTempDir();
            String sequence = "{\"name\":\"crash_path\",\"weight\":2,\"steps\":["
                    + "{\"name\":\"spike\",\"durationSeconds\":10,\"targetFactor\":0.4,\"curve\":\"instant\"},"
                    + "{\"name\":\"fade\",\"durationSeconds\":{\"min\":20,\"max\":40},\"targetFactor\":-0.6,\"curve\":\"linear\"},"
                    + "{\"name\":\"norm\",\"durationSeconds\":5,\"targetFactor\":0.0,\"curve\":\"exponential\"}]}";
            TestNewsPlugin plugin = pluginWithFile(dir,
                    fileJson("", sequenceEventJson("seq", "", sequence)));
            plugin.resolutions.put("seq", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));
            NewsEventDefinition def = plugin.getLibrary().getDefinition("seq");
            TestResult r = assertNotNull("sequence event loaded", def);
            if (!r.passed()) return r;
            r = assertNull("sequence event has no legacy envelope", def.getImpact());
            if (!r.passed()) return r;

            // renderEventInfo must not throw (the pre-fix NPE) and must show the steps.
            String joined = String.join("\n",
                    NewsAdminRequest.renderEventInfo(plugin, def, markets));
            r = assertTrue("sequence block header rendered", joined.contains("Sequences (1"));
            if (!r.passed()) return r;
            r = assertTrue("sequence name + weight rendered",
                    joined.contains("'crash_path' weight 2.0, 3 step(s)"));
            if (!r.passed()) return r;
            r = assertTrue("fixed step duration rendered", joined.contains("'spike' 10 s"));
            if (!r.passed()) return r;
            r = assertTrue("ranged step duration rendered", joined.contains("'fade' 20..40 s"));
            if (!r.passed()) return r;
            r = assertTrue("curve rendered", joined.contains("curve exponential"));
            if (!r.passed()) return r;
            r = assertTrue("no legacy Impact line for sequence events",
                    !joined.contains("  Impact:"));
            if (!r.passed()) return r;
            // Peak analogue: largest-magnitude signed target (-0.6) drives the
            // per-market peak percentage: 1 + (-0.6) * 1.0 = 0.4 → "-60.0%".
            r = assertTrue("peak influence derived from the step targets",
                    joined.contains("(peak -60.0%)"));
            if (!r.passed()) return r;

            // buildEventDetails must not throw either and must carry the block.
            List<EventDetails> details = NewsAdminRequest.buildEventDetails(plugin, markets);
            EventDetails d = details.stream().filter(e -> e.id().equals("seq")).findFirst().orElse(null);
            r = assertNotNull("'seq' details entry exists", d);
            if (!r.passed()) return r;
            r = assertEquals("one sequence shipped", 1, d.sequences().size());
            if (!r.passed()) return r;
            EventDetails.SequenceInfo info = d.sequences().get(0);
            r = assertEquals("sequence name", "crash_path", info.name());
            if (!r.passed()) return r;
            r = assertEquals("step count", 3, info.steps().size());
            if (!r.passed()) return r;
            EventDetails.StepInfo fade = info.steps().get(1);
            r = assertEquals("ranged duration min", 20_000L, fade.durationMinMs());
            if (!r.passed()) return r;
            r = assertEquals("ranged duration max", 40_000L, fade.durationMaxMs());
            if (!r.passed()) return r;
            r = assertTrue("step target factor", Math.abs(fade.targetFactor() + 0.6) < 1e-9);
            if (!r.passed()) return r;
            r = assertEquals("step curve name", "linear", fade.curve());
            if (!r.passed()) return r;
            r = assertTrue("no own step markets = inherits event markets",
                    fade.markets().isEmpty());
            if (!r.passed()) return r;
            r = assertTrue("legacy peak analogue = largest-magnitude target",
                    Math.abs(d.peakFactor() + 0.6) < 1e-9);
            if (!r.passed()) return r;
            return assertEquals("legacy reversal analogue", "sequence", d.reversal());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Legacy parity guard (T-099 acceptance): for an {@code impact}-authored event
     * without requires/chains the INFO rendering must stay byte-identical to the
     * pre-T-099 output — the classic Impact line, and NO sequence/requires/chains
     * blocks. The details snapshot keeps the exact envelope descriptor values but
     * additionally exposes the implicit normalized "impact" sequence (wire-only
     * append — it does not affect the rendered lines).
     */
    private TestResult test_infoLegacyRenderingUnchanged() {
        Path dir = null;
        try {
            dir = createTempDir();
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("legacy", "")));
            plugin.resolutions.put("legacy", weights(M1, 0.75f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));
            NewsEventDefinition def = plugin.getLibrary().getDefinition("legacy");

            List<String> lines = NewsAdminRequest.renderEventInfo(plugin, def, markets);
            String joined = String.join("\n", lines);
            // The exact pre-T-099 Impact line (trend, +0.5 peak, 10/20/10 s ramp reversal).
            TestResult r = assertTrue("classic Impact line rendered verbatim",
                    lines.contains("  Impact: trend, peak +50.0% (peakFactor 0.5), rampUp 10 s,"
                            + " hold 20 s, reversal ramp (10 s)"));
            if (!r.passed()) return r;
            r = assertTrue("no sequence block for legacy events",
                    !joined.contains("Sequences ("));
            if (!r.passed()) return r;
            r = assertTrue("no requires block without authored requirements",
                    !joined.contains("Requires ("));
            if (!r.passed()) return r;
            r = assertTrue("no chains block without authored chains",
                    !joined.contains("Chains ("));
            if (!r.passed()) return r;

            List<EventDetails> details = NewsAdminRequest.buildEventDetails(plugin, markets);
            EventDetails d = details.stream().filter(e -> e.id().equals("legacy")).findFirst().orElse(null);
            r = assertNotNull("'legacy' details entry exists", d);
            if (!r.passed()) return r;
            r = assertTrue("envelope peak factor kept", Math.abs(d.peakFactor() - 0.5) < 1e-9);
            if (!r.passed()) return r;
            r = assertEquals("envelope reversal kept", "ramp", d.reversal());
            if (!r.passed()) return r;
            r = assertEquals("implicit normalized sequence shipped", 1, d.sequences().size());
            if (!r.passed()) return r;
            r = assertEquals("implicit sequence is named 'impact'",
                    "impact", d.sequences().get(0).name());
            if (!r.passed()) return r;
            return assertEquals("implicit sequence has the 3 normalization steps",
                    3, d.sequences().get(0).steps().size());
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The per-requirement met/unmet status of {@link EventDetails#requirements()} must
     * be evaluated against the LIVE world registry at snapshot time (plan §10.1 — the
     * T-100 trigger-confirm popup renders the unmet entries): seeding/mutating the
     * registry between two snapshots must flip the flags accordingly. INFO renders the
     * same status inline ({@code [met]}/{@code [UNMET]}).
     */
    private TestResult test_eventDetails_requirementStatusTracksRegistry() {
        Path dir = null;
        try {
            dir = createTempDir();
            String requires = "\"requires\":["
                    + "{\"type\":\"notFired\",\"eventId\":\"gated\"},"
                    + "{\"type\":\"keyEquals\",\"key\":\"era\",\"value\":\"gold\"}]";
            TestNewsPlugin plugin = pluginWithFile(dir, fileJson("", eventJson("gated", requires)));
            plugin.resolutions.put("gated", weights(M1, 1.0f));
            List<MarketInterface> markets = markets(new TestMarket(M1, 100));
            NewsWorldRegistry registry = new NewsWorldRegistry();
            plugin.setRegistrySupplier(() -> registry);

            // Snapshot 1: never fired (met), key absent (unmet).
            EventDetails d = NewsAdminRequest.buildEventDetails(plugin, markets).get(0);
            TestResult r = assertEquals("both requirements shipped", 2, d.requirements().size());
            if (!r.passed()) return r;
            r = assertTrue("notFired met while never fired", d.requirements().get(0).met());
            if (!r.passed()) return r;
            r = assertFalse("keyEquals unmet while the key is absent",
                    d.requirements().get(1).met());
            if (!r.passed()) return r;
            r = assertTrue("description carries the describe() text",
                    d.requirements().get(0).description().contains("never fired: gated"));
            if (!r.passed()) return r;

            // Mutate the registry: fire the event, write the key → flags must flip.
            registry.recordFire("gated", 1_000L, 1L);
            registry.putValue("era", "gold");
            d = NewsAdminRequest.buildEventDetails(plugin, markets).get(0);
            r = assertFalse("notFired unmet after the fire was recorded",
                    d.requirements().get(0).met());
            if (!r.passed()) return r;
            r = assertTrue("keyEquals met after the key was written",
                    d.requirements().get(1).met());
            if (!r.passed()) return r;

            // INFO renders the same status inline.
            String joined = String.join("\n", NewsAdminRequest.renderEventInfo(plugin,
                    plugin.getLibrary().getDefinition("gated"), markets));
            r = assertTrue("requires header rendered", joined.contains("Requires (2"));
            if (!r.passed()) return r;
            r = assertTrue("unmet requirement flagged",
                    joined.contains("[UNMET] never fired: gated"));
            if (!r.passed()) return r;
            return assertTrue("met requirement flagged",
                    joined.contains("[met] registry key 'era' is 'gold'"));
        } catch (IOException e) {
            return fail("temp dir failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // T-099: wire appends (NewsRecord sequence fields, ActiveEventInfo step fields)
    // ========================================================================

    /**
     * The T-099 {@link NewsRecord} appends ({@code sequenceName}/{@code stepCount})
     * must survive an NBT round-trip, load as safe defaults from pre-T-099 tags
     * (contains() guard) and round-trip through the unconditionally-appended stream
     * codec — for populated and legacy-default records alike.
     */
    private TestResult test_newsRecord_sequenceFieldsRoundTrip() {
        NewsRecord sequenced = new NewsRecord(7L, "seq_event", 123L, 42L,
                Map.of("en_us", "H"), Map.of("en_us", "T"), List.of(),
                "sequence", -0.6f, "sequence", 75, "crash_path", 3);
        NewsRecord legacy = new NewsRecord(8L, "old_event", 456L, 43L,
                Map.of("en_us", "H"), Map.of("en_us", "T"), List.of(),
                "trend", 0.5f, "ramp", 40); // pre-T-099 constructor → "" / 0

        // NBT round-trip keeps the descriptor.
        CompoundTag tag = new CompoundTag();
        sequenced.save(tag);
        NewsRecord loaded = NewsRecord.createFromTag(tag);
        TestResult r = assertNotNull("sequenced record loads", loaded);
        if (!r.passed()) return r;
        r = assertEquals("NBT round-trip equality (incl. sequence fields)", sequenced, loaded);
        if (!r.passed()) return r;
        r = assertEquals("sequence name survives NBT", "crash_path", loaded.getSequenceName());
        if (!r.passed()) return r;
        r = assertEquals("step count survives NBT", 3, loaded.getStepCount());
        if (!r.passed()) return r;

        // Legacy records write NO sequence tags (byte-identical saves) and pre-T-099
        // tags load as the safe defaults.
        CompoundTag legacyTag = new CompoundTag();
        legacy.save(legacyTag);
        r = assertFalse("legacy save writes no sequenceName tag",
                legacyTag.contains("sequenceName"));
        if (!r.passed()) return r;
        r = assertFalse("legacy save writes no stepCount tag (count 0 = unknown)",
                legacyTag.contains("stepCount"));
        if (!r.passed()) return r;
        NewsRecord legacyLoaded = NewsRecord.createFromTag(legacyTag);
        r = assertNotNull("legacy record loads", legacyLoaded);
        if (!r.passed()) return r;
        r = assertEquals("absent tags load as empty name", "", legacyLoaded.getSequenceName());
        if (!r.passed()) return r;
        r = assertEquals("absent tags load as step count 0", 0, legacyLoaded.getStepCount());
        if (!r.passed()) return r;

        // Stream codec round-trip (unconditional append) — needs a registry context.
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("stream-codec round-trip skipped (no registry access in this context)");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            NewsRecord.STREAM_CODEC.encode(buf, sequenced);
            NewsRecord.STREAM_CODEC.encode(buf, legacy);
            NewsRecord decodedSequenced = NewsRecord.STREAM_CODEC.decode(buf);
            NewsRecord decodedLegacy = NewsRecord.STREAM_CODEC.decode(buf);
            r = assertEquals("codec round-trip (sequenced)", sequenced, decodedSequenced);
            if (!r.passed()) return r;
            r = assertEquals("codec round-trip (legacy defaults)", legacy, decodedLegacy);
            if (!r.passed()) return r;
            return assertEquals("no bytes left unread (back-to-back records)",
                    0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    /**
     * The T-099 {@link RuntimeStreamData.ActiveEventInfo} appends (stepName/stepIndex/
     * stepCount/stepRemainingMs incl. the -1 PENDING/terminal sentinel) must round-trip
     * through the unconditionally-appended stream codec (same lockstep guard as the
     * T-082 round-trip in {@code NewsSchedulerTestSuite}).
     */
    private TestResult test_activeEventInfo_stepFieldsRoundTrip() {
        List<RuntimeStreamData.ActiveEventInfo> events = new ArrayList<>();
        events.add(new RuntimeStreamData.ActiveEventInfo("running", "Headline", "fade",
                30_000L, true,
                List.of(new RuntimeStreamData.MarketFactor((short) 7, 1.25f)),
                null, "fade", 1, 3, 12_345L));
        // PENDING/terminal sentinel: stepRemainingMs = -1.
        events.add(new RuntimeStreamData.ActiveEventInfo("pending", "Headline 2", "PENDING",
                60_000L, false, List.of(), null, "spike", 0, 3, -1L));
        RuntimeStreamData original = new RuntimeStreamData(events, new ArrayList<>(),
                RuntimeStreamData.SchedulerState.createDefault());

        ByteBuf buf = Unpooled.buffer();
        try {
            RuntimeStreamData.CODEC.encode(buf, original);
            RuntimeStreamData decoded = RuntimeStreamData.CODEC.decode(buf);
            TestResult r = assertEquals("event count survives", 2, decoded.events().size());
            if (!r.passed()) return r;
            RuntimeStreamData.ActiveEventInfo running = decoded.events().get(0);
            r = assertEquals("step name survives", "fade", running.stepName());
            if (!r.passed()) return r;
            r = assertEquals("step index survives", 1, running.stepIndex());
            if (!r.passed()) return r;
            r = assertEquals("step count survives", 3, running.stepCount());
            if (!r.passed()) return r;
            r = assertEquals("step remaining ms survives", 12_345L, running.stepRemainingMs());
            if (!r.passed()) return r;
            r = assertEquals("PENDING sentinel survives",
                    -1L, decoded.events().get(1).stepRemainingMs());
            if (!r.passed()) return r;
            return assertEquals("no bytes left unread", 0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }
}
