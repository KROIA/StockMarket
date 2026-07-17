package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsRequirement;
import net.kroia.stockmarket.news.NewsRequirement.CountAtLeast;
import net.kroia.stockmarket.news.NewsRequirement.CountAtMost;
import net.kroia.stockmarket.news.NewsRequirement.FiredBefore;
import net.kroia.stockmarket.news.NewsRequirement.KeyAbsent;
import net.kroia.stockmarket.news.NewsRequirement.KeyAtLeast;
import net.kroia.stockmarket.news.NewsRequirement.KeyAtMost;
import net.kroia.stockmarket.news.NewsRequirement.KeyEquals;
import net.kroia.stockmarket.news.NewsRequirement.KeyExists;
import net.kroia.stockmarket.news.NewsRequirement.KeyNotEquals;
import net.kroia.stockmarket.news.NewsRequirement.NotFired;
import net.kroia.stockmarket.news.NewsRequirement.NotFiredWithin;
import net.kroia.stockmarket.news.NewsWorldRegistry;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.util.List;

/**
 * Tests for the news trigger-requirement engine (T-097, sequences plan §3/§7/§10,
 * category {@code sm_news_requirements}): every predicate type against a real
 * {@link NewsWorldRegistry} instance incl. the boundary and absent-key/unparseable
 * semantics, the {@link NewsRequirement#allMet}/{@link NewsRequirement#unmet}
 * composition helpers, {@link NewsRequirement#describe()} rendering, and the
 * {@code requires[]}/{@code records{}} parsing in {@link NewsEventDefinition}
 * (unknown type = fatal ERROR per plan §10; records use the non-fatal pattern).
 * Pure in-memory — no Minecraft context needed.
 */
