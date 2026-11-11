package net.kroia.stockmarket.minecraft.block.custom;

import net.kroia.banksystem.minecraft.block.custom.TerminalBlock;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class StockMarketBlock extends TerminalBlock {

    public static final String NAME = "stock_market_block";

    public StockMarketBlock()
    {
        // Pass the registered id so the BankSystem base class can apply RegistryCompat (multi-version support).
        // Must match the id used in StockMarketBlocks registration.
        super(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc<=1.21.10
        //super(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc>=1.21.11
    }
    /*@Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockMarketBlockEntity(pos, state);
    }*/
    /*public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof StockMarketBlockEntity) {
                StockMarketBlockEntity stockMarketBlock = (StockMarketBlockEntity) blockEntity;
                // Init stockMarketBlock entity if needed
            }
        }
    }*/

    /*@Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (world.getBlockEntity(pos) instanceof StockMarketBlockEntity blockEntity) {
            blockEntity.onBlockPlacedBy(state, placer);
        }
    }*/


    /*@Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            level.removeBlockEntity(pos);
        }
    }*/
    /**
     * The chat line the BankSystem base class shows when this slave has no master link and refuses the block,
     * with or without an item in hand (BankSystem #170 hook; T-246). Replaces the BankSystem wording with the
     * StockMarket one. Public (widened from protected) so the slave-masterdown tests can check the wording.
     *
     * @return the translatable StockMarket "not connected" message
     */
    @Override
    public Component getNoMasterMessage() {
        return Component.translatable("message.stockmarket.slave_not_connected");
    }

    /**
     * Opens the trade screen. T-245: the server decides, so the base class's master-link check (which only
     * runs server-side) can refuse; the client never opens the screen on its own.
     */
    @Override
    public void openGui(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer)
            OpenUIPacket.sendToClient(serverPlayer, OpenUIPacket.GUIType.TRADE);
    }
}
