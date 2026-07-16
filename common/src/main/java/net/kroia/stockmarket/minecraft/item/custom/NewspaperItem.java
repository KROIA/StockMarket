package net.kroia.stockmarket.minecraft.item.custom;

import net.kroia.stockmarket.minecraft.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
        super(new Properties().arch$tab(StockMarketCreativeModeTab.STOCK_MARKET_TAB));
    }

    /**
     * Right-click (on air or on a non-interactive block): opens the newspaper
     * screen on the client. The server side succeeds silently so the hand swings
     * consistently on both sides.
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            StockMarketClientHooks.openNewsScreen();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
