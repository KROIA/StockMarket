package net.kroia.stockmarket.minecraft.item.custom;

import net.kroia.modutilities.RegistryCompat;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResultHolder; // mc<=1.21.1
import net.minecraft.world.InteractionResult; // mc>=1.21.2
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The newspaper item (task T-074, plan §4): right-clicking it opens the
 * {@code NewsScreen} — the newspaper-styled feed of all published news events.
 * <p>
 * The screen is purely client-side (it fetches its data itself via
 * {@code NewsHistoryRequest} / the live {@code ClientNewsCache}), so the server
 * side of {@link #use} does nothing. The client hook is only touched on the
 * client-side path, mirroring how {@code StockMarketBlock} opens the trade screen
 * — registration via Architectury in common covers both Fabric and NeoForge.
 * <p>
 * Craftable from paper + ink sac (see
 * {@code data/stockmarket/recipe/newspaper.json}).
 */
public class NewspaperItem extends Item {

    /** Registry name of the item ({@code stockmarket:newspaper}). */
    public static final String NAME = "newspaper";

    public NewspaperItem() {
        // RegistryCompat: no-op on 1.21.1, applies setId on 1.21.2+ (id must match the registration name)
        super(RegistryCompat.itemProperties(new Properties().arch$tab(StockMarketCreativeModeTab.STOCK_MARKET_TAB),
                ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME))); // mc<=1.21.10
                //Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME))); // mc>=1.21.11
    }

    /**
     * Right-click (on air or on a non-interactive block): opens the newspaper
     * screen on the client. The server side succeeds silently so the hand swings
     * consistently on both sides.
     */
    @Override
    //public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { // mc<=1.21.1
    public @NotNull InteractionResult use(Level level, Player player, InteractionHand hand) { // mc>=1.21.2
        if (level.isClientSide()) {
            StockMarketClientHooks.openNewsScreen();
        }
        //return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide()); // mc<=1.21.1
        return InteractionResult.SUCCESS; // mc>=1.21.2 (swings on client, same as sidedSuccess)
    }
}
