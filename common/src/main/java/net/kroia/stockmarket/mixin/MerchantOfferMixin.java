package net.kroia.stockmarket.mixin;

import net.kroia.stockmarket.villagertrading.MoneyChangeCarrier;
import net.kroia.stockmarket.villagertrading.MoneyPayment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enables value-based money payment for merchant offers whose cost is
 * BankSystem money (offers produced by the T-064 villager trade repricing):
 * the player may pay with ANY combination of money denominations whose total
 * value covers the price, and overpayment returns exact change.
 * <p>
 * Both injections are HEAD-cancellable delegates to {@link MoneyPayment} and
 * bail out immediately (without cancelling) when the offer has no money cost —
 * the least invasive form: the vanilla method body stays byte-for-byte
 * untouched for every non-money offer and remains compatible with other mods'
 * mixins into the same methods. The single {@code satisfiedBy} injection
 * transparently fixes {@code MerchantOffers.getRecipeFor} (both scan paths and
 * both argument orientations) as well as the client-side trade preview/red-X,
 * because all of them funnel through this one method.
 */
@Mixin(MerchantOffer.class)
public class MerchantOfferMixin implements MoneyChangeCarrier {

    /**
     * Raw overpayment accumulated by value-based {@code take} calls, waiting
     * for {@code MerchantResultSlotMixin} to deliver it as change.
     */
    @Unique
    private long stockmarket$pendingChangeRaw;

    /**
     * Replaces the per-item cost matching with value-based matching on
     * money-cost offers (see {@link MoneyPayment#satisfied}).
     *
     * @param playerOfferA payment slot 0 stack
     * @param playerOfferB payment slot 1 stack
     * @param cir          callback; cancelled only for money-cost offers
     */
    @Inject(
            method = "satisfiedBy(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void stockmarket$valueSatisfiedBy(ItemStack playerOfferA, ItemStack playerOfferB,
                                              CallbackInfoReturnable<Boolean> cir) {
        MerchantOffer self = (MerchantOffer) (Object) this;
        if (MoneyPayment.isValueBasedOffer(self)) {
            cir.setReturnValue(MoneyPayment.satisfied(self, playerOfferA, playerOfferB));
        }
        // Gate failed: fall through to the untouched vanilla path.
    }

    /**
     * Replaces payment consumption on money-cost offers: consumes the optimal
     * note combination (mutating the slot stacks exactly like vanilla's
     * shrink) and records the overpayment for later change delivery.
     * <p>
     * Returning {@code false} (instead of cancelling) on an unsatisfied
     * payment preserves vanilla's swapped-orientation retry in
     * {@code MerchantResultSlot.onTake}.
     *
     * @param playerOfferA payment slot 0 stack (mutated on success)
     * @param playerOfferB payment slot 1 stack (mutated on success)
     * @param cir          callback; cancelled only for money-cost offers
     */
    @Inject(
            method = "take(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void stockmarket$valueTake(ItemStack playerOfferA, ItemStack playerOfferB,
                                       CallbackInfoReturnable<Boolean> cir) {
        MerchantOffer self = (MerchantOffer) (Object) this;
        if (!MoneyPayment.isValueBasedOffer(self)) {
            return; // vanilla path
        }
        if (!MoneyPayment.satisfied(self, playerOfferA, playerOfferB)) {
            cir.setReturnValue(false); // let the caller retry swapped
            return;
        }
        long changeRaw = MoneyPayment.consume(self, playerOfferA, playerOfferB);
        if (changeRaw > 0) {
            this.stockmarket$pendingChangeRaw += changeRaw;
        }
        cir.setReturnValue(true);
    }

    @Override
    public long stockmarket$consumePendingChange() {
        long change = this.stockmarket$pendingChangeRaw;
        this.stockmarket$pendingChangeRaw = 0L;
        return change;
    }
}
