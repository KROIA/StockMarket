package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.DefaultNewsEvents;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsEventLibrary;
import net.kroia.stockmarket.news.NewsImpactEnvelope;
import net.kroia.stockmarket.news.NewsImpactEnvelope.ImpactType;
import net.kroia.stockmarket.news.NewsImpactEnvelope.Phase;
import net.kroia.stockmarket.news.NewsImpactEnvelope.ReversalMode;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.news.NewsSequence;
import net.kroia.stockmarket.news.NewsSequence.Curve;
import net.kroia.stockmarket.news.NewsTranslations;
import net.kroia.stockmarket.news.NewsUiFormatting;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tests for the news event system data model (T-070):
 * {@link NewsImpactEnvelope} math (all three reversal modes + phases),
 * {@link NewsEventDefinition} parsing (string vs. map headline, announce delay,
 * matchers, validation report contents), {@link NewsEventLibrary} drop-in
 * loading/merging (duplicate ids, malformed files, defaults generation,
 * scheduler last-wins) and {@link NewsRecord} NBT + stream codec round-trips.
 */
public class NewsSystemTestSuite extends TestSuite {

    private static final double EPSILON = 1e-9;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS;
    }

    @Override
    public void registerTests() {
        // Envelope math
        addTest("envelope_negative_time_is_zero", this::test_envelope_negativeTime_isZero);
        addTest("envelope_ramp_is_linear", this::test_envelope_ramp_isLinear);
        addTest("envelope_zero_ramp_is_instant", this::test_envelope_zeroRamp_isInstant);
        addTest("envelope_hold_is_full", this::test_envelope_hold_isFull);
        addTest("envelope_ramp_reversal_linear_to_zero", this::test_envelope_rampReversal_linearToZero);
        addTest("envelope_exponential_reversal_decay", this::test_envelope_exponentialReversal_decay);
        addTest("envelope_none_reversal_is_permanent", this::test_envelope_noneReversal_isPermanent);
        addTest("envelope_phase_walk", this::test_envelope_phaseWalk);
        addTest("envelope_total_length_per_mode", this::test_envelope_totalLengthPerMode);
        addTest("envelope_sanitizes_invalid_inputs", this::test_envelope_sanitizesInvalidInputs);
        addTest("envelope_type_presets_provide_defaults", this::test_envelope_typePresets_provideDefaults);

        // Definition parsing
        addTest("parse_plain_string_headline_normalizes_to_map", this::test_parse_plainString_normalizesToMap);
        addTest("parse_translation_map_headline", this::test_parse_translationMap);
        addTest("parse_optional_fields_use_defaults", this::test_parse_optionalFields_useDefaults);
        addTest("parse_announce_delay_range", this::test_parse_announceDelayRange);
        addTest("parse_broken_definition_reports_all_errors", this::test_parse_brokenDefinition_reportsAllErrors);
        addTest("parse_unknown_field_is_warning", this::test_parse_unknownField_isWarning);
        addTest("parse_negative_delay_outliving_impact_warns", this::test_parse_negativeDelayOutlivingImpact_warns);

        // Matchers
        addTest("matcher_exact_match", this::test_matcher_exact);
        addTest("matcher_exact_normalizes_namespace", this::test_matcher_exact_normalizesNamespace);
        addTest("matcher_glob_match", this::test_matcher_glob);
        addTest("matcher_glob_without_namespace", this::test_matcher_glob_withoutNamespace);
        addTest("matcher_tag_match", this::test_matcher_tag);
        addTest("matcher_negative_weight_factor", this::test_matcher_negativeWeightFactor);

        // Library
        addTest("library_generates_defaults_on_empty_folder", this::test_library_generatesDefaults);
        addTest("library_default_events_parse_clean", this::test_library_defaultEventsParseClean);
        addTest("library_drop_in_merges_files", this::test_library_dropIn_mergesFiles);
        addTest("library_duplicate_id_error_later_wins", this::test_library_duplicateId_laterWins);
        addTest("library_malformed_file_never_throws", this::test_library_malformedFile_neverThrows);
        addTest("library_failed_reload_keeps_previous", this::test_library_failedReload_keepsPrevious);
        addTest("library_scheduler_last_loaded_wins", this::test_library_scheduler_lastLoadedWins);
        addTest("library_heals_missing_default_events", this::test_library_healsMissingDefaultEvents);

        // NewsRecord round-trips
        addTest("record_nbt_round_trip", this::test_record_nbtRoundTrip);
        addTest("record_nbt_preserves_translation_order", this::test_record_nbtPreservesTranslationOrder);
        addTest("record_stream_codec_round_trip", this::test_record_streamCodecRoundTrip);

        // Client-side translation resolution (T-074, NewsTranslations fallback chain)
        addTest("translations_exact_language_wins", this::test_translations_exactLanguageWins);
        addTest("translations_fall_back_to_en_us", this::test_translations_fallBackToEnUs);
        addTest("translations_fall_back_to_first_entry", this::test_translations_fallBackToFirstEntry);
        addTest("translations_null_language_skips_exact_step", this::test_translations_nullLanguage_skipsExactStep);
        addTest("translations_empty_map_yields_empty_string", this::test_translations_emptyMap_yieldsEmptyString);

        // Admin-GUI formatting helpers (T-075, NewsUiFormatting)
        addTest("format_remaining_time_mm_ss", this::test_formatRemainingTime_mmSs);
        addTest("format_remaining_time_h_mm_ss", this::test_formatRemainingTime_hMmSs);
        addTest("format_remaining_time_clamps_negative", this::test_formatRemainingTime_clampsNegative);
        addTest("format_factor_percent_signed", this::test_formatFactorPercent_signed);
        addTest("format_factor_percent_neutral_is_positive_zero", this::test_formatFactorPercent_neutralIsPositiveZero);
        addTest("format_factor_percent_non_finite", this::test_formatFactorPercent_nonFinite);

        // NewsSequence math (T-094)
        addTest("sequence_negative_age_is_zero", this::test_sequence_negativeAge_isZero);
        addTest("sequence_linear_curve_interpolates", this::test_sequence_linearCurve_interpolates);
        addTest("sequence_instant_curve_jumps_at_step_start", this::test_sequence_instantCurve_jumpsAtStepStart);
        addTest("sequence_hold_curve_keeps_start_value", this::test_sequence_holdCurve_keepsStartValue);
        addTest("sequence_exponential_curve_approaches_target", this::test_sequence_exponentialCurve_approachesTarget);
        addTest("sequence_end_without_permanent_snaps_to_zero", this::test_sequence_endWithoutPermanent_snapsToZero);
        addTest("sequence_end_with_permanent_keeps_final_value", this::test_sequence_endWithPermanent_keepsFinalValue);
        addTest("sequence_step_index_and_start_lookup", this::test_sequence_stepIndexAndStartLookup);
        addTest("sequence_start_values_chain", this::test_sequence_startValuesChain);
        addTest("sequence_sanitizes_invalid_inputs", this::test_sequence_sanitizesInvalidInputs);

        // Legacy-envelope normalization (T-094, equivalence contract)
        addTest("normalization_equivalence_ramp_reversal", this::test_normalization_equivalence_rampReversal);
        addTest("normalization_equivalence_exponential_reversal", this::test_normalization_equivalence_exponentialReversal);
        addTest("normalization_equivalence_none_reversal", this::test_normalization_equivalence_noneReversal);
        addTest("normalization_structure_matches_plan", this::test_normalization_structureMatchesPlan);

        // sequences[] parsing (T-094, plan §1.1/§7)
        addTest("parse_sequences_valid_multi_sequence", this::test_parse_sequences_validMultiSequence);
        addTest("parse_sequences_known_keys_no_warnings", this::test_parse_sequences_knownKeysNoWarnings);
        addTest("parse_impact_and_sequences_mutually_exclusive", this::test_parse_impactAndSequences_mutuallyExclusive);
        addTest("parse_neither_impact_nor_sequences_is_error", this::test_parse_neitherImpactNorSequences_isError);
        addTest("parse_sequences_empty_is_error", this::test_parse_sequencesEmpty_isError);
        addTest("parse_sequence_without_steps_is_error", this::test_parse_sequenceWithoutSteps_isError);
        addTest("parse_step_missing_name_is_error", this::test_parse_stepMissingName_isError);
        addTest("parse_duplicate_step_names_is_error", this::test_parse_duplicateStepNames_isError);
        addTest("parse_step_duration_errors", this::test_parse_stepDurationErrors);
        addTest("parse_step_target_factor_errors", this::test_parse_stepTargetFactorErrors);
        addTest("parse_step_unknown_curve_is_error", this::test_parse_stepUnknownCurve_isError);
        addTest("parse_permanent_on_non_last_step_is_error", this::test_parse_permanentOnNonLastStep_isError);
        addTest("parse_sequence_weight_invalid_is_error", this::test_parse_sequenceWeightInvalid_isError);
        addTest("parse_final_nonzero_without_permanent_warns", this::test_parse_finalNonzeroWithoutPermanent_warns);
        addTest("parse_hold_with_target_factor_warns", this::test_parse_holdWithTargetFactor_warns);
        addTest("parse_step_markets_parsed_and_inherit", this::test_parse_stepMarkets_parsedAndInherit);
        addTest("parse_legacy_impact_normalizes_to_implicit_sequence", this::test_parse_legacyImpact_normalizesToImplicitSequence);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Envelope: ramp 10s, hold 20s, given reversal over 10s, peak +0.5. */
    private static NewsImpactEnvelope envelope(ReversalMode reversal) {
        return new NewsImpactEnvelope(ImpactType.TREND, 0.5, 10, 20, reversal, 10, 0.0);
    }

    /** Parses one event JSON string into a definition + report. */
    private static NewsEventDefinition parseEvent(String json, ValidationReport report) {
        JsonObject obj = JsonUtilities.fromString(json).getAsJsonObject();
        return NewsEventDefinition.parse(obj, "test.json", report);
    }

    /** A minimal valid event JSON with the given extra fields spliced in after the id. */
    private static String minimalEvent(String extraFields) {
        return "{\"id\":\"test_event\"," + (extraFields.isEmpty() ? "" : extraFields + ",")
                + "\"headline\":\"Test headline\",\"text\":\"Test text\","
                + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5},"
                + "\"markets\":[{\"item\":\"minecraft:diamond\",\"weightFactor\":1.0}]}";
    }

    /** Creates a temp directory for library tests. */
    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_test");
    }

    /** Best-effort recursive cleanup of a temp directory. */
    private static void deleteRecursively(Path dir) {
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

    /** A tiny valid news file containing one event with the given id and weight. */
    private static String fileWithEvent(String eventId, float weight) {
        return "{\"events\":[{\"id\":\"" + eventId + "\",\"weight\":" + weight + ","
                + "\"headline\":\"H\",\"text\":\"T\","
                + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.2},"
                + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}]}";
    }

    // ========================================================================
    // Envelope math
    // ========================================================================

    /** Impact not started yet (negative active time) must have zero influence. */
    private TestResult test_envelope_negativeTime_isZero() {
        NewsImpactEnvelope env = envelope(ReversalMode.RAMP);
        return assertTrue("factor(-5000) must be 0, got: " + env.factor(-5000),
                env.factor(-5000) == 0.0);
    }

    /** The ramp-up phase must rise linearly from 0 to 1. */
    private TestResult test_envelope_ramp_isLinear() {
        NewsImpactEnvelope env = envelope(ReversalMode.RAMP); // ramp 10s
        TestResult r = assertTrue("factor(0) must be 0", env.factor(0) == 0.0);
        if (!r.passed()) return r;
        double mid = env.factor(5_000);
        r = assertTrue("factor at ramp midpoint must be 0.5, got: " + mid,
                Math.abs(mid - 0.5) < EPSILON);
        if (!r.passed()) return r;
        double quarter = env.factor(2_500);
        return assertTrue("factor at ramp quarter must be 0.25, got: " + quarter,
                Math.abs(quarter - 0.25) < EPSILON);
    }

    /** rampUpSeconds = 0 must jump straight to full influence (shock semantics). */
    private TestResult test_envelope_zeroRamp_isInstant() {
        NewsImpactEnvelope env = new NewsImpactEnvelope(ImpactType.SHOCK, 0.5, 0, 20,
                ReversalMode.RAMP, 10, 0.0);
        TestResult r = assertTrue("factor(0) must be 1 with zero ramp, got: " + env.factor(0),
                env.factor(0) == 1.0);
        if (!r.passed()) return r;
        return assertEquals("phase(0) must be HOLDING with zero ramp",
                Phase.HOLDING, env.phase(0));
    }

    /** The hold phase must stay at exactly 1. */
    private TestResult test_envelope_hold_isFull() {
        NewsImpactEnvelope env = envelope(ReversalMode.RAMP); // ramp 10s + hold 20s
        for (long t = 10_000; t < 30_000; t += 2_500) {
            if (env.factor(t) != 1.0) {
                return fail("factor(" + t + ") must be 1.0 during hold, got: " + env.factor(t));
            }
        }
        return pass("Envelope holds at 1.0 for the whole hold phase");
    }

    /** RAMP reversal: linear decay 1 -> 0 over reversalSeconds, then exactly 0. */
    private TestResult test_envelope_rampReversal_linearToZero() {
        NewsImpactEnvelope env = envelope(ReversalMode.RAMP); // reversal starts at 30s, 10s long
        double mid = env.factor(35_000);
        TestResult r = assertTrue("factor at reversal midpoint must be 0.5, got: " + mid,
                Math.abs(mid - 0.5) < EPSILON);
        if (!r.passed()) return r;
        r = assertTrue("factor at reversal end must be 0, got: " + env.factor(40_000),
                env.factor(40_000) == 0.0);
        if (!r.passed()) return r;
        return assertTrue("factor after reversal end must stay 0, got: " + env.factor(100_000),
                env.factor(100_000) == 0.0);
    }

    /** EXPONENTIAL reversal: e^(-t/tau) decay, hard-expired after the cutoff. */
    private TestResult test_envelope_exponentialReversal_decay() {
        NewsImpactEnvelope env = envelope(ReversalMode.EXPONENTIAL); // tau = 10s, hold ends at 30s
        double atTau = env.factor(40_000); // one time constant into the reversal
        TestResult r = assertTrue("factor after one tau must be 1/e, got: " + atTau,
                Math.abs(atTau - 1.0 / Math.E) < 1e-6);
        if (!r.passed()) return r;
        // After the expiry cutoff (6 time constants) the factor must be exactly 0
        long expired = 30_000 + (long) (10_000 * NewsImpactEnvelope.EXPONENTIAL_EXPIRY_TIME_CONSTANTS);
        r = assertTrue("factor after expiry cutoff must be 0, got: " + env.factor(expired),
                env.factor(expired) == 0.0);
        if (!r.passed()) return r;
        return assertEquals("phase after expiry cutoff must be EXPIRED",
                Phase.EXPIRED, env.phase(expired));
    }

    /** NONE reversal: the envelope stays at 1 forever and reports PERMANENT. */
    private TestResult test_envelope_noneReversal_isPermanent() {
        NewsImpactEnvelope env = envelope(ReversalMode.NONE); // ramp 10s + hold 20s
        TestResult r = assertTrue("factor long after hold end must stay 1.0, got: "
                + env.factor(10_000_000), env.factor(10_000_000) == 1.0);
        if (!r.passed()) return r;
        r = assertEquals("phase after hold end must be PERMANENT (distinct state)",
                Phase.PERMANENT, env.phase(31_000));
        if (!r.passed()) return r;
        r = assertEquals("phase during hold must still be HOLDING",
                Phase.HOLDING, env.phase(20_000));
        if (!r.passed()) return r;
        return assertTrue("totalLength of NONE must be ramp+hold (time until PERMANENT), got: "
                + env.totalLengthMillis(), env.totalLengthMillis() == 30_000);
    }

    /** Full phase walk for RAMP reversal: RAMPING -> HOLDING -> REVERTING -> EXPIRED. */
    private TestResult test_envelope_phaseWalk() {
        NewsImpactEnvelope env = envelope(ReversalMode.RAMP);
        TestResult r = assertEquals("phase during ramp", Phase.RAMPING, env.phase(5_000));
        if (!r.passed()) return r;
        r = assertEquals("phase before start (negative time)", Phase.RAMPING, env.phase(-1));
        if (!r.passed()) return r;
        r = assertEquals("phase during hold", Phase.HOLDING, env.phase(15_000));
        if (!r.passed()) return r;
        r = assertEquals("phase during reversal", Phase.REVERTING, env.phase(35_000));
        if (!r.passed()) return r;
        return assertEquals("phase after reversal", Phase.EXPIRED, env.phase(45_000));
    }

    /** totalLengthMillis per reversal mode (see NewsImpactEnvelope Javadoc). */
    private TestResult test_envelope_totalLengthPerMode() {
        TestResult r = assertTrue("RAMP total = ramp+hold+reversal (40s)",
                envelope(ReversalMode.RAMP).totalLengthMillis() == 40_000);
        if (!r.passed()) return r;
        long expExpected = 30_000 + (long) (10_000 * NewsImpactEnvelope.EXPONENTIAL_EXPIRY_TIME_CONSTANTS);
        r = assertTrue("EXPONENTIAL total = ramp+hold+cutoff (" + expExpected + ")",
                envelope(ReversalMode.EXPONENTIAL).totalLengthMillis() == expExpected);
        if (!r.passed()) return r;
        return assertTrue("NONE total = ramp+hold (30s)",
                envelope(ReversalMode.NONE).totalLengthMillis() == 30_000);
    }

    /** The constructor must defensively sanitize invalid inputs (never NaN/negative prices). */
    private TestResult test_envelope_sanitizesInvalidInputs() {
        NewsImpactEnvelope nan = new NewsImpactEnvelope(ImpactType.SHOCK, Double.NaN, -5, -5,
                ReversalMode.RAMP, -5, Double.NaN);
        TestResult r = assertTrue("NaN peakFactor must be sanitized to 0, got: " + nan.getPeakFactor(),
                nan.getPeakFactor() == 0.0);
        if (!r.passed()) return r;
        r = assertTrue("negative times must clamp to 0",
                nan.getRampUpMillis() == 0 && nan.getHoldMillis() == 0 && nan.getReversalMillis() == 0);
        if (!r.passed()) return r;
        NewsImpactEnvelope belowMinusOne = new NewsImpactEnvelope(ImpactType.CRASH, -1.5, 0, 10,
                ReversalMode.NONE, 0, 0);
        return assertTrue("peakFactor <= -1 must be clamped above -1, got: "
                        + belowMinusOne.getPeakFactor(),
                belowMinusOne.getPeakFactor() > -1.0);
    }

    /** ImpactType presets must supply their documented default shapes. */
    private TestResult test_envelope_typePresets_provideDefaults() {
        NewsImpactEnvelope shock = NewsImpactEnvelope.fromDefaults(ImpactType.SHOCK, 0.3);
        TestResult r = assertEquals("shock default reversal",
                ReversalMode.EXPONENTIAL, shock.getReversal());
        if (!r.passed()) return r;
        NewsImpactEnvelope trend = NewsImpactEnvelope.fromDefaults(ImpactType.TREND, 0.3);
        r = assertEquals("trend default reversal", ReversalMode.RAMP, trend.getReversal());
        if (!r.passed()) return r;
        r = assertTrue("trend default ramp-up is 120s, got: " + trend.getRampUpMillis(),
                trend.getRampUpMillis() == 120_000);
        if (!r.passed()) return r;
        NewsImpactEnvelope crash = NewsImpactEnvelope.fromDefaults(ImpactType.CRASH, -0.3);
        return assertTrue("crash default recovery is slower than shock (900s vs 300s)",
                crash.getReversalMillis() > shock.getReversalMillis());
    }

    // ========================================================================
    // Definition parsing
    // ========================================================================

    /** A plain-string headline/text must normalize to a single-entry en_us map. */
    private TestResult test_parse_plainString_normalizesToMap() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(""), report);
        TestResult r = assertNotNull("minimal event must parse (report: " + report + ")", def);
        if (!r.passed()) return r;
        r = assertEquals("headline map size", 1, def.getHeadline().size());
        if (!r.passed()) return r;
        r = assertEquals("plain string must land under 'en_us'",
                "Test headline", def.getHeadline().get("en_us"));
        if (!r.passed()) return r;
        return assertEquals("text map too", "Test text", def.getText().get("en_us"));
    }

    /** A translation-map headline must keep all languages and their order. */
    private TestResult test_parse_translationMap() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"map_event\","
                        + "\"headline\":{\"de_de\":\"Hallo\",\"en_us\":\"Hello\"},"
                        + "\"text\":{\"en_us\":\"Body\"},"
                        + "\"impact\":{\"type\":\"trend\",\"peakFactor\":0.2},"
                        + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}", report);
        TestResult r = assertNotNull("map event must parse (report: " + report + ")", def);
        if (!r.passed()) return r;
        r = assertEquals("headline languages", 2, def.getHeadline().size());
        if (!r.passed()) return r;
        r = assertEquals("de_de entry", "Hallo", def.getHeadline().get("de_de"));
        if (!r.passed()) return r;
        // Insertion order matters: de_de was first in the JSON -> first map entry
        String firstKey = def.getHeadline().keySet().iterator().next();
        return assertEquals("first map entry must preserve JSON order", "de_de", firstKey);
    }

    /** Omitted optional fields must fall back to their documented defaults. */
    private TestResult test_parse_optionalFields_useDefaults() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(""), report);
        TestResult r = assertNotNull("minimal event must parse", def);
        if (!r.passed()) return r;
        r = assertEquals("default category", NewsEventDefinition.DEFAULT_CATEGORY, def.getCategory());
        if (!r.passed()) return r;
        r = assertEquals("default weight", NewsEventDefinition.DEFAULT_WEIGHT, def.getWeight());
        if (!r.passed()) return r;
        r = assertTrue("default cooldown 0", def.getCooldownSeconds() == 0);
        if (!r.passed()) return r;
        r = assertFalse("default adminOnly false", def.isAdminOnly());
        if (!r.passed()) return r;
        return assertEquals("default announce delay {0,0}",
                NewsEventDefinition.AnnounceDelayRange.ZERO, def.getAnnounceDelayMs());
    }

    /** announceDelayMs {min,max} must parse, allowing negative values (min <= max). */
    private TestResult test_parse_announceDelayRange() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(
                minimalEvent("\"announceDelayMs\":{\"min\":-5000,\"max\":10000}"), report);
        TestResult r = assertNotNull("event with delay range must parse (report: " + report + ")", def);
        if (!r.passed()) return r;
        r = assertTrue("min = -5000, got: " + def.getAnnounceDelayMs().minMs(),
                def.getAnnounceDelayMs().minMs() == -5000);
        if (!r.passed()) return r;
        return assertTrue("max = 10000, got: " + def.getAnnounceDelayMs().maxMs(),
                def.getAnnounceDelayMs().maxMs() == 10000);
    }

    /**
     * A deliberately broken definition must be skipped and the report must contain
     * ALL problems (not just the first one): peakFactor <= -1 and min > max delay.
     */
    private TestResult test_parse_brokenDefinition_reportsAllErrors() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"broken_event\","
                        + "\"headline\":\"H\",\"text\":\"T\","
                        + "\"announceDelayMs\":{\"min\":5000,\"max\":-5000},"
                        + "\"impact\":{\"type\":\"crash\",\"peakFactor\":-1.5},"
                        + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}", report);
        TestResult r = assertNull("broken event must be skipped", def);
        if (!r.passed()) return r;
        r = assertTrue("report must have errors", report.hasErrors());
        if (!r.passed()) return r;
        boolean peakError = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("peakFactor"));
        r = assertTrue("report must contain the peakFactor <= -1 error: " + report, peakError);
        if (!r.passed()) return r;
        boolean delayError = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("announceDelayMs"));
        r = assertTrue("report must ALSO contain the min > max delay error: " + report, delayError);
        if (!r.passed()) return r;
        boolean idAttached = report.getErrors().stream()
                .allMatch(e -> "broken_event".equals(e.eventId()));
        return assertTrue("all errors must be attributed to the event id", idAttached);
    }

    /** An unknown field must produce a WARNING but keep the event loaded. */
    private TestResult test_parse_unknownField_isWarning() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"totallyUnknownField\":42"), report);
        TestResult r = assertNotNull("event with unknown field must still load", def);
        if (!r.passed()) return r;
        r = assertFalse("unknown field must not be an error", report.hasErrors());
        if (!r.passed()) return r;
        boolean warned = report.getWarnings().stream()
                .anyMatch(e -> e.message().contains("totallyUnknownField"));
        return assertTrue("report must warn about the unknown field: " + report, warned);
    }

    /** A negative delay longer than the whole impact must warn (allowed, but flagged). */
    private TestResult test_parse_negativeDelayOutlivingImpact_warns() {
        ValidationReport report = new ValidationReport();
        // Impact: shock, ramp 0s (preset 5s overridden), hold 10s, ramp reversal 10s -> 20s total.
        // Delay min of -60s outlives the whole impact.
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"late_news\","
                        + "\"headline\":\"H\",\"text\":\"T\","
                        + "\"announceDelayMs\":{\"min\":-60000,\"max\":0},"
                        + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.2,"
                        + "\"rampUpSeconds\":0,\"durationSeconds\":10,"
                        + "\"reversal\":\"ramp\",\"reversalSeconds\":10},"
                        + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}", report);
        TestResult r = assertNotNull("event must still load (warning, not error)", def);
        if (!r.passed()) return r;
        boolean warned = report.getWarnings().stream()
                .anyMatch(e -> e.message().contains("outlives"));
        return assertTrue("report must flag the delay outliving the impact: " + report, warned);
    }

    // ========================================================================
    // Matchers
    // ========================================================================

    private static NewsEventDefinition.MarketMatcher singleMatcher(String item, ValidationReport report) {
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"matcher_event\",\"headline\":\"H\",\"text\":\"T\","
                        + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.2},"
                        + "\"markets\":[{\"item\":\"" + item + "\",\"weightFactor\":1.0}]}", report);
        if (def == null || def.getMarkets().isEmpty()) return null;
        return def.getMarkets().get(0);
    }

    /** Exact matcher: matches only the exact registry name. */
    private TestResult test_matcher_exact() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition.MarketMatcher matcher = singleMatcher("minecraft:diamond", report);
        TestResult r = assertNotNull("exact matcher must parse (report: " + report + ")", matcher);
        if (!r.passed()) return r;
        r = assertEquals("kind", NewsEventDefinition.MarketMatcher.Kind.EXACT, matcher.getKind());
        if (!r.passed()) return r;
        r = assertTrue("must match minecraft:diamond",
                matcher.matches("minecraft:diamond", ItemStack.EMPTY));
        if (!r.passed()) return r;
        return assertFalse("must not match minecraft:diamond_block",
                matcher.matches("minecraft:diamond_block", ItemStack.EMPTY));
    }

    /** Exact matcher without a namespace must normalize to "minecraft:" like ResourceLocation. */
    private TestResult test_matcher_exact_normalizesNamespace() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition.MarketMatcher matcher = singleMatcher("diamond", report);
        TestResult r = assertNotNull("namespace-less matcher must parse", matcher);
        if (!r.passed()) return r;
        return assertTrue("'diamond' must match 'minecraft:diamond'",
                matcher.matches("minecraft:diamond", ItemStack.EMPTY));
    }

    /** Glob matcher: '*' wildcard on the registry name. */
    private TestResult test_matcher_glob() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition.MarketMatcher matcher = singleMatcher("minecraft:*_ore", report);
        TestResult r = assertNotNull("glob matcher must parse", matcher);
        if (!r.passed()) return r;
        r = assertEquals("kind", NewsEventDefinition.MarketMatcher.Kind.GLOB, matcher.getKind());
        if (!r.passed()) return r;
        r = assertTrue("must match minecraft:iron_ore",
                matcher.matches("minecraft:iron_ore", ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertTrue("must match minecraft:deepslate_gold_ore",
                matcher.matches("minecraft:deepslate_gold_ore", ItemStack.EMPTY));
        if (!r.passed()) return r;
        r = assertFalse("must not match minecraft:iron_ingot",
                matcher.matches("minecraft:iron_ingot", ItemStack.EMPTY));
        if (!r.passed()) return r;
        return assertFalse("must not match another namespace (mymod:iron_ore)",
                matcher.matches("mymod:iron_ore", ItemStack.EMPTY));
    }

    /** Glob without a namespace must default to "minecraft:". */
    private TestResult test_matcher_glob_withoutNamespace() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition.MarketMatcher matcher = singleMatcher("*_ore", report);
        TestResult r = assertNotNull("namespace-less glob must parse", matcher);
        if (!r.passed()) return r;
        return assertTrue("'*_ore' must match 'minecraft:iron_ore'",
                matcher.matches("minecraft:iron_ore", ItemStack.EMPTY));
    }

    /** Tag matcher: "#minecraft:logs" must match a log stack via its tag holders. */
    private TestResult test_matcher_tag() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition.MarketMatcher matcher = singleMatcher("#minecraft:logs", report);
        TestResult r = assertNotNull("tag matcher must parse (report: " + report + ")", matcher);
        if (!r.passed()) return r;
        r = assertEquals("kind", NewsEventDefinition.MarketMatcher.Kind.TAG, matcher.getKind());
        if (!r.passed()) return r;

        ItemStack oakLog = Items.OAK_LOG.getDefaultInstance();
        if (!oakLog.is(ItemTags.LOGS)) {
            // Item tags are only bound once datapacks load; skip instead of false-failing.
            return pass("Tag matcher parse OK; tag binding not available in this context — match check skipped");
        }
        r = assertTrue("#minecraft:logs must match an oak log stack",
                matcher.matches("minecraft:oak_log", oakLog));
        if (!r.passed()) return r;
        return assertFalse("#minecraft:logs must not match a diamond stack",
                matcher.matches("minecraft:diamond", Items.DIAMOND.getDefaultInstance()));
    }

    /** Negative weightFactor (inverted impact) must survive parsing. */
    private TestResult test_matcher_negativeWeightFactor() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"invert_event\",\"headline\":\"H\",\"text\":\"T\","
                        + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.2},"
                        + "\"markets\":[{\"item\":\"minecraft:emerald\",\"weightFactor\":-0.4}]}", report);
        TestResult r = assertNotNull("event must parse", def);
        if (!r.passed()) return r;
        return assertTrue("negative weightFactor must be preserved, got: "
                        + def.getMarkets().get(0).getWeightFactor(),
                def.getMarkets().get(0).getWeightFactor() == -0.4f);
    }

    // ========================================================================
    // Library
    // ========================================================================

    /** An empty folder must produce default_events.json and load its events. */
    private TestResult test_library_generatesDefaults() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);
            TestResult r = assertTrue("defaults file must exist after reload on empty folder",
                    Files.exists(dir.resolve(DefaultNewsEvents.DEFAULT_FILE_NAME)));
            if (!r.passed()) return r;
            r = assertTrue("default events must be loaded, got: " + library.getDefinitionCount(),
                    library.getDefinitionCount() >= 6);
            if (!r.passed()) return r;
            return assertFalse("defaults must load without errors: " + report, report.hasErrors());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** The shipped defaults must be exemplary: zero errors AND zero warnings. */
    private TestResult test_library_defaultEventsParseClean() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.writeString(dir.resolve("default_events.json"),
                    DefaultNewsEvents.getDefaultEventsJson());
            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);
            TestResult r = assertFalse("defaults must have no errors: " + report, report.hasErrors());
            if (!r.passed()) return r;
            r = assertFalse("defaults must have no warnings: " + report, report.hasWarnings());
            if (!r.passed()) return r;
            // Spot-check the schema showcase features
            NewsEventDefinition rush = library.getDefinition("diamond_rush");
            r = assertNotNull("diamond_rush must be loaded", rush);
            if (!r.passed()) return r;
            r = assertTrue("diamond_rush must be bilingual",
                    rush.getHeadline().containsKey("en_us") && rush.getHeadline().containsKey("de_de"));
            if (!r.passed()) return r;
            NewsEventDefinition gold = library.getDefinition("gold_reserve_standard");
            r = assertNotNull("gold_reserve_standard must be loaded", gold);
            if (!r.passed()) return r;
            r = assertEquals("gold event must demonstrate reversal:none",
                    ReversalMode.NONE, gold.getImpact().getReversal());
            if (!r.passed()) return r;
            NewsEventDefinition scandal = library.getDefinition("emerald_counterfeit_scandal");
            r = assertNotNull("emerald_counterfeit_scandal must be loaded", scandal);
            if (!r.passed()) return r;
            r = assertTrue("scandal event must demonstrate adminOnly", scandal.isAdminOnly());
            if (!r.passed()) return r;
            // T-101: the sequences/requirements/chains showcase entries.
            NewsEventDefinition rumor = library.getDefinition("gold_rush_rumor");
            r = assertNotNull("gold_rush_rumor must be loaded", rumor);
            if (!r.passed()) return r;
            r = assertTrue("gold_rush_rumor must be sequences-authored (no legacy impact,"
                            + " two weighted sequence variants)",
                    rumor.getImpact() == null && rumor.getSequences().size() == 2);
            if (!r.passed()) return r;
            r = assertTrue("gold_reserve_standard must demonstrate requires/records/chains",
                    !gold.getRequirements().isEmpty() && !gold.getRecords().isEmpty()
                            && !gold.getChains().isEmpty());
            if (!r.passed()) return r;
            NewsEventDefinition endOfGold = library.getDefinition("end_of_gold_standard");
            r = assertNotNull("end_of_gold_standard must be loaded", endOfGold);
            if (!r.passed()) return r;
            return assertEquals("end_of_gold_standard must gate on the gold-standard era",
                    3, endOfGold.getRequirements().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Drop-in loading: adding a new file + reload must add its events to the pool. */
    private TestResult test_library_dropIn_mergesFiles() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.writeString(dir.resolve("a.json"), fileWithEvent("event_a", 1f));
            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(dir);
            TestResult r = assertEquals("one event after first reload", 1, library.getDefinitionCount());
            if (!r.passed()) return r;

            // Admin drops a second file into the folder
            Files.writeString(dir.resolve("b.json"), fileWithEvent("event_b", 2f));
            ValidationReport report = library.reload(dir);
            r = assertFalse("merge reload must be clean: " + report, report.hasErrors());
            if (!r.passed()) return r;
            r = assertEquals("both events after second reload", 2, library.getDefinitionCount());
            if (!r.passed()) return r;
            r = assertNotNull("event_a still loaded", library.getDefinition("event_a"));
            if (!r.passed()) return r;
            return assertNotNull("event_b added by drop-in", library.getDefinition("event_b"));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Duplicate ids across files: ERROR reported, the later (alphabetical) file wins. */
    private TestResult test_library_duplicateId_laterWins() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.writeString(dir.resolve("a.json"), fileWithEvent("dupe", 1f));
            Files.writeString(dir.resolve("b.json"), fileWithEvent("dupe", 99f));
            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);
            TestResult r = assertTrue("duplicate id must be an error: " + report, report.hasErrors());
            if (!r.passed()) return r;
            boolean dupeError = report.getErrors().stream()
                    .anyMatch(e -> e.message().contains("duplicate"));
            r = assertTrue("error must mention the duplicate: " + report, dupeError);
            if (!r.passed()) return r;
            r = assertEquals("only one definition in the pool", 1, library.getDefinitionCount());
            if (!r.passed()) return r;
            NewsEventDefinition winner = library.getDefinition("dupe");
            r = assertNotNull("the id must still be loaded", winner);
            if (!r.passed()) return r;
            return assertTrue("the later file (b.json, weight 99) must win, got weight: "
                    + winner.getWeight(), winner.getWeight() == 99f);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A malformed file must never throw; valid files still load; the error is reported. */
    private TestResult test_library_malformedFile_neverThrows() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.writeString(dir.resolve("broken.json"), "{ this is not valid json !!");
            Files.writeString(dir.resolve("valid.json"), fileWithEvent("good_event", 1f));
            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report;
            try {
                report = library.reload(dir);
            } catch (Exception e) {
                return fail("reload must never throw for a malformed file, threw: " + e);
            }
            TestResult r = assertTrue("syntax error must be reported: " + report, report.hasErrors());
            if (!r.passed()) return r;
            r = assertNotNull("valid file must still load", library.getDefinition("good_event"));
            if (!r.passed()) return r;
            boolean fileAttributed = report.getErrors().stream()
                    .anyMatch(e -> e.file().equals("broken.json"));
            return assertTrue("error must be attributed to broken.json: " + report, fileAttributed);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A reload that produces zero valid events (with errors) must keep the previous pool. */
    private TestResult test_library_failedReload_keepsPrevious() {
        Path goodDir = null;
        Path badDir = null;
        try {
            goodDir = createTempDir();
            badDir = createTempDir();
            Files.writeString(goodDir.resolve("a.json"), fileWithEvent("keep_me", 1f));
            // badDir contains ONLY a broken file (a non-empty folder, so no defaults generation)
            Files.writeString(badDir.resolve("broken.json"), "{{{{");

            NewsEventLibrary library = new NewsEventLibrary();
            library.reload(goodDir);
            TestResult r = assertEquals("initial pool loaded", 1, library.getDefinitionCount());
            if (!r.passed()) return r;

            ValidationReport report = library.reload(badDir);
            r = assertTrue("failed reload must report errors: " + report, report.hasErrors());
            if (!r.passed()) return r;
            r = assertNotNull("previous definitions must be kept after a failed reload",
                    library.getDefinition("keep_me"));
            if (!r.passed()) return r;
            boolean keptNote = report.getErrors().stream()
                    .anyMatch(e -> e.message().contains("keeping"));
            return assertTrue("report must state that the old pool was kept: " + report, keptNote);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(goodDir);
            deleteRecursively(badDir);
        }
    }

    /** Scheduler blocks: defaults first, then per-field override, last-loaded file wins. */
    private TestResult test_library_scheduler_lastLoadedWins() {
        Path dir = null;
        try {
            dir = createTempDir();
            // a.json sets two fields; b.json overrides one of them (alphabetical order: a, b)
            Files.writeString(dir.resolve("a.json"),
                    "{\"scheduler\":{\"minSecondsBetweenEvents\":100,\"historyMaxEntries\":42},\"events\":[]}");
            Files.writeString(dir.resolve("b.json"),
                    "{\"scheduler\":{\"minSecondsBetweenEvents\":200},\"events\":[]}");
            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);
            TestResult r = assertFalse("scheduler merge must be clean: " + report, report.hasErrors());
            if (!r.passed()) return r;
            NewsEventLibrary.SchedulerConfig config = library.getSchedulerConfig();
            r = assertTrue("later file must override minSecondsBetweenEvents (200), got: "
                            + config.getMinSecondsBetweenEvents(),
                    config.getMinSecondsBetweenEvents() == 200);
            if (!r.passed()) return r;
            r = assertTrue("earlier file's historyMaxEntries (42) must survive, got: "
                            + config.getHistoryMaxEntries(),
                    config.getHistoryMaxEntries() == 42);
            if (!r.passed()) return r;
            return assertTrue("unspecified fields must keep defaults, got: "
                            + config.getMaxActiveEventsGlobal(),
                    config.getMaxActiveEventsGlobal()
                            == NewsEventLibrary.SchedulerConfig.DEFAULT_MAX_ACTIVE_EVENTS_GLOBAL);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Additive self-heal: an existing {@code default_events.json} holding only a SUBSET of
     * the shipped defaults — one of them admin-modified — plus a separate custom file must,
     * after reload, gain every shipped default while the admin-modified default keeps its
     * change and the custom event survives, with no duplicate-id errors. Also verifies the
     * heal is persisted back into {@code default_events.json}.
     */
    private TestResult test_library_healsMissingDefaultEvents() {
        Path dir = null;
        try {
            dir = createTempDir();

            Map<String, JsonObject> shipped = DefaultNewsEvents.getDefaultEventObjectsById();
            String firstId = DefaultNewsEvents.DEFAULT_EVENT_IDS.get(0);
            String secondId = DefaultNewsEvents.DEFAULT_EVENT_IDS.get(1);

            // default_events.json: scheduler + 2 shipped defaults; the first has a MODIFIED
            // weight (admin edit) that the heal must never overwrite.
            JsonObject modified = shipped.get(firstId).deepCopy();
            modified.addProperty("weight", 999);
            JsonObject secondCopy = shipped.get(secondId).deepCopy();
            JsonObject root = new JsonObject();
            JsonObject scheduler = new JsonObject();
            scheduler.addProperty("minSecondsBetweenEvents", 900);
            root.add("scheduler", scheduler);
            JsonArray events = new JsonArray();
            events.add(modified);
            events.add(secondCopy);
            root.add("events", events);
            Files.writeString(dir.resolve(DefaultNewsEvents.DEFAULT_FILE_NAME),
                    JsonUtilities.toPrettyString(root));

            // A separate custom file with a NON-default event id.
            Files.writeString(dir.resolve("custom.json"), fileWithEvent("my_custom_event", 3f));

            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);

            // No heal-induced errors (in particular: no duplicate-id error).
            TestResult r = assertFalse("heal must not produce errors: " + report, report.hasErrors());
            if (!r.passed()) return r;

            // Every shipped default is now present in the live pool.
            for (String id : DefaultNewsEvents.DEFAULT_EVENT_IDS) {
                if (library.getDefinition(id) == null) {
                    return fail("shipped default not healed into the pool: " + id);
                }
            }
            // The custom event survives the heal.
            r = assertNotNull("custom event must still be loaded", library.getDefinition("my_custom_event"));
            if (!r.passed()) return r;
            // All 72 defaults + the one custom event.
            r = assertEquals("pool = all defaults + the custom event",
                    DefaultNewsEvents.DEFAULT_EVENT_IDS.size() + 1, library.getDefinitionCount());
            if (!r.passed()) return r;
            // The admin-modified default keeps its changed weight (not overwritten).
            r = assertTrue("admin-modified default must keep its weight (999), got: "
                            + library.getDefinition(firstId).getWeight(),
                    library.getDefinition(firstId).getWeight() == 999f);
            if (!r.passed()) return r;

            // Persistence: default_events.json now holds all 72 defaults, and the modified
            // entry is preserved on disk (existing entries never rewritten).
            JsonObject reloadedRoot = JsonUtilities.fromString(Files.readString(
                    dir.resolve(DefaultNewsEvents.DEFAULT_FILE_NAME))).getAsJsonObject();
            JsonArray persistedEvents = reloadedRoot.getAsJsonArray("events");
            r = assertEquals("default_events.json must now contain every shipped default",
                    DefaultNewsEvents.DEFAULT_EVENT_IDS.size(), persistedEvents.size());
            if (!r.passed()) return r;
            int persistedWeight = -1;
            for (JsonElement el : persistedEvents) {
                JsonObject obj = el.getAsJsonObject();
                if (firstId.equals(obj.get("id").getAsString())) {
                    persistedWeight = obj.get("weight").getAsInt();
                    break;
                }
            }
            return assertEquals("modified default's weight must survive on disk", 999, persistedWeight);
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // NewsRecord round-trips
    // ========================================================================

    /** Builds a representative record with translation maps and multiple markets. */
    private static NewsRecord sampleRecord() {
        Map<String, String> headline = new LinkedHashMap<>();
        headline.put("en_us", "Diamond rush!");
        headline.put("de_de", "Diamantenrausch!");
        Map<String, String> text = new LinkedHashMap<>();
        text.put("en_us", "Long body text.");
        text.put("de_de", "Langer Fliesstext.");
        List<NewsRecord.AffectedMarket> markets = new ArrayList<>();
        markets.add(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f));
        markets.add(new NewsRecord.AffectedMarket(new ItemID((short) 7), "minecraft:emerald", -0.4f));
        return new NewsRecord(42L, "diamond_rush", 1_720_000_000_000L, 128L,
                headline, text, markets, "trend", 0.55f, "exponential", 1620);
    }

    /** NBT save + load must reproduce an equal record (incl. deleted-market name strings). */
    private TestResult test_record_nbtRoundTrip() {
        NewsRecord original = sampleRecord();
        CompoundTag tag = new CompoundTag();
        TestResult r = assertTrue("save must succeed", original.save(tag));
        if (!r.passed()) return r;
        NewsRecord loaded = NewsRecord.createFromTag(tag);
        r = assertNotNull("load must succeed", loaded);
        if (!r.passed()) return r;
        r = assertEquals("NBT round-trip must preserve all fields", original, loaded);
        if (!r.passed()) return r;
        return assertEquals("item name string must survive (market-deletion fallback)",
                "minecraft:emerald", loaded.getAffectedMarkets().get(1).itemName());
    }

    /** The translation-map insertion order must survive NBT (first-entry fallback). */
    private TestResult test_record_nbtPreservesTranslationOrder() {
        Map<String, String> headline = new LinkedHashMap<>();
        headline.put("de_de", "Zuerst");
        headline.put("en_us", "Second");
        NewsRecord original = new NewsRecord(1L, "order_test", 0L, 0L,
                headline, Map.of("en_us", "t"), List.of(), "shock", 0.1f, "ramp", 10);
        CompoundTag tag = new CompoundTag();
        original.save(tag);
        NewsRecord loaded = NewsRecord.createFromTag(tag);
        TestResult r = assertNotNull("load must succeed", loaded);
        if (!r.passed()) return r;
        String firstKey = loaded.getHeadline().keySet().iterator().next();
        return assertEquals("first map entry must stay first through NBT", "de_de", firstKey);
    }

    /** STREAM_CODEC encode + decode must reproduce an equal record. */
    private TestResult test_record_streamCodecRoundTrip() {
        RegistryAccess access = UtilitiesPlatform.getRegistryAccess();
        if (access == null) {
            return pass("STREAM_CODEC round-trip skipped (no registry access in this context)");
        }
        NewsRecord original = sampleRecord();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            NewsRecord.STREAM_CODEC.encode(buf, original);
            NewsRecord decoded = NewsRecord.STREAM_CODEC.decode(buf);
            TestResult r = assertEquals("codec round-trip must preserve all fields", original, decoded);
            if (!r.passed()) return r;
            String firstKey = decoded.getHeadline().keySet().iterator().next();
            return assertEquals("translation-map order must survive the network", "en_us", firstKey);
        } finally {
            buf.release();
        }
    }

    // ========================================================================
    // NewsTranslations — render-time resolution fallback chain (T-074)
    // ========================================================================

    /** Builds an insertion-ordered translation map: de_de first, then en_us, then fr_fr. */
    private static Map<String, String> translationMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("de_de", "Hallo");
        map.put("en_us", "Hello");
        map.put("fr_fr", "Bonjour");
        return map;
    }

    /** Step 1: the exact client language wins over en_us and map order. */
    private TestResult test_translations_exactLanguageWins() {
        TestResult r = assertEquals("exact de_de match",
                "Hallo", NewsTranslations.resolve(translationMap(), "de_de"));
        if (!r.passed()) return r;
        return assertEquals("exact fr_fr match (not first entry, not en_us)",
                "Bonjour", NewsTranslations.resolve(translationMap(), "fr_fr"));
    }

    /** Step 2: an unknown client language falls back to en_us. */
    private TestResult test_translations_fallBackToEnUs() {
        return assertEquals("unknown language falls back to en_us",
                "Hello", NewsTranslations.resolve(translationMap(), "es_es"));
    }

    /** Step 3: without an en_us entry, the first map entry (insertion order) wins. */
    private TestResult test_translations_fallBackToFirstEntry() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("de_de", "Hallo");
        map.put("fr_fr", "Bonjour");
        TestResult r = assertEquals("no exact + no en_us resolves to first entry",
                "Hallo", NewsTranslations.resolve(map, "es_es"));
        if (!r.passed()) return r;
        // Single-language admin event: a one-entry map always resolves.
        return assertEquals("one-entry map resolves regardless of language",
                "Bonjour", NewsTranslations.resolve(Map.of("fr_fr", "Bonjour"), "de_de"));
    }

    /** A null language code skips the exact step but keeps the rest of the chain. */
    private TestResult test_translations_nullLanguage_skipsExactStep() {
        TestResult r = assertEquals("null language falls back to en_us",
                "Hello", NewsTranslations.resolve(translationMap(), null));
        if (!r.passed()) return r;
        Map<String, String> noEnUs = new LinkedHashMap<>();
        noEnUs.put("de_de", "Hallo");
        return assertEquals("null language + no en_us resolves to first entry",
                "Hallo", NewsTranslations.resolve(noEnUs, null));
    }

    /** Null/empty maps resolve to the empty string instead of throwing. */
    private TestResult test_translations_emptyMap_yieldsEmptyString() {
        TestResult r = assertEquals("null map yields empty string",
                "", NewsTranslations.resolve(null, "en_us"));
        if (!r.passed()) return r;
        return assertEquals("empty map yields empty string",
                "", NewsTranslations.resolve(new LinkedHashMap<>(), "en_us"));
    }

    // ========================================================================
    // Admin-GUI formatting helpers (T-075, NewsUiFormatting)
    // ========================================================================

    /** Spans below one hour format as mm:ss with truncated partial seconds. */
    private TestResult test_formatRemainingTime_mmSs() {
        TestResult r = assertEquals("zero", "00:00", NewsUiFormatting.formatRemainingTime(0));
        if (!r.passed()) return r;
        r = assertEquals("83s", "01:23", NewsUiFormatting.formatRemainingTime(83_000));
        if (!r.passed()) return r;
        r = assertEquals("partial seconds truncate", "00:59",
                NewsUiFormatting.formatRemainingTime(59_999));
        if (!r.passed()) return r;
        return assertEquals("last sub-hour second", "59:59",
                NewsUiFormatting.formatRemainingTime(3_599_000));
    }

    /** Spans of one hour and above format as h:mm:ss. */
    private TestResult test_formatRemainingTime_hMmSs() {
        TestResult r = assertEquals("exactly one hour", "1:00:00",
                NewsUiFormatting.formatRemainingTime(3_600_000));
        if (!r.passed()) return r;
        r = assertEquals("1h 2m 3s", "1:02:03",
                NewsUiFormatting.formatRemainingTime(3_723_000));
        if (!r.passed()) return r;
        return assertEquals("multi-digit hours", "27:46:39",
                NewsUiFormatting.formatRemainingTime(99_999_000));
    }

    /** Negative remaining times clamp to 00:00 instead of formatting garbage. */
    private TestResult test_formatRemainingTime_clampsNegative() {
        return assertEquals("negative clamps to zero", "00:00",
                NewsUiFormatting.formatRemainingTime(-5_000));
    }

    /** Factor terms format as signed percentages with one decimal. */
    private TestResult test_formatFactorPercent_signed() {
        TestResult r = assertEquals("+4%", "+4.0%", NewsUiFormatting.formatFactorPercent(1.04));
        if (!r.passed()) return r;
        r = assertEquals("+12.3%", "+12.3%", NewsUiFormatting.formatFactorPercent(1.123));
        if (!r.passed()) return r;
        r = assertEquals("-8.1%", "-8.1%", NewsUiFormatting.formatFactorPercent(0.919));
        if (!r.passed()) return r;
        return assertEquals("large factor", "+250.0%", NewsUiFormatting.formatFactorPercent(3.5));
    }

    /** Factors that round to zero display as +0.0% (never "-0.0%"). */
    private TestResult test_formatFactorPercent_neutralIsPositiveZero() {
        TestResult r = assertEquals("exactly neutral", "+0.0%",
                NewsUiFormatting.formatFactorPercent(1.0));
        if (!r.passed()) return r;
        r = assertEquals("tiny negative rounds to +0.0%", "+0.0%",
                NewsUiFormatting.formatFactorPercent(0.9996));
        if (!r.passed()) return r;
        return assertEquals("tiny positive rounds to +0.0%", "+0.0%",
                NewsUiFormatting.formatFactorPercent(1.0004));
    }

    /** Non-finite factors yield the placeholder instead of NaN text. */
    private TestResult test_formatFactorPercent_nonFinite() {
        TestResult r = assertEquals("NaN", NewsUiFormatting.INVALID_FACTOR_TEXT,
                NewsUiFormatting.formatFactorPercent(Double.NaN));
        if (!r.passed()) return r;
        r = assertEquals("positive infinity", NewsUiFormatting.INVALID_FACTOR_TEXT,
                NewsUiFormatting.formatFactorPercent(Double.POSITIVE_INFINITY));
        if (!r.passed()) return r;
        return assertEquals("negative infinity", NewsUiFormatting.INVALID_FACTOR_TEXT,
                NewsUiFormatting.formatFactorPercent(Double.NEGATIVE_INFINITY));
    }

    // ========================================================================
    // NewsSequence math (T-094)
    // ========================================================================

    /**
     * Reference 4-step sequence used by the math tests (32s total, not permanent):
     * <pre>
     *   rise   linear      10s  0    -> 0.4
     *   spike  instant      5s  0.4  -> 0.8   (jump at 10s)
     *   floor  hold         5s  0.8  -> 0.8
     *   decay  exponential 12s  0.8  -> 0.2   (tau = 2s)
     * </pre>
     */
    private static NewsSequence fourStepSequence() {
        return NewsSequence.create(List.of(
                new NewsSequence.StepSpec("rise", 10_000, 0.4, Curve.LINEAR, 0.0, false),
                new NewsSequence.StepSpec("spike", 5_000, 0.8, Curve.INSTANT, 0.05, false),
                new NewsSequence.StepSpec("floor", 5_000, 123.0, Curve.HOLD, 0.0, false),
                new NewsSequence.StepSpec("decay", 12_000, 0.2, Curve.EXPONENTIAL, 0.0, false)));
    }

    /** Pre-start (PENDING) ages must have zero influence, like the legacy envelope. */
    private TestResult test_sequence_negativeAge_isZero() {
        NewsSequence seq = fourStepSequence();
        TestResult r = assertTrue("value(-1) must be 0, got: " + seq.value(-1),
                seq.value(-1) == 0.0);
        if (!r.passed()) return r;
        return assertTrue("value(-5000) must be 0, got: " + seq.value(-5000),
                seq.value(-5000) == 0.0);
    }

    /** LINEAR: interpolates from startValue to targetValue over the step duration. */
    private TestResult test_sequence_linearCurve_interpolates() {
        NewsSequence seq = fourStepSequence(); // rise: 0 -> 0.4 over 10s
        TestResult r = assertTrue("value(0) must be 0, got: " + seq.value(0),
                seq.value(0) == 0.0);
        if (!r.passed()) return r;
        r = assertTrue("value at rise midpoint must be 0.2, got: " + seq.value(5_000),
                Math.abs(seq.value(5_000) - 0.2) < EPSILON);
        if (!r.passed()) return r;
        return assertTrue("value at rise quarter must be 0.1, got: " + seq.value(2_500),
                Math.abs(seq.value(2_500) - 0.1) < EPSILON);
    }

    /** INSTANT: jumps to the target at step start and holds it for the whole step. */
    private TestResult test_sequence_instantCurve_jumpsAtStepStart() {
        NewsSequence seq = fourStepSequence(); // spike starts at 10s
        TestResult r = assertTrue("value just before the spike must still be ~0.4, got: "
                        + seq.value(9_999), Math.abs(seq.value(9_999) - 0.4) < 1e-4);
        if (!r.passed()) return r;
        r = assertTrue("value at spike start must jump to 0.8, got: " + seq.value(10_000),
                seq.value(10_000) == 0.8);
        if (!r.passed()) return r;
        return assertTrue("value at spike end-1 must still be 0.8, got: " + seq.value(14_999),
                seq.value(14_999) == 0.8);
    }

    /** HOLD: keeps the startValue; its targetValue is forced to the startValue. */
    private TestResult test_sequence_holdCurve_keepsStartValue() {
        NewsSequence seq = fourStepSequence(); // floor: 15s..20s at 0.8
        TestResult r = assertTrue("value during hold must be 0.8, got: " + seq.value(17_500),
                seq.value(17_500) == 0.8);
        if (!r.passed()) return r;
        // The spec passed targetValue 123.0 — hold must force it to the chained start.
        return assertTrue("hold step target must be forced to its start (0.8), got: "
                        + seq.getStep(2).targetValue(),
                seq.getStep(2).targetValue() == 0.8);
    }

    /** EXPONENTIAL: asymptotic approach with tau = duration/6 (envelope convention). */
    private TestResult test_sequence_exponentialCurve_approachesTarget() {
        NewsSequence seq = fourStepSequence(); // decay: 20s..32s, 0.8 -> 0.2, tau = 2s
        TestResult r = assertTrue("value at decay start must be 0.8, got: " + seq.value(20_000),
                Math.abs(seq.value(20_000) - 0.8) < EPSILON);
        if (!r.passed()) return r;
        double atTau = seq.value(22_000); // one time constant in
        double expected = 0.2 + 0.6 * Math.exp(-1.0);
        r = assertTrue("value after one tau must be 0.2 + 0.6/e, got: " + atTau,
                Math.abs(atTau - expected) < EPSILON);
        if (!r.passed()) return r;
        // e^-6 residual just before step end (~0.25% of the delta above the target)
        double nearEnd = seq.value(31_999);
        return assertTrue("value just before decay end must be within 1% of 0.2, got: " + nearEnd,
                nearEnd > 0.2 && nearEnd < 0.21);
    }

    /** Non-permanent sequences snap to 0 at and after their total duration. */
    private TestResult test_sequence_endWithoutPermanent_snapsToZero() {
        NewsSequence seq = fourStepSequence(); // total 32s, final target 0.2, not permanent
        TestResult r = assertTrue("totalDurationMs must be 32s, got: " + seq.totalDurationMs(),
                seq.totalDurationMs() == 32_000);
        if (!r.passed()) return r;
        r = assertFalse("sequence must not be permanent", seq.isPermanent());
        if (!r.passed()) return r;
        r = assertTrue("finalValue must be the last target (0.2), got: " + seq.finalValue(),
                seq.finalValue() == 0.2);
        if (!r.passed()) return r;
        r = assertTrue("value at total duration must snap to 0, got: " + seq.value(32_000),
                seq.value(32_000) == 0.0);
        if (!r.passed()) return r;
        return assertTrue("value long after the end must stay 0, got: " + seq.value(10_000_000),
                seq.value(10_000_000) == 0.0);
    }

    /** A permanent last step keeps the final value forever after the sequence end. */
    private TestResult test_sequence_endWithPermanent_keepsFinalValue() {
        NewsSequence seq = NewsSequence.create(List.of(
                new NewsSequence.StepSpec("rise", 10_000, 0.3, Curve.LINEAR, 0.0, false),
                new NewsSequence.StepSpec("lock", 5_000, 0.0, Curve.HOLD, 0.0, true)));
        TestResult r = assertTrue("sequence must be permanent", seq.isPermanent());
        if (!r.passed()) return r;
        r = assertTrue("finalValue must be 0.3, got: " + seq.finalValue(),
                seq.finalValue() == 0.3);
        if (!r.passed()) return r;
        r = assertTrue("value at end must be 0.3, got: " + seq.value(15_000),
                seq.value(15_000) == 0.3);
        if (!r.passed()) return r;
        return assertTrue("value long after the end must stay 0.3, got: " + seq.value(999_999_999),
                seq.value(999_999_999) == 0.3);
    }

    /** stepIndexAt clamps (pre-start -> 0, ended -> last) and skips 0-duration steps. */
    private TestResult test_sequence_stepIndexAndStartLookup() {
        NewsSequence seq = fourStepSequence();
        TestResult r = assertEquals("stepCount", 4, seq.stepCount());
        if (!r.passed()) return r;
        r = assertTrue("negative age clamps to step 0", seq.stepIndexAt(-100) == 0);
        if (!r.passed()) return r;
        r = assertTrue("t=0 is step 0", seq.stepIndexAt(0) == 0);
        if (!r.passed()) return r;
        r = assertTrue("t=9999 is still step 0", seq.stepIndexAt(9_999) == 0);
        if (!r.passed()) return r;
        r = assertTrue("t=10000 is step 1 (boundary belongs to the next step)",
                seq.stepIndexAt(10_000) == 1);
        if (!r.passed()) return r;
        r = assertTrue("t=17500 is step 2", seq.stepIndexAt(17_500) == 2);
        if (!r.passed()) return r;
        r = assertTrue("t=31999 is step 3", seq.stepIndexAt(31_999) == 3);
        if (!r.passed()) return r;
        r = assertTrue("ended clamps to the last step", seq.stepIndexAt(50_000) == 3);
        if (!r.passed()) return r;
        r = assertTrue("stepStartMs walk", seq.stepStartMs(0) == 0
                && seq.stepStartMs(1) == 10_000 && seq.stepStartMs(2) == 15_000
                && seq.stepStartMs(3) == 20_000);
        if (!r.passed()) return r;
        r = assertTrue("stepStartMs(stepCount) must be the total duration (skip target)",
                seq.stepStartMs(4) == 32_000);
        if (!r.passed()) return r;
        r = assertTrue("out-of-range index clamps to the total duration",
                seq.stepStartMs(99) == 32_000);
        if (!r.passed()) return r;

        // 0-duration steps occupy no time: the lookup skips them, but their target
        // still chains into the next step (instant jump at the shared boundary).
        NewsSequence zero = NewsSequence.create(List.of(
                new NewsSequence.StepSpec("a", 10_000, 0.4, Curve.LINEAR, 0.0, false),
                new NewsSequence.StepSpec("b", 0, 0.6, Curve.INSTANT, 0.0, false),
                new NewsSequence.StepSpec("c", 5_000, 0.0, Curve.HOLD, 0.0, false)));
        r = assertTrue("0-duration step must never be reported, got index: "
                + zero.stepIndexAt(10_000), zero.stepIndexAt(10_000) == 2);
        if (!r.passed()) return r;
        return assertTrue("0-duration step's target must chain into the next hold (0.6), got: "
                + zero.value(10_000), zero.value(10_000) == 0.6);
    }

    /** startValue(i) must equal targetValue(i-1); step 0 starts at 0. */
    private TestResult test_sequence_startValuesChain() {
        NewsSequence seq = fourStepSequence();
        List<NewsSequence.Step> steps = seq.getSteps();
        TestResult r = assertTrue("step 0 must start at 0", steps.get(0).startValue() == 0.0);
        if (!r.passed()) return r;
        for (int i = 1; i < steps.size(); i++) {
            if (steps.get(i).startValue() != steps.get(i - 1).targetValue()) {
                return fail("startValue of step " + i + " (" + steps.get(i).startValue()
                        + ") must equal targetValue of step " + (i - 1)
                        + " (" + steps.get(i - 1).targetValue() + ")");
            }
        }
        r = assertTrue("noise accessor must report the active step's noise (spike, 0.05)",
                seq.noiseAt(12_000) == 0.05);
        if (!r.passed()) return r;
        return assertTrue("noise accessor outside the spike must be 0",
                seq.noiseAt(5_000) == 0.0 && seq.noiseAt(17_500) == 0.0);
    }

    /** The factory must defensively sanitize invalid inputs (never NaN/negative prices). */
    private TestResult test_sequence_sanitizesInvalidInputs() {
        // Empty/null spec lists yield a harmless zero sequence instead of throwing.
        NewsSequence empty = NewsSequence.create(null);
        TestResult r = assertTrue("empty factory input must yield a zero sequence",
                empty.stepCount() == 1 && empty.totalDurationMs() == 0
                        && empty.value(0) == 0.0 && !empty.isPermanent());
        if (!r.passed()) return r;

        NewsSequence bad = NewsSequence.create(List.of(
                new NewsSequence.StepSpec(null, -5_000, Double.NaN, null, Double.NaN, true),
                new NewsSequence.StepSpec("end", 10_000, -1.5, Curve.LINEAR, -0.5, false)));
        r = assertTrue("negative duration must clamp to 0",
                bad.getStep(0).durationMs() == 0);
        if (!r.passed()) return r;
        r = assertTrue("NaN target must sanitize to 0", bad.getStep(0).targetValue() == 0.0);
        if (!r.passed()) return r;
        r = assertTrue("null curve must fall back to LINEAR",
                bad.getStep(0).curve() == Curve.LINEAR);
        if (!r.passed()) return r;
        r = assertFalse("permanent on a non-last step must be forced false",
                bad.getStep(0).permanent());
        if (!r.passed()) return r;
        r = assertTrue("target <= -1 must clamp above -1, got: " + bad.getStep(1).targetValue(),
                bad.getStep(1).targetValue() > -1.0);
        if (!r.passed()) return r;
        return assertTrue("negative/NaN noise must sanitize to 0",
                bad.getStep(0).noise() == 0.0 && bad.getStep(1).noise() == 0.0);
    }

    // ========================================================================
    // Legacy-envelope normalization (T-094): value(t) == envelope(t) * peakFactor
    // ========================================================================

    /**
     * Samples the full timeline of the envelope (pre-start, every phase, past the end)
     * and asserts {@code fromLegacyEnvelope(env).value(t) == env.factor(t) * peakFactor}
     * within 1e-9, plus the total-duration equality.
     */
    private TestResult checkNormalizationEquivalence(NewsImpactEnvelope env, String label) {
        NewsSequence seq = NewsSequence.fromLegacyEnvelope(env);
        if (seq.totalDurationMs() != env.totalLengthMillis()) {
            return fail(label + ": totalDurationMs (" + seq.totalDurationMs()
                    + ") != envelope totalLengthMillis (" + env.totalLengthMillis() + ")");
        }
        long total = env.totalLengthMillis();
        // Dense sweep (prime step so samples do not align with phase boundaries) ...
        for (long t = -5_000; t <= total + 30_000; t += 111) {
            TestResult r = compareAt(env, seq, t, label);
            if (r != null) return r;
        }
        // ... plus the exact boundaries and far-future points.
        long[] boundaries = {-1, 0, env.getRampUpMillis(),
                env.getRampUpMillis() + env.getHoldMillis(),
                total - 1, total, total + 1, total + 100_000, 10_000_000_000L};
        for (long t : boundaries) {
            TestResult r = compareAt(env, seq, t, label);
            if (r != null) return r;
        }
        return pass(label + ": sequence matches envelope*peakFactor across the full timeline");
    }

    /** @return a failure result if the two models diverge at t, else null */
    private TestResult compareAt(NewsImpactEnvelope env, NewsSequence seq, long t, String label) {
        double expected = env.factor(t) * env.getPeakFactor();
        double actual = seq.value(t);
        if (Math.abs(actual - expected) > EPSILON) {
            return fail(label + ": divergence at t=" + t + "ms — sequence " + actual
                    + " vs envelope*peak " + expected);
        }
        return null;
    }

    /** Equivalence for reversal:ramp across all impact types and peak signs. */
    private TestResult test_normalization_equivalence_rampReversal() {
        for (ImpactType type : ImpactType.values()) {
            for (double peak : new double[]{0.5, -0.35}) {
                TestResult r = checkNormalizationEquivalence(
                        new NewsImpactEnvelope(type, peak, 7, 13, ReversalMode.RAMP, 9, 0.02),
                        type.jsonName() + "/ramp/peak=" + peak);
                if (!r.passed()) return r;
            }
        }
        // Abrupt end: reversalSeconds = 0 zeroes the influence right after the hold.
        return checkNormalizationEquivalence(
                new NewsImpactEnvelope(ImpactType.SHOCK, 0.4, 2, 5, ReversalMode.RAMP, 0, 0.0),
                "shock/ramp/reversal=0");
    }

    /** Equivalence for reversal:exponential (incl. the e^-6 expiry cutoff and presets). */
    private TestResult test_normalization_equivalence_exponentialReversal() {
        for (ImpactType type : ImpactType.values()) {
            for (double peak : new double[]{0.5, -0.35}) {
                TestResult r = checkNormalizationEquivalence(
                        new NewsImpactEnvelope(type, peak, 7, 13, ReversalMode.EXPONENTIAL, 9, 0.02),
                        type.jsonName() + "/exponential/peak=" + peak);
                if (!r.passed()) return r;
            }
        }
        // The preset defaults of shock and crash use exponential reversals.
        TestResult r = checkNormalizationEquivalence(
                NewsImpactEnvelope.fromDefaults(ImpactType.SHOCK, 0.3), "shock/defaults");
        if (!r.passed()) return r;
        r = checkNormalizationEquivalence(
                NewsImpactEnvelope.fromDefaults(ImpactType.CRASH, -0.3), "crash/defaults");
        if (!r.passed()) return r;
        return checkNormalizationEquivalence(
                new NewsImpactEnvelope(ImpactType.SHOCK, 0.4, 2, 5, ReversalMode.EXPONENTIAL, 0, 0.0),
                "shock/exponential/reversal=0");
    }

    /** Equivalence for reversal:none (permanent shift, value stays at peak forever). */
    private TestResult test_normalization_equivalence_noneReversal() {
        for (ImpactType type : ImpactType.values()) {
            for (double peak : new double[]{0.5, -0.35}) {
                TestResult r = checkNormalizationEquivalence(
                        new NewsImpactEnvelope(type, peak, 7, 13, ReversalMode.NONE, 9, 0.02),
                        type.jsonName() + "/none/peak=" + peak);
                if (!r.passed()) return r;
            }
        }
        // Zero ramp + zero hold: instantly permanent.
        return checkNormalizationEquivalence(
                new NewsImpactEnvelope(ImpactType.SHOCK, 0.4, 0, 0, ReversalMode.NONE, 0, 0.0),
                "shock/none/instant");
    }

    /** The implicit 3-step structure of the normalization (plan §1.1 legacy compat). */
    private TestResult test_normalization_structureMatchesPlan() {
        NewsImpactEnvelope exp = new NewsImpactEnvelope(ImpactType.TREND, 0.5, 10, 20,
                ReversalMode.EXPONENTIAL, 10, 0.07);
        NewsSequence seq = NewsSequence.fromLegacyEnvelope(exp);
        TestResult r = assertEquals("3 steps", 3, seq.stepCount());
        if (!r.passed()) return r;
        r = assertTrue("step names must be ramp/hold/reversal",
                seq.getStep(0).name().equals("ramp") && seq.getStep(1).name().equals("hold")
                        && seq.getStep(2).name().equals("reversal"));
        if (!r.passed()) return r;
        r = assertTrue("exponential reversal step must span 6 time constants (60s), got: "
                        + seq.getStep(2).durationMs(),
                seq.getStep(2).durationMs() == 60_000);
        if (!r.passed()) return r;
        r = assertTrue("the envelope noise must propagate to all steps",
                seq.getStep(0).noise() == 0.07 && seq.getStep(1).noise() == 0.07
                        && seq.getStep(2).noise() == 0.07);
        if (!r.passed()) return r;

        NewsImpactEnvelope none = new NewsImpactEnvelope(ImpactType.TREND, 0.5, 10, 20,
                ReversalMode.NONE, 0, 0.0);
        NewsSequence permanentSeq = NewsSequence.fromLegacyEnvelope(none);
        r = assertEquals("reversal:none also normalizes to 3 steps", 3, permanentSeq.stepCount());
        if (!r.passed()) return r;
        r = assertTrue("third step must be the 0-duration permanent hold",
                permanentSeq.getStep(2).name().equals("permanent")
                        && permanentSeq.getStep(2).durationMs() == 0
                        && permanentSeq.getStep(2).curve() == Curve.HOLD
                        && permanentSeq.getStep(2).permanent());
        if (!r.passed()) return r;
        r = assertTrue("sequence must report permanent", permanentSeq.isPermanent());
        if (!r.passed()) return r;
        return assertTrue("permanent total must equal ramp+hold (30s), got: "
                        + permanentSeq.totalDurationMs(),
                permanentSeq.totalDurationMs() == 30_000);
    }

    // ========================================================================
    // sequences[] parsing (T-094, plan §1.1/§7)
    // ========================================================================

    /** A minimal valid event JSON using the given sequences array instead of impact. */
    private static String sequencesEvent(String sequencesJson) {
        return "{\"id\":\"seq_event\",\"headline\":\"H\",\"text\":\"T\","
                + "\"markets\":[{\"item\":\"minecraft:diamond\"}],"
                + "\"sequences\":" + sequencesJson + "}";
    }

    /** A valid single-sequence array with one custom-shaped sequence body spliced in. */
    private static String singleSequence(String stepsJson) {
        return "[{\"name\":\"seq\",\"steps\":" + stepsJson + "}]";
    }

    /** A full multi-sequence event must parse with all fields and defaults intact. */
    private TestResult test_parse_sequences_validMultiSequence() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(
                "[{\"name\":\"false_breakout\",\"weight\":2,\"steps\":["
                        + "{\"name\":\"hype\",\"durationSeconds\":{\"min\":60,\"max\":180},"
                        + "\"targetFactor\":0.3,\"curve\":\"linear\",\"noise\":0.05,"
                        + "\"markets\":[{\"item\":\"minecraft:gold_ingot\",\"weightFactor\":1.0}]},"
                        + "{\"name\":\"collapse\",\"durationSeconds\":{\"min\":30,\"max\":60},"
                        + "\"targetFactor\":-0.1,\"curve\":\"exponential\"},"
                        + "{\"name\":\"recover\",\"durationSeconds\":300,\"targetFactor\":0.0}]},"
                        + "{\"name\":\"slow_fade\",\"steps\":["
                        + "{\"name\":\"fade\",\"durationSeconds\":10,\"targetFactor\":0.0,"
                        + "\"curve\":\"instant\"}]}]"), report);
        TestResult r = assertNotNull("multi-sequence event must parse (report: " + report + ")", def);
        if (!r.passed()) return r;
        r = assertFalse("valid document must have no errors: " + report, report.hasErrors());
        if (!r.passed()) return r;
        r = assertNull("sequence events must have no legacy envelope", def.getImpact());
        if (!r.passed()) return r;
        r = assertEquals("two sequences", 2, def.getSequences().size());
        if (!r.passed()) return r;

        NewsEventDefinition.SequenceDefinition first = def.getSequences().get(0);
        r = assertEquals("first sequence name", "false_breakout", first.getName());
        if (!r.passed()) return r;
        r = assertTrue("explicit weight 2, got: " + first.getWeight(), first.getWeight() == 2f);
        if (!r.passed()) return r;
        r = assertEquals("first sequence has 3 steps", 3, first.getStepCount());
        if (!r.passed()) return r;

        NewsEventDefinition.StepDefinition hype = first.getSteps().get(0);
        r = assertTrue("hype duration range 60s..180s, got: " + hype.getDurationMinMs()
                        + ".." + hype.getDurationMaxMs(),
                hype.getDurationMinMs() == 60_000 && hype.getDurationMaxMs() == 180_000);
        if (!r.passed()) return r;
        r = assertTrue("hype target 0.3", hype.getTargetFactor() == 0.3);
        if (!r.passed()) return r;
        r = assertTrue("hype noise 0.05", hype.getNoise() == 0.05);
        if (!r.passed()) return r;
        r = assertNotNull("hype must carry its per-step markets", hype.getMarkets());
        if (!r.passed()) return r;

        NewsEventDefinition.StepDefinition collapse = first.getSteps().get(1);
        r = assertEquals("collapse curve", Curve.EXPONENTIAL, collapse.getCurve());
        if (!r.passed()) return r;
        r = assertNull("collapse has no per-step markets (inherits event-level)",
                collapse.getMarkets());
        if (!r.passed()) return r;

        NewsEventDefinition.StepDefinition recover = first.getSteps().get(2);
        r = assertEquals("omitted curve defaults to linear", Curve.LINEAR, recover.getCurve());
        if (!r.passed()) return r;
        r = assertTrue("fixed duration => min == max == 300s",
                recover.getDurationMinMs() == 300_000 && recover.getDurationMaxMs() == 300_000);
        if (!r.passed()) return r;
        r = assertTrue("omitted noise defaults to 0", recover.getNoise() == 0.0);
        if (!r.passed()) return r;

        return assertTrue("omitted sequence weight defaults to 1, got: "
                        + def.getSequences().get(1).getWeight(),
                def.getSequences().get(1).getWeight()
                        == NewsEventDefinition.SequenceDefinition.DEFAULT_SEQUENCE_WEIGHT);
    }

    /** Every schema key of the sequences form must be known (no unknown-key warnings). */
    private TestResult test_parse_sequences_knownKeysNoWarnings() {
        ValidationReport report = new ValidationReport();
        // Uses every key: name, weight, steps / name, durationSeconds (both forms),
        // targetFactor, curve, noise, markets, permanent.
        NewsEventDefinition def = parseEvent(sequencesEvent(
                "[{\"name\":\"perma\",\"weight\":1.5,\"steps\":["
                        + "{\"name\":\"rise\",\"durationSeconds\":{\"min\":5,\"max\":10},"
                        + "\"targetFactor\":0.2,\"curve\":\"linear\",\"noise\":0.01,"
                        + "\"markets\":[{\"item\":\"minecraft:gold_ingot\"}]},"
                        + "{\"name\":\"lock\",\"durationSeconds\":0,\"curve\":\"hold\","
                        + "\"permanent\":true}]}]"), report);
        TestResult r = assertNotNull("valid permanent sequence must parse (report: "
                + report + ")", def);
        if (!r.passed()) return r;
        r = assertFalse("no errors expected: " + report, report.hasErrors());
        if (!r.passed()) return r;
        r = assertFalse("no warnings expected (all keys known, permanent end): " + report,
                report.hasWarnings());
        if (!r.passed()) return r;
        return assertTrue("last step must be permanent",
                def.getSequences().get(0).getSteps().get(1).isPermanent());
    }

    /** impact + sequences on the same event is an ERROR (mutually exclusive). */
    private TestResult test_parse_impactAndSequences_mutuallyExclusive() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(
                "\"sequences\":[{\"name\":\"s\",\"steps\":[{\"name\":\"a\","
                        + "\"durationSeconds\":5,\"targetFactor\":0.1}]}]"), report);
        TestResult r = assertNull("event with both impact and sequences must be skipped", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("mutually exclusive"));
        return assertTrue("error must state the mutual exclusion: " + report, flagged);
    }

    /** Neither impact nor sequences is an ERROR (an event needs a behavior). */
    private TestResult test_parse_neitherImpactNorSequences_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(
                "{\"id\":\"no_behavior\",\"headline\":\"H\",\"text\":\"T\","
                        + "\"markets\":[{\"item\":\"minecraft:diamond\"}]}", report);
        TestResult r = assertNull("event without impact/sequences must be skipped", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("either an 'impact'"));
        return assertTrue("error must name both accepted forms: " + report, flagged);
    }

    /** An empty sequences array (and a non-array value) is an ERROR. */
    private TestResult test_parse_sequencesEmpty_isError() {
        ValidationReport report = new ValidationReport();
        TestResult r = assertNull("empty sequences must be skipped",
                parseEvent(sequencesEvent("[]"), report));
        if (!r.passed()) return r;
        boolean emptyFlagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("'sequences' is empty"));
        r = assertTrue("error must flag the empty array: " + report, emptyFlagged);
        if (!r.passed()) return r;

        ValidationReport report2 = new ValidationReport();
        r = assertNull("non-array sequences must be skipped",
                parseEvent(sequencesEvent("5"), report2));
        if (!r.passed()) return r;
        boolean typeFlagged = report2.getErrors().stream()
                .anyMatch(e -> e.message().contains("must be an array"));
        return assertTrue("error must flag the wrong type: " + report2, typeFlagged);
    }

    /** A sequence without steps (missing or empty array) is an ERROR. */
    private TestResult test_parse_sequenceWithoutSteps_isError() {
        ValidationReport report = new ValidationReport();
        TestResult r = assertNull("sequence with empty steps must be skipped",
                parseEvent(sequencesEvent("[{\"name\":\"s\",\"steps\":[]}]"), report));
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("non-empty 'steps'"));
        r = assertTrue("error must demand a non-empty steps array: " + report, flagged);
        if (!r.passed()) return r;
        ValidationReport report2 = new ValidationReport();
        return assertNull("sequence without a steps key must be skipped",
                parseEvent(sequencesEvent("[{\"name\":\"s\"}]"), report2));
    }

    /** A step without a name is an ERROR. */
    private TestResult test_parse_stepMissingName_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"durationSeconds\":5,\"targetFactor\":0.1}]")), report);
        TestResult r = assertNull("step without name must skip the event", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("has no 'name'"));
        return assertTrue("error must flag the missing step name: " + report, flagged);
    }

    /** Duplicate step names within one sequence are an ERROR. */
    private TestResult test_parse_duplicateStepNames_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"twin\",\"durationSeconds\":5,\"targetFactor\":0.1},"
                        + "{\"name\":\"twin\",\"durationSeconds\":5,\"targetFactor\":0.0}]")), report);
        TestResult r = assertNull("duplicate step names must skip the event", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("duplicate step name"));
        return assertTrue("error must flag the duplicate name: " + report, flagged);
    }

    /** durationSeconds: negative, min > max and missing are all ERRORs. */
    private TestResult test_parse_stepDurationErrors() {
        ValidationReport negative = new ValidationReport();
        TestResult r = assertNull("negative duration must skip the event",
                parseEvent(sequencesEvent(singleSequence(
                        "[{\"name\":\"a\",\"durationSeconds\":-5,\"targetFactor\":0.1}]")), negative));
        if (!r.passed()) return r;
        r = assertTrue("negative duration must be an error: " + negative, negative.hasErrors());
        if (!r.passed()) return r;

        ValidationReport range = new ValidationReport();
        r = assertNull("min > max must skip the event",
                parseEvent(sequencesEvent(singleSequence(
                        "[{\"name\":\"a\",\"durationSeconds\":{\"min\":60,\"max\":30},"
                                + "\"targetFactor\":0.1}]")), range));
        if (!r.passed()) return r;
        boolean rangeFlagged = range.getErrors().stream()
                .anyMatch(e -> e.message().contains("min (60"));
        r = assertTrue("error must show the inverted range: " + range, rangeFlagged);
        if (!r.passed()) return r;

        ValidationReport missing = new ValidationReport();
        r = assertNull("missing duration must skip the event",
                parseEvent(sequencesEvent(singleSequence(
                        "[{\"name\":\"a\",\"targetFactor\":0.1}]")), missing));
        if (!r.passed()) return r;
        boolean missingFlagged = missing.getErrors().stream()
                .anyMatch(e -> e.message().contains("durationSeconds"));
        return assertTrue("error must name durationSeconds: " + missing, missingFlagged);
    }

    /** targetFactor: <= -1 and missing (for a moving curve) are ERRORs. */
    private TestResult test_parse_stepTargetFactorErrors() {
        ValidationReport tooLow = new ValidationReport();
        TestResult r = assertNull("targetFactor <= -1 must skip the event",
                parseEvent(sequencesEvent(singleSequence(
                        "[{\"name\":\"a\",\"durationSeconds\":5,\"targetFactor\":-1.5}]")), tooLow));
        if (!r.passed()) return r;
        boolean lowFlagged = tooLow.getErrors().stream()
                .anyMatch(e -> e.message().contains("must be > -1"));
        r = assertTrue("error must state the > -1 bound: " + tooLow, lowFlagged);
        if (!r.passed()) return r;

        ValidationReport missing = new ValidationReport();
        r = assertNull("missing targetFactor on a linear step must skip the event",
                parseEvent(sequencesEvent(singleSequence(
                        "[{\"name\":\"a\",\"durationSeconds\":5}]")), missing));
        if (!r.passed()) return r;
        boolean missingFlagged = missing.getErrors().stream()
                .anyMatch(e -> e.message().contains("targetFactor"));
        return assertTrue("error must name targetFactor: " + missing, missingFlagged);
    }

    /** An unknown curve name is an ERROR. */
    private TestResult test_parse_stepUnknownCurve_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"a\",\"durationSeconds\":5,\"targetFactor\":0.1,"
                        + "\"curve\":\"wiggle\"}]")), report);
        TestResult r = assertNull("unknown curve must skip the event", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("linear|instant|exponential|hold"));
        return assertTrue("error must list the valid curves: " + report, flagged);
    }

    /** permanent:true on a non-last step is an ERROR (allowed on the last step). */
    private TestResult test_parse_permanentOnNonLastStep_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"a\",\"durationSeconds\":5,\"targetFactor\":0.1,\"permanent\":true},"
                        + "{\"name\":\"b\",\"durationSeconds\":5,\"targetFactor\":0.0}]")), report);
        TestResult r = assertNull("permanent on a non-last step must skip the event", def);
        if (!r.passed()) return r;
        boolean flagged = report.getErrors().stream()
                .anyMatch(e -> e.message().contains("only allowed on the last step"));
        r = assertTrue("error must state the last-step rule: " + report, flagged);
        if (!r.passed()) return r;

        // Control: permanent on the LAST step is valid.
        ValidationReport ok = new ValidationReport();
        NewsEventDefinition valid = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"a\",\"durationSeconds\":5,\"targetFactor\":0.1,"
                        + "\"permanent\":true}]")), ok);
        r = assertNotNull("permanent on the last step must parse: " + ok, valid);
        if (!r.passed()) return r;
        return assertTrue("last step must carry the permanent flag",
                valid.getSequences().get(0).getSteps().get(0).isPermanent());
    }

    /** A non-positive sequence weight is an ERROR. */
    private TestResult test_parse_sequenceWeightInvalid_isError() {
        ValidationReport zero = new ValidationReport();
        TestResult r = assertNull("weight 0 must skip the event",
                parseEvent(sequencesEvent(
                        "[{\"name\":\"s\",\"weight\":0,\"steps\":[{\"name\":\"a\","
                                + "\"durationSeconds\":5,\"targetFactor\":0.0}]}]"), zero));
        if (!r.passed()) return r;
        boolean flagged = zero.getErrors().stream()
                .anyMatch(e -> e.message().contains("positive finite"));
        r = assertTrue("error must demand a positive weight: " + zero, flagged);
        if (!r.passed()) return r;
        ValidationReport negative = new ValidationReport();
        return assertNull("negative weight must skip the event",
                parseEvent(sequencesEvent(
                        "[{\"name\":\"s\",\"weight\":-2,\"steps\":[{\"name\":\"a\","
                                + "\"durationSeconds\":5,\"targetFactor\":0.0}]}]"), negative));
    }

    /** A non-zero final value without permanent warns ("influence snaps off"). */
    private TestResult test_parse_finalNonzeroWithoutPermanent_warns() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"a\",\"durationSeconds\":5,\"targetFactor\":0.25}]")), report);
        TestResult r = assertNotNull("event must still load (warning, not error)", def);
        if (!r.passed()) return r;
        r = assertFalse("must not be an error: " + report, report.hasErrors());
        if (!r.passed()) return r;
        boolean warned = report.getWarnings().stream()
                .anyMatch(e -> e.message().contains("snaps off"));
        return assertTrue("report must warn about the snap-off: " + report, warned);
    }

    /** A hold step with a different explicit targetFactor warns and keeps the chain. */
    private TestResult test_parse_holdWithTargetFactor_warns() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"rise\",\"durationSeconds\":5,\"targetFactor\":0.3},"
                        + "{\"name\":\"plateau\",\"durationSeconds\":5,\"curve\":\"hold\","
                        + "\"targetFactor\":0.9},"
                        + "{\"name\":\"down\",\"durationSeconds\":5,\"targetFactor\":0.0}]")), report);
        TestResult r = assertNotNull("event must still load (warning, not error)", def);
        if (!r.passed()) return r;
        boolean warned = report.getWarnings().stream()
                .anyMatch(e -> e.message().contains("ignored for"));
        r = assertTrue("report must warn about the ignored targetFactor: " + report, warned);
        if (!r.passed()) return r;
        return assertTrue("hold step must resolve to the previous target (0.3), got: "
                        + def.getSequences().get(0).getSteps().get(1).getTargetFactor(),
                def.getSequences().get(0).getSteps().get(1).getTargetFactor() == 0.3);
    }

    /** Per-step markets parse with the shared matcher grammar; absent = inherit. */
    private TestResult test_parse_stepMarkets_parsedAndInherit() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(sequencesEvent(singleSequence(
                "[{\"name\":\"gold\",\"durationSeconds\":5,\"targetFactor\":0.2,"
                        + "\"markets\":[{\"item\":\"minecraft:gold_ingot\",\"weightFactor\":0.5},"
                        + "{\"item\":\"#c:ores\"}]},"
                        + "{\"name\":\"back\",\"durationSeconds\":5,\"targetFactor\":0.0}]")), report);
        TestResult r = assertNotNull("event must parse (report: " + report + ")", def);
        if (!r.passed()) return r;
        List<NewsEventDefinition.MarketMatcher> stepMarkets =
                def.getSequences().get(0).getSteps().get(0).getMarkets();
        r = assertNotNull("first step must have its own markets", stepMarkets);
        if (!r.passed()) return r;
        r = assertEquals("two matchers on the step", 2, stepMarkets.size());
        if (!r.passed()) return r;
        r = assertEquals("first matcher is exact",
                NewsEventDefinition.MarketMatcher.Kind.EXACT, stepMarkets.get(0).getKind());
        if (!r.passed()) return r;
        r = assertTrue("weightFactor 0.5 preserved", stepMarkets.get(0).getWeightFactor() == 0.5f);
        if (!r.passed()) return r;
        r = assertEquals("second matcher is a tag",
                NewsEventDefinition.MarketMatcher.Kind.TAG, stepMarkets.get(1).getKind());
        if (!r.passed()) return r;
        return assertNull("second step inherits the event-level markets (null)",
                def.getSequences().get(0).getSteps().get(1).getMarkets());
    }

    /** A legacy impact event must expose exactly one implicit normalized sequence. */
    private TestResult test_parse_legacyImpact_normalizesToImplicitSequence() {
        ValidationReport report = new ValidationReport();
        // shock preset: ramp 5s, hold 300s, exponential reversal tau 300s
        NewsEventDefinition def = parseEvent(minimalEvent(""), report);
        TestResult r = assertNotNull("legacy event must parse", def);
        if (!r.passed()) return r;
        r = assertNotNull("legacy event keeps its envelope", def.getImpact());
        if (!r.passed()) return r;
        r = assertEquals("exactly one implicit sequence", 1, def.getSequences().size());
        if (!r.passed()) return r;
        NewsEventDefinition.SequenceDefinition seq = def.getSequences().get(0);
        r = assertEquals("implicit sequence is named 'impact'", "impact", seq.getName());
        if (!r.passed()) return r;
        r = assertEquals("implicit sequence has 3 steps", 3, seq.getStepCount());
        if (!r.passed()) return r;
        NewsEventDefinition.StepDefinition ramp = seq.getSteps().get(0);
        r = assertTrue("ramp step: fixed 5s linear to peak 0.5",
                ramp.getName().equals("ramp") && ramp.getDurationMinMs() == 5_000
                        && ramp.getDurationMaxMs() == 5_000 && ramp.getTargetFactor() == 0.5
                        && ramp.getCurve() == Curve.LINEAR);
        if (!r.passed()) return r;
        NewsEventDefinition.StepDefinition hold = seq.getSteps().get(1);
        r = assertTrue("hold step: fixed 300s hold at peak",
                hold.getName().equals("hold") && hold.getDurationMinMs() == 300_000
                        && hold.getCurve() == Curve.HOLD && hold.getTargetFactor() == 0.5);
        if (!r.passed()) return r;
        NewsEventDefinition.StepDefinition reversal = seq.getSteps().get(2);
        r = assertTrue("reversal step: exponential over 6 time constants (1800s) to 0",
                reversal.getName().equals("reversal") && reversal.getDurationMinMs() == 1_800_000
                        && reversal.getCurve() == Curve.EXPONENTIAL
                        && reversal.getTargetFactor() == 0.0 && !reversal.isPermanent());
        if (!r.passed()) return r;
        r = assertNull("implicit steps have no per-step markets", ramp.getMarkets());
        if (!r.passed()) return r;

        // reversal:none normalizes to the 0-duration permanent hold step.
        ValidationReport report2 = new ValidationReport();
        NewsEventDefinition none = parseEvent(minimalEvent("").replace(
                "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5}",
                "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5,\"reversal\":\"none\"}"), report2);
        r = assertNotNull("reversal:none event must parse", none);
        if (!r.passed()) return r;
        NewsEventDefinition.StepDefinition last =
                none.getSequences().get(0).getSteps().get(2);
        return assertTrue("reversal:none => 0-duration permanent hold step named 'permanent'",
                last.getName().equals("permanent") && last.getDurationMinMs() == 0
                        && last.getCurve() == Curve.HOLD && last.isPermanent());
    }
}
