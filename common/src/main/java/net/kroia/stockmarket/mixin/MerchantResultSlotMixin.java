package net.kroia.stockmarket.mixin;

import net.kroia.stockmarket.villagertrading.MoneyChangeCarrier;
import net.kroia.stockmarket.villagertrading.MoneyPayment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Delivers the change recorded by a value-based trade
 * ({@code MerchantOfferMixin} / {@link MoneyPayment#consume}) after the
 * vanilla trade bookkeeping has completed.
 * <p>
 * The traded offer is captured at HEAD because by TAIL the vanilla
 * {@code setItem(0/1)} calls have already re-run {@code updateSellItem}, which
 * may have switched {@code getActiveOffer()} to a different offer. Delivering
 * at TAIL means the change's own {@code setItem} calls run after vanilla's,
 * re-arming the result slot correctly for the shift-click repeat loop (each
 * iteration nets a full price deduction, so the loop terminates).
 */
@Mixin(MerchantResultSlot.class)
public class MerchantResultSlotMixin {

    @Shadow
    @Final
    private MerchantContainer slots;

    @Shadow
    @Final
    private Player player;

    /** Offer active at the moment the result was taken (HEAD capture). */
    @Unique
    private MerchantOffer stockmarket$takenOffer;

    /**
     * Captures the offer that is about to be traded (non-cancelling).
     *
     * @param player the taking player
     * @param stack  the taken result stack
     * @param ci     callback info (never cancelled)
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void stockmarket$captureTakenOffer(Player player, ItemStack stack, CallbackInfo ci) {
        this.stockmarket$takenOffer = this.slots.getActiveOffer();
    }

    /**
     * Delivers any pending change of the captured offer once vanilla is done
     * (payment slots restored, trade notified).
     *
     * @param player the taking player
     * @param stack  the taken result stack
     * @param ci     callback info (never cancelled)
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void stockmarket$deliverChange(Player player, ItemStack stack, CallbackInfo ci) {
        MerchantOffer offer = this.stockmarket$takenOffer;
        this.stockmarket$takenOffer = null;
        // Every MerchantOffer is a MoneyChangeCarrier after mixin application;
        // the instanceof also guards against a null capture.
        if (offer instanceof MoneyChangeCarrier carrier) {
            long changeRaw = carrier.stockmarket$consumePendingChange();
            if (changeRaw > 0) {
                MoneyPayment.deliverChange(changeRaw, this.slots, this.player);
            }
        }
    }
}
