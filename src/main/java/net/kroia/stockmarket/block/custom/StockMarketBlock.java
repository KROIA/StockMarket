package net.kroia.stockmarket.block.custom;

import net.kroia.stockmarket.ClientHooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.packet.StockMarketBlockEntityLoadPacket;
import net.kroia.stockmarket.networking.packet.StockMarketBlockEntitySavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class StockMarketBlock extends Block implements EntityBlock {

    public static final String NAME = "stock_market_block";

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StockMarketBlock()
    {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH)); // Default facing

    }
    public StockMarketBlock(Properties pProperties) {
        super(pProperties);
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
            StockMarketMod.LOGGER.info("placing block");
            if (blockEntity instanceof StockMarketBlockEntity) {
                StockMarketMod.LOGGER.info("Entity is StockMarketBlock");
                StockMarketBlockEntity stockMarketBlock = (StockMarketBlockEntity) blockEntity;
                // Init stockMarketBlock entity if needed
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer)
        {
            StockMarketMod.LOGGER.info("server use StockMarketBlock");
            StockMarketBlockEntityLoadPacket.sendPacketToClient(pos, (StockMarketBlockEntity) level.getBlockEntity(pos), (ServerPlayer) player);
            // Open the screen on the server side
            //NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
            //        (id, playerInventory, playerEntity) -> new ChartMenu(id, playerInventory,
            //                (StockMarketBlockEntity) level.getBlockEntity(pos),
            //                ContainerLevelAccess.create(level,pos)),
            //        Component.translatable("container.chart")));
        } else if (level.isClientSide) {
            // On the client side, open the TradeScreen
            StockMarketMod.LOGGER.info("client use StockMarketBlock");
            //Minecraft.getInstance().setScreen(new TradeScreen(((StockMarketBlockEntity) level.getBlockEntity(pos)).getChartData()));
            BlockEntity entity = level.getBlockEntity(pos);
            return ClientHooks.openStockMarketBlockScreen(entity, pos);
        }
        return InteractionResult.SUCCESS;  // Indicate the interaction was successful
    }
}
