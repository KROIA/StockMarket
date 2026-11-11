package net.kroia.stockmarket.mixin;

import net.kroia.stockmarket.villagertrading.VillagerTradeRewriter;
import net.minecraft.world.entity.npc.Villager; // mc<=1.21.10
//import net.minecraft.world.entity.npc.villager.Villager; // mc>=1.21.11
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caps villager discounts on offers rewritten to currency (T-243).
 * <p>
 * {@code Villager.updateSpecialPrices} runs server-side when a player opens the
 * trade menu and lowers each offer's {@code specialPriceDiff} by the player's
 * reputation and the Hero of the Village effect. This TAIL injection then
 * limits the total discount of every rewritten offer to
 * {@link VillagerTradeRewriter#MAX_DISCOUNT_PERCENT} (see
 * {@link VillagerTradeRewriter#capDiscounts}). Offers still in emerald form are
 * not touched. Wandering traders have no discounts, so only {@code Villager} is
 * targeted.
 */
@Mixin(Villager.class)
public class VillagerMixin {

    /**
     * Clamps the discounts vanilla just applied.
     *
     * @param player the player opening the trade menu
     * @param ci     callback info (never cancelled)
     */
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void stockmarket$capDiscounts(Player player, CallbackInfo ci) {
        VillagerTradeRewriter.capDiscounts((Villager) (Object) this);
    }
}