public class NewsRequirementTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_REQUIREMENTS;
    }

    @Override
    public void registerTests() {
        // Predicate semantics
        addTest("fired_before_default_min_times", this::test_firedBefore_defaultMinTimes);
        addTest("fired_before_min_times", this::test_firedBefore_minTimes);
        addTest("fired_before_min_seconds_ago_boundary", this::test_firedBefore_minSecondsAgoBoundary);
        addTest("fired_before_max_seconds_ago_boundary", this::test_firedBefore_maxSecondsAgoBoundary);
        addTest("fired_before_combined_window", this::test_firedBefore_combinedWindow);
        addTest("not_fired", this::test_notFired);
        addTest("not_fired_within_boundary", this::test_notFiredWithin_boundary);
        addTest("count_at_least", this::test_countAtLeast);
        addTest("count_at_most", this::test_countAtMost);
        addTest("key_equals_absent_is_false", this::test_keyEquals_absentIsFalse);
        addTest("key_not_equals_absent_is_true", this::test_keyNotEquals_absentIsTrue);
        addTest("key_exists_and_key_absent", this::test_keyExistsAndKeyAbsent);
        addTest("key_at_least_boundary_and_unparseable", this::test_keyAtLeast_boundaryAndUnparseable);
        addTest("key_at_most_boundary_and_unparseable", this::test_keyAtMost_boundaryAndUnparseable);

        // Composition helpers (T-098 eligibility / T-100 popup)
        addTest("all_met_composition", this::test_allMet_composition);
        addTest("unmet_returns_failing_in_order", this::test_unmet_returnsFailingInOrder);

        // describe() rendering (T-099/T-100)
        addTest("describe_non_empty_for_every_type", this::test_describe_nonEmptyForEveryType);

        // requires[] parsing
        addTest("parse_gold_standard_pair", this::test_parse_goldStandardPair);
        addTest("parse_unknown_type_is_error_event_skipped", this::test_parse_unknownType_isErrorEventSkipped);
        addTest("parse_missing_fields_are_errors", this::test_parse_missingFields_areErrors);
        addTest("parse_negative_values_are_errors", this::test_parse_negativeValues_areErrors);
        addTest("parse_unknown_inner_key_warns", this::test_parse_unknownInnerKey_warns);
        addTest("parse_requires_not_array_is_error", this::test_parse_requiresNotArray_isError);
        addTest("parse_requires_entry_not_object_is_error", this::test_parse_requiresEntryNotObject_isError);
        addTest("parse_key_at_least_value_forms", this::test_parse_keyAtLeast_valueForms);
        addTest("parse_inverted_age_window_warns", this::test_parse_invertedAgeWindow_warns);
        addTest("parse_no_requires_no_records_defaults", this::test_parse_noRequiresNoRecords_defaults);

        // records{} parsing
        addTest("parse_records_map_happy_path", this::test_parse_recordsMap_happyPath);
        addTest("parse_records_non_string_entry_skipped_event_kept", this::test_parse_recordsNonString_entrySkippedEventKept);
        addTest("parse_records_not_object_field_ignored_event_kept", this::test_parse_recordsNotObject_fieldIgnoredEventKept);
        addTest("parse_records_cap_warnings", this::test_parse_recordsCapWarnings);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Parses one event JSON string into a definition + report (NewsSystemTestSuite pattern). */
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

    /** Parses one bare requirement object via {@link NewsRequirement#parse}. */
    private static NewsRequirement parseRequirement(String json, ValidationReport report) {
        JsonObject obj = JsonUtilities.fromString(json).getAsJsonObject();
        return NewsRequirement.parse(obj, "test.json", "test_event", report);
    }

    // ========================================================================
    // Predicate semantics
    // ========================================================================

    /** firedBefore with defaults: one fire suffices (minTimes 1); never-fired is false. */
    private TestResult test_firedBefore_defaultMinTimes() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("gold_standard", 100_000L, 1L);
        NewsRequirement fired = new FiredBefore("gold_standard", 1, null, null);
        NewsRequirement never = new FiredBefore("never_happened", 1, null, null);

        TestResult r = assertTrue("one fire must satisfy the default minTimes of 1",
                fired.test(registry, 200_000L));
        if (!r.passed()) return r;
        return assertFalse("a never-fired event must always fail firedBefore",
                never.test(registry, 200_000L));
    }

    /** firedBefore minTimes: count below the bound fails, reaching it passes. */
    private TestResult test_firedBefore_minTimes() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 1_000L, 1L);
        registry.recordFire("e", 2_000L, 1L);
        NewsRequirement needsThree = new FiredBefore("e", 3, null, null);
        NewsRequirement needsTwo = new FiredBefore("e", 2, null, null);

        TestResult r = assertFalse("2 fires must fail minTimes 3",
                needsThree.test(registry, 10_000L));
        if (!r.passed()) return r;
        r = assertTrue("2 fires must satisfy minTimes 2", needsTwo.test(registry, 10_000L));
        if (!r.passed()) return r;
        registry.recordFire("e", 3_000L, 1L);
        return assertTrue("the 3rd fire must satisfy minTimes 3",
                needsThree.test(registry, 10_000L));
    }

    /** minSecondsAgo is boundary-INCLUSIVE: exactly that old passes, 1 ms younger fails. */
    private TestResult test_firedBefore_minSecondsAgoBoundary() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 100_000L, 1L); // last fire at t=100s
        NewsRequirement req = new FiredBefore("e", 1, 50L, null); // at least 50s ago

        TestResult r = assertTrue("a fire EXACTLY minSecondsAgo old must pass (inclusive)",
                req.test(registry, 100_000L + 50_000L));
        if (!r.passed()) return r;
        r = assertFalse("a fire 1 ms younger than minSecondsAgo must fail",
                req.test(registry, 100_000L + 49_999L));
        if (!r.passed()) return r;
        return assertTrue("an older fire must pass", req.test(registry, 100_000L + 80_000L));
    }

    /** maxSecondsAgo is boundary-INCLUSIVE: exactly that old passes, 1 ms older fails. */
    private TestResult test_firedBefore_maxSecondsAgoBoundary() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 100_000L, 1L);
        NewsRequirement req = new FiredBefore("e", 1, null, 50L); // at most 50s ago

        TestResult r = assertTrue("a fire EXACTLY maxSecondsAgo old must pass (inclusive)",
                req.test(registry, 100_000L + 50_000L));
        if (!r.passed()) return r;
        r = assertFalse("a fire 1 ms older than maxSecondsAgo must fail",
                req.test(registry, 100_000L + 50_001L));
        if (!r.passed()) return r;
        return assertTrue("a fresh fire must pass", req.test(registry, 100_000L + 1_000L));
    }

    /** Both bounds together form an inclusive window on the LAST fire's age. */
    private TestResult test_firedBefore_combinedWindow() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 0L, 1L);
        registry.recordFire("e", 100_000L, 1L); // last fire at t=100s (window uses LAST)
        NewsRequirement req = new FiredBefore("e", 1, 10L, 20L); // 10s..20s ago

        TestResult r = assertFalse("age 5s must be below the window",
                req.test(registry, 105_000L));
        if (!r.passed()) return r;
        r = assertTrue("age 15s must be inside the window", req.test(registry, 115_000L));
        if (!r.passed()) return r;
        return assertFalse("age 25s must be above the window (window is on the LAST fire)",
                req.test(registry, 125_000L));
    }

    /** notFired: unknown id true, fired id false, cleared id true again. */
    private TestResult test_notFired() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        NewsRequirement req = new NotFired("gold_standard");

        TestResult r = assertTrue("a never-fired event must satisfy notFired",
                req.test(registry, 1_000L));
        if (!r.passed()) return r;
        registry.recordFire("gold_standard", 500L, 1L);
        r = assertFalse("a fired event must fail notFired", req.test(registry, 1_000L));
        if (!r.passed()) return r;
        registry.clearEvent("gold_standard");
        return assertTrue("a cleared event must satisfy notFired again (T-099 clear op)",
                req.test(registry, 1_000L));
    }

    /** notFiredWithin is strictly-greater: exactly `seconds` ago is still WITHIN (false). */
    private TestResult test_notFiredWithin_boundary() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        NewsRequirement req = new NotFiredWithin("e", 60);

        TestResult r = assertTrue("a never-fired event must satisfy notFiredWithin",
                req.test(registry, 1_000_000L));
        if (!r.passed()) return r;
        registry.recordFire("e", 100_000L, 1L);
        r = assertFalse("a fire EXACTLY `seconds` ago is still within the window (strict >)",
                req.test(registry, 100_000L + 60_000L));
        if (!r.passed()) return r;
        r = assertTrue("a fire 1 ms older than the window must pass",
                req.test(registry, 100_000L + 60_001L));
        if (!r.passed()) return r;
        return assertFalse("a fresh fire must fail", req.test(registry, 100_000L + 1_000L));
    }

    /** countAtLeast: unknown id counts as 0; boundary equality passes. */
    private TestResult test_countAtLeast() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 1_000L, 1L);
        registry.recordFire("e", 2_000L, 1L);

        TestResult r = assertTrue("count 2 must satisfy countAtLeast 2 (inclusive)",
                new CountAtLeast("e", 2).test(registry, 5_000L));
        if (!r.passed()) return r;
        r = assertFalse("count 2 must fail countAtLeast 3",
                new CountAtLeast("e", 3).test(registry, 5_000L));
        if (!r.passed()) return r;
        r = assertFalse("an unknown id (count 0) must fail countAtLeast 1",
                new CountAtLeast("unknown", 1).test(registry, 5_000L));
        if (!r.passed()) return r;
        return assertTrue("countAtLeast 0 is trivially true, even for unknown ids",
                new CountAtLeast("unknown", 0).test(registry, 5_000L));
    }

    /** countAtMost: unknown id counts as 0; boundary equality passes. */
    private TestResult test_countAtMost() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("e", 1_000L, 1L);
        registry.recordFire("e", 2_000L, 1L);

        TestResult r = assertTrue("count 2 must satisfy countAtMost 2 (inclusive)",
                new CountAtMost("e", 2).test(registry, 5_000L));
        if (!r.passed()) return r;
        r = assertFalse("count 2 must fail countAtMost 1",
                new CountAtMost("e", 1).test(registry, 5_000L));
        if (!r.passed()) return r;
        return assertTrue("an unknown id (count 0) must satisfy countAtMost 0",
                new CountAtMost("unknown", 0).test(registry, 5_000L));
    }

    /** keyEquals: match true, mismatch false, ABSENT key false (documented semantics). */
    private TestResult test_keyEquals_absentIsFalse() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "gold_standard");

        TestResult r = assertTrue("matching value must pass",
                new KeyEquals("era", "gold_standard").test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("different value must fail",
                new KeyEquals("era", "fiat").test(registry, 0L));
        if (!r.passed()) return r;
        return assertFalse("an ABSENT key must fail keyEquals",
                new KeyEquals("missing", "anything").test(registry, 0L));
    }

    /** keyNotEquals: mismatch true, match false, ABSENT key TRUE (documented semantics). */
    private TestResult test_keyNotEquals_absentIsTrue() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "gold_standard");

        TestResult r = assertTrue("different value must pass",
                new KeyNotEquals("era", "fiat").test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("matching value must fail",
                new KeyNotEquals("era", "gold_standard").test(registry, 0L));
        if (!r.passed()) return r;
        return assertTrue("an ABSENT key must PASS keyNotEquals (never written = not that value)",
                new KeyNotEquals("missing", "anything").test(registry, 0L));
    }

    /** keyExists/keyAbsent are exact mirrors over key presence. */
    private TestResult test_keyExistsAndKeyAbsent() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("era", "fiat");

        TestResult r = assertTrue("existing key must satisfy keyExists",
                new KeyExists("era").test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("missing key must fail keyExists",
                new KeyExists("missing").test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("existing key must fail keyAbsent",
                new KeyAbsent("era").test(registry, 0L));
        if (!r.passed()) return r;
        return assertTrue("missing key must satisfy keyAbsent",
                new KeyAbsent("missing").test(registry, 0L));
    }

    /** keyAtLeast: inclusive boundary; unparseable stored value and absent key are false. */
    private TestResult test_keyAtLeast_boundaryAndUnparseable() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("level", "5");
        registry.putValue("era", "gold_standard"); // not a number

        TestResult r = assertTrue("stored 5 must satisfy >= 5 (inclusive)",
                new KeyAtLeast("level", 5.0).test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("stored 5 must fail >= 5.1",
                new KeyAtLeast("level", 5.1).test(registry, 0L));
        if (!r.passed()) return r;
        r = assertTrue("stored 5 must satisfy >= 4.9",
                new KeyAtLeast("level", 4.9).test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("an UNPARSEABLE stored value must fail a numeric bound",
                new KeyAtLeast("era", 0.0).test(registry, 0L));
        if (!r.passed()) return r;
        return assertFalse("an ABSENT key must fail keyAtLeast",
                new KeyAtLeast("missing", 0.0).test(registry, 0L));
    }

    /** keyAtMost: inclusive boundary; unparseable stored value and absent key are false. */
    private TestResult test_keyAtMost_boundaryAndUnparseable() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.putValue("level", "5");
        registry.putValue("era", "gold_standard");

        TestResult r = assertTrue("stored 5 must satisfy <= 5 (inclusive)",
                new KeyAtMost("level", 5.0).test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("stored 5 must fail <= 4.9",
                new KeyAtMost("level", 4.9).test(registry, 0L));
        if (!r.passed()) return r;
        r = assertFalse("an UNPARSEABLE stored value must fail a numeric bound",
                new KeyAtMost("era", 1_000_000.0).test(registry, 0L));
        if (!r.passed()) return r;
        return assertFalse("an ABSENT key must fail keyAtMost",
                new KeyAtMost("missing", 1_000_000.0).test(registry, 0L));
    }

    // ========================================================================
    // Composition helpers
    // ========================================================================

    /** allMet: null/empty lists are trivially met; one failing entry flips the result. */
    private TestResult test_allMet_composition() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("a", 1_000L, 1L);
        registry.putValue("era", "fiat");

        TestResult r = assertTrue("a null requirement list must be trivially met",
                NewsRequirement.allMet(null, registry, 5_000L));
        if (!r.passed()) return r;
        r = assertTrue("an empty requirement list must be trivially met",
                NewsRequirement.allMet(List.of(), registry, 5_000L));
        if (!r.passed()) return r;
        r = assertTrue("all-passing requirements must be met (AND semantics)",
                NewsRequirement.allMet(List.of(
                        new FiredBefore("a", 1, null, null),
                        new KeyEquals("era", "fiat")), registry, 5_000L));
        if (!r.passed()) return r;
        return assertFalse("one failing requirement must fail the whole conjunction",
                NewsRequirement.allMet(List.of(
                        new FiredBefore("a", 1, null, null),
                        new NotFired("a")), registry, 5_000L));
    }

    /** unmet: exactly the failing entries, in list order (the T-100 popup content). */
    private TestResult test_unmet_returnsFailingInOrder() {
        NewsWorldRegistry registry = new NewsWorldRegistry();
        registry.recordFire("a", 1_000L, 1L);

        NewsRequirement failing1 = new NotFired("a");              // fails: a fired
        NewsRequirement passing = new CountAtLeast("a", 1);        // passes
        NewsRequirement failing2 = new KeyEquals("era", "fiat");   // fails: key absent
        List<NewsRequirement> unmet = NewsRequirement.unmet(
                List.of(failing1, passing, failing2), registry, 5_000L);

        TestResult r = assertEquals("exactly the two failing requirements must be returned",
                2, unmet.size());
        if (!r.passed()) return r;
        r = assertEquals("first failing entry must keep list order", failing1, unmet.get(0));
        if (!r.passed()) return r;
        r = assertEquals("second failing entry must keep list order", failing2, unmet.get(1));
        if (!r.passed()) return r;
        r = assertEquals("no failing entries must yield an empty list",
                0, NewsRequirement.unmet(List.of(passing), registry, 5_000L).size());
        if (!r.passed()) return r;
        return assertEquals("a null list must yield an empty list",
                0, NewsRequirement.unmet(null, registry, 5_000L).size());
    }

    // ========================================================================
    // describe()
    // ========================================================================

    /** Every predicate type must render a non-empty description (T-099/T-100 UI). */
    private TestResult test_describe_nonEmptyForEveryType() {
        NewsRequirement[] all = {
                new FiredBefore("gold_standard", 1, 86_400L, null),
                new FiredBefore("gold_standard", 2, 10L, 20L),
                new NotFired("gold_standard"),
                new NotFiredWithin("gold_standard", 3_600),
                new CountAtLeast("gold_standard", 3),
                new CountAtMost("gold_standard", 3),
                new KeyEquals("era", "gold_standard"),
                new KeyNotEquals("era", "fiat"),
                new KeyExists("era"),
                new KeyAbsent("era"),
                new KeyAtLeast("level", 3.0),
                new KeyAtMost("level", 3.5),
        };
        for (NewsRequirement requirement : all) {
            String text = requirement.describe();
            if (text == null || text.isBlank()) {
                return fail("describe() must be non-empty for "
                        + requirement.getClass().getSimpleName() + ", got: '" + text + "'");
            }
        }
        // Spot-check the documented example rendering from the task/plan.
        return assertEquals("firedBefore rendering must match the documented example",
                "fired before: gold_standard, at least 86400s ago", all[0].describe());
    }

    // ========================================================================
    // requires[] parsing
    // ========================================================================

    /** The gold-standard pair from plan §1.2 must parse with its requirements + records. */
    private TestResult test_parse_goldStandardPair() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition gold = parseEvent("{\"id\":\"gold_standard\","
                + "\"headline\":\"H\",\"text\":\"T\",\"adminOnly\":false,\"weight\":1,"
                + "\"impact\":{\"type\":\"trend\",\"peakFactor\":0.8,\"reversal\":\"none\"},"
                + "\"markets\":[{\"item\":\"minecraft:gold_ingot\"}],"
                + "\"requires\":[{\"type\":\"notFired\",\"eventId\":\"gold_standard\"}],"
                + "\"records\":{\"era\":\"gold_standard\"}}", report);
        NewsEventDefinition end = parseEvent("{\"id\":\"end_of_gold_standard\","
                + "\"headline\":\"H\",\"text\":\"T\","
                + "\"impact\":{\"type\":\"crash\",\"peakFactor\":-0.4},"
                + "\"markets\":[{\"item\":\"minecraft:gold_ingot\"}],"
                + "\"requires\":["
                + "{\"type\":\"firedBefore\",\"eventId\":\"gold_standard\",\"minSecondsAgo\":86400},"
                + "{\"type\":\"notFired\",\"eventId\":\"end_of_gold_standard\"},"
                + "{\"type\":\"keyEquals\",\"key\":\"era\",\"value\":\"gold_standard\"}],"
                + "\"records\":{\"era\":\"fiat\"}}", report);

        TestResult r = assertEquals("the pair must parse without errors: " + report,
                0, report.errorCount());
        if (!r.passed()) return r;
        r = assertNotNull("gold_standard must load", gold);
        if (!r.passed()) return r;
        r = assertNotNull("end_of_gold_standard must load", end);
        if (!r.passed()) return r;

        r = assertEquals("gold_standard must have 1 requirement", 1, gold.getRequirements().size());
        if (!r.passed()) return r;
        if (!(gold.getRequirements().get(0) instanceof NotFired notFired)
                || !"gold_standard".equals(notFired.eventId())) {
            return fail("gold_standard requirement must be notFired(gold_standard), got: "
                    + gold.getRequirements().get(0));
        }
        r = assertEquals("gold_standard must record era=gold_standard",
                "gold_standard", gold.getRecords().get("era"));
        if (!r.passed()) return r;

        r = assertEquals("end event must have 3 requirements", 3, end.getRequirements().size());
        if (!r.passed()) return r;
        if (!(end.getRequirements().get(0) instanceof FiredBefore firedBefore)
                || !"gold_standard".equals(firedBefore.eventId())
                || firedBefore.minTimes() != 1 // default when omitted
                || !Long.valueOf(86_400L).equals(firedBefore.minSecondsAgo())
                || firedBefore.maxSecondsAgo() != null) {
            return fail("requirement 0 must be firedBefore(gold_standard, minTimes 1,"
                    + " minSecondsAgo 86400), got: " + end.getRequirements().get(0));
        }
        if (!(end.getRequirements().get(1) instanceof NotFired)) {
            return fail("requirement 1 must be notFired, got: " + end.getRequirements().get(1));
        }
        if (!(end.getRequirements().get(2) instanceof KeyEquals keyEquals)
                || !"era".equals(keyEquals.key()) || !"gold_standard".equals(keyEquals.value())) {
            return fail("requirement 2 must be keyEquals(era, gold_standard), got: "
                    + end.getRequirements().get(2));
        }
        return assertEquals("end event must record era=fiat", "fiat", end.getRecords().get("era"));
    }

    /** Plan §10: an unknown requirement type is an ERROR and the whole event is skipped. */
    private TestResult test_parse_unknownType_isErrorEventSkipped() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(
                "\"requires\":[{\"type\":\"fullMoonTonight\"}]"), report);

        TestResult r = assertNull("an unenforceable requirement must skip the event", def);
        if (!r.passed()) return r;
        return assertTrue("an ERROR must be recorded for the unknown type: " + report,
                report.errorCount() >= 1);
    }

    /** Missing required fields (eventId / value / seconds / count) are ERRORs. */
    private TestResult test_parse_missingFields_areErrors() {
        String[] broken = {
                "{\"type\":\"firedBefore\"}",                        // no eventId
                "{\"type\":\"keyEquals\",\"key\":\"era\"}",          // no value
                "{\"type\":\"notFiredWithin\",\"eventId\":\"e\"}",   // no seconds
                "{\"type\":\"countAtLeast\",\"eventId\":\"e\"}",     // no count
                "{\"type\":\"keyExists\"}",                          // no key
                "{\"type\":\"keyAtLeast\",\"key\":\"k\"}",           // no value
        };
        for (String json : broken) {
            ValidationReport report = new ValidationReport();
            NewsRequirement requirement = parseRequirement(json, report);
            if (requirement != null) {
                return fail("requirement must not parse with a missing field: " + json);
            }
            if (report.errorCount() != 1) {
                return fail("exactly one ERROR expected for " + json + ", got: " + report);
            }
        }
        return pass("every missing-field variant was rejected with exactly one ERROR");
    }

    /** Negative seconds/counts/minTimes are ERRORs (plan §7 style). */
    private TestResult test_parse_negativeValues_areErrors() {
        String[] broken = {
                "{\"type\":\"notFiredWithin\",\"eventId\":\"e\",\"seconds\":-1}",
                "{\"type\":\"firedBefore\",\"eventId\":\"e\",\"minSecondsAgo\":-5}",
                "{\"type\":\"firedBefore\",\"eventId\":\"e\",\"maxSecondsAgo\":-5}",
                "{\"type\":\"firedBefore\",\"eventId\":\"e\",\"minTimes\":-1}",
                "{\"type\":\"countAtLeast\",\"eventId\":\"e\",\"count\":-2}",
                "{\"type\":\"countAtMost\",\"eventId\":\"e\",\"count\":-2}",
                "{\"type\":\"countAtLeast\",\"eventId\":\"e\",\"count\":1.5}", // fractional count
        };
        for (String json : broken) {
            ValidationReport report = new ValidationReport();
            NewsRequirement requirement = parseRequirement(json, report);
            if (requirement != null) {
                return fail("requirement must not parse with an invalid number: " + json);
            }
            if (report.errorCount() != 1) {
                return fail("exactly one ERROR expected for " + json + ", got: " + report);
            }
        }
        return pass("every invalid-number variant was rejected with exactly one ERROR");
    }

    /** Unknown keys INSIDE a requirement object are WARNINGs; the requirement still parses. */
    private TestResult test_parse_unknownInnerKey_warns() {
        ValidationReport report = new ValidationReport();
        NewsRequirement requirement = parseRequirement(
                "{\"type\":\"notFired\",\"eventId\":\"e\",\"typo_field\":1}", report);

        TestResult r = assertNotNull("the requirement must still parse", requirement);
        if (!r.passed()) return r;
        r = assertEquals("no ERROR must be recorded", 0, report.errorCount());
        if (!r.passed()) return r;
        return assertTrue("a WARNING must flag the unknown key: " + report,
                report.warningCount() >= 1);
    }

    /** A non-array 'requires' value is an ERROR and skips the event. */
    private TestResult test_parse_requiresNotArray_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"requires\":{}"), report);

        TestResult r = assertNull("event must be skipped", def);
        if (!r.passed()) return r;
        return assertTrue("an ERROR must be recorded: " + report, report.errorCount() >= 1);
    }

    /** A non-object entry inside 'requires' is an ERROR and skips the event. */
    private TestResult test_parse_requiresEntryNotObject_isError() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"requires\":[42]"), report);

        TestResult r = assertNull("event must be skipped", def);
        if (!r.passed()) return r;
        return assertTrue("an ERROR must be recorded: " + report, report.errorCount() >= 1);
    }

    /** keyAtLeast 'value' accepts a number or a numeric string; unparseable is an ERROR. */
    private TestResult test_parse_keyAtLeast_valueForms() {
        ValidationReport report = new ValidationReport();
        NewsRequirement numeric = parseRequirement(
                "{\"type\":\"keyAtLeast\",\"key\":\"k\",\"value\":3.5}", report);
        NewsRequirement numericString = parseRequirement(
                "{\"type\":\"keyAtMost\",\"key\":\"k\",\"value\":\"42\"}", report);

        TestResult r = assertEquals("both forms must parse without errors: " + report,
                0, report.errorCount());
        if (!r.passed()) return r;
        if (!(numeric instanceof KeyAtLeast atLeast) || atLeast.value() != 3.5) {
            return fail("JSON number form must parse to keyAtLeast(3.5), got: " + numeric);
        }
        if (!(numericString instanceof KeyAtMost atMost) || atMost.value() != 42.0) {
            return fail("numeric-string form must parse to keyAtMost(42), got: " + numericString);
        }

        ValidationReport badReport = new ValidationReport();
        NewsRequirement bad = parseRequirement(
                "{\"type\":\"keyAtLeast\",\"key\":\"k\",\"value\":\"lots\"}", badReport);
        r = assertNull("an unparseable expected value must not parse", bad);
        if (!r.passed()) return r;
        return assertEquals("exactly one ERROR expected: " + badReport, 1, badReport.errorCount());
    }

    /** firedBefore with minSecondsAgo > maxSecondsAgo parses but WARNs (never satisfiable). */
    private TestResult test_parse_invertedAgeWindow_warns() {
        ValidationReport report = new ValidationReport();
        NewsRequirement requirement = parseRequirement(
                "{\"type\":\"firedBefore\",\"eventId\":\"e\",\"minSecondsAgo\":100,\"maxSecondsAgo\":50}",
                report);

        TestResult r = assertNotNull("the requirement must still parse", requirement);
        if (!r.passed()) return r;
        r = assertEquals("no ERROR must be recorded", 0, report.errorCount());
        if (!r.passed()) return r;
        return assertTrue("a WARNING must flag the inverted window: " + report,
                report.warningCount() >= 1);
    }

    /** Events without requires/records expose empty (never null) accessors. */
    private TestResult test_parse_noRequiresNoRecords_defaults() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(""), report);

        TestResult r = assertNotNull("the minimal event must parse", def);
        if (!r.passed()) return r;
        r = assertNotNull("getRequirements() must never be null", def.getRequirements());
        if (!r.passed()) return r;
        r = assertEquals("requirements must default to empty", 0, def.getRequirements().size());
        if (!r.passed()) return r;
        r = assertNotNull("getRecords() must never be null", def.getRecords());
        if (!r.passed()) return r;
        return assertEquals("records must default to empty", 0, def.getRecords().size());
    }

    // ========================================================================
    // records{} parsing
    // ========================================================================

    /** A valid records object parses into an insertion-ordered string map. */
    private TestResult test_parse_recordsMap_happyPath() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(
                "\"records\":{\"era\":\"fiat\",\"counter\":\"42\"}"), report);

        TestResult r = assertNotNull("the event must parse", def);
        if (!r.passed()) return r;
        r = assertEquals("no errors expected: " + report, 0, report.errorCount());
        if (!r.passed()) return r;
        r = assertEquals("both pairs must be parsed", 2, def.getRecords().size());
        if (!r.passed()) return r;
        r = assertEquals("string value must round-trip", "fiat", def.getRecords().get("era"));
        if (!r.passed()) return r;
        r = assertEquals("numeric-string value must stay a string",
                "42", def.getRecords().get("counter"));
        if (!r.passed()) return r;
        // Insertion order matters for stable REGISTRY_LIST rendering downstream.
        return assertEquals("insertion order must be preserved",
                "era", def.getRecords().keySet().iterator().next());
    }

    /** A non-string records value is a NON-FATAL error: entry skipped, event kept. */
    private TestResult test_parse_recordsNonString_entrySkippedEventKept() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(
                "\"records\":{\"era\":\"fiat\",\"bad\":42}"), report);

        TestResult r = assertNotNull("the event must be KEPT despite the bad entry", def);
        if (!r.passed()) return r;
        r = assertTrue("an ERROR must be recorded for the bad entry: " + report,
                report.errorCount() >= 1);
        if (!r.passed()) return r;
        r = assertEquals("only the valid pair must survive", 1, def.getRecords().size());
        if (!r.passed()) return r;
        r = assertEquals("the valid pair must survive intact", "fiat", def.getRecords().get("era"));
        if (!r.passed()) return r;
        return assertNull("the bad entry must be skipped", def.getRecords().get("bad"));
    }

    /** A non-object 'records' value is a NON-FATAL error: field ignored, event kept. */
    private TestResult test_parse_recordsNotObject_fieldIgnoredEventKept() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"records\":[\"era\"]"), report);

        TestResult r = assertNotNull("the event must be KEPT despite the bad field", def);
        if (!r.passed()) return r;
        r = assertTrue("an ERROR must be recorded: " + report, report.errorCount() >= 1);
        if (!r.passed()) return r;
        return assertEquals("the records map must stay empty", 0, def.getRecords().size());
    }

    /** Registry cap breaches (key/value length) are parse-time WARNINGs, entries kept. */
    private TestResult test_parse_recordsCapWarnings() {
        String longKey = "k".repeat(NewsWorldRegistry.MAX_KEY_LENGTH + 1);
        String longValue = "v".repeat(NewsWorldRegistry.MAX_VALUE_LENGTH + 1);
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(
                "\"records\":{\"" + longKey + "\":\"x\",\"ok\":\"" + longValue + "\"}"), report);

        TestResult r = assertNotNull("the event must parse (caps are advisory here)", def);
        if (!r.passed()) return r;
        r = assertEquals("cap breaches must not be errors at parse time: " + report,
                0, report.errorCount());
        if (!r.passed()) return r;
        r = assertTrue("one WARNING per cap breach expected: " + report,
                report.warningCount() >= 2);
        if (!r.passed()) return r;
        // The entries stay in the map — the WRITE refusal happens at publish (T-098).
        return assertEquals("both entries must be kept for the T-098 publish attempt",
                2, def.getRecords().size());
    }
}
