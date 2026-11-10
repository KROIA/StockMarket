package net.kroia.stockmarket.mixin;

import net.kroia.stockmarket.villagertrading.MoneyChangeCarrier;
import net.kroia.stockmarket.villagertrading.MoneyPayment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
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
 * vanilla trade bookkeeping has completed, and logs the notes the trade
 * created or destroyed to BankSystem's NoteFlow log (T-216).
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

    @Shadow
    @Final
    private Merchant merchant;

    /** Offer active at the moment the result was taken (HEAD capture). */
    @Unique
    private MerchantOffer stockmarket$takenOffer;

    /** Copies of payment slots 0 and 1 at HEAD, to log the notes the trade used up. */
    @Unique
    private ItemStack[] stockmarket$paidBefore;

    /**
     * Captures the offer that is about to be traded and the payment slots
     * (non-cancelling).
     *
     * @param player the taking player
     * @param stack  the taken result stack
     * @param ci     callback info (never cancelled)
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void stockmarket$captureTakenOffer(Player player, ItemStack stack, CallbackInfo ci) {
        this.stockmarket$takenOffer = this.slots.getActiveOffer();
        this.stockmarket$paidBefore = new ItemStack[]{this.slots.getItem(0).copy(), this.slots.getItem(1).copy()};
    }

    /**
     * Logs the trade's notes, then delivers any pending change of the captured
     * offer once vanilla is done (payment slots restored, trade notified).
     *
     * @param player the taking player
     * @param stack  the taken result stack
     * @param ci     callback info (never cancelled)
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void stockmarket$deliverChange(Player player, ItemStack stack, CallbackInfo ci) {
        MerchantOffer offer = this.stockmarket$takenOffer;
        ItemStack[] paidBefore = this.stockmarket$paidBefore;
        this.stockmarket$takenOffer = null;
        this.stockmarket$paidBefore = null;
        // Server side the merchant is the villager entity; the client uses a ClientSideMerchant.
        Entity villager = this.merchant instanceof Entity entity ? entity : null;
        if (offer != null && paidBefore != null) {
            // Before the change: it may merge into the payment slots.
            MoneyPayment.recordTrade(offer, paidBefore, this.slots, this.player, villager);
        }
        // Every MerchantOffer is a MoneyChangeCarrier after mixin application;
        // the instanceof also guards against a null capture.
        if (offer instanceof MoneyChangeCarrier carrier) {
            long changeRaw = carrier.stockmarket$consumePendingChange();
            if (changeRaw > 0) {
                MoneyPayment.deliverChange(changeRaw, this.slots, this.player, villager);
            }
        }
    }
}
