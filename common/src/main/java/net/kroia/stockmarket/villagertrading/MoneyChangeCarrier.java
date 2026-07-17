package net.kroia.stockmarket.villagertrading;

import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Duck-type bridge implemented onto {@link MerchantOffer} by
 * {@code MerchantOfferMixin}: carries the raw overpayment recorded by a
 * value-based {@code take} until {@code MerchantResultSlotMixin} delivers it
 * as change after the vanilla trade bookkeeping has run.
 * <p>
 * Deliberately lives OUTSIDE the configured mixin package
 * ({@code net.kroia.stockmarket.mixin}): Sponge Mixin reserves that entire
 * package tree and forbids ordinary code — including the JVM's interface
 * resolution when a mixed-in class like {@code MerchantOffer} first loads —
 * from referencing classes inside it ({@code IllegalClassLoadError}).
 * Plain (non-mixin) code may reference this interface freely — after mixin
 * application every {@code MerchantOffer} instance is an instance of it.
 */
public interface MoneyChangeCarrier {

    /**
     * Reads and clears the pending change recorded by the last value-based
     * {@code take} call(s) on this offer. Read-and-clear semantics: change is
     * accumulated (a third-party caller invoking {@code take} directly without
     * going through {@code MerchantResultSlot.onTake} defers its change to the
     * next delivery of this offer — value is never destroyed) and handed out
     * exactly once.
     *
     * @return the pending raw change ({@code >= 0}); subsequent calls return 0
     *         until new change is recorded
     */
    long stockmarket$consumePendingChange();
}
