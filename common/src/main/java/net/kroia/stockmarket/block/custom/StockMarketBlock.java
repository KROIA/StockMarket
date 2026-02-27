package net.kroia.stockmarket.block.custom;

import net.kroia.banksystem.block.custom.TerminalBlock;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class StockMarketBlock extends TerminalBlock implements EntityBlock {

    public static final String NAME = "stock_market_block";

    public StockMarketBlock()
    {
        super();
    }
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockMarketBlockEntity(pos, state);
    }
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof StockMarketBlockEntity) {
                StockMarketBlockEntity stockMarketBlock = (StockMarketBlockEntity) blockEntity;
                // Init stockMarketBlock entity if needed
            }
        }
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (world.getBlockEntity(pos) instanceof StockMarketBlockEntity blockEntity) {
            blockEntity.onBlockPlacedBy(state, placer);
        }
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            level.removeBlockEntity(pos);
        }
    }
    @Override
    public void openGui(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer)
        {
            // todo: replace the function below
            //SyncStockMarketBlockEntityPacket.sendPacketToClient(pos, (StockMarketBlockEntity) level.getBlockEntity(pos), (ServerPlayer) player);
        } else if (level.isClientSide) {
            // On the client side, open the TradeScreen
            BlockEntity entity = level.getBlockEntity(pos);
            StockMarketClientHooks.openStockMarketBlockScreen(entity, pos);
        }
    }
}
