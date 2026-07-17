package net.kroia.stockmarket.news;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One trigger requirement of a news event — an entry of the optional
 * {@code "requires": [...]} array of an event definition (sequences plan §3, task T-097).
 * <p>
 * A requirement is a <b>pure predicate</b> over the {@link NewsWorldRegistry} (the
 * master-side long-term world memory) and the current wall-clock time: no side effects,
 * no Minecraft context, fully unit-testable. An event with requirements is only eligible
 * to fire when <b>ALL</b> of them hold ({@link #allMet}) — evaluated by the T-098
 * eligibility filter in {@code computeEligibleEvents} (planning AND fire time) and again
 * when a chain firing resolves. The T-100 admin trigger confirmation popup lists the
 * currently failing entries via {@link #unmet} rendered through {@link #describe()}.
 * <p>
 * <b>Time basis (plan §10.6):</b> all age predicates compare against wall-clock epoch
 * milliseconds ({@code nowEpochMs}, matching {@link NewsWorldRegistry.FireInfo}
 * timestamps) — registry ages keep growing while the server is offline, unlike event
 * cooldowns which tick on active-server time.
 * <p>
 * <b>Parse contract (plan §7 / §10):</b> {@link #parse} follows the skip-and-continue
 * style of {@link NewsEventDefinition}: every problem is recorded in the
 * {@link ValidationReport}; an unusable entry returns null after recording an ERROR.
 * Requirement ERRORs are <b>fatal for the event</b> (the caller skips it) — most
 * importantly an unknown {@code type}: an unenforceable requirement must never
 * silently pass.
 * <p>
 * The implementations are the sealed set of records below — one per predicate type of
 * the vocabulary: {@code firedBefore}, {@code notFired}, {@code notFiredWithin},
 * {@code countAtLeast}, {@code countAtMost}, {@code keyEquals}, {@code keyNotEquals},
 * {@code keyExists}, {@code keyAbsent}, {@code keyAtLeast}, {@code keyAtMost}.
 */
public sealed interface NewsRequirement
        permits NewsRequirement.FiredBefore, NewsRequirement.NotFired,
        NewsRequirement.NotFiredWithin, NewsRequirement.CountAtLeast,
        NewsRequirement.CountAtMost, NewsRequirement.KeyEquals,
        NewsRequirement.KeyNotEquals, NewsRequirement.KeyExists,
        NewsRequirement.KeyAbsent, NewsRequirement.KeyAtLeast,
        NewsRequirement.KeyAtMost {

    // ── Known JSON keys per requirement type (unknown keys ⇒ WARNING) ────

    /** Keys of a {@code firedBefore} requirement object. */
    Set<String> KNOWN_FIRED_BEFORE_KEYS = Set.of("type", "eventId", "minTimes",
            "minSecondsAgo", "maxSecondsAgo");
    /** Keys of a {@code notFired} requirement object. */
    Set<String> KNOWN_NOT_FIRED_KEYS = Set.of("type", "eventId");
    /** Keys of a {@code notFiredWithin} requirement object. */
    Set<String> KNOWN_NOT_FIRED_WITHIN_KEYS = Set.of("type", "eventId", "seconds");
    /** Keys of a {@code countAtLeast}/{@code countAtMost} requirement object. */
    Set<String> KNOWN_COUNT_KEYS = Set.of("type", "eventId", "count");
    /** Keys of a {@code keyEquals}/{@code keyNotEquals}/{@code keyAtLeast}/{@code keyAtMost} object. */
    Set<String> KNOWN_KEY_VALUE_KEYS = Set.of("type", "key", "value");
    /** Keys of a {@code keyExists}/{@code keyAbsent} requirement object. */
    Set<String> KNOWN_KEY_ONLY_KEYS = Set.of("type", "key");

    // ── Predicate contract ───────────────────────────────────────────────

    /**
     * Evaluates this requirement against the world registry — pure, no side effects.
     *
     * @param registry   the world-event registry to read (never null; the caller —
     *                   T-098 eligibility filter / chain fire / T-099 requirement
     *                   status rendering — always has the DataManager registry)
     * @param nowEpochMs the current wall-clock time in epoch milliseconds
     *                   ({@code System.currentTimeMillis()}; injected for testability)
     * @return true if the requirement holds right now
     */
    boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs);

    /**
     * @return a short human-readable English description of this requirement, e.g.
     *         {@code "fired before: gold_standard, at least 86400s ago"} — rendered
     *         to admins by T-099 (EventDetails INFO) and the T-100 trigger
     *         confirmation popup (unmet-requirements list); never null or blank
     */
    @NotNull String describe();

    // ── Composition helpers (T-098 eligibility / T-100 popup) ────────────

    /**
     * @param requirements the event's requirement list (null or empty = no
     *                     requirements = always eligible)
     * @param registry     the world-event registry to read (never null)
     * @param nowEpochMs   the current wall-clock time in epoch milliseconds
     * @return true if ALL requirements hold (plan §3: requirements are ANDed)
     */
    static boolean allMet(@Nullable List<NewsRequirement> requirements,
                          @NotNull NewsWorldRegistry registry, long nowEpochMs) {
        if (requirements == null || requirements.isEmpty()) return true;
        for (NewsRequirement requirement : requirements) {
            if (!requirement.test(registry, nowEpochMs)) return false;
        }
        return true;
    }

    /**
     * Collects the currently failing requirements — the list the T-100 admin trigger
     * confirmation popup shows (rendered via {@link #describe()}).
     *
     * @param requirements the event's requirement list (null or empty = nothing unmet)
     * @param registry     the world-event registry to read (never null)
     * @param nowEpochMs   the current wall-clock time in epoch milliseconds
     * @return the unmet requirements in list order (never null, possibly empty,
     *         unmodifiable)
     */
    static @NotNull List<NewsRequirement> unmet(@Nullable List<NewsRequirement> requirements,
                                                @NotNull NewsWorldRegistry registry,
                                                long nowEpochMs) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        List<NewsRequirement> failing = new ArrayList<>();
        for (NewsRequirement requirement : requirements) {
            if (!requirement.test(registry, nowEpochMs)) failing.add(requirement);
        }
        return Collections.unmodifiableList(failing);
    }

    // ── Predicate implementations ────────────────────────────────────────

    /**
     * {@code firedBefore} — the referenced event fired at least {@code minTimes} times
     * (default 1), optionally within an age window on its LAST fire.
     * <p>
     * Semantics: a <b>never-fired</b> event always fails this requirement, regardless
     * of {@code minTimes} (even 0). With {@code minSecondsAgo} set, the last fire must
     * be at least that old ({@code nowEpochMs - lastFiredEpochMs >= minSecondsAgo * 1000},
     * boundary inclusive); with {@code maxSecondsAgo} set, at most that old (inclusive).
     *
     * @param eventId       the event id whose fire record is checked (never null/blank)
     * @param minTimes      minimum fire count (parse default 1; non-negative)
     * @param minSecondsAgo optional minimum age of the last fire in seconds, or null
     * @param maxSecondsAgo optional maximum age of the last fire in seconds, or null
     */
    record FiredBefore(String eventId, int minTimes,
                       @Nullable Long minSecondsAgo,
                       @Nullable Long maxSecondsAgo) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public FiredBefore {
            Objects.requireNonNull(eventId, "eventId");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            NewsWorldRegistry.FireInfo info = registry.getFireInfo(eventId);
            if (info == null) return false; // never fired ⇒ always false
            if (info.fireCount() < minTimes) return false;
            long ageMs = nowEpochMs - info.lastFiredEpochMs();
            if (minSecondsAgo != null && ageMs < secondsToMillisSaturated(minSecondsAgo)) return false;
            if (maxSecondsAgo != null && ageMs > secondsToMillisSaturated(maxSecondsAgo)) return false;
            return true;
        }

        @Override
        public @NotNull String describe() {
            StringBuilder sb = new StringBuilder("fired before: ").append(eventId);
            if (minTimes != 1) {
                sb.append(", at least ").append(minTimes).append(minTimes == 1 ? " time" : " times");
            }
            if (minSecondsAgo != null) sb.append(", at least ").append(minSecondsAgo).append("s ago");
            if (maxSecondsAgo != null) sb.append(", at most ").append(maxSecondsAgo).append("s ago");
            return sb.toString();
        }
    }

    /**
     * {@code notFired} — the referenced event never fired (fire count 0, i.e. no
     * registry record; a record cleared via the T-099 {@code REGISTRY_CLEAR} admin op
     * counts as never fired again).
     *
     * @param eventId the event id whose fire record is checked (never null/blank)
     */
    record NotFired(String eventId) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public NotFired {
            Objects.requireNonNull(eventId, "eventId");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return registry.fireCount(eventId) == 0;
        }

        @Override
        public @NotNull String describe() {
            return "never fired: " + eventId;
        }
    }

    /**
     * {@code notFiredWithin} — the referenced event never fired at all OR its last fire
     * is <b>strictly older</b> than the given window
     * ({@code nowEpochMs - lastFiredEpochMs > seconds * 1000}; a fire exactly
     * {@code seconds} ago is still "within" and fails).
     *
     * @param eventId the event id whose fire record is checked (never null/blank)
     * @param seconds the window size in seconds (non-negative)
     */
    record NotFiredWithin(String eventId, long seconds) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public NotFiredWithin {
            Objects.requireNonNull(eventId, "eventId");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            NewsWorldRegistry.FireInfo info = registry.getFireInfo(eventId);
            if (info == null) return true; // never fired ⇒ trivially not fired within
            return nowEpochMs - info.lastFiredEpochMs() > secondsToMillisSaturated(seconds);
        }

        @Override
        public @NotNull String describe() {
            return "not fired within the last " + seconds + "s: " + eventId;
        }
    }

    /**
     * {@code countAtLeast} — the referenced event fired at least {@code count} times.
     * A never-fired event has count 0 (so {@code count = 0} is trivially true).
     *
     * @param eventId the event id whose fire count is checked (never null/blank)
     * @param count   the minimum fire count (non-negative)
     */
    record CountAtLeast(String eventId, int count) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public CountAtLeast {
            Objects.requireNonNull(eventId, "eventId");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return registry.fireCount(eventId) >= count;
        }

        @Override
        public @NotNull String describe() {
            return "fired at least " + count + (count == 1 ? " time: " : " times: ") + eventId;
        }
    }

    /**
     * {@code countAtMost} — the referenced event fired at most {@code count} times.
     * A never-fired event has count 0 (so this holds for any non-negative {@code count}).
     *
     * @param eventId the event id whose fire count is checked (never null/blank)
     * @param count   the maximum fire count (non-negative)
     */
    record CountAtMost(String eventId, int count) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public CountAtMost {
            Objects.requireNonNull(eventId, "eventId");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return registry.fireCount(eventId) <= count;
        }

        @Override
        public @NotNull String describe() {
            return "fired at most " + count + (count == 1 ? " time: " : " times: ") + eventId;
        }
    }

    /**
     * {@code keyEquals} — the custom registry key exists AND its stored value equals
     * the expected value exactly (case-sensitive string comparison).
     * <p>
     * <b>Absent-key semantics:</b> an absent key is <b>false</b> — "equals" requires
     * the key to exist. (The mirror {@link KeyNotEquals} is <b>true</b> for an absent
     * key: a value that was never written is trivially "not" the given value.)
     *
     * @param key   the custom registry key to read (never null/blank)
     * @param value the expected value (never null)
     */
    record KeyEquals(String key, String value) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyEquals {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return value.equals(registry.getValue(key));
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' is '" + value + "'";
        }
    }

    /**
     * {@code keyNotEquals} — the custom registry key is absent OR its stored value
     * differs from the given value (case-sensitive string comparison).
     * <p>
     * <b>Absent-key semantics:</b> an absent key is <b>true</b> — a value that was
     * never written is trivially "not" the given value. (The mirror {@link KeyEquals}
     * is <b>false</b> for an absent key.) Use {@link KeyExists} in addition when the
     * key must have been written.
     *
     * @param key   the custom registry key to read (never null/blank)
     * @param value the forbidden value (never null)
     */
    record KeyNotEquals(String key, String value) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyNotEquals {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return !value.equals(registry.getValue(key));
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' is not '" + value + "'";
        }
    }

    /**
     * {@code keyExists} — the custom registry key has been written (any value).
     *
     * @param key the custom registry key to check (never null/blank)
     */
    record KeyExists(String key) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyExists {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return registry.getValue(key) != null;
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' exists";
        }
    }

    /**
     * {@code keyAbsent} — the custom registry key has never been written (or was
     * cleared via the T-099 {@code REGISTRY_CLEAR} admin op).
     *
     * @param key the custom registry key to check (never null/blank)
     */
    record KeyAbsent(String key) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyAbsent {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            return registry.getValue(key) == null;
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' is absent";
        }
    }

    /**
     * {@code keyAtLeast} — the stored value of the custom registry key, parsed as a
     * double, is {@code >=} the expected number (boundary inclusive).
     * <p>
     * <b>Absent/unparseable semantics:</b> an absent key is <b>false</b>, and a stored
     * value that does not parse as a finite double (e.g. {@code "gold_standard"}) is
     * also <b>false</b> — a non-numeric value can never satisfy a numeric bound.
     *
     * @param key   the custom registry key to read (never null/blank)
     * @param value the inclusive lower bound (finite; the JSON accepts a number or a
     *              numeric string, parsed once at parse time)
     */
    record KeyAtLeast(String key, double value) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyAtLeast {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            Double stored = parseStoredNumber(registry.getValue(key));
            return stored != null && stored >= value;
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' is at least " + formatNumber(value);
        }
    }

    /**
     * {@code keyAtMost} — the stored value of the custom registry key, parsed as a
     * double, is {@code <=} the expected number (boundary inclusive).
     * <p>
     * <b>Absent/unparseable semantics:</b> identical to {@link KeyAtLeast} — an absent
     * key or a stored value that does not parse as a finite double is <b>false</b>.
     *
     * @param key   the custom registry key to read (never null/blank)
     * @param value the inclusive upper bound (finite; the JSON accepts a number or a
     *              numeric string, parsed once at parse time)
     */
    record KeyAtMost(String key, double value) implements NewsRequirement {

        /** Validates the T-098/T-100 construction contract (parse already guarantees it). */
        public KeyAtMost {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public boolean test(@NotNull NewsWorldRegistry registry, long nowEpochMs) {
            Double stored = parseStoredNumber(registry.getValue(key));
            return stored != null && stored <= value;
        }

        @Override
        public @NotNull String describe() {
            return "registry key '" + key + "' is at most " + formatNumber(value);
        }
    }

    // ── JSON parsing (plan §3 / §7, skip-and-continue) ───────────────────

    /**
     * Parses one {@code requires[]} entry.
     * <p>
     * Skip-and-continue contract (like {@link NewsEventDefinition#parse}): never throws;
     * all problems are recorded in the report. On any ERROR (unknown {@code type},
     * missing or wrong-typed field, negative seconds/counts) null is returned —
     * requirement ERRORs are fatal, the caller skips the whole event (plan §10:
     * an unenforceable requirement must never silently pass). Unknown keys inside the
     * requirement object are WARNINGs only.
     *
     * @param json    the requirement object from the {@code requires[]} array
     * @param file    source file name (for report entries)
     * @param eventId owning event id (for report entries)
     * @param report  collector for all problems found
     * @return the parsed requirement, or null if the entry had an ERROR
     */
    static @Nullable NewsRequirement parse(@NotNull JsonObject json, String file,
                                           @Nullable String eventId,
                                           @NotNull ValidationReport report) {
        String type = NewsEventDefinition.getString(json, "type");
        if (type == null || type.isBlank()) {
            report.addError(file, eventId,
                    "requires entry has no 'type' string — event skipped");
            return null;
        }
        type = type.trim();
        String label = "requires entry '" + type + "'";

        switch (type) {
            case "firedBefore": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_FIRED_BEFORE_KEYS, label,
                        file, eventId, report);
                String targetId = requireEventId(json, label, file, eventId, report);
                if (targetId == null) return null;

                int minTimes = 1; // plan §3: default 1
                if (json.has("minTimes")) {
                    Integer parsed = requireNonNegativeInt(json, "minTimes", label,
                            file, eventId, report);
                    if (parsed == null) return null;
                    minTimes = parsed;
                }
                Long minSecondsAgo = null;
                if (json.has("minSecondsAgo")) {
                    minSecondsAgo = requireNonNegativeSeconds(json, "minSecondsAgo", label,
                            file, eventId, report);
                    if (minSecondsAgo == null) return null;
                }
                Long maxSecondsAgo = null;
                if (json.has("maxSecondsAgo")) {
                    maxSecondsAgo = requireNonNegativeSeconds(json, "maxSecondsAgo", label,
                            file, eventId, report);
                    if (maxSecondsAgo == null) return null;
                }
                // A window with min > max can never be satisfied — the event loads but
                // is permanently ineligible; flag it so the author notices.
                if (minSecondsAgo != null && maxSecondsAgo != null
                        && minSecondsAgo > maxSecondsAgo) {
                    report.addWarning(file, eventId, label + ": 'minSecondsAgo' ("
                            + minSecondsAgo + ") > 'maxSecondsAgo' (" + maxSecondsAgo
                            + ") — this requirement can never be satisfied");
                }
                return new FiredBefore(targetId, minTimes, minSecondsAgo, maxSecondsAgo);
            }
            case "notFired": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_NOT_FIRED_KEYS, label,
                        file, eventId, report);
                String targetId = requireEventId(json, label, file, eventId, report);
                return targetId == null ? null : new NotFired(targetId);
            }
            case "notFiredWithin": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_NOT_FIRED_WITHIN_KEYS, label,
                        file, eventId, report);
                String targetId = requireEventId(json, label, file, eventId, report);
                if (targetId == null) return null;
                if (!json.has("seconds")) {
                    report.addError(file, eventId, label
                            + " needs a 'seconds' number — event skipped");
                    return null;
                }
                Long seconds = requireNonNegativeSeconds(json, "seconds", label,
                        file, eventId, report);
                return seconds == null ? null : new NotFiredWithin(targetId, seconds);
            }
            case "countAtLeast":
            case "countAtMost": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_COUNT_KEYS, label,
                        file, eventId, report);
                String targetId = requireEventId(json, label, file, eventId, report);
                if (targetId == null) return null;
                if (!json.has("count")) {
                    report.addError(file, eventId, label
                            + " needs a 'count' number — event skipped");
                    return null;
                }
                Integer count = requireNonNegativeInt(json, "count", label,
                        file, eventId, report);
                if (count == null) return null;
                return type.equals("countAtLeast")
                        ? new CountAtLeast(targetId, count)
                        : new CountAtMost(targetId, count);
            }
            case "keyEquals":
            case "keyNotEquals": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_KEY_VALUE_KEYS, label,
                        file, eventId, report);
                String key = requireKey(json, label, file, eventId, report);
                if (key == null) return null;
                String value = NewsEventDefinition.getString(json, "value");
                if (value == null) {
                    report.addError(file, eventId, label
                            + " needs a 'value' string — event skipped");
                    return null;
                }
                return type.equals("keyEquals")
                        ? new KeyEquals(key, value)
                        : new KeyNotEquals(key, value);
            }
            case "keyExists":
            case "keyAbsent": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_KEY_ONLY_KEYS, label,
                        file, eventId, report);
                String key = requireKey(json, label, file, eventId, report);
                if (key == null) return null;
                return type.equals("keyExists") ? new KeyExists(key) : new KeyAbsent(key);
            }
            case "keyAtLeast":
            case "keyAtMost": {
                NewsEventDefinition.warnUnknownKeys(json, KNOWN_KEY_VALUE_KEYS, label,
                        file, eventId, report);
                String key = requireKey(json, label, file, eventId, report);
                if (key == null) return null;
                Double value = requireFiniteNumberOrNumericString(json, "value", label,
                        file, eventId, report);
                if (value == null) return null;
                return type.equals("keyAtLeast")
                        ? new KeyAtLeast(key, value)
                        : new KeyAtMost(key, value);
            }
            default:
                // Plan §10: an UNKNOWN requirement type is an ERROR and the event is
                // skipped — an unenforceable requirement must never silently pass.
                report.addError(file, eventId, "requires entry has unknown type '" + type
                        + "' (firedBefore|notFired|notFiredWithin|countAtLeast|countAtMost"
                        + "|keyEquals|keyNotEquals|keyExists|keyAbsent|keyAtLeast|keyAtMost)"
                        + " — event skipped");
                return null;
        }
    }

    // ── Private parse helpers ────────────────────────────────────────────

    /** @return the required non-blank {@code eventId} string, or null after an ERROR */
    private static @Nullable String requireEventId(JsonObject json, String label, String file,
                                                   @Nullable String eventId,
                                                   ValidationReport report) {
        String value = NewsEventDefinition.getString(json, "eventId");
        if (value == null || value.isBlank()) {
            report.addError(file, eventId, label
                    + " needs a non-empty 'eventId' string — event skipped");
            return null;
        }
        return value.trim();
    }

    /** @return the required non-blank {@code key} string, or null after an ERROR */
    private static @Nullable String requireKey(JsonObject json, String label, String file,
                                               @Nullable String eventId,
                                               ValidationReport report) {
        String value = NewsEventDefinition.getString(json, "key");
        if (value == null || value.isBlank()) {
            report.addError(file, eventId, label
                    + " needs a non-empty 'key' string — event skipped");
            return null;
        }
        return value.trim();
    }

    /**
     * Reads a present member as a non-negative whole number (integer semantics —
     * fire counts). Fractional, non-finite, non-numeric or negative values are ERRORs.
     *
     * @return the value, or null after an ERROR
     */
    private static @Nullable Integer requireNonNegativeInt(JsonObject json, String member,
                                                           String label, String file,
                                                           @Nullable String eventId,
                                                           ValidationReport report) {
        Double value = NewsEventDefinition.getFiniteNumber(json, member);
        if (value == null || value < 0 || value != Math.floor(value)
                || value > Integer.MAX_VALUE) {
            report.addError(file, eventId, label + ": '" + member
                    + "' must be a non-negative whole number — event skipped");
            return null;
        }
        return value.intValue();
    }

    /**
     * Reads a present member as a non-negative duration in seconds. Fractional values
     * are rounded to the nearest whole second (sub-second age bounds are meaningless
     * for wall-clock world memory). Non-finite, non-numeric or negative values are ERRORs.
     *
     * @return the value in whole seconds, or null after an ERROR
     */
    private static @Nullable Long requireNonNegativeSeconds(JsonObject json, String member,
                                                            String label, String file,
                                                            @Nullable String eventId,
                                                            ValidationReport report) {
        Double value = NewsEventDefinition.getFiniteNumber(json, member);
        if (value == null || value < 0) {
            report.addError(file, eventId, label + ": '" + member
                    + "' must be a non-negative number of seconds — event skipped");
            return null;
        }
        return Math.round(value);
    }

    /**
     * Reads a present member as a finite number, accepting a JSON number OR a numeric
     * string (mirrors the registry's string value store, where numeric strings are the
     * convention for {@code keyAtLeast}/{@code keyAtMost}).
     *
     * @return the value, or null after an ERROR
     */
    private static @Nullable Double requireFiniteNumberOrNumericString(JsonObject json,
                                                                       String member,
                                                                       String label, String file,
                                                                       @Nullable String eventId,
                                                                       ValidationReport report) {
        Double value = NewsEventDefinition.getFiniteNumber(json, member);
        if (value == null) {
            // Fall back to a numeric string ("3.5") — the registry stores strings anyway.
            String raw = NewsEventDefinition.getString(json, member);
            value = parseStoredNumber(raw);
        }
        if (value == null) {
            report.addError(file, eventId, label + ": '" + member
                    + "' must be a finite number (or numeric string) — event skipped");
            return null;
        }
        return value;
    }

    /**
     * Parses a stored registry value as a finite double — the shared leniency of the
     * {@code keyAtLeast}/{@code keyAtMost} runtime checks and the numeric-string
     * fallback at parse time.
     *
     * @param stored the stored value (null-safe)
     * @return the parsed finite double, or null if absent/unparseable/non-finite
     */
    private static @Nullable Double parseStoredNumber(@Nullable String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            double value = Double.parseDouble(stored.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converts non-negative whole seconds to milliseconds, saturating at
     * {@link Long#MAX_VALUE} instead of overflowing on absurd author input.
     */
    private static long secondsToMillisSaturated(long seconds) {
        return seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
    }

    /** Formats a bound for {@link #describe()}: whole values without the ".0" tail. */
    private static @NotNull String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
