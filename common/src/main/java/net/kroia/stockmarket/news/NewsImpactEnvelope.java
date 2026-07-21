package net.kroia.stockmarket.news;

/**
 * Pure-math price-impact envelope of a news event. <b>No Minecraft dependencies</b> —
 * fully unit-testable outside a server context.
 * <p>
 * The envelope describes how strongly an event influences a market over its lifetime
 * as a normalized curve in {@code [0, 1]}:
 * <pre>
 *   1.0 ┤        ________________
 *       │       /                \
 *       │      /                  \__
 *   0.0 ┤_____/                      \______
 *        ramp-up       hold        reversal
 * </pre>
 * <ul>
 *   <li><b>Ramp-up</b> ({@code rampUpSeconds}): linear rise from 0 to 1.</li>
 *   <li><b>Hold</b> ({@code durationSeconds}): stays at 1.</li>
 *   <li><b>Reversal</b> ({@link ReversalMode}): how the influence returns to 0
 *       (or, for {@link ReversalMode#NONE}, never does — see below).</li>
 * </ul>
 * <p>
 * <b>Contract:</b> {@link #factor(long)} returns only the normalized envelope value in
 * {@code [0, 1]}. The caller multiplies it with {@link #getPeakFactor()} (and a per-market
 * weight factor) to obtain the actual multiplicative price influence, e.g.
 * {@code priceMultiplier = 1 + factor(t) * peakFactor * weightFactor}.
 * <p>
 * <b>Time basis:</b> {@code activeMillis} is the accumulated "active milliseconds while
 * the server was ticking" of the event, <b>not</b> raw wall-clock time — so events do not
 * silently expire while the server is paused or empty (plan §6.9). Values {@code < 0}
 * (impact scheduled but not yet started, e.g. a positive announce delay) yield factor 0.
 * <p>
 * <b>{@code reversal: none} semantics:</b> after the hold phase the envelope stays at 1.0
 * forever and {@link #phase(long)} reports the distinct state {@link Phase#PERMANENT}.
 * The future NewsPlugin (T-071) must react to that state by <b>baking the shift into the
 * market's default/base price</b> and then retiring the event — it cannot simply stop
 * applying the factor, or the price would snap back on the next tick because
 * {@code VolatilityPlugin} recomputes the target price absolutely.
 * <p>
 * <b>Exponential expiry:</b> a pure {@code e^(-t/tau)} curve never reaches 0, so the
 * envelope treats the event as {@link Phase#EXPIRED} (factor hard-clamped to 0) after
 * {@link #EXPONENTIAL_EXPIRY_TIME_CONSTANTS} time constants (~0.25% residual influence).
 */
public final class NewsImpactEnvelope {

    /**
     * Preset envelope shapes. A type only supplies <b>default values</b> for the shape
     * parameters — explicit JSON fields always override them (plan §1).
     */
    public enum ImpactType {
        /** Near-instant jump, short hold, exponential decay back to normal. */
        SHOCK(5, 300, ReversalMode.EXPONENTIAL, 300),
        /** Slow ramp to peak, long hold, linear ramp back to normal. */
        TREND(120, 600, ReversalMode.RAMP, 600),
        /** Near-instant move (typically negative peakFactor), slow exponential recovery. */
        CRASH(10, 120, ReversalMode.EXPONENTIAL, 900);

        private final long defaultRampUpSeconds;
        private final long defaultDurationSeconds;
        private final ReversalMode defaultReversal;
        private final long defaultReversalSeconds;

        ImpactType(long defaultRampUpSeconds, long defaultDurationSeconds,
                   ReversalMode defaultReversal, long defaultReversalSeconds) {
            this.defaultRampUpSeconds = defaultRampUpSeconds;
            this.defaultDurationSeconds = defaultDurationSeconds;
            this.defaultReversal = defaultReversal;
            this.defaultReversalSeconds = defaultReversalSeconds;
        }

