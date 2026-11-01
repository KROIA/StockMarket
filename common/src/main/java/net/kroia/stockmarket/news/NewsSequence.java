package net.kroia.stockmarket.news;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure-math multi-step price-influence sequence of a news event — the successor of
 * {@link NewsImpactEnvelope} (sequences plan §2). <b>No Minecraft dependencies</b> —
 * fully unit-testable outside a server context.
 * <p>
 * A sequence is an immutable list of <b>resolved</b> {@link Step}s: every step's
 * duration is already a concrete value (ranges are rolled at activation time by the
 * plugin, T-095 — this class never rolls anything). Each step interpolates the
 * influence value from its {@code startValue} (= previous step's {@code targetValue};
 * step 0 starts at 0) to its {@code targetValue} over its duration, using one of the
 * {@link Curve}s.
 * <p>
 * <b>Value semantics:</b> unlike the old envelope (normalized {@code [0,1]} × external
 * {@code peakFactor}), {@link #value(long)} returns the <b>signed influence level
 * directly</b> — e.g. {@code 0.3} = +30%, {@code -0.45} = -45%. The caller's factor
 * term becomes {@code priceMultiplier = 1 + value(t) * weightFactor * sensitivity}.
 * For normalized legacy events {@code value(t) == envelope.factor(t) * peakFactor}
 * holds exactly (see {@link #fromLegacyEnvelope(NewsImpactEnvelope)}).
 * <p>
 * <b>Time basis:</b> {@code activeMillis} is the accumulated "active milliseconds while
 * the server was ticking" of the event, <b>not</b> raw wall-clock time (same convention
 * as {@link NewsImpactEnvelope#factor(long)}). Values {@code < 0} (impact scheduled but
 * not yet started, e.g. a positive announce delay — the PENDING phase) yield value 0.
 * <p>
 * <b>End semantics:</b> once {@code activeMillis >= }{@link #totalDurationMs()} the
 * sequence has ended:
 * <ul>
 *   <li>{@link #isPermanent()} {@code == false}: {@link #value(long)} returns 0 —
 *       the influence snaps off (validation warns when the final value is non-zero,
 *       plan §7).</li>
 *   <li>{@link #isPermanent()} {@code == true} (last step flagged {@code permanent}):
 *       {@link #value(long)} keeps returning {@link #finalValue()} forever. The plugin
 *       must react by <b>baking the shift into the market's default price</b> and then
 *       retiring the event (same PERMANENT doctrine as {@code reversal:none} envelopes,
 *       see {@link NewsImpactEnvelope} class Javadoc).</li>
 * </ul>
 */
public final class NewsSequence {

    /**
     * Number of exponential time constants that fit into an {@link Curve#EXPONENTIAL}
     * step's duration ({@code tau = durationMs / 6}). Matches
     * {@link NewsImpactEnvelope#EXPONENTIAL_EXPIRY_TIME_CONSTANTS} ({@code e^-6} ≈ 0.25%
     * residual) so a normalized legacy exponential reversal is <b>equivalence-exact</b>:
     * the step end coincides with the envelope's hard expiry cutoff.
     */
    public static final double EXPONENTIAL_TIME_CONSTANTS =
            NewsImpactEnvelope.EXPONENTIAL_EXPIRY_TIME_CONSTANTS;

    /**
     * Interpolation curve of one step (JSON {@code curve} field, plan §1.1).
     */
    public enum Curve {
        /** Linear interpolation from {@code startValue} to {@code targetValue}. */
        LINEAR,
        /** Jump to {@code targetValue} at step start, then hold it for the whole step. */
        INSTANT,
        /**
         * Asymptotic exponential approach from {@code startValue} towards
         * {@code targetValue} with time constant {@code tau = durationMs /}
         * {@link #EXPONENTIAL_TIME_CONSTANTS}. The target is <b>not</b> reached exactly
         * at step end ({@code e^-6} ≈ 0.25% of the delta remains) — the next step (or
         * the sequence end) snaps to the target, mirroring the legacy envelope's hard
         * expiry cutoff.
         */
        EXPONENTIAL,
        /**
         * Keep the {@code startValue} for the whole step. {@code targetValue} is forced
         * to {@code startValue} at construction so the next step chains correctly.
         */
        HOLD;

        /**
         * Case-insensitive lookup of a curve by its JSON name.
         *
         * @param name the JSON value, e.g. {@code "exponential"}
         * @return the matching curve, or {@code null} if unknown
         */
        public static Curve fromString(String name) {
            if (name == null) return null;
            for (Curve curve : values()) {
                if (curve.name().equalsIgnoreCase(name)) return curve;
            }
            return null;
        }

        /** @return the lowercase JSON name of this curve (e.g. {@code "linear"}) */
        public String jsonName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One fully resolved step of a sequence. Instances are created exclusively by
     * {@link NewsSequence#create(List)} which precomputes every {@code startValue}
     * from the previous step's {@code targetValue} (step 0 starts at 0).
     *
     * @param name        the step name (shown in the UI, referenced by chains' {@code onStep})
     * @param durationMs  the <b>rolled</b> concrete step duration in milliseconds (≥ 0;
     *                    a 0-duration step is skipped by the time lookup and only
     *                    contributes its {@code targetValue} to the chain)
     * @param startValue  the influence level at step start — always the previous step's
     *                    {@code targetValue} (0 for step 0), precomputed at construction
     * @param targetValue the signed influence level ({@code 0.3} = +30%) this step reaches
     *                    at its end; {@code > -1} (sanitized at construction). For
     *                    {@link Curve#HOLD} forced to {@code startValue}
     * @param curve       how the value moves from start to target (never null)
     * @param noise       optional per-tick jitter amplitude on the influence while this
     *                    step is active (0 = none); applying it is the plugin's job,
     *                    the sequence itself stays deterministic
     * @param permanent   only meaningful (and only allowed) on the <b>last</b> step: at
     *                    sequence end the final value bakes into the market's default
     *                    price instead of snapping off (forced false on non-last steps)
     */
    public record Step(String name, long durationMs, double startValue, double targetValue,
                       Curve curve, double noise, boolean permanent) {
    }

    /**
     * Construction input for one step — a {@link Step} without the derived
     * {@code startValue} (which {@link #create(List)} computes from the chain).
     *
     * @param name        the step name (blank/null falls back to {@code "step<i>"})
     * @param durationMs  the rolled concrete duration in milliseconds (negative → 0)
     * @param targetValue the signed influence level at step end ({@code > -1}; sanitized
     *                    like {@link NewsImpactEnvelope}: non-finite → 0, {@code <= -1}
     *                    → clamped to -0.99). Ignored for {@link Curve#HOLD}
     * @param curve       the interpolation curve (null → {@link Curve#LINEAR})
     * @param noise       per-step jitter amplitude (non-finite/negative → 0)
     * @param permanent   permanent-shift flag; only honored on the last step
     */
    public record StepSpec(String name, long durationMs, double targetValue,
                           Curve curve, double noise, boolean permanent) {
    }

    // ── State ────────────────────────────────────────────────────────────

    /** Resolved steps in order (unmodifiable, never empty). */
    private final List<Step> steps;
    /**
     * Cumulative step starts: {@code stepStartsMs[i]} = active-millis at which step i
     * begins; index {@code stepCount()} holds {@link #totalDurationMs()}.
     */
    private final long[] stepStartsMs;
    private final long totalDurationMs;
    private final boolean permanent;

    private NewsSequence(List<Step> steps, long[] stepStartsMs) {
        this.steps = Collections.unmodifiableList(steps);
        this.stepStartsMs = stepStartsMs;
        this.totalDurationMs = stepStartsMs[stepStartsMs.length - 1];
        this.permanent = steps.get(steps.size() - 1).permanent();
    }

    // ── Factories ────────────────────────────────────────────────────────

    /**
     * Creates a sequence from resolved step specs (durations already rolled — rolling
     * ranges is the plugin's job at activation time, T-095).
     * <p>
     * Defensive like {@link NewsImpactEnvelope}: parse-time validation rejects invalid
     * definitions before this is called, but inputs are still sanitized here (see
     * {@link StepSpec}) so the math can never produce NaN/negative prices, and an
     * empty/null spec list yields a harmless single 0-duration step at value 0
     * instead of throwing.
     * <p>
     * Start values are precomputed: {@code startValue(i) = targetValue(i-1)},
     * {@code startValue(0) = 0}. {@link Curve#HOLD} steps get their
     * {@code targetValue} forced to their {@code startValue}; the {@code permanent}
     * flag is forced to false on every non-last step.
     *
     * @param specs the resolved step specs in order
     * @return the immutable sequence (never null)
     */
    public static NewsSequence create(List<StepSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            // Defensive fallback: a "null sequence" with zero influence and zero length.
            specs = List.of(new StepSpec("empty", 0L, 0.0, Curve.HOLD, 0.0, false));
        }
        List<Step> steps = new ArrayList<>(specs.size());
        long[] starts = new long[specs.size() + 1];
        double previousTarget = 0.0; // step 0 starts at 0 influence
        long cursor = 0;
        for (int i = 0; i < specs.size(); i++) {
            StepSpec spec = specs.get(i);
            String name = (spec.name() == null || spec.name().isBlank()) ? "step" + i : spec.name();
            long durationMs = Math.max(0L, spec.durationMs());
            Curve curve = spec.curve() != null ? spec.curve() : Curve.LINEAR;

            double target = spec.targetValue();
            if (!Double.isFinite(target)) target = 0.0;
            if (target <= -1.0) target = -0.99; // a factor of -1 would zero the price
            if (curve == Curve.HOLD) target = previousTarget; // hold keeps the level

            double noise = (Double.isFinite(spec.noise()) && spec.noise() > 0.0) ? spec.noise() : 0.0;
            boolean permanent = spec.permanent() && i == specs.size() - 1; // last step only

            starts[i] = cursor;
            steps.add(new Step(name, durationMs, previousTarget, target, curve, noise, permanent));
            previousTarget = target;
            cursor += durationMs;
        }
        starts[specs.size()] = cursor;
        return new NewsSequence(steps, starts);
    }

    /**
     * Normalizes a legacy {@link NewsImpactEnvelope} (JSON {@code impact} block) into
     * the equivalent implicit 3-step sequence — the single runtime code path of the
     * sequences plan (§1.1 legacy compat):
     * <ol>
     *   <li>{@code "ramp"} — {@link Curve#LINEAR} from 0 to {@code peakFactor} over the
     *       envelope's ramp-up time.</li>
     *   <li>{@code "hold"} — {@link Curve#HOLD} at {@code peakFactor} for the envelope's
     *       hold duration.</li>
     *   <li>Reversal step, depending on the envelope's {@link NewsImpactEnvelope.ReversalMode}:
     *     <ul>
     *       <li>{@code RAMP} → {@code "reversal"}, {@link Curve#LINEAR} to 0 over
     *           {@code reversalMillis}.</li>
     *       <li>{@code EXPONENTIAL} → {@code "reversal"}, {@link Curve#EXPONENTIAL} to 0
     *           over {@code reversalMillis ×}{@link #EXPONENTIAL_TIME_CONSTANTS} — the
     *           step's time constant is then exactly {@code reversalMillis} and the step
     *           end coincides with the envelope's hard expiry cutoff.</li>
     *       <li>{@code NONE} → {@code "permanent"}, a 0-duration {@link Curve#HOLD} step
     *           with {@code permanent:true} — the sequence ends where the envelope
     *           reports {@code PERMANENT} and the value stays at {@code peakFactor}
     *           forever.</li>
     *     </ul>
     *   </li>
     * </ol>
     * The envelope's single noise amplitude is applied to all three steps.
     * <p>
     * <b>Equivalence contract:</b> for every {@code t} (including negative pre-start
     * ages and times past the envelope's total length),
     * {@code fromLegacyEnvelope(env).value(t) == env.factor(t) * env.getPeakFactor()}
     * — bit-exact for the hold/exponential/permanent branches and within floating-point
     * associativity (≪ 1e-9) for the linear ramps. Also
     * {@code totalDurationMs() == env.totalLengthMillis()} for every reversal mode.
     *
     * @param envelope the parsed legacy impact envelope (never null)
     * @return the equivalent resolved sequence
     */
    public static NewsSequence fromLegacyEnvelope(NewsImpactEnvelope envelope) {
        double peak = envelope.getPeakFactor();
        double noise = envelope.getNoise();
        List<StepSpec> specs = new ArrayList<>(3);
        specs.add(new StepSpec("ramp", envelope.getRampUpMillis(), peak, Curve.LINEAR, noise, false));
        specs.add(new StepSpec("hold", envelope.getHoldMillis(), peak, Curve.HOLD, noise, false));
        switch (envelope.getReversal()) {
            case RAMP:
                specs.add(new StepSpec("reversal", envelope.getReversalMillis(), 0.0,
                        Curve.LINEAR, noise, false));
                break;
            case EXPONENTIAL:
                specs.add(new StepSpec("reversal",
                        envelope.getReversalMillis() * (long) EXPONENTIAL_TIME_CONSTANTS, 0.0,
                        Curve.EXPONENTIAL, noise, false));
                break;
            case NONE:
            default:
                // Permanent shift: 0-duration hold step so the sequence "ends" (and the
                // plugin bakes+retires) exactly where the envelope reports PERMANENT,
                // while the value stays at peak forever (permanent end semantics).
                specs.add(new StepSpec("permanent", 0L, peak, Curve.HOLD, noise, true));
                break;
        }
        return create(specs);
    }

    // ── Math ─────────────────────────────────────────────────────────────

    /**
     * The signed influence level at the given accumulated active time — the direct
     * factor term input: {@code priceMultiplier = 1 + value(t) * weightFactor * sensitivity}.
     * <ul>
     *   <li>{@code activeMillis < 0} (impact scheduled but not started, PENDING) → 0,
     *       matching {@link NewsImpactEnvelope#factor(long)}.</li>
     *   <li>Within a step: interpolated per the step's {@link Curve} (see enum docs).</li>
     *   <li>{@code activeMillis >= }{@link #totalDurationMs()} → {@link #finalValue()}
     *       if {@link #isPermanent()}, else 0 (see class Javadoc end semantics).</li>
     * </ul>
     *
     * @param activeMillis accumulated active milliseconds since impact start
     *                     (negative = impact not started yet)
     * @return the signed influence level (0.3 = +30%); always finite, always {@code > -1}
     */
    public double value(long activeMillis) {
        if (activeMillis < 0) return 0.0;
        if (activeMillis >= totalDurationMs) {
            return permanent ? finalValue() : 0.0;
        }
        // Find the step containing activeMillis (0-duration steps can never contain a
        // time point: their start equals the next step's start and are skipped here).
        for (int i = 0; i < steps.size(); i++) {
            if (activeMillis < stepStartsMs[i + 1]) {
                Step step = steps.get(i);
                long into = activeMillis - stepStartsMs[i];
                switch (step.curve()) {
                    case INSTANT:
                        return step.targetValue();
                    case HOLD:
                        return step.startValue();
                    case EXPONENTIAL: {
                        // tau = duration / 6 — for normalized legacy reversals this is
                        // exactly the envelope's reversalMillis, making the expression
                        // exp(-into/tau) bit-identical to the envelope's exp(-timeConstants).
                        double tau = step.durationMs() / EXPONENTIAL_TIME_CONSTANTS;
                        return step.targetValue()
                                + (step.startValue() - step.targetValue()) * Math.exp(-into / tau);
                    }
                    case LINEAR:
                    default:
                        return step.startValue() + (step.targetValue() - step.startValue())
                                * ((double) into / (double) step.durationMs());
                }
            }
        }
        // Unreachable (activeMillis < totalDurationMs implies a containing step exists).
        return permanent ? finalValue() : 0.0;
    }

    /**
     * The index of the step active at the given time, <b>clamped</b> to valid indices
     * so UI code always gets a renderable step:
     * <ul>
     *   <li>{@code activeMillis < 0} (PENDING) → 0 — callers tracking the pending phase
     *       must check the sign of their own active-time accumulator (same convention
     *       as {@link NewsImpactEnvelope#phase(long)} reporting RAMPING pre-start).</li>
     *   <li>{@code activeMillis >= }{@link #totalDurationMs()} (ended/permanent) →
     *       {@code stepCount() - 1} — callers detect the terminal state by comparing
     *       against {@link #totalDurationMs()}.</li>
     *   <li>Otherwise the unique step i with
     *       {@code stepStartMs(i) <= activeMillis < stepStartMs(i + 1)}; 0-duration
     *       steps are never reported (they occupy no time).</li>
     * </ul>
     *
     * @param activeMillis accumulated active milliseconds since impact start
     * @return the clamped step index in {@code [0, stepCount())}
     */
    public int stepIndexAt(long activeMillis) {
        if (activeMillis < 0) return 0;
        if (activeMillis >= totalDurationMs) return steps.size() - 1;
        for (int i = 0; i < steps.size(); i++) {
            if (activeMillis < stepStartsMs[i + 1]) return i;
        }
        return steps.size() - 1; // unreachable, defensive
    }

    /**
     * The active-millis at which the given step begins. The one-past-the-end index
     * {@code stepCount()} is explicitly allowed and returns {@link #totalDurationMs()}
     * — useful as the skip target of the last step ({@code skipPhase} jumps to
     * {@code stepStartMs(i + 1)}, T-095). Out-of-range indices are clamped.
     *
     * @param stepIndex the step index in {@code [0, stepCount()]}
     * @return the start time of that step in active milliseconds
     */
    public long stepStartMs(int stepIndex) {
        if (stepIndex < 0) return 0L;
        if (stepIndex >= stepStartsMs.length) return totalDurationMs;
        return stepStartsMs[stepIndex];
    }

    /**
     * @return the total active length of the sequence in milliseconds (sum of all step
     *         durations). For {@link #isPermanent()} sequences this is the time until
     *         the plugin bakes the shift and retires the event — the influence itself
     *         lasts forever (mirrors {@link NewsImpactEnvelope#totalLengthMillis()}
     *         for {@code reversal:none})
     */
    public long totalDurationMs() {
        return totalDurationMs;
    }

    /** @return the number of steps (always ≥ 1) */
    public int stepCount() {
        return steps.size();
    }

    /** @return the resolved steps in order (unmodifiable, never empty) */
    public List<Step> getSteps() {
        return steps;
    }

    /**
     * @param stepIndex the step index, clamped to {@code [0, stepCount())}
     * @return the resolved step at that (clamped) index
     */
    public Step getStep(int stepIndex) {
        if (stepIndex < 0) stepIndex = 0;
        if (stepIndex >= steps.size()) stepIndex = steps.size() - 1;
        return steps.get(stepIndex);
    }

    /**
     * The per-tick jitter amplitude of the step active at the given time (see
     * {@link #stepIndexAt(long)} for the clamping rules — pre-start times report step
     * 0's noise, but the plugin never applies noise while an impact is PENDING).
     *
     * @param activeMillis accumulated active milliseconds since impact start
     * @return the active step's noise amplitude (0 = none)
     */
    public double noiseAt(long activeMillis) {
        return steps.get(stepIndexAt(activeMillis)).noise();
    }

    /**
     * @return true if the last step is flagged {@code permanent}: after
     *         {@link #totalDurationMs()} the value stays at {@link #finalValue()}
     *         forever and the plugin must bake the shift into the default price
     *         (see class Javadoc)
     */
    public boolean isPermanent() {
        return permanent;
    }

    /**
     * @return the influence level at sequence end — the last step's {@code targetValue}.
     *         This is the bake input for permanent sequences
     *         ({@code bakeFactor = 1 + finalValue * weight * sensitivity}, plan §2)
     */
    public double finalValue() {
        return steps.get(steps.size() - 1).targetValue();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NewsSequence{steps=[");
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (i > 0) sb.append(", ");
            sb.append(step.name()).append(':').append(step.curve().jsonName())
                    .append("->").append(step.targetValue())
                    .append('@').append(step.durationMs()).append("ms");
            if (step.permanent()) sb.append("(permanent)");
        }
        return sb.append("], totalMs=").append(totalDurationMs).append('}').toString();
    }
}
