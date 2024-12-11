package net.kroia.stockmarket.block.custom;

import net.kroia.stockmarket.ClientHooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BankTerminalBlock extends Block implements EntityBlock {

    public static final String NAME = "bank_terminal_block";

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BankTerminalBlock()
    {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH)); // Default facing

    }
    public BankTerminalBlock(Properties pProperties) {
        super(pProperties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankTerminalBlockEntity(pos, state);
    }
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        checkNeighbors(level, pos);
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            StockMarketMod.LOGGER.info("placing block");
            if (blockEntity instanceof BankTerminalBlockEntity) {
                StockMarketMod.LOGGER.info("Entity is BankTerminalBlockEntity");
                BankTerminalBlockEntity bankTerminalEntity = (BankTerminalBlockEntity) blockEntity;
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

        if (world.getBlockEntity(pos) instanceof BankTerminalBlockEntity blockEntity) {
            blockEntity.onBlockPlacedBy(state, placer);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (!level.isClientSide)
            checkNeighbor(level, fromPos);
    }

    private boolean checkNeighbor(Level level, BlockPos pos)
    {
        // Check the neighbors

        // Check if the neighbor block is Block B
        BlockState neighborState = level.getBlockState(pos);
        if (neighborState.getBlock() instanceof StockMarketBlock) {
            // Get the BlockEntity of Block B
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof StockMarketBlockEntity) {
                StockMarketBlockEntity blockBEntity = (StockMarketBlockEntity) blockEntity;

                // Perform server_sender-side logic
                System.out.println("Server: Detected Block B with data: "); //+ blockBEntity.getCustomData());
                //blockBEntity.setCustomData(42); // Example: Modify BlockEntity dataä
                return true;
            }
        }
        return false;
    }
    private void checkNeighbors(Level level, BlockPos pos) {
        // Check the neighbors
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if(checkNeighbor(level, neighborPos))
            {
                return;
            }
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
            StockMarketMod.LOGGER.info("server_sender use BankTerminalBlock");
            //SyncStockMarketBlockEntityPacket.sendPacketToClient(pos, (StockMarketBlockEntity) level.getBlockEntity(pos), (ServerPlayer) player);
            // Open the screen on the server_sender side
            //NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
            //        (id, playerInventory, playerEntity) -> new ChartMenu(id, playerInventory,
            //                (StockMarketBlockEntity) level.getBlockEntity(pos),
            //                ContainerLevelAccess.create(level,pos)),
            //        Component.translatable("container.chart")));
        } else if (level.isClientSide) {
            // On the client side, open the TradeScreen
            StockMarketMod.LOGGER.info("client use BankTerminalBlock");
            //Minecraft.getInstance().setScreen(new TradeScreen(((StockMarketBlockEntity) level.getBlockEntity(pos)).getChartData()));
            BlockEntity entity = level.getBlockEntity(pos);
            Inventory playerInventory = player.getInventory();
            return ClientHooks.openBankTerminalBlockScreen(entity, pos, playerInventory);
        }
        return InteractionResult.SUCCESS;  // Indicate the interaction was successful
    }
}