        /** @return the default ramp-up time (seconds) this preset supplies */
        public long getDefaultRampUpSeconds() { return defaultRampUpSeconds; }
        /** @return the default hold duration (seconds) this preset supplies */
        public long getDefaultDurationSeconds() { return defaultDurationSeconds; }
        /** @return the default reversal mode this preset supplies */
        public ReversalMode getDefaultReversal() { return defaultReversal; }
        /** @return the default reversal time / time constant (seconds) this preset supplies */
        public long getDefaultReversalSeconds() { return defaultReversalSeconds; }

        /**
         * Case-insensitive lookup of an impact type by its JSON name.
         *
         * @param name the JSON value, e.g. {@code "shock"}
         * @return the matching type, or {@code null} if unknown
         */
        public static ImpactType fromString(String name) {
            if (name == null) return null;
            for (ImpactType type : values()) {
                if (type.name().equalsIgnoreCase(name)) return type;
            }
            return null;
        }

        /** @return the lowercase JSON name of this type (e.g. {@code "shock"}) */
        public String jsonName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** How the influence returns to normal after the hold phase (plan §1). */
    public enum ReversalMode {
        /** Linear ramp from 1 back to 0 over {@code reversalSeconds}. */
        RAMP,
        /** Exponential decay {@code e^(-t/tau)} with {@code tau = reversalSeconds}. */
        EXPONENTIAL,
        /**
         * No reversal — the shift is permanent. The envelope stays at 1.0 after the hold
         * phase and {@link #phase(long)} reports {@link Phase#PERMANENT}; the plugin must
         * bake the shift into the market's default price (see class Javadoc).
         */
        NONE;

        /**
         * Case-insensitive lookup of a reversal mode by its JSON name.
         *
         * @param name the JSON value, e.g. {@code "exponential"}
         * @return the matching mode, or {@code null} if unknown
         */
        public static ReversalMode fromString(String name) {
            if (name == null) return null;
            for (ReversalMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) return mode;
            }
            return null;
        }

        /** @return the lowercase JSON name of this mode (e.g. {@code "ramp"}) */
        public String jsonName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Lifecycle phase of the envelope at a given active time. */
    public enum Phase {
        /** Before or during the ramp-up (also reported for {@code activeMillis < 0}). */
        RAMPING,
        /** At peak influence (envelope = 1). */
        HOLDING,
        /** Influence decaying back to 0 ({@link ReversalMode#RAMP} / {@link ReversalMode#EXPONENTIAL}). */
        REVERTING,
        /**
         * Hold phase of a {@link ReversalMode#NONE} event has ended — the shift is now
         * permanent. Distinct from {@link #HOLDING} so the plugin can detect the moment
         * to bake the shift into the default price and retire the event.
         */
        PERMANENT,
        /** The event no longer has any influence (envelope = 0). */
        EXPIRED
    }

    /**
     * Number of exponential time constants after which an {@link ReversalMode#EXPONENTIAL}
     * event is considered fully expired ({@code e^-6} ≈ 0.25% residual influence).
     */
    public static final double EXPONENTIAL_EXPIRY_TIME_CONSTANTS = 6.0;

    private final ImpactType type;
    private final double peakFactor;
    private final long rampUpMillis;
    private final long holdMillis;
    private final ReversalMode reversal;
    private final long reversalMillis;
    private final double noise;

