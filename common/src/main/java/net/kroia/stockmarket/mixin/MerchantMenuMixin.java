package net.kroia.stockmarket.mixin;

import net.kroia.stockmarket.villagertrading.MoneyPayment;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Convenience auto-fill for value-based money offers (Phase 2 of T-065): when
 * the player clicks an offer row, vanilla {@code tryMoveItems} only moves
 * inventory stacks that exactly match the offer's fitted cost denomination.
 * This TAIL injection tops the payment slots up by value with whatever money
 * denominations the player actually owns (see
 * {@link MoneyPayment#topUpPaymentSlots}).
 * <p>
 * Runs on both logical sides, exactly like vanilla {@code tryMoveItems}
 * (client on row click, server via {@code ServerboundSelectTradePacket}).
 * TAIL targets the method's final return; the early-return paths it skips are
 * the degenerate ones (invalid index, payment slots could not be emptied into
 * a full inventory), where skipping the top-up is acceptable.
 */
@Mixin(MerchantMenu.class)
public class MerchantMenuMixin {

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    /**
     * Tops up the payment slots after vanilla's exact auto-fill.
     *
     * @param selectedMerchantIndex the clicked offer row index
     * @param ci                    callback info (never cancelled)
     */
    @Inject(method = "tryMoveItems(I)V", at = @At("TAIL"))
    private void stockmarket$topUpMoneyPayment(int selectedMerchantIndex, CallbackInfo ci) {
        MerchantMenu self = (MerchantMenu) (Object) this;
        MerchantOffers offers = self.getOffers();
        if (selectedMerchantIndex < 0 || selectedMerchantIndex >= offers.size()) {
            return;
        }
        MoneyPayment.topUpPaymentSlots(self, this.tradeContainer, offers.get(selectedMerchantIndex));
    }
}
