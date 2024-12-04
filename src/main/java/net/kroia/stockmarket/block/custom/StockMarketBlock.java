package net.kroia.stockmarket.block.custom;

import net.kroia.stockmarket.ClientHooks;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.menu.custom.ChartMenu;
import net.kroia.stockmarket.screen.custom.ChartScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class StockMarketBlock extends Block implements EntityBlock {

    public static final String NAME = "stock_market_block";

    public StockMarketBlock()
    {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
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
            // Open the screen on the server side
            //NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
            //        (id, playerInventory, playerEntity) -> new ChartMenu(id, playerInventory,
            //                (StockMarketBlockEntity) level.getBlockEntity(pos),
            //                ContainerLevelAccess.create(level,pos)),
            //        Component.translatable("container.chart")));
        } else if (level.isClientSide) {
            // On the client side, open the ChartScreen
            StockMarketMod.LOGGER.info("client use StockMarketBlock");
            //Minecraft.getInstance().setScreen(new ChartScreen(((StockMarketBlockEntity) level.getBlockEntity(pos)).getChartData()));
            BlockEntity entity = level.getBlockEntity(pos);
            return ClientHooks.openStockMarketBlockScreen(entity, pos);
        }
        return InteractionResult.SUCCESS;  // Indicate the interaction was successful
    }
}