    /**
     * Creates an envelope from fully resolved parameters (defaults from the
     * {@link ImpactType} preset already applied by the parser).
     * <p>
     * The constructor is defensive: {@link NewsEventLibrary} validation rejects invalid
     * values before construction, but out-of-range inputs are still sanitized here
     * (negative times → 0, non-finite peakFactor → 0, {@code peakFactor <= -1} → clamped
     * just above -1, negative/non-finite noise → 0) so the math can never produce
     * NaN/negative prices.
     *
     * @param type            the preset shape this envelope was derived from (informational,
     *                        kept for history records and the admin GUI)
     * @param peakFactor      peak multiplicative influence; {@code 0.55} = +55% at peak,
     *                        {@code -0.4} = -40%. Must be {@code > -1} (a factor of -1
     *                        would zero the price)
     * @param rampUpSeconds   seconds to ramp linearly from 0 to peak (0 = instant)
     * @param durationSeconds seconds the influence holds at peak
     * @param reversal        how the influence returns to normal (never null; falls back
     *                        to the type preset's default when null)
     * @param reversalSeconds reversal length ({@link ReversalMode#RAMP}) or time constant
     *                        ({@link ReversalMode#EXPONENTIAL}); ignored for
     *                        {@link ReversalMode#NONE}
     * @param noise           optional extra per-tick jitter amplitude on the factor,
     *                        applied by the plugin (0 = none). Stored here so the impact
     *                        definition stays self-contained
     */
    public NewsImpactEnvelope(ImpactType type, double peakFactor, long rampUpSeconds,
                              long durationSeconds, ReversalMode reversal, long reversalSeconds,
                              double noise) {
        this.type = type != null ? type : ImpactType.SHOCK;
        if (!Double.isFinite(peakFactor)) peakFactor = 0.0;
        if (peakFactor <= -1.0) peakFactor = -0.99;
        this.peakFactor = peakFactor;
        this.rampUpMillis = Math.max(0, rampUpSeconds) * 1000L;
        this.holdMillis = Math.max(0, durationSeconds) * 1000L;
        this.reversal = reversal != null ? reversal : this.type.getDefaultReversal();
        this.reversalMillis = Math.max(0, reversalSeconds) * 1000L;
        this.noise = (Double.isFinite(noise) && noise > 0.0) ? noise : 0.0;
    }

    /**
     * Creates an envelope purely from an {@link ImpactType} preset's defaults.
     *
     * @param type       the preset shape
     * @param peakFactor peak multiplicative influence (see constructor)
     * @return an envelope with all shape parameters taken from the preset
     */
    public static NewsImpactEnvelope fromDefaults(ImpactType type, double peakFactor) {
        ImpactType t = type != null ? type : ImpactType.SHOCK;
        return new NewsImpactEnvelope(t, peakFactor, t.getDefaultRampUpSeconds(),
                t.getDefaultDurationSeconds(), t.getDefaultReversal(),
                t.getDefaultReversalSeconds(), 0.0);
    }

    /**
     * Normalized envelope value at the given accumulated active time.
     * <p>
     * The caller multiplies the result by {@link #getPeakFactor()} (and any per-market
     * weight factor) to obtain the actual influence — see class Javadoc.
     *
     * @param activeMillis accumulated active milliseconds since impact start
     *                     (negative = impact not started yet)
     * @return the envelope value in {@code [0, 1]}
     */
    public double factor(long activeMillis) {
        if (activeMillis < 0) return 0.0;

        // Ramp-up phase: linear 0 -> 1 (rampUpMillis == 0 skips straight to hold)
        if (activeMillis < rampUpMillis) {
            return (double) activeMillis / (double) rampUpMillis;
        }

        // Hold phase: full influence
        long afterRamp = activeMillis - rampUpMillis;
        if (afterRamp < holdMillis) {
            return 1.0;
        }

        // Reversal phase
        long intoReversal = afterRamp - holdMillis;
        switch (reversal) {
            case NONE:
                // Permanent shift: the envelope never decays (see class Javadoc)
                return 1.0;
            case RAMP:
                if (reversalMillis <= 0 || intoReversal >= reversalMillis) return 0.0;
                return 1.0 - (double) intoReversal / (double) reversalMillis;
            case EXPONENTIAL:
            default:
                if (reversalMillis <= 0) return 0.0;
                double timeConstants = (double) intoReversal / (double) reversalMillis;
                // Hard cutoff so exponential events truly expire instead of
                // influencing the market forever with a microscopic residue.
                if (timeConstants >= EXPONENTIAL_EXPIRY_TIME_CONSTANTS) return 0.0;
                return Math.exp(-timeConstants);
        }
    }

