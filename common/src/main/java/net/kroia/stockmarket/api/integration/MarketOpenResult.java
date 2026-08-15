package net.kroia.stockmarket.api.integration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of {@link IStockMarketIntegration#openMarket}.
 * <p>
 * Design choice: a small {@link Result} record wraps the {@link Status} enum so
 * failure cases can carry a human-readable {@code reason} string without
 * introducing a heavier exception-based contract. The status enum alone is
 * exposed for callers that only care about the outcome kind and want to
 * {@code switch} on it.
 */
public final class MarketOpenResult {

    /** Kinds of outcomes {@link IStockMarketIntegration#openMarket} can return. */
    public enum Status {
        /** The market was created successfully. */
        SUCCESS,
        /** A market for the requested subject already exists — no-op. */
        ALREADY_EXISTS,
        /** The subject item is blacklisted in the banking system and cannot be traded. */
        ITEM_BLACKLISTED,
        /** Creation failed for some other reason; see {@link Result#reason()}. */
        FAILED
    }

    /**
     * Wrapper carrying a {@link Status} plus an optional reason string for the
     * failure cases. Reason is {@code null} on {@link Status#SUCCESS}.
     *
     * @param status the outcome kind (never null)
     * @param reason optional human-readable explanation; typically populated for
     *               {@link Status#FAILED} or {@link Status#ITEM_BLACKLISTED}
     */
    public record Result(@NotNull Status status, @Nullable String reason) {

        /** Convenience constructor for outcomes that don't need a reason. */
        public Result(@NotNull Status status) {
            this(status, null);
        }

        /** @return {@code true} iff {@link #status()} is {@link Status#SUCCESS}. */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    private MarketOpenResult() { /* no instances */ }
}
