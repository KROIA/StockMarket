package net.kroia.stockmarket.minecraft.block.custom;

import net.kroia.banksystem.minecraft.block.custom.TerminalBlock;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.minecraft.core.BlockPos;
//import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
import net.minecraft.resources.Identifier; // mc>=1.21.11
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
        //super(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc<=1.21.10
        super(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc>=1.21.11
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
    @Override
    public void openGui(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer)
        {
            // todo: replace the function below
            //SyncStockMarketBlockEntityPacket.sendPacketToClient(pos, (StockMarketBlockEntity) level.getBlockEntity(pos), (ServerPlayer) player);
        } else if (level.isClientSide()) {
            // On the client side, open the TradeScreen
            BlockEntity entity = level.getBlockEntity(pos);
            StockMarketClientHooks.openStockMarketBlockScreen(entity, pos);
        }
    }
}