    /**
     * Lifecycle phase at the given accumulated active time.
     * <p>
     * Note: {@code activeMillis < 0} (impact scheduled but not started) reports
     * {@link Phase#RAMPING} because from the market's perspective the event is
     * in its onset. Callers tracking pending impacts should check the sign of
     * their own active-time accumulator instead.
     *
     * @param activeMillis accumulated active milliseconds since impact start
     * @return the phase (never null)
     */
    public Phase phase(long activeMillis) {
        if (activeMillis < rampUpMillis) return Phase.RAMPING;
        long afterRamp = activeMillis - rampUpMillis;
        if (afterRamp < holdMillis) return Phase.HOLDING;
        long intoReversal = afterRamp - holdMillis;
        switch (reversal) {
            case NONE:
                return Phase.PERMANENT;
            case RAMP:
                return intoReversal < reversalMillis ? Phase.REVERTING : Phase.EXPIRED;
            case EXPONENTIAL:
            default:
                if (reversalMillis <= 0) return Phase.EXPIRED;
                return (double) intoReversal / (double) reversalMillis < EXPONENTIAL_EXPIRY_TIME_CONSTANTS
                        ? Phase.REVERTING : Phase.EXPIRED;
        }
    }

    /**
     * Total active length of the envelope in milliseconds:
     * <ul>
     *   <li>{@link ReversalMode#RAMP}: ramp-up + hold + reversal</li>
     *   <li>{@link ReversalMode#EXPONENTIAL}: ramp-up + hold +
     *       {@link #EXPONENTIAL_EXPIRY_TIME_CONSTANTS} x time constant (expiry cutoff)</li>
     *   <li>{@link ReversalMode#NONE}: ramp-up + hold — the time until the phase becomes
     *       {@link Phase#PERMANENT} and the plugin retires the event (the price shift
     *       itself lasts forever)</li>
     * </ul>
     *
     * @return the total envelope length in milliseconds
     */
    public long totalLengthMillis() {
        switch (reversal) {
            case NONE:
                return rampUpMillis + holdMillis;
            case RAMP:
                return rampUpMillis + holdMillis + reversalMillis;
            case EXPONENTIAL:
            default:
                return rampUpMillis + holdMillis
                        + (long) (reversalMillis * EXPONENTIAL_EXPIRY_TIME_CONSTANTS);
        }
    }

    /** @return the total envelope length in whole seconds (see {@link #totalLengthMillis()}) */
    public int totalLengthSeconds() {
        return (int) Math.min(Integer.MAX_VALUE, totalLengthMillis() / 1000L);
    }

    /** @return the preset shape this envelope was derived from */
    public ImpactType getType() { return type; }

    /**
     * @return the peak multiplicative influence the caller multiplies the
     *         {@link #factor(long)} with ({@code 0.55} = +55%, {@code -0.4} = -40%)
     */
    public double getPeakFactor() { return peakFactor; }

    /** @return the ramp-up length in milliseconds */
    public long getRampUpMillis() { return rampUpMillis; }

    /** @return the hold length in milliseconds */
    public long getHoldMillis() { return holdMillis; }

    /** @return how the influence returns to normal after the hold phase */
    public ReversalMode getReversal() { return reversal; }

    /**
     * @return the reversal length ({@link ReversalMode#RAMP}) or time constant
     *         ({@link ReversalMode#EXPONENTIAL}) in milliseconds
     */
    public long getReversalMillis() { return reversalMillis; }

    /**
     * @return the optional per-tick jitter amplitude on the factor (0 = none);
     *         applying it is the plugin's job, the envelope itself stays deterministic
     */
    public double getNoise() { return noise; }

    @Override
    public String toString() {
        return "NewsImpactEnvelope{type=" + type.jsonName()
                + ", peakFactor=" + peakFactor
                + ", rampUpMs=" + rampUpMillis
                + ", holdMs=" + holdMillis
                + ", reversal=" + reversal.jsonName()
                + ", reversalMs=" + reversalMillis
                + ", noise=" + noise + "}";
    }
}
